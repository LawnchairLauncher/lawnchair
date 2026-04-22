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

package com.android.launcher3

import com.android.launcher3.model.data.ItemInfo

/**
 * Data class containing which cells should be highlighted and the container in which we are trying
 * to highlight the cell. Depending on the container we may or may not want to show the highlight.
 */
data class CellsToHighlight(val cells: List<CellPosition>, val container: Int)

/** Data class for the position of a cell which we would want to highlight. */
data class CellPosition(val col: Int, val row: Int)

/** Interface for highlighting items on the home screen grid. */
interface GridHighlighter {
    /** Show the highlight when called. */
    fun showHighlight()

    /** Dismiss the highlight when called. */
    fun dismissHighlight()

    /** Factory for creating a GridHighlighter to highlight certain cells in a grid. */
    companion object GridHighlighterFactory {

        /**
         * Creates a highlighter for the grid cell we want to highlight.
         *
         * @param cellsToHighlight is the cells that we should highlight, along with the container
         *   to which they belong.
         * @param shouldAddPadding tells us whether we want to add padding to the highlighted cells.
         * @param cellLayout is used to add/remove the highlights inside of the correct CellLayout
         * @return GridHighlighter that we create when calling this function.
         */
        fun createGridHighlighter(
            cellsToHighlight: CellsToHighlight,
            shouldAddPadding: Boolean,
            cellLayout: CellLayout,
        ): GridHighlighter? {
            return TODO("Provide the return value")
        }

        /**
         * Creates a highlighter for the grid item we want to highlight. This would be used to
         * highlight items that might have a different highlighting logic, such as widgets where we
         * would open their AppWidgetResizeFrame instead of using the general grid highlighting
         * logic.
         *
         * @param itemInfo is used to get certain specifics about the item we are trying to
         *   highlight, such as its spanX, spanY.
         * @return GridHighlighter that we create when calling this function.
         */
        fun createGridHighlighter(itemInfo: ItemInfo): GridHighlighter? {
            return TODO("Provide the return value")
        }
    }
}
