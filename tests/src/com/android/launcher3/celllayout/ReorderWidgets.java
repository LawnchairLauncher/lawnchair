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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Point;
import android.util.Log;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.CellLayout;
import com.android.launcher3.celllayout.testcases.FullReorderCase;
import com.android.launcher3.celllayout.testcases.MoveOutReorderCase;
import com.android.launcher3.celllayout.testcases.PushReorderCase;
import com.android.launcher3.celllayout.testcases.ReorderTestCase;
import com.android.launcher3.celllayout.testcases.SimpleReorderCase;
import com.android.launcher3.tapl.Widget;
import com.android.launcher3.tapl.WidgetResizeFrame;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.ui.TaplTestsLauncher3;
import com.android.launcher3.util.rule.ShellCommandRule;
import com.android.launcher3.views.DoubleShadowBubbleTextView;
import com.android.launcher3.widget.LauncherAppWidgetHostView;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.concurrent.ExecutionException;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ReorderWidgets extends AbstractLauncherUiTest {

    @Rule
    public ShellCommandRule mGrantWidgetRule = ShellCommandRule.grantWidgetBind();

    private static final String TAG = ReorderWidgets.class.getSimpleName();

    TestWorkspaceBuilder mWorkspaceBuilder;

    private View getViewAt(int cellX, int cellY) {
        return getFromLauncher(l -> l.getWorkspace().getScreenWithId(
                l.getWorkspace().getScreenIdForPageIndex(0)).getChildAt(cellX, cellY));
    }

    private Point getCellDimensions() {
        return getFromLauncher(l -> {
            CellLayout cellLayout = l.getWorkspace().getScreenWithId(
                    l.getWorkspace().getScreenIdForPageIndex(0));
            return new Point(cellLayout.getWidth() / cellLayout.getCountX(),
                    cellLayout.getHeight() / cellLayout.getCountY());
        });
    }

    @Before
    public void setup() throws Throwable {
        mWorkspaceBuilder = new TestWorkspaceBuilder(this, mTargetContext);
        TaplTestsLauncher3.initialize(this);
        clearHomescreen();
    }

    /**
     * Validate if the given board represent the current CellLayout
     **/
    private boolean validateBoard(CellLayoutBoard board) {
        boolean match = true;
        Point cellDimensions = getCellDimensions();
        for (CellLayoutBoard.WidgetRect widgetRect : board.getWidgets()) {
            if (widgetRect.shouldIgnore()) {
                continue;
            }
            View widget = getViewAt(widgetRect.getCellX(), widgetRect.getCellY());
            assertTrue("The view selected at " + board + " is not a widget",
                    widget instanceof LauncherAppWidgetHostView);
            match &= widgetRect.getSpanX()
                    == Math.round(widget.getWidth() / (float) cellDimensions.x);
            match &= widgetRect.getSpanY()
                    == Math.round(widget.getHeight() / (float) cellDimensions.y);
            if (!match) return match;
        }
        for (CellLayoutBoard.IconPoint iconPoint : board.getIcons()) {
            View icon = getViewAt(iconPoint.getCoord().x, iconPoint.getCoord().y);
            assertTrue("The view selected at " + iconPoint.coord + " is not an Icon",
                    icon instanceof DoubleShadowBubbleTextView);
        }
        return match;
    }

    private void runTestCase(ReorderTestCase testCase)
            throws ExecutionException, InterruptedException {
        Point mainWidgetCellPos = testCase.mStart.getMain();

        FavoriteItemsTransaction transaction =
                new FavoriteItemsTransaction(mTargetContext, this);
        mWorkspaceBuilder.buildFromBoard(testCase.mStart, transaction).commit();
        waitForLauncherCondition("Workspace didn't finish loading", l -> !l.isWorkspaceLoading());
        Widget widget = mLauncher.getWorkspace().getWidgetAtCell(mainWidgetCellPos.x,
                mainWidgetCellPos.y);
        assertNotNull(widget);
        WidgetResizeFrame resizeFrame = widget.dragWidgetToWorkspace(testCase.moveMainTo.x,
                testCase.moveMainTo.y);
        resizeFrame.dismiss();

        boolean isValid = false;
        for (CellLayoutBoard board : testCase.mEnd) {
            isValid |= validateBoard(board);
        }
        assertTrue("Non of the valid boards match with the current state", isValid);
    }

    /**
     * Run only the test define for the current grid size if such test exist
     *
     * @param testCaseMap map containing all the tests per grid size (Point)
     */
    private void runTestCaseMap(Map<Point, ReorderTestCase> testCaseMap, String testName)
            throws ExecutionException, InterruptedException {
        Point iconGridDimensions = mLauncher.getWorkspace().getIconGridDimensions();
        Log.d(TAG, "Running test " + testName + " for grid " + iconGridDimensions);
        Assume.assumeTrue(
                "The test " + testName + " doesn't support " + iconGridDimensions + " grid layout",
                testCaseMap.containsKey(iconGridDimensions));
        runTestCase(testCaseMap.get(iconGridDimensions));
    }

    @Test
    public void simpleReorder() throws ExecutionException, InterruptedException {
        runTestCaseMap(SimpleReorderCase.TEST_BY_GRID_SIZE,
                SimpleReorderCase.class.getSimpleName());
    }

    @Ignore
    @Test
    public void pushTest() throws ExecutionException, InterruptedException {
        runTestCaseMap(PushReorderCase.TEST_BY_GRID_SIZE, PushReorderCase.class.getSimpleName());
    }

    @Ignore
    @Test
    public void fullReorder() throws ExecutionException, InterruptedException {
        runTestCaseMap(FullReorderCase.TEST_BY_GRID_SIZE, FullReorderCase.class.getSimpleName());
    }

    @Test
    public void moveOutReorder() throws ExecutionException, InterruptedException {
        runTestCaseMap(MoveOutReorderCase.TEST_BY_GRID_SIZE,
                MoveOutReorderCase.class.getSimpleName());
    }
}
