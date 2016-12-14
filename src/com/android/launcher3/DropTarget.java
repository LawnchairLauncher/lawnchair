/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import android.graphics.Rect;

import com.android.launcher3.accessibility.DragViewStateAnnouncer;
import com.android.launcher3.dragndrop.DragView;

/**
 * Interface defining an object that can receive a drag.
 *
 */
public interface DropTarget {

    class DragObject {
        public int x = -1;
        public int y = -1;

        /** X offset from the upper-left corner of the cell to where we touched.  */
        public int xOffset = -1;

        /** Y offset from the upper-left corner of the cell to where we touched.  */
        public int yOffset = -1;

        /** This indicates whether a drag is in final stages, either drop or cancel. It
         * differentiates onDragExit, since this is called when the drag is ending, above
         * the current drag target, or when the drag moves off the current drag object.
         */
        public boolean dragComplete = false;

        /** The view that moves around while you drag.  */
        public DragView dragView = null;

        /** The data associated with the object, after item is dropped. */
        public ItemInfo dragInfo = null;

        /** The data associated with the object  being dragged */
        public ItemInfo originalDragInfo = null;

        /** Where the drag originated */
        public DragSource dragSource = null;

        /** The object is part of an accessible drag operation */
        public boolean accessibleDrag;

        /** Post drag animation runnable */
        public Runnable postAnimationRunnable = null;

        /** Indicates that the drag operation was cancelled */
        public boolean cancelled = false;

        /** Defers removing the DragView from the DragLayer until after the drop animation. */
        public boolean deferDragViewCleanupPostAnimation = true;

        public DragViewStateAnnouncer stateAnnouncer;

        public DragObject() {
        }

        /**
         * This is used to compute the visual center of the dragView. This point is then
         * used to visualize drop locations and determine where to drop an item. The idea is that
         * the visual center represents the user's interpretation of where the item is, and hence
         * is the appropriate point to use when determining drop location.
         */
        public final float[] getVisualCenter(float[] recycle) {
            final float res[] = (recycle == null) ? new float[2] : recycle;

            // These represent the visual top and left of drag view if a dragRect was provided.
            // If a dragRect was not provided, then they correspond to the actual view left and
            // top, as the dragRect is in that case taken to be the entire dragView.
            // R.dimen.dragViewOffsetY.
            int left = x - xOffset;
            int top = y - yOffset;

            // In order to find the visual center, we shift by half the dragRect
            res[0] = left + dragView.getDragRegion().width() / 2;
            res[1] = top + dragView.getDragRegion().height() / 2;

            return res;
        }
    }

    /**
     * Used to temporarily disable certain drop targets
     *
     * @return boolean specifying whether this drop target is currently enabled
     */
    boolean isDropEnabled();

    /**
     * Handle an object being dropped on the DropTarget
     */
    void onDrop(DragObject dragObject);

    void onDragEnter(DragObject dragObject);

    void onDragOver(DragObject dragObject);

    void onDragExit(DragObject dragObject);

    /**
     * Check if a drop action can occur at, or near, the requested location.
     * This will be called just before onDrop.
     * @return True if the drop will be accepted, false otherwise.
     */
    boolean acceptDrop(DragObject dragObject);

    void prepareAccessibilityDrop();

    // These methods are implemented in Views
    void getHitRectRelativeToDragLayer(Rect outRect);
}
