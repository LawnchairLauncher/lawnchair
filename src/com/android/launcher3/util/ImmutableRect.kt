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

package com.android.launcher3.util

import android.graphics.Rect

/** An immutable data class of [Rect]. */
data class ImmutableRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    /** The width of the rectangle. */
    val width: Int
        get() = right - left

    /** The height of the rectangle. */
    val height: Int
        get() = bottom - top

    /** The X coordinate of the center of the rectangle. */
    val centerX: Int
        get() = left + (width / 2)

    /** The Y coordinate of the center of the rectangle. */
    val centerY: Int
        get() = top + (height / 2)

    /** Checks if the rectangle is empty (width or height is zero or negative). */
    fun isEmpty(): Boolean {
        return left >= right || top >= bottom
    }

    /**
     * Creates a new [ImmutableRect] with a specified offset.
     *
     * @param dx The horizontal offset to apply.
     * @param dy The vertical offset to apply.
     * @return A new [ImmutableRect] instance.
     */
    fun offsetBy(dx: Int, dy: Int): ImmutableRect {
        return ImmutableRect(left + dx, top + dy, right + dx, bottom + dy)
    }

    /**
     * Creates a new [ImmutableRect] with insets applied to all four sides.
     *
     * @param dx The horizontal inset to apply.
     * @param dy The vertical inset to apply.
     * @return A new [ImmutableRect] instance.
     */
    fun insetBy(dx: Int, dy: Int): ImmutableRect {
        return ImmutableRect(left + dx, top + dy, right - dx, bottom - dy)
    }

    /**
     * Checks if this rectangle contains a given point.
     *
     * @param x The X coordinate of the point.
     * @param y The Y coordinate of the point.
     * @return True if the point is within the rectangle, false otherwise.
     */
    fun contains(x: Float, y: Float): Boolean {
        return x >= left && x < right && y >= top && y < bottom
    }

    companion object {
        /**
         * Creates an [ImmutableRect] from a mutable [Rect] instance.
         *
         * @param rect The mutable Rect to copy from.
         * @return A new [ImmutableRect] instance.
         */
        fun from(rect: Rect): ImmutableRect {
            return ImmutableRect(rect.left, rect.top, rect.right, rect.bottom)
        }

        val EMPTY_RECT = ImmutableRect(0, 0, 0, 0)
    }
}
