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

import static android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS;

import static com.android.launcher3.uioverrides.plugins.PluginManagerWrapper.PLUGIN_CHANGED;
import static com.android.launcher3.uioverrides.plugins.PluginManagerWrapper.pluginEnabledKey;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreference;

import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.config.FlagTogglerPrefUi;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dev-build only UI allowing developers to toggle flag settings and plugins.
 * See {@link FeatureFlags}.
 */
@TargetApi(Build.VERSION_CODES.O)
public class DeveloperOptionsFragment extends PreferenceFragmentCompat {

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
        maybeAddSandboxCategory();
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

        ArrayMap<Pair<String, String>, ArrayList<Pair<String, ServiceInfo>>> plugins =
                new ArrayMap<>();

        Set<String> pluginPermissionApps = pm.getPackagesHoldingPermissions(
                new String[]{PLUGIN_PERMISSION}, MATCH_DISABLED_COMPONENTS)
                .stream()
                .map(pi -> pi.packageName)
                .collect(Collectors.toSet());

        for (String action : pluginActions) {
            String name = toName(action);
            List<ResolveInfo> result = pm.queryIntentServices(
                    new Intent(action), MATCH_DISABLED_COMPONENTS);
            for (ResolveInfo info : result) {
                String packageName = info.serviceInfo.packageName;
                if (!pluginPermissionApps.contains(packageName)) {
                    continue;
                }

                Pair<String, String> key = Pair.create(packageName, info.serviceInfo.processName);
                if (!plugins.containsKey(key)) {
                    plugins.put(key, new ArrayList<>());
                }
                plugins.get(key).add(Pair.create(name, info.serviceInfo));
            }
        }

        PreferenceDataStore enabler = manager.getPluginEnabler();
        plugins.forEach((key, si) -> {
            String packageName = key.first;
            List<ComponentName> componentNames = si.stream()
                    .map(p -> new ComponentName(packageName, p.second.name))
                    .collect(Collectors.toList());
            if (!componentNames.isEmpty()) {
                SwitchPreference pref = new PluginPreference(
                        prefContext, si.get(0).second.applicationInfo, enabler, componentNames);
                pref.setSummary("Plugins: "
                        + si.stream().map(p -> p.first).collect(Collectors.joining(", ")));
                mPluginsCategory.addPreference(pref);
            }
        });
    }

    private void maybeAddSandboxCategory() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        Intent launchSandboxIntent =
                new Intent("com.android.quickstep.action.GESTURE_SANDBOX")
                        .setPackage(context.getPackageName())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (launchSandboxIntent.resolveActivity(context.getPackageManager()) == null) {
            return;
        }
        PreferenceCategory sandboxCategory = newCategory("Gesture Navigation Sandbox");
        sandboxCategory.setSummary("Learn and practice navigation gestures");
        Preference launchBackTutorialPreference = new Preference(context);
        launchBackTutorialPreference.setKey("launchBackTutorial");
        launchBackTutorialPreference.setTitle("Launch Back Tutorial");
        launchBackTutorialPreference.setSummary("Learn how to use the Back gesture");
        launchBackTutorialPreference.setOnPreferenceClickListener(preference -> {
            startActivity(launchSandboxIntent.putExtra(
                    "tutorial_type", "RIGHT_EDGE_BACK_NAVIGATION"));
            return true;
        });
        sandboxCategory.addPreference(launchBackTutorialPreference);
        Preference launchHomeTutorialPreference = new Preference(context);
        launchHomeTutorialPreference.setKey("launchHomeTutorial");
        launchHomeTutorialPreference.setTitle("Launch Home Tutorial");
        launchHomeTutorialPreference.setSummary("Learn how to use the Home gesture");
        launchHomeTutorialPreference.setOnPreferenceClickListener(preference -> {
            startActivity(launchSandboxIntent.putExtra("tutorial_type", "HOME_NAVIGATION"));
            return true;
        });
        sandboxCategory.addPreference(launchHomeTutorialPreference);
        Preference launchOverviewTutorialPreference = new Preference(context);
        launchOverviewTutorialPreference.setKey("launchOverviewTutorial");
        launchOverviewTutorialPreference.setTitle("Launch Overview Tutorial");
        launchOverviewTutorialPreference.setSummary("Learn how to use the Overview gesture");
        launchOverviewTutorialPreference.setOnPreferenceClickListener(preference -> {
            startActivity(launchSandboxIntent.putExtra("tutorial_type", "OVERVIEW_NAVIGATION"));
            return true;
        });
        sandboxCategory.addPreference(launchOverviewTutorialPreference);
        Preference launchAssistantTutorialPreference = new Preference(context);
        launchAssistantTutorialPreference.setKey("launchAssistantTutorial");
        launchAssistantTutorialPreference.setTitle("Launch Assistant Tutorial");
        launchAssistantTutorialPreference.setSummary("Learn how to use the Assistant gesture");
        launchAssistantTutorialPreference.setOnPreferenceClickListener(preference -> {
            startActivity(launchSandboxIntent.putExtra("tutorial_type", "ASSISTANT"));
            return true;
        });
        sandboxCategory.addPreference(launchAssistantTutorialPreference);
    }

    private String toName(String action) {
        String str = action.replace("com.android.systemui.action.PLUGIN_", "")
                .replace("com.android.launcher3.action.PLUGIN_", "");
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
        private final PreferenceDataStore mPluginEnabler;
        private final String mPackageName;
        private final List<ComponentName> mComponentNames;

        PluginPreference(Context prefContext, ApplicationInfo info,
                PreferenceDataStore pluginEnabler, List<ComponentName> componentNames) {
            super(prefContext);
            PackageManager pm = prefContext.getPackageManager();
            mHasSettings = pm.resolveActivity(new Intent(ACTION_PLUGIN_SETTINGS)
                    .setPackage(info.packageName), 0) != null;
            mPackageName = info.packageName;
            mComponentNames = componentNames;
            mPluginEnabler = pluginEnabler;
            setTitle(info.loadLabel(pm));
            setChecked(isPluginEnabled());
            setWidgetLayoutResource(R.layout.switch_preference_with_settings);
        }

        private boolean isEnabled(ComponentName cn) {
            return mPluginEnabler.getBoolean(pluginEnabledKey(cn), true);

        }

        private boolean isPluginEnabled() {
            for (ComponentName componentName : mComponentNames) {
                if (!isEnabled(componentName)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected boolean persistBoolean(boolean isEnabled) {
            boolean shouldSendBroadcast = false;
            for (ComponentName componentName : mComponentNames) {
                if (isEnabled(componentName) != isEnabled) {
                    mPluginEnabler.putBoolean(pluginEnabledKey(componentName), isEnabled);
                    shouldSendBroadcast = true;
                }
            }
            if (shouldSendBroadcast) {
                final String pkg = mPackageName;
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
                        new Intent(ACTION_PLUGIN_SETTINGS).setPackage(mPackageName), 0);
                if (result != null) {
                    v.getContext().startActivity(new Intent().setComponent(
                            new ComponentName(result.activityInfo.packageName,
                                    result.activityInfo.name)));
                }
            });
            holder.itemView.setOnLongClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.fromParts("package", mPackageName, null));
                getContext().startActivity(intent);
                return true;
            });
        }
    }
}
