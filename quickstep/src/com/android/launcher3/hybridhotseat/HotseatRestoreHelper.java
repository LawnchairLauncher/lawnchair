/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.hybridhotseat;

import static com.android.launcher3.LauncherSettings.Favorites.HYBRID_HOTSEAT_BACKUP_TABLE;
import static com.android.launcher3.provider.LauncherDbUtils.tableExists;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.Context;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.GridBackupTable;
import com.android.launcher3.provider.LauncherDbUtils;

/**
 * A helper class to manage migration revert restoration for hybrid hotseat
 */
public class HotseatRestoreHelper {

    /**
     * Creates a snapshot backup of Favorite table for future restoration use.
     */
    public static void createBackup(Context context) {
        MODEL_EXECUTOR.execute(() -> {
            try (LauncherDbUtils.SQLiteTransaction transaction = (LauncherDbUtils.SQLiteTransaction)
                    LauncherSettings.Settings.call(
                            context.getContentResolver(),
                            LauncherSettings.Settings.METHOD_NEW_TRANSACTION)
                            .getBinder(LauncherSettings.Settings.EXTRA_VALUE)) {
                InvariantDeviceProfile idp = LauncherAppState.getIDP(context);
                GridBackupTable backupTable = new GridBackupTable(context,
                        transaction.getDb(), idp.numDatabaseHotseatIcons, idp.numColumns,
                        idp.numRows);
                backupTable.createCustomBackupTable(HYBRID_HOTSEAT_BACKUP_TABLE);
                transaction.commit();
                LauncherSettings.Settings.call(context.getContentResolver(),
                        LauncherSettings.Settings.METHOD_REFRESH_HOTSEAT_RESTORE_TABLE);
            }
        });
    }

    /**
     * Finds and restores a previously saved snapshow of Favorites table
     */
    public static void restoreBackup(Context context) {
        MODEL_EXECUTOR.execute(() -> {
            try (LauncherDbUtils.SQLiteTransaction transaction = (LauncherDbUtils.SQLiteTransaction)
                    LauncherSettings.Settings.call(
                            context.getContentResolver(),
                            LauncherSettings.Settings.METHOD_NEW_TRANSACTION)
                            .getBinder(LauncherSettings.Settings.EXTRA_VALUE)) {
                if (!tableExists(transaction.getDb(), HYBRID_HOTSEAT_BACKUP_TABLE)) {
                    return;
                }
                InvariantDeviceProfile idp = LauncherAppState.getIDP(context);
                GridBackupTable backupTable = new GridBackupTable(context,
                        transaction.getDb(), idp.numDatabaseHotseatIcons, idp.numColumns,
                        idp.numRows);
                backupTable.restoreFromCustomBackupTable(HYBRID_HOTSEAT_BACKUP_TABLE, true);
                transaction.commit();
                LauncherAppState.getInstance(context).getModel().forceReload();
            }
        });
    }
}
