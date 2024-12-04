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
package com.android.launcher3.celllayout;

import static android.platform.uiautomator_helpers.DeviceHelpers.getContext;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Point;
import android.net.Uri;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.MultipageCellLayout;
import com.android.launcher3.celllayout.board.CellLayoutBoard;
import com.android.launcher3.celllayout.board.TestWorkspaceBuilder;
import com.android.launcher3.celllayout.board.WidgetRect;
import com.android.launcher3.tapl.Widget;
import com.android.launcher3.tapl.WidgetResizeFrame;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.util.ModelTestExtensions;
import com.android.launcher3.util.rule.ShellCommandRule;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TaplReorderWidgetsTest extends AbstractLauncherUiTest<Launcher> {

    @Rule
    public ShellCommandRule mGrantWidgetRule = ShellCommandRule.grantWidgetBind();

    private static final String TAG = TaplReorderWidgetsTest.class.getSimpleName();

    private static final List<String> FOLDABLE_GRIDS = List.of("normal", "practical", "reasonable");

    TestWorkspaceBuilder mWorkspaceBuilder;

    @Before
    public void setup() throws Throwable {
        mWorkspaceBuilder = new TestWorkspaceBuilder(mTargetContext);
        super.setUp();
    }

    @After
    public void tearDown() {
        ModelTestExtensions.INSTANCE.clearModelDb(
                LauncherAppState.getInstance(getContext()).getModel()
        );
    }

    /**
     * Validate if the given board represent the current CellLayout
     **/
    private boolean validateBoard(List<CellLayoutBoard> testBoards) {
        ArrayList<CellLayoutBoard> workspaceBoards = workspaceToBoards();
        if (workspaceBoards.size() < testBoards.size()) {
            return false;
        }
        for (int i = 0; i < testBoards.size(); i++) {
            if (testBoards.get(i).compareTo(workspaceBoards.get(i)) != 0) {
                return false;
            }
        }
        return true;
    }

    private FavoriteItemsTransaction buildWorkspaceFromBoards(List<CellLayoutBoard> boards,
            FavoriteItemsTransaction transaction) {
        for (int i = 0; i < boards.size(); i++) {
            CellLayoutBoard board = boards.get(i);
            mWorkspaceBuilder.buildFromBoard(board, transaction, i);
        }
        return transaction;
    }

    private void printCurrentWorkspace() {
        InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(mTargetContext);
        ArrayList<CellLayoutBoard> boards = workspaceToBoards();
        for (int i = 0; i < boards.size(); i++) {
            Log.d(TAG, "Screen number " + i);
            Log.d(TAG, ".\n" + boards.get(i).toString(idp.numColumns, idp.numRows));
        }
    }

    private ArrayList<CellLayoutBoard> workspaceToBoards() {
        return getFromLauncher(CellLayoutTestUtils::workspaceToBoards);
    }

    private WidgetRect getWidgetClosestTo(Point point) {
        ArrayList<CellLayoutBoard> workspaceBoards = workspaceToBoards();
        int maxDistance = 9999;
        WidgetRect bestRect = null;
        for (int i = 0; i < workspaceBoards.get(0).getWidgets().size(); i++) {
            WidgetRect widget = workspaceBoards.get(0).getWidgets().get(i);
            if (widget.getCellX() == 0 && widget.getCellY() == 0) {
                continue;
            }
            int distance = Math.abs(point.x - widget.getCellX())
                    + Math.abs(point.y - widget.getCellY());
            if (distance == 0) {
                break;
            }
            if (distance < maxDistance) {
                maxDistance = distance;
                bestRect = widget;
            }
        }
        return bestRect;
    }

    /**
     * This function might be odd, its function is to select a widget and leave it in its place.
     * The idea is to make the test broader and also test after a widgets resized because the
     * underlying code does different things in that case
     */
    private void triggerWidgetResize(ReorderTestCase testCase) {
        WidgetRect widgetRect = getWidgetClosestTo(testCase.moveMainTo);
        if (widgetRect == null) {
            // Some test doesn't have a widget in the final position, in those cases we will ignore
            // them
            return;
        }
        Widget widget = mLauncher.getWorkspace().getWidgetAtCell(widgetRect.getCellX(),
                widgetRect.getCellY());
        WidgetResizeFrame resizeFrame = widget.dragWidgetToWorkspace(widgetRect.getCellX(),
                widgetRect.getCellY(), widgetRect.getSpanX(), widgetRect.getSpanY());
        resizeFrame.dismiss();
    }

    private void runTestCase(ReorderTestCase testCase) {
        WidgetRect mainWidgetCellPos = CellLayoutBoard.getMainFromList(
                testCase.mStart);

        FavoriteItemsTransaction transaction =
                new FavoriteItemsTransaction(mTargetContext);
        transaction = buildWorkspaceFromBoards(testCase.mStart, transaction);
        transaction.commit();
        mLauncher.waitForLauncherInitialized();
        // resetLoaderState triggers the launcher to start loading the workspace which allows
        // waitForLauncherCondition to wait for that condition, otherwise the condition would
        // always be true and it wouldn't wait for the changes to be applied.
        waitForLauncherCondition("Workspace didn't finish loading", l -> !l.isWorkspaceLoading());

        triggerWidgetResize(testCase);

        Widget widget = mLauncher.getWorkspace().getWidgetAtCell(mainWidgetCellPos.getCellX(),
                mainWidgetCellPos.getCellY());
        assertNotNull(widget);
        WidgetResizeFrame resizeFrame = widget.dragWidgetToWorkspace(testCase.moveMainTo.x,
                testCase.moveMainTo.y, mainWidgetCellPos.getSpanX(), mainWidgetCellPos.getSpanY());
        resizeFrame.dismiss();

        boolean isValid = false;
        for (List<CellLayoutBoard> boards : testCase.mEnd) {
            isValid |= validateBoard(boards);
            if (isValid) break;
        }
        printCurrentWorkspace();
        assertTrue("Non of the valid boards match with the current state", isValid);
    }

    /**
     * Run only the test define for the current grid size if such test exist
     *
     * @param testCaseMap map containing all the tests per grid size (Point)
     */
    private boolean runTestCaseMap(Map<Point, ReorderTestCase> testCaseMap, String testName) {
        Point iconGridDimensions = mLauncher.getWorkspace().getIconGridDimensions();
        Log.d(TAG, "Running test " + testName + " for grid " + iconGridDimensions);
        if (!testCaseMap.containsKey(iconGridDimensions)) {
            Log.d(TAG, "The test " + testName + " doesn't support " + iconGridDimensions
                    + " grid layout");
            return false;
        }
        runTestCase(testCaseMap.get(iconGridDimensions));

        return true;
    }

    private void runTestCaseMapForAllGrids(Map<Point, ReorderTestCase> testCaseMap,
            String testName) {
        boolean runAtLeastOnce = false;
        for (String grid : FOLDABLE_GRIDS) {
            applyGridOption(grid);
            mLauncher.waitForLauncherInitialized();
            runAtLeastOnce |= runTestCaseMap(testCaseMap, testName);
        }
        Assume.assumeTrue("None of the grids are supported", runAtLeastOnce);
    }

    private void applyGridOption(Object argValue) {
        String testProviderAuthority = mTargetContext.getPackageName() + ".grid_control";
        Uri gridUri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(testProviderAuthority)
                .appendPath("default_grid")
                .build();
        ContentValues values = new ContentValues();
        values.putObject("name", argValue);
        Assert.assertEquals(1,
                mTargetContext.getContentResolver().update(gridUri, values, null, null));
    }

    @Test
    public void simpleReorder() throws Exception {
        runTestCaseMap(getTestMap("ReorderWidgets/simple_reorder_case"),
                "push_reorder_case");
    }

    @Test
    public void pushTest() throws Exception {
        runTestCaseMap(getTestMap("ReorderWidgets/push_reorder_case"),
                "push_reorder_case");
    }

    @Test
    public void fullReorder() throws Exception {
        runTestCaseMap(getTestMap("ReorderWidgets/full_reorder_case"),
                "full_reorder_case");
    }

    @Test
    public void moveOutReorder() throws Exception {
        runTestCaseMap(getTestMap("ReorderWidgets/move_out_reorder_case"),
                "move_out_reorder_case");
    }

    @Test
    public void multipleCellLayoutsSimpleReorder() throws Exception {
        Assume.assumeTrue("Test doesn't support foldables", getFromLauncher(
                l -> l.getWorkspace().getScreenWithId(0) instanceof MultipageCellLayout));
        runTestCaseMapForAllGrids(getTestMap("ReorderWidgets/multiple_cell_layouts_simple_reorder"),
                "multiple_cell_layouts_simple_reorder");
    }

    @Test
    public void multipleCellLayoutsNoSpaceReorder() throws Exception {
        Assume.assumeTrue("Test doesn't support foldables", getFromLauncher(
                l -> l.getWorkspace().getScreenWithId(0) instanceof MultipageCellLayout));
        runTestCaseMapForAllGrids(
                getTestMap("ReorderWidgets/multiple_cell_layouts_no_space_reorder"),
                "multiple_cell_layouts_no_space_reorder");
    }

    @Test
    public void multipleCellLayoutsReorderToOtherSide() throws Exception {
        Assume.assumeTrue("Test doesn't support foldables", getFromLauncher(
                l -> l.getWorkspace().getScreenWithId(0) instanceof MultipageCellLayout));
        runTestCaseMapForAllGrids(
                getTestMap("ReorderWidgets/multiple_cell_layouts_reorder_other_side"),
                "multiple_cell_layouts_reorder_other_side");
    }

    private void addTestCase(Iterator<CellLayoutTestCaseReader.TestSection> sections,
            Map<Point, ReorderTestCase> testCaseMap) {
        CellLayoutTestCaseReader.Board startBoard =
                ((CellLayoutTestCaseReader.Board) sections.next());
        CellLayoutTestCaseReader.Arguments point =
                ((CellLayoutTestCaseReader.Arguments) sections.next());
        CellLayoutTestCaseReader.Board endBoard =
                ((CellLayoutTestCaseReader.Board) sections.next());
        Point moveTo = new Point(Integer.parseInt(point.arguments[0]),
                Integer.parseInt(point.arguments[1]));
        testCaseMap.put(endBoard.gridSize,
                new ReorderTestCase(startBoard.board, moveTo, endBoard.board));
    }

    private Map<Point, ReorderTestCase> getTestMap(String testPath) throws IOException {
        Map<Point, ReorderTestCase> testCaseMap = new HashMap<>();
        Iterator<CellLayoutTestCaseReader.TestSection> iterableSection =
                CellLayoutTestCaseReader.readFromFile(testPath).parse().iterator();
        while (iterableSection.hasNext()) {
            addTestCase(iterableSection, testCaseMap);
        }
        return testCaseMap;
    }
}
