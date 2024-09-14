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

/**
 * Compares two [CellLayoutBoard] and returns 0 if they contain the same widgets and icons even if
 * they are in different positions i.e. in a different permutation.
 */
class PermutedBoardComparator : Comparator<CellLayoutBoard> {

    /**
     * The key for the set is the span since the widgets could change location but shouldn't change
     * size
     */
    private fun boardToSpanCountMap(widgets: List<WidgetRect>) =
        widgets.groupingBy { Point(it.spanX, it.spanY) }.eachCount()
    override fun compare(
        cellLayoutBoard: CellLayoutBoard,
        otherCellLayoutBoard: CellLayoutBoard
    ): Int {
        return if (
            boardToSpanCountMap(cellLayoutBoard.widgets) !=
                boardToSpanCountMap(otherCellLayoutBoard.widgets)
        ) {
            1
        } else cellLayoutBoard.icons.size.compareTo(otherCellLayoutBoard.icons.size)
    }
}
