/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.model;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotSame;
import static junit.framework.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.launcher3.LauncherProvider;
import com.android.launcher3.LauncherProvider.DatabaseHelper;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * Tests for {@link DbDowngradeHelper}
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DbDowngradeHelperTest {

    private static final String SCHEMA_FILE = "test_schema.json";
    private static final String DB_FILE = "test.db";

    private Context mContext;
    private File mSchemaFile;
    private File mDbFile;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
        mSchemaFile = mContext.getFileStreamPath(SCHEMA_FILE);
        mDbFile = mContext.getDatabasePath(DB_FILE);
    }

    @Test
    public void testUpdateSchemaFile() throws Exception {
        Context myContext = InstrumentationRegistry.getContext();
        int testResId = myContext.getResources().getIdentifier(
                "db_schema_v10", "raw", myContext.getPackageName());
        mSchemaFile.delete();
        assertFalse(mSchemaFile.exists());

        DbDowngradeHelper.updateSchemaFile(mSchemaFile, 10, myContext, testResId);
        assertTrue(mSchemaFile.exists());
        assertEquals(10, DbDowngradeHelper.parse(mSchemaFile).version);

        // Schema is updated on version upgrade
        assertTrue(mSchemaFile.setLastModified(0));
        DbDowngradeHelper.updateSchemaFile(mSchemaFile, 11, myContext, testResId);
        assertNotSame(0, mSchemaFile.lastModified());

        // Schema is not updated when version is same
        assertTrue(mSchemaFile.setLastModified(0));
        DbDowngradeHelper.updateSchemaFile(mSchemaFile, 10, myContext, testResId);
        assertEquals(0, mSchemaFile.lastModified());

        // Schema is not updated on version downgrade
        DbDowngradeHelper.updateSchemaFile(mSchemaFile, 3, myContext, testResId);
        assertEquals(0, mSchemaFile.lastModified());
    }

    @Test
    public void testDowngrade_success_v24() throws Exception {
        setupTestDb();

        TestOpenHelper helper = new TestOpenHelper(24);
        assertEquals(24, helper.getReadableDatabase().getVersion());
        helper.close();
    }

    @Test
    public void testDowngrade_success_v22() throws Exception {
        setupTestDb();

        SQLiteOpenHelper helper = new TestOpenHelper(22);
        assertEquals(22, helper.getWritableDatabase().getVersion());

        // Check column does not exist
        try (Cursor c = helper.getWritableDatabase().query(Favorites.TABLE_NAME,
                null, null, null, null, null, null)) {
            assertEquals(-1, c.getColumnIndex(Favorites.OPTIONS));

            // Check data is present
            assertEquals(10, c.getCount());
        }
        helper.close();

        helper = new DatabaseHelper(mContext, null, DB_FILE) {
            @Override
            public void onOpen(SQLiteDatabase db) { }
        };
        assertEquals(LauncherProvider.SCHEMA_VERSION, helper.getWritableDatabase().getVersion());

        try (Cursor c = helper.getWritableDatabase().query(Favorites.TABLE_NAME,
                null, null, null, null, null, null)) {
            // Check column exists
            assertNotSame(-1, c.getColumnIndex(Favorites.OPTIONS));

            // Check data is present
            assertEquals(10, c.getCount());
        }
        helper.close();
    }

    @Test(expected = DowngradeFailException.class)
    public void testDowngrade_fail_v20() throws Exception {
        setupTestDb();

        TestOpenHelper helper = new TestOpenHelper(20);
        helper.getReadableDatabase().getVersion();
    }

    private void setupTestDb() throws Exception {
        mSchemaFile.delete();
        mDbFile.delete();

        DbDowngradeHelper.updateSchemaFile(mSchemaFile, LauncherProvider.SCHEMA_VERSION, mContext,
                R.raw.downgrade_schema);

        DatabaseHelper dbHelper = new DatabaseHelper(mContext, null, DB_FILE) {
            @Override
            public void onOpen(SQLiteDatabase db) { }
        };
        // Insert dummy data
        for (int i = 0; i < 10; i++) {
            ContentValues values = new ContentValues();
            values.put(Favorites._ID, i);
            values.put(Favorites.TITLE, "title " + i);
            dbHelper.getWritableDatabase().insert(Favorites.TABLE_NAME, null, values);
        }
        dbHelper.close();
    }

    private class TestOpenHelper extends SQLiteOpenHelper {

        public TestOpenHelper(int version) {
            super(mContext, DB_FILE, null, version);
        }

        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase) {
            throw new RuntimeException("DB should already be created");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            throw new RuntimeException("Only downgrade supported");
        }

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            try {
                DbDowngradeHelper.parse(mSchemaFile).onDowngrade(db, oldVersion, newVersion);
            } catch (Exception e) {
                throw new DowngradeFailException(e);
            }
        }
    }

    private static class DowngradeFailException extends RuntimeException {
        public DowngradeFailException(Exception e) {
            super(e);
        }
    }
}
