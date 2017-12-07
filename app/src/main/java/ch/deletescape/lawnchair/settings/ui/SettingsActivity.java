/*
 * Copyright (C) 2015 The Android Open Source Project
 * Copyright (C) 2017 The MoKee Open Source Project
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
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import ch.deletescape.lawnchair.BuildConfig;
import ch.deletescape.lawnchair.DumbImportExportTask;
import ch.deletescape.lawnchair.LauncherAppState;
import ch.deletescape.lawnchair.LauncherFiles;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider;
import ch.deletescape.lawnchair.config.FeatureFlags;
import ch.deletescape.lawnchair.graphics.IconShapeOverride;
import ch.deletescape.lawnchair.overlay.ILauncherClient;
import ch.deletescape.lawnchair.preferences.IPreferenceProvider;
import ch.deletescape.lawnchair.preferences.PreferenceFlags;

/**
 * Settings activity for Launcher. Currently implements the following setting: Allow rotation
 */
public class SettingsActivity extends AppCompatActivity implements
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback, SharedPreferences.OnSharedPreferenceChangeListener {

    private static IPreferenceProvider sharedPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        FeatureFlags.INSTANCE.applyDarkTheme(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        if (FeatureFlags.INSTANCE.getCurrentTheme() != 2)
            BlurWallpaperProvider.Companion.applyBlurBackground(this);

        if (savedInstanceState == null) {
            // Display the fragment as the main content.
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content, new LauncherSettingsFragment())

                    .commit();
        }

        sharedPrefs = Utilities.getPrefs(this);
        sharedPrefs.registerOnSharedPreferenceChangeListener(this);
        updateUpButton();
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        Fragment fragment;
        if (pref instanceof SubPreference) {
            fragment = SubSettingsFragment.newInstance(((SubPreference) pref));
        } else {
            fragment = Fragment.instantiate(this, pref.getFragment());
        }
        if (fragment != null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            setTitle(pref.getTitle());
            transaction.setCustomAnimations(R.animator.fly_in, R.animator.fade_out, R.animator.fade_in, R.animator.fly_out);
            transaction.replace(R.id.content, fragment);
            transaction.addToBackStack("PreferenceFragment");
            transaction.commit();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
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
        getSupportActionBar().setDisplayHomeAsUpEnabled(getSupportFragmentManager().getBackStackEntryCount() != 0);
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
        if (FeatureFlags.KEY_PREF_THEME.equals(key)) {
            FeatureFlags.INSTANCE.loadThemePreference(this);
            recreate();
        }
    }

    private abstract static class BaseFragment extends PreferenceFragmentCompat implements AdapterView.OnItemLongClickListener {

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

    /**
     * This fragment shows the launcher preferences.
     */
    public static class LauncherSettingsFragment extends BaseFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            setDivider(null);
            return view;
        }

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            addPreferencesFromResource(R.xml.launcher_preferences);
        }

        @Override
        public void onResume() {
            super.onResume();
            getActivity().setTitle(R.string.settings_button_text);
        }
    }

    public static class SubSettingsFragment extends BaseFragment implements Preference.OnPreferenceChangeListener {

        private static final String TITLE = "title";
        private static final String CONTENT_RES_ID = "content_res_id";

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (getContent() == R.xml.launcher_theme_preferences) {
                Preference prefWeatherEnabled = findPreference(FeatureFlags.KEY_PREF_WEATHER);
                prefWeatherEnabled.setOnPreferenceChangeListener(this);
                Preference prefWeatherProvider = findPreference(PreferenceFlags.KEY_WEATHER_PROVIDER);
                prefWeatherProvider.setEnabled(BuildConfig.AWARENESS_API_ENABLED);
                prefWeatherProvider.setOnPreferenceChangeListener(this);
                updateEnabledState(Utilities.getPrefs(getActivity()).getWeatherProvider());
                Preference overrideShapePreference = findPreference(PreferenceFlags.KEY_OVERRIDE_ICON_SHAPE);
                if (IconShapeOverride.Companion.isSupported(getActivity())) {
                    IconShapeOverride.Companion.handlePreferenceUi((ListPreference) overrideShapePreference);
                } else {
                    ((PreferenceCategory) findPreference("prefCat_homeScreen"))
                            .removePreference(overrideShapePreference);
                }
                if (Utilities.ATLEAST_NOUGAT) {
                    ((PreferenceCategory) findPreference("prefCat_homeScreen"))
                        .removePreference(findPreference(PreferenceFlags.KEY_PREF_PIXEL_STYLE_ICONS));
                }
            } else if (getContent() == R.xml.launcher_about_preferences) {
                findPreference("about_version").setSummary(BuildConfig.VERSION_NAME);
                if (BuildConfig.TRAVIS && !BuildConfig.BUILD_TYPE.equalsIgnoreCase("stable")) {
                    findPreference("about_changelog").setSummary(Utilities.getChangelog());
                }
            } else if (getContent() == R.xml.launcher_behavior_preferences) {
                if (Utilities.ATLEAST_NOUGAT_MR1 && BuildConfig.TRAVIS) {
                    getPreferenceScreen().removePreference(findPreference(FeatureFlags.KEY_PREF_ENABLE_BACKPORT_SHORTCUTS));
                }

                // Remove Google Now tab option when Lawnfeed is not installed
                int enabledState = ILauncherClient.Companion.getEnabledState(getContext());
                if (BuildConfig.ENABLE_LAWNFEED && enabledState == ILauncherClient.Companion.DISABLED_NO_PROXY_APP) {
                    getPreferenceScreen().removePreference(findPreference(FeatureFlags.KEY_PREF_SHOW_NOW_TAB));
                }
            } else if (getContent() == R.xml.launcher_snowfall_preferences) {
                Preference prefSnowfallEnabled = findPreference(FeatureFlags.KEY_PREF_SNOWFALL);
                prefSnowfallEnabled.setOnPreferenceChangeListener(this);
                if (Utilities.getPrefs(getActivity()).getEnableSnowfall()) {
                    prefSnowfallEnabled.setSummary(R.string.snowfall_enabled);
                }
            }
        }

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            addPreferencesFromResource(getContent());
        }

        private void updateEnabledState(String weatherProvider) {
            boolean awarenessApiEnabled = weatherProvider.equals(PreferenceFlags.PREF_WEATHER_PROVIDER_AWARENESS);
            Preference prefWeatherCity = findPreference(PreferenceFlags.KEY_WEATHER_CITY);
            Preference prefWeatherApiKey = findPreference(PreferenceFlags.KEY_WEATHER_API_KEY);
            prefWeatherCity.setEnabled(!awarenessApiEnabled);
            prefWeatherApiKey.setEnabled(!awarenessApiEnabled);
        }

        private void updateSnowfallSummary(boolean enabled) {
            Preference prefEnableSnowfall = findPreference(FeatureFlags.KEY_PREF_SNOWFALL);
            prefEnableSnowfall.setSummary(enabled ? R.string.snowfall_enabled : R.string.enable_snowfall_summary);
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (preference.getKey() != null) {
                switch (preference.getKey()) {
                    case PreferenceFlags.KEY_WEATHER_PROVIDER:
                        updateEnabledState((String) newValue);
                        break;
                    case FeatureFlags.KEY_PREF_WEATHER:
                        Context context = getActivity();
                        if (Utilities.getPrefs(context).getShowWeather() && Utilities.isAwarenessApiEnabled(context)) {
                            checkPermission(Manifest.permission.ACCESS_FINE_LOCATION);
                        }
                        break;
                    case FeatureFlags.KEY_PREF_SNOWFALL:
                        updateSnowfallSummary((boolean) newValue);
                        break;
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            if (preference.getKey() != null) {
                switch (preference.getKey()) {
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
                            LauncherAppState.getInstance().getLauncher().scheduleReloadIcons();
                            LauncherAppState.getInstance().getLauncher().scheduleKill();
                        }
                        break;
                    case PreferenceFlags.KEY_WEATHER_PROVIDER:
                        if (!checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                            Toast.makeText(getActivity(), R.string.location_permission_warn, Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case "about_translators":
                        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                        builder.setTitle(R.string.about_translators);
                        builder.setView(R.layout.dialog_translators);
                        builder.setNeutralButton("OK", null);
                        AlertDialog dialog = builder.create();
                        dialog.show();

                        // Custom LayoutParams for neutral button of AlertDialog
                        final Button neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                        LinearLayout.LayoutParams neutralButtonLL = (LinearLayout.LayoutParams) neutralButton.getLayoutParams();
                        neutralButtonLL.width = ViewGroup.LayoutParams.MATCH_PARENT;
                        neutralButton.setLayoutParams(neutralButtonLL);
                        break;
                    default:
                        return super.onPreferenceTreeClick(preference);
                }
                return true;
            }
            return super.onPreferenceTreeClick(preference);
        }

        private boolean checkStoragePermission() {
            return checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        private boolean checkPermission(String permission) {
            boolean granted = ContextCompat.checkSelfPermission(
                    getActivity(),
                    permission) == PackageManager.PERMISSION_GRANTED;
            if (granted) return true;
            ActivityCompat.requestPermissions(
                    getActivity(),
                    new String[]{permission},
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

        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return super.onCreateView(FeatureFlags.INSTANCE.getLayoutInflator(inflater), container, savedInstanceState);
        }
    }
}
