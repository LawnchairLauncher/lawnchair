/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.shared.bubbles

import android.graphics.Rect

/**
 * Represents an invisible area on the screen that determines what happens to a dragged object if it
 * is released in that area.
 *
 * [bounds] are the bounds of the drag zone. Drag zones have an associated drop target that serves
 * as visual feedback hinting what would happen if the object is released. When a dragged object is
 * dragged into a drag zone, the associated drop target will be displayed. Not all drag zones have
 * drop targets; only those that are made visible by Bubbles do.
 */
sealed interface DragZone {

    /** The bounds of this drag zone. */
    val bounds: Rect
    /** The bounds of the drop target associated with this drag zone. */
    val dropTarget: Rect?

    fun contains(x: Int, y: Int) = bounds.contains(x, y)

    /** Represents the bubble drag area on the screen. */
    sealed class Bubble(override val bounds: Rect, override val dropTarget: Rect) : DragZone {
        data class Left(override val bounds: Rect, override val dropTarget: Rect) :
            Bubble(bounds, dropTarget)

        data class Right(override val bounds: Rect, override val dropTarget: Rect) :
            Bubble(bounds, dropTarget)
    }

    /** Represents dragging to Desktop Window. */
    data class DesktopWindow(override val bounds: Rect, override val dropTarget: Rect) : DragZone

    /** Represents dragging to Full Screen. */
    data class FullScreen(override val bounds: Rect, override val dropTarget: Rect) : DragZone

    /** Represents dragging to dismiss. */
    data class Dismiss(override val bounds: Rect) : DragZone {
        override val dropTarget: Rect? = null
    }

    /** Represents dragging to enter Split or replace a Split app. */
    sealed class Split(override val bounds: Rect) : DragZone {
        override val dropTarget: Rect? = null

        data class Left(override val bounds: Rect) : Split(bounds)

        data class Right(override val bounds: Rect) : Split(bounds)

        data class Top(override val bounds: Rect) : Split(bounds)

        data class Bottom(override val bounds: Rect) : Split(bounds)
    }
}
