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

import static com.android.launcher3.config.FeatureFlags.FLAGS_PREF_NAME;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreference;

import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags.DebugFlag;

/**
 * Dev-build only UI allowing developers to toggle flag settings. See {@link FeatureFlags}.
 */
public final class FlagTogglerPrefUi {

    private static final String TAG = "FlagTogglerPrefFrag";

    private final PreferenceFragmentCompat mFragment;
    private final Context mContext;
    private final SharedPreferences mSharedPreferences;

    private final PreferenceDataStore mDataStore = new PreferenceDataStore() {

        @Override
        public void putBoolean(String key, boolean value) {
            for (DebugFlag flag : FeatureFlags.getDebugFlags()) {
                if (flag.key.equals(key)) {
                    SharedPreferences prefs = mContext.getSharedPreferences(
                            FLAGS_PREF_NAME, Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    // We keep the key in the prefs even if it has the default value, because it's a
                    // signal that it has been changed at one point.
                    if (!prefs.contains(key) && value == flag.defaultValue) {
                        editor.remove(key).apply();
                        flag.mHasBeenChangedAtLeastOnce = false;
                    } else {
                        editor.putBoolean(key, value).apply();
                        flag.mHasBeenChangedAtLeastOnce = true;
                    }
                    updateMenu();
                }
            }
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            for (DebugFlag flag : FeatureFlags.getDebugFlags()) {
                if (flag.key.equals(key)) {
                    return mContext.getSharedPreferences(FLAGS_PREF_NAME, Context.MODE_PRIVATE)
                            .getBoolean(key, flag.defaultValue);
                }
            }
            return defaultValue;
        }
    };

    public FlagTogglerPrefUi(PreferenceFragmentCompat fragment) {
        mFragment = fragment;
        mContext = fragment.getActivity();
        mSharedPreferences = mContext.getSharedPreferences(
                FLAGS_PREF_NAME, Context.MODE_PRIVATE);
    }

    public void applyTo(PreferenceGroup parent) {
        // For flag overrides we only want to store when the engineer chose to override the
        // flag with a different value than the default. That way, when we flip flags in
        // future, engineers will pick up the new value immediately. To accomplish this, we use a
        // custom preference data store.
        for (DebugFlag flag : FeatureFlags.getDebugFlags()) {
            SwitchPreference switchPreference = new SwitchPreference(mContext);
            switchPreference.setKey(flag.key);
            switchPreference.setDefaultValue(flag.defaultValue);
            switchPreference.setChecked(getFlagStateFromSharedPrefs(flag));
            switchPreference.setTitle(flag.key);
            updateSummary(switchPreference, flag);
            switchPreference.setPreferenceDataStore(mDataStore);
            parent.addPreference(switchPreference);
        }
        updateMenu();
    }

    /**
     * Updates the summary to show the description and whether the flag overrides the default value.
     */
    private void updateSummary(SwitchPreference switchPreference, DebugFlag flag) {
        String onWarning = flag.defaultValue ? "" : "<b>OVERRIDDEN</b><br>";
        String offWarning = flag.defaultValue ? "<b>OVERRIDDEN</b><br>" : "";
        switchPreference.setSummaryOn(Html.fromHtml(onWarning + flag.description));
        switchPreference.setSummaryOff(Html.fromHtml(offWarning + flag.description));
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

    private boolean getFlagStateFromSharedPrefs(DebugFlag flag) {
        return mDataStore.getBoolean(flag.key, flag.defaultValue);
    }

    private boolean anyChanged() {
        for (DebugFlag flag : FeatureFlags.getDebugFlags()) {
            if (getFlagStateFromSharedPrefs(flag) != flag.get()) {
                return true;
            }
        }
        return false;
    }
}