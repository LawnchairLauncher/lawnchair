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

package com.android.launcher2;

/**
 * Handles scrolling while dragging
 *
 */
public interface DragScroller {
    void scrollLeft();
    void scrollRight();

    /**
     * The touch point has entered the scroll area; a scroll is imminent.
     * This event will only occur while a drag is active.
     *
     * @param direction The scroll direction
     */
    boolean onEnterScrollArea(int x, int y, int direction);

    /**
     * The touch point has left the scroll area.
     * NOTE: This may not be called, if a drop occurs inside the scroll area.
     */
    boolean onExitScrollArea();
}
