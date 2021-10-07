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

import android.app.Notification;
import android.app.Notification.Action;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.ArraySet;
import android.util.Log;
import android.view.LayoutInflater;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.systemui.plugins.Plugin;
import com.android.systemui.plugins.PluginFragment;
import com.android.systemui.plugins.PluginListener;
import com.android.systemui.shared.plugins.VersionInfo.InvalidVersionException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PluginInstanceManager<T extends Plugin> {

    private static final boolean DEBUG = false;

    private static final String TAG = "PluginInstanceManager";
    public static final String PLUGIN_PERMISSION = "com.android.systemui.permission.PLUGIN";

    private final Context mContext;
    private final PluginListener<T> mListener;
    private final String mAction;
    private final boolean mAllowMultiple;
    private final VersionInfo mVersion;

    @VisibleForTesting
    final MainHandler mMainHandler;
    @VisibleForTesting
    final PluginHandler mPluginHandler;
    private final boolean isDebuggable;
    private final PackageManager mPm;
    private final PluginManagerImpl mManager;
    private final ArraySet<String> mWhitelistedPlugins = new ArraySet<>();

    PluginInstanceManager(Context context, String action, PluginListener<T> listener,
            boolean allowMultiple, Looper looper, VersionInfo version, PluginManagerImpl manager) {
        this(context, context.getPackageManager(), action, listener, allowMultiple, looper, version,
                manager, manager.isDebuggable(), manager.getWhitelistedPlugins());
    }

    @VisibleForTesting
    PluginInstanceManager(Context context, PackageManager pm, String action,
            PluginListener<T> listener, boolean allowMultiple, Looper looper, VersionInfo version,
            PluginManagerImpl manager, boolean debuggable, String[] pluginWhitelist) {
        mMainHandler = new MainHandler(Looper.getMainLooper());
        mPluginHandler = new PluginHandler(looper);
        mManager = manager;
        mContext = context;
        mPm = pm;
        mAction = action;
        mListener = listener;
        mAllowMultiple = allowMultiple;
        mVersion = version;
        mWhitelistedPlugins.addAll(Arrays.asList(pluginWhitelist));
        isDebuggable = debuggable;
    }

    public PluginInfo<T> getPlugin() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new RuntimeException("Must be called from UI thread");
        }
        mPluginHandler.handleQueryPlugins(null /* All packages */);
        if (mPluginHandler.mPlugins.size() > 0) {
            mMainHandler.removeMessages(MainHandler.PLUGIN_CONNECTED);
            PluginInfo<T> info = mPluginHandler.mPlugins.get(0);
            PluginPrefs.setHasPlugins(mContext);
            info.mPlugin.onCreate(mContext, info.mPluginContext);
            return info;
        }
        return null;
    }

    public void loadAll() {
        if (DEBUG) Log.d(TAG, "startListening");
        mPluginHandler.sendEmptyMessage(PluginHandler.QUERY_ALL);
    }

    public void destroy() {
        if (DEBUG) Log.d(TAG, "stopListening");
        ArrayList<PluginInfo> plugins = new ArrayList<PluginInfo>(mPluginHandler.mPlugins);
        for (PluginInfo plugin : plugins) {
            mMainHandler.obtainMessage(MainHandler.PLUGIN_DISCONNECTED,
                    plugin.mPlugin).sendToTarget();
        }
    }

    public void onPackageRemoved(String pkg) {
        mPluginHandler.obtainMessage(PluginHandler.REMOVE_PKG, pkg).sendToTarget();
    }

    public void onPackageChange(String pkg) {
        mPluginHandler.obtainMessage(PluginHandler.REMOVE_PKG, pkg).sendToTarget();
        mPluginHandler.obtainMessage(PluginHandler.QUERY_PKG, pkg).sendToTarget();
    }

    public boolean checkAndDisable(String className) {
        boolean disableAny = false;
        ArrayList<PluginInfo> plugins = new ArrayList<PluginInfo>(mPluginHandler.mPlugins);
        for (PluginInfo info : plugins) {
            if (className.startsWith(info.mPackage)) {
                disableAny |= disable(info, PluginEnabler.DISABLED_FROM_EXPLICIT_CRASH);
            }
        }
        return disableAny;
    }

    public boolean disableAll() {
        ArrayList<PluginInfo> plugins = new ArrayList<PluginInfo>(mPluginHandler.mPlugins);
        boolean disabledAny = false;
        for (int i = 0; i < plugins.size(); i++) {
            disabledAny |= disable(plugins.get(i), PluginEnabler.DISABLED_FROM_SYSTEM_CRASH);
        }
        return disabledAny;
    }

    private boolean isPluginWhitelisted(ComponentName pluginName) {
        for (String componentNameOrPackage : mWhitelistedPlugins) {
            ComponentName componentName = ComponentName.unflattenFromString(componentNameOrPackage);
            if (componentName == null) {
                if (componentNameOrPackage.equals(pluginName.getPackageName())) {
                    return true;
                }
            } else {
                if (componentName.equals(pluginName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean disable(PluginInfo info, @PluginEnabler.DisableReason int reason) {
        // Live by the sword, die by the sword.
        // Misbehaving plugins get disabled and won't come back until uninstall/reinstall.

        ComponentName pluginComponent = new ComponentName(info.mPackage, info.mClass);
        // If a plugin is detected in the stack of a crash then this will be called for that
        // plugin, if the plugin causing a crash cannot be identified, they are all disabled
        // assuming one of them must be bad.
        if (isPluginWhitelisted(pluginComponent)) {
            // Don't disable whitelisted plugins as they are a part of the OS.
            return false;
        }
        Log.w(TAG, "Disabling plugin " + pluginComponent.flattenToShortString());
        mManager.getPluginEnabler().setDisabled(pluginComponent, reason);

        return true;
    }

    public <T> boolean dependsOn(Plugin p, Class<T> cls) {
        ArrayList<PluginInfo> plugins = new ArrayList<PluginInfo>(mPluginHandler.mPlugins);
        for (PluginInfo info : plugins) {
            if (info.mPlugin.getClass().getName().equals(p.getClass().getName())) {
                return info.mVersion != null && info.mVersion.hasClass(cls);
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("%s@%s (action=%s)",
                getClass().getSimpleName(), hashCode(), mAction);
    }

    private class MainHandler extends Handler {
        private static final int PLUGIN_CONNECTED = 1;
        private static final int PLUGIN_DISCONNECTED = 2;

        public MainHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case PLUGIN_CONNECTED:
                    if (DEBUG) Log.d(TAG, "onPluginConnected");
                    PluginPrefs.setHasPlugins(mContext);
                    PluginInfo<T> info = (PluginInfo<T>) msg.obj;
                    mManager.handleWtfs();
                    if (!(msg.obj instanceof PluginFragment)) {
                        // Only call onDestroy for plugins that aren't fragments, as fragments
                        // will get the onCreate as part of the fragment lifecycle.
                        info.mPlugin.onCreate(mContext, info.mPluginContext);
                    }
                    mListener.onPluginConnected(info.mPlugin, info.mPluginContext);
                    break;
                case PLUGIN_DISCONNECTED:
                    if (DEBUG) Log.d(TAG, "onPluginDisconnected");
                    mListener.onPluginDisconnected((T) msg.obj);
                    if (!(msg.obj instanceof PluginFragment)) {
                        // Only call onDestroy for plugins that aren't fragments, as fragments
                        // will get the onDestroy as part of the fragment lifecycle.
                        ((T) msg.obj).onDestroy();
                    }
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }

    private class PluginHandler extends Handler {
        private static final int QUERY_ALL = 1;
        private static final int QUERY_PKG = 2;
        private static final int REMOVE_PKG = 3;

        private final ArrayList<PluginInfo<T>> mPlugins = new ArrayList<>();

        public PluginHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case QUERY_ALL:
                    if (DEBUG) Log.d(TAG, "queryAll " + mAction);
                    for (int i = mPlugins.size() - 1; i >= 0; i--) {
                        PluginInfo<T> pluginInfo = mPlugins.get(i);
                        mMainHandler.obtainMessage(
                                MainHandler.PLUGIN_DISCONNECTED, pluginInfo.mPlugin).sendToTarget();
                    }
                    mPlugins.clear();
                    handleQueryPlugins(null);
                    break;
                case REMOVE_PKG:
                    String pkg = (String) msg.obj;
                    for (int i = mPlugins.size() - 1; i >= 0; i--) {
                        final PluginInfo<T> plugin = mPlugins.get(i);
                        if (plugin.mPackage.equals(pkg)) {
                            mMainHandler.obtainMessage(MainHandler.PLUGIN_DISCONNECTED,
                                    plugin.mPlugin).sendToTarget();
                            mPlugins.remove(i);
                        }
                    }
                    break;
                case QUERY_PKG:
                    String p = (String) msg.obj;
                    if (DEBUG) Log.d(TAG, "queryPkg " + p + " " + mAction);
                    if (mAllowMultiple || (mPlugins.size() == 0)) {
                        handleQueryPlugins(p);
                    } else {
                        if (DEBUG) Log.d(TAG, "Too many of " + mAction);
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }

        private void handleQueryPlugins(String pkgName) {
            // This isn't actually a service and shouldn't ever be started, but is
            // a convenient PM based way to manage our plugins.
            Intent intent = new Intent(mAction);
            if (pkgName != null) {
                intent.setPackage(pkgName);
            }
            List<ResolveInfo> result = mPm.queryIntentServices(intent, 0);
            if (DEBUG) Log.d(TAG, "Found " + result.size() + " plugins");
            if (result.size() > 1 && !mAllowMultiple) {
                // TODO: Show warning.
                Log.w(TAG, "Multiple plugins found for " + mAction);
                if (DEBUG) {
                    for (ResolveInfo info : result) {
                        ComponentName name = new ComponentName(info.serviceInfo.packageName,
                                info.serviceInfo.name);
                        Log.w(TAG, "  " + name);
                    }
                }
                return;
            }
            for (ResolveInfo info : result) {
                ComponentName name = new ComponentName(info.serviceInfo.packageName,
                        info.serviceInfo.name);
                PluginInfo<T> t = handleLoadPlugin(name);
                if (t == null) continue;

                // add plugin before sending PLUGIN_CONNECTED message
                mPlugins.add(t);
                mMainHandler.obtainMessage(mMainHandler.PLUGIN_CONNECTED, t).sendToTarget();
            }
        }

        protected PluginInfo<T> handleLoadPlugin(ComponentName component) {
            // This was already checked, but do it again here to make extra extra sure, we don't
            // use these on production builds.
            if (!isDebuggable && !isPluginWhitelisted(component)) {
                // Never ever ever allow these on production builds, they are only for prototyping.
                Log.w(TAG, "Plugin cannot be loaded on production build: " + component);
                return null;
            }
            if (!mManager.getPluginEnabler().isEnabled(component)) {
                if (DEBUG) Log.d(TAG, "Plugin is not enabled, aborting load: " + component);
                return null;
            }
            String pkg = component.getPackageName();
            String cls = component.getClassName();
            try {
                ApplicationInfo info = mPm.getApplicationInfo(pkg, 0);
                // TODO: This probably isn't needed given that we don't have IGNORE_SECURITY on
                if (mPm.checkPermission(PLUGIN_PERMISSION, pkg)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Plugin doesn't have permission: " + pkg);
                    return null;
                }
                // Create our own ClassLoader so we can use our own code as the parent.
                ClassLoader classLoader = mManager.getClassLoader(info);
                Context pluginContext = new PluginContextWrapper(
                        mContext.createApplicationContext(info, 0), classLoader);
                Class<?> pluginClass = Class.forName(cls, true, classLoader);
                // TODO: Only create the plugin before version check if we need it for
                // legacy version check.
                T plugin = (T) pluginClass.newInstance();
                try {
                    VersionInfo version = checkVersion(pluginClass, plugin, mVersion);
                    if (DEBUG) Log.d(TAG, "createPlugin");
                    return new PluginInfo(pkg, cls, plugin, pluginContext, version);
                } catch (InvalidVersionException e) {
                    final int icon = Resources.getSystem().getIdentifier(
                            "stat_sys_warning", "drawable", "android");
                    final int color = Resources.getSystem().getIdentifier(
                            "system_notification_accent_color", "color", "android");
                    final Notification.Builder nb = new Notification.Builder(mContext,
                            PluginManager.NOTIFICATION_CHANNEL_ID)
                                    .setStyle(new Notification.BigTextStyle())
                                    .setSmallIcon(icon)
                                    .setWhen(0)
                                    .setShowWhen(false)
                                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                                    .setColor(mContext.getColor(color));
                    String label = cls;
                    try {
                        label = mPm.getServiceInfo(component, 0).loadLabel(mPm).toString();
                    } catch (NameNotFoundException e2) {
                    }
                    if (!e.isTooNew()) {
                        // Localization not required as this will never ever appear in a user build.
                        nb.setContentTitle("Plugin \"" + label + "\" is too old")
                                .setContentText("Contact plugin developer to get an updated"
                                        + " version.\n" + e.getMessage());
                    } else {
                        // Localization not required as this will never ever appear in a user build.
                        nb.setContentTitle("Plugin \"" + label + "\" is too new")
                                .setContentText("Check to see if an OTA is available.\n"
                                        + e.getMessage());
                    }
                    Intent i = new Intent(PluginManagerImpl.DISABLE_PLUGIN).setData(
                            Uri.parse("package://" + component.flattenToString()));
                    PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, i,
                            PendingIntent.FLAG_IMMUTABLE);
                    nb.addAction(new Action.Builder(null, "Disable plugin", pi).build());
                    mContext.getSystemService(NotificationManager.class)
                            .notify(SystemMessage.NOTE_PLUGIN, nb.build());
                    // TODO: Warn user.
                    Log.w(TAG, "Plugin has invalid interface version " + plugin.getVersion()
                            + ", expected " + mVersion);
                    return null;
                }
            } catch (Throwable e) {
                Log.w(TAG, "Couldn't load plugin: " + pkg, e);
                return null;
            }
        }

        private VersionInfo checkVersion(Class<?> pluginClass, T plugin, VersionInfo version)
                throws InvalidVersionException {
            VersionInfo pv = new VersionInfo().addClass(pluginClass);
            if (pv.hasVersionInfo()) {
                version.checkVersion(pv);
            } else {
                int fallbackVersion = plugin.getVersion();
                if (fallbackVersion != version.getDefaultVersion()) {
                    throw new InvalidVersionException("Invalid legacy version", false);
                }
                return null;
            }
            return pv;
        }
    }

    public static class PluginContextWrapper extends ContextWrapper {
        private final ClassLoader mClassLoader;
        private LayoutInflater mInflater;

        public PluginContextWrapper(Context base, ClassLoader classLoader) {
            super(base);
            mClassLoader = classLoader;
        }

        @Override
        public ClassLoader getClassLoader() {
            return mClassLoader;
        }

        @Override
        public Object getSystemService(String name) {
            if (LAYOUT_INFLATER_SERVICE.equals(name)) {
                if (mInflater == null) {
                    mInflater = LayoutInflater.from(getBaseContext()).cloneInContext(this);
                }
                return mInflater;
            }
            return getBaseContext().getSystemService(name);
        }
    }

    static class PluginInfo<T> {
        private final Context mPluginContext;
        private final VersionInfo mVersion;
        private String mClass;
        T mPlugin;
        String mPackage;

        public PluginInfo(String pkg, String cls, T plugin, Context pluginContext,
                VersionInfo info) {
            mPlugin = plugin;
            mClass = cls;
            mPackage = pkg;
            mPluginContext = pluginContext;
            mVersion = info;
        }
    }
}
