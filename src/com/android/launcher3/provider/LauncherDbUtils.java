/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.provider;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.LauncherSettings.WorkspaceScreens;
import com.android.launcher3.logging.FileLog;

import java.util.ArrayList;

/**
 * A set of utility methods for Launcher DB used for DB updates and migration.
 */
public class LauncherDbUtils {

    private static final String TAG = "LauncherDbUtils";

    /**
     * Makes the first screen as screen 0 (if screen 0 already exists,
     * renames it to some other number).
     * If the first row of screen 0 is non empty, runs a 'lossy' GridMigrationTask to clear
     * the first row. The items in the first screen are moved and resized but the carry-forward
     * items are simply deleted.
     */
    public static boolean prepareScreenZeroToHostQsb(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            // Get the existing screens
            ArrayList<Long> screenIds = getScreenIdsFromCursor(db.query(WorkspaceScreens.TABLE_NAME,
                    null, null, null, null, null, WorkspaceScreens.SCREEN_RANK));

            if (screenIds.isEmpty()) {
                // No update needed
                return true;
            }
            if (screenIds.get(0) != 0) {
                // First screen is not 0, we need to rename screens
                if (screenIds.indexOf(0L) > -1) {
                    // There is already a screen 0. First rename it to a differen screen.
                    long newScreenId = 1;
                    while (screenIds.indexOf(newScreenId) > -1) newScreenId++;
                    renameScreen(db, 0, newScreenId);
                }

                // Rename the first screen to 0.
                renameScreen(db, screenIds.get(0), 0);
            }

            // Check if the first row is empty
            try (Cursor c = db.query(Favorites.TABLE_NAME, null,
                    "container = -100 and screen = 0 and cellY = 0", null, null, null, null)) {
                if (c.getCount() == 0) {
                    // First row is empty, no need to migrate.
                    return true;
                }
            }

            LauncherAppState app = LauncherAppState.getInstance();
            new LossyScreenMigrationTask(app.getContext(), app.getInvariantDeviceProfile(), db)
                    .migrateScreen0();
            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to update workspace size", e);
            return false;
        } finally {
            db.endTransaction();
        }
    }

    private static void renameScreen(SQLiteDatabase db, long oldScreen, long newScreen) {
        String[] whereParams = new String[] { Long.toString(oldScreen) };

        ContentValues values = new ContentValues();
        values.put(WorkspaceScreens._ID, newScreen);
        db.update(WorkspaceScreens.TABLE_NAME, values, "_id = ?", whereParams);

        values.clear();
        values.put(Favorites.SCREEN, newScreen);
        db.update(Favorites.TABLE_NAME, values, "container = -100 and screen = ?", whereParams);
    }

    /**
     * Parses the cursor containing workspace screens table and returns the list of screen IDs
     */
    public static ArrayList<Long> getScreenIdsFromCursor(Cursor sc) {
        ArrayList<Long> screenIds = new ArrayList<Long>();
        try {
            final int idIndex = sc.getColumnIndexOrThrow(WorkspaceScreens._ID);
            while (sc.moveToNext()) {
                try {
                    screenIds.add(sc.getLong(idIndex));
                } catch (Exception e) {
                    FileLog.d(TAG, "Invalid screen id", e);
                }
            }
        } finally {
            sc.close();
        }
        return screenIds;
    }
}
