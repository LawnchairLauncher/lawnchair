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
 * Tests for DeviceProfile for phone with 3-Button navigation.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceProfilePhone3ButtonTest : DeviceProfileBaseTest() {

    lateinit var dp: DeviceProfile

    @Before
    fun before() {
        initializeVarsForPhone(isGestureMode = false)
        dp = newDP()
    }

    @Test
    fun isScalableGrid() {
        assertThat(dp.isScalableGrid).isTrue()
    }

    @Test
    fun cellWidthPx() {
        assertThat(dp.cellWidthPx).isEqualTo(265)
    }

    @Test
    fun cellHeightPx() {
        assertThat(dp.cellHeightPx).isEqualTo(343)
    }

    @Test
    fun getCellSizeX() {
        assertThat(dp.getCellSize().x).isEqualTo(265)
    }

    @Test
    fun getCellSizeY() {
        assertThat(dp.getCellSize().y).isEqualTo(552)
    }

    @Test
    fun cellLayoutBorderSpacePxX() {
        assertThat(dp.cellLayoutBorderSpacePx.x).isEqualTo(66)
    }

    @Test
    fun cellLayoutBorderSpacePxY() {
        assertThat(dp.cellLayoutBorderSpacePx.y).isEqualTo(66)
    }

    @Test
    fun cellLayoutPaddingPxLeft() {
        assertThat(dp.cellLayoutPaddingPx.left).isEqualTo(38)
    }

    @Test
    fun cellLayoutPaddingPxTop() {
        assertThat(dp.cellLayoutPaddingPx.top).isEqualTo(0)
    }

    @Test
    fun cellLayoutPaddingPxRight() {
        assertThat(dp.cellLayoutPaddingPx.right).isEqualTo(38)
    }

    @Test
    fun cellLayoutPaddingPxBottom() {
        assertThat(dp.cellLayoutPaddingPx.bottom).isEqualTo(38)
    }

    @Test
    fun iconSizePx() {
        assertThat(dp.iconSizePx).isEqualTo(196)
    }

    @Test
    fun iconTextSizePx() {
        assertThat(dp.iconTextSizePx).isEqualTo(49)
    }

    @Test
    fun iconDrawablePaddingPx() {
        assertThat(dp.iconDrawablePaddingPx).isEqualTo(25)
    }

    @Test
    fun folderCellWidthPx() {
        assertThat(dp.folderCellWidthPx).isEqualTo(265)
    }

    @Test
    fun folderCellHeightPx() {
        assertThat(dp.folderCellHeightPx).isEqualTo(343)
    }

    @Test
    fun folderChildIconSizePx() {
        assertThat(dp.folderChildIconSizePx).isEqualTo(196)
    }

    @Test
    fun folderChildTextSizePx() {
        assertThat(dp.folderChildTextSizePx).isEqualTo(49)
    }

    @Test
    fun folderChildDrawablePaddingPx() {
        assertThat(dp.folderChildDrawablePaddingPx).isEqualTo(27)
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
        assertThat(dp.bottomSheetTopPadding).isEqualTo(38)
    }

    @Test
    fun allAppsShiftRange() {
        assertThat(dp.allAppsShiftRange).isEqualTo(1050)
    }

    @Test
    fun allAppsTopPadding() {
        assertThat(dp.allAppsTopPadding).isEqualTo(0)
    }

    @Test
    fun allAppsIconSizePx() {
        assertThat(dp.allAppsIconSizePx).isEqualTo(232)
    }

    @Test
    fun allAppsIconTextSizePx() {
        assertThat(dp.allAppsIconTextSizePx).isEqualTo(58)
    }

    @Test
    fun allAppsIconDrawablePaddingPx() {
        assertThat(dp.allAppsIconDrawablePaddingPx).isEqualTo(25)
    }

    @Test
    fun allAppsCellHeightPx() {
        assertThat(dp.allAppsCellHeightPx).isEqualTo(409)
    }

    @Test
    fun allAppsCellWidthPx() {
        assertThat(dp.allAppsCellWidthPx).isEqualTo(265)
    }

    @Test
    fun allAppsBorderSpacePxX() {
        assertThat(dp.allAppsBorderSpacePx.x).isEqualTo(66)
    }

    @Test
    fun allAppsBorderSpacePxY() {
        assertThat(dp.allAppsBorderSpacePx.y).isEqualTo(66)
    }

    @Test
    fun numShownAllAppsColumns() {
        assertThat(dp.numShownAllAppsColumns).isEqualTo(0)
    }

    @Test
    fun allAppsLeftRightPadding() {
        assertThat(dp.allAppsLeftRightPadding).isEqualTo(91)
    }

    @Test
    fun allAppsLeftRightMargin() {
        assertThat(dp.allAppsLeftRightMargin).isEqualTo(0)
    }

    @Test
    fun hotseatBarSizePx() {
        assertThat(dp.hotseatBarSizePx).isEqualTo(669)
    }

    @Test
    fun hotseatCellHeightPx() {
        assertThat(dp.hotseatCellHeightPx).isEqualTo(221)
    }

    @Test
    fun hotseatBarBottomPaddingPx() {
        assertThat(dp.hotseatBarBottomSpacePx).isEqualTo(168)
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
        assertThat(dp.hotseatQsbSpace).isEqualTo(126)
    }

    @Test
    fun hotseatQsbHeight() {
        assertThat(dp.hotseatQsbHeight).isEqualTo(221)
    }

    @Test
    fun springLoadedHotseatBarTopMarginPx() {
        assertThat(dp.springLoadedHotseatBarTopMarginPx).isEqualTo(266)
    }

    @Test
    fun numShownHotseatIcons() {
        assertThat(dp.numShownHotseatIcons).isEqualTo(4)
    }

    @Test
    fun hotseatBorderSpace() {
        assertThat(dp.hotseatBorderSpace).isEqualTo(135)
    }

    @Test
    fun isQsbInline() {
        assertThat(dp.isQsbInline).isEqualTo(false)
    }

    @Test
    fun qsbWidth() {
        assertThat(dp.qsbWidth).isEqualTo(1189)
    }

    @Test
    fun isTaskbarPresent() {
        assertThat(dp.isTaskbarPresent).isEqualTo(false)
    }

    @Test
    fun isTaskbarPresentInApps() {
        assertThat(dp.isTaskbarPresentInApps).isEqualTo(false)
    }

    @Test
    fun taskbarSize() {
        assertThat(dp.taskbarSize).isEqualTo(0)
    }

    @Test
    fun desiredWorkspaceHorizontalMarginPx() {
        assertThat(dp.desiredWorkspaceHorizontalMarginPx).isEqualTo(91)
    }

    @Test
    fun workspacePaddingLeft() {
        assertThat(dp.workspacePadding.left).isEqualTo(53)
    }

    @Test
    fun workspacePaddingTop() {
        assertThat(dp.workspacePadding.top).isEqualTo(0)
    }

    @Test
    fun workspacePaddingRight() {
        assertThat(dp.workspacePadding.right).isEqualTo(53)
    }

    @Test
    fun workspacePaddingBottom() {
        assertThat(dp.workspacePadding.bottom).isEqualTo(573)
    }

    @Test
    fun iconScale() {
        assertThat(dp.iconScale).isEqualTo(1)
    }

    @Test
    fun cellScaleToFit() {
        assertThat(dp.cellScaleToFit).isEqualTo(1.182266f)
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
        assertThat(dp.overviewTaskMarginPx).isEqualTo(56)
    }

    @Test
    fun overviewTaskMarginGridPx() {
        assertThat(dp.overviewTaskMarginGridPx).isEqualTo(0)
    }

    @Test
    fun overviewTaskIconSizePx() {
        assertThat(dp.overviewTaskIconSizePx).isEqualTo(168)
    }

    @Test
    fun overviewTaskIconDrawableSizePx() {
        assertThat(dp.overviewTaskIconDrawableSizePx).isEqualTo(154)
    }

    @Test
    fun overviewTaskIconDrawableSizeGridPx() {
        assertThat(dp.overviewTaskIconDrawableSizeGridPx).isEqualTo(0)
    }

    @Test
    fun overviewTaskThumbnailTopMarginPx() {
        assertThat(dp.overviewTaskThumbnailTopMarginPx).isEqualTo(280)
    }

    @Test
    fun overviewActionsTopMarginPx() {
        assertThat(dp.overviewActionsTopMarginPx).isEqualTo(84)
    }

    @Test
    fun overviewActionsHeight() {
        assertThat(dp.overviewActionsHeight).isEqualTo(168)
    }

    @Test
    fun overviewActionsButtonSpacing() {
        assertThat(dp.overviewActionsButtonSpacing).isEqualTo(126)
    }

    @Test
    fun overviewPageSpacing() {
        assertThat(dp.overviewPageSpacing).isEqualTo(56)
    }

    @Test
    fun overviewRowSpacing() {
        assertThat(dp.overviewRowSpacing).isEqualTo(-112)
    }

    @Test
    fun overviewGridSideMargin() {
        assertThat(dp.overviewGridSideMargin).isEqualTo(0)
    }

    @Test
    fun dropTargetBarTopMarginPx() {
        assertThat(dp.dropTargetBarTopMarginPx).isEqualTo(112)
    }

    @Test
    fun dropTargetBarSizePx() {
        assertThat(dp.dropTargetBarSizePx).isEqualTo(196)
    }

    @Test
    fun dropTargetBarBottomMarginPx() {
        assertThat(dp.dropTargetBarBottomMarginPx).isEqualTo(56)
    }

    @Test
    fun workspaceSpringLoadedMinNextPageVisiblePx() {
        assertThat(dp.workspaceSpringLoadedMinNextPageVisiblePx).isEqualTo(84)
    }

    @Test
    fun getWorkspaceSpringLoadScale() {
        assertThat(dp.workspaceSpringLoadScale).isEqualTo(0.74417657f)
    }

    @Test
    fun getCellLayoutHeight() {
        assertThat(dp.cellLayoutHeight).isEqualTo(2447)
    }

    @Test
    fun getCellLayoutWidth() {
        assertThat(dp.cellLayoutWidth).isEqualTo(1334)
    }

    @Test
    fun getPanelCount() {
        assertThat(dp.panelCount).isEqualTo(1)
    }

    @Test
    fun isVerticalBarLayout() {
        assertThat(dp.isVerticalBarLayout).isEqualTo(false)
    }

    @Test
    fun getCellLayoutSpringLoadShrunkTop() {
        assertThat(dp.cellLayoutSpringLoadShrunkTop).isEqualTo(364)
    }

    @Test
    fun getCellLayoutSpringLoadShrunkBottom() {
        assertThat(dp.cellLayoutSpringLoadShrunkBottom).isEqualTo(2185)
    }

    @Test
    fun getQsbOffsetY() {
        assertThat(dp.qsbOffsetY).isEqualTo(147)
    }

    @Test
    fun getTaskbarOffsetY() {
        assertThat(dp.taskbarOffsetY).isEqualTo(558)
    }

    @Test
    fun getHotseatLayoutPaddingLeft() {
        assertThat(dp.getHotseatLayoutPadding(context).left).isEqualTo(125)
    }

    @Test
    fun getHotseatLayoutPaddingTop() {
        assertThat(dp.getHotseatLayoutPadding(context).top).isEqualTo(0)
    }

    @Test
    fun getHotseatLayoutPaddingRight() {
        assertThat(dp.getHotseatLayoutPadding(context).right).isEqualTo(125)
    }

    @Test
    fun getHotseatLayoutPaddingBottom() {
        assertThat(dp.getHotseatLayoutPadding(context).bottom).isEqualTo(448)
    }

    @Test
    fun hotseatBarEndOffset() {
        assertThat(dp.hotseatBarEndOffset).isEqualTo(0)
    }

    @Test
    fun overviewGridRectLeft() {
        assertThat(dp.overviewGridRect.left).isEqualTo(0)
    }

    @Test
    fun overviewGridRectTop() {
        assertThat(dp.overviewGridRect.top).isEqualTo(280)
    }

    @Test
    fun overviewGridRectRight() {
        assertThat(dp.overviewGridRect.right).isEqualTo(1440)
    }

    @Test
    fun overviewGridRectBottom() {
        assertThat(dp.overviewGridRect.bottom).isEqualTo(2768)
    }

    @Test
    fun taskDimensionX() {
        assertThat(dp.taskDimension.x).isEqualTo(1440)
    }

    @Test
    fun taskDimensionY() {
        assertThat(dp.taskDimension.y).isEqualTo(3120)
    }

    @Test
    fun overviewTaskRectLeft() {
        assertThat(dp.overviewTaskRect.left).isEqualTo(231)
    }

    @Test
    fun overviewTaskRectTop() {
        assertThat(dp.overviewTaskRect.top).isEqualTo(414)
    }

    @Test
    fun overviewTaskRectRight() {
        assertThat(dp.overviewTaskRect.right).isEqualTo(1209)
    }

    @Test
    fun overviewTaskRectBottom() {
        assertThat(dp.overviewTaskRect.bottom).isEqualTo(2533)
    }

    @Test
    fun overviewGridTaskDimensionX() {
        assertThat(dp.overviewGridTaskDimension.x).isEqualTo(450)
    }

    @Test
    fun overviewGridTaskDimensionY() {
        assertThat(dp.overviewGridTaskDimension.y).isEqualTo(976)
    }

    @Test
    fun overviewModalTaskRectLeft() {
        assertThat(dp.overviewModalTaskRect.left).isEqualTo(182)
    }

    @Test
    fun overviewModalTaskRectTop() {
        assertThat(dp.overviewModalTaskRect.top).isEqualTo(202)
    }

    @Test
    fun overviewModalTaskRectRight() {
        assertThat(dp.overviewModalTaskRect.right).isEqualTo(1258)
    }

    @Test
    fun overviewModalTaskRectBottom() {
        assertThat(dp.overviewModalTaskRect.bottom).isEqualTo(2533)
    }

    @Test
    fun getGridTaskRectLeft() {
        assertThat(dp.getOverviewGridTaskRect(isRecentsRtl).left).isEqualTo(759)
    }

    @Test
    fun getGridTaskRectTop() {
        assertThat(dp.getOverviewGridTaskRect(isRecentsRtl).top).isEqualTo(414)
    }

    @Test
    fun getGridTaskRectRight() {
        assertThat(dp.getOverviewGridTaskRect(isRecentsRtl).right).isEqualTo(1209)
    }

    @Test
    fun getGridTaskRectBottom() {
        assertThat(dp.getOverviewGridTaskRect(isRecentsRtl).bottom).isEqualTo(1390)
    }

    @Test
    fun overviewTaskScale() {
        assertThat(dp.overviewTaskWorkspaceScale).isEqualTo(0.86595833f)
    }

    @Test
    fun overviewModalTaskScale() {
        assertThat(dp.overviewModalTaskScale).isEqualTo(1.1000472f)
    }
}