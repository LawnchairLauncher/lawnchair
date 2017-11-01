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
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Point;
import android.util.Log;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.model.GridSizeMigrationTask;
import com.android.launcher3.util.LongArrayMap;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * An extension of {@link GridSizeMigrationTask} which migrates only one screen and
 * deletes all carry-forward items.
 */
public class LossyScreenMigrationTask extends GridSizeMigrationTask {

    private final SQLiteDatabase mDb;

    private final LongArrayMap<DbEntry> mOriginalItems;
    private final LongArrayMap<DbEntry> mUpdates;

    protected LossyScreenMigrationTask(
            Context context, InvariantDeviceProfile idp, SQLiteDatabase db) {
        // Decrease the rows count by 1
        super(context, idp, getValidPackages(context),
                new Point(idp.numColumns, idp.numRows + 1),
                new Point(idp.numColumns, idp.numRows));

        mDb = db;
        mOriginalItems = new LongArrayMap<>();
        mUpdates = new LongArrayMap<>();
    }

    @Override
    protected Cursor queryWorkspace(String[] columns, String where) {
        return mDb.query(Favorites.TABLE_NAME, columns, where, null, null, null, null);
    }

    @Override
    protected void update(DbEntry item) {
        mUpdates.put(item.id, item.copy());
    }

    @Override
    protected ArrayList<DbEntry> loadWorkspaceEntries(long screen) {
        ArrayList<DbEntry> result = super.loadWorkspaceEntries(screen);
        for (DbEntry entry : result) {
            mOriginalItems.put(entry.id, entry.copy());

            // Shift all items by 1 in y direction and mark them for update.
            entry.cellY++;
            mUpdates.put(entry.id, entry.copy());
        }

        return result;
    }

    public void migrateScreen0() {
        migrateScreen(Workspace.FIRST_SCREEN_ID);

        ContentValues tempValues = new ContentValues();
        for (DbEntry update : mUpdates) {
            DbEntry org = mOriginalItems.get(update.id);

            if (org.cellX != update.cellX || org.cellY != update.cellY
                    || org.spanX != update.spanX || org.spanY != update.spanY) {
                tempValues.clear();
                update.addToContentValues(tempValues);
                mDb.update(Favorites.TABLE_NAME, tempValues, "_id = ?",
                        new String[] {Long.toString(update.id)});
            }
        }

        // Delete any carry over items as we are only migration a single screen.
        for (DbEntry entry : mCarryOver) {
            mEntryToRemove.add(entry.id);
        }

        if (!mEntryToRemove.isEmpty()) {
            mDb.delete(Favorites.TABLE_NAME,
                    Utilities.createDbSelectionQuery(Favorites._ID, mEntryToRemove), null);
        }
    }
}
