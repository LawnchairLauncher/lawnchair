/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.celllayout

import org.junit.Rule
import org.junit.Test

// @RunWith(AndroidJUnit4::class) b/353965234
class CellLayoutMethodsTest {

    @JvmField @Rule var cellLayoutBuilder = UnitTestCellLayoutBuilderRule()

    //@Test
    fun pointToCellExact() {
        val width = 1000
        val height = 1000
        val columns = 30
        val rows = 30
        val cl = cellLayoutBuilder.createCellLayout(columns, rows, false, width, height)

        val res = intArrayOf(0, 0)
        for (col in 0..<columns) {
            for (row in 0..<rows) {
                val x = (width / columns) * col
                val y = (height / rows) * row
                cl.pointToCellExact(x, y, res)
                cl.pointToCellExact(x, y, res)
                assertValues(col, res, row, columns, rows, width, height, x, y)
            }
        }

        cl.pointToCellExact(-10, -10, res)
        assertValues(0, res, 0, columns, rows, width, height, -10, -10)
        cl.pointToCellExact(width + 10, height + 10, res)
        assertValues(columns - 1, res, rows - 1, columns, rows, width, height, -10, -10)
    }

    private fun assertValues(
        col: Int,
        res: IntArray,
        row: Int,
        columns: Int,
        rows: Int,
        width: Int,
        height: Int,
        x: Int,
        y: Int
    ) {
        assert(col == res[0] && row == res[1]) {
            "Cell Layout with values (c= $columns, r= $rows, w= $width, h= $height) didn't mapped correctly the pixels ($x, $y) to the cells ($col, $row) with result (${res[0]}, ${res[1]})"
        }
    }
}
