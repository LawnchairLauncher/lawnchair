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
import android.os.Process;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.launcher3.R;

import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreference;
import com.android.launcher3.config.BaseFlags.BaseTogglableFlag;
import com.android.launcher3.uioverrides.TogglableFlag;

/**
 * Dev-build only UI allowing developers to toggle flag settings. See {@link FeatureFlags}.
 */
public final class FlagTogglerPrefUi {

    private static final String TAG = "FlagTogglerPrefFrag";

    private final PreferenceFragment mFragment;
    private final Context mContext;
    private final SharedPreferences mSharedPreferences;

    private final PreferenceDataStore mDataStore = new PreferenceDataStore() {

        @Override
        public void putBoolean(String key, boolean value) {
            for (TogglableFlag flag : FeatureFlags.getTogglableFlags()) {
                if (flag.getKey().equals(key)) {
                    boolean prevValue = flag.get();
                    flag.updateStorage(mContext, value);
                    updateMenu();
                    if (flag.get() != prevValue) {
                        Toast.makeText(mContext, "Flag applied", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            for (BaseTogglableFlag flag : FeatureFlags.getTogglableFlags()) {
                if (flag.getKey().equals(key)) {
                    return flag.getFromStorage(mContext, defaultValue);
                }
            }
            return defaultValue;
        }
    };

    public FlagTogglerPrefUi(PreferenceFragment fragment) {
        mFragment = fragment;
        mContext = fragment.getActivity();
        mSharedPreferences = mContext.getSharedPreferences(
                FeatureFlags.FLAGS_PREF_NAME, Context.MODE_PRIVATE);
    }

    public void applyTo(PreferenceGroup parent) {
        // For flag overrides we only want to store when the engineer chose to override the
        // flag with a different value than the default. That way, when we flip flags in
        // future, engineers will pick up the new value immediately. To accomplish this, we use a
        // custom preference data store.
        for (BaseTogglableFlag flag : FeatureFlags.getTogglableFlags()) {
            SwitchPreference switchPreference = new SwitchPreference(mContext);
            switchPreference.setKey(flag.getKey());
            switchPreference.setDefaultValue(flag.getDefaultValue());
            switchPreference.setChecked(getFlagStateFromSharedPrefs(flag));
            switchPreference.setTitle(flag.getKey());
            updateSummary(switchPreference, flag);
            switchPreference.setPreferenceDataStore(mDataStore);
            parent.addPreference(switchPreference);
        }
        updateMenu();
    }

    /**
     * Updates the summary to show the description and whether the flag overrides the default value.
     */
    private void updateSummary(SwitchPreference switchPreference, BaseTogglableFlag flag) {
        String onWarning = flag.getDefaultValue() ? "" : "<b>OVERRIDDEN</b><br>";
        String offWarning = flag.getDefaultValue() ? "<b>OVERRIDDEN</b><br>" : "";
        switchPreference.setSummaryOn(Html.fromHtml(onWarning + flag.getDescription()));
        switchPreference.setSummaryOff(Html.fromHtml(offWarning + flag.getDescription()));
    }

    private void updateMenu() {
        mFragment.setHasOptionsMenu(anyChanged());
        mFragment.getActivity().invalidateOptionsMenu();
    }

    public void onCreateOptionsMenu(Menu menu) {
        if (anyChanged()) {
            menu.add(0, R.id.menu_apply_flags, 0, "Apply")
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
    }

    public void onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_apply_flags) {
            mSharedPreferences.edit().commit();
            Log.e(TAG,
                    "Killing launcher process " + Process.myPid() + " to apply new flag values");
            System.exit(0);
        }
    }

    public void onStop() {
        if (anyChanged()) {
            Toast.makeText(mContext, "Flag won't be applied until you restart launcher",
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean getFlagStateFromSharedPrefs(BaseTogglableFlag flag) {
        return mDataStore.getBoolean(flag.getKey(), flag.getDefaultValue());
    }

    private boolean anyChanged() {
        for (TogglableFlag flag : FeatureFlags.getTogglableFlags()) {
            if (getFlagStateFromSharedPrefs(flag) != flag.get()) {
                return true;
            }
        }
        return false;
    }
}