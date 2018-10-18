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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.view.View;

import com.android.launcher3.R;
import com.android.systemui.shared.plugins.PluginEnabler;
import com.android.systemui.shared.plugins.PluginManager;
import com.android.systemui.shared.plugins.PluginPrefs;

import java.util.List;
import java.util.Set;

/**
 * This class is copied from System UI Tuner, except using our PluginEnablerImpl. The reason we
 * can't share a common base class in the shared lib is because the androidx preference dependency
 * interferes with our recyclerview and fragment dependencies.
 */
public class PluginPreferencesFragment extends PreferenceFragment {
    public static final String ACTION_PLUGIN_SETTINGS
            = "com.android.systemui.action.PLUGIN_SETTINGS";

    private static final String PLUGIN_PERMISSION = "com.android.systemui.permission.PLUGIN";

    private PluginPrefs mPluginPrefs;
    private PluginEnabler mPluginEnabler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        getContext().registerReceiver(mReceiver, filter);
        filter = new IntentFilter(Intent.ACTION_USER_UNLOCKED);
        getContext().registerReceiver(mReceiver, filter);

        mPluginEnabler = PluginManagerWrapper.INSTANCE.get(getContext()).getPluginEnabler();
        loadPrefs();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getContext().unregisterReceiver(mReceiver);
    }

    private void loadPrefs() {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getContext());
        screen.setOrderingAsAdded(false);
        Context prefContext = getContext();
        mPluginPrefs = new PluginPrefs(getContext());
        PackageManager pm = getContext().getPackageManager();

        Set<String> pluginActions = mPluginPrefs.getPluginList();
        ArrayMap<String, ArraySet<String>> plugins = new ArrayMap<>();
        for (String action : pluginActions) {
            String name = toName(action);
            List<ResolveInfo> result = pm.queryIntentServices(
                    new Intent(action), PackageManager.MATCH_DISABLED_COMPONENTS);
            for (ResolveInfo info : result) {
                String packageName = info.serviceInfo.packageName;
                if (!plugins.containsKey(packageName)) {
                    plugins.put(packageName, new ArraySet<>());
                }
                plugins.get(packageName).add(name);
            }
        }

        List<PackageInfo> apps = pm.getPackagesHoldingPermissions(new String[]{PLUGIN_PERMISSION},
                PackageManager.MATCH_DISABLED_COMPONENTS | PackageManager.GET_SERVICES);
        apps.forEach(app -> {
            if (!plugins.containsKey(app.packageName)) return;
            SwitchPreference pref = new PluginPreference(prefContext, app, mPluginEnabler);
            pref.setSummary("Plugins: " + toString(plugins.get(app.packageName)));
            screen.addPreference(pref);
        });
        setPreferenceScreen(screen);
    }

    private String toString(ArraySet<String> plugins) {
        StringBuilder b = new StringBuilder();
        for (String string : plugins) {
            if (b.length() != 0) {
                b.append(", ");
            }
            b.append(string);
        }
        return b.toString();
    }

    private String toName(String action) {
        String str = action.replace("com.android.systemui.action.PLUGIN_", "");
        StringBuilder b = new StringBuilder();
        for (String s : str.split("_")) {
            if (b.length() != 0) {
                b.append(' ');
            }
            b.append(s.substring(0, 1));
            b.append(s.substring(1).toLowerCase());
        }
        return b.toString();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadPrefs();
        }
    };

    private static class PluginPreference extends SwitchPreference {
        private final boolean mHasSettings;
        private final PackageInfo mInfo;
        private final PluginEnabler mPluginEnabler;

        public PluginPreference(Context prefContext, PackageInfo info, PluginEnabler pluginEnabler) {
            super(prefContext);
            PackageManager pm = prefContext.getPackageManager();
            mHasSettings = pm.resolveActivity(new Intent(ACTION_PLUGIN_SETTINGS)
                    .setPackage(info.packageName), 0) != null;
            mInfo = info;
            mPluginEnabler = pluginEnabler;
            setTitle(info.applicationInfo.loadLabel(pm));
            setChecked(isPluginEnabled());
            setWidgetLayoutResource(R.layout.switch_preference_with_settings);
        }

        private boolean isPluginEnabled() {
            for (int i = 0; i < mInfo.services.length; i++) {
                ComponentName componentName = new ComponentName(mInfo.packageName,
                        mInfo.services[i].name);
                if (!mPluginEnabler.isEnabled(componentName)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected boolean persistBoolean(boolean isEnabled) {
            boolean shouldSendBroadcast = false;
            for (int i = 0; i < mInfo.services.length; i++) {
                ComponentName componentName = new ComponentName(mInfo.packageName,
                        mInfo.services[i].name);

                if (mPluginEnabler.isEnabled(componentName) != isEnabled) {
                    mPluginEnabler.setEnabled(componentName, isEnabled);
                    shouldSendBroadcast = true;
                }
            }
            if (shouldSendBroadcast) {
                final String pkg = mInfo.packageName;
                final Intent intent = new Intent(PluginManager.PLUGIN_CHANGED,
                        pkg != null ? Uri.fromParts("package", pkg, null) : null);
                getContext().sendBroadcast(intent);
            }
            setChecked(isEnabled);
            return true;
        }

        @Override
        protected void onBindView(View view) {
            super.onBindView(view);
            view.findViewById(R.id.settings).setVisibility(mHasSettings ? View.VISIBLE
                    : View.GONE);
            view.findViewById(R.id.divider).setVisibility(mHasSettings ? View.VISIBLE
                    : View.GONE);
            view.findViewById(R.id.settings).setOnClickListener(v -> {
                ResolveInfo result = v.getContext().getPackageManager().resolveActivity(
                        new Intent(ACTION_PLUGIN_SETTINGS).setPackage(
                                mInfo.packageName), 0);
                if (result != null) {
                    v.getContext().startActivity(new Intent().setComponent(
                            new ComponentName(result.activityInfo.packageName,
                                    result.activityInfo.name)));
                }
            });
            view.setOnLongClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.fromParts("package", mInfo.packageName, null));
                getContext().startActivity(intent);
                return true;
            });
        }
    }
}
