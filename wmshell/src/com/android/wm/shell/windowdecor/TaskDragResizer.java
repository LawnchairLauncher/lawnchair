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

package com.android.wm.shell.windowdecor;

/**
 * Holds the state of a drag resize.
 */
public interface TaskDragResizer {

    /**
     * Returns true if task is currently being resized or animating the final transition after
     * a resize is complete.
     */
    boolean isResizingOrAnimating();

    /**
     * Adds a drag start listener to be notified of drag start events.
     *
     * @param dragEventListener Listener to be added.
     */
    void addDragEventListener(DragPositioningCallbackUtility.DragEventListener dragEventListener);

    /**
     * Removes a drag start listener from the listener set.
     *
     * @param dragEventListener Listener to be removed.
     */
    void removeDragEventListener(
            DragPositioningCallbackUtility.DragEventListener dragEventListener);

    /**
     * Releases any resources associated with this TaskDragResizer. This should be called when the
     * associated window is closed.
     */
    void close();
}
