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

package com.android.launcher3.model;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.LauncherSettings.Favorites.TMP_CONTENT_URI;
import static com.android.launcher3.provider.LauncherDbUtils.dropTable;
import static com.android.launcher3.util.LauncherModelHelper.APP_ICON;
import static com.android.launcher3.util.LauncherModelHelper.DESKTOP;
import static com.android.launcher3.util.LauncherModelHelper.HOTSEAT;
import static com.android.launcher3.util.LauncherModelHelper.SHORTCUT;
import static com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Point;
import android.os.Process;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.LauncherModelHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.HashSet;

/** Unit tests for {@link GridSizeMigrationTaskV2} */
@RunWith(RobolectricTestRunner.class)
public class GridSizeMigrationTaskV2Test {

    private LauncherModelHelper mModelHelper;
    private Context mContext;
    private SQLiteDatabase mDb;

    private HashSet<String> mValidPackages;
    private InvariantDeviceProfile mIdp;

    private final String testPackage1 = "com.android.launcher3.validpackage1";
    private final String testPackage2 = "com.android.launcher3.validpackage2";
    private final String testPackage3 = "com.android.launcher3.validpackage3";
    private final String testPackage4 = "com.android.launcher3.validpackage4";
    private final String testPackage5 = "com.android.launcher3.validpackage5";
    private final String testPackage6 = "com.android.launcher3.validpackage6";
    private final String testPackage7 = "com.android.launcher3.validpackage7";
    private final String testPackage8 = "com.android.launcher3.validpackage8";
    private final String testPackage9 = "com.android.launcher3.validpackage9";
    private final String testPackage10 = "com.android.launcher3.validpackage10";

    @Before
    public void setUp() {
        mModelHelper = new LauncherModelHelper();
        mContext = RuntimeEnvironment.application;
        mDb = mModelHelper.provider.getDb();

        mValidPackages = new HashSet<>();
        mValidPackages.add(TEST_PACKAGE);
        mValidPackages.add(testPackage1);
        mValidPackages.add(testPackage2);
        mValidPackages.add(testPackage3);
        mValidPackages.add(testPackage4);
        mValidPackages.add(testPackage5);
        mValidPackages.add(testPackage6);
        mValidPackages.add(testPackage7);
        mValidPackages.add(testPackage8);
        mValidPackages.add(testPackage9);
        mValidPackages.add(testPackage10);

        mIdp = InvariantDeviceProfile.INSTANCE.get(mContext);

        long userSerial = UserCache.INSTANCE.get(mContext).getSerialNumberForUser(
                Process.myUserHandle());
        dropTable(mDb, LauncherSettings.Favorites.TMP_TABLE);
        LauncherSettings.Favorites.addTableToDb(mDb, userSerial, false,
                LauncherSettings.Favorites.TMP_TABLE);
    }

    @Test
    public void testMigration() {
        int[] srcHotseatItems = {
                mModelHelper.addItem(APP_ICON, 0, HOTSEAT, 0, 0, testPackage1, 1, TMP_CONTENT_URI),
                mModelHelper.addItem(SHORTCUT, 1, HOTSEAT, 0, 0, testPackage2, 2, TMP_CONTENT_URI),
                -1,
                mModelHelper.addItem(SHORTCUT, 3, HOTSEAT, 0, 0, testPackage3, 3, TMP_CONTENT_URI),
                mModelHelper.addItem(APP_ICON, 4, HOTSEAT, 0, 0, testPackage4, 4, TMP_CONTENT_URI),
        };
        mModelHelper.addItem(APP_ICON, 0, DESKTOP, 2, 2, testPackage5, 5, TMP_CONTENT_URI);
        mModelHelper.addItem(APP_ICON, 0, DESKTOP, 2, 3, testPackage6, 6, TMP_CONTENT_URI);
        mModelHelper.addItem(APP_ICON, 0, DESKTOP, 4, 1, testPackage8, 8, TMP_CONTENT_URI);
        mModelHelper.addItem(APP_ICON, 0, DESKTOP, 4, 2, testPackage9, 9, TMP_CONTENT_URI);
        mModelHelper.addItem(APP_ICON, 0, DESKTOP, 4, 3, testPackage10, 10, TMP_CONTENT_URI);

        int[] destHotseatItems = {
                -1,
                mModelHelper.addItem(SHORTCUT, 1, HOTSEAT, 0, 0, testPackage2),
                -1,
        };
        mModelHelper.addItem(APP_ICON, 0, DESKTOP, 2, 2, testPackage7);

        mIdp.numHotseatIcons = 4;
        mIdp.numColumns = 4;
        mIdp.numRows = 4;
        GridSizeMigrationTaskV2.DbReader srcReader = new GridSizeMigrationTaskV2.DbReader(mDb,
                LauncherSettings.Favorites.TMP_TABLE, mContext, mValidPackages,
                srcHotseatItems.length);
        GridSizeMigrationTaskV2.DbReader destReader = new GridSizeMigrationTaskV2.DbReader(mDb,
                LauncherSettings.Favorites.TABLE_NAME, mContext, mValidPackages,
                mIdp.numHotseatIcons);
        GridSizeMigrationTaskV2 task = new GridSizeMigrationTaskV2(mContext, mDb, srcReader,
                destReader, mIdp.numHotseatIcons, new Point(mIdp.numColumns, mIdp.numRows));
        task.migrate();

        // Check hotseat items
        Cursor c = mContext.getContentResolver().query(LauncherSettings.Favorites.CONTENT_URI,
                new String[]{LauncherSettings.Favorites.SCREEN, LauncherSettings.Favorites.INTENT},
                "container=" + CONTAINER_HOTSEAT, null, null, null);
        assertEquals(c.getCount(), mIdp.numHotseatIcons);
        int screenIndex = c.getColumnIndex(LauncherSettings.Favorites.SCREEN);
        int intentIndex = c.getColumnIndex(LauncherSettings.Favorites.INTENT);
        c.moveToNext();
        assertEquals(c.getInt(screenIndex), 1);
        assertTrue(c.getString(intentIndex).contains(testPackage2));
        c.moveToNext();
        assertEquals(c.getInt(screenIndex), 0);
        assertTrue(c.getString(intentIndex).contains(testPackage1));
        c.moveToNext();
        assertEquals(c.getInt(screenIndex), 2);
        assertTrue(c.getString(intentIndex).contains(testPackage3));
        c.moveToNext();
        assertEquals(c.getInt(screenIndex), 3);
        assertTrue(c.getString(intentIndex).contains(testPackage4));
        c.close();

        // Check workspace items
        c = mContext.getContentResolver().query(LauncherSettings.Favorites.CONTENT_URI,
                new String[]{LauncherSettings.Favorites.CELLX, LauncherSettings.Favorites.CELLY,
                        LauncherSettings.Favorites.INTENT},
                "container=" + CONTAINER_DESKTOP, null, null, null);
        assertEquals(c.getCount(), 6);
        intentIndex = c.getColumnIndex(LauncherSettings.Favorites.INTENT);
        int cellXIndex = c.getColumnIndex(LauncherSettings.Favorites.CELLX);
        int cellYIndex = c.getColumnIndex(LauncherSettings.Favorites.CELLY);

        c.moveToNext();
        assertTrue(c.getString(intentIndex).contains(testPackage7));
        c.moveToNext();
        assertTrue(c.getString(intentIndex).contains(testPackage6));
        assertEquals(c.getInt(cellXIndex), 0);
        assertEquals(c.getInt(cellYIndex), 3);
        c.moveToNext();
        assertTrue(c.getString(intentIndex).contains(testPackage10));
        assertEquals(c.getInt(cellXIndex), 1);
        assertEquals(c.getInt(cellYIndex), 3);
        c.moveToNext();
        assertTrue(c.getString(intentIndex).contains(testPackage5));
        assertEquals(c.getInt(cellXIndex), 2);
        assertEquals(c.getInt(cellYIndex), 3);
        c.moveToNext();
        assertTrue(c.getString(intentIndex).contains(testPackage9));
        assertEquals(c.getInt(cellXIndex), 3);
        assertEquals(c.getInt(cellYIndex), 3);
        c.moveToNext();
        assertTrue(c.getString(intentIndex).contains(testPackage8));
        assertEquals(c.getInt(cellXIndex), 0);
        assertEquals(c.getInt(cellYIndex), 2);
    }
}
