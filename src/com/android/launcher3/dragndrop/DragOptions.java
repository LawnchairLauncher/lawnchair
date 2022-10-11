/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.dragndrop;

import android.graphics.Point;

import com.android.launcher3.DropTarget;

/**
 * Set of options to control the drag and drop behavior.
 */
public class DragOptions {

    /** Whether or not an accessible drag operation is in progress. */
    public boolean isAccessibleDrag = false;

    /** Whether or not the drag operation is controlled by keyboard. */
    public boolean isKeyboardDrag = false;

    /**
     * Specifies the start location for a simulated DnD (like system drag or accessibility drag),
     * null when using internal DnD
     */
    public Point simulatedDndStartPoint = null;

    /** Determines when a pre-drag should transition to a drag. By default, this is immediate. */
    public PreDragCondition preDragCondition = null;

    /**
     * A drag scale that scales the original drag view size when the preDragCondition is met (or
     * is ignored if preDragEndScale is 0).
     */
    public float preDragEndScale;

    /** Scale of the icons over the workspace icon size. */
    public float intrinsicIconScaleFactor = 1f;

    public boolean isFlingToDelete;

    /**
     * Specifies a condition that must be met before DragListener#onDragStart() is called.
     * By default, there is no condition and onDragStart() is called immediately following
     * DragController#startDrag().
     *
     * This condition can be overridden, and callbacks are provided for the following cases:
     * - The pre-drag starts, but onDragStart() is deferred (onPreDragStart()).
     * - The pre-drag ends before the condition is met (onPreDragEnd(false)).
     * - The actual drag starts when the condition is met (onPreDragEnd(true)).
     */
    public interface PreDragCondition {

        public boolean shouldStartDrag(double distanceDragged);

        /**
         * The pre-drag has started, but onDragStart() is
         * deferred until shouldStartDrag() returns true.
         */
        void onPreDragStart(DropTarget.DragObject dragObject);

        /**
         * The pre-drag has ended. This gets called at the same time as onDragStart()
         * if the condition is met, otherwise at the same time as onDragEnd().
         * @param dragStarted Whether the pre-drag ended because the actual drag started.
         *                    This will be true if the condition was met, otherwise false.
         */
        void onPreDragEnd(DropTarget.DragObject dragObject, boolean dragStarted);
    }
}
