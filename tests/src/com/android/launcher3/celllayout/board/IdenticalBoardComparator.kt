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
package com.android.launcher3.celllayout.board

import android.graphics.Point
import android.graphics.Rect

/**
 * Compares two [CellLayoutBoard] and returns 0 if they are identical, meaning they have the same
 * widget and icons in the same place, they can be different letters tough.
 */
class IdenticalBoardComparator : Comparator<CellLayoutBoard> {

    /** Converts a list of WidgetRect into a map of the count of different widget.bounds */
    private fun widgetsToBoundsMap(widgets: List<WidgetRect>) =
        widgets.groupingBy { it.mBounds }.eachCount()

    /** Converts a list of IconPoint into a map of the count of different icon.coord */
    private fun iconsToPosCountMap(widgets: List<IconPoint>) =
        widgets.groupingBy { it.getCoord() }.eachCount()

    override fun compare(
        cellLayoutBoard: CellLayoutBoard,
        otherCellLayoutBoard: CellLayoutBoard
    ): Int {
        // to be equal they need to have the same number of widgets and the same dimensions
        // their order can be different
        val widgetsMap: Map<Rect, Int> =
            widgetsToBoundsMap(cellLayoutBoard.widgets.filter { !it.shouldIgnore() })
        val ignoredRectangles: Map<Rect, Int> =
            widgetsToBoundsMap(cellLayoutBoard.widgets.filter { it.shouldIgnore() })

        val otherWidgetMap: Map<Rect, Int> =
            widgetsToBoundsMap(
                otherCellLayoutBoard.widgets
                    .filter { !it.shouldIgnore() }
                    .filter { !overlapsWithIgnored(ignoredRectangles, it.mBounds) }
            )

        if (widgetsMap != otherWidgetMap) {
            return -1
        }

        // to be equal they need to have the same number of icons their order can be different
        return if (
            iconsToPosCountMap(cellLayoutBoard.icons) ==
                iconsToPosCountMap(otherCellLayoutBoard.icons)
        ) {
            0
        } else {
            1
        }
    }

    private fun overlapsWithIgnored(ignoredRectangles: Map<Rect, Int>, rect: Rect): Boolean {
        for (ignoredRect in ignoredRectangles.keys) {
            // Using the built in intersects doesn't work because it doesn't account for area 0
            if (touches(ignoredRect, rect)) {
                return true
            }
        }
        return false
    }

    companion object {
        /**
         * Similar function to {@link Rect#intersects} but this one returns true if the rectangles
         * are intersecting or touching whereas {@link Rect#intersects} doesn't return true when
         * they are touching.
         */
        fun touches(r1: Rect, r2: Rect): Boolean {
            // If one rectangle is on left side of other
            return if (r1.left > r2.right || r2.left > r1.right) {
                false
            } else r1.bottom <= r2.top && r2.bottom <= r1.top

            // If one rectangle is above other
        }

        /**
         * Similar function to {@link Rect#contains} but this one returns true if {link @Point} is
         * intersecting or touching the {@link Rect}. Similar to {@link touches}.
         */
        fun touchesPoint(r1: Rect, p: Point): Boolean {
            return r1.left <= p.x && p.x <= r1.right && r1.bottom <= p.y && p.y <= r1.top
        }
    }
}
