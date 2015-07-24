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
import android.util.Log;

import java.io.IOException;

public class LauncherBackupAgentHelper extends BackupAgentHelper {

    private static final String TAG = "LauncherBackupAgentHelper";

    private static final String LAUNCHER_DATA_PREFIX = "L";

    static final boolean VERBOSE = false;
    static final boolean DEBUG = false;

    private static BackupManager sBackupManager;

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

    private LauncherBackupHelper mHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        mHelper = new LauncherBackupHelper(this);
        addHelper(LAUNCHER_DATA_PREFIX, mHelper);
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
            throws IOException {
        if (!Utilities.isLmpOrAbove()) {
            // No restore for old devices.
            Log.i(TAG, "You shall not pass!!!");
            Log.d(TAG, "Restore is only supported on devices running Lollipop and above.");
            return;
        }

        // Clear dB before restore
        LauncherAppState.getLauncherProvider().createEmptyDB();

        boolean hasData;
        try {
            super.onRestore(data, appVersionCode, newState);
            // If no favorite was migrated, clear the data and start fresh.
            final Cursor c = getContentResolver().query(
                    LauncherSettings.Favorites.CONTENT_URI, null, null, null, null);
            hasData = c.moveToNext();
            c.close();
        } catch (Exception e) {
            // If the restore fails, we should do a fresh start.
            Log.e(TAG, "Restore failed", e);
            hasData = false;
        }

        if (hasData && mHelper.restoreSuccessful) {
            LauncherAppState.getLauncherProvider().clearFlagEmptyDbCreated();
            LauncherClings.synchonouslyMarkFirstRunClingDismissed(this);

            // TODO: Update the backup set to include rank.
            if (mHelper.restoredBackupVersion <= 3) {
                LauncherAppState.getLauncherProvider().updateFolderItemsRank();
                LauncherAppState.getLauncherProvider().convertShortcutsToLauncherActivities();
            }
        } else {
            if (VERBOSE) Log.v(TAG, "Nothing was restored, clearing DB");
            LauncherAppState.getLauncherProvider().createEmptyDB();
        }
    }
}
