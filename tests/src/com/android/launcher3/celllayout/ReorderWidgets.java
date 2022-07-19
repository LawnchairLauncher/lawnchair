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

import static com.android.launcher3.util.WidgetUtils.createWidgetInfo;

import static org.junit.Assert.assertTrue;

import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.CellLayout;
import com.android.launcher3.celllayout.testcases.FullReorderCase;
import com.android.launcher3.celllayout.testcases.MoveOutReorderCase;
import com.android.launcher3.celllayout.testcases.PushReorderCase;
import com.android.launcher3.celllayout.testcases.ReorderTestCase;
import com.android.launcher3.celllayout.testcases.SimpleReorderCase;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.ui.TaplTestsLauncher3;
import com.android.launcher3.ui.TestViewHelpers;
import com.android.launcher3.util.rule.ShellCommandRule;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;


@SmallTest
@RunWith(AndroidJUnit4.class)
public class ReorderWidgets extends AbstractLauncherUiTest {

    @Rule
    public ShellCommandRule mGrantWidgetRule = ShellCommandRule.grantWidgetBind();

    private static final String TAG = ReorderWidgets.class.getSimpleName();

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
        TaplTestsLauncher3.initialize(this);
        clearHomescreen();
    }

    /**
     * Validate if the given board represent the current CellLayout
     **/
    private boolean validateBoard(CellLayoutBoard board) {
        boolean match = true;
        Point cellDimensions = getCellDimensions();
        for (TestBoardWidget widgetRect: board.getWidgets()) {
            if (widgetRect.shouldIgnore()) {
                continue;
            }
            View widget = getViewAt(widgetRect.getCellX(), widgetRect.getCellY());
            match &= widgetRect.getSpanX()
                    == Math.round(widget.getWidth() / (float) cellDimensions.x);
            match &= widgetRect.getSpanY()
                    == Math.round(widget.getHeight() / (float) cellDimensions.y);
            if (!match) return match;
        }
        return match;
    }

    /**
     * Fills the given rect in WidgetRect with 1x1 widgets. This is useful to equalize cases.
     */
    private void fillWithWidgets(TestBoardWidget widgetRect) {
        int initX = widgetRect.getCellX();
        int initY = widgetRect.getCellY();
        for (int x = 0; x < widgetRect.getSpanX(); x++) {
            for (int y = 0; y < widgetRect.getSpanY(); y++) {
                int auxX = initX + x;
                int auxY = initY + y;
                try {
                    // this widgets are filling, we don't care if we can't place them
                    addWidgetInCell(
                            new TestBoardWidget('x',
                                    new Rect(auxX, auxY, auxX, auxY))
                    );
                } catch (Exception e) {
                    Log.d(TAG, "Unable to place filling widget at " + auxX + "," + auxY);
                }
            }
        }
    }

    private void addWidgetInCell(TestBoardWidget widgetRect) {
        LauncherAppWidgetProviderInfo info = TestViewHelpers.findWidgetProvider(this, false);
        LauncherAppWidgetInfo item = createWidgetInfo(info,
                ApplicationProvider.getApplicationContext(), true);
        item.cellX = widgetRect.getCellX();
        item.cellY = widgetRect.getCellY();

        item.spanX = widgetRect.getSpanX();
        item.spanY = widgetRect.getSpanY();
        addItemToScreen(item);
    }

    private void addCorrespondingWidgetRect(TestBoardWidget widgetRect) {
        if (widgetRect.mType == 'x') {
            fillWithWidgets(widgetRect);
        } else {
            addWidgetInCell(widgetRect);
        }
    }

    private void runTestCase(ReorderTestCase testCase) {
        Point mainWidgetCellPos = testCase.mStart.getMain();

        testCase.mStart.getWidgets().forEach(this::addCorrespondingWidgetRect);

        mLauncher.getWorkspace()
                .getWidgetAtCell(mainWidgetCellPos.x, mainWidgetCellPos.y)
                .dragWidgetToWorkspace(testCase.moveMainTo.x, testCase.moveMainTo.y)
                .dismiss(); // dismiss resize frame

        boolean isValid = false;
        for (CellLayoutBoard board : testCase.mEnd) {
            isValid |= validateBoard(board);
        }
        assertTrue("None of the valid boards match with the current state", isValid);
    }

    /**
     * Run only the test define for the current grid size if such test exist
     *
     * @param testCaseMap map containing all the tests per grid size (Point)
     */
    private void runTestCaseMap(Map<Point, ReorderTestCase> testCaseMap, String testName) {
        Point iconGridDimensions = mLauncher.getWorkspace().getIconGridDimensions();
        Log.d(TAG, "Running test " + testName + " for grid " + iconGridDimensions);
        Assume.assumeTrue(
                "The test " + testName + " doesn't support " + iconGridDimensions + " grid layout",
                testCaseMap.containsKey(iconGridDimensions));
        runTestCase(testCaseMap.get(iconGridDimensions));
    }

    @Test
    public void simpleReorder() {
        runTestCaseMap(SimpleReorderCase.TEST_BY_GRID_SIZE,
                SimpleReorderCase.class.getSimpleName());
    }

    @Test
    public void pushTest() {
        runTestCaseMap(PushReorderCase.TEST_BY_GRID_SIZE, PushReorderCase.class.getSimpleName());
    }

    @Test
    public void fullReorder() {
        runTestCaseMap(FullReorderCase.TEST_BY_GRID_SIZE, FullReorderCase.class.getSimpleName());
    }

    @Test
    public void moveOutReorder() {
        runTestCaseMap(MoveOutReorderCase.TEST_BY_GRID_SIZE,
                MoveOutReorderCase.class.getSimpleName());
    }
}
