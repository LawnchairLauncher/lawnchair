/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.systemui.shared.plugins;

import android.app.LoadedApk;
import android.app.Notification;
import android.app.Notification.Action;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.systemui.plugins.Plugin;
import com.android.systemui.plugins.PluginListener;
import com.android.systemui.plugins.annotations.ProvidesInterface;
import com.android.systemui.shared.plugins.PluginInstanceManager.PluginInfo;

import dalvik.system.PathClassLoader;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
/**
 * @see Plugin
 */
public class PluginManagerImpl extends BroadcastReceiver implements PluginManager {

    private static final String TAG = PluginManagerImpl.class.getSimpleName();
    static final String DISABLE_PLUGIN = "com.android.systemui.action.DISABLE_PLUGIN";

    private static PluginManager sInstance;

    private final ArrayMap<PluginListener<?>, PluginInstanceManager> mPluginMap
            = new ArrayMap<>();
    private final Map<String, ClassLoader> mClassLoaders = new ArrayMap<>();
    private final ArraySet<String> mOneShotPackages = new ArraySet<>();
    private final ArraySet<String> mWhitelistedPlugins = new ArraySet<>();
    private final Context mContext;
    private final PluginInstanceManagerFactory mFactory;
    private final boolean isDebuggable;
    private final PluginPrefs mPluginPrefs;
    private final PluginEnabler mPluginEnabler;
    private final PluginInitializer mPluginInitializer;
    private ClassLoaderFilter mParentClassLoader;
    private boolean mListening;
    private boolean mHasOneShot;
    private Looper mLooper;

    public PluginManagerImpl(Context context, PluginInitializer initializer) {
        this(context, new PluginInstanceManagerFactory(), Build.IS_DEBUGGABLE,
                Thread.getUncaughtExceptionPreHandler(), initializer);
    }

    @VisibleForTesting
    PluginManagerImpl(Context context, PluginInstanceManagerFactory factory, boolean debuggable,
            UncaughtExceptionHandler defaultHandler, final PluginInitializer initializer) {
        mContext = context;
        mFactory = factory;
        mLooper = initializer.getBgLooper();
        isDebuggable = true;
        mWhitelistedPlugins.addAll(Arrays.asList(initializer.getWhitelistedPlugins(mContext)));
        mPluginPrefs = new PluginPrefs(mContext);
        mPluginEnabler = initializer.getPluginEnabler(mContext);
        mPluginInitializer = initializer;

        PluginExceptionHandler uncaughtExceptionHandler = new PluginExceptionHandler(
                defaultHandler);
        Thread.setUncaughtExceptionPreHandler(uncaughtExceptionHandler);

        new Handler(mLooper).post(new Runnable() {
            @Override
            public void run() {
                initializer.onPluginManagerInit();
            }
        });
    }

    public String[] getWhitelistedPlugins() {
        return mWhitelistedPlugins.toArray(new String[0]);
    }

    public PluginEnabler getPluginEnabler() {
        return mPluginEnabler;
    }

    // TODO(mankoff): This appears to be only called from tests. Remove?
    public <T extends Plugin> T getOneShotPlugin(Class<T> cls) {
        ProvidesInterface info = cls.getDeclaredAnnotation(ProvidesInterface.class);
        if (info == null) {
            throw new RuntimeException(cls + " doesn't provide an interface");
        }
        if (TextUtils.isEmpty(info.action())) {
            throw new RuntimeException(cls + " doesn't provide an action");
        }
        return getOneShotPlugin(info.action(), cls);
    }

    public <T extends Plugin> T getOneShotPlugin(String action, Class<?> cls) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new RuntimeException("Must be called from UI thread");
        }
        // Passing null causes compiler to complain about incompatible (generic) types.
        PluginListener<Plugin> dummy = null;
        PluginInstanceManager<T> p = mFactory.createPluginInstanceManager(mContext, action, dummy,
                false, mLooper, cls, this);
        mPluginPrefs.addAction(action);
        PluginInfo<T> info = p.getPlugin();
        if (info != null) {
            mOneShotPackages.add(info.mPackage);
            mHasOneShot = true;
            startListening();
            return info.mPlugin;
        }
        return null;
    }

    public <T extends Plugin> void addPluginListener(PluginListener<T> listener, Class<?> cls) {
        addPluginListener(listener, cls, false);
    }

    public <T extends Plugin> void addPluginListener(PluginListener<T> listener, Class<?> cls,
            boolean allowMultiple) {
        addPluginListener(PluginManager.Helper.getAction(cls), listener, cls, allowMultiple);
    }

    public <T extends Plugin> void addPluginListener(String action, PluginListener<T> listener,
            Class<?> cls) {
        addPluginListener(action, listener, cls, false);
    }

    public <T extends Plugin> void addPluginListener(String action, PluginListener<T> listener,
            Class cls, boolean allowMultiple) {
        mPluginPrefs.addAction(action);
        PluginInstanceManager p = mFactory.createPluginInstanceManager(mContext, action, listener,
                allowMultiple, mLooper, cls, this);
        p.loadAll();
        synchronized (this) {
            mPluginMap.put(listener, p);
        }
        startListening();
    }

    public void removePluginListener(PluginListener<?> listener) {
        synchronized (this) {
            if (!mPluginMap.containsKey(listener)) {
                return;
            }
            mPluginMap.remove(listener).destroy();
            if (mPluginMap.size() == 0) {
                stopListening();
            }
        }
    }

    private void startListening() {
        if (mListening) return;
        mListening = true;
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(PLUGIN_CHANGED);
        filter.addAction(DISABLE_PLUGIN);
        filter.addDataScheme("package");
        mContext.registerReceiver(this, filter);
        filter = new IntentFilter(Intent.ACTION_USER_UNLOCKED);
        mContext.registerReceiver(this, filter);
    }

    private void stopListening() {
        // Never stop listening if a one-shot is present.
        if (!mListening || mHasOneShot) return;
        mListening = false;
        mContext.unregisterReceiver(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_USER_UNLOCKED.equals(intent.getAction())) {
            synchronized (this) {
                for (PluginInstanceManager manager : mPluginMap.values()) {
                    manager.loadAll();
                }
            }
        } else if (DISABLE_PLUGIN.equals(intent.getAction())) {
            Uri uri = intent.getData();
            ComponentName component = ComponentName.unflattenFromString(
                    uri.toString().substring(10));
            if (isPluginWhitelisted(component)) {
                // Don't disable whitelisted plugins as they are a part of the OS.
                return;
            }
            getPluginEnabler().setDisabled(component, PluginEnabler.DISABLED_INVALID_VERSION);
            mContext.getSystemService(NotificationManager.class).cancel(component.getClassName(),
                    SystemMessage.NOTE_PLUGIN);
        } else {
            Uri data = intent.getData();
            String pkg = data.getEncodedSchemeSpecificPart();
            ComponentName componentName = ComponentName.unflattenFromString(pkg);
            if (mOneShotPackages.contains(pkg)) {
                int icon = Resources.getSystem().getIdentifier(
                        "stat_sys_warning", "drawable", "android");
                int color = Resources.getSystem().getIdentifier(
                        "system_notification_accent_color", "color", "android");
                String label = pkg;
                try {
                    PackageManager pm = mContext.getPackageManager();
                    label = pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString();
                } catch (NameNotFoundException e) {
                }
                // Localization not required as this will never ever appear in a user build.
                final Notification.Builder nb =
                        new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                                .setSmallIcon(icon)
                                .setWhen(0)
                                .setShowWhen(false)
                                .setPriority(Notification.PRIORITY_MAX)
                                .setVisibility(Notification.VISIBILITY_PUBLIC)
                                .setColor(mContext.getColor(color))
                                .setContentTitle("Plugin \"" + label + "\" has updated")
                                .setContentText("Restart SysUI for changes to take effect.");
                Intent i = new Intent("com.android.systemui.action.RESTART").setData(
                            Uri.parse("package://" + pkg));
                PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, i, 0);
                nb.addAction(new Action.Builder(null, "Restart SysUI", pi).build());
                mContext.getSystemService(NotificationManager.class)
                        .notify(SystemMessage.NOTE_PLUGIN, nb.build());
            }
            if (clearClassLoader(pkg)) {
                if (Build.IS_ENG) {
                    Toast.makeText(mContext, "Reloading " + pkg, Toast.LENGTH_LONG).show();
                } else {
                    Log.v(TAG, "Reloading " + pkg);
                }
            }
            if (Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction())
                    && componentName != null) {
                @PluginEnabler.DisableReason int disableReason =
                        getPluginEnabler().getDisableReason(componentName);
                if (disableReason == PluginEnabler.DISABLED_FROM_EXPLICIT_CRASH
                        || disableReason == PluginEnabler.DISABLED_FROM_SYSTEM_CRASH
                        || disableReason == PluginEnabler.DISABLED_INVALID_VERSION) {
                    Log.i(TAG, "Re-enabling previously disabled plugin that has been "
                            + "updated: " + componentName.flattenToShortString());
                    getPluginEnabler().setEnabled(componentName);
                }
            }
            synchronized (this) {
                if (!Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
                    for (PluginInstanceManager manager : mPluginMap.values()) {
                        manager.onPackageChange(pkg);
                    }
                } else {
                    for (PluginInstanceManager manager : mPluginMap.values()) {
                        manager.onPackageRemoved(pkg);
                    }
                }
            }
        }
    }

    /** Returns class loader specific for the given plugin. */
    public ClassLoader getClassLoader(ApplicationInfo appInfo) {
        if (!isDebuggable && !isPluginPackageWhitelisted(appInfo.packageName)) {
            Log.w(TAG, "Cannot get class loader for non-whitelisted plugin. Src:"
                    + appInfo.sourceDir + ", pkg: " + appInfo.packageName);
            return null;
        }
        if (mClassLoaders.containsKey(appInfo.packageName)) {
            return mClassLoaders.get(appInfo.packageName);
        }

        List<String> zipPaths = new ArrayList<>();
        List<String> libPaths = new ArrayList<>();
        LoadedApk.makePaths(null, true, appInfo, zipPaths, libPaths);
        ClassLoader classLoader = new PathClassLoader(
                TextUtils.join(File.pathSeparator, zipPaths),
                TextUtils.join(File.pathSeparator, libPaths),
                getParentClassLoader());
        mClassLoaders.put(appInfo.packageName, classLoader);
        return classLoader;
    }

    private boolean clearClassLoader(String pkg) {
        return mClassLoaders.remove(pkg) != null;
    }

    ClassLoader getParentClassLoader() {
        if (mParentClassLoader == null) {
            // Lazily load this so it doesn't have any effect on devices without plugins.
            mParentClassLoader = new ClassLoaderFilter(getClass().getClassLoader(),
                    "com.android.systemui.plugin");
        }
        return mParentClassLoader;
    }

    public <T> boolean dependsOn(Plugin p, Class<T> cls) {
        synchronized (this) {
            for (int i = 0; i < mPluginMap.size(); i++) {
                if (mPluginMap.valueAt(i).dependsOn(p, cls)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void handleWtfs() {
        mPluginInitializer.handleWtfs();
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        synchronized (this) {
            pw.println(String.format("  plugin map (%d):", mPluginMap.size()));
            for (PluginListener listener : mPluginMap.keySet()) {
                pw.println(String.format("    %s -> %s",
                        listener, mPluginMap.get(listener)));
            }
        }
    }

    @VisibleForTesting
    public static class PluginInstanceManagerFactory {
        public <T extends Plugin> PluginInstanceManager createPluginInstanceManager(Context context,
                String action, PluginListener<T> listener, boolean allowMultiple, Looper looper,
                Class<?> cls, PluginManagerImpl manager) {
            return new PluginInstanceManager(context, action, listener, allowMultiple, looper,
                    new VersionInfo().addClass(cls), manager);
        }
    }

    private boolean isPluginPackageWhitelisted(String packageName) {
        for (String componentNameOrPackage : mWhitelistedPlugins) {
            ComponentName componentName = ComponentName.unflattenFromString(componentNameOrPackage);
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

    private boolean isPluginWhitelisted(ComponentName pluginName) {
        for (String componentNameOrPackage : mWhitelistedPlugins) {
            ComponentName componentName = ComponentName.unflattenFromString(componentNameOrPackage);
            if (componentName != null) {
                if (componentName.equals(pluginName)) {
                    return true;
                }
            } else if (componentNameOrPackage.equals(pluginName.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    // This allows plugins to include any libraries or copied code they want by only including
    // classes from the plugin library.
    private static class ClassLoaderFilter extends ClassLoader {
        private final String mPackage;
        private final ClassLoader mBase;

        public ClassLoaderFilter(ClassLoader base, String pkg) {
            super(ClassLoader.getSystemClassLoader());
            mBase = base;
            mPackage = pkg;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(mPackage)) super.loadClass(name, resolve);
            return mBase.loadClass(name);
        }
    }

    private class PluginExceptionHandler implements UncaughtExceptionHandler {
        private final UncaughtExceptionHandler mHandler;

        private PluginExceptionHandler(UncaughtExceptionHandler handler) {
            mHandler = handler;
        }

        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
            if (SystemProperties.getBoolean("plugin.debugging", false)) {
                mHandler.uncaughtException(thread, throwable);
                return;
            }
            // Search for and disable plugins that may have been involved in this crash.
            boolean disabledAny = checkStack(throwable);
            if (!disabledAny) {
                // We couldn't find any plugins involved in this crash, just to be safe
                // disable all the plugins, so we can be sure that SysUI is running as
                // best as possible.
                synchronized (this) {
                    for (PluginInstanceManager manager : mPluginMap.values()) {
                        disabledAny |= manager.disableAll();
                    }
                }
            }
            if (disabledAny) {
                throwable = new CrashWhilePluginActiveException(throwable);
            }

            // Run the normal exception handler so we can crash and cleanup our state.
            mHandler.uncaughtException(thread, throwable);
        }

        private boolean checkStack(Throwable throwable) {
            if (throwable == null) return false;
            boolean disabledAny = false;
            synchronized (this) {
                for (StackTraceElement element : throwable.getStackTrace()) {
                    for (PluginInstanceManager manager : mPluginMap.values()) {
                        disabledAny |= manager.checkAndDisable(element.getClassName());
                    }
                }
            }
            return disabledAny | checkStack(throwable.getCause());
        }
    }

    public static class CrashWhilePluginActiveException extends RuntimeException {
        public CrashWhilePluginActiveException(Throwable throwable) {
            super(throwable);
        }
    }
}
