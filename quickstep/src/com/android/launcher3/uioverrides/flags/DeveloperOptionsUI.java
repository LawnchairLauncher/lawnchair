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
package com.android.launcher3.uioverrides.flags;

import static com.android.launcher3.settings.SettingsActivity.EXTRA_FRAGMENT_HIGHLIGHT_KEY;
import static com.android.launcher3.util.OnboardingPrefs.ALL_APPS_VISITED_COUNT;
import static com.android.launcher3.util.OnboardingPrefs.HOME_BOUNCE_COUNT;
import static com.android.launcher3.util.OnboardingPrefs.HOME_BOUNCE_SEEN;
import static com.android.launcher3.util.OnboardingPrefs.HOTSEAT_DISCOVERY_TIP_COUNT;
import static com.android.launcher3.util.OnboardingPrefs.HOTSEAT_LONGPRESS_TIP_SEEN;
import static com.android.launcher3.util.OnboardingPrefs.TASKBAR_EDU_TOOLTIP_STEP;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.secondarydisplay.SecondaryDisplayLauncher;
import com.android.systemui.shared.plugins.PluginPrefs;

/**
 * Dev-build only UI allowing developers to toggle flag settings and plugins.
 * See {@link FeatureFlags}.
 */
public class DeveloperOptionsUI {

    private final PreferenceFragmentCompat mFragment;
    private final PreferenceScreen mPreferenceScreen;

    public DeveloperOptionsUI(PreferenceFragmentCompat fragment, PreferenceCategory flags) {
        mFragment = fragment;
        mPreferenceScreen = fragment.getPreferenceScreen();
        flags.getParent().removePreference(flags);

        // Add search bar
        View listView = mFragment.getListView();
        ViewGroup parent = (ViewGroup) listView.getParent();
        View topBar = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.developer_options_top_bar, parent, false);
        parent.addView(topBar, parent.indexOfChild(listView));
        initSearch(topBar.findViewById(R.id.filter_box));

        DevOptionsUiHelper uiHelper = new DevOptionsUiHelper();
        uiHelper.inflateServerFlags(newCategory("Server flags"));
        if (PluginPrefs.hasPlugins(getContext())) {
            uiHelper.inflatePluginPrefs(newCategory("Plugins"));
        }

        maybeAddSandboxCategory();
        addOnboardingPrefsCatergory();
    }

    private void filterPreferences(String query, PreferenceGroup pg) {
        int count = pg.getPreferenceCount();
        int hidden = 0;
        for (int i = 0; i < count; i++) {
            Preference preference = pg.getPreference(i);
            if (preference instanceof PreferenceGroup) {
                filterPreferences(query, (PreferenceGroup) preference);
            } else {
                String title = preference.getTitle().toString().toLowerCase().replace("_", " ");
                if (query.isEmpty() || title.contains(query)) {
                    preference.setVisible(true);
                } else {
                    preference.setVisible(false);
                    hidden++;
                }
            }
        }
        pg.setVisible(hidden != count);
    }

    private void initSearch(EditText filterBox) {
        filterBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void afterTextChanged(Editable editable) {
                String query = editable.toString().toLowerCase().replace("_", " ");
                filterPreferences(query, mPreferenceScreen);
            }
        });

        if (mFragment.getArguments() != null) {
            String filter = mFragment.getArguments().getString(EXTRA_FRAGMENT_HIGHLIGHT_KEY);
            // Normally EXTRA_FRAGMENT_ARG_KEY is used to highlight the preference with the given
            // key. This is a slight variation where we instead filter by the human-readable titles.
            if (filter != null) {
                filterBox.setText(filter);
            }
        }
    }

    private PreferenceCategory newCategory(String title) {
        PreferenceCategory category = new PreferenceCategory(getContext());
        category.setOrder(Preference.DEFAULT_ORDER);
        category.setTitle(title);
        mPreferenceScreen.addPreference(category);
        return category;
    }

    private Context getContext() {
        return mFragment.requireContext();
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
        Preference launchTutorialStepMenuPreference = new Preference(context);
        launchTutorialStepMenuPreference.setKey("launchTutorialStepMenu");
        launchTutorialStepMenuPreference.setTitle("Launch Gesture Tutorial Steps menu");
        launchTutorialStepMenuPreference.setSummary("Select a gesture tutorial step.");
        launchTutorialStepMenuPreference.setIntent(
                new Intent(launchSandboxIntent).putExtra("use_tutorial_menu", true));

        sandboxCategory.addPreference(launchTutorialStepMenuPreference);
        Preference launchOnboardingTutorialPreference = new Preference(context);
        launchOnboardingTutorialPreference.setKey("launchOnboardingTutorial");
        launchOnboardingTutorialPreference.setTitle("Launch Onboarding Tutorial");
        launchOnboardingTutorialPreference.setSummary("Learn the basic navigation gestures.");
        launchTutorialStepMenuPreference.setIntent(new Intent(launchSandboxIntent)
                .putExtra("use_tutorial_menu", false)
                .putExtra("tutorial_steps",
                        new String[] {
                                "HOME_NAVIGATION",
                                "BACK_NAVIGATION",
                                "OVERVIEW_NAVIGATION"}));

        sandboxCategory.addPreference(launchOnboardingTutorialPreference);
        Preference launchBackTutorialPreference = new Preference(context);
        launchBackTutorialPreference.setKey("launchBackTutorial");
        launchBackTutorialPreference.setTitle("Launch Back Tutorial");
        launchBackTutorialPreference.setSummary("Learn how to use the Back gesture");
        launchBackTutorialPreference.setIntent(new Intent(launchSandboxIntent)
                    .putExtra("use_tutorial_menu", false)
                    .putExtra("tutorial_steps", new String[] {"BACK_NAVIGATION"}));

        sandboxCategory.addPreference(launchBackTutorialPreference);
        Preference launchHomeTutorialPreference = new Preference(context);
        launchHomeTutorialPreference.setKey("launchHomeTutorial");
        launchHomeTutorialPreference.setTitle("Launch Home Tutorial");
        launchHomeTutorialPreference.setSummary("Learn how to use the Home gesture");
        launchHomeTutorialPreference.setIntent(new Intent(launchSandboxIntent)
                    .putExtra("use_tutorial_menu", false)
                    .putExtra("tutorial_steps", new String[] {"HOME_NAVIGATION"}));

        sandboxCategory.addPreference(launchHomeTutorialPreference);
        Preference launchOverviewTutorialPreference = new Preference(context);
        launchOverviewTutorialPreference.setKey("launchOverviewTutorial");
        launchOverviewTutorialPreference.setTitle("Launch Overview Tutorial");
        launchOverviewTutorialPreference.setSummary("Learn how to use the Overview gesture");
        launchOverviewTutorialPreference.setIntent(new Intent(launchSandboxIntent)
                    .putExtra("use_tutorial_menu", false)
                    .putExtra("tutorial_steps", new String[] {"OVERVIEW_NAVIGATION"}));

        sandboxCategory.addPreference(launchOverviewTutorialPreference);
        Preference launchSecondaryDisplayPreference = new Preference(context);
        launchSecondaryDisplayPreference.setKey("launchSecondaryDisplay");
        launchSecondaryDisplayPreference.setTitle("Launch Secondary Display");
        launchSecondaryDisplayPreference.setSummary("Launch secondary display activity");
        launchSecondaryDisplayPreference.setIntent(
                new Intent(context, SecondaryDisplayLauncher.class));

    }

    private void addOnboardingPrefsCatergory() {
        PreferenceCategory onboardingCategory = newCategory("Onboarding Flows");
        onboardingCategory.setSummary("Reset these if you want to see the education again.");

        onboardingCategory.addPreference(createOnboardPref("All Apps Bounce",
                HOME_BOUNCE_SEEN.getSharedPrefKey(), HOME_BOUNCE_COUNT.getSharedPrefKey()));
        onboardingCategory.addPreference(createOnboardPref("Hybrid Hotseat Education",
                HOTSEAT_DISCOVERY_TIP_COUNT.getSharedPrefKey(),
                HOTSEAT_LONGPRESS_TIP_SEEN.getSharedPrefKey()));
        onboardingCategory.addPreference(createOnboardPref("Taskbar Education",
                TASKBAR_EDU_TOOLTIP_STEP.getSharedPrefKey()));
        onboardingCategory.addPreference(createOnboardPref("All Apps Visited Count",
                ALL_APPS_VISITED_COUNT.getSharedPrefKey()));
    }

    private Preference createOnboardPref(String title, String... keys) {
        Preference onboardingPref = new Preference(getContext());
        onboardingPref.setTitle(title);
        onboardingPref.setSummary("Tap to reset");
        onboardingPref.setOnPreferenceClickListener(preference -> {
            SharedPreferences.Editor sharedPrefsEdit = LauncherPrefs.getPrefs(getContext())
                    .edit();
            for (String key : keys) {
                sharedPrefsEdit.remove(key);
            }
            sharedPrefsEdit.apply();
            Toast.makeText(getContext(), "Reset " + title, Toast.LENGTH_SHORT).show();
            return true;
        });
        return onboardingPref;
    }
}
