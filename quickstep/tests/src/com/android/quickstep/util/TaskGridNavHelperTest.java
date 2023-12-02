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

package com.android.quickstep.util;

import static com.android.quickstep.util.TaskGridNavHelper.CLEAR_ALL_PLACEHOLDER_ID;
import static com.android.quickstep.util.TaskGridNavHelper.INVALID_FOCUSED_TASK_ID;

import static org.junit.Assert.assertEquals;

import com.android.launcher3.util.IntArray;

import org.junit.Test;

public class TaskGridNavHelperTest {

    @Test
    public void equalLengthRows_noFocused_onTop_pressDown_goesToBottom() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int currentPageTaskViewId = 1;
        int delta = 1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_DOWN;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", 2, nextGridPage);
    }

    @Test
    public void equalLengthRows_noFocused_onTop_pressUp_goesToBottom() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int currentPageTaskViewId = 1;
        int delta = 1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_UP;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", 2, nextGridPage);
    }

    @Test
    public void equalLengthRows_noFocused_onBottom_pressDown_goesToTop() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int currentPageTaskViewId = 2;
        int delta = 1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_DOWN;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", 1, nextGridPage);
    }

    @Test
    public void equalLengthRows_noFocused_onBottom_pressUp_goesToTop() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int currentPageTaskViewId = 2;
        int delta = 1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_UP;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", 1, nextGridPage);
    }

    @Test
    public void equalLengthRows_noFocused_onTop_pressLeft_goesLeft() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int currentPageTaskViewId = 1;
        int delta = 1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_LEFT;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", 3, nextGridPage);
    }

    @Test
    public void equalLengthRows_noFocused_onBottom_pressLeft_goesLeft() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int currentPageTaskViewId = 2;
        int delta = 1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_LEFT;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", 4, nextGridPage);
    }

    @Test
    public void equalLengthRows_noFocused_onTop_secondItem_pressRight_goesRight() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int currentPageTaskViewId = 3;
        int delta = -1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_RIGHT;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", 1, nextGridPage);
    }

    @Test
    public void equalLengthRows_noFocused_onBottom_secondItem_pressRight_goesRight() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int currentPageTaskViewId = 4;
        int delta = -1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_RIGHT;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", 2, nextGridPage);
    }

    @Test
    public void equalLengthRows_noFocused_onTop_pressRight_cycleToClearAll() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int currentPageTaskViewId = 1;
        int delta = -1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_RIGHT;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", CLEAR_ALL_PLACEHOLDER_ID, nextGridPage);
    }

    @Test
    public void equalLengthRows_noFocused_onBottom_pressRight_cycleToClearAll() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int currentPageTaskViewId = 2;
        int delta = -1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_RIGHT;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", CLEAR_ALL_PLACEHOLDER_ID, nextGridPage);
    }

    @Test
    public void equalLengthRows_noFocused_onTop_lastItem_pressLeft_toClearAll() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int currentPageTaskViewId = 5;
        int delta = 1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_LEFT;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", CLEAR_ALL_PLACEHOLDER_ID, nextGridPage);
    }

    @Test
    public void equalLengthRows_noFocused_onBottom_lastItem_pressLeft_toClearAll() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int currentPageTaskViewId = 6;
        int delta = 1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_LEFT;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", CLEAR_ALL_PLACEHOLDER_ID, nextGridPage);
    }

    @Test
    public void equalLengthRows_noFocused_onClearAll_pressLeft_cycleToFirst() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int currentPageTaskViewId = CLEAR_ALL_PLACEHOLDER_ID;
        int delta = 1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_LEFT;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", 1, nextGridPage);
    }

    @Test
    public void equalLengthRows_noFocused_onClearAll_pressRight_toLastInBottom() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int currentPageTaskViewId = CLEAR_ALL_PLACEHOLDER_ID;
        int delta = -1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_RIGHT;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", 6, nextGridPage);
    }

    @Test
    public void equalLengthRows_withFocused_onFocused_pressLeft_toTop() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int focusedTaskId = 99;
        int currentPageTaskViewId = focusedTaskId;
        int delta = 1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_LEFT;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, focusedTaskId);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", 1, nextGridPage);
    }

    @Test
    public void equalLengthRows_withFocused_onFocused_pressUp_stayOnFocused() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int focusedTaskId = 99;
        int currentPageTaskViewId = focusedTaskId;
        int delta = 1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_UP;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, focusedTaskId);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", focusedTaskId, nextGridPage);
    }

    @Test
    public void equalLengthRows_withFocused_onFocused_pressDown_stayOnFocused() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int focusedTaskId = 99;
        int currentPageTaskViewId = focusedTaskId;
        int delta = 1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_DOWN;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, focusedTaskId);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", focusedTaskId, nextGridPage);
    }

    @Test
    public void equalLengthRows_withFocused_onFocused_pressRight_cycleToClearAll() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int focusedTaskId = 99;
        int currentPageTaskViewId = focusedTaskId;
        int delta = -1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_RIGHT;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, focusedTaskId);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", CLEAR_ALL_PLACEHOLDER_ID, nextGridPage);
    }

    @Test
    public void equalLengthRows_withFocused_onClearAll_pressLeft_cycleToFocusedTask() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int focusedTaskId = 99;
        int currentPageTaskViewId = CLEAR_ALL_PLACEHOLDER_ID;
        int delta = 1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_LEFT;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, focusedTaskId);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", focusedTaskId, nextGridPage);
    }

    @Test
    public void longerTopRow_noFocused_atEndTopBeyondBottom_pressDown_stayTop() {
        IntArray topIds = IntArray.wrap(1, 3, 5, 7);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int currentPageTaskViewId = 7;
        int delta = 1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_DOWN;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", 7, nextGridPage);
    }

    @Test
    public void longerTopRow_noFocused_atEndTopBeyondBottom_pressUp_stayTop() {
        IntArray topIds = IntArray.wrap(1, 3, 5, 7);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int currentPageTaskViewId = 7;
        int delta = 1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_UP;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", 7, nextGridPage);
    }

    @Test
    public void longerTopRow_noFocused_atEndBottom_pressLeft_goToTop() {
        IntArray topIds = IntArray.wrap(1, 3, 5, 7);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int currentPageTaskViewId = 6;
        int delta = 1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_LEFT;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", 7, nextGridPage);
    }

    @Test
    public void longerTopRow_noFocused_atClearAll_pressRight_goToLonger() {
        IntArray topIds = IntArray.wrap(1, 3, 5, 7);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int currentPageTaskViewId = CLEAR_ALL_PLACEHOLDER_ID;
        int delta = -1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_RIGHT;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", 7, nextGridPage);
    }

    @Test
    public void longerBottomRow_noFocused_atClearAll_pressRight_goToLonger() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6, 7);
        int currentPageTaskViewId = CLEAR_ALL_PLACEHOLDER_ID;
        int delta = -1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_RIGHT;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", 7, nextGridPage);
    }

    @Test
    public void equalLengthRows_noFocused_onTop_pressTab_goesToBottom() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int currentPageTaskViewId = 1;
        int delta = 1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_TAB;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", 2, nextGridPage);
    }

    @Test
    public void equalLengthRows_noFocused_onBottom_pressTab_goesToNextTop() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int currentPageTaskViewId = 2;
        int delta = 1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_TAB;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", 3, nextGridPage);
    }

    @Test
    public void equalLengthRows_noFocused_onTop_pressTabWithShift_goesToPreviousBottom() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int currentPageTaskViewId = 3;
        int delta = -1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_TAB;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", 2, nextGridPage);
    }

    @Test
    public void equalLengthRows_noFocused_onBottom_pressTabWithShift_goesToTop() {
        IntArray topIds = IntArray.wrap(1, 3, 5);
        IntArray bottomIds = IntArray.wrap(2, 4, 6);
        int currentPageTaskViewId = 2;
        int delta = -1;
        @TaskGridNavHelper.TASK_NAV_DIRECTION int direction = TaskGridNavHelper.DIRECTION_TAB;
        boolean cycle = true;
        TaskGridNavHelper taskGridNavHelper =
                new TaskGridNavHelper(topIds, bottomIds, INVALID_FOCUSED_TASK_ID);

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);

        assertEquals("Wrong next page returned.", 1, nextGridPage);
    }
}
