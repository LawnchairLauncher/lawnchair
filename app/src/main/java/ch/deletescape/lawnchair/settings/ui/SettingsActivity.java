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

package ch.deletescape.lawnchair.settings.ui;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;

import ch.deletescape.lawnchair.DumbImportExportTask;
import ch.deletescape.lawnchair.LauncherAppState;
import ch.deletescape.lawnchair.LauncherFiles;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider;
import ch.deletescape.lawnchair.config.FeatureFlags;

/**
 * Settings activity for Launcher. Currently implements the following setting: Allow rotation
 */
public class SettingsActivity extends Activity implements PreferenceFragment.OnPreferenceStartFragmentCallback, SharedPreferences.OnSharedPreferenceChangeListener {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        FeatureFlags.INSTANCE.applyDarkTheme(this);
        super.onCreate(savedInstanceState);

        if (FeatureFlags.INSTANCE.getCurrentTheme() != 2)
            BlurWallpaperProvider.Companion.applyBlurBackground(this);

        if (savedInstanceState == null) {
            // Display the fragment as the main content.
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new LauncherSettingsFragment())
                    .commit();
        }

        Utilities.getPrefs(this).registerOnSharedPreferenceChangeListener(this);
        updateUpButton();
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref) {
        if (pref instanceof SubPreference) {
            Fragment fragment = SubSettingsFragment.newInstance(((SubPreference) pref));
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            setTitle(pref.getTitle());
            transaction.setCustomAnimations(R.animator.fly_in, R.animator.fade_out, R.animator.fade_in, R.animator.fly_out);
            transaction.replace(android.R.id.content, fragment);
            transaction.addToBackStack("PreferenceFragment");
            transaction.commit();
            getActionBar().setDisplayHomeAsUpEnabled(true);
            return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        updateUpButton();
    }

    private void updateUpButton() {
        getActionBar().setDisplayHomeAsUpEnabled(getFragmentManager().getBackStackEntryCount() != 0);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (FeatureFlags.INSTANCE.getKEY_PREF_THEME().equals(key)) {
            FeatureFlags.INSTANCE.loadDarkThemePreference(this);
            recreate();
        }
    }

    /**
     * This fragment shows the launcher preferences.
     */
    public static class LauncherSettingsFragment extends PreferenceFragment implements AdapterView.OnItemLongClickListener {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            addPreferencesFromResource(R.xml.launcher_preferences);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            if (view == null) return null;
            ListView listView = view.findViewById(android.R.id.list);
            listView.setOnItemLongClickListener(this);
            return view;
        }

        @Override
        public void onResume() {
            super.onResume();
            getActivity().setTitle(R.string.settings_button_text);
        }

        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            ListView listView = (ListView) parent;
            ListAdapter listAdapter = listView.getAdapter();
            Object item = listAdapter.getItem(position);

            if (item instanceof SubPreference) {
                SubPreference subPreference = (SubPreference) item;
                if (subPreference.onLongClick(null)) {
                    ((SettingsActivity) getActivity()).onPreferenceStartFragment(this, subPreference);
                    return true;
                } else {
                    return false;
                }
            }
            return item != null && item instanceof View.OnLongClickListener && ((View.OnLongClickListener) item).onLongClick(view);
        }
    }

    public static class SubSettingsFragment extends PreferenceFragment {

        private static final String TITLE = "title";
        private static final String CONTENT_RES_ID = "content_res_id";

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            addPreferencesFromResource(getContent());
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            if (preference.getKey() != null) {
                switch (preference.getKey()) {
                    case "notification_access":
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                        break;
                    case "kill":
                        LauncherAppState.getInstance().getLauncher().scheduleKill();
                        break;
                    case "rebuild_icondb":
                        LauncherAppState.getInstance().getLauncher().scheduleReloadIcons();
                        break;
                    case "export_db":
                        if (checkStoragePermission())
                            DumbImportExportTask.exportDB(getActivity());
                        break;
                    case "import_db":
                        if (checkStoragePermission()) {
                            DumbImportExportTask.importDB(getActivity());
                            LauncherAppState.getInstance().getLauncher().scheduleKill();
                        }
                        break;
                    case "export_prefs":
                        if (checkStoragePermission())
                            DumbImportExportTask.exportPrefs(getActivity());
                        break;
                    case "import_prefs":
                        if (checkStoragePermission()) {
                            DumbImportExportTask.importPrefs(getActivity());
                            LauncherAppState.getInstance().getLauncher().scheduleKill();
                        }
                        break;
                    default:
                        return false;
                }
                return true;
            }
            return false;
        }

        private boolean checkStoragePermission() {
            boolean granted = ContextCompat.checkSelfPermission(
                    getActivity(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            if (granted) return true;
            ActivityCompat.requestPermissions(
                    getActivity(),
                    new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    0);
            return false;
        }

        private int getContent() {
            return getArguments().getInt(CONTENT_RES_ID);
        }

        @Override
        public void onResume() {
            super.onResume();
            getActivity().setTitle(getArguments().getString(TITLE));
        }

        public static SubSettingsFragment newInstance(SubPreference preference) {
            SubSettingsFragment fragment = new SubSettingsFragment();
            Bundle b = new Bundle(2);
            b.putString(TITLE, (String) preference.getTitle());
            b.putInt(CONTENT_RES_ID, preference.getContent());
            fragment.setArguments(b);
            return fragment;
        }
    }
}
