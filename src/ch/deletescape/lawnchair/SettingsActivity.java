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

package ch.deletescape.lawnchair;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.provider.Settings;

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
    public static class LauncherSettingsFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            addPreferencesFromResource(R.xml.launcher_preferences);
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            if (preference.getKey() != null) {
                switch (preference.getKey()) {
                    case "about":
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/deletescape-media/lawnchair"));
                        startActivity(i);
                        break;
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
                        DumbImportExportTask.exportDB(getActivity());
                        break;
                    case "import_db":
                        DumbImportExportTask.importDB(getActivity());
                        LauncherAppState.getInstance().getLauncher().scheduleKill();
                        break;
                    case "export_prefs":
                        DumbImportExportTask.exportPrefs(getActivity());
                        break;
                    case "import_prefs":
                        DumbImportExportTask.importPrefs(getActivity());
                        LauncherAppState.getInstance().getLauncher().scheduleKill();
                        break;
                    default:
                        return false;
                }
                return true;
            }
            return false;
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
        }
    }
}
