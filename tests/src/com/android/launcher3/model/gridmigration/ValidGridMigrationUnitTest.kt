/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.launcher3.model.gridmigration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Point
import android.os.Process
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.celllayout.testgenerator.ValidGridMigrationTestCaseGenerator
import com.android.launcher3.celllayout.testgenerator.generateItemsForTest
import com.android.launcher3.model.DatabaseHelper
import com.android.launcher3.model.DeviceGridState
import com.android.launcher3.model.GridSizeMigrationUtil
import com.android.launcher3.pm.UserCache
import com.android.launcher3.provider.LauncherDbUtils
import com.android.launcher3.util.rule.TestStabilityRule
import com.android.launcher3.util.rule.TestStabilityRule.Stability
import java.util.Random
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private data class Grid(val tableName: String, val size: Point, val items: List<WorkspaceItem>) {
    fun toGridState(): DeviceGridState =
        DeviceGridState(size.x, size.y, size.x, InvariantDeviceProfile.TYPE_PHONE, tableName)
}

@SmallTest
@RunWith(AndroidJUnit4::class)
class ValidGridMigrationUnitTest {

    companion object {
        const val SEED = 1044542
        val REPEAT_AFTER = Point(0, 10)
        val REPEAT_AFTER_DST = Point(6, 15)
        const val TAG = "ValidGridMigrationUnitTest"
        const val SMALL_TEST_SIZE = 60
        const val LARGE_TEST_SIZE = 1000
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    private fun validate(srcGrid: Grid, dstGrid: Grid, resultItems: List<WorkspaceItem>) {
        // This returns a map with the number of repeated elements
        // ex { calculatorIcon : 6, weatherWidget : 2 }
        val itemsToMap = { it: List<WorkspaceItem> ->
            it.filter { it.container != Favorites.CONTAINER_HOTSEAT }
                .groupingBy {
                    when (it.type) {
                        Favorites.ITEM_TYPE_FOLDER,
                        Favorites.ITEM_TYPE_APP_PAIR -> throw Exception("Not implemented")
                        Favorites.ITEM_TYPE_APPWIDGET -> it.appWidgetProvider
                        Favorites.ITEM_TYPE_APPLICATION -> it.intent
                        else -> it.title
                    }
                }
                .eachCount()
        }
        resultItems.forEach {
            assert((it.x in 0..dstGrid.size.x) && (it.y in 0..dstGrid.size.y)) {
                "Item outside of the board size. Size = ${dstGrid.size} Item = $it"
            }
            assert(
                (it.x + it.spanX in 0..dstGrid.size.x) && (it.y + it.spanY in 0..dstGrid.size.y)
            ) {
                "Item doesn't fit in the grid. Size = ${dstGrid.size} Item = $it"
            }
        }

        val srcCountMap = itemsToMap(srcGrid.items)
        val resultCountMap = itemsToMap(resultItems)
        val diff = resultCountMap - srcCountMap

        diff.forEach { (k, count) ->
            assert(count >= 0) { "Source item $k not present on the result" }
        }
    }

    private fun addItemsToDb(db: SQLiteDatabase, grid: Grid) {
        LauncherDbUtils.SQLiteTransaction(db).use { transaction ->
            grid.items.forEach { insertIntoDb(grid.tableName, it, transaction.db) }
            transaction.commit()
        }
    }

    private fun migrate(
        srcGrid: Grid,
        dstGrid: Grid,
    ): List<WorkspaceItem> {
        val userSerial = UserCache.INSTANCE[context].getSerialNumberForUser(Process.myUserHandle())
        val dbHelper =
            DatabaseHelper(
                context,
                null,
                { UserCache.INSTANCE.get(context).getSerialNumberForUser(it) },
                {}
            )

        Favorites.addTableToDb(dbHelper.writableDatabase, userSerial, false, srcGrid.tableName)

        addItemsToDb(dbHelper.writableDatabase, srcGrid)
        addItemsToDb(dbHelper.writableDatabase, dstGrid)

        LauncherDbUtils.SQLiteTransaction(dbHelper.writableDatabase).use {
            GridSizeMigrationUtil.migrate(
                dbHelper,
                GridSizeMigrationUtil.DbReader(it.db, srcGrid.tableName, context, MockSet(1)),
                GridSizeMigrationUtil.DbReader(it.db, dstGrid.tableName, context, MockSet(1)),
                dstGrid.size.x,
                dstGrid.size,
                srcGrid.toGridState(),
                dstGrid.toGridState()
            )
            it.commit()
        }
        return readDb(dstGrid.tableName, dbHelper.readableDatabase)
    }

    @Test
    fun runTestCase() {
        val caseGenerator = ValidGridMigrationTestCaseGenerator(Random(SEED.toLong()))
        for (i in 0..SMALL_TEST_SIZE) {
            val testCase = caseGenerator.generateTestCase(isDestEmpty = true)
            Log.d(TAG, "Test case = $testCase")
            val srcGrid =
                Grid(
                    tableName = Favorites.TMP_TABLE,
                    size = testCase.srcSize,
                    items = generateItemsForTest(testCase.boards, REPEAT_AFTER)
                )
            val dstGrid =
                Grid(tableName = Favorites.TABLE_NAME, size = testCase.targetSize, items = listOf())
            validate(srcGrid, dstGrid, migrate(srcGrid, dstGrid))
        }
    }

    @Test
    fun mergeBoards() {
        val caseGenerator = ValidGridMigrationTestCaseGenerator(Random(SEED.toLong()))
        for (i in 0..SMALL_TEST_SIZE) {
            val testCase = caseGenerator.generateTestCase(isDestEmpty = false)
            Log.d(TAG, "Test case = $testCase")
            val srcGrid =
                Grid(
                    tableName = Favorites.TMP_TABLE,
                    size = testCase.srcSize,
                    items = generateItemsForTest(testCase.boards, REPEAT_AFTER)
                )
            val dstGrid =
                Grid(
                    tableName = Favorites.TABLE_NAME,
                    size = testCase.targetSize,
                    items = generateItemsForTest(testCase.destBoards, REPEAT_AFTER_DST)
                )
            validate(srcGrid, dstGrid, migrate(srcGrid, dstGrid))
        }
    }

    // This test takes about 4 minutes, there is no need to run it in presubmit.
    @Stability(flavors = TestStabilityRule.LOCAL or TestStabilityRule.PLATFORM_POSTSUBMIT)
    @Test
    fun runExtensiveTestCases() {
        val caseGenerator = ValidGridMigrationTestCaseGenerator(Random(SEED.toLong()))
        for (i in 0..LARGE_TEST_SIZE) {
            val testCase = caseGenerator.generateTestCase(isDestEmpty = true)
            Log.d(TAG, "Test case = $testCase")
            val srcGrid =
                Grid(
                    tableName = Favorites.TMP_TABLE,
                    size = testCase.srcSize,
                    items = generateItemsForTest(testCase.boards, REPEAT_AFTER)
                )
            val dstGrid =
                Grid(tableName = Favorites.TABLE_NAME, size = testCase.targetSize, items = listOf())
            validate(srcGrid, dstGrid, migrate(srcGrid, dstGrid))
        }
    }
}
