/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.shared.plugins;

import android.app.LoadedApk;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.plugins.Plugin;
import com.android.systemui.plugins.PluginFragment;
import com.android.systemui.plugins.PluginLifecycleManager;
import com.android.systemui.plugins.PluginListener;
import com.android.systemui.plugins.PluginProtector;
import com.android.systemui.plugins.PluginWrapper;
import com.android.systemui.plugins.ProtectedPluginListener;

import dalvik.system.PathClassLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Contains a single instantiation of a Plugin.
 *
 * This class and its related Factory are in charge of actually instantiating a plugin and
 * managing any state related to it.
 *
 * @param <T> The type of plugin that this contains.
 */
public class PluginInstance<T extends Plugin>
        implements PluginLifecycleManager, ProtectedPluginListener {
    private static final String TAG = "PluginInstance";

    private final Context mAppContext;
    private final PluginListener<T> mListener;
    private final ComponentName mComponentName;
    private final PluginFactory<T> mPluginFactory;
    private final String mTag;

    private boolean mHasError = false;
    private BiConsumer<String, String> mLogConsumer = null;
    private Context mPluginContext;
    private T mPlugin;

    /** */
    public PluginInstance(
            Context appContext,
            PluginListener<T> listener,
            ComponentName componentName,
            PluginFactory<T> pluginFactory,
            @Nullable T plugin) {
        mAppContext = appContext;
        mListener = listener;
        mComponentName = componentName;
        mPluginFactory = pluginFactory;
        mPlugin = plugin;
        mTag = TAG + "[" + mComponentName.getShortClassName() + "]"
                + '@' + Integer.toHexString(hashCode());

        if (mPlugin != null) {
            mPluginContext = mPluginFactory.createPluginContext();
        }
    }

    @Override
    public String toString() {
        return mTag;
    }

    /** */
    public boolean hasError() {
        return mHasError;
    }

    public void setLogFunc(BiConsumer logConsumer) {
        mLogConsumer = logConsumer;
    }

    private void log(String message) {
        if (mLogConsumer != null) {
            mLogConsumer.accept(mTag, message);
        }
    }

    @Override
    public synchronized boolean onFail(String className, String methodName, Throwable failure) {
        Log.e(TAG, "Failure from " + mPlugin + ". Disabling Plugin.");
        mHasError = true;
        unloadPlugin();
        mListener.onPluginDetached(this);
        return true;
    }

    /** Alerts listener and plugin that the plugin has been created. */
    public synchronized void onCreate() {
        if (mHasError) {
            log("Previous Fatal Exception detected for plugin class");
            return;
        }

        boolean loadPlugin = mListener.onPluginAttached(this);
        if (!loadPlugin) {
            if (mPlugin != null) {
                log("onCreate: auto-unload");
                unloadPlugin();
            }
            return;
        }

        if (mPlugin == null) {
            log("onCreate: auto-load");
            loadPlugin();
            return;
        }

        if (!checkVersion()) {
            log("onCreate: version check failed");
            return;
        }

        log("onCreate: load callbacks");
        if (!(mPlugin instanceof PluginFragment)) {
            // Only call onCreate for plugins that aren't fragments, as fragments
            // will get the onCreate as part of the fragment lifecycle.
            mPlugin.onCreate(mAppContext, mPluginContext);
        }
        mListener.onPluginLoaded(mPlugin, mPluginContext, this);
    }

    /** Alerts listener and plugin that the plugin is being shutdown. */
    public synchronized void onDestroy() {
        if (mHasError) {
            // Detached in error handler
            log("onDestroy - no-op");
            return;
        }

        log("onDestroy");
        unloadPlugin();
        mListener.onPluginDetached(this);
    }

    /** Returns the current plugin instance (if it is loaded). */
    @Nullable
    public T getPlugin() {
        return mHasError ? null : mPlugin;
    }

    /**
     * Loads and creates the plugin if it does not exist.
     */
    public synchronized void loadPlugin() {
        if (mHasError) {
            log("Previous Fatal Exception detected for plugin class");
            return;
        }

        if (mPlugin != null) {
            log("Load request when already loaded");
            return;
        }

        // Both of these calls take about 1 - 1.5 seconds in test runs
        mPlugin = mPluginFactory.createPlugin(this);
        mPluginContext = mPluginFactory.createPluginContext();
        if (mPlugin == null || mPluginContext == null) {
            Log.e(mTag, "Requested load, but failed");
            return;
        }

        if (!checkVersion()) {
            log("loadPlugin: version check failed");
            return;
        }

        log("Loaded plugin; running callbacks");
        if (!(mPlugin instanceof PluginFragment)) {
            // Only call onCreate for plugins that aren't fragments, as fragments
            // will get the onCreate as part of the fragment lifecycle.
            mPlugin.onCreate(mAppContext, mPluginContext);
        }
        mListener.onPluginLoaded(mPlugin, mPluginContext, this);
    }

    /**
     * Checks the plugin version, and permanently destroys the plugin instance on a failure
     */
    private synchronized boolean checkVersion() {
        if (mHasError) {
            return false;
        }

        if (mPlugin == null) {
            return true;
        }

        if (mPluginFactory.checkVersion(mPlugin)) {
            return true;
        }

        Log.wtf(TAG, "Version check failed for " + mPlugin.getClass().getSimpleName());
        mHasError = true;
        unloadPlugin();
        mListener.onPluginDetached(this);
        return false;
    }

    /**
     * Unloads and destroys the current plugin instance if it exists.
     *
     * This will free the associated memory if there are not other references.
     */
    public synchronized void unloadPlugin() {
        if (mPlugin == null) {
            log("Unload request when already unloaded");
            return;
        }

        log("Unloading plugin, running callbacks");
        mListener.onPluginUnloaded(mPlugin, this);
        if (!(mPlugin instanceof PluginFragment)) {
            // Only call onDestroy for plugins that aren't fragments, as fragments
            // will get the onDestroy as part of the fragment lifecycle.
            mPlugin.onDestroy();
        }
        mPlugin = null;
        mPluginContext = null;
    }

    /**
     * Returns if the contained plugin matches the passed in class name.
     *
     * It does this by string comparison of the class names.
     **/
    public boolean containsPluginClass(Class pluginClass) {
        return mComponentName.getClassName().equals(pluginClass.getName());
    }

    public ComponentName getComponentName() {
        return mComponentName;
    }

    public String getPackage() {
        return mComponentName.getPackageName();
    }

    public VersionInfo getVersionInfo() {
        return mPluginFactory.getVersionInfo(mPlugin);
    }

    @VisibleForTesting
    Context getPluginContext() {
        return mPluginContext;
    }

    /** Used to create new {@link PluginInstance}s. */
    public static class Factory {
        private final ClassLoader mBaseClassLoader;
        private final InstanceFactory<?> mInstanceFactory;
        private final VersionChecker mVersionChecker;
        private final boolean mIsDebug;
        private final List<String> mPrivilegedPlugins;

        /** Factory used to construct {@link PluginInstance}s. */
        public Factory(ClassLoader classLoader, InstanceFactory<?> instanceFactory,
                VersionChecker versionChecker,
                List<String> privilegedPlugins,
                boolean isDebug) {
            mPrivilegedPlugins = privilegedPlugins;
            mBaseClassLoader = classLoader;
            mInstanceFactory = instanceFactory;
            mVersionChecker = versionChecker;
            mIsDebug = isDebug;
        }

        /** Construct a new PluginInstance. */
        public <T extends Plugin> PluginInstance<T> create(
                Context context,
                ApplicationInfo appInfo,
                ComponentName componentName,
                Class<T> pluginClass,
                PluginListener<T> listener)
                throws PackageManager.NameNotFoundException, ClassNotFoundException,
                InstantiationException, IllegalAccessException {

            PluginFactory<T> pluginFactory = new PluginFactory<T>(
                    context, mInstanceFactory, appInfo, componentName, mVersionChecker, pluginClass,
                    () -> getClassLoader(appInfo, mBaseClassLoader));
            return new PluginInstance<T>(
                    context, listener, componentName, pluginFactory, null);
        }

        private boolean isPluginPackagePrivileged(String packageName) {
            for (String componentNameOrPackage : mPrivilegedPlugins) {
                ComponentName componentName = ComponentName.unflattenFromString(
                        componentNameOrPackage);
                if (componentName != null) {
                    if (componentName.getPackageName().equals(packageName)) {
                        return true;
                    }
                } else if (componentNameOrPackage.equals(packageName)) {
                    return true;
                }
            }
            return false;
        }

        private ClassLoader getParentClassLoader(ClassLoader baseClassLoader) {
            return new PluginManagerImpl.ClassLoaderFilter(
                    baseClassLoader,
                    "androidx.constraintlayout.widget",
                    "com.android.systemui.common",
                    "com.android.systemui.log",
                    "com.android.systemui.plugin");
        }

        /** Returns class loader specific for the given plugin. */
        private ClassLoader getClassLoader(ApplicationInfo appInfo,
                ClassLoader baseClassLoader) {
            if (!mIsDebug && !isPluginPackagePrivileged(appInfo.packageName)) {
                Log.w(TAG, "Cannot get class loader for non-privileged plugin. Src:"
                        + appInfo.sourceDir + ", pkg: " + appInfo.packageName);
                return null;
            }

            List<String> zipPaths = new ArrayList<>();
            List<String> libPaths = new ArrayList<>();
            LoadedApk.makePaths(null, true, appInfo, zipPaths, libPaths);
            ClassLoader classLoader = new PathClassLoader(
                    TextUtils.join(File.pathSeparator, zipPaths),
                    TextUtils.join(File.pathSeparator, libPaths),
                    getParentClassLoader(baseClassLoader));
            return classLoader;
        }
    }

    /** Class that compares a plugin class against an implementation for version matching. */
    public interface VersionChecker {
        /** Compares two plugin classes. Returns true when match. */
        <T extends Plugin> boolean checkVersion(
                Class<T> instanceClass, Class<T> pluginClass, Plugin plugin);

        /** Returns VersionInfo for the target class */
        <T extends Plugin> VersionInfo getVersionInfo(Class<T> instanceclass);
    }

    /** Class that compares a plugin class against an implementation for version matching. */
    public static class VersionCheckerImpl implements VersionChecker {
        @Override
        /** Compares two plugin classes. */
        public <T extends Plugin> boolean checkVersion(
                Class<T> instanceClass, Class<T> pluginClass, Plugin plugin) {
            VersionInfo pluginVersion = new VersionInfo().addClass(pluginClass);
            VersionInfo instanceVersion = new VersionInfo().addClass(instanceClass);
            if (instanceVersion.hasVersionInfo()) {
                pluginVersion.checkVersion(instanceVersion);
            } else if (plugin != null) {
                int fallbackVersion = plugin.getVersion();
                if (fallbackVersion != pluginVersion.getDefaultVersion()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        /** Returns the version info for the class */
        public <T extends Plugin> VersionInfo getVersionInfo(Class<T> instanceClass) {
            VersionInfo instanceVersion = new VersionInfo().addClass(instanceClass);
            return instanceVersion.hasVersionInfo() ? instanceVersion : null;
        }
    }

    /**
     *  Simple class to create a new instance. Useful for testing.
     *
     * @param <T> The type of plugin this create.
     **/
    public static class InstanceFactory<T extends Plugin> {
        T create(Class cls) throws IllegalAccessException, InstantiationException {
            return (T) cls.newInstance();
        }
    }

    /**
     * Instanced wrapper of InstanceFactory
     *
     * @param <T> is the type of the plugin object to be built
     **/
    public static class PluginFactory<T extends Plugin> {
        private final Context mContext;
        private final InstanceFactory<?> mInstanceFactory;
        private final ApplicationInfo mAppInfo;
        private final ComponentName mComponentName;
        private final VersionChecker mVersionChecker;
        private final Class<T> mPluginClass;
        private final Supplier<ClassLoader> mClassLoaderFactory;

        public PluginFactory(
                Context context,
                InstanceFactory<?> instanceFactory,
                ApplicationInfo appInfo,
                ComponentName componentName,
                VersionChecker versionChecker,
                Class<T> pluginClass,
                Supplier<ClassLoader> classLoaderFactory) {
            mContext = context;
            mInstanceFactory = instanceFactory;
            mAppInfo = appInfo;
            mComponentName = componentName;
            mVersionChecker = versionChecker;
            mPluginClass = pluginClass;
            mClassLoaderFactory = classLoaderFactory;
        }

        /** Creates the related plugin object from the factory */
        public T createPlugin(ProtectedPluginListener listener) {
            try {
                ClassLoader loader = mClassLoaderFactory.get();
                Class<T> instanceClass = (Class<T>) Class.forName(
                        mComponentName.getClassName(), true, loader);
                T result = (T) mInstanceFactory.create(instanceClass);
                Log.v(TAG, "Created plugin: " + result);
                return PluginProtector.protectIfAble(result, listener);
            } catch (ReflectiveOperationException ex) {
                Log.wtf(TAG, "Failed to load plugin", ex);
            }
            return null;
        }

        /** Creates a context wrapper for the plugin */
        public Context createPluginContext() {
            try {
                ClassLoader loader = mClassLoaderFactory.get();
                return new PluginActionManager.PluginContextWrapper(
                    mContext.createApplicationContext(mAppInfo, 0), loader);
            } catch (NameNotFoundException ex) {
                Log.e(TAG, "Failed to create plugin context", ex);
            }
            return null;
        }

        /** Check Version for the instance */
        public boolean checkVersion(T instance) {
            if (instance == null) {
                instance = createPlugin(null);
            }
            if (instance instanceof PluginWrapper) {
                instance = ((PluginWrapper<T>) instance).getPlugin();
            }
            return mVersionChecker.checkVersion(
                    (Class<T>) instance.getClass(), mPluginClass, instance);
        }

        /** Get Version Info for the instance */
        public VersionInfo getVersionInfo(T instance) {
            if (instance == null) {
                instance = createPlugin(null);
            }
            if (instance instanceof PluginWrapper) {
                instance = ((PluginWrapper<T>) instance).getPlugin();
            }
            return mVersionChecker.getVersionInfo((Class<T>) instance.getClass());
        }
    }
}
