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

package com.android.wm.shell.windowdecor;

import android.annotation.IntDef;
import android.graphics.Rect;

/**
 * Callback called when receiving drag-resize or drag-move related input events.
 */
public interface DragPositioningCallback {
    /**
     * Indicates the direction of resizing. May be combined together to indicate a diagonal drag.
     */
    @IntDef(flag = true, value = {
            CTRL_TYPE_UNDEFINED, CTRL_TYPE_LEFT, CTRL_TYPE_RIGHT, CTRL_TYPE_TOP, CTRL_TYPE_BOTTOM
    })
    @interface CtrlType {}

    int CTRL_TYPE_UNDEFINED = 0;
    int CTRL_TYPE_LEFT = 1;
    int CTRL_TYPE_RIGHT = 2;
    int CTRL_TYPE_TOP = 4;
    int CTRL_TYPE_BOTTOM = 8;
    /**
     * Called when a drag-resize or drag-move starts.
     *
     * @param ctrlType {@link CtrlType} indicating the direction of resizing, use
     *                 {@code 0} to indicate it's a move
     * @param x x coordinate in window decoration coordinate system where the drag starts
     * @param y y coordinate in window decoration coordinate system where the drag starts
     * @return the starting task bounds
     */
    Rect onDragPositioningStart(@CtrlType int ctrlType, float x, float y);

    /**
     * Called when the pointer moves during a drag-resize or drag-move.
     * @param x x coordinate in window decoration coordinate system of the new pointer location
     * @param y y coordinate in window decoration coordinate system of the new pointer location
     * @return the updated task bounds
     */
    Rect onDragPositioningMove(float x, float y);

    /**
     * Called when a drag-resize or drag-move stops.
     * @param x x coordinate in window decoration coordinate system where the drag resize stops
     * @param y y coordinate in window decoration coordinate system where the drag resize stops
     * @return the final bounds for the dragged task
     */
    Rect onDragPositioningEnd(float x, float y);
}
