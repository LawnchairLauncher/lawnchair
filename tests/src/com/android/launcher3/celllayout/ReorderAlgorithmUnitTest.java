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
import android.util.Log;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.MultipageCellLayout;
import com.android.launcher3.celllayout.board.CellLayoutBoard;
import com.android.launcher3.celllayout.board.IconPoint;
import com.android.launcher3.celllayout.board.PermutedBoardComparator;
import com.android.launcher3.celllayout.board.WidgetRect;
import com.android.launcher3.celllayout.testgenerator.RandomBoardGenerator;
import com.android.launcher3.celllayout.testgenerator.RandomMultiBoardGenerator;
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

    private static final String TAG = "ReorderAlgorithmUnitTest";
    private static final char MAIN_WIDGET_TYPE = 'z';

    // There is nothing special about this numbers, the random seed is just to be able to reproduce
    // the test cases and the height and width is a random number similar to what users expect on
    // their devices
    private static final int SEED = 897;
    private static final int MAX_BOARD_SIZE = 13;

    private static final int TOTAL_OF_CASES_GENERATED = 300;
    private Context mApplicationContext;

    private int mPrevNumColumns, mPrevNumRows;

    /**
     * This test reads existing test cases and makes sure the CellLayout produces the same
     * output for each of them for a given input.
     */
    @Test
    public void testAllCases() throws IOException {
        List<ReorderAlgorithmUnitTestCase> testCases = getTestCases(
                "ReorderAlgorithmUnitTest/reorder_algorithm_test_cases");
        mApplicationContext = new ActivityContextWrapper(getApplicationContext());
        List<Integer> failingCases = new ArrayList<>();
        for (int i = 0; i < testCases.size(); i++) {
            try {
                evaluateTestCase(testCases.get(i), false);
            } catch (AssertionError e) {
                e.printStackTrace();
                failingCases.add(i);
            }
        }
        assertEquals("Some test cases failed " + Arrays.toString(failingCases.toArray()), 0,
                failingCases.size());
    }

    /**
     * This test generates random CellLayout configurations and then try to reorder it and makes
     * sure the result is a valid board meaning it didn't remove any widget or icon.
     */
    @Test
    public void generateValidTests() {
        Random generator = new Random(SEED);
        mApplicationContext = new ActivityContextWrapper(getApplicationContext());
        for (int i = 0; i < TOTAL_OF_CASES_GENERATED; i++) {
            // Using a new seed so that we can replicate the same test cases.
            int seed = generator.nextInt();
            Log.d(TAG, "Seed = " + seed);
            ReorderAlgorithmUnitTestCase testCase = generateRandomTestCase(
                    new RandomBoardGenerator(new Random(seed))
            );
            Log.d(TAG, "testCase = " + testCase);
            assertTrue("invalid case " + i,
                    validateIntegrity(testCase.startBoard, testCase.endBoard, testCase));
        }
    }

    /**
     * Same as above but testing the Multipage CellLayout.
     */
    @Test
    public void generateValidTests_Multi() {
        Random generator = new Random(SEED);
        mApplicationContext = new ActivityContextWrapper(getApplicationContext());
        for (int i = 0; i < TOTAL_OF_CASES_GENERATED; i++) {
            // Using a new seed so that we can replicate the same test cases.
            int seed = generator.nextInt();
            Log.d(TAG, "Seed = " + seed);
            ReorderAlgorithmUnitTestCase testCase = generateRandomTestCase(
                    new RandomMultiBoardGenerator(new Random(seed))
            );
            Log.d(TAG, "testCase = " + testCase);
            assertTrue("invalid case " + i,
                    validateIntegrity(testCase.startBoard, testCase.endBoard, testCase));
        }
    }

    private void addViewInCellLayout(CellLayout cellLayout, int cellX, int cellY, int spanX,
            int spanY, boolean isWidget) {
        View cell = isWidget ? new View(mApplicationContext) : new DoubleShadowBubbleTextView(
                mApplicationContext);
        cell.setLayoutParams(new CellLayoutLayoutParams(cellX, cellY, spanX, spanY));
        cellLayout.addViewToCellLayout(cell, -1, cell.getId(),
                (CellLayoutLayoutParams) cell.getLayoutParams(), true);
    }

    public CellLayout createCellLayout(int width, int height, boolean isMulti) {
        Context c = mApplicationContext;
        DeviceProfile dp = InvariantDeviceProfile.INSTANCE.get(c).getDeviceProfile(c).copy(c);
        // modify the device profile.
        dp.inv.numColumns = isMulti ? width / 2 : width;
        dp.inv.numRows = height;
        dp.cellLayoutBorderSpacePx = new Point(0, 0);

        CellLayout cl = isMulti ? new MultipageCellLayout(getWrappedContext(c, dp))
                : new CellLayout(getWrappedContext(c, dp));
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

    public ItemConfiguration solve(CellLayoutBoard board, int x, int y, int spanX,
            int spanY, int minSpanX, int minSpanY, boolean isMulti) {
        CellLayout cl = createCellLayout(board.getWidth(), board.getHeight(), isMulti);

        // The views have to be sorted or the result can vary
        board.getIcons()
                .stream()
                .map(IconPoint::getCoord)
                .sorted(Comparator.comparing(p -> ((Point) p).x).thenComparing(p -> ((Point) p).y))
                .forEach(p -> addViewInCellLayout(cl, p.x, p.y, 1, 1, false));
        board.getWidgets()
                .stream()
                .sorted(Comparator
                        .comparing(WidgetRect::getCellX)
                        .thenComparing(WidgetRect::getCellY)
                ).forEach(
                        widget -> addViewInCellLayout(cl, widget.getCellX(), widget.getCellY(),
                                widget.getSpanX(), widget.getSpanY(), true)
                );

        int[] testCaseXYinPixels = new int[2];
        cl.regionToCenterPoint(x, y, spanX, spanY, testCaseXYinPixels);
        ItemConfiguration solution = cl.createReorderAlgorithm().calculateReorder(
                testCaseXYinPixels[0], testCaseXYinPixels[1], minSpanX, minSpanY, spanX, spanY,
                null);
        if (solution == null) {
            solution = new ItemConfiguration();
            solution.isSolution = false;
        }
        if (!solution.isSolution) {
            cl.copyCurrentStateToSolution(solution);
            if (cl instanceof MultipageCellLayout) {
                solution =
                        ((MultipageCellLayout) cl).createReorderAlgorithm().removeSeamFromSolution(
                                solution);
            }
            solution.isSolution = false;
        }
        return solution;
    }

    public CellLayoutBoard boardFromSolution(ItemConfiguration solution, int width,
            int height) {
        // Update the views with solution value
        solution.map.forEach((key, val) -> key.setLayoutParams(
                new CellLayoutLayoutParams(val.cellX, val.cellY, val.spanX, val.spanY)));
        CellLayoutBoard board = CellLayoutTestUtils.viewsToBoard(
                new ArrayList<>(solution.map.keySet()), width, height);
        if (solution.isSolution) {
            board.addWidget(solution.cellX, solution.cellY, solution.spanX, solution.spanY,
                    MAIN_WIDGET_TYPE);
        }
        return board;
    }

    public void evaluateTestCase(ReorderAlgorithmUnitTestCase testCase, boolean isMultiCellLayout) {
        ItemConfiguration solution = solve(testCase.startBoard, testCase.x, testCase.y,
                testCase.spanX, testCase.spanY, testCase.minSpanX, testCase.minSpanY,
                isMultiCellLayout);
        assertEquals("should be a valid solution", solution.isSolution, testCase.isValidSolution);
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

    private ReorderAlgorithmUnitTestCase generateRandomTestCase(
            RandomBoardGenerator boardGenerator) {
        ReorderAlgorithmUnitTestCase testCase = new ReorderAlgorithmUnitTestCase();

        boolean isMultiCellLayout = boardGenerator instanceof RandomMultiBoardGenerator;

        int width = isMultiCellLayout
                ? boardGenerator.getRandom(3, MAX_BOARD_SIZE / 2) * 2
                : boardGenerator.getRandom(3, MAX_BOARD_SIZE);
        int height = boardGenerator.getRandom(3, MAX_BOARD_SIZE);

        int targetWidth = boardGenerator.getRandom(1, width - 2);
        int targetHeight = boardGenerator.getRandom(1, height - 2);

        int minTargetWidth = boardGenerator.getRandom(1, targetWidth);
        int minTargetHeight = boardGenerator.getRandom(1, targetHeight);

        int x = boardGenerator.getRandom(0, width - targetWidth);
        int y = boardGenerator.getRandom(0, height - targetHeight);

        CellLayoutBoard board = boardGenerator.generateBoard(width, height,
                targetWidth * targetHeight);

        ItemConfiguration solution = solve(board, x, y, targetWidth, targetHeight,
                minTargetWidth, minTargetHeight, isMultiCellLayout);

        CellLayoutBoard finishBoard = boardFromSolution(solution, board.getWidth(),
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

    /**
     * Makes sure the final solution has valid integrity meaning that the number and sizes of
     * widgets is the expect and there are no missing widgets.
     */
    public boolean validateIntegrity(CellLayoutBoard startBoard, CellLayoutBoard finishBoard,
            ReorderAlgorithmUnitTestCase testCase) {
        if (!testCase.isValidSolution) {
            // if we couldn't place the widget then the solution should be identical to the board
            return startBoard.compareTo(finishBoard) == 0;
        }
        WidgetRect addedWidget = finishBoard.getWidgetOfType(MAIN_WIDGET_TYPE);
        finishBoard.removeItem(MAIN_WIDGET_TYPE);
        Comparator<CellLayoutBoard> comparator = new PermutedBoardComparator();
        if (comparator.compare(startBoard, finishBoard) != 0) {
            return false;
        }
        return addedWidget.getSpanX() >= testCase.minSpanX
                && addedWidget.getSpanY() >= testCase.minSpanY;
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
