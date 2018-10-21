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

package com.android.launcher3.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Process;
import android.preference.PreferenceDataStore;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.launcher3.R;
import com.android.launcher3.config.BaseFlags.TogglableFlag;

/**
 * Dev-build only UI allowing developers to toggle flag settings. See {@link FeatureFlags}.
 */
public final class FlagTogglerPreferenceFragment extends PreferenceFragment {
    private static final String TAG = "FlagTogglerPrefFrag";

    private SharedPreferences mSharedPreferences;
    private MenuItem saveButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.flag_preferences);
        mSharedPreferences = getContext().getSharedPreferences(
                FeatureFlags.FLAGS_PREF_NAME, Context.MODE_PRIVATE);

        // For flag overrides we only want to store when the engineer chose to override the
        // flag with a different value than the default. That way, when we flip flags in
        // future, engineers will pick up the new value immediately. To accomplish this, we use a
        // custom preference data store.
        getPreferenceManager().setPreferenceDataStore(new PreferenceDataStore() {
            @Override
            public void putBoolean(String key, boolean value) {
                for (TogglableFlag flag : FeatureFlags.getTogglableFlags()) {
                    if (flag.getKey().equals(key)) {
                        if (value == flag.getDefaultValue()) {
                            mSharedPreferences.edit().remove(key).apply();
                        } else {
                            mSharedPreferences.edit().putBoolean(key, value).apply();
                        }
                    }
                }
            }
        });

        for (TogglableFlag flag : FeatureFlags.getTogglableFlags()) {
            SwitchPreference switchPreference = new SwitchPreference(getContext());
            switchPreference.setKey(flag.getKey());
            switchPreference.setDefaultValue(flag.getDefaultValue());
            switchPreference.setChecked(getFlagStateFromSharedPrefs(flag));
            switchPreference.setTitle(flag.getKey());
            switchPreference.setSummaryOn(flag.getDefaultValue() ? "" : "overridden");
            switchPreference.setSummaryOff(flag.getDefaultValue() ? "overridden" : "");
            getPreferenceScreen().addPreference(switchPreference);
        }
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        saveButton = menu.add("Apply");
        saveButton.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item == saveButton) {
            mSharedPreferences.edit().commit();
            Log.e(TAG,
                    "Killing launcher process " + Process.myPid() + " to apply new flag values");
            System.exit(0);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStop() {
        boolean anyChanged = false;
        for (TogglableFlag flag : FeatureFlags.getTogglableFlags()) {
            anyChanged = anyChanged ||
                    getFlagStateFromSharedPrefs(flag) != flag.get();
        }

        if (anyChanged) {
            Toast.makeText(
                    getContext(),
                    "Flag won't be applied until you restart launcher",
                    Toast.LENGTH_LONG).show();
        }
        super.onStop();
    }

    private boolean getFlagStateFromSharedPrefs(TogglableFlag flag) {
        return mSharedPreferences.getBoolean(flag.getKey(), flag.getDefaultValue());
    }
}