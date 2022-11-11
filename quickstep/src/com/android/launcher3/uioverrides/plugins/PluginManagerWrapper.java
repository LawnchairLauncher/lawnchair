/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.launcher3.uioverrides.plugins;

import static android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS;

import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import com.android.launcher3.Utilities;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.systemui.plugins.Plugin;
import com.android.systemui.plugins.PluginListener;
import com.android.systemui.plugins.PluginManager;
import com.android.systemui.shared.plugins.PluginActionManager;
import com.android.systemui.shared.plugins.PluginInstance;
import com.android.systemui.shared.plugins.PluginManagerImpl;
import com.android.systemui.shared.plugins.PluginPrefs;
import com.android.systemui.shared.system.UncaughtExceptionPreHandlerManager;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PluginManagerWrapper {

    public static final MainThreadInitializedObject<PluginManagerWrapper> INSTANCE =
            new MainThreadInitializedObject<>(PluginManagerWrapper::new);

    public static final String PLUGIN_CHANGED = PluginManager.PLUGIN_CHANGED;

    private static final UncaughtExceptionPreHandlerManager UNCAUGHT_EXCEPTION_PRE_HANDLER_MANAGER =
            new UncaughtExceptionPreHandlerManager();

    private final Context mContext;
    private final PluginManager mPluginManager;
    private final PluginEnablerImpl mPluginEnabler;

    private PluginManagerWrapper(Context c) {
        mContext = c;
        mPluginEnabler = new PluginEnablerImpl(c);
        List<String> privilegedPlugins = Collections.emptyList();
        PluginInstance.Factory instanceFactory = new PluginInstance.Factory(
                getClass().getClassLoader(), new PluginInstance.InstanceFactory<>(),
                new PluginInstance.VersionChecker(), privilegedPlugins,
                Utilities.IS_DEBUG_DEVICE);
        PluginActionManager.Factory instanceManagerFactory = new PluginActionManager.Factory(
                c, c.getPackageManager(), c.getMainExecutor(), MODEL_EXECUTOR,
                c.getSystemService(NotificationManager.class), mPluginEnabler,
                privilegedPlugins, instanceFactory);

        mPluginManager = new PluginManagerImpl(c, instanceManagerFactory,
                Utilities.IS_DEBUG_DEVICE,
                UNCAUGHT_EXCEPTION_PRE_HANDLER_MANAGER, mPluginEnabler,
                new PluginPrefs(c), privilegedPlugins);
    }

    public PluginEnablerImpl getPluginEnabler() {
        return mPluginEnabler;
    }

    /** */
    public <T extends Plugin> void addPluginListener(
            PluginListener<T> listener, Class<T> pluginClass) {
        addPluginListener(listener, pluginClass, false);
    }

    /** */
    public <T extends Plugin> void addPluginListener(
            PluginListener<T> listener, Class<T> pluginClass, boolean allowMultiple) {
        mPluginManager.addPluginListener(listener, pluginClass, allowMultiple);
    }

    public void removePluginListener(PluginListener<? extends Plugin> listener) {
        mPluginManager.removePluginListener(listener);
    }

    public Set<String> getPluginActions() {
        return new PluginPrefs(mContext).getPluginList();
    }

    /**
     * Returns the string key used to store plugin enabled/disabled setting
     */
    public static String pluginEnabledKey(ComponentName cn) {
        return PluginEnablerImpl.pluginEnabledKey(cn);
    }

    public static boolean hasPlugins(Context context) {
        return PluginPrefs.hasPlugins(context);
    }

    public void dump(PrintWriter pw) {
        final List<ComponentName> enabledPlugins = new ArrayList<>();
        final List<ComponentName> disabledPlugins = new ArrayList<>();
        for (String action : getPluginActions()) {
            for (ResolveInfo resolveInfo : mContext.getPackageManager().queryIntentServices(
                    new Intent(action), MATCH_DISABLED_COMPONENTS)) {
                ComponentName installedPlugin = new ComponentName(
                        resolveInfo.serviceInfo.packageName, resolveInfo.serviceInfo.name);
                if (mPluginEnabler.isEnabled(installedPlugin)) {
                    enabledPlugins.add(installedPlugin);
                } else {
                    disabledPlugins.add(installedPlugin);
                }
            }
        }

        pw.println("PluginManager:");
        pw.println("  numEnabledPlugins=" + enabledPlugins.size());
        pw.println("  numDisabledPlugins=" + disabledPlugins.size());
        pw.println("  enabledPlugins=" + enabledPlugins);
        pw.println("  disabledPlugins=" + disabledPlugins);
    }
}
