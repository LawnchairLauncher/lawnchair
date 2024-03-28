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

package com.android.launcher3.celllayout.testgenerator

import android.graphics.Point
import com.android.launcher3.LauncherSettings
import com.android.launcher3.celllayout.board.CellLayoutBoard
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.gridmigration.WorkspaceItem
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger

/**
 * Generate a list of WorkspaceItem's for the given test case.
 *
 * @param repeatAfter a number after which we would repeat the same number of icons and widgets to
 *   account for cases where the user have the same item multiple times.
 */
fun generateItemsForTest(
    testCase: GridMigrationUnitTestCase,
    repeatAfter: Int
): List<WorkspaceItem> {
    val id = AtomicInteger(0)
    val widgetId = AtomicInteger(LauncherAppWidgetInfo.CUSTOM_WIDGET_ID - 1)
    val boards = testCase.boards
    // Repeat the same appWidgetProvider and intent to have repeating widgets and icons and test
    // that case too
    val getIntent = { i: Int -> "Intent ${i % repeatAfter}" }
    val getProvider = { i: Int -> "com.test/test.Provider${i % repeatAfter}" }
    val hotseatEntries =
        (0 until boards[0].width).map {
            WorkspaceItem(
                x = it,
                y = 0,
                spanX = 1,
                spanY = 1,
                id = id.getAndAdd(1),
                screenId = it,
                title = "Hotseat ${id.get()}",
                appWidgetId = -1,
                appWidgetProvider = "Hotseat icons don't have a provider",
                intent = getIntent(id.get()),
                type = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                container = LauncherSettings.Favorites.CONTAINER_HOTSEAT
            )
        }
    var widgetEntries =
        boards
            .flatMapIndexed { i, board -> board.widgets.map { Pair(i, it) } }
            .map {
                WorkspaceItem(
                    x = it.second.cellX,
                    y = it.second.cellY,
                    spanX = it.second.spanX,
                    spanY = it.second.spanY,
                    id = id.getAndAdd(1),
                    screenId = it.first,
                    title = "Title Widget ${id.get()}",
                    appWidgetId = widgetId.getAndAdd(-1),
                    appWidgetProvider = getProvider(id.get()),
                    intent = "Widgets don't have intent",
                    type = LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET,
                    container = LauncherSettings.Favorites.CONTAINER_DESKTOP
                )
            }
    widgetEntries = widgetEntries.filter { it.appWidgetProvider.contains("Provider4") }
    val iconEntries =
        boards
            .flatMapIndexed { i, board -> board.icons.map { Pair(i, it) } }
            .map {
                WorkspaceItem(
                    x = it.second.coord.x,
                    y = it.second.coord.y,
                    spanX = 1,
                    spanY = 1,
                    id = id.getAndAdd(1),
                    screenId = it.first,
                    title = "Title Icon ${id.get()}",
                    appWidgetId = -1,
                    appWidgetProvider = "Icons don't have providers",
                    intent = getIntent(id.get()),
                    type = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION,
                    container = LauncherSettings.Favorites.CONTAINER_DESKTOP
                )
            }
    return widgetEntries + hotseatEntries // + iconEntries
}

data class GridMigrationUnitTestCase(
    val boards: List<CellLayoutBoard>,
    val srcSize: Point,
    val targetSize: Point,
    val seed: Long
)

class ValidGridMigrationTestCaseGenerator(private val generator: Random) :
    DeterministicRandomGenerator(generator) {

    companion object {
        const val MAX_BOARD_SIZE = 12
        const val MAX_BOARD_COUNT = 10
        const val SEED = 10342
    }

    private fun generateBoards(
        boardGenerator: RandomBoardGenerator,
        width: Int,
        height: Int,
        boardCount: Int
    ): List<CellLayoutBoard> {
        val boards = mutableListOf<CellLayoutBoard>()
        for (i in 0 until boardCount) {
            boards.add(
                boardGenerator.generateBoard(
                    width,
                    height,
                    boardGenerator.getRandom(0, width * height)
                )
            )
        }
        return boards
    }

    fun generateTestCase(): GridMigrationUnitTestCase {
        var seed = generator.nextLong()
        val randomBoardGenerator = RandomBoardGenerator(Random(seed))
        val width = randomBoardGenerator.getRandom(3, MAX_BOARD_SIZE)
        val height = randomBoardGenerator.getRandom(3, MAX_BOARD_SIZE)
        return GridMigrationUnitTestCase(
            boards =
                generateBoards(
                    boardGenerator = randomBoardGenerator,
                    width = width,
                    height = height,
                    boardCount = randomBoardGenerator.getRandom(3, MAX_BOARD_COUNT)
                ),
            srcSize = Point(width, height),
            targetSize =
                Point(
                    randomBoardGenerator.getRandom(3, MAX_BOARD_SIZE),
                    randomBoardGenerator.getRandom(3, MAX_BOARD_SIZE)
                ),
            seed = seed
        )
    }
}
