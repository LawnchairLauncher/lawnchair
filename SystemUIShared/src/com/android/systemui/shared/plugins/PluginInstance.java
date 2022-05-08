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
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.plugins.Plugin;
import com.android.systemui.plugins.PluginFragment;
import com.android.systemui.plugins.PluginListener;

import dalvik.system.PathClassLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Contains a single instantiation of a Plugin.
 *
 * This class and its related Factory are in charge of actually instantiating a plugin and
 * managing any state related to it.
 *
 * @param <T> The type of plugin that this contains.
 */
public class PluginInstance<T extends Plugin> {
    private static final String TAG = "PluginInstance";
    private static final Map<String, ClassLoader> sClassLoaders = new ArrayMap<>();

    private final Context mPluginContext;
    private final VersionInfo mVersionInfo;
    private final ComponentName mComponentName;
    private final T mPlugin;

    /** */
    public PluginInstance(ComponentName componentName, T plugin, Context pluginContext,
            VersionInfo versionInfo) {
        mComponentName = componentName;
        mPlugin = plugin;
        mPluginContext = pluginContext;
        mVersionInfo = versionInfo;
    }

    /** Alerts listener and plugin that the plugin has been created. */
    public void onCreate(Context appContext, PluginListener<T> listener) {
        if (!(mPlugin instanceof PluginFragment)) {
            // Only call onCreate for plugins that aren't fragments, as fragments
            // will get the onCreate as part of the fragment lifecycle.
            mPlugin.onCreate(appContext, mPluginContext);
        }
        listener.onPluginConnected(mPlugin, mPluginContext);
    }

    /** Alerts listener and plugin that the plugin is being shutdown. */
    public void onDestroy(PluginListener<T> listener) {
        listener.onPluginDisconnected(mPlugin);
        if (!(mPlugin instanceof PluginFragment)) {
            // Only call onDestroy for plugins that aren't fragments, as fragments
            // will get the onDestroy as part of the fragment lifecycle.
            mPlugin.onDestroy();
        }
    }

    /**
     * Returns if the contained plugin matches the passed in class name.
     *
     * It does this by string comparison of the class names.
     **/
    public boolean containsPluginClass(Class pluginClass) {
        return mPlugin.getClass().getName().equals(pluginClass.getName());
    }

    public ComponentName getComponentName() {
        return mComponentName;
    }

    public String getPackage() {
        return mComponentName.getPackageName();
    }

    public VersionInfo getVersionInfo() {
        return mVersionInfo;
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
                Class<T> pluginClass)
                throws PackageManager.NameNotFoundException, ClassNotFoundException,
                InstantiationException, IllegalAccessException {

            ClassLoader classLoader = getClassLoader(appInfo, mBaseClassLoader);
            Context pluginContext = new PluginActionManager.PluginContextWrapper(
                    context.createApplicationContext(appInfo, 0), classLoader);
            Class<T> instanceClass = (Class<T>) Class.forName(
                    componentName.getClassName(), true, classLoader);
            // TODO: Only create the plugin before version check if we need it for
            // legacy version check.
            T instance = (T) mInstanceFactory.create(instanceClass);
            VersionInfo version = mVersionChecker.checkVersion(
                    instanceClass, pluginClass, instance);
            return new PluginInstance<T>(componentName, instance, pluginContext, version);
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
                    baseClassLoader, "com.android.systemui.plugin");
        }

        /** Returns class loader specific for the given plugin. */
        private ClassLoader getClassLoader(ApplicationInfo appInfo,
                ClassLoader baseClassLoader) {
            if (!mIsDebug && !isPluginPackagePrivileged(appInfo.packageName)) {
                Log.w(TAG, "Cannot get class loader for non-privileged plugin. Src:"
                        + appInfo.sourceDir + ", pkg: " + appInfo.packageName);
                return null;
            }
            if (sClassLoaders.containsKey(appInfo.packageName)) {
                return sClassLoaders.get(appInfo.packageName);
            }

            List<String> zipPaths = new ArrayList<>();
            List<String> libPaths = new ArrayList<>();
            LoadedApk.makePaths(null, true, appInfo, zipPaths, libPaths);
            ClassLoader classLoader = new PathClassLoader(
                    TextUtils.join(File.pathSeparator, zipPaths),
                    TextUtils.join(File.pathSeparator, libPaths),
                    getParentClassLoader(baseClassLoader));
            sClassLoaders.put(appInfo.packageName, classLoader);
            return classLoader;
        }
    }

    /** Class that compares a plugin class against an implementation for version matching. */
    public static class VersionChecker {
        /** Compares two plugin classes. */
        public <T extends Plugin> VersionInfo checkVersion(
                Class<T> instanceClass, Class<T> pluginClass, Plugin plugin) {
            VersionInfo pluginVersion = new VersionInfo().addClass(pluginClass);
            VersionInfo instanceVersion = new VersionInfo().addClass(instanceClass);
            if (instanceVersion.hasVersionInfo()) {
                pluginVersion.checkVersion(instanceVersion);
            } else {
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
}
