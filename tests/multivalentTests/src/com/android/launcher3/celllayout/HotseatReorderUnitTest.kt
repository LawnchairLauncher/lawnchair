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

package com.android.launcher3.celllayout

import android.content.Context
import android.graphics.Point
import android.util.Log
import android.view.View
import androidx.core.view.get
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.CellLayout
import com.android.launcher3.celllayout.board.CellLayoutBoard
import com.android.launcher3.celllayout.board.IconPoint
import com.android.launcher3.celllayout.board.PermutedBoardComparator
import com.android.launcher3.celllayout.board.WidgetRect
import com.android.launcher3.celllayout.testgenerator.RandomBoardGenerator
import com.android.launcher3.util.ActivityContextWrapper
import com.android.launcher3.views.DoubleShadowBubbleTextView
import java.util.Random
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private class HotseatReorderTestCase(
    val startBoard: CellLayoutBoard,
    val endBoard: CellLayoutBoard
) {
    override fun toString(): String {
        return "$startBoard#endBoard:\n$endBoard"
    }
}

@SmallTest
@RunWith(AndroidJUnit4::class)
class HotseatReorderUnitTest {

    private val applicationContext: Context =
        ActivityContextWrapper(ApplicationProvider.getApplicationContext())

    @JvmField @Rule var cellLayoutBuilder = UnitTestCellLayoutBuilderRule()

    /**
     * This test generates random CellLayout configurations and then try to reorder it and makes
     * sure the result is a valid board meaning it didn't remove any widget or icon.
     */
    @Test
    fun generateValidTests() {
        val generator = Random(Companion.SEED.toLong())
        for (i in 0 until Companion.TOTAL_OF_CASES_GENERATED) {
            // Using a new seed so that we can replicate the same test cases.
            val seed = generator.nextInt()
            Log.d(Companion.TAG, "Seed = $seed")

            val testCase: HotseatReorderTestCase =
                generateRandomTestCase(RandomBoardGenerator(Random(seed.toLong())))
            Log.d(Companion.TAG, "testCase = $testCase")

            Assert.assertTrue(
                "invalid case $i",
                PermutedBoardComparator().compare(testCase.startBoard, testCase.endBoard) == 0
            )
        }
    }

    private fun addViewInCellLayout(
        cellLayout: CellLayout,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
        isWidget: Boolean
    ) {
        val cell =
            if (isWidget) View(applicationContext)
            else DoubleShadowBubbleTextView(applicationContext)
        cell.layoutParams = CellLayoutLayoutParams(cellX, cellY, spanX, spanY)
        cellLayout.addViewToCellLayout(
            cell,
            -1,
            cell.id,
            cell.layoutParams as CellLayoutLayoutParams,
            true
        )
    }

    private fun solve(board: CellLayoutBoard): CellLayout {
        val cl = cellLayoutBuilder.createCellLayout(board.width, board.height, false)
        // The views have to be sorted or the result can vary
        board.icons
            .map(IconPoint::getCoord)
            .sortedWith(
                Comparator.comparing { p: Any -> (p as Point).x }
                    .thenComparing { p: Any -> (p as Point).y }
            )
            .forEach { p ->
                addViewInCellLayout(
                    cellLayout = cl,
                    cellX = p.x,
                    cellY = p.y,
                    spanX = 1,
                    spanY = 1,
                    isWidget = false
                )
            }
        board.widgets
            .sortedWith(
                Comparator.comparing(WidgetRect::getCellX).thenComparing(WidgetRect::getCellY)
            )
            .forEach { widget ->
                addViewInCellLayout(
                    cl,
                    widget.cellX,
                    widget.cellY,
                    widget.spanX,
                    widget.spanY,
                    isWidget = true
                )
            }
        if (cl.makeSpaceForHotseatMigration(true)) {
            commitTempPosition(cl)
        }
        return cl
    }

    private fun commitTempPosition(cellLayout: CellLayout) {
        val count = cellLayout.shortcutsAndWidgets.childCount
        for (i in 0 until count) {
            val params = cellLayout.shortcutsAndWidgets[i].layoutParams as CellLayoutLayoutParams
            params.cellX = params.tmpCellX
            params.cellY = params.tmpCellY
        }
    }

    private fun boardFromCellLayout(cellLayout: CellLayout): CellLayoutBoard {
        val views = mutableListOf<View>()
        for (i in 0 until cellLayout.shortcutsAndWidgets.childCount) {
            views.add(cellLayout.shortcutsAndWidgets.getChildAt(i))
        }
        return CellLayoutTestUtils.viewsToBoard(views, cellLayout.countX, cellLayout.countY)
    }

    private fun generateRandomTestCase(
        boardGenerator: RandomBoardGenerator
    ): HotseatReorderTestCase {
        val width: Int = boardGenerator.getRandom(3, Companion.MAX_BOARD_SIZE)
        val height: Int = boardGenerator.getRandom(3, Companion.MAX_BOARD_SIZE)
        val targetWidth: Int = boardGenerator.getRandom(1, width - 2)
        val targetHeight: Int = boardGenerator.getRandom(1, height - 2)
        val board: CellLayoutBoard =
            boardGenerator.generateBoard(width, height, targetWidth * targetHeight)
        val finishBoard: CellLayoutBoard = boardFromCellLayout(solve(board))
        return HotseatReorderTestCase(board, finishBoard)
    }

    companion object {
        private const val MAX_BOARD_SIZE = 13

        /**
         * There is nothing special about this numbers, the random seed is just to be able to
         * reproduce the test cases and the height and width is a random number similar to what
         * users expect on their devices
         */
        private const val SEED = -194162315
        private const val TOTAL_OF_CASES_GENERATED = 300
        private const val TAG = "HotseatReorderUnitTest"
    }
}
