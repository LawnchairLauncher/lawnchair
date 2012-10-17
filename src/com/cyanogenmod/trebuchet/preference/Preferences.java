/*
 * Copyright (C) 2011 The CyanogenMod Project
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

package com.cyanogenmod.trebuchet.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;

import com.cyanogenmod.trebuchet.LauncherApplication;
import com.cyanogenmod.trebuchet.R;

public class Preferences extends PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "Launcher.Preferences";

    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        if (LauncherApplication.isScreenLarge()) {
            findPreference("ui_dock").setEnabled(false);
        }

        mPrefs = getSharedPreferences(PreferencesProvider.PREFERENCES_KEY,
                Context.MODE_PRIVATE);

        Preference version = findPreference("application_version");
        version.setTitle(getString(R.string.application_name) + " " + getString(R.string.application_version));
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(PreferencesProvider.PREFERENCES_CHANGED, true);
        editor.commit();
    }

}
