/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.model

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Point
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.WORKSPACE_SIZE
import com.android.launcher3.LauncherSettings.Favorites.*
import com.android.launcher3.config.FeatureFlags
import com.android.launcher3.model.GridSizeMigrationUtil.DbReader
import com.android.launcher3.pm.UserCache
import com.android.launcher3.provider.LauncherDbUtils
import com.android.launcher3.util.LauncherModelHelper
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [GridSizeMigrationUtil] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class GridSizeMigrationUtilTest {

    private lateinit var modelHelper: LauncherModelHelper
    private lateinit var context: Context
    private lateinit var validPackages: Set<String>
    private lateinit var idp: InvariantDeviceProfile
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var db: SQLiteDatabase
    private val testPackage1 = "com.android.launcher3.validpackage1"
    private val testPackage2 = "com.android.launcher3.validpackage2"
    private val testPackage3 = "com.android.launcher3.validpackage3"
    private val testPackage4 = "com.android.launcher3.validpackage4"
    private val testPackage5 = "com.android.launcher3.validpackage5"
    private val testPackage6 = "com.android.launcher3.validpackage6"
    private val testPackage7 = "com.android.launcher3.validpackage7"
    private val testPackage8 = "com.android.launcher3.validpackage8"
    private val testPackage9 = "com.android.launcher3.validpackage9"
    private val testPackage10 = "com.android.launcher3.validpackage10"

    @Before
    fun setUp() {
        modelHelper = LauncherModelHelper()
        context = modelHelper.sandboxContext
        dbHelper =
            DatabaseHelper(
                context,
                null,
                UserCache.INSTANCE.get(context)::getSerialNumberForUser
            ) {}
        db = dbHelper.writableDatabase

        validPackages =
            setOf(
                testPackage1,
                testPackage2,
                testPackage3,
                testPackage4,
                testPackage5,
                testPackage6,
                testPackage7,
                testPackage8,
                testPackage9,
                testPackage10
            )

        idp = InvariantDeviceProfile.INSTANCE[context]
        val userSerial = UserCache.INSTANCE[context].getSerialNumberForUser(Process.myUserHandle())
        LauncherDbUtils.dropTable(db, TMP_TABLE)
        addTableToDb(db, userSerial, false, TMP_TABLE)
    }

    @After
    fun tearDown() {
        modelHelper.destroy()
    }

    /**
     * Old migration logic, should be modified once [FeatureFlags.ENABLE_NEW_MIGRATION_LOGIC] is not
     * needed anymore
     */
    @Test
    @Throws(Exception::class)
    fun testMigration() {
        // Src Hotseat icons
        addItem(ITEM_TYPE_APPLICATION, 0, CONTAINER_HOTSEAT, 0, 0, testPackage1, 1, TMP_TABLE)
        addItem(ITEM_TYPE_DEEP_SHORTCUT, 1, CONTAINER_HOTSEAT, 0, 0, testPackage2, 2, TMP_TABLE)
        addItem(ITEM_TYPE_DEEP_SHORTCUT, 3, CONTAINER_HOTSEAT, 0, 0, testPackage3, 3, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 4, CONTAINER_HOTSEAT, 0, 0, testPackage4, 4, TMP_TABLE)
        // Src grid icons
        // _ _ _ _ _
        // _ _ _ _ 5
        // _ _ 6 _ 7
        // _ _ 8 _ 9
        // _ _ _ _ _
        addItem(ITEM_TYPE_APPLICATION, 0, CONTAINER_DESKTOP, 4, 1, testPackage5, 5, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 0, CONTAINER_DESKTOP, 2, 2, testPackage6, 6, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 0, CONTAINER_DESKTOP, 4, 2, testPackage7, 7, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 0, CONTAINER_DESKTOP, 2, 3, testPackage8, 8, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 0, CONTAINER_DESKTOP, 4, 3, testPackage9, 9, TMP_TABLE)

        // Dest hotseat icons
        addItem(ITEM_TYPE_DEEP_SHORTCUT, 1, CONTAINER_HOTSEAT, 0, 0, testPackage2)
        // Dest grid icons
        addItem(ITEM_TYPE_APPLICATION, 0, CONTAINER_DESKTOP, 2, 2, testPackage10)

        idp.numDatabaseHotseatIcons = 4
        idp.numColumns = 4
        idp.numRows = 4
        val srcReader = DbReader(db, TMP_TABLE, context, validPackages)
        val destReader = DbReader(db, TABLE_NAME, context, validPackages)
        GridSizeMigrationUtil.migrate(
            dbHelper,
            srcReader,
            destReader,
            idp.numDatabaseHotseatIcons,
            Point(idp.numColumns, idp.numRows),
            DeviceGridState(context),
            DeviceGridState(idp)
        )

        // Check hotseat items
        var c =
            db.query(
                TABLE_NAME,
                arrayOf(SCREEN, INTENT),
                "container=$CONTAINER_HOTSEAT",
                null,
                SCREEN,
                null,
                null
            )
                ?: throw IllegalStateException()

        assertThat(c.count).isEqualTo(idp.numDatabaseHotseatIcons)

        val screenIndex = c.getColumnIndex(SCREEN)
        var intentIndex = c.getColumnIndex(INTENT)
        c.moveToNext()
        assertThat(c.getInt(screenIndex).toLong()).isEqualTo(0)
        assertThat(c.getString(intentIndex)).contains(testPackage1)
        c.moveToNext()
        assertThat(c.getInt(screenIndex).toLong()).isEqualTo(1)
        assertThat(c.getString(intentIndex)).contains(testPackage2)
        c.moveToNext()
        assertThat(c.getInt(screenIndex).toLong()).isEqualTo(2)
        assertThat(c.getString(intentIndex)).contains(testPackage3)
        c.moveToNext()
        assertThat(c.getInt(screenIndex).toLong()).isEqualTo(3)
        assertThat(c.getString(intentIndex)).contains(testPackage4)
        c.close()

        // Check workspace items
        c =
            db.query(
                TABLE_NAME,
                arrayOf(CELLX, CELLY, INTENT),
                "container=$CONTAINER_DESKTOP",
                null,
                null,
                null,
                null
            )
                ?: throw IllegalStateException()

        intentIndex = c.getColumnIndex(INTENT)
        val cellXIndex = c.getColumnIndex(CELLX)
        val cellYIndex = c.getColumnIndex(CELLY)
        val locMap = HashMap<String?, Point>()
        while (c.moveToNext()) {
            locMap[Intent.parseUri(c.getString(intentIndex), 0).getPackage()] =
                Point(c.getInt(cellXIndex), c.getInt(cellYIndex))
        }
        c.close()
        // Expected dest grid icons
        // _ _ _ _
        // 5 6 7 8
        // 9 _ _ _
        // _ _ _ _
        assertThat(locMap.size.toLong()).isEqualTo(5)
        assertThat(locMap[testPackage5]).isEqualTo(Point(0, 1))
        assertThat(locMap[testPackage6]).isEqualTo(Point(1, 1))
        assertThat(locMap[testPackage7]).isEqualTo(Point(2, 1))
        assertThat(locMap[testPackage8]).isEqualTo(Point(3, 1))
        assertThat(locMap[testPackage9]).isEqualTo(Point(0, 2))
    }

    /**
     * Old migration logic, should be modified once [FeatureFlags.ENABLE_NEW_MIGRATION_LOGIC] is not
     * needed anymore
     */
    @Test
    @Throws(Exception::class)
    fun testMigrationBackAndForth() {
        // Hotseat items in grid A
        // 1 2 _ 3 4
        addItem(ITEM_TYPE_APPLICATION, 0, CONTAINER_HOTSEAT, 0, 0, testPackage1, 1, TMP_TABLE)
        addItem(ITEM_TYPE_DEEP_SHORTCUT, 1, CONTAINER_HOTSEAT, 0, 0, testPackage2, 2, TMP_TABLE)
        addItem(ITEM_TYPE_DEEP_SHORTCUT, 3, CONTAINER_HOTSEAT, 0, 0, testPackage3, 3, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 4, CONTAINER_HOTSEAT, 0, 0, testPackage4, 4, TMP_TABLE)
        // Workspace items in grid A
        // _ _ _ _ _
        // _ _ _ _ 5
        // _ _ 6 _ 7
        // _ _ 8 _ _
        // _ _ _ _ _
        addItem(ITEM_TYPE_APPLICATION, 0, CONTAINER_DESKTOP, 4, 1, testPackage5, 5, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 0, CONTAINER_DESKTOP, 2, 2, testPackage6, 6, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 0, CONTAINER_DESKTOP, 4, 2, testPackage7, 7, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 0, CONTAINER_DESKTOP, 2, 3, testPackage8, 8, TMP_TABLE)

        // Hotseat items in grid B
        // 2 _ _ _
        addItem(ITEM_TYPE_DEEP_SHORTCUT, 0, CONTAINER_HOTSEAT, 0, 0, testPackage2)
        // Workspace items in grid B
        // _ _ _ _
        // _ _ _ 10
        // _ _ _ _
        // _ _ _ _
        addItem(ITEM_TYPE_APPLICATION, 0, CONTAINER_DESKTOP, 1, 3, testPackage10)

        idp.numDatabaseHotseatIcons = 4
        idp.numColumns = 4
        idp.numRows = 4
        val readerGridA = DbReader(db, TMP_TABLE, context, validPackages)
        val readerGridB = DbReader(db, TABLE_NAME, context, validPackages)
        // migrate from A -> B
        GridSizeMigrationUtil.migrate(
            dbHelper,
            readerGridA,
            readerGridB,
            idp.numDatabaseHotseatIcons,
            Point(idp.numColumns, idp.numRows),
            DeviceGridState(context),
            DeviceGridState(idp)
        )

        // Check hotseat items in grid B
        var c =
            db.query(
                TABLE_NAME,
                arrayOf(SCREEN, INTENT),
                "container=$CONTAINER_HOTSEAT",
                null,
                SCREEN,
                null,
                null
            )
                ?: throw IllegalStateException()
        // Expected hotseat items in grid B
        // 2 1 3 4
        verifyHotseat(
            c,
            idp,
            mutableListOf(testPackage2, testPackage1, testPackage3, testPackage4).toList()
        )

        // Check workspace items in grid B
        c =
            db.query(
                TABLE_NAME,
                arrayOf(SCREEN, CELLX, CELLY, INTENT),
                "container=$CONTAINER_DESKTOP",
                null,
                null,
                null,
                null
            )
                ?: throw IllegalStateException()
        var locMap = parseLocMap(c)
        // Expected items in grid B
        // _ _ _ _
        // 5 6 7 8
        // _ _ _ _
        // _ _ _ _
        assertThat(locMap.size.toLong()).isEqualTo(4)
        assertThat(locMap[testPackage5]).isEqualTo(Triple(0, 0, 1))
        assertThat(locMap[testPackage6]).isEqualTo(Triple(0, 1, 1))
        assertThat(locMap[testPackage7]).isEqualTo(Triple(0, 2, 1))
        assertThat(locMap[testPackage8]).isEqualTo(Triple(0, 3, 1))

        // add item in B
        addItem(ITEM_TYPE_APPLICATION, 0, CONTAINER_DESKTOP, 0, 2, testPackage9)

        // migrate from B -> A
        GridSizeMigrationUtil.migrate(
            dbHelper,
            readerGridB,
            readerGridA,
            5,
            Point(5, 5),
            DeviceGridState(idp),
            DeviceGridState(context)
        )
        // Check hotseat items in grid A
        c =
            db.query(
                TMP_TABLE,
                arrayOf(SCREEN, INTENT),
                "container=$CONTAINER_HOTSEAT",
                null,
                SCREEN,
                null,
                null
            )
                ?: throw IllegalStateException()
        // Expected hotseat items in grid A
        // 1 2 _ 3 4
        verifyHotseat(
            c,
            idp,
            mutableListOf(testPackage1, testPackage2, null, testPackage3, testPackage4).toList()
        )

        // Check workspace items in grid A
        c =
            db.query(
                TMP_TABLE,
                arrayOf(SCREEN, CELLX, CELLY, INTENT),
                "container=$CONTAINER_DESKTOP",
                null,
                null,
                null,
                null
            )
                ?: throw IllegalStateException()
        locMap = parseLocMap(c)
        // Expected workspace items in grid A
        // _ _ _ _ _
        // _ _ _ _ 5
        // 9 _ 6 _ 7
        // _ _ 8 _ _
        // _ _ _ _ _
        assertThat(locMap.size.toLong()).isEqualTo(5)
        // Verify items that existed in grid A remains in same position
        assertThat(locMap[testPackage5]).isEqualTo(Triple(0, 4, 1))
        assertThat(locMap[testPackage6]).isEqualTo(Triple(0, 2, 2))
        assertThat(locMap[testPackage7]).isEqualTo(Triple(0, 4, 2))
        assertThat(locMap[testPackage8]).isEqualTo(Triple(0, 2, 3))
        // Verify items that didn't exist in grid A are added in new screen
        assertThat(locMap[testPackage9]).isEqualTo(Triple(0, 0, 2))

        // remove item from B
        db.delete(TMP_TABLE, "$_ID=7", null)

        // migrate from A -> B
        GridSizeMigrationUtil.migrate(
            dbHelper,
            readerGridA,
            readerGridB,
            idp.numDatabaseHotseatIcons,
            Point(idp.numColumns, idp.numRows),
            DeviceGridState(context),
            DeviceGridState(idp)
        )

        // Check hotseat items in grid B
        c =
            db.query(
                TABLE_NAME,
                arrayOf(SCREEN, INTENT),
                "container=$CONTAINER_HOTSEAT",
                null,
                SCREEN,
                null,
                null
            )
                ?: throw IllegalStateException()
        // Expected hotseat items in grid B
        // 2 1 3 4
        verifyHotseat(
            c,
            idp,
            mutableListOf(testPackage2, testPackage1, testPackage3, testPackage4).toList()
        )

        // Check workspace items in grid B
        c =
            db.query(
                TABLE_NAME,
                arrayOf(SCREEN, CELLX, CELLY, INTENT),
                "container=$CONTAINER_DESKTOP",
                null,
                null,
                null,
                null
            )
                ?: throw IllegalStateException()
        locMap = parseLocMap(c)
        // Expected workspace items in grid B
        // _ _ _ _
        // 5 6 _ 8
        // 9 _ _ _
        // _ _ _ _
        assertThat(locMap.size.toLong()).isEqualTo(4)
        assertThat(locMap[testPackage5]).isEqualTo(Triple(0, 0, 1))
        assertThat(locMap[testPackage6]).isEqualTo(Triple(0, 1, 1))
        assertThat(locMap[testPackage8]).isEqualTo(Triple(0, 3, 1))
        assertThat(locMap[testPackage9]).isEqualTo(Triple(0, 0, 2))
    }

    private fun verifyHotseat(c: Cursor, idp: InvariantDeviceProfile, expected: List<String?>) {
        assertThat(c.count).isEqualTo(idp.numDatabaseHotseatIcons)
        val screenIndex = c.getColumnIndex(SCREEN)
        val intentIndex = c.getColumnIndex(INTENT)
        expected.forEachIndexed { idx, pkg ->
            if (pkg == null) return@forEachIndexed
            c.moveToNext()
            assertThat(c.getInt(screenIndex).toLong()).isEqualTo(idx)
            assertThat(c.getString(intentIndex)).contains(pkg)
        }
        c.close()
    }

    private fun parseLocMap(c: Cursor): Map<String?, Triple<Int, Int, Int>> {
        // Check workspace items
        val intentIndex = c.getColumnIndex(INTENT)
        val screenIndex = c.getColumnIndex(SCREEN)
        val cellXIndex = c.getColumnIndex(CELLX)
        val cellYIndex = c.getColumnIndex(CELLY)
        val locMap = mutableMapOf<String?, Triple<Int, Int, Int>>()
        while (c.moveToNext()) {
            locMap[Intent.parseUri(c.getString(intentIndex), 0).getPackage()] =
                Triple(c.getInt(screenIndex), c.getInt(cellXIndex), c.getInt(cellYIndex))
        }
        c.close()
        return locMap.toMap()
    }

    @Test
    fun migrateToLargerHotseat() {
        val srcHotseatItems =
            intArrayOf(
                addItem(
                    ITEM_TYPE_APPLICATION,
                    0,
                    CONTAINER_HOTSEAT,
                    0,
                    0,
                    testPackage1,
                    1,
                    TMP_TABLE
                ),
                addItem(
                    ITEM_TYPE_DEEP_SHORTCUT,
                    1,
                    CONTAINER_HOTSEAT,
                    0,
                    0,
                    testPackage2,
                    2,
                    TMP_TABLE
                ),
                addItem(
                    ITEM_TYPE_APPLICATION,
                    2,
                    CONTAINER_HOTSEAT,
                    0,
                    0,
                    testPackage3,
                    3,
                    TMP_TABLE
                ),
                addItem(
                    ITEM_TYPE_DEEP_SHORTCUT,
                    3,
                    CONTAINER_HOTSEAT,
                    0,
                    0,
                    testPackage4,
                    4,
                    TMP_TABLE
                )
            )
        val numSrcDatabaseHotseatIcons = srcHotseatItems.size
        idp.numDatabaseHotseatIcons = 6
        idp.numColumns = 4
        idp.numRows = 4
        val srcReader = DbReader(db, TMP_TABLE, context, validPackages)
        val destReader = DbReader(db, TABLE_NAME, context, validPackages)
        GridSizeMigrationUtil.migrate(
            dbHelper,
            srcReader,
            destReader,
            idp.numDatabaseHotseatIcons,
            Point(idp.numColumns, idp.numRows),
            DeviceGridState(context),
            DeviceGridState(idp)
        )

        // Check hotseat items
        val c =
            db.query(
                TABLE_NAME,
                arrayOf(SCREEN, INTENT),
                "container=$CONTAINER_HOTSEAT",
                null,
                SCREEN,
                null,
                null
            )
                ?: throw IllegalStateException()

        assertThat(c.count.toLong()).isEqualTo(numSrcDatabaseHotseatIcons.toLong())
        val screenIndex = c.getColumnIndex(SCREEN)
        val intentIndex = c.getColumnIndex(INTENT)
        c.moveToNext()
        assertThat(c.getInt(screenIndex)).isEqualTo(0)
        assertThat(c.getString(intentIndex)).contains(testPackage1)

        c.moveToNext()
        assertThat(c.getInt(screenIndex)).isEqualTo(1)
        assertThat(c.getString(intentIndex)).contains(testPackage2)

        c.moveToNext()
        assertThat(c.getInt(screenIndex)).isEqualTo(2)
        assertThat(c.getString(intentIndex)).contains(testPackage3)

        c.moveToNext()
        assertThat(c.getInt(screenIndex)).isEqualTo(3)
        assertThat(c.getString(intentIndex)).contains(testPackage4)

        c.close()
    }

    @Test
    fun migrateFromLargerHotseat() {
        addItem(ITEM_TYPE_APPLICATION, 0, CONTAINER_HOTSEAT, 0, 0, testPackage1, 1, TMP_TABLE)
        addItem(ITEM_TYPE_DEEP_SHORTCUT, 2, CONTAINER_HOTSEAT, 0, 0, testPackage2, 2, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 3, CONTAINER_HOTSEAT, 0, 0, testPackage3, 3, TMP_TABLE)
        addItem(ITEM_TYPE_DEEP_SHORTCUT, 4, CONTAINER_HOTSEAT, 0, 0, testPackage4, 4, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 5, CONTAINER_HOTSEAT, 0, 0, testPackage5, 5, TMP_TABLE)

        idp.numDatabaseHotseatIcons = 4
        idp.numColumns = 4
        idp.numRows = 4
        val srcReader = DbReader(db, TMP_TABLE, context, validPackages)
        val destReader = DbReader(db, TABLE_NAME, context, validPackages)
        GridSizeMigrationUtil.migrate(
            dbHelper,
            srcReader,
            destReader,
            idp.numDatabaseHotseatIcons,
            Point(idp.numColumns, idp.numRows),
            DeviceGridState(context),
            DeviceGridState(idp)
        )

        // Check hotseat items
        val c =
            db.query(
                TABLE_NAME,
                arrayOf(SCREEN, INTENT),
                "container=$CONTAINER_HOTSEAT",
                null,
                SCREEN,
                null,
                null
            )
                ?: throw IllegalStateException()

        assertThat(c.count.toLong()).isEqualTo(idp.numDatabaseHotseatIcons.toLong())
        val screenIndex = c.getColumnIndex(SCREEN)
        val intentIndex = c.getColumnIndex(INTENT)

        c.moveToNext()
        assertThat(c.getInt(screenIndex)).isEqualTo(0)
        assertThat(c.getString(intentIndex)).contains(testPackage1)

        c.moveToNext()
        assertThat(c.getInt(screenIndex)).isEqualTo(1)
        assertThat(c.getString(intentIndex)).contains(testPackage2)

        c.moveToNext()
        assertThat(c.getInt(screenIndex)).isEqualTo(2)
        assertThat(c.getString(intentIndex)).contains(testPackage3)

        c.moveToNext()
        assertThat(c.getInt(screenIndex)).isEqualTo(3)
        assertThat(c.getString(intentIndex)).contains(testPackage4)

        c.close()
    }

    /**
     * Migrating from a smaller grid to a large one should keep the pages if the column difference
     * is less than 2
     */
    @Test
    @Throws(Exception::class)
    fun migrateFromSmallerGridSmallDifference() {
        enableNewMigrationLogic("4,4")

        // Setup src grid
        addItem(ITEM_TYPE_APPLICATION, 0, CONTAINER_DESKTOP, 2, 2, testPackage1, 5, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 0, CONTAINER_DESKTOP, 2, 3, testPackage2, 6, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 1, CONTAINER_DESKTOP, 3, 1, testPackage3, 7, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 1, CONTAINER_DESKTOP, 3, 2, testPackage4, 8, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 2, CONTAINER_DESKTOP, 3, 3, testPackage5, 9, TMP_TABLE)

        idp.numDatabaseHotseatIcons = 4
        idp.numColumns = 6
        idp.numRows = 5

        val srcReader = DbReader(db, TMP_TABLE, context, validPackages)
        val destReader = DbReader(db, TABLE_NAME, context, validPackages)
        GridSizeMigrationUtil.migrate(
            dbHelper,
            srcReader,
            destReader,
            idp.numDatabaseHotseatIcons,
            Point(idp.numColumns, idp.numRows),
            DeviceGridState(context),
            DeviceGridState(idp)
        )

        // Get workspace items
        val c =
            db.query(
                TABLE_NAME,
                arrayOf(INTENT, SCREEN),
                "container=$CONTAINER_DESKTOP",
                null,
                null,
                null,
                null
            )
                ?: throw IllegalStateException()
        val intentIndex = c.getColumnIndex(INTENT)
        val screenIndex = c.getColumnIndex(SCREEN)

        // Get in which screen the icon is
        val locMap = HashMap<String?, Int>()
        while (c.moveToNext()) {
            locMap[Intent.parseUri(c.getString(intentIndex), 0).getPackage()] =
                c.getInt(screenIndex)
        }
        c.close()
        assertThat(locMap.size).isEqualTo(5)
        assertThat(locMap[testPackage1]).isEqualTo(0)
        assertThat(locMap[testPackage2]).isEqualTo(0)
        assertThat(locMap[testPackage3]).isEqualTo(1)
        assertThat(locMap[testPackage4]).isEqualTo(1)
        assertThat(locMap[testPackage5]).isEqualTo(2)
    }

    /**
     * Migrating from a smaller grid to a large one should reflow the pages if the column difference
     * is more than 2
     */
    @Test
    @Throws(Exception::class)
    fun migrateFromSmallerGridBigDifference() {
        enableNewMigrationLogic("2,2")

        // Setup src grid
        addItem(ITEM_TYPE_APPLICATION, 0, CONTAINER_DESKTOP, 0, 1, testPackage1, 5, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 0, CONTAINER_DESKTOP, 1, 1, testPackage2, 6, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 1, CONTAINER_DESKTOP, 0, 0, testPackage3, 7, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 1, CONTAINER_DESKTOP, 1, 0, testPackage4, 8, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 2, CONTAINER_DESKTOP, 0, 0, testPackage5, 9, TMP_TABLE)

        idp.numDatabaseHotseatIcons = 4
        idp.numColumns = 5
        idp.numRows = 5
        val srcReader = DbReader(db, TMP_TABLE, context, validPackages)
        val destReader = DbReader(db, TABLE_NAME, context, validPackages)
        GridSizeMigrationUtil.migrate(
            dbHelper,
            srcReader,
            destReader,
            idp.numDatabaseHotseatIcons,
            Point(idp.numColumns, idp.numRows),
            DeviceGridState(context),
            DeviceGridState(idp)
        )

        // Get workspace items
        val c =
            db.query(
                TABLE_NAME,
                arrayOf(INTENT, SCREEN),
                "container=$CONTAINER_DESKTOP",
                null,
                null,
                null,
                null
            )
                ?: throw IllegalStateException()

        val intentIndex = c.getColumnIndex(INTENT)
        val screenIndex = c.getColumnIndex(SCREEN)

        // Get in which screen the icon is
        val locMap = HashMap<String?, Int>()
        while (c.moveToNext()) {
            locMap[Intent.parseUri(c.getString(intentIndex), 0).getPackage()] =
                c.getInt(screenIndex)
        }
        c.close()

        // All icons fit the first screen
        assertThat(locMap.size).isEqualTo(5)
        assertThat(locMap[testPackage1]).isEqualTo(0)
        assertThat(locMap[testPackage2]).isEqualTo(0)
        assertThat(locMap[testPackage3]).isEqualTo(0)
        assertThat(locMap[testPackage4]).isEqualTo(0)
        assertThat(locMap[testPackage5]).isEqualTo(0)
    }

    /** Migrating from a larger grid to a smaller, we reflow from page 0 */
    @Test
    @Throws(Exception::class)
    fun migrateFromLargerGrid() {
        enableNewMigrationLogic("5,5")

        // Setup src grid
        addItem(ITEM_TYPE_APPLICATION, 0, CONTAINER_DESKTOP, 0, 1, testPackage1, 5, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 0, CONTAINER_DESKTOP, 1, 1, testPackage2, 6, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 1, CONTAINER_DESKTOP, 0, 0, testPackage3, 7, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 1, CONTAINER_DESKTOP, 1, 0, testPackage4, 8, TMP_TABLE)
        addItem(ITEM_TYPE_APPLICATION, 2, CONTAINER_DESKTOP, 0, 0, testPackage5, 9, TMP_TABLE)

        idp.numDatabaseHotseatIcons = 4
        idp.numColumns = 4
        idp.numRows = 4
        val srcReader = DbReader(db, TMP_TABLE, context, validPackages)
        val destReader = DbReader(db, TABLE_NAME, context, validPackages)
        GridSizeMigrationUtil.migrate(
            dbHelper,
            srcReader,
            destReader,
            idp.numDatabaseHotseatIcons,
            Point(idp.numColumns, idp.numRows),
            DeviceGridState(context),
            DeviceGridState(idp)
        )

        // Get workspace items
        val c =
            db.query(
                TABLE_NAME,
                arrayOf(INTENT, SCREEN),
                "container=$CONTAINER_DESKTOP",
                null,
                null,
                null,
                null
            )
                ?: throw IllegalStateException()
        val intentIndex = c.getColumnIndex(INTENT)
        val screenIndex = c.getColumnIndex(SCREEN)

        // Get in which screen the icon is
        val locMap = HashMap<String?, Int>()
        while (c.moveToNext()) {
            locMap[Intent.parseUri(c.getString(intentIndex), 0).getPackage()] =
                c.getInt(screenIndex)
        }
        c.close()

        // All icons fit the first screen
        assertThat(locMap.size).isEqualTo(5)
        assertThat(locMap[testPackage1]).isEqualTo(0)
        assertThat(locMap[testPackage2]).isEqualTo(0)
        assertThat(locMap[testPackage3]).isEqualTo(0)
        assertThat(locMap[testPackage4]).isEqualTo(0)
        assertThat(locMap[testPackage5]).isEqualTo(0)
    }

    private fun enableNewMigrationLogic(srcGridSize: String) {
        LauncherPrefs.get(context).putSync(WORKSPACE_SIZE.to(srcGridSize))
    }

    private fun addItem(
        type: Int,
        screen: Int,
        container: Int,
        x: Int,
        y: Int,
        packageName: String?
    ): Int {
        return addItem(
            type,
            screen,
            container,
            x,
            y,
            packageName,
            dbHelper.generateNewItemId(),
            TABLE_NAME
        )
    }

    private fun addItem(
        type: Int,
        screen: Int,
        container: Int,
        x: Int,
        y: Int,
        packageName: String?,
        id: Int,
        tableName: String
    ): Int {
        val values = ContentValues()
        values.put(_ID, id)
        values.put(CONTAINER, container)
        values.put(SCREEN, screen)
        values.put(CELLX, x)
        values.put(CELLY, y)
        values.put(SPANX, 1)
        values.put(SPANY, 1)
        values.put(ITEM_TYPE, type)
        values.put(INTENT, Intent(Intent.ACTION_MAIN).setPackage(packageName).toUri(0))
        db.insert(tableName, null, values)
        return id
    }
}
