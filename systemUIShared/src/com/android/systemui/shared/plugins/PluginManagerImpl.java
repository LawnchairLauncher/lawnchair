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

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.SystemProperties;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.systemui.plugins.Plugin;
import com.android.systemui.plugins.PluginListener;
import com.android.systemui.plugins.PluginManager;
import com.android.systemui.shared.system.UncaughtExceptionPreHandlerManager;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.List;
import java.util.Map;

/**
 * @see Plugin
 */
public class PluginManagerImpl extends BroadcastReceiver implements PluginManager {

    private static final String TAG = PluginManagerImpl.class.getSimpleName();
    static final String DISABLE_PLUGIN = "com.android.systemui.action.DISABLE_PLUGIN";

    private final ArrayMap<PluginListener<?>, PluginActionManager<?>> mPluginMap
            = new ArrayMap<>();
    private final Map<String, ClassLoader> mClassLoaders = new ArrayMap<>();
    private final ArraySet<String> mPrivilegedPlugins = new ArraySet<>();
    private final Context mContext;
    private final PluginActionManager.Factory mActionManagerFactory;
    private final boolean mIsDebuggable;
    private final PluginPrefs mPluginPrefs;
    private final PluginEnabler mPluginEnabler;
    private boolean mListening;

    public PluginManagerImpl(Context context,
            PluginActionManager.Factory actionManagerFactory,
            boolean debuggable,
            UncaughtExceptionPreHandlerManager preHandlerManager,
            PluginEnabler pluginEnabler,
            PluginPrefs pluginPrefs,
            List<String> privilegedPlugins) {
        mContext = context;
        mActionManagerFactory = actionManagerFactory;
        mIsDebuggable = debuggable;
        mPrivilegedPlugins.addAll(privilegedPlugins);
        mPluginPrefs = pluginPrefs;
        mPluginEnabler = pluginEnabler;

        preHandlerManager.registerHandler(new PluginExceptionHandler());
    }

    public boolean isDebuggable() {
        return mIsDebuggable;
    }

    public String[] getPrivilegedPlugins() {
        return mPrivilegedPlugins.toArray(new String[0]);
    }

    /** */
    public <T extends Plugin> void addPluginListener(PluginListener<T> listener, Class<T> cls) {
        addPluginListener(listener, cls, false);
    }

    /** */
    public <T extends Plugin> void addPluginListener(PluginListener<T> listener, Class<T> cls,
            boolean allowMultiple) {
        addPluginListener(PluginManager.Helper.getAction(cls), listener, cls, allowMultiple);
    }

    public <T extends Plugin> void addPluginListener(String action, PluginListener<T> listener,
            Class<T> cls) {
        addPluginListener(action, listener, cls, false);
    }

    public <T extends Plugin> void addPluginListener(String action, PluginListener<T> listener,
            Class<T> cls, boolean allowMultiple) {
        mPluginPrefs.addAction(action);
        PluginActionManager<T> p = mActionManagerFactory.create(action, listener, cls,
                allowMultiple, isDebuggable());
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
        filter.addDataScheme("package");
        mContext.registerReceiver(this, filter);
        filter.addAction(PLUGIN_CHANGED);
        filter.addAction(DISABLE_PLUGIN);
        filter.addDataScheme("package");
        mContext.registerReceiver(this, filter, PluginActionManager.PLUGIN_PERMISSION, null,
                Context.RECEIVER_EXPORTED_UNAUDITED);
        filter = new IntentFilter(Intent.ACTION_USER_UNLOCKED);
        mContext.registerReceiver(this, filter);
    }

    private void stopListening() {
        if (!mListening) return;
        mListening = false;
        mContext.unregisterReceiver(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_USER_UNLOCKED.equals(intent.getAction())) {
            synchronized (this) {
                for (PluginActionManager<?> manager : mPluginMap.values()) {
                    manager.loadAll();
                }
            }
        } else if (DISABLE_PLUGIN.equals(intent.getAction())) {
            Uri uri = intent.getData();
            ComponentName component = ComponentName.unflattenFromString(
                    uri.toString().substring(10));
            if (isPluginPrivileged(component)) {
                // Don't disable privileged plugins as they are a part of the OS.
                return;
            }
            mPluginEnabler.setDisabled(component, PluginEnabler.DISABLED_INVALID_VERSION);
            mContext.getSystemService(NotificationManager.class).cancel(component.getClassName(),
                    SystemMessage.NOTE_PLUGIN);
        } else {
            Uri data = intent.getData();
            String pkg = data.getEncodedSchemeSpecificPart();
            ComponentName componentName = ComponentName.unflattenFromString(pkg);
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
                        mPluginEnabler.getDisableReason(componentName);
                if (disableReason == PluginEnabler.DISABLED_FROM_EXPLICIT_CRASH
                        || disableReason == PluginEnabler.DISABLED_FROM_SYSTEM_CRASH
                        || disableReason == PluginEnabler.DISABLED_INVALID_VERSION) {
                    Log.i(TAG, "Re-enabling previously disabled plugin that has been "
                            + "updated: " + componentName.flattenToShortString());
                    mPluginEnabler.setEnabled(componentName);
                }
            }
            synchronized (this) {
                if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())
                        || Intent.ACTION_PACKAGE_CHANGED.equals(intent.getAction())
                        || Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction())) {
                    for (PluginActionManager<?> actionManager : mPluginMap.values()) {
                        actionManager.reloadPackage(pkg);
                    }
                } else {
                    for (PluginActionManager<?> manager : mPluginMap.values()) {
                        manager.onPackageRemoved(pkg);
                    }
                }
            }
        }
    }

    private boolean clearClassLoader(String pkg) {
        return mClassLoaders.remove(pkg) != null;
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

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        synchronized (this) {
            pw.println(String.format("  plugin map (%d):", mPluginMap.size()));
            for (PluginListener<?> listener : mPluginMap.keySet()) {
                pw.println(String.format("    %s -> %s",
                        listener, mPluginMap.get(listener)));
            }
        }
    }

    private boolean isPluginPrivileged(ComponentName pluginName) {
        for (String componentNameOrPackage : mPrivilegedPlugins) {
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
    static class ClassLoaderFilter extends ClassLoader {
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

        private PluginExceptionHandler() {}

        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
            if (SystemProperties.getBoolean("plugin.debugging", false)) {
                return;
            }
            // Search for and disable plugins that may have been involved in this crash.
            boolean disabledAny = checkStack(throwable);
            if (!disabledAny) {
                // We couldn't find any plugins involved in this crash, just to be safe
                // disable all the plugins, so we can be sure that SysUI is running as
                // best as possible.
                synchronized (this) {
                    for (PluginActionManager<?> manager : mPluginMap.values()) {
                        disabledAny |= manager.disableAll();
                    }
                }
            }
            if (disabledAny) {
                throwable = new CrashWhilePluginActiveException(throwable);
            }
        }

        private boolean checkStack(Throwable throwable) {
            if (throwable == null) return false;
            boolean disabledAny = false;
            synchronized (this) {
                for (StackTraceElement element : throwable.getStackTrace()) {
                    for (PluginActionManager<?> manager : mPluginMap.values()) {
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
