/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.launcher3.util;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.KeyEvent;
import android.view.View;

import com.android.launcher3.util.FocusLogic;

/**
 * Tests the {@link FocusLogic} class that handles key event based focus handling.
 */
@SmallTest
public final class FocusLogicTest extends AndroidTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Nothing to set up as this class only tests static methods.
    }

    @Override
    protected void tearDown() throws Exception {
        // Nothing to tear down as this class only tests static methods.
    }

    public void testShouldConsume() {
         assertTrue(FocusLogic.shouldConsume(KeyEvent.KEYCODE_DPAD_LEFT));
         assertTrue(FocusLogic.shouldConsume(KeyEvent.KEYCODE_DPAD_RIGHT));
         assertTrue(FocusLogic.shouldConsume(KeyEvent.KEYCODE_DPAD_UP));
         assertTrue(FocusLogic.shouldConsume(KeyEvent.KEYCODE_DPAD_DOWN));
         assertTrue(FocusLogic.shouldConsume(KeyEvent.KEYCODE_MOVE_HOME));
         assertTrue(FocusLogic.shouldConsume(KeyEvent.KEYCODE_MOVE_END));
         assertTrue(FocusLogic.shouldConsume(KeyEvent.KEYCODE_PAGE_UP));
         assertTrue(FocusLogic.shouldConsume(KeyEvent.KEYCODE_PAGE_DOWN));
    }

    public void testCreateSparseMatrix() {
         // Either, 1) create a helper method to generate/instantiate all possible cell layout that
         // may get created in real world to test this method. OR 2) Move all the matrix
         // management routine to celllayout and write tests for them.
    }

    public void testMoveFromBottomRightToBottomLeft() {
        int[][] map = transpose(new int[][] {
                {-1, 0, -1, -1, -1, -1},
                {-1, -1, -1, -1, -1, -1},
                {-1, -1, -1, -1, -1, -1},
                {-1, -1, -1, -1, -1, -1},
                {100, 1, -1, -1, -1, -1},
        });
        int i = FocusLogic.handleKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, map, 100, 1, 2, false);
        assertEquals(1, i);
    }

    public void testMoveFromBottomRightToTopLeft() {
        int[][] map = transpose(new int[][] {
                {-1, 0, -1, -1, -1, -1},
                {-1, -1, -1, -1, -1, -1},
                {-1, -1, -1, -1, -1, -1},
                {-1, -1, -1, -1, -1, -1},
                {100, -1, -1, -1, -1, -1},
        });
        int i = FocusLogic.handleKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, map, 100, 1, 2, false);
        assertEquals(FocusLogic.NEXT_PAGE_FIRST_ITEM, i);
    }

    public void testMoveIntoHotseatWithEqualHotseatAndWorkspaceColumns() {
        // Test going from an icon right above the All Apps button to the All Apps button.
        int[][] map = transpose(new int[][] {
                {-1, -1, -1, -1, -1},
                {-1, -1, -1, -1, -1},
                {-1, -1, -1, -1, -1},
                {-1, -1,  0, -1, -1},
                { 2,  3,  1,  4,  5},
        });
        int i = FocusLogic.handleKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, map, 0, 1, 1, true);
        assertEquals(1, i);
        // Test going from an icon above and to the right of the All Apps
        // button to an icon to the right of the All Apps button.
        map = transpose(new int[][] {
                {-1, -1, -1, -1, -1},
                {-1, -1, -1, -1, -1},
                {-1, -1, -1, -1, -1},
                {-1, -1, -1,  0, -1},
                { 2,  3,  1,  4,  5},
        });
        i = FocusLogic.handleKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, map, 0, 1, 1, true);
        assertEquals(4, i);
    }

    public void testMoveIntoHotseatWithExtraColumnForAllApps() {
        // Test going from an icon above and to the left
        // of the All Apps button to the All Apps button.
        int[][] map = transpose(new int[][] {
                {-1, -1, -1,-11, -1, -1, -1},
                {-1, -1, -1,-11, -1, -1, -1},
                {-1, -1, -1,-11, -1, -1, -1},
                {-1, -1, -1,-11, -1, -1, -1},
                {-1, -1,  0,-11, -1, -1, -1},
                {-1, -1, -1,  1,  1, -1, -1},
        });
        int i = FocusLogic.handleKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, map, 0, 1, 1, true);
        assertEquals(1, i);
        // Test going from an icon above and to the right
        // of the All Apps button to the All Apps button.
        map = transpose(new int[][] {
                {-1, -1, -1,-11, -1, -1, -1},
                {-1, -1, -1,-11, -1, -1, -1},
                {-1, -1, -1,-11, -1, -1, -1},
                {-1, -1, -1,-11, -1, -1, -1},
                {-1, -1, -1,-11,  0, -1, -1},
                {-1, -1, -1,  1, -1, -1, -1},
        });
        i = FocusLogic.handleKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, map, 0, 1, 1, true);
        assertEquals(1, i);
        // Test going from the All Apps button to an icon
        // above and to the right of the All Apps button.
        map = transpose(new int[][] {
                {-1, -1, -1,-11, -1, -1, -1},
                {-1, -1, -1,-11, -1, -1, -1},
                {-1, -1, -1,-11, -1, -1, -1},
                {-1, -1, -1,-11, -1, -1, -1},
                {-1, -1, -1,-11,  0, -1, -1},
                {-1, -1, -1,  1, -1, -1, -1},
        });
        i = FocusLogic.handleKeyEvent(KeyEvent.KEYCODE_DPAD_UP, map, 1, 1, 1, true);
        assertEquals(0, i);
        // Test going from an icon above and to the left of the
        // All Apps button in landscape to the All Apps button.
        map = transpose(new int[][] {
                { -1, -1, -1, -1, -1},
                { -1, -1, -1,  0, -1},
                {-11,-11,-11,-11,  1},
                { -1, -1, -1, -1, -1},
                { -1, -1, -1, -1, -1},
        });
        i = FocusLogic.handleKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, map, 0, 1, 1, true);
        assertEquals(1, i);
        // Test going from the All Apps button in landscape to
        // an icon above and to the left of the All Apps button.
        map = transpose(new int[][] {
                { -1, -1, -1, -1, -1},
                { -1, -1, -1,  0, -1},
                {-11,-11,-11,-11,  1},
                { -1, -1, -1, -1, -1},
                { -1, -1, -1, -1, -1},
        });
        i = FocusLogic.handleKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, map, 1, 1, 1, true);
        assertEquals(0, i);
        // Test that going to the hotseat always goes to the same row as the original icon.
        map = transpose(new int[][]{
                { 0,  1,  2,-11,  3,  4,  5},
                {-1, -1, -1,-11, -1, -1, -1},
                {-1, -1, -1,-11, -1, -1, -1},
                {-1, -1, -1,-11, -1, -1, -1},
                {-1, -1, -1,-11, -1, -1, -1},
                { 7,  8,  9,  6, 10, 11, 12},
        });
        i = FocusLogic.handleKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, map, 0, 1, 1, true);
        assertEquals(7, i);
        i = FocusLogic.handleKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, map, 1, 1, 1, true);
        assertEquals(8, i);
        i = FocusLogic.handleKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, map, 2, 1, 1, true);
        assertEquals(9, i);
        i = FocusLogic.handleKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, map, 3, 1, 1, true);
        assertEquals(10, i);
        i = FocusLogic.handleKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, map, 4, 1, 1, true);
        assertEquals(11, i);
        i = FocusLogic.handleKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, map, 5, 1, 1, true);
        assertEquals(12, i);
    }

    public void testCrossingAllAppsColumn() {
        // Test crossing from left to right in portrait.
        int[][] map = transpose(new int[][] {
                {-1, -1,-11, -1, -1},
                {-1,  0,-11, -1, -1},
                {-1, -1,-11,  1, -1},
                {-1, -1,-11, -1, -1},
                {-1, -1,  2, -1, -1},
        });
        int i = FocusLogic.handleKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, map, 0, 1, 1, true);
        assertEquals(1, i);
        // Test crossing from right to left in portrait.
        map = transpose(new int[][] {
                {-1, -1,-11, -1, -1},
                {-1, -1,-11,  0, -1},
                {-1,  1,-11, -1, -1},
                {-1, -1,-11, -1, -1},
                {-1, -1,  2, -1, -1},
        });
        i = FocusLogic.handleKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, map, 0, 1, 1, true);
        assertEquals(1, i);
        // Test crossing from left to right in landscape.
        map = transpose(new int[][] {
                { -1, -1, -1, -1, -1},
                { -1, -1, -1,  0, -1},
                {-11,-11,-11,-11,  2},
                { -1,  1, -1, -1, -1},
                { -1, -1, -1, -1, -1},
        });
        i = FocusLogic.handleKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, map, 0, 1, 1, true);
        assertEquals(1, i);
        // Test crossing from right to left in landscape.
        map = transpose(new int[][] {
                { -1, -1, -1, -1, -1},
                { -1,  0, -1, -1, -1},
                {-11,-11,-11,-11,  2},
                { -1, -1,  1, -1, -1},
                { -1, -1, -1, -1, -1},
        });
        i = FocusLogic.handleKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, map, 0, 1, 1, true);
        assertEquals(1, i);
        // Test NOT crossing it, if the All Apps button is the only suitable candidate.
        map = transpose(new int[][]{
                {-1, 0, -1, -1, -1},
                {-1, 1, -1, -1, -1},
                {-11, -11, -11, -11, 4},
                {-1, 2, -1, -1, -1},
                {-1, 3, -1, -1, -1},
        });
        i = FocusLogic.handleKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, map, 1, 1, 1, true);
        assertEquals(4, i);
        i = FocusLogic.handleKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, map, 2, 1, 1, true);
        assertEquals(4, i);
    }

    /** Transposes the matrix so that we can write it in human-readable format in the tests. */
    private int[][] transpose(int[][] m) {
        int[][] t = new int[m[0].length][m.length];
        for (int i = 0; i < m.length; i++) {
            for (int j = 0; j < m[0].length; j++) {
                t[j][i] = m[i][j];
            }
        }
        return t;
    }
}
