/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.animation.ValueAnimator.areAnimatorsEnabled;

import static com.android.launcher3.widget.picker.WidgetsListAdapter.VIEW_TYPE_WIDGETS_LIST;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

public class WidgetsListItemAnimator extends DefaultItemAnimator {
    public static final int CHANGE_DURATION_MS = 90;
    public static final int MOVE_DURATION_MS = 90;
    public static final int ADD_DURATION_MS = 120;

    // DefaultItemAnimator runs change and move animations before running add animations (i.e.
    // before expanded list item's content start animating to become visible on screen).
    public static final int WIDGET_LIST_ITEM_APPEARANCE_START_DELAY =
            areAnimatorsEnabled() ? (CHANGE_DURATION_MS + MOVE_DURATION_MS) : 0;
    // Delay after which all item animations are ran and list item's content is visible.
    public static final int WIDGET_LIST_ITEM_APPEARANCE_DELAY =
            WIDGET_LIST_ITEM_APPEARANCE_START_DELAY + ADD_DURATION_MS;

    public WidgetsListItemAnimator() {
        super();

        // Disable change animations because it disrupts the item focus upon adapter item
        // change.
        setSupportsChangeAnimations(false);
        // Make the moves a bit faster, so that the amount of time for which user sees the
        // bottom-sheet background before "add" animation starts is less making it smoother.
        setChangeDuration(CHANGE_DURATION_MS);
        setMoveDuration(MOVE_DURATION_MS);
        setAddDuration(ADD_DURATION_MS);
    }
    @Override
    public boolean animateChange(RecyclerView.ViewHolder oldHolder,
            RecyclerView.ViewHolder newHolder, int fromLeft, int fromTop, int toLeft,
            int toTop) {
        // As we expand an item, the content / widgets list that appears (with add
        // event) also gets change events as its previews load asynchronously. The
        // super implementation of animateChange cancels the animations on it - breaking
        // the "add animation". Instead, here, we skip "change" animation for content
        // list - because we want it to either appear or disappear. And, the previews
        // themselves have their own animation when loaded, so, we don't need change
        // animations for them anyway. Below, we do-nothing.
        if (oldHolder.getItemViewType() == VIEW_TYPE_WIDGETS_LIST) {
            dispatchChangeStarting(oldHolder, true);
            dispatchChangeFinished(oldHolder, true);
            return true;
        }
        return super.animateChange(oldHolder, newHolder, fromLeft, fromTop, toLeft,
                toTop);
    }
}
