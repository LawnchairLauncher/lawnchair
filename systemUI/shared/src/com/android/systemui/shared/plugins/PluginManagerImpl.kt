/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.shared.plugins

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemProperties
import androidx.annotation.VisibleForTesting
import com.android.internal.messages.nano.SystemMessageProto
import com.android.systemui.Dumpable
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.log.LogcatOnlyMessageBuffer
import com.android.systemui.log.core.LogLevel
import com.android.systemui.log.core.Logger
import com.android.systemui.plugins.Plugin
import com.android.systemui.plugins.PluginListener
import com.android.systemui.plugins.PluginManager
import com.android.systemui.shared.plugins.PluginEnabler.DisableReason
import com.android.systemui.shared.system.UncaughtExceptionPreHandlerManager
import java.io.PrintWriter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginManagerImpl
@Inject
constructor(
    @Application private val hostContext: Context,
    private val actionManagerFactory: PluginActionManager.Factory,
    preHandlerManager: UncaughtExceptionPreHandlerManager?,
    private val pluginEnabler: PluginEnabler,
    private val pluginPrefs: PluginPrefs,
    override val config: PluginManager.Config,
) : BroadcastReceiver(), PluginManager, Dumpable {
    private val pluginMap = mutableMapOf<PluginListener<*>, PluginActionManager<*>>()
    private val logger = Logger(DEFAULT_LOGBUFFER, TAG)
    var isListening = false
        private set

    init {
        preHandlerManager?.registerHandler(PluginExceptionHandler())
    }

    override fun <T : Plugin> addPluginListener(
        listener: PluginListener<T>,
        cls: Class<T>,
        allowMultiple: Boolean,
    ) {
        addPluginListener(PluginManager.getAction(cls), listener, cls, allowMultiple)
    }

    @VisibleForTesting
    fun <T : Plugin> addPluginListener(
        action: String,
        listener: PluginListener<T>,
        cls: Class<T>,
        allowMultiple: Boolean = false,
    ) {
        pluginPrefs.addAction(action)
        val actionManager =
            actionManagerFactory.create(action, listener, cls, allowMultiple).apply { loadAll() }
        synchronized(this) { pluginMap.put(listener, actionManager) }
        startListening()
    }

    @Synchronized
    override fun removePluginListener(listener: PluginListener<*>) {
        if (!pluginMap.containsKey(listener)) {
            return
        }

        pluginMap.remove(listener)?.destroy()
        if (pluginMap.isEmpty()) {
            stopListening()
        }
    }

    private fun startListening() {
        if (isListening) return
        isListening = true
        hostContext.registerReceiver(
            this,
            IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            },
        )

        hostContext.registerReceiver(
            this,
            IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(PluginManager.PLUGIN_CHANGED)
                addAction(DISABLE_PLUGIN)
                addDataScheme("package")
            },
            PluginActionManager.PLUGIN_PERMISSION,
            /* scheduler = */ null,
            Context.RECEIVER_EXPORTED_UNAUDITED,
        )

        hostContext.registerReceiver(this, IntentFilter(Intent.ACTION_USER_UNLOCKED))
    }

    private fun stopListening() {
        if (!isListening) return
        isListening = false
        hostContext.unregisterReceiver(this)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_USER_UNLOCKED -> {
                synchronized(this) {
                    for ((_, manager) in pluginMap) {
                        manager.loadAll()
                    }
                }
            }

            DISABLE_PLUGIN -> {
                val component =
                    ComponentName.unflattenFromString("${intent.data}".substring(10))
                        ?: throw IllegalStateException("Received invalid URI: ${intent.data}")

                // Don't disable privileged plugins as they are a part of the OS.
                if (config.isPrivileged(component)) return

                pluginEnabler.setDisabled(component, DisableReason.DISABLED_INVALID_VERSION)
                hostContext
                    .getSystemService(NotificationManager::class.java)
                    ?.cancel(component.className, SystemMessageProto.SystemMessage.NOTE_PLUGIN)
            }

            else -> {
                val pkg =
                    intent.data?.encodedSchemeSpecificPart
                        ?: throw IllegalStateException("Received invalid URI: ${intent.data}")
                val componentName = ComponentName.unflattenFromString(pkg)

                if (Intent.ACTION_PACKAGE_REPLACED == intent.action && componentName != null) {
                    val disableReason = pluginEnabler.getDisableReason(componentName)
                    if (disableReason.autoEnable) {
                        logger.i({ "Re-enabling disabled plugin that was updated: $str1" }) {
                            str1 = componentName.flattenToShortString()
                        }
                        pluginEnabler.setEnabled(componentName)
                    }
                }

                val isReload =
                    Intent.ACTION_PACKAGE_ADDED == intent.action ||
                        Intent.ACTION_PACKAGE_CHANGED == intent.action ||
                        Intent.ACTION_PACKAGE_REPLACED == intent.action
                synchronized(this) {
                    for (actionManager in pluginMap.values) {
                        if (isReload) {
                            actionManager.reloadPackage(pkg)
                        } else {
                            actionManager.onPackageRemoved(pkg)
                        }
                    }
                }
            }
        }
    }

    @Synchronized
    override fun <T> dependsOn(p: Plugin, cls: Class<T>): Boolean {
        for ((_, manager) in pluginMap) {
            if (manager.dependsOn(p, cls)) {
                return true
            }
        }
        return false
    }

    @Synchronized
    override fun dump(pw: PrintWriter, args: Array<String>) {
        pw.println("  plugin map (${pluginMap.size}):")
        for ((listener, actionManager) in pluginMap) {
            pw.println("    $listener -> $actionManager")
        }
    }

    private inner class PluginExceptionHandler : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            if (SystemProperties.getBoolean("plugin.debugging", false)) {
                return
            }

            // Search for and disable plugins that may have been involved in this crash.
            if (checkStack(throwable)) {
                logger.e("Uncaught plugin error", throwable)
                return
            }

            // We couldn't find any plugins involved in this crash, just to be safe disable all the
            // plugins, so we can be sure that SysUI keeps running as expected.
            synchronized(this) {
                logger.e("System Crash; Disabling all plugins", throwable)
                for ((_, manager) in pluginMap) {
                    manager.disableAll()
                }
            }
        }

        fun checkStack(throwable: Throwable?): Boolean {
            if (throwable == null) {
                return false
            }

            var disabledAny = false
            synchronized(this) {
                for (element in throwable.stackTrace) {
                    for ((_, manager) in pluginMap) {
                        disabledAny = disabledAny || manager.checkAndDisable(element.className)
                    }
                }
            }
            return disabledAny || checkStack(throwable.cause)
        }
    }

    companion object {
        val DEFAULT_LOGBUFFER = LogcatOnlyMessageBuffer(LogLevel.INFO)
        const val PLUGIN_THREAD: String = "plugin_thread"
        const val PLUGIN_CLASSLOADER: String = "plugin_classloader"

        private val TAG: String = PluginManagerImpl::class.java.simpleName
        const val DISABLE_PLUGIN: String = "com.android.systemui.action.DISABLE_PLUGIN"
    }
}
