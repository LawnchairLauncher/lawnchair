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
package com.android.launcher3.celllayout.testgenerator

import android.graphics.Rect
import com.android.launcher3.celllayout.board.CellLayoutBoard
import java.util.Random

/** Generates a random CellLayoutBoard. */
open class RandomBoardGenerator(generator: Random) : DeterministicRandomGenerator(generator) {

    companion object {
        // This is the max number of widgets because we encode the widgets as letters A-Z and we
        // already have some of those letter used by other things so 22 is a safe number
        val MAX_NUMBER_OF_WIDGETS = 22
    }

    /**
     * @param remainingEmptySpaces the maximum number of spaces we will fill with icons and widgets
     *   meaning that if the number is 100 we will try to fill the board with at most 100 spaces
     *   usually less than 100.
     * @return a randomly generated board filled with icons and widgets.
     */
    open fun generateBoard(width: Int, height: Int, remainingEmptySpaces: Int): CellLayoutBoard {
        val cellLayoutBoard = CellLayoutBoard(width, height)
        return fillBoard(cellLayoutBoard, Rect(0, 0, width, height), remainingEmptySpaces)
    }

    protected fun fillBoard(
        board: CellLayoutBoard,
        area: Rect,
        remainingEmptySpacesArg: Int
    ): CellLayoutBoard {
        var remainingEmptySpaces = remainingEmptySpacesArg
        if (area.height() * area.width() <= 0) return board
        val width = getRandom(1, area.width())
        val height = getRandom(1, area.height())
        val x = area.left + getRandom(0, area.width() - width)
        val y = area.top + getRandom(0, area.height() - height)
        if (remainingEmptySpaces > 0) {
            remainingEmptySpaces -= width * height
        }

        if (board.widgets.size <= MAX_NUMBER_OF_WIDGETS && width * height > 1) {
            board.addWidget(x, y, width, height)
        } else {
            board.addIcon(x, y)
        }

        if (remainingEmptySpaces < 0) {
            // optimization, no need to keep going
            return board
        }
        fillBoard(board, Rect(area.left, area.top, area.right, y), remainingEmptySpaces)
        fillBoard(board, Rect(area.left, y, x, area.bottom), remainingEmptySpaces)
        fillBoard(board, Rect(x, y + height, area.right, area.bottom), remainingEmptySpaces)
        fillBoard(board, Rect(x + width, y, area.right, y + height), remainingEmptySpaces)
        return board
    }
}
