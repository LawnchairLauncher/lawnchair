/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.content.Context
import android.graphics.PointF
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.DisplayController.Info
import com.android.launcher3.util.WindowBounds
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*

@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceProfileTest {

    private var context: Context? = null
    private var inv: InvariantDeviceProfile? = null
    private var info: Info = mock(Info::class.java)
    private var windowBounds: WindowBounds? = null
    private var isMultiWindowMode: Boolean = false
    private var transposeLayoutWithOrientation: Boolean = false
    private var useTwoPanels: Boolean = false

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // make sure to reset values
        useTwoPanels = false
    }

    @Test
    fun qsbWidth_is_match_parent_for_phones() {
        initializeVarsForPhone()

        val dp = DeviceProfile(
            context,
            inv,
            info,
            windowBounds,
            isMultiWindowMode,
            transposeLayoutWithOrientation,
            useTwoPanels
        )

        assertThat(dp.isQsbInline).isFalse()
        assertThat(dp.qsbWidth).isEqualTo(0)
    }

    @Test
    fun qsbWidth_is_match_parent_for_tablet_portrait() {
        initializeVarsForTablet()

        val dp = DeviceProfile(
            context,
            inv,
            info,
            windowBounds,
            isMultiWindowMode,
            transposeLayoutWithOrientation,
            useTwoPanels
        )

        assertThat(dp.isQsbInline).isFalse()
        assertThat(dp.qsbWidth).isEqualTo(0)
    }

    @Test
    fun qsbWidth_has_size_for_tablet_landscape() {
        initializeVarsForTablet(true)

        val dp = DeviceProfile(
            context,
            inv,
            info,
            windowBounds,
            isMultiWindowMode,
            transposeLayoutWithOrientation,
            useTwoPanels
        )

        if (dp.hotseatQsbHeight > 0) {
            assertThat(dp.isQsbInline).isTrue()
            assertThat(dp.qsbWidth).isGreaterThan(0)
        } else {
            assertThat(dp.isQsbInline).isFalse()
            assertThat(dp.qsbWidth).isEqualTo(0)
        }
    }

    /**
     * This test is to make sure that two panels don't inline the QSB as tablets do
     */
    @Test
    fun qsbWidth_is_match_parent_for_two_panel_landscape() {
        initializeVarsForTablet(true)
        useTwoPanels = true

        val dp = DeviceProfile(
            context,
            inv,
            info,
            windowBounds,
            isMultiWindowMode,
            transposeLayoutWithOrientation,
            useTwoPanels
        )

        assertThat(dp.isQsbInline).isFalse()
        assertThat(dp.qsbWidth).isEqualTo(0)
    }

    private fun initializeVarsForPhone(isLandscape: Boolean = false) {
        val (x, y) = if (isLandscape)
            Pair(3120, 1440)
        else
            Pair(1440, 3120)

        windowBounds = WindowBounds(x, y, x, y - 100)

        `when`(info.isTablet(any())).thenReturn(false)

        scalableInvariantDeviceProfile()
    }

    private fun initializeVarsForTablet(isLandscape: Boolean = false) {
        val (x, y) = if (isLandscape)
            Pair(2560, 1600)
        else
            Pair(1600, 2560)

        windowBounds = WindowBounds(x, y, x, y - 100)

        `when`(info.isTablet(any())).thenReturn(true)

        scalableInvariantDeviceProfile()
    }

    /**
     * A very generic grid, just to make qsb tests work. For real calculations, make sure to use
     * values that better represent a real grid.
     */
    private fun scalableInvariantDeviceProfile() {
        inv = InvariantDeviceProfile().apply {
            isScalable = true
            numColumns = 5
            numRows = 5
            horizontalMargin = FloatArray(4) { 22f }
            borderSpaces = listOf(
                PointF(16f, 16f),
                PointF(16f, 16f),
                PointF(16f, 16f),
                PointF(16f, 16f)
            ).toTypedArray()
            allAppsBorderSpaces = listOf(
                PointF(16f, 16f),
                PointF(16f, 16f),
                PointF(16f, 16f),
                PointF(16f, 16f)
            ).toTypedArray()
            hotseatBorderSpaces = FloatArray(4) { 16f }
            iconSize = FloatArray(4) { 56f }
            allAppsIconSize = FloatArray(4) { 56f }
            iconTextSize = FloatArray(4) { 14f }
            allAppsIconTextSize = FloatArray(4) { 14f }
            minCellSize = listOf(
                PointF(64f, 83f),
                PointF(64f, 83f),
                PointF(64f, 83f),
                PointF(64f, 83f)
            ).toTypedArray()
        }
    }
}