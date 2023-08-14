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

import static androidx.test.InstrumentationRegistry.getContext;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotSame;
import static junit.framework.Assert.assertTrue;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.R;
import com.android.launcher3.pm.UserCache;

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
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mSchemaFile = mContext.getFileStreamPath(SCHEMA_FILE);
        mDbFile = mContext.getDatabasePath(DB_FILE);
    }

    @Test
    public void testDowngradeSchemaMatchesVersion() throws Exception {
        mSchemaFile.delete();
        assertFalse(mSchemaFile.exists());
        DbDowngradeHelper.updateSchemaFile(mSchemaFile, 0, mContext);
        assertEquals(DatabaseHelper.SCHEMA_VERSION, DbDowngradeHelper.parse(mSchemaFile).version);
    }

    @Test
    public void testUpdateSchemaFile() throws Exception {
        // Setup mock resources
        Resources res = spy(mContext.getResources());
        Resources myRes = getContext().getResources();
        doAnswer(i -> myRes.openRawResource(
                myRes.getIdentifier("db_schema_v10", "raw", getContext().getPackageName())))
                .when(res).openRawResource(R.raw.downgrade_schema);
        Context context = spy(mContext);
        when(context.getResources()).thenReturn(res);

        mSchemaFile.delete();
        assertFalse(mSchemaFile.exists());

        DbDowngradeHelper.updateSchemaFile(mSchemaFile, 10, context);
        assertTrue(mSchemaFile.exists());
        assertEquals(10, DbDowngradeHelper.parse(mSchemaFile).version);

        // Schema is updated on version upgrade
        assertTrue(mSchemaFile.setLastModified(0));
        DbDowngradeHelper.updateSchemaFile(mSchemaFile, 11, context);
        assertNotSame(0, mSchemaFile.lastModified());

        // Schema is not updated when version is same
        assertTrue(mSchemaFile.setLastModified(0));
        DbDowngradeHelper.updateSchemaFile(mSchemaFile, 10, context);
        assertEquals(0, mSchemaFile.lastModified());

        // Schema is not updated on version downgrade
        DbDowngradeHelper.updateSchemaFile(mSchemaFile, 3, context);
        assertEquals(0, mSchemaFile.lastModified());
    }

    @Test
    public void testDowngrade_success_v31() throws Exception {
        setupTestDb();

        try (SQLiteOpenHelper helper = new MyDatabaseHelper()) {
            assertFalse(hasFavoritesColumn(helper.getWritableDatabase(), "iconPackage"));
            assertFalse(hasFavoritesColumn(helper.getWritableDatabase(), "iconResource"));
        }

        try (TestOpenHelper helper = new TestOpenHelper(24)) {
            assertTrue(hasFavoritesColumn(helper.getWritableDatabase(), "iconPackage"));
            assertTrue(hasFavoritesColumn(helper.getWritableDatabase(), "iconResource"));
        }
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

        try (SQLiteOpenHelper helper = new TestOpenHelper(22)) {
            assertEquals(22, helper.getWritableDatabase().getVersion());
            assertFalse(hasFavoritesColumn(helper.getWritableDatabase(), Favorites.OPTIONS));
            assertEquals(10, getFavoriteDataCount(helper.getWritableDatabase()));
        }

        try (SQLiteOpenHelper helper = new MyDatabaseHelper()) {
            assertEquals(DatabaseHelper.SCHEMA_VERSION,
                    helper.getWritableDatabase().getVersion());
            assertTrue(hasFavoritesColumn(helper.getWritableDatabase(), Favorites.OPTIONS));
            assertEquals(10, getFavoriteDataCount(helper.getWritableDatabase()));
        }
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

        DbDowngradeHelper.updateSchemaFile(mSchemaFile, DatabaseHelper.SCHEMA_VERSION, mContext);

        DatabaseHelper dbHelper = new MyDatabaseHelper();
        // Insert mock data
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

    private static boolean hasFavoritesColumn(SQLiteDatabase db, String columnName) {
        try (Cursor c = db.query(Favorites.TABLE_NAME, null, null, null, null, null, null)) {
            return c.getColumnIndex(columnName) >= 0;
        }
    }

    public static int getFavoriteDataCount(SQLiteDatabase db) {
        try (Cursor c = db.query(Favorites.TABLE_NAME, null, null, null, null, null, null)) {
            return c.getCount();
        }
    }

    private class MyDatabaseHelper extends DatabaseHelper {

        MyDatabaseHelper() {
            super(mContext, DB_FILE,
                    UserCache.INSTANCE.get(mContext)::getSerialNumberForUser,
                    () -> { });
        }

        @Override
        public void onOpen(SQLiteDatabase db) { }
    }
}
