/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.integration.celllayout

import android.content.Context
import android.graphics.Point
import android.platform.uiautomatorhelpers.DeviceHelpers.context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.AppWidgetResizeFrame
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherState
import com.android.launcher3.celllayout.CellInfo
import com.android.launcher3.celllayout.CellLayoutTestCaseReader
import com.android.launcher3.celllayout.CellLayoutTestCaseReader.Board
import com.android.launcher3.celllayout.CellLayoutTestCaseReader.TestSection
import com.android.launcher3.celllayout.CellLayoutTestUtils
import com.android.launcher3.celllayout.ReorderTestCase
import com.android.launcher3.celllayout.board.CellLayoutBoard
import com.android.launcher3.celllayout.board.TestWorkspaceBuilder
import com.android.launcher3.celllayout.board.WidgetRect
import com.android.launcher3.debug.TestEventEmitter.TestEvent
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.integration.events.EventsRule
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.integration.util.TestUtils.getCellTopLeftRelativeToWorkspace
import com.android.launcher3.integration.util.TestUtils.getWidgetAtCell
import com.android.launcher3.integration.util.TestUtils.searchChildren
import com.android.launcher3.integration.util.events.ActivityTestEvents.createResizeFrameShownWaiter
import com.android.launcher3.integration.util.events.ActivityTestEvents.createStateWaiter
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.CellAndSpan
import com.android.launcher3.util.ModelTestExtensions.clearModelDb
import com.android.launcher3.util.rule.ScreenRecordRule
import com.android.launcher3.util.rule.ScreenRecordRule.ScreenRecord
import com.android.launcher3.util.rule.ShellCommandRule
import com.android.launcher3.util.workspace.FavoriteItemsTransaction
import com.android.launcher3.widget.LauncherAppWidgetHostView
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class IntegrationReorderWidgetsTest {

    var targetContext: Context = getInstrumentation().targetContext

    var eventsRule = EventsRule(targetContext)

    var launcherActivity = LauncherActivityScenarioRule<Launcher>(targetContext, false)

    @get:Rule var mScreenRecordRule: ScreenRecordRule = ScreenRecordRule()

    @get:Rule val chainRule = RuleChain.outerRule(eventsRule).around(launcherActivity)

    @get:Rule var grantWidgetRule = ShellCommandRule.grantWidgetBind()

    private var workspaceBuilder: TestWorkspaceBuilder? = null

    @Before
    fun setup() {
        workspaceBuilder = TestWorkspaceBuilder(targetContext)
    }

    @After
    fun tearDown() {
        LauncherAppState.getInstance(context).model.clearModelDb()
    }

    /** Validate if the given board represent the current CellLayout */
    private fun validateBoard(testBoards: List<CellLayoutBoard>): Boolean {
        val workspaceBoards = workspaceToBoards()
        if (workspaceBoards.size < testBoards.size) {
            return false
        }
        for (i in testBoards.indices) {
            if (testBoards[i].compareTo(workspaceBoards[i]) != 0) {
                return false
            }
        }
        return true
    }

    private fun buildWorkspaceFromBoards(
        boards: List<CellLayoutBoard>,
        transaction: FavoriteItemsTransaction,
    ): FavoriteItemsTransaction {
        for (i in boards.indices) {
            val board = boards[i]
            workspaceBuilder!!.buildFromBoard(board, transaction, i)
        }
        return transaction
    }

    private fun printCurrentWorkspace() {
        val idp = InvariantDeviceProfile.INSTANCE[targetContext]
        val boards = workspaceToBoards()
        for (i in boards.indices) {
            Log.d(TAG, "Screen number $i")
            Log.d(TAG, ".${boards[i].toString(idp.numColumns, idp.numRows)}".trimIndent())
        }
    }

    private fun workspaceToBoards(): ArrayList<CellLayoutBoard> =
        launcherActivity.getFromLauncher { launcher ->
            CellLayoutTestUtils.workspaceToBoards(launcher)
        }!!

    private fun getWidgetClosestTo(point: Point): WidgetRect? {
        val workspaceBoards = workspaceToBoards()
        var maxDistance = Integer.MAX_VALUE
        var bestRect: WidgetRect? = null
        for (i in workspaceBoards[0].widgets.indices) {
            val widget = workspaceBoards[0].widgets[i]
            if (widget.cellX == 0 && widget.cellY == 0) {
                continue
            }
            val distance =
                (abs((point.x - widget.cellX).toDouble()) +
                        abs((point.y - widget.cellY).toDouble()))
                    .toInt()
            if (distance == 0) {
                break
            }
            if (distance < maxDistance) {
                maxDistance = distance
                bestRect = widget
            }
        }
        return bestRect
    }

    private fun moveDragObjectToLocation(toCell: CellAndSpan) {
        launcherActivity.executeOnLauncher {
            // The coordinates need to be relative to the drop layer, in this case is the Workspace
            // not the Cell Layout
            val cellPixelCenter = getCellTopLeftRelativeToWorkspace(it.workspace, toCell)
            // We add 1 pixel to make sure we don't drop the widget on the corner outside of
            // the Workspace
            it.dragController.mDragObject.x = cellPixelCenter.x + 1
            it.dragController.mDragObject.y = cellPixelCenter.y + 1

            it.dragController.mDragObject.xOffset = 0
            it.dragController.mDragObject.yOffset = 0
        }
        getInstrumentation().waitForIdleSync()
    }

    private fun simulateDrag(fromCell: CellAndSpan, toCell: CellAndSpan) {
        val editModeWaiter = launcherActivity.createStateWaiter(LauncherState.SPRING_LOADED)
        launcherActivity.executeOnLauncher {
            val widget: LauncherAppWidgetHostView =
                getWidgetAtCell(it.workspace, fromCell.cellX, fromCell.cellY)
            val options = DragOptions()
            options.simulatedDndStartPoint = Point(widget.x.roundToInt(), widget.y.roundToInt())
            val info = widget.tag as ItemInfo
            it.workspace.startDrag(
                CellInfo(widget, info, it.cellPosMapper.mapModelToPresenter(info)),
                options,
            )
        }
        getInstrumentation().waitForIdleSync()
        launcherActivity.executeOnLauncher {
            it.workspace.onDragEnter(it.dragController.mDragObject)
        }
        getInstrumentation().waitForIdleSync()
        launcherActivity.executeOnLauncher {
            it.workspace.onDragOver(it.dragController.mDragObject)
        }
        getInstrumentation().waitForIdleSync()
        editModeWaiter.waitForSignal()
        moveDragObjectToLocation(toCell)
        launcherActivity.executeOnLauncher {
            it.workspace.onDragOver(it.dragController.mDragObject)
        }
        getInstrumentation().waitForIdleSync()
        launcherActivity.executeOnLauncher {
            it.workspace.onDragExit(it.dragController.mDragObject)
        }
        getInstrumentation().waitForIdleSync()
        launcherActivity.executeOnLauncher {
            it.workspace.onDrop(it.dragController.mDragObject, it.dragController.mOptions)
        }
        getInstrumentation().waitForIdleSync()
    }

    private fun dismissResizeFrame() {
        launcherActivity.executeOnLauncher {
            searchChildren(it.rootView, AppWidgetResizeFrame::class.java)!!.close(false)
        }
        getInstrumentation().waitForIdleSync()
    }

    /**
     * This function might be odd, its function is to select a widget and leave it in its place. The
     * idea is to make the test broader and also test after a widgets resized because the underlying
     * code does different things in that case
     */
    private fun triggerWidgetResize(testCase: ReorderTestCase) {
        val widgetRect =
            getWidgetClosestTo(testCase.moveMainTo)
                ?: // Some test doesn't have a widget in the final position, in those cases we will
                // ignore them
                return
        val widgetCell =
            CellAndSpan(widgetRect.cellX, widgetRect.cellY, widgetRect.spanX, widgetRect.spanY)

        val frameShowingEvent = launcherActivity.createResizeFrameShownWaiter()
        simulateDrag(widgetCell, widgetCell)
        frameShowingEvent.waitForSignal()
        dismissResizeFrame()
    }

    private fun runTestCase(testCase: ReorderTestCase) {
        val workspaceLoadedEvent = eventsRule.createEventWaiter(TestEvent.WORKSPACE_FINISH_LOADING)
        val mainWidgetCellPos = CellLayoutBoard.getMainFromList(testCase.mStart)
        var transaction = FavoriteItemsTransaction(targetContext)
        transaction = buildWorkspaceFromBoards(testCase.mStart, transaction)
        transaction.commit()
        // This makes sure the Workspace is fully loaded before continuing
        workspaceLoadedEvent.waitForSignal()
        triggerWidgetResize(testCase)
        val frameShowingEvent = launcherActivity.createResizeFrameShownWaiter()
        simulateDrag(
            fromCell =
                CellAndSpan(
                    mainWidgetCellPos.cellX,
                    mainWidgetCellPos.cellY,
                    mainWidgetCellPos.spanX,
                    mainWidgetCellPos.spanY,
                ),
            toCell =
                CellAndSpan(
                    testCase.moveMainTo.x,
                    testCase.moveMainTo.y,
                    mainWidgetCellPos.spanX,
                    mainWidgetCellPos.spanY,
                ),
        )
        frameShowingEvent.waitForSignal()
        dismissResizeFrame()
        var isValid = false
        for (boards in testCase.mEnd) {
            isValid = isValid or validateBoard(boards)
            if (isValid) break
        }
        printCurrentWorkspace()
        Assert.assertTrue("None of the valid boards match with the current state", isValid)
    }

    /**
     * Run only the test define for the current grid size if such test exist
     *
     * @param testCaseMap map containing all the tests per grid size (Point)
     */
    private fun runTestCaseMap(
        testCaseMap: Map<Point, ReorderTestCase>,
        testName: String,
    ): Boolean {
        launcherActivity.initializeActivity()
        val dp = launcherActivity.getFromLauncher { it.deviceProfile }!!

        val iconGridDimensions = Point(dp.inv.numColumns, dp.inv.numRows)
        Log.d(TAG, "Running test $testName for grid $iconGridDimensions")
        if (!testCaseMap.containsKey(iconGridDimensions)) {
            Log.d(TAG, "The test $testName doesn't support $iconGridDimensions grid layout")
            return false
        }
        testCaseMap[iconGridDimensions]?.let { runTestCase(it) }
        return true
    }

    @ScreenRecord
    @Test
    fun simpleReorder() =
        runTest(timeout = TIMEOUT) {
            runTestCaseMap(getTestMap("ReorderWidgets/simple_reorder_case"), "simple_reorder_case")
        }

    @ScreenRecord
    @Test
    fun pushTest() =
        runTest(timeout = TIMEOUT) {
            runTestCaseMap(getTestMap("ReorderWidgets/push_reorder_case"), "push_reorder_case")
        }

    @ScreenRecord
    @Test
    fun moveOutReorder() =
        runTest(timeout = TIMEOUT) {
            runTestCaseMap(
                getTestMap("ReorderWidgets/move_out_reorder_case"),
                "move_out_reorder_case",
            )
        }

    private fun addTestCase(
        sections: Iterator<TestSection>,
        testCaseMap: MutableMap<Point, ReorderTestCase>,
    ) {
        val startBoard = sections.next() as Board
        val point = sections.next() as CellLayoutTestCaseReader.Arguments
        val endBoard = sections.next() as Board
        val moveTo = Point(point.arguments[0].toInt(), point.arguments[1].toInt())
        testCaseMap[endBoard.gridSize] = ReorderTestCase(startBoard.board, moveTo, endBoard.board)
    }

    private fun getTestMap(testPath: String): Map<Point, ReorderTestCase> {
        val testCaseMap: MutableMap<Point, ReorderTestCase> = HashMap()
        val iterableSection: Iterator<TestSection> =
            CellLayoutTestCaseReader.readFromFile(testPath).parse().iterator()
        while (iterableSection.hasNext()) {
            addTestCase(iterableSection, testCaseMap)
        }
        return testCaseMap
    }

    companion object {
        private val TAG = IntegrationReorderWidgetsTest::class.java.simpleName
        private val TIMEOUT = 30.seconds
    }
}
