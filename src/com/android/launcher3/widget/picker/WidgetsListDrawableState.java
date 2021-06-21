/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.widget.picker;

/**
 * Different possible list position states for an item in the widgets list to have. Note that only
 * headers use the expanded state.
 */
enum WidgetsListDrawableState {
    FIRST(new int[]{android.R.attr.state_first}),
    FIRST_EXPANDED(new int[]{android.R.attr.state_first, android.R.attr.state_expanded}),
    MIDDLE(new int[]{android.R.attr.state_middle}),
    MIDDLE_EXPANDED(new int[]{android.R.attr.state_middle, android.R.attr.state_expanded}),
    LAST(new int[]{android.R.attr.state_last}),
    SINGLE(new int[]{android.R.attr.state_single});

    final int[] mStateSet;

    WidgetsListDrawableState(int[] stateSet) {
        mStateSet = stateSet;
    }

    static WidgetsListDrawableState obtain(boolean isFirst, boolean isLast, boolean isExpanded) {
        if (isFirst && isLast) return SINGLE;
        if (isFirst && isExpanded) return FIRST_EXPANDED;
        if (isFirst) return FIRST;
        if (isLast) return LAST;
        if (isExpanded) return MIDDLE_EXPANDED;
        return MIDDLE;
    }
}
