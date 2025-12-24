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

import android.app.LoadedApk
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.text.TextUtils
import androidx.core.content.edit
import com.android.systemui.log.core.Logger
import com.android.systemui.plugins.Plugin
import com.android.systemui.plugins.PluginFragment
import com.android.systemui.plugins.PluginLifecycleManager
import com.android.systemui.plugins.PluginListener
import com.android.systemui.plugins.PluginManager
import com.android.systemui.plugins.PluginProtector.protectIfAble
import com.android.systemui.plugins.PluginWrapper
import com.android.systemui.plugins.ProtectedPluginListener
import com.android.systemui.shared.plugins.PluginActionManager.PluginContextWrapper
import com.android.systemui.shared.plugins.PluginManagerImpl.Companion.DEFAULT_LOGBUFFER
import com.android.systemui.shared.plugins.PluginManagerImpl.Companion.PLUGIN_CLASSLOADER
import dalvik.system.PathClassLoader
import java.io.File
import javax.inject.Inject
import javax.inject.Named

/**
 * Contains a single instantiation of a Plugin.
 *
 * This class and its related Factory are in charge of actually instantiating a plugin and managing
 * any state related to it.
 *
 * @param [T] The type of plugin that this object manages.
 */
class PluginInstance<T : Plugin>(
    private val hostContext: Context,
    private val listener: PluginListener<T>,
    override val componentName: ComponentName,
    private val pluginFactory: PluginFactory<T>,
    private val buildInfo: BuildInfo,
) : PluginLifecycleManager<T>, ProtectedPluginListener {
    private val debugName = componentName.shortClassName
    private val tag = "$TAG[$debugName]@${hashCode()}"
    private val logger = Logger(listener.logBuffer ?: DEFAULT_LOGBUFFER, tag)
    override val packageName: String = componentName.packageName

    /** True if an error has been observed */
    var hasError: Boolean = loadFailure()
        private set

    private data class PluginData<T>(val plugin: T, val context: Context)

    private var pluginData: PluginData<T>? = null

    /** Returns the current plugin instance (if it is loaded). */
    override val plugin: T?
        get() = if (hasError) null else pluginData?.plugin

    val pluginContext: Context?
        get() = if (hasError) null else pluginData?.context

    override fun toString(): String = tag

    @Synchronized
    override fun onFail(className: String, methodName: String, failure: Throwable): Boolean {
        logger.e({ "Failure from '$str1'. Disabling Plugin." }, failure) { str1 = debugName }

        storeFailure(failure)
        hasError = true
        unloadPlugin()
        listener.onPluginDetached(this)
        return true
    }

    private fun getSharedPreferences(): SharedPreferences {
        return hostContext.getSharedPreferences("PluginFailure_$debugName", Context.MODE_PRIVATE)
    }

    /** Persists failure to avoid boot looping if process recovery fails */
    @Synchronized
    private fun storeFailure(failure: Throwable) {
        getSharedPreferences().edit(commit = true) {
            clear()
            putLong(FAIL_TIME, System.currentTimeMillis())
            putString(FAIL_MESSAGE, failure.message)
            var i = 0
            while (i < failure.stackTrace.size && i < FAIL_MAX_STACK) {
                putString("Stack[$i]", "${failure.stackTrace[i]}")
                i++
            }
        }
    }

    /** Loads a persisted failure if it's still within the timeout. */
    @Synchronized
    private fun loadFailure(): Boolean {
        val sharedPrefs = getSharedPreferences()

        if (buildInfo.isEng) {
            hasError = false
            return false
        }

        // TODO(b/438515243): Check apk checksums for differences (systemui & plugin)
        // If the failure occurred too long ago, we ignore it to check if it's still happening.
        if (sharedPrefs.getLong(FAIL_TIME, 0) < System.currentTimeMillis() - FAIL_TIMEOUT_MILLIS) {
            hasError = false
            return false
        }

        // Log previous the failure so that it appears in new bugreports
        logger.e({ "Disabling Plugin '$str1' due to persisted failure '$str2'" }) {
            str1 = debugName
            str2 = sharedPrefs.getString(FAIL_MESSAGE, "Unknown")
        }

        hasError = true
        return true
    }

    /** Alerts listener and plugin that the plugin has been created. */
    @Synchronized
    fun onCreate() {
        if (hasError) {
            logger.w("Previous Fatal Exception detected for plugin class")
            return
        }

        val loadPlugin = listener.onPluginAttached(this)
        if (!loadPlugin) {
            if (pluginData != null) {
                logger.d("onCreate: auto-unload")
                unloadPlugin()
            }
            return
        }

        val (plugin, pluginContext) =
            pluginData
                ?: run {
                    logger.d("onCreate: auto-load")
                    loadPlugin()
                    return
                }

        if (!checkVersion(plugin)) {
            logger.d("onCreate: version check failed")
            return
        }

        logger.i("onCreate: load callbacks")
        if (plugin !is PluginFragment) {
            // Only call onCreate for plugins that aren't fragments, as fragments
            // will get the onCreate as part of the fragment lifecycle.
            plugin.onCreate(hostContext, pluginContext)
        }
        listener.onPluginLoaded(plugin, pluginContext, this)
    }

    /** Alerts listener and plugin that the plugin is being shutdown. */
    @Synchronized
    fun onDestroy() {
        if (hasError) {
            // Detached in error handler
            logger.d("onDestroy - no-op")
            return
        }

        logger.i("onDestroy")
        unloadPlugin()
        listener.onPluginDetached(this)
    }

    /** Loads and creates the plugin if it does not exist. */
    @Synchronized
    override fun loadPlugin() {
        if (hasError) {
            logger.w("Previous Fatal Exception detected for plugin class")
            return
        }

        if (pluginData != null) {
            logger.d("Load request when already loaded")
            return
        }

        // Both of these calls take about 1 - 1.5 seconds in test runs
        val plugin = pluginFactory.createPlugin(this)
        val pluginContext = pluginFactory.createPluginContext()
        if (plugin == null || pluginContext == null) {
            logger.e("Requested load, but failed")
            return
        }

        if (!checkVersion(plugin)) {
            logger.e("loadPlugin: version check failed")
            return
        }

        pluginData = PluginData(plugin, pluginContext)

        logger.e("Loaded plugin; running callbacks")
        if (plugin !is PluginFragment) {
            // Only call onCreate for plugins that aren't fragments, as fragments
            // will get the onCreate as part of the fragment lifecycle.
            plugin.onCreate(hostContext, pluginContext)
        }
        listener.onPluginLoaded(plugin, pluginContext, this)
    }

    /** Checks the plugin version, and permanently destroys the plugin instance on a failure */
    @Synchronized
    private fun checkVersion(plugin: T): Boolean {
        if (hasError) return false
        if (pluginFactory.checkVersion(plugin)) return true

        logger.wtf({ "Version check failed for '$str1'" }) { str1 = debugName }
        hasError = true
        unloadPlugin()
        listener.onPluginDetached(this)
        return false
    }

    /**
     * Unloads and destroys the current plugin instance if it exists.
     *
     * This will free the associated memory if there are not other references.
     */
    @Synchronized
    override fun unloadPlugin() {
        val (plugin, _) =
            pluginData
                ?: run {
                    logger.d("Unload request when already unloaded")
                    return
                }

        logger.i("Unloading plugin, running callbacks")
        listener.onPluginUnloaded(plugin, this)
        if (plugin !is PluginFragment) {
            // Only call onDestroy for plugins that aren't fragments, as fragments
            // will get the onDestroy as part of the fragment lifecycle.
            plugin.onDestroy()
        }
        pluginData = null
    }

    /**
     * Returns if the contained plugin matches the passed in class name.
     *
     * It does this by string comparison of the class names.
     */
    fun containsPluginClass(pluginClass: Class<*>): Boolean {
        return componentName.className == pluginClass.name
    }

    val versionInfo: VersionInfo?
        get() = pluginFactory.getVersionInfo(plugin)

    /** Factory used to construct [PluginInstance]s. */
    open class Factory(
        private val versionChecker: VersionChecker,
        @Named(PLUGIN_CLASSLOADER) private val baseClassLoader: ClassLoader,
        private val config: PluginManager.Config,
        private val buildInfo: BuildInfo,
        private val instanceFactory: (Class<*>) -> Any = { it.newInstance() },
    ) {
        private val logger = Logger(DEFAULT_LOGBUFFER, TAG)

        @Inject
        constructor(
            versionChecker: VersionChecker,
            @Named(PLUGIN_CLASSLOADER) baseClassLoader: ClassLoader,
            config: PluginManager.Config,
        ) : this(versionChecker, baseClassLoader, config, BuildInfo.CURRENT)

        /** Construct a new PluginInstance. */
        open fun <T : Plugin> create(
            hostContext: Context,
            pluginAppInfo: ApplicationInfo,
            componentName: ComponentName,
            pluginClass: Class<T>,
            listener: PluginListener<T>,
        ): PluginInstance<T>? {
            if (!buildInfo.isDebuggable && !config.isPackagePrivileged(pluginAppInfo.packageName)) {
                logger.w({ "Cannot build non-privileged plugin. Src: $str1, pkg: $str2" }) {
                    str1 = pluginAppInfo.sourceDir
                    str2 = pluginAppInfo.packageName
                }
                return null
            }

            return PluginInstance(
                hostContext,
                listener,
                componentName,
                PluginFactory(
                    hostContext,
                    { instanceFactory(it) as T },
                    pluginAppInfo,
                    componentName,
                    versionChecker,
                    pluginClass,
                    baseClassLoader,
                ),
                buildInfo,
            )
        }
    }

    private class ClassLoaderFilter(
        private val target: ClassLoader,
        private val packages: List<String>,
        parent: ClassLoader,
    ) : ClassLoader(parent) {
        override fun findClass(name: String): Class<*> {
            for (pkg in packages) {
                if (name.startsWith(pkg)) {
                    return target.loadClass(name)
                }
            }
            return super.findClass(name)
        }
    }

    /**
     * Instanced wrapper of InstanceFactory
     *
     * @param [T] is the type of the plugin object to be built
     */
    class PluginFactory<T : Plugin>(
        private val hostContext: Context,
        private val instanceFactory: (Class<T>) -> T,
        private val pluginAppInfo: ApplicationInfo,
        private val componentName: ComponentName,
        private val versionChecker: VersionChecker,
        private val pluginClass: Class<T>,
        private val baseClassLoader: ClassLoader,
    ) {
        private val logger = Logger(DEFAULT_LOGBUFFER, TAG)

        /** Creates the related plugin object from the factory */
        fun createPlugin(listener: ProtectedPluginListener?): T? {
            try {
                val loader = createClassLoader()
                val cls = Class.forName(componentName.className, true, loader) as Class<T>
                val result = instanceFactory(cls)
                logger.v({ "Created plugin: $str1" }) { str1 = "$result" }
                return protectIfAble(result, listener)
            } catch (ex: ReflectiveOperationException) {
                logger.wtf("Failed to load plugin", ex)
            }
            return null
        }

        /** Creates a context wrapper for the plugin */
        fun createPluginContext(): Context? {
            try {
                val loader = createClassLoader()
                return PluginContextWrapper(
                    hostContext.createApplicationContext(pluginAppInfo, 0),
                    loader,
                )
            } catch (ex: PackageManager.NameNotFoundException) {
                logger.e("Failed to create plugin context", ex)
            }
            return null
        }

        /** Returns class loader specific for the given plugin. */
        private fun createClassLoader(): ClassLoader {
            val zipPaths = mutableListOf<String>()
            val libPaths = mutableListOf<String>()
            LoadedApk.makePaths(null, true, pluginAppInfo, zipPaths, libPaths)

            val filteredLoader: ClassLoader =
                ClassLoaderFilter(
                    baseClassLoader,
                    FILTERED_PACKAGES,
                    ClassLoader.getSystemClassLoader(),
                )

            return PathClassLoader(
                TextUtils.join(File.pathSeparator, zipPaths),
                TextUtils.join(File.pathSeparator, libPaths),
                filteredLoader,
            )
        }

        /** Check Version for the instance */
        fun checkVersion(target: T?): Boolean {
            var instance = target ?: createPlugin(null) ?: return false
            if (instance is PluginWrapper<*>) instance = instance.plugin as T
            return versionChecker.checkVersion(instance.javaClass, pluginClass, instance)
        }

        /** Get Version Info for the instance */
        fun getVersionInfo(target: T?): VersionInfo? {
            var instance = target ?: createPlugin(null) ?: return null
            if (instance is PluginWrapper<*>) instance = instance.plugin as T
            return versionChecker.getVersionInfo(instance.javaClass)
        }
    }

    companion object {
        private const val TAG = "PluginInstance"
        private const val FAIL_TIME = "FailureTime"
        private const val FAIL_MESSAGE = "ErrorMessage"
        private const val FAIL_MAX_STACK = 20
        private const val FAIL_TIMEOUT_MILLIS = (24 * 60 * 60 * 1000).toLong()

        private val FILTERED_PACKAGES =
            listOf(
                "androidx.compose",
                "androidx.constraintlayout.widget",
                "com.android.systemui.common",
                "com.android.systemui.log",
                "com.android.systemui.plugin",
                "com.android.compose.animation.scene",
                "kotlin.jvm.functions",
            )
    }
}
