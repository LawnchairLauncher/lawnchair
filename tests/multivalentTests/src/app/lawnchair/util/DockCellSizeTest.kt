/*
 * Copyright 2026, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.util

import com.android.launcher3.folder.ClippedFolderIconLayoutRule.ICON_OVERLAP_FACTOR
import com.google.common.truth.Truth.assertThat
import kotlin.math.ceil
import org.junit.Test

class DockCellSizeTest {

    @Test
    fun calculate_withLabels_includesBaseSizeAndLabelHeight() {
        val iconSize = 120
        val labelHeight = 30
        val minimumTouchSize = 48
        val baseSize = ceil(iconSize * 2 * ICON_OVERLAP_FACTOR).toInt() - iconSize / 2

        val result = DockCellSize.calculate(
            iconSize = iconSize,
            labelHeight = labelHeight,
            showLabels = true,
            minimumTouchSize = minimumTouchSize,
        )

        assertThat(result).isEqualTo(baseSize + labelHeight)
    }

    @Test
    fun calculate_withoutLabels_preservesBaseSizePadding() {
        val iconSize = 120
        val labelHeight = 30
        val minimumTouchSize = 48
        val baseSize = ceil(iconSize * 2 * ICON_OVERLAP_FACTOR).toInt() - iconSize / 2

        val result = DockCellSize.calculate(
            iconSize = iconSize,
            labelHeight = labelHeight,
            showLabels = false,
            minimumTouchSize = minimumTouchSize,
        )

        assertThat(result).isEqualTo(baseSize)
        val cellPaddingY = (result - iconSize) / 2
        assertThat(cellPaddingY).isGreaterThan(0)
    }

    @Test
    fun calculate_withoutLabels_respectsMinimumTouchSize() {
        val iconSize = 10
        val labelHeight = 20
        val minimumTouchSize = 48

        val result = DockCellSize.calculate(
            iconSize = iconSize,
            labelHeight = labelHeight,
            showLabels = false,
            minimumTouchSize = minimumTouchSize,
        )

        assertThat(result).isEqualTo(minimumTouchSize)
    }
}
