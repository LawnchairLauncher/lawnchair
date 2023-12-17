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
package com.android.launcher3.celllayout;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.util.ActivityContextWrapper;
import com.android.launcher3.views.DoubleShadowBubbleTextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ReorderAlgorithmUnitTest {
    private Context mApplicationContext;

    private int mPrevNumColumns, mPrevNumRows;

    @Test
    public void testAllCases() throws IOException {
        List<ReorderAlgorithmUnitTestCase> testCases = getTestCases(
                "ReorderAlgorithmUnitTest/reorder_algorithm_test_cases");
        mApplicationContext = new ActivityContextWrapper(getApplicationContext());
        List<Integer> failingCases = new ArrayList<>();
        for (int i = 0; i < testCases.size(); i++) {
            try {
                evaluateTestCase(testCases.get(i));
            } catch (AssertionError e) {
                e.printStackTrace();
                failingCases.add(i);
            }
        }
        assertEquals("Some test cases failed " + Arrays.toString(failingCases.toArray()), 0,
                failingCases.size());
    }

    private void addViewInCellLayout(CellLayout cellLayout, int cellX, int cellY, int spanX,
            int spanY, boolean isWidget) {
        View cell = isWidget ? new View(mApplicationContext) : new DoubleShadowBubbleTextView(
                mApplicationContext);
        cell.setLayoutParams(new CellLayoutLayoutParams(cellX, cellY, spanX, spanY));
        cellLayout.addViewToCellLayout(cell, -1, cell.getId(),
                (CellLayoutLayoutParams) cell.getLayoutParams(), true);
    }

    public CellLayout createCellLayout(int width, int height) {
        Context c = mApplicationContext;
        DeviceProfile dp = InvariantDeviceProfile.INSTANCE.get(c).getDeviceProfile(c).copy(c);
        // modify the device profile.
        dp.inv.numColumns = width;
        dp.inv.numRows = height;
        dp.cellLayoutBorderSpacePx = new Point(0, 0);

        CellLayout cl = new CellLayout(getWrappedContext(c, dp));
        // I put a very large number for width and height so that all the items can fit, it doesn't
        // need to be exact, just bigger than the sum of cell border
        cl.measure(View.MeasureSpec.makeMeasureSpec(10000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(10000, View.MeasureSpec.EXACTLY));
        return cl;
    }

    private Context getWrappedContext(Context context, DeviceProfile dp) {
        return new ActivityContextWrapper(context) {
            public DeviceProfile getDeviceProfile() {
                return dp;
            }
        };
    }

    public CellLayout.ItemConfiguration solve(CellLayoutBoard board, int x, int y, int spanX,
            int spanY, int minSpanX, int minSpanY) {
        CellLayout cl = createCellLayout(board.getWidth(), board.getHeight());

        // The views have to be sorted or the result can vary
        board.getIcons()
                .stream()
                .map(CellLayoutBoard.IconPoint::getCoord)
                .sorted(Comparator.comparing(p -> ((Point) p).x).thenComparing(p -> ((Point) p).y))
                .forEach(p -> addViewInCellLayout(cl, p.x, p.y, 1, 1, false));
        board.getWidgets().stream()
                .sorted(Comparator.comparing(CellLayoutBoard.WidgetRect::getCellX)
                        .thenComparing(CellLayoutBoard.WidgetRect::getCellY))
                .forEach(widget -> addViewInCellLayout(cl, widget.getCellX(), widget.getCellY(),
                        widget.getSpanX(), widget.getSpanY(), true));

        int[] testCaseXYinPixels = new int[2];
        cl.regionToCenterPoint(x, y, spanX, spanY, testCaseXYinPixels);
        CellLayout.ItemConfiguration solution = cl.createReorderAlgorithm().calculateReorder(
                testCaseXYinPixels[0], testCaseXYinPixels[1], minSpanX, minSpanY, spanX, spanY,
                null);
        if (solution == null) {
            solution = new CellLayout.ItemConfiguration();
            solution.isSolution = false;
        }
        return solution;
    }

    public CellLayoutBoard boardFromSolution(CellLayout.ItemConfiguration solution, int width,
            int height) {
        // Update the views with solution value
        solution.map.forEach((key, val) -> key.setLayoutParams(
                new CellLayoutLayoutParams(val.cellX, val.cellY, val.spanX, val.spanY)));
        CellLayoutBoard board = CellLayoutTestUtils.viewsToBoard(
                new ArrayList<>(solution.map.keySet()), width, height);
        board.addWidget(solution.cellX, solution.cellY, solution.spanX, solution.spanY,
                'z');
        return board;
    }

    public void evaluateTestCase(ReorderAlgorithmUnitTestCase testCase) {
        CellLayout.ItemConfiguration solution = solve(testCase.startBoard, testCase.x,
                testCase.y, testCase.spanX, testCase.spanY, testCase.minSpanX,
                testCase.minSpanY);
        assertEquals("should be a valid solution", solution.isSolution,
                testCase.isValidSolution);
        if (testCase.isValidSolution) {
            CellLayoutBoard finishBoard = boardFromSolution(solution,
                    testCase.startBoard.getWidth(), testCase.startBoard.getHeight());
            assertTrue("End result and test case result board doesn't match ",
                    finishBoard.compareTo(testCase.endBoard) == 0);
        }
    }

    @Before
    public void storePreviousValues() {
        Context c = new ActivityContextWrapper(getApplicationContext());
        DeviceProfile dp = InvariantDeviceProfile.INSTANCE.get(c).getDeviceProfile(c).copy(c);
        mPrevNumColumns = dp.inv.numColumns;
        mPrevNumRows = dp.inv.numRows;
    }

    @After
    public void restorePreviousValues() {
        Context c = new ActivityContextWrapper(getApplicationContext());
        DeviceProfile dp = InvariantDeviceProfile.INSTANCE.get(c).getDeviceProfile(c).copy(c);
        dp.inv.numColumns = mPrevNumColumns;
        dp.inv.numRows = mPrevNumRows;
    }

    @SuppressWarnings("UnusedMethod")
    /**
     * Utility function used to generate all the test cases
     */
    private ReorderAlgorithmUnitTestCase generateRandomTestCase() {
        ReorderAlgorithmUnitTestCase testCase = new ReorderAlgorithmUnitTestCase();

        int width = getRandom(3, 8);
        int height = getRandom(3, 8);

        int targetWidth = getRandom(1, width - 2);
        int targetHeight = getRandom(1, height - 2);

        int minTargetWidth = getRandom(1, targetWidth);
        int minTargetHeight = getRandom(1, targetHeight);

        int x = getRandom(0, width - targetWidth);
        int y = getRandom(0, height - targetHeight);

        CellLayoutBoard board = generateBoard(new CellLayoutBoard(width, height),
                new Rect(0, 0, width, height), targetWidth * targetHeight);

        CellLayout.ItemConfiguration solution = solve(board, x, y, targetWidth, targetHeight,
                minTargetWidth, minTargetHeight);

        CellLayoutBoard finishBoard = solution.isSolution ? boardFromSolution(solution,
                board.getWidth(), board.getHeight()) : new CellLayoutBoard(board.getWidth(),
                board.getHeight());


        testCase.startBoard = board;
        testCase.endBoard = finishBoard;
        testCase.isValidSolution = solution.isSolution;
        testCase.x = x;
        testCase.y = y;
        testCase.spanX = targetWidth;
        testCase.spanY = targetHeight;
        testCase.minSpanX = minTargetWidth;
        testCase.minSpanY = minTargetHeight;
        testCase.type = solution.area() == 1 ? "icon" : "widget";

        return testCase;
    }

    private int getRandom(int start, int end) {
        int random = end == 0 ? 0 : new Random().nextInt(end);
        return start + random;
    }

    private CellLayoutBoard generateBoard(CellLayoutBoard board, Rect area,
            int emptySpaces) {
        if (area.height() * area.width() <= 0) return board;

        int width = getRandom(1, area.width() - 1);
        int height = getRandom(1, area.height() - 1);

        int x = area.left + getRandom(0, area.width() - width);
        int y = area.top + getRandom(0, area.height() - height);

        if (emptySpaces > 0) {
            emptySpaces -= width * height;
        } else if (width * height > 1) {
            board.addWidget(x, y, width, height);
        } else {
            board.addIcon(x, y);
        }

        generateBoard(board,
                new Rect(area.left, area.top, area.right, y), emptySpaces);
        generateBoard(board,
                new Rect(area.left, y, x, area.bottom), emptySpaces);
        generateBoard(board,
                new Rect(x, y + height, area.right, area.bottom), emptySpaces);
        generateBoard(board,
                new Rect(x + width, y, area.right, y + height), emptySpaces);

        return board;
    }

    private static List<ReorderAlgorithmUnitTestCase> getTestCases(String testPath)
            throws IOException {
        List<ReorderAlgorithmUnitTestCase> cases = new ArrayList<>();
        Iterator<CellLayoutTestCaseReader.TestSection> iterableSection =
                CellLayoutTestCaseReader.readFromFile(testPath).parse().iterator();
        while (iterableSection.hasNext()) {
            cases.add(ReorderAlgorithmUnitTestCase.readNextCase(iterableSection));
        }
        return cases;
    }
}
