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
public class PluginInstance<T extends Plugin> implements PluginLifecycleManager {
    private static final String TAG = "PluginInstance";

    private final Context mAppContext;
    private final PluginListener<T> mListener;
    private final ComponentName mComponentName;
    private final PluginFactory<T> mPluginFactory;
    private final String mTag;

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

    public void setLogFunc(BiConsumer logConsumer) {
        mLogConsumer = logConsumer;
    }

    private void log(String message) {
        if (mLogConsumer != null) {
            mLogConsumer.accept(mTag, message);
        }
    }

    /** Alerts listener and plugin that the plugin has been created. */
    public synchronized void onCreate() {
        boolean loadPlugin = mListener.onPluginAttached(this);
        if (!loadPlugin) {
            if (mPlugin != null) {
                log("onCreate: auto-unload");
                unloadPlugin();
            }
            return;
        }

        if (mPlugin == null) {
            log("onCreate auto-load");
            loadPlugin();
            return;
        }

        log("onCreate: load callbacks");
        mPluginFactory.checkVersion(mPlugin);
        if (!(mPlugin instanceof PluginFragment)) {
            // Only call onCreate for plugins that aren't fragments, as fragments
            // will get the onCreate as part of the fragment lifecycle.
            mPlugin.onCreate(mAppContext, mPluginContext);
        }
        mListener.onPluginLoaded(mPlugin, mPluginContext, this);
    }

    /** Alerts listener and plugin that the plugin is being shutdown. */
    public synchronized void onDestroy() {
        log("onDestroy");
        unloadPlugin();
        mListener.onPluginDetached(this);
    }

    /** Returns the current plugin instance (if it is loaded). */
    @Nullable
    public T getPlugin() {
        return mPlugin;
    }

    /**
     * Loads and creates the plugin if it does not exist.
     */
    public synchronized void loadPlugin() {
        if (mPlugin != null) {
            log("Load request when already loaded");
            return;
        }

        // Both of these calls take about 1 - 1.5 seconds in test runs
        mPlugin = mPluginFactory.createPlugin();
        mPluginContext = mPluginFactory.createPluginContext();
        if (mPlugin == null || mPluginContext == null) {
            Log.e(mTag, "Requested load, but failed");
            return;
        }

        log("Loaded plugin; running callbacks");
        mPluginFactory.checkVersion(mPlugin);
        if (!(mPlugin instanceof PluginFragment)) {
            // Only call onCreate for plugins that aren't fragments, as fragments
            // will get the onCreate as part of the fragment lifecycle.
            mPlugin.onCreate(mAppContext, mPluginContext);
        }
        mListener.onPluginLoaded(mPlugin, mPluginContext, this);
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
        return mPluginFactory.checkVersion(mPlugin);
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
        /** Compares two plugin classes. */
        <T extends Plugin> VersionInfo checkVersion(
                Class<T> instanceClass, Class<T> pluginClass, Plugin plugin);
    }

    /** Class that compares a plugin class against an implementation for version matching. */
    public static class VersionCheckerImpl implements VersionChecker {
        @Override
        /** Compares two plugin classes. */
        public <T extends Plugin> VersionInfo checkVersion(
                Class<T> instanceClass, Class<T> pluginClass, Plugin plugin) {
            VersionInfo pluginVersion = new VersionInfo().addClass(pluginClass);
            VersionInfo instanceVersion = new VersionInfo().addClass(instanceClass);
            if (instanceVersion.hasVersionInfo()) {
                pluginVersion.checkVersion(instanceVersion);
            } else if (plugin != null) {
                int fallbackVersion = plugin.getVersion();
                if (fallbackVersion != pluginVersion.getDefaultVersion()) {
                    throw new VersionInfo.InvalidVersionException("Invalid legacy version", false);
                }
                return null;
            }
            return instanceVersion;
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
        public T createPlugin() {
            try {
                ClassLoader loader = mClassLoaderFactory.get();
                Class<T> instanceClass = (Class<T>) Class.forName(
                        mComponentName.getClassName(), true, loader);
                T result = (T) mInstanceFactory.create(instanceClass);
                Log.v(TAG, "Created plugin: " + result);
                return result;
            } catch (ClassNotFoundException ex) {
                Log.e(TAG, "Failed to load plugin", ex);
            } catch (IllegalAccessException ex) {
                Log.e(TAG, "Failed to load plugin", ex);
            } catch (InstantiationException ex) {
                Log.e(TAG, "Failed to load plugin", ex);
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

        /** Check Version and create VersionInfo for instance */
        public VersionInfo checkVersion(T instance) {
            if (instance == null) {
                instance = createPlugin();
            }
            return mVersionChecker.checkVersion(
                    (Class<T>) instance.getClass(), mPluginClass, instance);
        }
    }
}
