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

/** Represents a widget in a CellLayoutBoard */
data class WidgetRect(
    val type: Char,
    val bounds: Rect,
) {
    val spanX: Int = bounds.right - bounds.left + 1
    val spanY: Int = bounds.top - bounds.bottom + 1
    val cellY: Int = bounds.bottom
    val cellX: Int = bounds.left

    fun shouldIgnore() = type == CellType.IGNORE

    fun contains(x: Int, y: Int) = bounds.contains(x, y)
}

/**
 * [A-Z]: Represents a folder and number of icons in the folder is represented by the order of
 * letter in the alphabet, A=2, B=3, C=4 ... etc.
 */
data class FolderPoint(val coord: Point, val type: Char) {
    val numberIconsInside: Int = type.code - 'A'.code + 2
}

/** Represents an icon in a CellLayoutBoard */
data class IconPoint(val coord: Point, val type: Char = CellType.ICON)

object CellType {
    // The cells marked by this will be filled by 1x1 widgets and will be ignored when
    // validating
    const val IGNORE = 'x'

    // The cells marked by this will be filled by app icons
    const val ICON = 'i'

    // The cells marked by FOLDER will be filled by folders with 27 app icons inside
    const val FOLDER = 'Z'

    // Empty space
    const val EMPTY = '-'

    // Widget that will be saved as "main widget" for easier retrieval
    const val MAIN_WIDGET = 'm' // Everything else will be consider a widget
}
