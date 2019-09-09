/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.settings;

import static com.android.launcher3.uioverrides.plugins.PluginManagerWrapper.PLUGIN_CHANGED;
import static com.android.launcher3.uioverrides.plugins.PluginManagerWrapper.pluginEnabledKey;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.config.FlagTogglerPrefUi;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;

import java.util.List;
import java.util.Set;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreference;

/**
 * Dev-build only UI allowing developers to toggle flag settings and plugins.
 * See {@link FeatureFlags}.
 */
@TargetApi(Build.VERSION_CODES.O)
public class DeveloperOptionsFragment extends PreferenceFragment {

    private static final String ACTION_PLUGIN_SETTINGS = "com.android.systemui.action.PLUGIN_SETTINGS";
    private static final String PLUGIN_PERMISSION = "com.android.systemui.permission.PLUGIN";

    private final BroadcastReceiver mPluginReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadPluginPrefs();
        }
    };

    private PreferenceScreen mPreferenceScreen;

    private PreferenceCategory mPluginsCategory;
    private FlagTogglerPrefUi mFlagTogglerPrefUi;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        getContext().registerReceiver(mPluginReceiver, filter);
        getContext().registerReceiver(mPluginReceiver,
                new IntentFilter(Intent.ACTION_USER_UNLOCKED));

        mPreferenceScreen = getPreferenceManager().createPreferenceScreen(getContext());
        setPreferenceScreen(mPreferenceScreen);

        initFlags();
        loadPluginPrefs();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getContext().unregisterReceiver(mPluginReceiver);
    }

    private PreferenceCategory newCategory(String title) {
        PreferenceCategory category = new PreferenceCategory(getContext());
        category.setOrder(Preference.DEFAULT_ORDER);
        category.setTitle(title);
        mPreferenceScreen.addPreference(category);
        return category;
    }

    private void initFlags() {
        if (!FeatureFlags.showFlagTogglerUi(getContext())) {
            return;
        }

        mFlagTogglerPrefUi = new FlagTogglerPrefUi(this);
        mFlagTogglerPrefUi.applyTo(newCategory("Feature flags"));
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (mFlagTogglerPrefUi != null) {
            mFlagTogglerPrefUi.onCreateOptionsMenu(menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mFlagTogglerPrefUi != null) {
            mFlagTogglerPrefUi.onOptionsItemSelected(item);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStop() {
        if (mFlagTogglerPrefUi != null) {
            mFlagTogglerPrefUi.onStop();
        }
        super.onStop();
    }

    private void loadPluginPrefs() {
        if (mPluginsCategory != null) {
            mPreferenceScreen.removePreference(mPluginsCategory);
        }
        if (!PluginManagerWrapper.hasPlugins(getActivity())) {
            mPluginsCategory = null;
            return;
        }
        mPluginsCategory = newCategory("Plugins");

        PluginManagerWrapper manager = PluginManagerWrapper.INSTANCE.get(getContext());
        Context prefContext = getContext();
        PackageManager pm = getContext().getPackageManager();

        Set<String> pluginActions = manager.getPluginActions();
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
        PreferenceDataStore enabled = manager.getPluginEnabler();
        apps.forEach(app -> {
            if (!plugins.containsKey(app.packageName)) return;
            SwitchPreference pref = new PluginPreference(prefContext, app, enabled);
            pref.setSummary("Plugins: " + toString(plugins.get(app.packageName)));
            mPluginsCategory.addPreference(pref);
        });
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

    private static class PluginPreference extends SwitchPreference {
        private final boolean mHasSettings;
        private final PackageInfo mInfo;
        private final PreferenceDataStore mPluginEnabler;

        public PluginPreference(Context prefContext, PackageInfo info,
                PreferenceDataStore pluginEnabler) {
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

        private boolean isEnabled(ComponentName cn) {
            return mPluginEnabler.getBoolean(pluginEnabledKey(cn), true);

        }

        private boolean isPluginEnabled() {
            for (int i = 0; i < mInfo.services.length; i++) {
                ComponentName componentName = new ComponentName(mInfo.packageName,
                        mInfo.services[i].name);
                if (!isEnabled(componentName)) {
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

                if (isEnabled(componentName) != isEnabled) {
                    mPluginEnabler.putBoolean(pluginEnabledKey(componentName), isEnabled);
                    shouldSendBroadcast = true;
                }
            }
            if (shouldSendBroadcast) {
                final String pkg = mInfo.packageName;
                final Intent intent = new Intent(PLUGIN_CHANGED,
                        pkg != null ? Uri.fromParts("package", pkg, null) : null);
                getContext().sendBroadcast(intent);
            }
            setChecked(isEnabled);
            return true;
        }

        @Override
        public void onBindViewHolder(PreferenceViewHolder holder) {
            super.onBindViewHolder(holder);
            holder.findViewById(R.id.settings).setVisibility(mHasSettings ? View.VISIBLE
                    : View.GONE);
            holder.findViewById(R.id.divider).setVisibility(mHasSettings ? View.VISIBLE
                    : View.GONE);
            holder.findViewById(R.id.settings).setOnClickListener(v -> {
                ResolveInfo result = v.getContext().getPackageManager().resolveActivity(
                        new Intent(ACTION_PLUGIN_SETTINGS).setPackage(
                                mInfo.packageName), 0);
                if (result != null) {
                    v.getContext().startActivity(new Intent().setComponent(
                            new ComponentName(result.activityInfo.packageName,
                                    result.activityInfo.name)));
                }
            });
            holder.itemView.setOnLongClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.fromParts("package", mInfo.packageName, null));
                getContext().startActivity(intent);
                return true;
            });
        }
    }
}
