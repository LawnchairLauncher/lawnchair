/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.shared.plugins

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.view.LayoutInflater
import androidx.core.net.toUri
import com.android.internal.messages.nano.SystemMessageProto
import com.android.systemui.log.core.LogLevel
import com.android.systemui.log.core.Logger
import com.android.systemui.plugins.Plugin
import com.android.systemui.plugins.PluginListener
import com.android.systemui.plugins.PluginManager
import com.android.systemui.shared.plugins.PluginEnabler.DisableReason
import com.android.systemui.shared.plugins.PluginManagerImpl.Companion.DEFAULT_LOGBUFFER
import com.android.systemui.shared.plugins.VersionInfo.InvalidVersionException
import java.util.concurrent.Executor

/**
 * Coordinates all the available plugins for a given action.
 *
 * The available plugins are queried from the [PackageManager] via an an [Intent] action.
 *
 * @param <T> The type of plugin that this contains.
 */
class PluginActionManager<T : Plugin>
private constructor(
    private val hostContext: Context,
    private val packageManager: PackageManager,
    private val action: String,
    private val listener: PluginListener<T>,
    private val pluginClass: Class<T>,
    private val allowMultiple: Boolean,
    private val mainExecutor: Executor,
    private val bgExecutor: Executor,
    private val buildInfo: BuildInfo,
    private val notificationManager: NotificationManager,
    private val pluginEnabler: PluginEnabler,
    private val config: PluginManager.Config,
    private val pluginInstanceFactory: PluginInstance.Factory,
) {
    private val pluginInstances = mutableListOf<PluginInstance<T>>()
    private val logger = Logger(listener.logBuffer ?: DEFAULT_LOGBUFFER, "$TAG[$action]")

    /** Load all plugins matching this instance's action. */
    fun loadAll() {
        logger.d("startListening")
        bgExecutor.execute { queryAll() }
    }

    /** Unload all plugins managed by this instance. */
    fun destroy() {
        logger.d("stopListening")
        val plugins = ArrayList(pluginInstances)
        for (plugInstance in plugins) {
            mainExecutor.execute { onPluginDisconnected(plugInstance) }
        }
    }

    /** Unload all matching plugins managed by this instance. */
    fun onPackageRemoved(pkg: String) {
        bgExecutor.execute { removePkg(pkg) }
    }

    /** Unload and then reload all matching plugins managed by this instance. */
    fun reloadPackage(pkg: String) {
        bgExecutor.execute {
            removePkg(pkg)
            queryPkg(pkg)
        }
    }

    /** Disable a specific plugin managed by this instance. */
    fun checkAndDisable(className: String): Boolean {
        var disableAny = false
        val plugins = ArrayList(pluginInstances)
        for (info in plugins) {
            if (className.startsWith(info.packageName)) {
                disableAny = disableAny || disable(info, DisableReason.DISABLED_FROM_EXPLICIT_CRASH)
            }
        }
        return disableAny
    }

    /** Disable all plugins managed by this instance. */
    fun disableAll(): Boolean {
        val plugins = ArrayList(pluginInstances)
        var disabledAny = false
        for (i in plugins.indices) {
            disabledAny =
                disabledAny || disable(plugins[i], DisableReason.DISABLED_FROM_SYSTEM_CRASH)
        }
        return disabledAny
    }

    /** Misbehaving plugins get disabled and won't come back until uninstall/reinstall. */
    private fun disable(pluginInstance: PluginInstance<T>, reason: DisableReason): Boolean {
        val pluginComponent = pluginInstance.componentName

        if (config.isPrivileged(pluginComponent)) {
            logger.i({ "Ignoring request to disable privileged plugin: $str1" }) {
                str1 = pluginComponent.flattenToShortString()
            }
            return false
        }

        logger.w({ "Disabling plugin: $str1" }) { str1 = pluginComponent.flattenToShortString() }
        pluginEnabler.setDisabled(pluginComponent, reason)
        return true
    }

    fun <C> dependsOn(p: Plugin, cls: Class<C>?): Boolean {
        val instances = ArrayList(pluginInstances)
        for (instance in instances) {
            if (instance.containsPluginClass(p.javaClass)) {
                return instance.versionInfo?.hasClass(cls) ?: false
            }
        }
        return false
    }

    override fun toString(): String = "${this::class.simpleName}@${hashCode()} (action=$action)"

    private fun onPluginConnected(pluginInstance: PluginInstance<T>) {
        logger.d("onPluginConnected")
        PluginPrefs.setHasPlugins(hostContext)
        pluginInstance.onCreate()
    }

    private fun onPluginDisconnected(pluginInstance: PluginInstance<T>) {
        logger.d("onPluginDisconnected")
        pluginInstance.onDestroy()
    }

    private fun queryAll() {
        logger.d("queryAll")
        for (i in pluginInstances.indices.reversed()) {
            val pluginInstance = pluginInstances[i]
            mainExecutor.execute { onPluginDisconnected(pluginInstance) }
        }
        pluginInstances.clear()
        handleQueryPlugins(null)
    }

    private fun removePkg(pkg: String) {
        for (i in pluginInstances.indices.reversed()) {
            val pluginInstance = pluginInstances[i]
            if (pluginInstance.packageName == pkg) {
                mainExecutor.execute { onPluginDisconnected(pluginInstance) }
                pluginInstances.removeAt(i)
            }
        }
    }

    private fun queryPkg(pkg: String) {
        logger.d({ "queryPkg($str1)" }) { str1 = pkg }
        if (allowMultiple || (pluginInstances.size == 0)) {
            handleQueryPlugins(pkg)
        } else {
            logger.d("Too many matching packages found")
        }
    }

    private fun handleQueryPlugins(pkgName: String?) {
        // This isn't actually a service and shouldn't ever be started, but is
        // a convenient PM based way to manage our plugins.
        val intent = Intent(action)
        if (pkgName != null) {
            intent.setPackage(pkgName)
        }
        val result = packageManager.queryIntentServices(intent, 0)
        var logLevel = LogLevel.INFO
        val logMessage = buildString {
            append("Found ")
            append(result.size)
            append(" plugins")

            if (result.size > 1 && !allowMultiple) {
                append(", but multiple plugins are disallowed.")
                logLevel = LogLevel.WARNING
                return@buildString
            }

            for (info in result) {
                val name = ComponentName(info.serviceInfo.packageName, info.serviceInfo.name)
                append("\n  $name")
                val pluginInstance = loadPluginComponent(name)
                if (pluginInstance != null) {
                    // add plugin before sending PLUGIN_CONNECTED message
                    pluginInstances.add(pluginInstance)
                    mainExecutor.execute { onPluginConnected(pluginInstance) }
                }
            }
        }

        logger.log(logLevel, logMessage)
    }

    private fun loadPluginComponent(component: ComponentName): PluginInstance<T>? {
        // Do not load non-privileged plugins in production builds.
        if (!buildInfo.isDebuggable && !config.isPrivileged(component)) {
            logger.e({ "Plugin cannot be loaded in production: $str1" }) { str1 = "$component" }
            return null
        }

        if (!pluginEnabler.isEnabled(component)) {
            logger.w({ "Plugin is not enabled, aborting load: $str1" }) { str1 = "$component" }
            return null
        }

        try {
            val packageName = component.packageName
            // This isn't needed given that we don't have IGNORE_SECURITY on, but given the number
            // different contexts plugins is executed in, we prefer to check ourselves again.
            if (
                packageManager.checkPermission(PLUGIN_PERMISSION, packageName) !=
                    PackageManager.PERMISSION_GRANTED
            ) {
                logger.e({ "Plugin doesn't have permission: $str1" }) { str1 = packageName }
                return null
            }

            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            logger.d({ "createPlugin: $str1" }) { str1 = "$component" }

            try {
                return pluginInstanceFactory.create(
                    hostContext,
                    appInfo,
                    component,
                    pluginClass,
                    listener,
                )
            } catch (e: InvalidVersionException) {
                reportInvalidVersion(component, component.className, e)
            }
        } catch (ex: Throwable) {
            logger.e({ "Couldn't load plugin: $str1" }, ex) { str1 = "$component" }
            return null
        }

        return null
    }

    private fun reportInvalidVersion(
        component: ComponentName,
        className: String,
        ex: InvalidVersionException,
    ) {
        val icon = Resources.getSystem().getIdentifier("stat_sys_warning", "drawable", "android")
        val color =
            Resources.getSystem()
                .getIdentifier("system_notification_accent_color", "color", "android")
        val nb =
            Notification.Builder(hostContext, PluginManager.NOTIFICATION_CHANNEL_ID)
                .setStyle(Notification.BigTextStyle())
                .setSmallIcon(icon)
                .setWhen(0)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setColor(hostContext.getColor(color))
        val label =
            try {
                "${packageManager.getServiceInfo(component, 0).loadLabel(packageManager)}"
            } catch (_: PackageManager.NameNotFoundException) {
                className
            }

        if (!ex.isTooNew) {
            nb.setContentTitle("Plugin '$label' is too old")
                .setContentText("Contact plugin developer to get an updated version. ${ex.message}")
        } else {
            nb.setContentTitle("Plugin '$label' is too new")
                .setContentText("Check to see if an OTA is available. ${ex.message}")
        }

        val i =
            Intent(PluginManagerImpl.DISABLE_PLUGIN)
                .setData("package://${component.flattenToString()}".toUri())
        val pi = PendingIntent.getBroadcast(hostContext, 0, i, PendingIntent.FLAG_IMMUTABLE)
        nb.addAction(Notification.Action.Builder(null, "Disable plugin", pi).build())
        notificationManager.notify(SystemMessageProto.SystemMessage.NOTE_PLUGIN, nb.build())
        logger.e("Error loading plugin", ex)
    }

    /** Construct a [PluginActionManager] */
    class Factory
    @JvmOverloads
    constructor(
        private val context: Context,
        private val packageManager: PackageManager,
        private val mainExecutor: Executor,
        private val bgExecutor: Executor,
        private val notificationManager: NotificationManager,
        private val pluginEnabler: PluginEnabler,
        private val config: PluginManager.Config,
        private val pluginInstanceFactory: PluginInstance.Factory,
        private val buildInfo: BuildInfo = BuildInfo.CURRENT,
    ) {
        fun <T : Plugin> create(
            action: String,
            listener: PluginListener<T>,
            pluginClass: Class<T>,
            allowMultiple: Boolean,
        ): PluginActionManager<T> {
            return PluginActionManager(
                context,
                packageManager,
                action,
                listener,
                pluginClass,
                allowMultiple,
                mainExecutor,
                bgExecutor,
                buildInfo,
                notificationManager,
                pluginEnabler,
                config,
                pluginInstanceFactory,
            )
        }
    }

    /** Wrapper for PluginInstance contexts */
    class PluginContextWrapper(base: Context?, private val classLoader: ClassLoader) :
        ContextWrapper(base) {
        private val inflater: LayoutInflater by lazy {
            LayoutInflater.from(baseContext).cloneInContext(this)
        }

        override fun getClassLoader(): ClassLoader {
            return classLoader
        }

        override fun getSystemService(name: String): Any? {
            if (LAYOUT_INFLATER_SERVICE == name) {
                return inflater
            }
            return baseContext.getSystemService(name)
        }
    }

    companion object {
        private const val TAG = "PluginActionManager"
        const val PLUGIN_PERMISSION: String = "com.android.systemui.permission.PLUGIN"
    }
}
