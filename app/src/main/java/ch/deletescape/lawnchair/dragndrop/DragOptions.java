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

package ch.deletescape.lawnchair.dragndrop;

import android.graphics.Point;

import ch.deletescape.lawnchair.DropTarget;

/**
 * Set of options to control the drag and drop behavior.
 */
public class DragOptions {

    /**
     * Whether or not an accessible drag operation is in progress.
     */
    public boolean isAccessibleDrag = false;

    /**
     * Specifies the start location for the system DnD, null when using internal DnD
     */
    public Point systemDndStartPoint = null;

    /**
     * Determines when a deferred drag should start. By default, drags aren't deferred at all.
     */
    public DeferDragCondition deferDragCondition = new DeferDragCondition();
    public PreDragCondition preDragCondition = null;

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

        boolean shouldStartDrag(double distanceDragged);

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

    /**
     * Specifies a condition that must be met before DragListener#onDragStart() is called.
     * By default, there is no condition and onDragStart() is called immediately following
     * DragController#startDrag().
     * <p>
     * This condition can be overridden, and callbacks are provided for the following cases:
     * - The drag starts, but onDragStart() is deferred (onDeferredDragStart()).
     * - The drag ends before the condition is met (onDropBeforeDeferredDrag()).
     * - The condition is met (onDragStart()).
     */
    public static class DeferDragCondition {
        public boolean shouldStartDeferredDrag(double distanceDragged) {
            return true;
        }

        /**
         * The drag has started, but onDragStart() is deferred.
         * This happens when shouldStartDeferredDrag() returns true.
         */
        public void onDeferredDragStart() {
            // Do nothing.
        }

        /**
         * User dropped before the deferred condition was met,
         * i.e. before shouldStartDeferredDrag() returned true.
         */
        public void onDropBeforeDeferredDrag() {
            // Do nothing
        }

        /**
         * onDragStart() has been called, now we are in a normal drag.
         */
        public void onDragStart() {
            // Do nothing
        }
    }
}
