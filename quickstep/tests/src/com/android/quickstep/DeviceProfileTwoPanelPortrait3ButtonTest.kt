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
import com.android.launcher3.DeviceProfile
import com.android.launcher3.DeviceProfileBaseTest
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for DeviceProfile for two panel in portrait with 3-Button navigation.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceProfileTwoPanelPortrait3ButtonTest : DeviceProfileBaseTest() {

    lateinit var dp: DeviceProfile

    @Before
    fun before() {
        initializeVarsForTablet(isTwoPanel = true, isGestureMode = false)
        dp = newDP()
    }

    @Test
    fun isScalableGrid() {
        assertThat(dp.isScalableGrid).isTrue()
    }

    @Test
    fun cellWidthPx() {
        assertThat(dp.cellWidthPx).isEqualTo(153)
    }

    @Test
    fun cellHeightPx() {
        assertThat(dp.cellHeightPx).isEqualTo(199)
    }

    @Test
    fun getCellSizeX() {
        assertThat(dp.cellSize.x).isEqualTo(153)
    }

    @Test
    fun getCellSizeY() {
        assertThat(dp.cellSize.y).isEqualTo(509)
    }

    @Test
    fun cellLayoutBorderSpacePxX() {
        assertThat(dp.cellLayoutBorderSpacePx.x).isEqualTo(38)
    }

    @Test
    fun cellLayoutBorderSpacePxY() {
        assertThat(dp.cellLayoutBorderSpacePx.y).isEqualTo(38)
    }

    @Test
    fun cellLayoutPaddingPxLeft() {
        assertThat(dp.cellLayoutPaddingPx.left).isEqualTo(19)
    }

    @Test
    fun cellLayoutPaddingPxTop() {
        assertThat(dp.cellLayoutPaddingPx.top).isEqualTo(0)
    }

    @Test
    fun cellLayoutPaddingPxRight() {
        assertThat(dp.cellLayoutPaddingPx.right).isEqualTo(19)
    }

    @Test
    fun cellLayoutPaddingPxBottom() {
        assertThat(dp.cellLayoutPaddingPx.bottom).isEqualTo(19)
    }

    @Test
    fun iconSizePx() {
        assertThat(dp.iconSizePx).isEqualTo(112)
    }

    @Test
    fun iconTextSizePx() {
        assertThat(dp.iconTextSizePx).isEqualTo(28)
    }

    @Test
    fun iconDrawablePaddingPx() {
        assertThat(dp.iconDrawablePaddingPx).isEqualTo(14)
    }

    @Test
    fun folderCellWidthPx() {
        assertThat(dp.folderCellWidthPx).isEqualTo(153)
    }

    @Test
    fun folderCellHeightPx() {
        assertThat(dp.folderCellHeightPx).isEqualTo(199)
    }

    @Test
    fun folderChildIconSizePx() {
        assertThat(dp.folderChildIconSizePx).isEqualTo(112)
    }

    @Test
    fun folderChildTextSizePx() {
        assertThat(dp.folderChildTextSizePx).isEqualTo(28)
    }

    @Test
    fun folderChildDrawablePaddingPx() {
        assertThat(dp.folderChildDrawablePaddingPx).isEqualTo(16)
    }

    @Test
    fun folderCellLayoutBorderSpaceOriginalPx() {
        assertThat(dp.folderCellLayoutBorderSpaceOriginalPx).isEqualTo(0)
    }

    @Test
    fun folderCellLayoutBorderSpacePxX() {
        assertThat(dp.folderCellLayoutBorderSpacePx.x).isEqualTo(0)
    }

    @Test
    fun folderCellLayoutBorderSpacePxY() {
        assertThat(dp.folderCellLayoutBorderSpacePx.y).isEqualTo(0)
    }

    @Test
    fun bottomSheetTopPadding() {
        assertThat(dp.bottomSheetTopPadding).isEqualTo(600)
    }

    @Test
    fun allAppsShiftRange() {
        assertThat(dp.allAppsShiftRange).isEqualTo(1960)
    }

    @Test
    fun allAppsTopPadding() {
        assertThat(dp.allAppsTopPadding).isEqualTo(600)
    }

    @Test
    fun allAppsIconSizePx() {
        assertThat(dp.allAppsIconSizePx).isEqualTo(134)
    }

    @Test
    fun allAppsIconTextSizePx() {
        assertThat(dp.allAppsIconTextSizePx).isEqualTo(34)
    }

    @Test
    fun allAppsIconDrawablePaddingPx() {
        assertThat(dp.allAppsIconDrawablePaddingPx).isEqualTo(14)
    }

    @Test
    fun allAppsCellHeightPx() {
        assertThat(dp.allAppsCellHeightPx).isEqualTo(237)
    }

    @Test
    fun allAppsCellWidthPx() {
        assertThat(dp.allAppsCellWidthPx).isEqualTo(153)
    }

    @Test
    fun allAppsBorderSpacePxX() {
        assertThat(dp.allAppsBorderSpacePx.x).isEqualTo(38)
    }

    @Test
    fun allAppsBorderSpacePxY() {
        assertThat(dp.allAppsBorderSpacePx.y).isEqualTo(38)
    }

    @Test
    fun numShownAllAppsColumns() {
        assertThat(dp.numShownAllAppsColumns).isEqualTo(0)
    }

    @Test
    fun allAppsLeftRightPadding() {
        assertThat(dp.allAppsLeftRightPadding).isEqualTo(56)
    }

    @Test
    fun allAppsLeftRightMargin() {
        assertThat(dp.allAppsLeftRightMargin).isEqualTo(763)
    }

    @Test
    fun hotseatBarSizePx() {
        assertThat(dp.hotseatBarSizePx).isEqualTo(386)
    }

    @Test
    fun hotseatCellHeightPx() {
        assertThat(dp.hotseatCellHeightPx).isEqualTo(126)
    }

    @Test
    fun hotseatBarBottomSpacePx() {
        assertThat(dp.hotseatBarBottomSpacePx).isEqualTo(116)
    }

    @Test
    fun hotseatBarSidePaddingStartPx() {
        assertThat(dp.hotseatBarSidePaddingStartPx).isEqualTo(0)
    }

    @Test
    fun hotseatBarSidePaddingEndPx() {
        assertThat(dp.hotseatBarSidePaddingEndPx).isEqualTo(0)
    }

    @Test
    fun hotseatQsbSpace() {
        assertThat(dp.hotseatQsbSpace).isEqualTo(56)
    }

    @Test
    fun hotseatQsbHeight() {
        assertThat(dp.hotseatQsbHeight).isEqualTo(126)
    }

    @Test
    fun springLoadedHotseatBarTopMarginPx() {
        assertThat(dp.springLoadedHotseatBarTopMarginPx).isEqualTo(216)
    }

    @Test
    fun numShownHotseatIcons() {
        assertThat(dp.numShownHotseatIcons).isEqualTo(6)
    }

    @Test
    fun hotseatBorderSpace() {
        assertThat(dp.hotseatBorderSpace).isEqualTo(32)
    }

    @Test
    fun isQsbInline() {
        assertThat(dp.isQsbInline).isEqualTo(false)
    }

    @Test
    fun qsbWidth() {
        assertThat(dp.qsbWidth).isEqualTo(685)
    }

    @Test
    fun isTaskbarPresent() {
        assertThat(dp.isTaskbarPresent).isEqualTo(true)
    }

    @Test
    fun isTaskbarPresentInApps() {
        assertThat(dp.isTaskbarPresentInApps).isEqualTo(false)
    }

    @Test
    fun taskbarSize() {
        assertThat(dp.taskbarSize).isEqualTo(120)
    }

    @Test
    fun desiredWorkspaceHorizontalMarginPx() {
        assertThat(dp.desiredWorkspaceHorizontalMarginPx).isEqualTo(52)
    }

    @Test
    fun workspacePaddingLeft() {
        assertThat(dp.workspacePadding.left).isEqualTo(33)
    }

    @Test
    fun workspacePaddingTop() {
        assertThat(dp.workspacePadding.top).isEqualTo(0)
    }

    @Test
    fun workspacePaddingRight() {
        assertThat(dp.workspacePadding.right).isEqualTo(33)
    }

    @Test
    fun workspacePaddingBottom() {
        assertThat(dp.workspacePadding.bottom).isEqualTo(291)
    }

    @Test
    fun iconScale() {
        assertThat(dp.iconScale).isEqualTo(1)
    }

    @Test
    fun cellScaleToFit() {
        assertThat(dp.cellScaleToFit).isEqualTo(1.1976048f)
    }

    @Test
    fun workspaceTopPadding() {
        assertThat(dp.workspaceTopPadding).isEqualTo(0)
    }

    @Test
    fun workspaceBottomPadding() {
        assertThat(dp.workspaceBottomPadding).isEqualTo(0)
    }

    @Test
    fun overviewTaskMarginPx() {
        assertThat(dp.overviewTaskMarginPx).isEqualTo(32)
    }

    @Test
    fun overviewTaskMarginGridPx() {
        assertThat(dp.overviewTaskMarginGridPx).isEqualTo(32)
    }

    @Test
    fun overviewTaskIconSizePx() {
        assertThat(dp.overviewTaskIconSizePx).isEqualTo(96)
    }

    @Test
    fun overviewTaskIconDrawableSizePx() {
        assertThat(dp.overviewTaskIconDrawableSizePx).isEqualTo(88)
    }

    @Test
    fun overviewTaskIconDrawableSizeGridPx() {
        assertThat(dp.overviewTaskIconDrawableSizeGridPx).isEqualTo(88)
    }

    @Test
    fun overviewTaskThumbnailTopMarginPx() {
        assertThat(dp.overviewTaskThumbnailTopMarginPx).isEqualTo(160)
    }

    @Test
    fun overviewActionsTopMarginPx() {
        assertThat(dp.overviewActionsTopMarginPx).isEqualTo(48)
    }

    @Test
    fun overviewActionsHeight() {
        assertThat(dp.overviewActionsHeight).isEqualTo(96)
    }

    @Test
    fun overviewActionsButtonSpacing() {
        assertThat(dp.overviewActionsButtonSpacing).isEqualTo(72)
    }

    @Test
    fun overviewPageSpacing() {
        assertThat(dp.overviewPageSpacing).isEqualTo(88)
    }

    @Test
    fun overviewRowSpacing() {
        assertThat(dp.overviewRowSpacing).isEqualTo(40)
    }

    @Test
    fun overviewGridSideMargin() {
        assertThat(dp.overviewGridSideMargin).isEqualTo(128)
    }

    @Test
    fun dropTargetBarTopMarginPx() {
        assertThat(dp.dropTargetBarTopMarginPx).isEqualTo(220)
    }

    @Test
    fun dropTargetBarSizePx() {
        assertThat(dp.dropTargetBarSizePx).isEqualTo(144)
    }

    @Test
    fun dropTargetBarBottomMarginPx() {
        assertThat(dp.dropTargetBarBottomMarginPx).isEqualTo(96)
    }

    @Test
    fun workspaceSpringLoadedMinNextPageVisiblePx() {
        assertThat(dp.workspaceSpringLoadedMinNextPageVisiblePx).isEqualTo(48)
    }

    @Test
    fun getWorkspaceSpringLoadScale() {
        assertThat(dp.workspaceSpringLoadScale).isEqualTo(0.69064087f)
    }

    @Test
    fun getCellLayoutHeight() {
        assertThat(dp.cellLayoutHeight).isEqualTo(2169)
    }

    @Test
    fun getCellLayoutWidth() {
        assertThat(dp.cellLayoutWidth).isEqualTo(767)
    }

    @Test
    fun getPanelCount() {
        assertThat(dp.panelCount).isEqualTo(2)
    }

    @Test
    fun isVerticalBarLayout() {
        assertThat(dp.isVerticalBarLayout).isEqualTo(false)
    }

    @Test
    fun getCellLayoutSpringLoadShrunkTop() {
        assertThat(dp.cellLayoutSpringLoadShrunkTop).isEqualTo(460)
    }

    @Test
    fun getCellLayoutSpringLoadShrunkBottom() {
        assertThat(dp.cellLayoutSpringLoadShrunkBottom).isEqualTo(1958)
    }

    @Test
    fun getQsbOffsetY() {
        assertThat(dp.qsbOffsetY).isEqualTo(272)
    }

    @Test
    fun getTaskbarOffsetY() {
        assertThat(dp.taskbarOffsetY).isEqualTo(112)
    }

    @Test
    fun getHotseatLayoutPaddingLeft() {
        assertThat(dp.getHotseatLayoutPadding(context).left).isEqualTo(340)
    }

    @Test
    fun getHotseatLayoutPaddingTop() {
        assertThat(dp.getHotseatLayoutPadding(context).top).isEqualTo(151)
    }

    @Test
    fun getHotseatLayoutPaddingRight() {
        assertThat(dp.getHotseatLayoutPadding(context).right).isEqualTo(428)
    }

    @Test
    fun getHotseatLayoutPaddingBottom() {
        assertThat(dp.getHotseatLayoutPadding(context).bottom).isEqualTo(109)
    }

    @Test
    fun hotseatBarEndOffset() {
        assertThat(dp.hotseatBarEndOffset).isEqualTo(428)
    }

    @Test
    fun overviewGridRectLeft() {
        assertThat(dp.overviewGridRect.left).isEqualTo(128)
    }

    @Test
    fun overviewGridRectTop() {
        assertThat(dp.overviewGridRect.top).isEqualTo(160)
    }

    @Test
    fun overviewGridRectRight() {
        assertThat(dp.overviewGridRect.right).isEqualTo(1472)
    }

    @Test
    fun overviewGridRectBottom() {
        assertThat(dp.overviewGridRect.bottom).isEqualTo(2292)
    }

    @Test
    fun taskDimensionX() {
        assertThat(dp.taskDimension.x).isEqualTo(1600)
    }

    @Test
    fun taskDimensionY() {
        assertThat(dp.taskDimension.y).isEqualTo(2440)
    }

    @Test
    fun overviewTaskRectLeft() {
        assertThat(dp.overviewTaskRect.left).isEqualTo(240)
    }

    @Test
    fun overviewTaskRectTop() {
        assertThat(dp.overviewTaskRect.top).isEqualTo(372)
    }

    @Test
    fun overviewTaskRectRight() {
        assertThat(dp.overviewTaskRect.right).isEqualTo(1360)
    }

    @Test
    fun overviewTaskRectBottom() {
        assertThat(dp.overviewTaskRect.bottom).isEqualTo(2080)
    }

    @Test
    fun overviewGridTaskDimensionX() {
        assertThat(dp.overviewGridTaskDimension.x).isEqualTo(494)
    }

    @Test
    fun overviewGridTaskDimensionY() {
        assertThat(dp.overviewGridTaskDimension.y).isEqualTo(754)
    }

    @Test
    fun overviewModalTaskRectLeft() {
        assertThat(dp.overviewModalTaskRect.left).isEqualTo(184)
    }

    @Test
    fun overviewModalTaskRectTop() {
        assertThat(dp.overviewModalTaskRect.top).isEqualTo(201)
    }

    @Test
    fun overviewModalTaskRectRight() {
        assertThat(dp.overviewModalTaskRect.right).isEqualTo(1416)
    }

    @Test
    fun overviewModalTaskRectBottom() {
        assertThat(dp.overviewModalTaskRect.bottom).isEqualTo(2080)
    }

    @Test
    fun getGridTaskRectLeft() {
        assertThat(dp.getOverviewGridTaskRect(isRecentsRtl).left).isEqualTo(866)
    }

    @Test
    fun getGridTaskRectTop() {
        assertThat(dp.getOverviewGridTaskRect(isRecentsRtl).top).isEqualTo(372)
    }

    @Test
    fun getGridTaskRectRight() {
        assertThat(dp.getOverviewGridTaskRect(isRecentsRtl).right).isEqualTo(1360)
    }

    @Test
    fun getGridTaskRectBottom() {
        assertThat(dp.getOverviewGridTaskRect(isRecentsRtl).bottom).isEqualTo(1126)
    }

    @Test
    fun overviewTaskScale() {
        assertThat(dp.overviewTaskWorkspaceScale).isEqualTo(0.7874597f)
    }

    @Test
    fun overviewModalTaskScale() {
        assertThat(dp.overviewModalTaskScale).isEqualTo(1.1f)
    }
}