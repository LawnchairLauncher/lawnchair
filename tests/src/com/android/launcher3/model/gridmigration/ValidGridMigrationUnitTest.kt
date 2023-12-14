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

@SmallTest
@RunWith(AndroidJUnit4::class)
class ValidGridMigrationUnitTest {

    companion object {
        const val SEED = 1044542
        const val REPEAT_AFTER = 10
        const val TAG = "ValidGridMigrationUnitTest"
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    private fun validate(
        srcItems: List<WorkspaceItem>,
        dstItems: List<WorkspaceItem>,
        destinationSize: Point
    ) {
        // This returns a map with the number of repeated elements
        // ex { calculatorIcon : 6, weatherWidget : 2 }
        val itemsToSet = { it: List<WorkspaceItem> ->
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
        for (it in dstItems) {
            assert((it.x in 0..destinationSize.x) && (it.y in 0..destinationSize.y)) {
                "Item outside of the board size. Size = $destinationSize Item = $it"
            }
            assert(
                (it.x + it.spanX in 0..destinationSize.x) &&
                    (it.y + it.spanY in 0..destinationSize.y)
            ) {
                "Item doesn't fit in the grid. Size = $destinationSize Item = $it"
            }
        }

        assert(itemsToSet(srcItems) == itemsToSet(dstItems)) {
            "The srcItems do not match the dstItems src = $srcItems  dst = $dstItems"
        }
    }

    private fun addItemsToDb(db: SQLiteDatabase, tableName: String, items: List<WorkspaceItem>) {
        LauncherDbUtils.SQLiteTransaction(db).use { transaction ->
            items.forEach { insertIntoDb(tableName, it, transaction.db) }
            transaction.commit()
        }
    }

    private fun migrate(
        srcItems: List<WorkspaceItem>,
        srcSize: Point,
        targetSize: Point
    ): List<WorkspaceItem> {
        val userSerial = UserCache.INSTANCE[context].getSerialNumberForUser(Process.myUserHandle())
        val dbHelper =
            DatabaseHelper(
                context,
                null,
                { UserCache.INSTANCE.get(context).getSerialNumberForUser(it) },
                {}
            )
        val srcTableName = Favorites.TMP_TABLE
        val dstTableName = Favorites.TABLE_NAME
        Favorites.addTableToDb(dbHelper.writableDatabase, userSerial, false, srcTableName)
        addItemsToDb(dbHelper.writableDatabase, srcTableName, srcItems)
        LauncherDbUtils.SQLiteTransaction(dbHelper.writableDatabase).use {
            GridSizeMigrationUtil.migrate(
                dbHelper,
                GridSizeMigrationUtil.DbReader(it.db, srcTableName, context, MockSet(1)),
                GridSizeMigrationUtil.DbReader(it.db, dstTableName, context, MockSet(1)),
                targetSize.x,
                targetSize,
                DeviceGridState(
                    srcSize.x,
                    srcSize.y,
                    srcSize.x,
                    InvariantDeviceProfile.TYPE_PHONE,
                    srcTableName
                ),
                DeviceGridState(
                    targetSize.x,
                    targetSize.y,
                    targetSize.x,
                    InvariantDeviceProfile.TYPE_PHONE,
                    dstTableName
                )
            )
            it.commit()
        }
        return readDb(dstTableName, dbHelper.readableDatabase)
    }

    @Test
    fun runTestCase() {
        val caseGenerator = ValidGridMigrationTestCaseGenerator(Random(SEED.toLong()))
        for (i in 0..50) {
            val testCase = caseGenerator.generateTestCase()
            Log.d(TAG, "Test case = $testCase")
            val srcItemList = generateItemsForTest(testCase, REPEAT_AFTER)
            val dstItemList = migrate(srcItemList, testCase.srcSize, testCase.targetSize)
            validate(srcItemList, dstItemList, testCase.targetSize)
        }
    }

    // This test takes about 4 minutes, there is no need to run it in presubmit.
    @Stability(flavors = TestStabilityRule.LOCAL or TestStabilityRule.PLATFORM_POSTSUBMIT)
    @Test
    fun runExtensiveTestCases() {
        val caseGenerator = ValidGridMigrationTestCaseGenerator(Random(SEED.toLong()))
        for (i in 0..1000) {
            val testCase = caseGenerator.generateTestCase()
            Log.d(TAG, "Test case = $testCase")
            val srcItemList = generateItemsForTest(testCase, REPEAT_AFTER)
            val dstItemList = migrate(srcItemList, testCase.srcSize, testCase.targetSize)
            validate(srcItemList, dstItemList, testCase.targetSize)
        }
    }
}
