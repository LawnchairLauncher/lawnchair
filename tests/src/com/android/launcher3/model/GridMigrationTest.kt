/*
 * Copyright (C) 2024 The Android Open Source Project
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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.InvariantDeviceProfile.TYPE_PHONE
import com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME
import com.android.launcher3.celllayout.board.CellLayoutBoard
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.rule.TestToPhoneFileCopier
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val phoneContext = InstrumentationRegistry.getInstrumentation().targetContext

data class EntryData(val x: Int, val y: Int, val spanX: Int, val spanY: Int, val rank: Int)

/**
 * Holds the data needed to run a test in GridMigrationTest, usually we would have a src
 * GridMigrationData and a dst GridMigrationData meaning the data after a migration has occurred.
 * This class holds a gridState, which is the size of the grid like 5x5 (among other things). a
 * dbHelper which contains the readable database and writable database used to migrate the
 * databases.
 *
 * You can also get all the entries defined in the dbHelper database.
 */
class GridMigrationData(dbFileName: String?, val gridState: DeviceGridState) {

    val dbHelper: DatabaseHelper =
        DatabaseHelper(
            phoneContext,
            dbFileName,
            { UserCache.INSTANCE.get(phoneContext).getSerialNumberForUser(it) },
            {}
        )

    fun readEntries(): List<GridSizeMigrationUtil.DbEntry> =
        GridSizeMigrationUtil.readAllEntries(dbHelper.readableDatabase, TABLE_NAME, phoneContext)
}

/**
 * Test the migration of a database from one size to another. It reads a database from the test
 * assets, uploads it into the phone and migrates the database to a database in memory which is
 * later compared against a database in the test assets to make sure they are identical.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class GridMigrationTest {
    private val DB_FILE = "test_launcher.db"

    // Copying the src db for all tests.
    @JvmField
    @Rule
    val fileCopier =
        TestToPhoneFileCopier("databases/GridMigrationTest/$DB_FILE", "databases/$DB_FILE", true)

    private fun migrate(src: GridMigrationData, dst: GridMigrationData) {
        GridSizeMigrationUtil.migrateGridIfNeeded(
            phoneContext,
            src.gridState,
            dst.gridState,
            dst.dbHelper,
            src.dbHelper.readableDatabase
        )
    }

    /**
     * Makes sure that none of the items overlaps on the result, i.e. no widget or icons share the
     * same space in the db.
     */
    private fun validateDb(data: GridMigrationData) {
        val cellLayoutBoard = CellLayoutBoard(data.gridState.columns, data.gridState.rows)
        data.readEntries().forEach {
            assert(cellLayoutBoard.isEmpty(it.cellX, it.cellY, it.spanX, it.spanY)) {
                "Db has overlapping items"
            }
            cellLayoutBoard.addWidget(it.cellX, it.cellY, it.spanX, it.spanY)
        }
    }

    private fun compare(dst: GridMigrationData, target: GridMigrationData) {
        val sortX = { it: GridSizeMigrationUtil.DbEntry -> it.cellX }
        val sortY = { it: GridSizeMigrationUtil.DbEntry -> it.cellX }
        val mapF = { it: GridSizeMigrationUtil.DbEntry ->
            EntryData(it.cellX, it.cellY, it.spanX, it.spanY, it.rank)
        }
        val entriesDst = dst.readEntries().sortedBy(sortX).sortedBy(sortY).map(mapF)
        val entriesTarget = target.readEntries().sortedBy(sortX).sortedBy(sortY).map(mapF)
        assert(entriesDst == entriesTarget) {
            "The elements on the dst database is not the same as in the target"
        }
    }

    /**
     * Migrate src into dst and compare to target. This method validates 3 things:
     * 1. dst has the same number of items as src after the migration, meaning, none of the items
     *    were removed during the migration.
     * 2. dst is valid, meaning that none of the items overlap with each other.
     * 3. dst is equal to target to ensure we don't unintentionally change the migration logic.
     */
    private fun runTest(src: GridMigrationData, dst: GridMigrationData, target: GridMigrationData) {
        migrate(src, dst)
        assert(src.readEntries().size == dst.readEntries().size) {
            "Source db and destination db do not contain the same number of elements"
        }
        validateDb(dst)
        compare(dst, target)
    }

    @JvmField
    @Rule
    val result5x5to3x3 =
        TestToPhoneFileCopier(
            "databases/GridMigrationTest/result5x5to3x3.db",
            "databases/result5x5to3x3.db",
            true
        )

    @Test
    fun `5x5 to 3x3`() =
        runTest(
            src = GridMigrationData(DB_FILE, DeviceGridState(5, 5, 5, TYPE_PHONE, DB_FILE)),
            dst =
                GridMigrationData(
                    null, // in memory db, to download a new db change null for the filename of the
                    // db name to store it. Do not use existing names.
                    DeviceGridState(3, 3, 3, TYPE_PHONE, "")
                ),
            target =
                GridMigrationData("result5x5to3x3.db", DeviceGridState(3, 3, 3, TYPE_PHONE, ""))
        )

    @JvmField
    @Rule
    val result5x5to4x7 =
        TestToPhoneFileCopier(
            "databases/GridMigrationTest/result5x5to4x7.db",
            "databases/result5x5to4x7.db",
            true
        )

    @Test
    fun `5x5 to 4x7`() =
        runTest(
            src = GridMigrationData(DB_FILE, DeviceGridState(5, 5, 5, TYPE_PHONE, DB_FILE)),
            dst =
                GridMigrationData(
                    null, // in memory db, to download a new db change null for the filename of the
                    // db name to store it. Do not use existing names.
                    DeviceGridState(4, 7, 4, TYPE_PHONE, "")
                ),
            target =
                GridMigrationData("result5x5to4x7.db", DeviceGridState(4, 7, 4, TYPE_PHONE, ""))
        )

    @JvmField
    @Rule
    val result5x5to5x8 =
        TestToPhoneFileCopier(
            "databases/GridMigrationTest/result5x5to5x8.db",
            "databases/result5x5to5x8.db",
            true
        )

    @Test
    fun `5x5 to 5x8`() =
        runTest(
            src = GridMigrationData(DB_FILE, DeviceGridState(5, 5, 5, TYPE_PHONE, DB_FILE)),
            dst =
                GridMigrationData(
                    null, // in memory db, to download a new db change null for the filename of the
                    // db name to store it. Do not use existing names.
                    DeviceGridState(5, 8, 5, TYPE_PHONE, "")
                ),
            target =
                GridMigrationData("result5x5to5x8.db", DeviceGridState(5, 8, 5, TYPE_PHONE, ""))
        )
}
