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
package com.android.launcher3.util

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class CellContentDimensionsTest {
    private var context: Context? = null
    private val runningContext: Context = ApplicationProvider.getApplicationContext()
    private lateinit var iconSizeSteps: IconSizeSteps

    @Before
    fun setup() {
        // 160dp makes 1px = 1dp
        val config =
            Configuration(runningContext.resources.configuration).apply {
                this.densityDpi = DisplayMetrics.DENSITY_DEFAULT
                fontScale = 1.0f
            }
        context = runningContext.createConfigurationContext(config)
        iconSizeSteps = IconSizeSteps(context!!.resources)
    }

    @Test
    fun dimensionsFitTheCell() {
        val cellSize = Pair(80, 104)
        val cellContentDimensions =
            CellContentDimensions(iconSizePx = 66, iconDrawablePaddingPx = 8, iconTextSizePx = 14)

        val contentHeight =
            cellContentDimensions.resizeToFitCellHeight(cellSize.second, iconSizeSteps)

        assertThat(contentHeight).isEqualTo(93)
        cellContentDimensions.run {
            assertThat(iconSizePx).isEqualTo(66)
            assertThat(iconDrawablePaddingPx).isEqualTo(8)
            assertThat(iconTextSizePx).isEqualTo(14)
        }
    }

    @Test
    fun decreasePadding() {
        val cellSize = Pair(67, 87)
        val cellContentDimensions =
            CellContentDimensions(iconSizePx = 66, iconDrawablePaddingPx = 8, iconTextSizePx = 14)

        val contentHeight =
            cellContentDimensions.resizeToFitCellHeight(cellSize.second, iconSizeSteps)

        assertThat(contentHeight).isEqualTo(87)
        cellContentDimensions.run {
            assertThat(iconSizePx).isEqualTo(66)
            assertThat(iconDrawablePaddingPx).isEqualTo(2)
            assertThat(iconTextSizePx).isEqualTo(14)
        }
    }

    @Test
    fun decreaseIcon() {
        val cellSize = Pair(65, 84)
        val cellContentDimensions =
            CellContentDimensions(iconSizePx = 66, iconDrawablePaddingPx = 8, iconTextSizePx = 14)

        val contentHeight =
            cellContentDimensions.resizeToFitCellHeight(cellSize.second, iconSizeSteps)

        assertThat(contentHeight).isEqualTo(82)
        cellContentDimensions.run {
            assertThat(iconSizePx).isEqualTo(63)
            assertThat(iconDrawablePaddingPx).isEqualTo(0)
            assertThat(iconTextSizePx).isEqualTo(14)
        }
    }

    @Test
    fun decreaseText() {
        val cellSize = Pair(63, 81)
        val cellContentDimensions =
            CellContentDimensions(iconSizePx = 66, iconDrawablePaddingPx = 8, iconTextSizePx = 14)

        val contentHeight =
            cellContentDimensions.resizeToFitCellHeight(cellSize.second, iconSizeSteps)

        assertThat(contentHeight).isEqualTo(81)
        cellContentDimensions.run {
            assertThat(iconSizePx).isEqualTo(63)
            assertThat(iconDrawablePaddingPx).isEqualTo(0)
            assertThat(iconTextSizePx).isEqualTo(13)
        }
    }

    @Test
    fun decreaseIconAndTextTwoSteps() {
        val cellSize = Pair(60, 78)
        val cellContentDimensions =
            CellContentDimensions(iconSizePx = 66, iconDrawablePaddingPx = 8, iconTextSizePx = 14)

        val contentHeight =
            cellContentDimensions.resizeToFitCellHeight(cellSize.second, iconSizeSteps)

        assertThat(contentHeight).isEqualTo(77)
        cellContentDimensions.run {
            assertThat(iconSizePx).isEqualTo(61)
            assertThat(iconDrawablePaddingPx).isEqualTo(0)
            assertThat(iconTextSizePx).isEqualTo(12)
        }
    }

    @Test
    fun decreaseIconAndTextToMinimum() {
        val cellSize = Pair(52, 63)
        val cellContentDimensions =
            CellContentDimensions(iconSizePx = 66, iconDrawablePaddingPx = 8, iconTextSizePx = 14)

        val contentHeight =
            cellContentDimensions.resizeToFitCellHeight(cellSize.second, iconSizeSteps)

        assertThat(contentHeight).isEqualTo(63)
        cellContentDimensions.run {
            assertThat(iconSizePx).isEqualTo(52)
            assertThat(iconDrawablePaddingPx).isEqualTo(0)
            assertThat(iconTextSizePx).isEqualTo(8)
        }
    }
}
