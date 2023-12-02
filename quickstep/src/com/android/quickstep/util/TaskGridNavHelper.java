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

import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;

import com.android.launcher3.util.IntArray;

import java.lang.annotation.Retention;

/**
 * Helper class for navigating RecentsView grid tasks via arrow keys and tab.
 */
public class TaskGridNavHelper {
    public static final int CLEAR_ALL_PLACEHOLDER_ID = -1;
    public static final int INVALID_FOCUSED_TASK_ID = -1;

    public static final int DIRECTION_UP = 0;
    public static final int DIRECTION_DOWN = 1;
    public static final int DIRECTION_LEFT = 2;
    public static final int DIRECTION_RIGHT = 3;
    public static final int DIRECTION_TAB = 4;

    @Retention(SOURCE)
    @IntDef({DIRECTION_UP, DIRECTION_DOWN, DIRECTION_LEFT, DIRECTION_RIGHT, DIRECTION_TAB})
    public @interface TASK_NAV_DIRECTION {}

    private final IntArray mOriginalTopRowIds;
    private IntArray mTopRowIds;
    private IntArray mBottomRowIds;
    private final int mFocusedTaskId;

    public TaskGridNavHelper(IntArray topIds, IntArray bottomIds, int focusedTaskId) {
        mFocusedTaskId = focusedTaskId;
        mOriginalTopRowIds = topIds.clone();
        generateTaskViewIdGrid(topIds, bottomIds);
    }

    private void generateTaskViewIdGrid(IntArray topRowIdArray, IntArray bottomRowIdArray) {
        boolean hasFocusedTask = mFocusedTaskId != INVALID_FOCUSED_TASK_ID;
        int maxSize =
                Math.max(topRowIdArray.size(), bottomRowIdArray.size()) + (hasFocusedTask ? 1 : 0);
        int minSize =
                Math.min(topRowIdArray.size(), bottomRowIdArray.size()) + (hasFocusedTask ? 1 : 0);

        // Add the focused task to the beginning of both arrays if it exists.
        if (hasFocusedTask) {
            topRowIdArray.add(0, mFocusedTaskId);
            bottomRowIdArray.add(0, mFocusedTaskId);
        }

        // Fill in the shorter array with the ids from the longer one.
        for (int i = minSize; i < maxSize; i++) {
            if (i >= topRowIdArray.size()) {
                topRowIdArray.add(bottomRowIdArray.get(i));
            } else {
                bottomRowIdArray.add(topRowIdArray.get(i));
            }
        }

        // Add the clear all button to the end of both arrays
        topRowIdArray.add(CLEAR_ALL_PLACEHOLDER_ID);
        bottomRowIdArray.add(CLEAR_ALL_PLACEHOLDER_ID);

        mTopRowIds = topRowIdArray;
        mBottomRowIds = bottomRowIdArray;
    }

    /**
     * Returns the id of the next page in the grid or -1 for the clear all button.
     */
    public int getNextGridPage(int currentPageTaskViewId, int delta,
            @TASK_NAV_DIRECTION int direction, boolean cycle) {
        boolean inTop = mTopRowIds.contains(currentPageTaskViewId);
        int index = inTop ? mTopRowIds.indexOf(currentPageTaskViewId)
                : mBottomRowIds.indexOf(currentPageTaskViewId);
        int maxSize = Math.max(mTopRowIds.size(), mBottomRowIds.size());
        int nextIndex = index + delta;

        switch (direction) {
            case DIRECTION_UP:
            case DIRECTION_DOWN: {
                return inTop ? mBottomRowIds.get(index) : mTopRowIds.get(index);
            }
            case DIRECTION_LEFT: {
                int boundedIndex = cycle ? nextIndex % maxSize : Math.min(nextIndex, maxSize - 1);
                return inTop ? mTopRowIds.get(boundedIndex)
                        : mBottomRowIds.get(boundedIndex);
            }
            case DIRECTION_RIGHT: {
                int boundedIndex =
                        cycle ? (nextIndex < 0 ? maxSize - 1 : nextIndex) : Math.max(
                                nextIndex, 0);
                boolean inOriginalTop = mOriginalTopRowIds.contains(currentPageTaskViewId);
                return inOriginalTop ? mTopRowIds.get(boundedIndex)
                        : mBottomRowIds.get(boundedIndex);
            }
            case DIRECTION_TAB: {
                int boundedIndex =
                        cycle ? nextIndex < 0 ? maxSize - 1 : nextIndex % maxSize : Math.min(
                                nextIndex, maxSize - 1);
                if (delta >= 0) {
                    return inTop && mTopRowIds.get(index) != mBottomRowIds.get(index)
                            ? mBottomRowIds.get(index)
                            : mTopRowIds.get(boundedIndex);
                } else {
                    if (mTopRowIds.contains(currentPageTaskViewId)) {
                        return mBottomRowIds.get(boundedIndex);
                    } else {
                        // Go up to top if there is task above
                        return mTopRowIds.get(index) != mBottomRowIds.get(index)
                                ? mTopRowIds.get(index)
                                : mBottomRowIds.get(boundedIndex);
                    }
                }
            }
            default:
                return currentPageTaskViewId;
        }
    }
}
