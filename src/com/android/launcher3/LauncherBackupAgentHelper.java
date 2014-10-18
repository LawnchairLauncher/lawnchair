/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupManager;
import android.content.Context;
import android.database.Cursor;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.util.Log;

import java.io.IOException;

public class LauncherBackupAgentHelper extends BackupAgentHelper {

    private static final String TAG = "LauncherBackupAgentHelper";
    static final boolean VERBOSE = true;
    static final boolean DEBUG = false;

    private static BackupManager sBackupManager;

    protected static final String SETTING_RESTORE_ENABLED = "launcher_restore_enabled";

    /**
     * Notify the backup manager that out database is dirty.
     *
     * <P>This does not force an immediate backup.
     *
     * @param context application context
     */
    public static void dataChanged(Context context) {
        if (sBackupManager == null) {
            sBackupManager = new BackupManager(context);
        }
        sBackupManager.dataChanged();
    }

    @Override
    public void onDestroy() {
        // There is only one process accessing this preference file, but the restore
        // modifies the file outside the normal codepaths, so it looks like another
        // process.  This forces a reload of the file, in case this process persists.
        String spKey = LauncherAppState.getSharedPreferencesKey();
        getSharedPreferences(spKey, Context.MODE_MULTI_PROCESS);
        super.onDestroy();
    }

    @Override
    public void onCreate() {
        boolean restoreEnabled = 0 != Settings.Secure.getInt(
                getContentResolver(), SETTING_RESTORE_ENABLED, 1);
        if (VERBOSE) Log.v(TAG, "restore is " + (restoreEnabled ? "enabled" : "disabled"));

        addHelper(LauncherBackupHelper.LAUNCHER_PREFS_PREFIX,
                new LauncherPreferencesBackupHelper(this,
                        LauncherAppState.getSharedPreferencesKey(),
                        restoreEnabled));
        addHelper(LauncherBackupHelper.LAUNCHER_PREFIX,
                new LauncherBackupHelper(this, restoreEnabled));
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
            throws IOException {
        super.onRestore(data, appVersionCode, newState);

        // If no favorite was migrated, clear the data and start fresh.
        final Cursor c = getContentResolver().query(
                LauncherSettings.Favorites.CONTENT_URI_NO_NOTIFICATION, null, null, null, null);
        boolean hasData = c.moveToNext();
        c.close();

        if (!hasData) {
            if (VERBOSE) Log.v(TAG, "Nothing was restored, clearing DB");
            LauncherAppState.getLauncherProvider().createEmptyDB();
        }
    }
}
