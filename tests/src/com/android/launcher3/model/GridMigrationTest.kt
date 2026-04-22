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

import android.platform.test.flag.junit.SetFlagsRule
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.Flags
import com.android.launcher3.GridType.Companion.GRID_TYPE_ANY
import com.android.launcher3.InvariantDeviceProfile.TYPE_PHONE
import com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME
import com.android.launcher3.celllayout.board.CellLayoutBoard
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.rule.TestToPhoneFileCopier
import com.android.launcher3.util.rule.setFlags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

private val phoneContext = InstrumentationRegistry.getInstrumentation().targetContext

data class EntryData(
    val x: Int,
    val y: Int,
    val screenId: Int,
    val spanX: Int,
    val spanY: Int,
    val rank: Int,
)

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
            {},
        )

    fun readEntries(): List<DbEntry> =
        GridSizeMigrationDBController.readAllEntries(
            dbHelper.readableDatabase,
            TABLE_NAME,
            phoneContext,
        )
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

    @JvmField
    @Rule
    val setFlagsRule = SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT)

    val modelDelegate = mock<ModelDelegate>()

    @Before
    fun setup() {
        setFlagsRule.setFlags(true, Flags.FLAG_ONE_GRID_SPECS)
    }

    private fun migrate(src: GridMigrationData, dst: GridMigrationData) {
        if (Flags.gridMigrationRefactor()) {
            val gridSizeMigrationLogic = GridSizeMigrationLogic()
            gridSizeMigrationLogic.migrateGrid(
                phoneContext,
                src.gridState,
                dst.gridState,
                dst.dbHelper,
                src.dbHelper.readableDatabase,
                true,
                modelDelegate,
            )
        } else {
            GridSizeMigrationDBController.migrateGridIfNeeded(
                phoneContext,
                src.gridState,
                dst.gridState,
                dst.dbHelper,
                src.dbHelper.readableDatabase,
                true,
                modelDelegate,
            )
        }
    }

    /**
     * Makes sure that none of the items overlaps on the result, i.e. no widget or icons share the
     * same space in the db.
     */
    private fun validateDb(data: GridMigrationData) {
        // The array size is just a big enough number to fit all the number of workspaces
        val boards = Array(100) { CellLayoutBoard(data.gridState.columns, data.gridState.rows) }
        data.readEntries().forEach {
            val cellLayoutBoard = boards[it.screenId]
            assert(cellLayoutBoard.isEmpty(it.cellX, it.cellY, it.spanX, it.spanY)) {
                "Db has overlapping items"
            }
            cellLayoutBoard.addWidget(it.cellX, it.cellY, it.spanX, it.spanY)
        }
    }

    private fun compare(dst: GridMigrationData, target: GridMigrationData, src: GridMigrationData) {
        val sort = compareBy<DbEntry>({ it.screenId }, { it.cellX }, { it.cellY })
        val mapF = { it: DbEntry ->
            EntryData(it.cellX, it.cellY, it.screenId, it.spanX, it.spanY, it.rank)
        }
        val entriesDst = dst.readEntries().sortedWith(sort).map(mapF)
        val entriesTarget = target.readEntries().sortedWith(sort).map(mapF)
        val entriesSrc = src.readEntries().sortedWith(sort).map(mapF)
        Log.i(
            TAG,
            "entriesSrc: $entriesSrc\n entriesDst: $entriesDst\n entriesTarget: $entriesTarget",
        )
        assert(entriesDst == entriesTarget) {
            "The elements on the dst database is not the same as in the target"
        }
    }

    /**
     * Migrate src into dst and compare to target. This method validates 4 things:
     * 1. dst has the same number of items as src after the migration, meaning, none of the items
     *    were removed during the migration.
     * 2. dst is valid, meaning that none of the items overlap with each other.
     * 3. dst is equal to target to ensure we don't unintentionally change the migration logic.
     * 4. migration notifies the complete callback.
     */
    private fun runTest(src: GridMigrationData, dst: GridMigrationData, target: GridMigrationData) {
        migrate(src, dst)
        assert(src.readEntries().size == dst.readEntries().size) {
            "Source db and destination db do not contain the same number of elements"
        }
        validateDb(dst)
        compare(dst, target, src)
        verify(modelDelegate).gridMigrationComplete(src.gridState, dst.gridState)
    }

    // Copying the src db for all tests.
    @JvmField
    @Rule
    val fileCopier =
        TestToPhoneFileCopier(
            src = "databases/GridMigrationTest/$DB_FILE",
            dest = "databases/$DB_FILE",
            removeOnFinish = true,
        )

    @JvmField
    @Rule
    val result5x5to3x3 =
        TestToPhoneFileCopier(
            src = "databases/GridMigrationTest/result5x5to3x3.db",
            dest = "databases/result5x5to3x3.db",
            removeOnFinish = true,
        )

    @Test
    fun `5x5 to 3x3`() =
        runTest(
            src =
                GridMigrationData(
                    DB_FILE,
                    DeviceGridState(5, 5, 5, TYPE_PHONE, DB_FILE, GRID_TYPE_ANY),
                ),
            dst =
                GridMigrationData(
                    null, // in memory db, to download a new db change null for
                    // the filename of the db name to store it. Do not use existing names.
                    DeviceGridState(3, 3, 3, TYPE_PHONE, "", GRID_TYPE_ANY),
                ),
            target =
                GridMigrationData(
                    "result5x5to3x3.db",
                    DeviceGridState(3, 3, 3, TYPE_PHONE, "", GRID_TYPE_ANY),
                ),
        )

    @JvmField
    @Rule
    val result5x5to4x7 =
        TestToPhoneFileCopier(
            src = "databases/GridMigrationTest/result5x5to4x7.db",
            dest = "databases/result5x5to4x7.db",
            removeOnFinish = true,
        )

    @Test
    fun `5x5 to 4x7`() =
        runTest(
            src =
                GridMigrationData(
                    DB_FILE,
                    DeviceGridState(5, 5, 5, TYPE_PHONE, DB_FILE, GRID_TYPE_ANY),
                ),
            dst =
                GridMigrationData(
                    null, // in memory db, to download a new db change null for
                    // the filename of the db name to store it. Do not use existing names.
                    DeviceGridState(4, 7, 4, TYPE_PHONE, "", GRID_TYPE_ANY),
                ),
            target =
                GridMigrationData(
                    "result5x5to4x7.db",
                    DeviceGridState(4, 7, 4, TYPE_PHONE, "", GRID_TYPE_ANY),
                ),
        )

    @JvmField
    @Rule
    val result5x5to5x8 =
        TestToPhoneFileCopier(
            src = "databases/GridMigrationTest/result5x5to5x8.db",
            dest = "databases/result5x5to5x8.db",
            removeOnFinish = true,
        )

    @Test
    fun `5x5 to 5x8`() =
        runTest(
            src =
                GridMigrationData(
                    DB_FILE,
                    DeviceGridState(5, 5, 5, TYPE_PHONE, DB_FILE, GRID_TYPE_ANY),
                ),
            dst =
                GridMigrationData(
                    null, // in memory db, to download a new db change null
                    // for
                    // the filename of the db name to store it. Do not use existing names.
                    DeviceGridState(5, 8, 5, TYPE_PHONE, "", GRID_TYPE_ANY),
                ),
            target =
                GridMigrationData(
                    "result5x5to5x8.db",
                    DeviceGridState(5, 8, 5, TYPE_PHONE, "", GRID_TYPE_ANY),
                ),
        )

    companion object {
        private const val TAG = "GridMigrationTest"
    }
}
