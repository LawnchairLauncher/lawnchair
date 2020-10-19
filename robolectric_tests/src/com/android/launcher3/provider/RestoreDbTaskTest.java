/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.android.launcher3.LauncherProvider.DatabaseHelper;
import com.android.launcher3.LauncherSettings.Favorites;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Tests for {@link RestoreDbTask}
 */
@RunWith(RobolectricTestRunner.class)
public class RestoreDbTaskTest {

    @Test
    public void testGetProfileId() throws Exception {
        SQLiteDatabase db = new MyDatabaseHelper(23).getWritableDatabase();
        assertEquals(23, new RestoreDbTask().getDefaultProfileId(db));
    }

    @Test
    public void testMigrateProfileId() throws Exception {
        SQLiteDatabase db = new MyDatabaseHelper(42).getWritableDatabase();
        // Add some dummy data
        for (int i = 0; i < 5; i++) {
            ContentValues values = new ContentValues();
            values.put(Favorites._ID, i);
            values.put(Favorites.TITLE, "item " + i);
            db.insert(Favorites.TABLE_NAME, null, values);
        }
        // Verify item add
        assertEquals(5, getCount(db, "select * from favorites where profileId = 42"));

        new RestoreDbTask().migrateProfileId(db, 42, 33);

        // verify data migrated
        assertEquals(0, getCount(db, "select * from favorites where profileId = 42"));
        assertEquals(5, getCount(db, "select * from favorites where profileId = 33"));
    }

    @Test
    public void testChangeDefaultColumn() throws Exception {
        SQLiteDatabase db = new MyDatabaseHelper(42).getWritableDatabase();
        // Add some dummy data
        for (int i = 0; i < 5; i++) {
            ContentValues values = new ContentValues();
            values.put(Favorites._ID, i);
            values.put(Favorites.TITLE, "item " + i);
            db.insert(Favorites.TABLE_NAME, null, values);
        }
        // Verify default column is 42
        assertEquals(5, getCount(db, "select * from favorites where profileId = 42"));

        new RestoreDbTask().changeDefaultColumn(db, 33);

        // Verify default value changed
        ContentValues values = new ContentValues();
        values.put(Favorites._ID, 100);
        values.put(Favorites.TITLE, "item 100");
        db.insert(Favorites.TABLE_NAME, null, values);
        assertEquals(1, getCount(db, "select * from favorites where profileId = 33"));
    }

    private int getCount(SQLiteDatabase db, String sql) {
        try (Cursor c = db.rawQuery(sql, null)) {
            return c.getCount();
        }
    }

    private class MyDatabaseHelper extends DatabaseHelper {

        private final long mProfileId;

        MyDatabaseHelper(long profileId) {
            super(RuntimeEnvironment.application, null, false);
            mProfileId = profileId;
        }

        @Override
        public long getDefaultUserSerial() {
            return mProfileId;
        }

        @Override
        protected void handleOneTimeDataUpgrade(SQLiteDatabase db) { }

        protected void onEmptyDbCreated() { }
    }
}
