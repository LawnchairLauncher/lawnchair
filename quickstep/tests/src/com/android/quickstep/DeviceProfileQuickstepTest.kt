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
package com.android.quickstep

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.DeviceProfileBaseTest
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.util.WindowBounds
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when` as whenever

/**
 * Test for [DeviceProfile] quickstep.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceProfileQuickstepTest : DeviceProfileBaseTest() {

    @Test
    fun getCellLayoutWidthAndHeight_twoPanelLandscapeScalable4By4GridTablet() {
        val tabletWidth = 2560
        val tabletHeight = 1600
        val availableWidth = 2560
        val availableHeight = 1500
        windowBounds = WindowBounds(tabletWidth, tabletHeight, availableWidth, availableHeight, 0)
        useTwoPanels = true
        whenever(info.isTablet(ArgumentMatchers.any())).thenReturn(true)
        whenever(info.densityDpi).thenReturn(320)
        whenever(info.smallestSizeDp(ArgumentMatchers.any())).thenReturn(800f)
        inv = newScalableInvariantDeviceProfile()

        val dp = newDP()

        assertThat(dp.cellLayoutWidth).isEqualTo(1235)
        assertThat(dp.cellLayoutHeight).isEqualTo(1235)
    }

    @Test
    fun getCellSize_twoPanelLandscapeScalable4By4GridTablet() {
        val tabletWidth = 2560
        val tabletHeight = 1600
        val availableWidth = 2560
        val availableHeight = 1500
        windowBounds = WindowBounds(tabletWidth, tabletHeight, availableWidth, availableHeight, 0)
        useTwoPanels = true
        whenever(info.isTablet(ArgumentMatchers.any())).thenReturn(true)
        whenever(info.densityDpi).thenReturn(320)
        whenever(info.smallestSizeDp(ArgumentMatchers.any())).thenReturn(800f)
        inv = newScalableInvariantDeviceProfile()

        val dp = newDP()

        assertThat(dp.getCellSize().y).isEqualTo(264)
        assertThat(dp.getCellSize().x).isEqualTo(258)
    }

    @Test
    fun getPanelCount_twoPanelLandscapeScalable4By4GridTablet() {
        val tabletWidth = 2560
        val tabletHeight = 1600
        val availableWidth = 2560
        val availableHeight = 1500
        windowBounds = WindowBounds(tabletWidth, tabletHeight, availableWidth, availableHeight, 0)
        useTwoPanels = true
        whenever(info.isTablet(ArgumentMatchers.any())).thenReturn(true)
        whenever(info.densityDpi).thenReturn(320)
        whenever(info.smallestSizeDp(ArgumentMatchers.any())).thenReturn(800f)
        inv = newScalableInvariantDeviceProfile()

        val dp = newDP()

        assertThat(dp.panelCount).isEqualTo(2)
    }

    @Test
    fun getWorkspaceSpringLoadShrunkTopBottom_landscapePhoneVerticalBar() {
        inv = newScalableInvariantDeviceProfile()
        initializeVarsForPhone(true)
        inv = newScalableInvariantDeviceProfile().apply {
            deviceType = InvariantDeviceProfile.TYPE_PHONE
            transposeLayoutWithOrientation = true
        }

        val dp = newDP()

        assertThat(dp.isVerticalBarLayout).isEqualTo(true)
        assertThat(dp.cellLayoutSpringLoadShrunkTop).isEqualTo(168)
        assertThat(dp.cellLayoutSpringLoadShrunkBottom).isEqualTo(1358)
    }

    @Test
    fun getWorkspaceSpringLoadShrunkTopBottom_portraitPhone() {
        inv = newScalableInvariantDeviceProfile()
        initializeVarsForPhone()
        inv = newScalableInvariantDeviceProfile().apply {
            deviceType = InvariantDeviceProfile.TYPE_PHONE
        }

        val dp = newDP()

        assertThat(dp.isVerticalBarLayout).isEqualTo(false)
        assertThat(dp.cellLayoutSpringLoadShrunkTop).isEqualTo(364)
        assertThat(dp.cellLayoutSpringLoadShrunkBottom).isEqualTo(2199)
    }
}