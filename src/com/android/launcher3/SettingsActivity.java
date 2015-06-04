/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;

/**
 * Settings activity for Launcher. Currently implements the following setting: Allow rotation
 */
public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new LauncherSettingsFragment())
                .commit();
    }

    /**
     * This fragment shows the launcher preferences.
     */
    @SuppressWarnings("WeakerAccess")
    public static class LauncherSettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesMode(Context.MODE_MULTI_PROCESS);
            getPreferenceManager().setSharedPreferencesName(LauncherFiles.ROTATION_PREF_FILE);
            addPreferencesFromResource(R.xml.launcher_preferences);
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
                                             Preference preference) {
            boolean allowRotation = getPreferenceManager().getSharedPreferences().getBoolean(
                    Utilities.ALLOW_ROTATION_PREFERENCE_KEY, false);
            Intent rotationSetting = new Intent(Utilities.SCREEN_ROTATION_SETTING_INTENT);
            String launchBroadcastPermission = getResources().getString(
                            R.string.receive_update_orientation_broadcasts_permission);
            rotationSetting.putExtra(Utilities.SCREEN_ROTATION_SETTING_EXTRA, allowRotation);
            getActivity().sendBroadcast(rotationSetting, launchBroadcastPermission);
            return true;
        }
    }
}
