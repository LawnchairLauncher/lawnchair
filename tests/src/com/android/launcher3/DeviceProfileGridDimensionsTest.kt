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

import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.WindowBounds
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when` as whenever

/**
 * Test for [DeviceProfile] grid dimensions.
 *
 * This includes workspace, cell layout, shortcut and widget container, cell sizes, etc.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceProfileGridDimensionsTest : DeviceProfileBaseTest() {

    @Test
    fun getWorkspaceWidth_twoPanelLandscapeScalable4By4GridTablet_workspaceWidthIsFullPage() {
        val tabletWidth = 2560
        val tabletHeight = 1600
        val availableWidth = 2560
        val availableHeight = 1500
        windowBounds = WindowBounds(tabletWidth, tabletHeight, availableWidth, availableHeight, 0)
        useTwoPanels = true
        whenever(info.isTablet(ArgumentMatchers.any())).thenReturn(true)
        whenever(info.getDensityDpi()).thenReturn(320)
        inv = getScalable4By4InvariantDeviceProfile()

        val dp = newDP()

        val expectedWorkspaceWidth = availableWidth
        assertThat(dp.workspaceWidth).isEqualTo(expectedWorkspaceWidth)
    }

    @Test
    fun getWorkspaceHeight_twoPanelLandscapeScalable4By4GridTablet_workspaceHeightIsFullPage() {
        val tabletWidth = 2560
        val tabletHeight = 1600
        val availableWidth = 2560
        val availableHeight = 1500
        windowBounds = WindowBounds(tabletWidth, tabletHeight, availableWidth, availableHeight, 0)
        useTwoPanels = true
        whenever(info.isTablet(ArgumentMatchers.any())).thenReturn(true)
        whenever(info.getDensityDpi()).thenReturn(320)
        inv = getScalable4By4InvariantDeviceProfile()

        val dp = newDP()

        val expectedWorkspaceHeight = availableHeight
        assertThat(dp.workspaceHeight).isEqualTo(expectedWorkspaceHeight)
    }

    @Test
    fun getCellLayoutWidth_twoPanelLandscapeScalable4By4GridTablet_equalsSinglePanelWidth() {
        val tabletWidth = 2560
        val tabletHeight = 1600
        val availableWidth = 2560
        val availableHeight = 1500
        windowBounds = WindowBounds(tabletWidth, tabletHeight, availableWidth, availableHeight, 0)
        useTwoPanels = true
        whenever(info.isTablet(ArgumentMatchers.any())).thenReturn(true)
        whenever(info.getDensityDpi()).thenReturn(320)
        inv = getScalable4By4InvariantDeviceProfile()

        val dp = newDP()

        val expectedWorkspaceWidth = availableWidth
        val expectedCellLayoutWidth =
                (expectedWorkspaceWidth - (dp.workspacePadding.right + dp.workspacePadding.left)) /
                        dp.panelCount
        assertThat(dp.cellLayoutWidth).isEqualTo(expectedCellLayoutWidth)
    }

    @Test
    fun getCellLayoutHeight_twoPanelLandscapeScalable4By4GridTablet_equalsSinglePanelHeight() {
        val tabletWidth = 2560
        val tabletHeight = 1600
        val availableWidth = 2560
        val availableHeight = 1500
        windowBounds = WindowBounds(tabletWidth, tabletHeight, availableWidth, availableHeight, 0)
        useTwoPanels = true
        whenever(info.isTablet(ArgumentMatchers.any())).thenReturn(true)
        whenever(info.getDensityDpi()).thenReturn(320)
        inv = getScalable4By4InvariantDeviceProfile()

        val dp = newDP()

        val expectedWorkspaceHeight = availableHeight
        val expectedCellLayoutHeight =
                expectedWorkspaceHeight - (dp.workspacePadding.top + dp.workspacePadding.bottom)
        assertThat(dp.cellLayoutHeight).isEqualTo(expectedCellLayoutHeight)
    }

    @Test
    fun getShortcutAndWidgetContainerWidth_twoPanelLandscapeScalable4By4GridTablet_equalsIconsPlusBorderSpacesWidth() {
        val tabletWidth = 2560
        val tabletHeight = 1600
        val availableWidth = 2560
        val availableHeight = 1500
        windowBounds = WindowBounds(tabletWidth, tabletHeight, availableWidth, availableHeight, 0)
        useTwoPanels = true
        whenever(info.isTablet(ArgumentMatchers.any())).thenReturn(true)
        whenever(info.getDensityDpi()).thenReturn(320)
        inv = getScalable4By4InvariantDeviceProfile()

        val dp = newDP()

        val expectedWorkspaceWidth = availableWidth
        val expectedCellLayoutWidth =
                (expectedWorkspaceWidth - (dp.workspacePadding.right + dp.workspacePadding.left)) /
                        dp.panelCount
        val expectedShortcutAndWidgetContainerWidth = expectedCellLayoutWidth -
                (dp.cellLayoutPaddingPx.left + dp.cellLayoutPaddingPx.right)
        assertThat(dp.shortcutAndWidgetContainerWidth).isEqualTo(expectedShortcutAndWidgetContainerWidth)
    }

    @Test
    fun getShortcutAndWidgetContainerHeight_twoPanelLandscapeScalable4By4GridTablet_equalsIconsPlusBorderSpacesHeight() {
        val tabletWidth = 2560
        val tabletHeight = 1600
        val availableWidth = 2560
        val availableHeight = 1500
        windowBounds = WindowBounds(tabletWidth, tabletHeight, availableWidth, availableHeight, 0)
        useTwoPanels = true
        whenever(info.isTablet(ArgumentMatchers.any())).thenReturn(true)
        whenever(info.getDensityDpi()).thenReturn(320)
        inv = getScalable4By4InvariantDeviceProfile()

        val dp = newDP()

        val expectedWorkspaceHeight = availableHeight
        val expectedCellLayoutHeight =
                expectedWorkspaceHeight - (dp.workspacePadding.top + dp.workspacePadding.bottom)
        val expectedShortcutAndWidgetContainerHeight = expectedCellLayoutHeight -
                (dp.cellLayoutPaddingPx.top + dp.cellLayoutPaddingPx.bottom)
        assertThat(dp.shortcutAndWidgetContainerHeight).isEqualTo(
                expectedShortcutAndWidgetContainerHeight)
    }

    @Test
    fun getCellSize_twoPanelLandscapeScalable4By4GridTablet_equalsSinglePanelWidth() {
        val tabletWidth = 2560
        val tabletHeight = 1600
        val availableWidth = 2560
        val availableHeight = 1500
        windowBounds = WindowBounds(tabletWidth, tabletHeight, availableWidth, availableHeight, 0)
        useTwoPanels = true
        whenever(info.isTablet(ArgumentMatchers.any())).thenReturn(true)
        whenever(info.getDensityDpi()).thenReturn(320)
        inv = getScalable4By4InvariantDeviceProfile()

        val dp = newDP()

        val expectedWorkspaceWidth = availableWidth
        val expectedCellLayoutWidth =
                (expectedWorkspaceWidth - (dp.workspacePadding.right + dp.workspacePadding.left)) /
                        dp.panelCount
        val expectedShortcutAndWidgetContainerWidth =
                expectedCellLayoutWidth -
                        (dp.cellLayoutPaddingPx.left + dp.cellLayoutPaddingPx.right)
        assertThat(dp.getCellSize().x).isEqualTo(
                (expectedShortcutAndWidgetContainerWidth -
                        ((inv!!.numColumns - 1) * dp.cellLayoutBorderSpacePx.x)) / inv!!.numColumns)
        val expectedWorkspaceHeight = availableHeight
        val expectedCellLayoutHeight =
                expectedWorkspaceHeight - (dp.workspacePadding.top + dp.workspacePadding.bottom)
        val expectedShortcutAndWidgetContainerHeight = expectedCellLayoutHeight -
                (dp.cellLayoutPaddingPx.top + dp.cellLayoutPaddingPx.bottom)
        assertThat(dp.getCellSize().y).isEqualTo(
                (expectedShortcutAndWidgetContainerHeight -
                        ((inv!!.numRows - 1) * dp.cellLayoutBorderSpacePx.y)) / inv!!.numRows)
    }

    @Test
    fun getPanelCount_twoPanelLandscapeScalable4By4GridTablet_equalsTwoPanels() {
        val tabletWidth = 2560
        val tabletHeight = 1600
        val availableWidth = 2560
        val availableHeight = 1500
        windowBounds = WindowBounds(tabletWidth, tabletHeight, availableWidth, availableHeight, 0)
        useTwoPanels = true
        whenever(info.isTablet(ArgumentMatchers.any())).thenReturn(true)
        whenever(info.getDensityDpi()).thenReturn(320)
        inv = getScalable4By4InvariantDeviceProfile()

        val dp = newDP()

        assertThat(dp.panelCount).isEqualTo(2)
    }

    fun getScalable4By4InvariantDeviceProfile(): InvariantDeviceProfile {
        return InvariantDeviceProfile().apply {
            isScalable = true
            numColumns = 4
            numRows = 4
            numShownHotseatIcons = 4
            numDatabaseHotseatIcons = 6
            numShrunkenHotseatIcons = 5
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
            hotseatColumnSpan = IntArray(4) { 4 }
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
            allAppsCellSize = listOf(
                    PointF(64f, 83f),
                    PointF(64f, 83f),
                    PointF(64f, 83f),
                    PointF(64f, 83f)
            ).toTypedArray()
            inlineQsb = booleanArrayOf(
                    false,
                    false,
                    false,
                    false
            )
        }
    }
}