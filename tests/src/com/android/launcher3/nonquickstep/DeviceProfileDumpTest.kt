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
package com.android.launcher3.nonquickstep

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.AbstractDeviceProfileTest
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile
import com.google.common.truth.Truth.assertThat
import java.io.PrintWriter
import java.io.StringWriter
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for DeviceProfile. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceProfileDumpTest : AbstractDeviceProfileTest() {

    @Test
    fun phonePortrait3Button() {
        initializeVarsForPhone(deviceSpecs["phone"]!!, isGestureMode = false)
        val dp = getDeviceProfileForGrid("5_by_5")

        assertThat(dump(dp))
            .isEqualTo(
                "DeviceProfile:\n" +
                    "\t1 dp = 2.625 px\n" +
                    "\tisTablet:false\n" +
                    "\tisPhone:true\n" +
                    "\ttransposeLayoutWithOrientation:true\n" +
                    "\tisGestureMode:false\n" +
                    "\tisLandscape:false\n" +
                    "\tisMultiWindowMode:false\n" +
                    "\tisTwoPanels:false\n" +
                    "\twindowX: 0.0px (0.0dp)\n" +
                    "\twindowY: 0.0px (0.0dp)\n" +
                    "\twidthPx: 1080.0px (411.42856dp)\n" +
                    "\theightPx: 2400.0px (914.2857dp)\n" +
                    "\tavailableWidthPx: 1080.0px (411.42856dp)\n" +
                    "\tavailableHeightPx: 2156.0px (821.3333dp)\n" +
                    "\tmInsets.left: 0.0px (0.0dp)\n" +
                    "\tmInsets.top: 118.0px (44.95238dp)\n" +
                    "\tmInsets.right: 0.0px (0.0dp)\n" +
                    "\tmInsets.bottom: 126.0px (48.0dp)\n" +
                    "\taspectRatio:2.2222223\n" +
                    "\tisResponsiveGrid:false\n" +
                    "\tisScalableGrid:false\n" +
                    "\tinv.numRows: 5\n" +
                    "\tinv.numColumns: 5\n" +
                    "\tinv.numSearchContainerColumns: 5\n" +
                    "\tminCellSize: PointF(0.0, 0.0)dp\n" +
                    "\tcellWidthPx: 159.0px (60.57143dp)\n" +
                    "\tcellHeightPx: 229.0px (87.2381dp)\n" +
                    "\tgetCellSize().x: 207.0px (78.85714dp)\n" +
                    "\tgetCellSize().y: 379.0px (144.38095dp)\n" +
                    "\tcellLayoutBorderSpacePx Horizontal: 0.0px (0.0dp)\n" +
                    "\tcellLayoutBorderSpacePx Vertical: 0.0px (0.0dp)\n" +
                    "\tcellLayoutPaddingPx.left: 21.0px (8.0dp)\n" +
                    "\tcellLayoutPaddingPx.top: 28.0px (10.666667dp)\n" +
                    "\tcellLayoutPaddingPx.right: 21.0px (8.0dp)\n" +
                    "\tcellLayoutPaddingPx.bottom: 28.0px (10.666667dp)\n" +
                    "\ticonSizePx: 147.0px (56.0dp)\n" +
                    "\ticonTextSizePx: 38.0px (14.476191dp)\n" +
                    "\ticonDrawablePaddingPx: 12.0px (4.571429dp)\n" +
                    "\tinv.numFolderRows: 4\n" +
                    "\tinv.numFolderColumns: 4\n" +
                    "\tfolderCellWidthPx: 195.0px (74.28571dp)\n" +
                    "\tfolderCellHeightPx: 230.0px (87.61905dp)\n" +
                    "\tfolderChildIconSizePx: 147.0px (56.0dp)\n" +
                    "\tfolderChildTextSizePx: 38.0px (14.476191dp)\n" +
                    "\tfolderChildDrawablePaddingPx: 4.0px (1.5238096dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.x: 0.0px (0.0dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.y: 0.0px (0.0dp)\n" +
                    "\tfolderContentPaddingLeftRight: 21.0px (8.0dp)\n" +
                    "\tfolderTopPadding: 63.0px (24.0dp)\n" +
                    "\tfolderFooterHeight: 147.0px (56.0dp)\n" +
                    "\tbottomSheetTopPadding: 146.0px (55.61905dp)\n" +
                    "\tbottomSheetOpenDuration: 267\n" +
                    "\tbottomSheetCloseDuration: 267\n" +
                    "\tbottomSheetWorkspaceScale: 1.0\n" +
                    "\tbottomSheetDepth: 0.0\n" +
                    "\tallAppsShiftRange: 788.0px (300.1905dp)\n" +
                    "\tallAppsTopPadding: 0.0px (0.0dp)\n" +
                    "\tallAppsOpenDuration: 600\n" +
                    "\tallAppsCloseDuration: 300\n" +
                    "\tallAppsIconSizePx: 147.0px (56.0dp)\n" +
                    "\tallAppsIconTextSizePx: 38.0px (14.476191dp)\n" +
                    "\tallAppsIconDrawablePaddingPx: 21.0px (8.0dp)\n" +
                    "\tallAppsCellHeightPx: 315.0px (120.0dp)\n" +
                    "\tallAppsCellWidthPx: 189.0px (72.0dp)\n" +
                    "\tallAppsBorderSpacePxX: 42.0px (16.0dp)\n" +
                    "\tallAppsBorderSpacePxY: 42.0px (16.0dp)\n" +
                    "\tnumShownAllAppsColumns: 5\n" +
                    "\tallAppsLeftRightPadding: 0.0px (0.0dp)\n" +
                    "\tallAppsLeftRightMargin: 0.0px (0.0dp)\n" +
                    "\thotseatBarSizePx: 294.0px (112.0dp)\n" +
                    "\tinv.hotseatColumnSpan: 5\n" +
                    "\thotseatCellHeightPx: 166.0px (63.238094dp)\n" +
                    "\thotseatBarBottomSpacePx: 147.0px (56.0dp)\n" +
                    "\thotseatBarSidePaddingStartPx: 0.0px (0.0dp)\n" +
                    "\thotseatBarSidePaddingEndPx: 0.0px (0.0dp)\n" +
                    "\thotseatBarEndOffset: 0.0px (0.0dp)\n" +
                    "\thotseatQsbSpace: 0.0px (0.0dp)\n" +
                    "\thotseatQsbHeight: 0.0px (0.0dp)\n" +
                    "\tspringLoadedHotseatBarTopMarginPx: 200.0px (76.190475dp)\n" +
                    "\tgetHotseatLayoutPadding(context).top: 0.0px (0.0dp)\n" +
                    "\tgetHotseatLayoutPadding(context).bottom: 128.0px (48.761906dp)\n" +
                    "\tgetHotseatLayoutPadding(context).left: 21.0px (8.0dp)\n" +
                    "\tgetHotseatLayoutPadding(context).right: 21.0px (8.0dp)\n" +
                    "\tnumShownHotseatIcons: 5\n" +
                    "\thotseatBorderSpace: 0.0px (0.0dp)\n" +
                    "\tisQsbInline: false\n" +
                    "\thotseatQsbWidth: 0.0px (0.0dp)\n" +
                    "\tisTaskbarPresent:false\n" +
                    "\tisTaskbarPresentInApps:false\n" +
                    "\ttaskbarHeight: 0.0px (0.0dp)\n" +
                    "\tstashedTaskbarHeight: 0.0px (0.0dp)\n" +
                    "\ttaskbarBottomMargin: 0.0px (0.0dp)\n" +
                    "\ttaskbarIconSize: 0.0px (0.0dp)\n" +
                    "\tdesiredWorkspaceHorizontalMarginPx: 21.0px (8.0dp)\n" +
                    "\tworkspacePadding.left: 0.0px (0.0dp)\n" +
                    "\tworkspacePadding.top: 0.0px (0.0dp)\n" +
                    "\tworkspacePadding.right: 0.0px (0.0dp)\n" +
                    "\tworkspacePadding.bottom: 203.0px (77.333336dp)\n" +
                    "\ticonScale: 1.0px (0.3809524dp)\n" +
                    "\tcellScaleToFit : 1.0px (0.3809524dp)\n" +
                    "\textraSpace: 752.0px (286.4762dp)\n" +
                    "\tunscaled extraSpace: 752.0px (286.4762dp)\n" +
                    "\tmaxEmptySpace: 0.0px (0.0dp)\n" +
                    "\tworkspaceTopPadding: 0.0px (0.0dp)\n" +
                    "\tworkspaceBottomPadding: 0.0px (0.0dp)\n" +
                    "\toverviewTaskMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizeGridPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskThumbnailTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsHeight: 0.0px (0.0dp)\n" +
                    "\toverviewActionsClaimedSpaceBelow: 126.0px (48.0dp)\n" +
                    "\toverviewActionsButtonSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewPageSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewRowSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewGridSideMargin: 0.0px (0.0dp)\n" +
                    "\tdropTargetBarTopMarginPx: 84.0px (32.0dp)\n" +
                    "\tdropTargetBarSizePx: 147.0px (56.0dp)\n" +
                    "\tdropTargetBarBottomMarginPx: 42.0px (16.0dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkTop(): 391.0px (148.95238dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkBottom(): 1906.0px (726.0952dp)\n" +
                    "\tworkspaceSpringLoadedMinNextPageVisiblePx: 63.0px (24.0dp)\n" +
                    "\tgetWorkspaceSpringLoadScale(): 0.77572966px (0.29551607dp)\n" +
                    "\tgetCellLayoutHeight(): 1953.0px (744.0dp)\n" +
                    "\tgetCellLayoutWidth(): 1080.0px (411.42856dp)\n"
            )
    }

    @Test
    fun phonePortrait() {
        initializeVarsForPhone(deviceSpecs["phone"]!!)
        val dp = getDeviceProfileForGrid("5_by_5")

        assertThat(dump(dp))
            .isEqualTo(
                "DeviceProfile:\n" +
                    "\t1 dp = 2.625 px\n" +
                    "\tisTablet:false\n" +
                    "\tisPhone:true\n" +
                    "\ttransposeLayoutWithOrientation:true\n" +
                    "\tisGestureMode:true\n" +
                    "\tisLandscape:false\n" +
                    "\tisMultiWindowMode:false\n" +
                    "\tisTwoPanels:false\n" +
                    "\twindowX: 0.0px (0.0dp)\n" +
                    "\twindowY: 0.0px (0.0dp)\n" +
                    "\twidthPx: 1080.0px (411.42856dp)\n" +
                    "\theightPx: 2400.0px (914.2857dp)\n" +
                    "\tavailableWidthPx: 1080.0px (411.42856dp)\n" +
                    "\tavailableHeightPx: 2219.0px (845.3333dp)\n" +
                    "\tmInsets.left: 0.0px (0.0dp)\n" +
                    "\tmInsets.top: 118.0px (44.95238dp)\n" +
                    "\tmInsets.right: 0.0px (0.0dp)\n" +
                    "\tmInsets.bottom: 63.0px (24.0dp)\n" +
                    "\taspectRatio:2.2222223\n" +
                    "\tisResponsiveGrid:false\n" +
                    "\tisScalableGrid:false\n" +
                    "\tinv.numRows: 5\n" +
                    "\tinv.numColumns: 5\n" +
                    "\tinv.numSearchContainerColumns: 5\n" +
                    "\tminCellSize: PointF(0.0, 0.0)dp\n" +
                    "\tcellWidthPx: 159.0px (60.57143dp)\n" +
                    "\tcellHeightPx: 229.0px (87.2381dp)\n" +
                    "\tgetCellSize().x: 207.0px (78.85714dp)\n" +
                    "\tgetCellSize().y: 383.0px (145.90475dp)\n" +
                    "\tcellLayoutBorderSpacePx Horizontal: 0.0px (0.0dp)\n" +
                    "\tcellLayoutBorderSpacePx Vertical: 0.0px (0.0dp)\n" +
                    "\tcellLayoutPaddingPx.left: 21.0px (8.0dp)\n" +
                    "\tcellLayoutPaddingPx.top: 28.0px (10.666667dp)\n" +
                    "\tcellLayoutPaddingPx.right: 21.0px (8.0dp)\n" +
                    "\tcellLayoutPaddingPx.bottom: 28.0px (10.666667dp)\n" +
                    "\ticonSizePx: 147.0px (56.0dp)\n" +
                    "\ticonTextSizePx: 38.0px (14.476191dp)\n" +
                    "\ticonDrawablePaddingPx: 12.0px (4.571429dp)\n" +
                    "\tinv.numFolderRows: 4\n" +
                    "\tinv.numFolderColumns: 4\n" +
                    "\tfolderCellWidthPx: 195.0px (74.28571dp)\n" +
                    "\tfolderCellHeightPx: 230.0px (87.61905dp)\n" +
                    "\tfolderChildIconSizePx: 147.0px (56.0dp)\n" +
                    "\tfolderChildTextSizePx: 38.0px (14.476191dp)\n" +
                    "\tfolderChildDrawablePaddingPx: 4.0px (1.5238096dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.x: 0.0px (0.0dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.y: 0.0px (0.0dp)\n" +
                    "\tfolderContentPaddingLeftRight: 21.0px (8.0dp)\n" +
                    "\tfolderTopPadding: 63.0px (24.0dp)\n" +
                    "\tfolderFooterHeight: 147.0px (56.0dp)\n" +
                    "\tbottomSheetTopPadding: 146.0px (55.61905dp)\n" +
                    "\tbottomSheetOpenDuration: 267\n" +
                    "\tbottomSheetCloseDuration: 267\n" +
                    "\tbottomSheetWorkspaceScale: 1.0\n" +
                    "\tbottomSheetDepth: 0.0\n" +
                    "\tallAppsShiftRange: 788.0px (300.1905dp)\n" +
                    "\tallAppsTopPadding: 0.0px (0.0dp)\n" +
                    "\tallAppsOpenDuration: 600\n" +
                    "\tallAppsCloseDuration: 300\n" +
                    "\tallAppsIconSizePx: 147.0px (56.0dp)\n" +
                    "\tallAppsIconTextSizePx: 38.0px (14.476191dp)\n" +
                    "\tallAppsIconDrawablePaddingPx: 21.0px (8.0dp)\n" +
                    "\tallAppsCellHeightPx: 315.0px (120.0dp)\n" +
                    "\tallAppsCellWidthPx: 189.0px (72.0dp)\n" +
                    "\tallAppsBorderSpacePxX: 42.0px (16.0dp)\n" +
                    "\tallAppsBorderSpacePxY: 42.0px (16.0dp)\n" +
                    "\tnumShownAllAppsColumns: 5\n" +
                    "\tallAppsLeftRightPadding: 0.0px (0.0dp)\n" +
                    "\tallAppsLeftRightMargin: 0.0px (0.0dp)\n" +
                    "\thotseatBarSizePx: 273.0px (104.0dp)\n" +
                    "\tinv.hotseatColumnSpan: 5\n" +
                    "\thotseatCellHeightPx: 166.0px (63.238094dp)\n" +
                    "\thotseatBarBottomSpacePx: 126.0px (48.0dp)\n" +
                    "\thotseatBarSidePaddingStartPx: 0.0px (0.0dp)\n" +
                    "\thotseatBarSidePaddingEndPx: 0.0px (0.0dp)\n" +
                    "\thotseatBarEndOffset: 0.0px (0.0dp)\n" +
                    "\thotseatQsbSpace: 0.0px (0.0dp)\n" +
                    "\thotseatQsbHeight: 0.0px (0.0dp)\n" +
                    "\tspringLoadedHotseatBarTopMarginPx: 200.0px (76.190475dp)\n" +
                    "\tgetHotseatLayoutPadding(context).top: 0.0px (0.0dp)\n" +
                    "\tgetHotseatLayoutPadding(context).bottom: 107.0px (40.761906dp)\n" +
                    "\tgetHotseatLayoutPadding(context).left: 21.0px (8.0dp)\n" +
                    "\tgetHotseatLayoutPadding(context).right: 21.0px (8.0dp)\n" +
                    "\tnumShownHotseatIcons: 5\n" +
                    "\thotseatBorderSpace: 0.0px (0.0dp)\n" +
                    "\tisQsbInline: false\n" +
                    "\thotseatQsbWidth: 0.0px (0.0dp)\n" +
                    "\tisTaskbarPresent:false\n" +
                    "\tisTaskbarPresentInApps:false\n" +
                    "\ttaskbarHeight: 0.0px (0.0dp)\n" +
                    "\tstashedTaskbarHeight: 0.0px (0.0dp)\n" +
                    "\ttaskbarBottomMargin: 0.0px (0.0dp)\n" +
                    "\ttaskbarIconSize: 0.0px (0.0dp)\n" +
                    "\tdesiredWorkspaceHorizontalMarginPx: 21.0px (8.0dp)\n" +
                    "\tworkspacePadding.left: 0.0px (0.0dp)\n" +
                    "\tworkspacePadding.top: 0.0px (0.0dp)\n" +
                    "\tworkspacePadding.right: 0.0px (0.0dp)\n" +
                    "\tworkspacePadding.bottom: 245.0px (93.333336dp)\n" +
                    "\ticonScale: 1.0px (0.3809524dp)\n" +
                    "\tcellScaleToFit : 1.0px (0.3809524dp)\n" +
                    "\textraSpace: 773.0px (294.4762dp)\n" +
                    "\tunscaled extraSpace: 773.0px (294.4762dp)\n" +
                    "\tmaxEmptySpace: 0.0px (0.0dp)\n" +
                    "\tworkspaceTopPadding: 0.0px (0.0dp)\n" +
                    "\tworkspaceBottomPadding: 0.0px (0.0dp)\n" +
                    "\toverviewTaskMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizeGridPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskThumbnailTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsHeight: 0.0px (0.0dp)\n" +
                    "\toverviewActionsClaimedSpaceBelow: 63.0px (24.0dp)\n" +
                    "\toverviewActionsButtonSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewPageSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewRowSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewGridSideMargin: 0.0px (0.0dp)\n" +
                    "\tdropTargetBarTopMarginPx: 84.0px (32.0dp)\n" +
                    "\tdropTargetBarSizePx: 147.0px (56.0dp)\n" +
                    "\tdropTargetBarBottomMarginPx: 42.0px (16.0dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkTop(): 391.0px (148.95238dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkBottom(): 1927.0px (734.0952dp)\n" +
                    "\tworkspaceSpringLoadedMinNextPageVisiblePx: 63.0px (24.0dp)\n" +
                    "\tgetWorkspaceSpringLoadScale(): 0.7781155px (0.29642496dp)\n" +
                    "\tgetCellLayoutHeight(): 1974.0px (752.0dp)\n" +
                    "\tgetCellLayoutWidth(): 1080.0px (411.42856dp)\n"
            )
    }

    @Test
    fun phoneVerticalBar3Button() {
        initializeVarsForPhone(deviceSpecs["phone"]!!, isVerticalBar = true, isGestureMode = false)
        val dp = getDeviceProfileForGrid("5_by_5")

        assertThat(dump(dp))
            .isEqualTo(
                "DeviceProfile:\n" +
                    "\t1 dp = 2.625 px\n" +
                    "\tisTablet:false\n" +
                    "\tisPhone:true\n" +
                    "\ttransposeLayoutWithOrientation:true\n" +
                    "\tisGestureMode:false\n" +
                    "\tisLandscape:true\n" +
                    "\tisMultiWindowMode:false\n" +
                    "\tisTwoPanels:false\n" +
                    "\twindowX: 0.0px (0.0dp)\n" +
                    "\twindowY: 0.0px (0.0dp)\n" +
                    "\twidthPx: 2400.0px (914.2857dp)\n" +
                    "\theightPx: 1080.0px (411.42856dp)\n" +
                    "\tavailableWidthPx: 2156.0px (821.3333dp)\n" +
                    "\tavailableHeightPx: 1006.0px (383.2381dp)\n" +
                    "\tmInsets.left: 118.0px (44.95238dp)\n" +
                    "\tmInsets.top: 74.0px (28.190475dp)\n" +
                    "\tmInsets.right: 126.0px (48.0dp)\n" +
                    "\tmInsets.bottom: 0.0px (0.0dp)\n" +
                    "\taspectRatio:2.2222223\n" +
                    "\tisResponsiveGrid:false\n" +
                    "\tisScalableGrid:false\n" +
                    "\tinv.numRows: 5\n" +
                    "\tinv.numColumns: 5\n" +
                    "\tinv.numSearchContainerColumns: 5\n" +
                    "\tminCellSize: PointF(0.0, 0.0)dp\n" +
                    "\tcellWidthPx: 152.0px (57.904762dp)\n" +
                    "\tcellHeightPx: 166.0px (63.238094dp)\n" +
                    "\tgetCellSize().x: 368.0px (140.19048dp)\n" +
                    "\tgetCellSize().y: 193.0px (73.52381dp)\n" +
                    "\tcellLayoutBorderSpacePx Horizontal: 0.0px (0.0dp)\n" +
                    "\tcellLayoutBorderSpacePx Vertical: 0.0px (0.0dp)\n" +
                    "\tcellLayoutPaddingPx.left: 53.0px (20.190475dp)\n" +
                    "\tcellLayoutPaddingPx.top: 0.0px (0.0dp)\n" +
                    "\tcellLayoutPaddingPx.right: 53.0px (20.190475dp)\n" +
                    "\tcellLayoutPaddingPx.bottom: 40.0px (15.238095dp)\n" +
                    "\ticonSizePx: 147.0px (56.0dp)\n" +
                    "\ticonTextSizePx: 0.0px (0.0dp)\n" +
                    "\ticonDrawablePaddingPx: 0.0px (0.0dp)\n" +
                    "\tinv.numFolderRows: 4\n" +
                    "\tinv.numFolderColumns: 4\n" +
                    "\tfolderCellWidthPx: 173.0px (65.90476dp)\n" +
                    "\tfolderCellHeightPx: 205.0px (78.09524dp)\n" +
                    "\tfolderChildIconSizePx: 131.0px (49.904762dp)\n" +
                    "\tfolderChildTextSizePx: 34.0px (12.952381dp)\n" +
                    "\tfolderChildDrawablePaddingPx: 4.0px (1.5238096dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.x: 0.0px (0.0dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.y: 0.0px (0.0dp)\n" +
                    "\tfolderContentPaddingLeftRight: 21.0px (8.0dp)\n" +
                    "\tfolderTopPadding: 56.0px (21.333334dp)\n" +
                    "\tfolderFooterHeight: 131.0px (49.904762dp)\n" +
                    "\tbottomSheetTopPadding: 114.0px (43.42857dp)\n" +
                    "\tbottomSheetOpenDuration: 267\n" +
                    "\tbottomSheetCloseDuration: 267\n" +
                    "\tbottomSheetWorkspaceScale: 1.0\n" +
                    "\tbottomSheetDepth: 0.0\n" +
                    "\tallAppsShiftRange: 788.0px (300.1905dp)\n" +
                    "\tallAppsTopPadding: 0.0px (0.0dp)\n" +
                    "\tallAppsOpenDuration: 600\n" +
                    "\tallAppsCloseDuration: 300\n" +
                    "\tallAppsIconSizePx: 147.0px (56.0dp)\n" +
                    "\tallAppsIconTextSizePx: 38.0px (14.476191dp)\n" +
                    "\tallAppsIconDrawablePaddingPx: 21.0px (8.0dp)\n" +
                    "\tallAppsCellHeightPx: 321.0px (122.28571dp)\n" +
                    "\tallAppsCellWidthPx: 189.0px (72.0dp)\n" +
                    "\tallAppsBorderSpacePxX: 42.0px (16.0dp)\n" +
                    "\tallAppsBorderSpacePxY: 42.0px (16.0dp)\n" +
                    "\tnumShownAllAppsColumns: 5\n" +
                    "\tallAppsLeftRightPadding: 0.0px (0.0dp)\n" +
                    "\tallAppsLeftRightMargin: 0.0px (0.0dp)\n" +
                    "\thotseatBarSizePx: 252.0px (96.0dp)\n" +
                    "\tinv.hotseatColumnSpan: 5\n" +
                    "\thotseatCellHeightPx: 166.0px (63.238094dp)\n" +
                    "\thotseatBarBottomSpacePx: 126.0px (48.0dp)\n" +
                    "\thotseatBarSidePaddingStartPx: 63.0px (24.0dp)\n" +
                    "\thotseatBarSidePaddingEndPx: 42.0px (16.0dp)\n" +
                    "\thotseatBarEndOffset: 0.0px (0.0dp)\n" +
                    "\thotseatQsbSpace: 0.0px (0.0dp)\n" +
                    "\thotseatQsbHeight: 0.0px (0.0dp)\n" +
                    "\tspringLoadedHotseatBarTopMarginPx: 118.0px (44.95238dp)\n" +
                    "\tgetHotseatLayoutPadding(context).top: 64.0px (24.380953dp)\n" +
                    "\tgetHotseatLayoutPadding(context).bottom: 49.0px (18.666666dp)\n" +
                    "\tgetHotseatLayoutPadding(context).left: 42.0px (16.0dp)\n" +
                    "\tgetHotseatLayoutPadding(context).right: 189.0px (72.0dp)\n" +
                    "\tnumShownHotseatIcons: 5\n" +
                    "\thotseatBorderSpace: 0.0px (0.0dp)\n" +
                    "\tisQsbInline: false\n" +
                    "\thotseatQsbWidth: 0.0px (0.0dp)\n" +
                    "\tisTaskbarPresent:false\n" +
                    "\tisTaskbarPresentInApps:false\n" +
                    "\ttaskbarHeight: 0.0px (0.0dp)\n" +
                    "\tstashedTaskbarHeight: 0.0px (0.0dp)\n" +
                    "\ttaskbarBottomMargin: 0.0px (0.0dp)\n" +
                    "\ttaskbarIconSize: 0.0px (0.0dp)\n" +
                    "\tdesiredWorkspaceHorizontalMarginPx: 0.0px (0.0dp)\n" +
                    "\tworkspacePadding.left: 10.0px (3.8095238dp)\n" +
                    "\tworkspacePadding.top: 0.0px (0.0dp)\n" +
                    "\tworkspacePadding.right: 199.0px (75.809525dp)\n" +
                    "\tworkspacePadding.bottom: 0.0px (0.0dp)\n" +
                    "\ticonScale: 1.0px (0.3809524dp)\n" +
                    "\tcellScaleToFit : 1.0px (0.3809524dp)\n" +
                    "\textraSpace: 136.0px (51.809525dp)\n" +
                    "\tunscaled extraSpace: 136.0px (51.809525dp)\n" +
                    "\tmaxEmptySpace: 0.0px (0.0dp)\n" +
                    "\tworkspaceTopPadding: 0.0px (0.0dp)\n" +
                    "\tworkspaceBottomPadding: 0.0px (0.0dp)\n" +
                    "\toverviewTaskMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizeGridPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskThumbnailTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsHeight: 0.0px (0.0dp)\n" +
                    "\toverviewActionsClaimedSpaceBelow: 0.0px (0.0dp)\n" +
                    "\toverviewActionsButtonSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewPageSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewRowSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewGridSideMargin: 0.0px (0.0dp)\n" +
                    "\tdropTargetBarTopMarginPx: 16.0px (6.095238dp)\n" +
                    "\tdropTargetBarSizePx: 95.0px (36.190475dp)\n" +
                    "\tdropTargetBarBottomMarginPx: 16.0px (6.095238dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkTop(): 201.0px (76.57143dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkBottom(): 1008.0px (384.0dp)\n" +
                    "\tworkspaceSpringLoadedMinNextPageVisiblePx: 63.0px (24.0dp)\n" +
                    "\tgetWorkspaceSpringLoadScale(): 0.8021869px (0.305595dp)\n" +
                    "\tgetCellLayoutHeight(): 1006.0px (383.2381dp)\n" +
                    "\tgetCellLayoutWidth(): 1947.0px (741.7143dp)\n"
            )
    }

    @Test
    fun phoneVerticalBar() {
        initializeVarsForPhone(deviceSpecs["phone"]!!, isVerticalBar = true)
        val dp = getDeviceProfileForGrid("5_by_5")

        assertThat(dump(dp))
            .isEqualTo(
                "DeviceProfile:\n" +
                    "\t1 dp = 2.625 px\n" +
                    "\tisTablet:false\n" +
                    "\tisPhone:true\n" +
                    "\ttransposeLayoutWithOrientation:true\n" +
                    "\tisGestureMode:true\n" +
                    "\tisLandscape:true\n" +
                    "\tisMultiWindowMode:false\n" +
                    "\tisTwoPanels:false\n" +
                    "\twindowX: 0.0px (0.0dp)\n" +
                    "\twindowY: 0.0px (0.0dp)\n" +
                    "\twidthPx: 2400.0px (914.2857dp)\n" +
                    "\theightPx: 1080.0px (411.42856dp)\n" +
                    "\tavailableWidthPx: 2282.0px (869.3333dp)\n" +
                    "\tavailableHeightPx: 943.0px (359.2381dp)\n" +
                    "\tmInsets.left: 118.0px (44.95238dp)\n" +
                    "\tmInsets.top: 74.0px (28.190475dp)\n" +
                    "\tmInsets.right: 0.0px (0.0dp)\n" +
                    "\tmInsets.bottom: 63.0px (24.0dp)\n" +
                    "\taspectRatio:2.2222223\n" +
                    "\tisResponsiveGrid:false\n" +
                    "\tisScalableGrid:false\n" +
                    "\tinv.numRows: 5\n" +
                    "\tinv.numColumns: 5\n" +
                    "\tinv.numSearchContainerColumns: 5\n" +
                    "\tminCellSize: PointF(0.0, 0.0)dp\n" +
                    "\tcellWidthPx: 152.0px (57.904762dp)\n" +
                    "\tcellHeightPx: 166.0px (63.238094dp)\n" +
                    "\tgetCellSize().x: 393.0px (149.71428dp)\n" +
                    "\tgetCellSize().y: 180.0px (68.57143dp)\n" +
                    "\tcellLayoutBorderSpacePx Horizontal: 0.0px (0.0dp)\n" +
                    "\tcellLayoutBorderSpacePx Vertical: 0.0px (0.0dp)\n" +
                    "\tcellLayoutPaddingPx.left: 53.0px (20.190475dp)\n" +
                    "\tcellLayoutPaddingPx.top: 0.0px (0.0dp)\n" +
                    "\tcellLayoutPaddingPx.right: 53.0px (20.190475dp)\n" +
                    "\tcellLayoutPaddingPx.bottom: 40.0px (15.238095dp)\n" +
                    "\ticonSizePx: 147.0px (56.0dp)\n" +
                    "\ticonTextSizePx: 0.0px (0.0dp)\n" +
                    "\ticonDrawablePaddingPx: 0.0px (0.0dp)\n" +
                    "\tinv.numFolderRows: 4\n" +
                    "\tinv.numFolderColumns: 4\n" +
                    "\tfolderCellWidthPx: 163.0px (62.095238dp)\n" +
                    "\tfolderCellHeightPx: 192.0px (73.14286dp)\n" +
                    "\tfolderChildIconSizePx: 123.0px (46.857143dp)\n" +
                    "\tfolderChildTextSizePx: 32.0px (12.190476dp)\n" +
                    "\tfolderChildDrawablePaddingPx: 3.0px (1.1428572dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.x: 0.0px (0.0dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.y: 0.0px (0.0dp)\n" +
                    "\tfolderContentPaddingLeftRight: 21.0px (8.0dp)\n" +
                    "\tfolderTopPadding: 53.0px (20.190475dp)\n" +
                    "\tfolderFooterHeight: 123.0px (46.857143dp)\n" +
                    "\tbottomSheetTopPadding: 114.0px (43.42857dp)\n" +
                    "\tbottomSheetOpenDuration: 267\n" +
                    "\tbottomSheetCloseDuration: 267\n" +
                    "\tbottomSheetWorkspaceScale: 1.0\n" +
                    "\tbottomSheetDepth: 0.0\n" +
                    "\tallAppsShiftRange: 788.0px (300.1905dp)\n" +
                    "\tallAppsTopPadding: 0.0px (0.0dp)\n" +
                    "\tallAppsOpenDuration: 600\n" +
                    "\tallAppsCloseDuration: 300\n" +
                    "\tallAppsIconSizePx: 147.0px (56.0dp)\n" +
                    "\tallAppsIconTextSizePx: 38.0px (14.476191dp)\n" +
                    "\tallAppsIconDrawablePaddingPx: 21.0px (8.0dp)\n" +
                    "\tallAppsCellHeightPx: 321.0px (122.28571dp)\n" +
                    "\tallAppsCellWidthPx: 189.0px (72.0dp)\n" +
                    "\tallAppsBorderSpacePxX: 42.0px (16.0dp)\n" +
                    "\tallAppsBorderSpacePxY: 42.0px (16.0dp)\n" +
                    "\tnumShownAllAppsColumns: 5\n" +
                    "\tallAppsLeftRightPadding: 0.0px (0.0dp)\n" +
                    "\tallAppsLeftRightMargin: 0.0px (0.0dp)\n" +
                    "\thotseatBarSizePx: 252.0px (96.0dp)\n" +
                    "\tinv.hotseatColumnSpan: 5\n" +
                    "\thotseatCellHeightPx: 166.0px (63.238094dp)\n" +
                    "\thotseatBarBottomSpacePx: 126.0px (48.0dp)\n" +
                    "\thotseatBarSidePaddingStartPx: 63.0px (24.0dp)\n" +
                    "\thotseatBarSidePaddingEndPx: 42.0px (16.0dp)\n" +
                    "\thotseatBarEndOffset: 0.0px (0.0dp)\n" +
                    "\thotseatQsbSpace: 0.0px (0.0dp)\n" +
                    "\thotseatQsbHeight: 0.0px (0.0dp)\n" +
                    "\tspringLoadedHotseatBarTopMarginPx: 118.0px (44.95238dp)\n" +
                    "\tgetHotseatLayoutPadding(context).top: 64.0px (24.380953dp)\n" +
                    "\tgetHotseatLayoutPadding(context).bottom: 112.0px (42.666668dp)\n" +
                    "\tgetHotseatLayoutPadding(context).left: 42.0px (16.0dp)\n" +
                    "\tgetHotseatLayoutPadding(context).right: 63.0px (24.0dp)\n" +
                    "\tnumShownHotseatIcons: 5\n" +
                    "\thotseatBorderSpace: 0.0px (0.0dp)\n" +
                    "\tisQsbInline: false\n" +
                    "\thotseatQsbWidth: 0.0px (0.0dp)\n" +
                    "\tisTaskbarPresent:false\n" +
                    "\tisTaskbarPresentInApps:false\n" +
                    "\ttaskbarHeight: 0.0px (0.0dp)\n" +
                    "\tstashedTaskbarHeight: 0.0px (0.0dp)\n" +
                    "\ttaskbarBottomMargin: 0.0px (0.0dp)\n" +
                    "\ttaskbarIconSize: 0.0px (0.0dp)\n" +
                    "\tdesiredWorkspaceHorizontalMarginPx: 0.0px (0.0dp)\n" +
                    "\tworkspacePadding.left: 10.0px (3.8095238dp)\n" +
                    "\tworkspacePadding.top: 0.0px (0.0dp)\n" +
                    "\tworkspacePadding.right: 199.0px (75.809525dp)\n" +
                    "\tworkspacePadding.bottom: 0.0px (0.0dp)\n" +
                    "\ticonScale: 1.0px (0.3809524dp)\n" +
                    "\tcellScaleToFit : 1.0px (0.3809524dp)\n" +
                    "\textraSpace: 73.0px (27.809525dp)\n" +
                    "\tunscaled extraSpace: 73.0px (27.809525dp)\n" +
                    "\tmaxEmptySpace: 0.0px (0.0dp)\n" +
                    "\tworkspaceTopPadding: 0.0px (0.0dp)\n" +
                    "\tworkspaceBottomPadding: 0.0px (0.0dp)\n" +
                    "\toverviewTaskMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizeGridPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskThumbnailTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsHeight: 0.0px (0.0dp)\n" +
                    "\toverviewActionsClaimedSpaceBelow: 63.0px (24.0dp)\n" +
                    "\toverviewActionsButtonSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewPageSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewRowSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewGridSideMargin: 0.0px (0.0dp)\n" +
                    "\tdropTargetBarTopMarginPx: 16.0px (6.095238dp)\n" +
                    "\tdropTargetBarSizePx: 95.0px (36.190475dp)\n" +
                    "\tdropTargetBarBottomMarginPx: 16.0px (6.095238dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkTop(): 201.0px (76.57143dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkBottom(): 952.0px (362.66666dp)\n" +
                    "\tworkspaceSpringLoadedMinNextPageVisiblePx: 63.0px (24.0dp)\n" +
                    "\tgetWorkspaceSpringLoadScale(): 0.79639447px (0.30338836dp)\n" +
                    "\tgetCellLayoutHeight(): 943.0px (359.2381dp)\n" +
                    "\tgetCellLayoutWidth(): 2073.0px (789.7143dp)\n"
            )
    }

    @Test
    fun tabletLandscape3Button() {
        initializeVarsForTablet(deviceSpecs["tablet"]!!, isLandscape = true, isGestureMode = false)
        val dp = getDeviceProfileForGrid("6_by_5")
        dp.isTaskbarPresentInApps = true

        assertThat(dump(dp))
            .isEqualTo(
                "DeviceProfile:\n" +
                    "\t1 dp = 2.0 px\n" +
                    "\tisTablet:true\n" +
                    "\tisPhone:false\n" +
                    "\ttransposeLayoutWithOrientation:false\n" +
                    "\tisGestureMode:false\n" +
                    "\tisLandscape:true\n" +
                    "\tisMultiWindowMode:false\n" +
                    "\tisTwoPanels:false\n" +
                    "\twindowX: 0.0px (0.0dp)\n" +
                    "\twindowY: 0.0px (0.0dp)\n" +
                    "\twidthPx: 2560.0px (1280.0dp)\n" +
                    "\theightPx: 1600.0px (800.0dp)\n" +
                    "\tavailableWidthPx: 2560.0px (1280.0dp)\n" +
                    "\tavailableHeightPx: 1496.0px (748.0dp)\n" +
                    "\tmInsets.left: 0.0px (0.0dp)\n" +
                    "\tmInsets.top: 104.0px (52.0dp)\n" +
                    "\tmInsets.right: 0.0px (0.0dp)\n" +
                    "\tmInsets.bottom: 0.0px (0.0dp)\n" +
                    "\taspectRatio:1.6\n" +
                    "\tisResponsiveGrid:false\n" +
                    "\tisScalableGrid:true\n" +
                    "\tinv.numRows: 5\n" +
                    "\tinv.numColumns: 6\n" +
                    "\tinv.numSearchContainerColumns: 3\n" +
                    "\tminCellSize: PointF(120.0, 104.0)dp\n" +
                    "\tcellWidthPx: 240.0px (120.0dp)\n" +
                    "\tcellHeightPx: 208.0px (104.0dp)\n" +
                    "\tgetCellSize().x: 240.0px (120.0dp)\n" +
                    "\tgetCellSize().y: 208.0px (104.0dp)\n" +
                    "\tcellLayoutBorderSpacePx Horizontal: 128.0px (64.0dp)\n" +
                    "\tcellLayoutBorderSpacePx Vertical: 32.0px (16.0dp)\n" +
                    "\tcellLayoutPaddingPx.left: 59.0px (29.5dp)\n" +
                    "\tcellLayoutPaddingPx.top: 25.0px (12.5dp)\n" +
                    "\tcellLayoutPaddingPx.right: 59.0px (29.5dp)\n" +
                    "\tcellLayoutPaddingPx.bottom: 59.0px (29.5dp)\n" +
                    "\ticonSizePx: 120.0px (60.0dp)\n" +
                    "\ticonTextSizePx: 28.0px (14.0dp)\n" +
                    "\ticonDrawablePaddingPx: 9.0px (4.5dp)\n" +
                    "\tinv.numFolderRows: 3\n" +
                    "\tinv.numFolderColumns: 3\n" +
                    "\tfolderCellWidthPx: 240.0px (120.0dp)\n" +
                    "\tfolderCellHeightPx: 208.0px (104.0dp)\n" +
                    "\tfolderChildIconSizePx: 120.0px (60.0dp)\n" +
                    "\tfolderChildTextSizePx: 28.0px (14.0dp)\n" +
                    "\tfolderChildDrawablePaddingPx: 11.0px (5.5dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.x: 0.0px (0.0dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.y: 0.0px (0.0dp)\n" +
                    "\tfolderContentPaddingLeftRight: 0.0px (0.0dp)\n" +
                    "\tfolderTopPadding: 48.0px (24.0dp)\n" +
                    "\tfolderFooterHeight: 112.0px (56.0dp)\n" +
                    "\tbottomSheetTopPadding: 104.0px (52.0dp)\n" +
                    "\tbottomSheetOpenDuration: 500\n" +
                    "\tbottomSheetCloseDuration: 500\n" +
                    "\tbottomSheetWorkspaceScale: 0.97\n" +
                    "\tbottomSheetDepth: 0.0\n" +
                    "\tallAppsShiftRange: 1496.0px (748.0dp)\n" +
                    "\tallAppsTopPadding: 104.0px (52.0dp)\n" +
                    "\tallAppsOpenDuration: 500\n" +
                    "\tallAppsCloseDuration: 500\n" +
                    "\tallAppsIconSizePx: 120.0px (60.0dp)\n" +
                    "\tallAppsIconTextSizePx: 28.0px (14.0dp)\n" +
                    "\tallAppsIconDrawablePaddingPx: 9.0px (4.5dp)\n" +
                    "\tallAppsCellHeightPx: 284.0px (142.0dp)\n" +
                    "\tallAppsCellWidthPx: 252.0px (126.0dp)\n" +
                    "\tallAppsBorderSpacePxX: 32.0px (16.0dp)\n" +
                    "\tallAppsBorderSpacePxY: 32.0px (16.0dp)\n" +
                    "\tnumShownAllAppsColumns: 6\n" +
                    "\tallAppsLeftRightPadding: 32.0px (16.0dp)\n" +
                    "\tallAppsLeftRightMargin: 412.0px (206.0dp)\n" +
                    "\thotseatBarSizePx: 200.0px (100.0dp)\n" +
                    "\tinv.hotseatColumnSpan: 4\n" +
                    "\thotseatCellHeightPx: 135.0px (67.5dp)\n" +
                    "\thotseatBarBottomSpacePx: 80.0px (40.0dp)\n" +
                    "\thotseatBarSidePaddingStartPx: 0.0px (0.0dp)\n" +
                    "\thotseatBarSidePaddingEndPx: 0.0px (0.0dp)\n" +
                    "\thotseatBarEndOffset: 0.0px (0.0dp)\n" +
                    "\thotseatQsbSpace: 0.0px (0.0dp)\n" +
                    "\thotseatQsbHeight: 0.0px (0.0dp)\n" +
                    "\tspringLoadedHotseatBarTopMarginPx: 128.0px (64.0dp)\n" +
                    "\tgetHotseatLayoutPadding(context).top: 0.0px (0.0dp)\n" +
                    "\tgetHotseatLayoutPadding(context).bottom: 65.0px (32.5dp)\n" +
                    "\tgetHotseatLayoutPadding(context).left: 668.0px (334.0dp)\n" +
                    "\tgetHotseatLayoutPadding(context).right: 668.0px (334.0dp)\n" +
                    "\tnumShownHotseatIcons: 6\n" +
                    "\thotseatBorderSpace: 100.0px (50.0dp)\n" +
                    "\tisQsbInline: false\n" +
                    "\thotseatQsbWidth: 1224.0px (612.0dp)\n" +
                    "\tisTaskbarPresent:false\n" +
                    "\tisTaskbarPresentInApps:true\n" +
                    "\ttaskbarHeight: 0.0px (0.0dp)\n" +
                    "\tstashedTaskbarHeight: 0.0px (0.0dp)\n" +
                    "\ttaskbarBottomMargin: 0.0px (0.0dp)\n" +
                    "\ttaskbarIconSize: 0.0px (0.0dp)\n" +
                    "\tdesiredWorkspaceHorizontalMarginPx: 240.0px (120.0dp)\n" +
                    "\tworkspacePadding.left: 181.0px (90.5dp)\n" +
                    "\tworkspacePadding.top: 0.0px (0.0dp)\n" +
                    "\tworkspacePadding.right: 181.0px (90.5dp)\n" +
                    "\tworkspacePadding.bottom: 244.0px (122.0dp)\n" +
                    "\ticonScale: 1.0px (0.5dp)\n" +
                    "\tcellScaleToFit : 1.0px (0.5dp)\n" +
                    "\textraSpace: 80.0px (40.0dp)\n" +
                    "\tunscaled extraSpace: 80.0px (40.0dp)\n" +
                    "\tmaxEmptySpace: 200.0px (100.0dp)\n" +
                    "\tworkspaceTopPadding: 25.0px (12.5dp)\n" +
                    "\tworkspaceBottomPadding: 55.0px (27.5dp)\n" +
                    "\toverviewTaskMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizeGridPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskThumbnailTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsHeight: 0.0px (0.0dp)\n" +
                    "\toverviewActionsClaimedSpaceBelow: 0.0px (0.0dp)\n" +
                    "\toverviewActionsButtonSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewPageSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewRowSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewGridSideMargin: 0.0px (0.0dp)\n" +
                    "\tdropTargetBarTopMarginPx: 0.0px (0.0dp)\n" +
                    "\tdropTargetBarSizePx: 144.0px (72.0dp)\n" +
                    "\tdropTargetBarBottomMarginPx: 64.0px (32.0dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkTop(): 312.0px (156.0dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkBottom(): 1272.0px (636.0dp)\n" +
                    "\tworkspaceSpringLoadedMinNextPageVisiblePx: 48.0px (24.0dp)\n" +
                    "\tgetWorkspaceSpringLoadScale(): 0.76677316px (0.38338658dp)\n" +
                    "\tgetCellLayoutHeight(): 1252.0px (626.0dp)\n" +
                    "\tgetCellLayoutWidth(): 2198.0px (1099.0dp)\n"
            )
    }

    @Test
    fun tabletLandscape() {
        initializeVarsForTablet(deviceSpecs["tablet"]!!, isLandscape = true)
        val dp = getDeviceProfileForGrid("6_by_5")
        dp.isTaskbarPresentInApps = true

        assertThat(dump(dp))
            .isEqualTo(
                "DeviceProfile:\n" +
                    "\t1 dp = 2.0 px\n" +
                    "\tisTablet:true\n" +
                    "\tisPhone:false\n" +
                    "\ttransposeLayoutWithOrientation:false\n" +
                    "\tisGestureMode:true\n" +
                    "\tisLandscape:true\n" +
                    "\tisMultiWindowMode:false\n" +
                    "\tisTwoPanels:false\n" +
                    "\twindowX: 0.0px (0.0dp)\n" +
                    "\twindowY: 0.0px (0.0dp)\n" +
                    "\twidthPx: 2560.0px (1280.0dp)\n" +
                    "\theightPx: 1600.0px (800.0dp)\n" +
                    "\tavailableWidthPx: 2560.0px (1280.0dp)\n" +
                    "\tavailableHeightPx: 1496.0px (748.0dp)\n" +
                    "\tmInsets.left: 0.0px (0.0dp)\n" +
                    "\tmInsets.top: 104.0px (52.0dp)\n" +
                    "\tmInsets.right: 0.0px (0.0dp)\n" +
                    "\tmInsets.bottom: 0.0px (0.0dp)\n" +
                    "\taspectRatio:1.6\n" +
                    "\tisResponsiveGrid:false\n" +
                    "\tisScalableGrid:true\n" +
                    "\tinv.numRows: 5\n" +
                    "\tinv.numColumns: 6\n" +
                    "\tinv.numSearchContainerColumns: 3\n" +
                    "\tminCellSize: PointF(120.0, 104.0)dp\n" +
                    "\tcellWidthPx: 240.0px (120.0dp)\n" +
                    "\tcellHeightPx: 208.0px (104.0dp)\n" +
                    "\tgetCellSize().x: 240.0px (120.0dp)\n" +
                    "\tgetCellSize().y: 208.0px (104.0dp)\n" +
                    "\tcellLayoutBorderSpacePx Horizontal: 128.0px (64.0dp)\n" +
                    "\tcellLayoutBorderSpacePx Vertical: 32.0px (16.0dp)\n" +
                    "\tcellLayoutPaddingPx.left: 59.0px (29.5dp)\n" +
                    "\tcellLayoutPaddingPx.top: 25.0px (12.5dp)\n" +
                    "\tcellLayoutPaddingPx.right: 59.0px (29.5dp)\n" +
                    "\tcellLayoutPaddingPx.bottom: 59.0px (29.5dp)\n" +
                    "\ticonSizePx: 120.0px (60.0dp)\n" +
                    "\ticonTextSizePx: 28.0px (14.0dp)\n" +
                    "\ticonDrawablePaddingPx: 9.0px (4.5dp)\n" +
                    "\tinv.numFolderRows: 3\n" +
                    "\tinv.numFolderColumns: 3\n" +
                    "\tfolderCellWidthPx: 240.0px (120.0dp)\n" +
                    "\tfolderCellHeightPx: 208.0px (104.0dp)\n" +
                    "\tfolderChildIconSizePx: 120.0px (60.0dp)\n" +
                    "\tfolderChildTextSizePx: 28.0px (14.0dp)\n" +
                    "\tfolderChildDrawablePaddingPx: 11.0px (5.5dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.x: 0.0px (0.0dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.y: 0.0px (0.0dp)\n" +
                    "\tfolderContentPaddingLeftRight: 0.0px (0.0dp)\n" +
                    "\tfolderTopPadding: 48.0px (24.0dp)\n" +
                    "\tfolderFooterHeight: 112.0px (56.0dp)\n" +
                    "\tbottomSheetTopPadding: 104.0px (52.0dp)\n" +
                    "\tbottomSheetOpenDuration: 500\n" +
                    "\tbottomSheetCloseDuration: 500\n" +
                    "\tbottomSheetWorkspaceScale: 0.97\n" +
                    "\tbottomSheetDepth: 0.0\n" +
                    "\tallAppsShiftRange: 1496.0px (748.0dp)\n" +
                    "\tallAppsTopPadding: 104.0px (52.0dp)\n" +
                    "\tallAppsOpenDuration: 500\n" +
                    "\tallAppsCloseDuration: 500\n" +
                    "\tallAppsIconSizePx: 120.0px (60.0dp)\n" +
                    "\tallAppsIconTextSizePx: 28.0px (14.0dp)\n" +
                    "\tallAppsIconDrawablePaddingPx: 9.0px (4.5dp)\n" +
                    "\tallAppsCellHeightPx: 284.0px (142.0dp)\n" +
                    "\tallAppsCellWidthPx: 252.0px (126.0dp)\n" +
                    "\tallAppsBorderSpacePxX: 32.0px (16.0dp)\n" +
                    "\tallAppsBorderSpacePxY: 32.0px (16.0dp)\n" +
                    "\tnumShownAllAppsColumns: 6\n" +
                    "\tallAppsLeftRightPadding: 32.0px (16.0dp)\n" +
                    "\tallAppsLeftRightMargin: 412.0px (206.0dp)\n" +
                    "\thotseatBarSizePx: 200.0px (100.0dp)\n" +
                    "\tinv.hotseatColumnSpan: 4\n" +
                    "\thotseatCellHeightPx: 135.0px (67.5dp)\n" +
                    "\thotseatBarBottomSpacePx: 80.0px (40.0dp)\n" +
                    "\thotseatBarSidePaddingStartPx: 0.0px (0.0dp)\n" +
                    "\thotseatBarSidePaddingEndPx: 0.0px (0.0dp)\n" +
                    "\thotseatBarEndOffset: 0.0px (0.0dp)\n" +
                    "\thotseatQsbSpace: 0.0px (0.0dp)\n" +
                    "\thotseatQsbHeight: 0.0px (0.0dp)\n" +
                    "\tspringLoadedHotseatBarTopMarginPx: 128.0px (64.0dp)\n" +
                    "\tgetHotseatLayoutPadding(context).top: 0.0px (0.0dp)\n" +
                    "\tgetHotseatLayoutPadding(context).bottom: 65.0px (32.5dp)\n" +
                    "\tgetHotseatLayoutPadding(context).left: 668.0px (334.0dp)\n" +
                    "\tgetHotseatLayoutPadding(context).right: 668.0px (334.0dp)\n" +
                    "\tnumShownHotseatIcons: 6\n" +
                    "\thotseatBorderSpace: 100.0px (50.0dp)\n" +
                    "\tisQsbInline: false\n" +
                    "\thotseatQsbWidth: 1224.0px (612.0dp)\n" +
                    "\tisTaskbarPresent:false\n" +
                    "\tisTaskbarPresentInApps:true\n" +
                    "\ttaskbarHeight: 0.0px (0.0dp)\n" +
                    "\tstashedTaskbarHeight: 0.0px (0.0dp)\n" +
                    "\ttaskbarBottomMargin: 0.0px (0.0dp)\n" +
                    "\ttaskbarIconSize: 0.0px (0.0dp)\n" +
                    "\tdesiredWorkspaceHorizontalMarginPx: 240.0px (120.0dp)\n" +
                    "\tworkspacePadding.left: 181.0px (90.5dp)\n" +
                    "\tworkspacePadding.top: 0.0px (0.0dp)\n" +
                    "\tworkspacePadding.right: 181.0px (90.5dp)\n" +
                    "\tworkspacePadding.bottom: 244.0px (122.0dp)\n" +
                    "\ticonScale: 1.0px (0.5dp)\n" +
                    "\tcellScaleToFit : 1.0px (0.5dp)\n" +
                    "\textraSpace: 80.0px (40.0dp)\n" +
                    "\tunscaled extraSpace: 80.0px (40.0dp)\n" +
                    "\tmaxEmptySpace: 200.0px (100.0dp)\n" +
                    "\tworkspaceTopPadding: 25.0px (12.5dp)\n" +
                    "\tworkspaceBottomPadding: 55.0px (27.5dp)\n" +
                    "\toverviewTaskMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizeGridPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskThumbnailTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsHeight: 0.0px (0.0dp)\n" +
                    "\toverviewActionsClaimedSpaceBelow: 0.0px (0.0dp)\n" +
                    "\toverviewActionsButtonSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewPageSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewRowSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewGridSideMargin: 0.0px (0.0dp)\n" +
                    "\tdropTargetBarTopMarginPx: 0.0px (0.0dp)\n" +
                    "\tdropTargetBarSizePx: 144.0px (72.0dp)\n" +
                    "\tdropTargetBarBottomMarginPx: 64.0px (32.0dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkTop(): 312.0px (156.0dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkBottom(): 1272.0px (636.0dp)\n" +
                    "\tworkspaceSpringLoadedMinNextPageVisiblePx: 48.0px (24.0dp)\n" +
                    "\tgetWorkspaceSpringLoadScale(): 0.76677316px (0.38338658dp)\n" +
                    "\tgetCellLayoutHeight(): 1252.0px (626.0dp)\n" +
                    "\tgetCellLayoutWidth(): 2198.0px (1099.0dp)\n"
            )
    }

    @Test
    fun tabletPortrait3Button() {
        initializeVarsForTablet(deviceSpecs["tablet"]!!, isGestureMode = false)
        val dp = getDeviceProfileForGrid("6_by_5")
        dp.isTaskbarPresentInApps = true

        assertThat(dump(dp))
            .isEqualTo(
                "DeviceProfile:\n" +
                    "\t1 dp = 2.0 px\n" +
                    "\tisTablet:true\n" +
                    "\tisPhone:false\n" +
                    "\ttransposeLayoutWithOrientation:false\n" +
                    "\tisGestureMode:false\n" +
                    "\tisLandscape:false\n" +
                    "\tisMultiWindowMode:false\n" +
                    "\tisTwoPanels:false\n" +
                    "\twindowX: 0.0px (0.0dp)\n" +
                    "\twindowY: 0.0px (0.0dp)\n" +
                    "\twidthPx: 1600.0px (800.0dp)\n" +
                    "\theightPx: 2560.0px (1280.0dp)\n" +
                    "\tavailableWidthPx: 1600.0px (800.0dp)\n" +
                    "\tavailableHeightPx: 2456.0px (1228.0dp)\n" +
                    "\tmInsets.left: 0.0px (0.0dp)\n" +
                    "\tmInsets.top: 104.0px (52.0dp)\n" +
                    "\tmInsets.right: 0.0px (0.0dp)\n" +
                    "\tmInsets.bottom: 0.0px (0.0dp)\n" +
                    "\taspectRatio:1.6\n" +
                    "\tisResponsiveGrid:false\n" +
                    "\tisScalableGrid:true\n" +
                    "\tinv.numRows: 5\n" +
                    "\tinv.numColumns: 6\n" +
                    "\tinv.numSearchContainerColumns: 3\n" +
                    "\tminCellSize: PointF(102.0, 120.0)dp\n" +
                    "\tcellWidthPx: 204.0px (102.0dp)\n" +
                    "\tcellHeightPx: 240.0px (120.0dp)\n" +
                    "\tgetCellSize().x: 204.0px (102.0dp)\n" +
                    "\tgetCellSize().y: 240.0px (120.0dp)\n" +
                    "\tcellLayoutBorderSpacePx Horizontal: 32.0px (16.0dp)\n" +
                    "\tcellLayoutBorderSpacePx Vertical: 128.0px (64.0dp)\n" +
                    "\tcellLayoutPaddingPx.left: 72.0px (36.0dp)\n" +
                    "\tcellLayoutPaddingPx.top: 72.0px (36.0dp)\n" +
                    "\tcellLayoutPaddingPx.right: 72.0px (36.0dp)\n" +
                    "\tcellLayoutPaddingPx.bottom: 72.0px (36.0dp)\n" +
                    "\ticonSizePx: 120.0px (60.0dp)\n" +
                    "\ticonTextSizePx: 28.0px (14.0dp)\n" +
                    "\ticonDrawablePaddingPx: 9.0px (4.5dp)\n" +
                    "\tinv.numFolderRows: 3\n" +
                    "\tinv.numFolderColumns: 3\n" +
                    "\tfolderCellWidthPx: 204.0px (102.0dp)\n" +
                    "\tfolderCellHeightPx: 240.0px (120.0dp)\n" +
                    "\tfolderChildIconSizePx: 120.0px (60.0dp)\n" +
                    "\tfolderChildTextSizePx: 28.0px (14.0dp)\n" +
                    "\tfolderChildDrawablePaddingPx: 22.0px (11.0dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.x: 0.0px (0.0dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.y: 0.0px (0.0dp)\n" +
                    "\tfolderContentPaddingLeftRight: 0.0px (0.0dp)\n" +
                    "\tfolderTopPadding: 48.0px (24.0dp)\n" +
                    "\tfolderFooterHeight: 112.0px (56.0dp)\n" +
                    "\tbottomSheetTopPadding: 704.0px (352.0dp)\n" +
                    "\tbottomSheetOpenDuration: 500\n" +
                    "\tbottomSheetCloseDuration: 500\n" +
                    "\tbottomSheetWorkspaceScale: 0.97\n" +
                    "\tbottomSheetDepth: 0.0\n" +
                    "\tallAppsShiftRange: 1810.0px (905.0dp)\n" +
                    "\tallAppsTopPadding: 750.0px (375.0dp)\n" +
                    "\tallAppsOpenDuration: 500\n" +
                    "\tallAppsCloseDuration: 500\n" +
                    "\tallAppsIconSizePx: 120.0px (60.0dp)\n" +
                    "\tallAppsIconTextSizePx: 28.0px (14.0dp)\n" +
                    "\tallAppsIconDrawablePaddingPx: 9.0px (4.5dp)\n" +
                    "\tallAppsCellHeightPx: 316.0px (158.0dp)\n" +
                    "\tallAppsCellWidthPx: 192.0px (96.0dp)\n" +
                    "\tallAppsBorderSpacePxX: 16.0px (8.0dp)\n" +
                    "\tallAppsBorderSpacePxY: 32.0px (16.0dp)\n" +
                    "\tnumShownAllAppsColumns: 6\n" +
                    "\tallAppsLeftRightPadding: 32.0px (16.0dp)\n" +
                    "\tallAppsLeftRightMargin: 152.0px (76.0dp)\n" +
                    "\thotseatBarSizePx: 272.0px (136.0dp)\n" +
                    "\tinv.hotseatColumnSpan: 6\n" +
                    "\thotseatCellHeightPx: 135.0px (67.5dp)\n" +
                    "\thotseatBarBottomSpacePx: 152.0px (76.0dp)\n" +
                    "\thotseatBarSidePaddingStartPx: 0.0px (0.0dp)\n" +
                    "\thotseatBarSidePaddingEndPx: 0.0px (0.0dp)\n" +
                    "\thotseatBarEndOffset: 0.0px (0.0dp)\n" +
                    "\thotseatQsbSpace: 0.0px (0.0dp)\n" +
                    "\thotseatQsbHeight: 0.0px (0.0dp)\n" +
                    "\tspringLoadedHotseatBarTopMarginPx: 216.0px (108.0dp)\n" +
                    "\tgetHotseatLayoutPadding(context).top: 0.0px (0.0dp)\n" +
                    "\tgetHotseatLayoutPadding(context).bottom: 137.0px (68.5dp)\n" +
                    "\tgetHotseatLayoutPadding(context).left: 150.0px (75.0dp)\n" +
                    "\tgetHotseatLayoutPadding(context).right: 150.0px (75.0dp)\n" +
                    "\tnumShownHotseatIcons: 6\n" +
                    "\thotseatBorderSpace: 116.0px (58.0dp)\n" +
                    "\tisQsbInline: false\n" +
                    "\thotseatQsbWidth: 1300.0px (650.0dp)\n" +
                    "\tisTaskbarPresent:false\n" +
                    "\tisTaskbarPresentInApps:true\n" +
                    "\ttaskbarHeight: 0.0px (0.0dp)\n" +
                    "\tstashedTaskbarHeight: 0.0px (0.0dp)\n" +
                    "\ttaskbarBottomMargin: 0.0px (0.0dp)\n" +
                    "\ttaskbarIconSize: 0.0px (0.0dp)\n" +
                    "\tdesiredWorkspaceHorizontalMarginPx: 108.0px (54.0dp)\n" +
                    "\tworkspacePadding.left: 36.0px (18.0dp)\n" +
                    "\tworkspacePadding.top: 132.0px (66.0dp)\n" +
                    "\tworkspacePadding.right: 36.0px (18.0dp)\n" +
                    "\tworkspacePadding.bottom: 468.0px (234.0dp)\n" +
                    "\ticonScale: 1.0px (0.5dp)\n" +
                    "\tcellScaleToFit : 1.0px (0.5dp)\n" +
                    "\textraSpace: 424.0px (212.0dp)\n" +
                    "\tunscaled extraSpace: 424.0px (212.0dp)\n" +
                    "\tmaxEmptySpace: 19998.0px (9999.0dp)\n" +
                    "\tworkspaceTopPadding: 204.0px (102.0dp)\n" +
                    "\tworkspaceBottomPadding: 220.0px (110.0dp)\n" +
                    "\toverviewTaskMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizeGridPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskThumbnailTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsHeight: 0.0px (0.0dp)\n" +
                    "\toverviewActionsClaimedSpaceBelow: 0.0px (0.0dp)\n" +
                    "\toverviewActionsButtonSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewPageSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewRowSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewGridSideMargin: 0.0px (0.0dp)\n" +
                    "\tdropTargetBarTopMarginPx: 220.0px (110.0dp)\n" +
                    "\tdropTargetBarSizePx: 144.0px (72.0dp)\n" +
                    "\tdropTargetBarBottomMarginPx: 96.0px (48.0dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkTop(): 564.0px (282.0dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkBottom(): 2072.0px (1036.0dp)\n" +
                    "\tworkspaceSpringLoadedMinNextPageVisiblePx: 48.0px (24.0dp)\n" +
                    "\tgetWorkspaceSpringLoadScale(): 0.8125px (0.40625dp)\n" +
                    "\tgetCellLayoutHeight(): 1856.0px (928.0dp)\n" +
                    "\tgetCellLayoutWidth(): 1528.0px (764.0dp)\n"
            )
    }

    @Test
    fun tabletPortrait() {
        initializeVarsForTablet(deviceSpecs["tablet"]!!)
        val dp = getDeviceProfileForGrid("6_by_5")
        dp.isTaskbarPresentInApps = true

        assertThat(dump(dp))
            .isEqualTo(
                "DeviceProfile:\n" +
                    "\t1 dp = 2.0 px\n" +
                    "\tisTablet:true\n" +
                    "\tisPhone:false\n" +
                    "\ttransposeLayoutWithOrientation:false\n" +
                    "\tisGestureMode:true\n" +
                    "\tisLandscape:false\n" +
                    "\tisMultiWindowMode:false\n" +
                    "\tisTwoPanels:false\n" +
                    "\twindowX: 0.0px (0.0dp)\n" +
                    "\twindowY: 0.0px (0.0dp)\n" +
                    "\twidthPx: 1600.0px (800.0dp)\n" +
                    "\theightPx: 2560.0px (1280.0dp)\n" +
                    "\tavailableWidthPx: 1600.0px (800.0dp)\n" +
                    "\tavailableHeightPx: 2456.0px (1228.0dp)\n" +
                    "\tmInsets.left: 0.0px (0.0dp)\n" +
                    "\tmInsets.top: 104.0px (52.0dp)\n" +
                    "\tmInsets.right: 0.0px (0.0dp)\n" +
                    "\tmInsets.bottom: 0.0px (0.0dp)\n" +
                    "\taspectRatio:1.6\n" +
                    "\tisResponsiveGrid:false\n" +
                    "\tisScalableGrid:true\n" +
                    "\tinv.numRows: 5\n" +
                    "\tinv.numColumns: 6\n" +
                    "\tinv.numSearchContainerColumns: 3\n" +
                    "\tminCellSize: PointF(102.0, 120.0)dp\n" +
                    "\tcellWidthPx: 204.0px (102.0dp)\n" +
                    "\tcellHeightPx: 240.0px (120.0dp)\n" +
                    "\tgetCellSize().x: 204.0px (102.0dp)\n" +
                    "\tgetCellSize().y: 240.0px (120.0dp)\n" +
                    "\tcellLayoutBorderSpacePx Horizontal: 32.0px (16.0dp)\n" +
                    "\tcellLayoutBorderSpacePx Vertical: 128.0px (64.0dp)\n" +
                    "\tcellLayoutPaddingPx.left: 72.0px (36.0dp)\n" +
                    "\tcellLayoutPaddingPx.top: 72.0px (36.0dp)\n" +
                    "\tcellLayoutPaddingPx.right: 72.0px (36.0dp)\n" +
                    "\tcellLayoutPaddingPx.bottom: 72.0px (36.0dp)\n" +
                    "\ticonSizePx: 120.0px (60.0dp)\n" +
                    "\ticonTextSizePx: 28.0px (14.0dp)\n" +
                    "\ticonDrawablePaddingPx: 9.0px (4.5dp)\n" +
                    "\tinv.numFolderRows: 3\n" +
                    "\tinv.numFolderColumns: 3\n" +
                    "\tfolderCellWidthPx: 204.0px (102.0dp)\n" +
                    "\tfolderCellHeightPx: 240.0px (120.0dp)\n" +
                    "\tfolderChildIconSizePx: 120.0px (60.0dp)\n" +
                    "\tfolderChildTextSizePx: 28.0px (14.0dp)\n" +
                    "\tfolderChildDrawablePaddingPx: 22.0px (11.0dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.x: 0.0px (0.0dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.y: 0.0px (0.0dp)\n" +
                    "\tfolderContentPaddingLeftRight: 0.0px (0.0dp)\n" +
                    "\tfolderTopPadding: 48.0px (24.0dp)\n" +
                    "\tfolderFooterHeight: 112.0px (56.0dp)\n" +
                    "\tbottomSheetTopPadding: 704.0px (352.0dp)\n" +
                    "\tbottomSheetOpenDuration: 500\n" +
                    "\tbottomSheetCloseDuration: 500\n" +
                    "\tbottomSheetWorkspaceScale: 0.97\n" +
                    "\tbottomSheetDepth: 0.0\n" +
                    "\tallAppsShiftRange: 1810.0px (905.0dp)\n" +
                    "\tallAppsTopPadding: 750.0px (375.0dp)\n" +
                    "\tallAppsOpenDuration: 500\n" +
                    "\tallAppsCloseDuration: 500\n" +
                    "\tallAppsIconSizePx: 120.0px (60.0dp)\n" +
                    "\tallAppsIconTextSizePx: 28.0px (14.0dp)\n" +
                    "\tallAppsIconDrawablePaddingPx: 9.0px (4.5dp)\n" +
                    "\tallAppsCellHeightPx: 316.0px (158.0dp)\n" +
                    "\tallAppsCellWidthPx: 192.0px (96.0dp)\n" +
                    "\tallAppsBorderSpacePxX: 16.0px (8.0dp)\n" +
                    "\tallAppsBorderSpacePxY: 32.0px (16.0dp)\n" +
                    "\tnumShownAllAppsColumns: 6\n" +
                    "\tallAppsLeftRightPadding: 32.0px (16.0dp)\n" +
                    "\tallAppsLeftRightMargin: 152.0px (76.0dp)\n" +
                    "\thotseatBarSizePx: 272.0px (136.0dp)\n" +
                    "\tinv.hotseatColumnSpan: 6\n" +
                    "\thotseatCellHeightPx: 135.0px (67.5dp)\n" +
                    "\thotseatBarBottomSpacePx: 152.0px (76.0dp)\n" +
                    "\thotseatBarSidePaddingStartPx: 0.0px (0.0dp)\n" +
                    "\thotseatBarSidePaddingEndPx: 0.0px (0.0dp)\n" +
                    "\thotseatBarEndOffset: 0.0px (0.0dp)\n" +
                    "\thotseatQsbSpace: 0.0px (0.0dp)\n" +
                    "\thotseatQsbHeight: 0.0px (0.0dp)\n" +
                    "\tspringLoadedHotseatBarTopMarginPx: 216.0px (108.0dp)\n" +
                    "\tgetHotseatLayoutPadding(context).top: 0.0px (0.0dp)\n" +
                    "\tgetHotseatLayoutPadding(context).bottom: 137.0px (68.5dp)\n" +
                    "\tgetHotseatLayoutPadding(context).left: 150.0px (75.0dp)\n" +
                    "\tgetHotseatLayoutPadding(context).right: 150.0px (75.0dp)\n" +
                    "\tnumShownHotseatIcons: 6\n" +
                    "\thotseatBorderSpace: 116.0px (58.0dp)\n" +
                    "\tisQsbInline: false\n" +
                    "\thotseatQsbWidth: 1300.0px (650.0dp)\n" +
                    "\tisTaskbarPresent:false\n" +
                    "\tisTaskbarPresentInApps:true\n" +
                    "\ttaskbarHeight: 0.0px (0.0dp)\n" +
                    "\tstashedTaskbarHeight: 0.0px (0.0dp)\n" +
                    "\ttaskbarBottomMargin: 0.0px (0.0dp)\n" +
                    "\ttaskbarIconSize: 0.0px (0.0dp)\n" +
                    "\tdesiredWorkspaceHorizontalMarginPx: 108.0px (54.0dp)\n" +
                    "\tworkspacePadding.left: 36.0px (18.0dp)\n" +
                    "\tworkspacePadding.top: 132.0px (66.0dp)\n" +
                    "\tworkspacePadding.right: 36.0px (18.0dp)\n" +
                    "\tworkspacePadding.bottom: 468.0px (234.0dp)\n" +
                    "\ticonScale: 1.0px (0.5dp)\n" +
                    "\tcellScaleToFit : 1.0px (0.5dp)\n" +
                    "\textraSpace: 424.0px (212.0dp)\n" +
                    "\tunscaled extraSpace: 424.0px (212.0dp)\n" +
                    "\tmaxEmptySpace: 19998.0px (9999.0dp)\n" +
                    "\tworkspaceTopPadding: 204.0px (102.0dp)\n" +
                    "\tworkspaceBottomPadding: 220.0px (110.0dp)\n" +
                    "\toverviewTaskMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizeGridPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskThumbnailTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsHeight: 0.0px (0.0dp)\n" +
                    "\toverviewActionsClaimedSpaceBelow: 0.0px (0.0dp)\n" +
                    "\toverviewActionsButtonSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewPageSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewRowSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewGridSideMargin: 0.0px (0.0dp)\n" +
                    "\tdropTargetBarTopMarginPx: 220.0px (110.0dp)\n" +
                    "\tdropTargetBarSizePx: 144.0px (72.0dp)\n" +
                    "\tdropTargetBarBottomMarginPx: 96.0px (48.0dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkTop(): 564.0px (282.0dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkBottom(): 2072.0px (1036.0dp)\n" +
                    "\tworkspaceSpringLoadedMinNextPageVisiblePx: 48.0px (24.0dp)\n" +
                    "\tgetWorkspaceSpringLoadScale(): 0.8125px (0.40625dp)\n" +
                    "\tgetCellLayoutHeight(): 1856.0px (928.0dp)\n" +
                    "\tgetCellLayoutWidth(): 1528.0px (764.0dp)\n"
            )
    }

    @Test
    fun twoPanelLandscape3Button() {
        initializeVarsForTwoPanel(
            deviceSpecs["twopanel-tablet"]!!,
            deviceSpecs["twopanel-phone"]!!,
            isLandscape = true,
            isGestureMode = false
        )
        val dp = getDeviceProfileForGrid("4_by_4")
        dp.isTaskbarPresentInApps = true

        assertThat(dump(dp))
            .isEqualTo(
                "DeviceProfile:\n" +
                    "\t1 dp = 2.625 px\n" +
                    "\tisTablet:true\n" +
                    "\tisPhone:false\n" +
                    "\ttransposeLayoutWithOrientation:false\n" +
                    "\tisGestureMode:false\n" +
                    "\tisLandscape:true\n" +
                    "\tisMultiWindowMode:false\n" +
                    "\tisTwoPanels:true\n" +
                    "\twindowX: 0.0px (0.0dp)\n" +
                    "\twindowY: 0.0px (0.0dp)\n" +
                    "\twidthPx: 2208.0px (841.1429dp)\n" +
                    "\theightPx: 1840.0px (700.9524dp)\n" +
                    "\tavailableWidthPx: 2208.0px (841.1429dp)\n" +
                    "\tavailableHeightPx: 1730.0px (659.0476dp)\n" +
                    "\tmInsets.left: 0.0px (0.0dp)\n" +
                    "\tmInsets.top: 110.0px (41.904762dp)\n" +
                    "\tmInsets.right: 0.0px (0.0dp)\n" +
                    "\tmInsets.bottom: 0.0px (0.0dp)\n" +
                    "\taspectRatio:1.2\n" +
                    "\tisResponsiveGrid:false\n" +
                    "\tisScalableGrid:false\n" +
                    "\tinv.numRows: 4\n" +
                    "\tinv.numColumns: 4\n" +
                    "\tinv.numSearchContainerColumns: 4\n" +
                    "\tminCellSize: PointF(0.0, 0.0)dp\n" +
                    "\tcellWidthPx: 154.0px (58.666668dp)\n" +
                    "\tcellHeightPx: 218.0px (83.04762dp)\n" +
                    "\tgetCellSize().x: 270.0px (102.85714dp)\n" +
                    "\tgetCellSize().y: 342.0px (130.28572dp)\n" +
                    "\tcellLayoutBorderSpacePx Horizontal: 0.0px (0.0dp)\n" +
                    "\tcellLayoutBorderSpacePx Vertical: 0.0px (0.0dp)\n" +
                    "\tcellLayoutPaddingPx.left: 0.0px (0.0dp)\n" +
                    "\tcellLayoutPaddingPx.top: 0.0px (0.0dp)\n" +
                    "\tcellLayoutPaddingPx.right: 0.0px (0.0dp)\n" +
                    "\tcellLayoutPaddingPx.bottom: 0.0px (0.0dp)\n" +
                    "\ticonSizePx: 141.0px (53.714287dp)\n" +
                    "\ticonTextSizePx: 34.0px (12.952381dp)\n" +
                    "\ticonDrawablePaddingPx: 13.0px (4.952381dp)\n" +
                    "\tinv.numFolderRows: 3\n" +
                    "\tinv.numFolderColumns: 4\n" +
                    "\tfolderCellWidthPx: 189.0px (72.0dp)\n" +
                    "\tfolderCellHeightPx: 219.0px (83.42857dp)\n" +
                    "\tfolderChildIconSizePx: 141.0px (53.714287dp)\n" +
                    "\tfolderChildTextSizePx: 34.0px (12.952381dp)\n" +
                    "\tfolderChildDrawablePaddingPx: 5.0px (1.9047619dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.x: 0.0px (0.0dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.y: 0.0px (0.0dp)\n" +
                    "\tfolderContentPaddingLeftRight: 21.0px (8.0dp)\n" +
                    "\tfolderTopPadding: 63.0px (24.0dp)\n" +
                    "\tfolderFooterHeight: 147.0px (56.0dp)\n" +
                    "\tbottomSheetTopPadding: 110.0px (41.904762dp)\n" +
                    "\tbottomSheetOpenDuration: 500\n" +
                    "\tbottomSheetCloseDuration: 500\n" +
                    "\tbottomSheetWorkspaceScale: 0.97\n" +
                    "\tbottomSheetDepth: 1.0\n" +
                    "\tallAppsShiftRange: 1730.0px (659.0476dp)\n" +
                    "\tallAppsTopPadding: 110.0px (41.904762dp)\n" +
                    "\tallAppsOpenDuration: 500\n" +
                    "\tallAppsCloseDuration: 500\n" +
                    "\tallAppsIconSizePx: 141.0px (53.714287dp)\n" +
                    "\tallAppsIconTextSizePx: 34.0px (12.952381dp)\n" +
                    "\tallAppsIconDrawablePaddingPx: 21.0px (8.0dp)\n" +
                    "\tallAppsCellHeightPx: 315.0px (120.0dp)\n" +
                    "\tallAppsCellWidthPx: 183.0px (69.71429dp)\n" +
                    "\tallAppsBorderSpacePxX: 42.0px (16.0dp)\n" +
                    "\tallAppsBorderSpacePxY: 42.0px (16.0dp)\n" +
                    "\tnumShownAllAppsColumns: 8\n" +
                    "\tallAppsLeftRightPadding: 42.0px (16.0dp)\n" +
                    "\tallAppsLeftRightMargin: 183.0px (69.71429dp)\n" +
                    "\thotseatBarSizePx: 267.0px (101.71429dp)\n" +
                    "\tinv.hotseatColumnSpan: 4\n" +
                    "\thotseatCellHeightPx: 159.0px (60.57143dp)\n" +
                    "\thotseatBarBottomSpacePx: 126.0px (48.0dp)\n" +
                    "\thotseatBarSidePaddingStartPx: 0.0px (0.0dp)\n" +
                    "\thotseatBarSidePaddingEndPx: 0.0px (0.0dp)\n" +
                    "\thotseatBarEndOffset: 0.0px (0.0dp)\n" +
                    "\thotseatQsbSpace: 0.0px (0.0dp)\n" +
                    "\thotseatQsbHeight: 0.0px (0.0dp)\n" +
                    "\tspringLoadedHotseatBarTopMarginPx: 116.0px (44.190475dp)\n" +
                    "\tgetHotseatLayoutPadding(context).top: 0.0px (0.0dp)\n" +
                    "\tgetHotseatLayoutPadding(context).bottom: 108.0px (41.142857dp)\n" +
                    "\tgetHotseatLayoutPadding(context).left: 113.0px (43.04762dp)\n" +
                    "\tgetHotseatLayoutPadding(context).right: 113.0px (43.04762dp)\n" +
                    "\tnumShownHotseatIcons: 6\n" +
                    "\thotseatBorderSpace: 0.0px (0.0dp)\n" +
                    "\tisQsbInline: false\n" +
                    "\thotseatQsbWidth: 0.0px (0.0dp)\n" +
                    "\tisTaskbarPresent:false\n" +
                    "\tisTaskbarPresentInApps:true\n" +
                    "\ttaskbarHeight: 0.0px (0.0dp)\n" +
                    "\tstashedTaskbarHeight: 0.0px (0.0dp)\n" +
                    "\ttaskbarBottomMargin: 0.0px (0.0dp)\n" +
                    "\ttaskbarIconSize: 0.0px (0.0dp)\n" +
                    "\tdesiredWorkspaceHorizontalMarginPx: 21.0px (8.0dp)\n" +
                    "\tworkspacePadding.left: 21.0px (8.0dp)\n" +
                    "\tworkspacePadding.top: 30.0px (11.428572dp)\n" +
                    "\tworkspacePadding.right: 21.0px (8.0dp)\n" +
                    "\tworkspacePadding.bottom: 330.0px (125.71429dp)\n" +
                    "\ticonScale: 1.0px (0.3809524dp)\n" +
                    "\tcellScaleToFit : 1.0px (0.3809524dp)\n" +
                    "\textraSpace: 498.0px (189.71428dp)\n" +
                    "\tunscaled extraSpace: 498.0px (189.71428dp)\n" +
                    "\tmaxEmptySpace: 0.0px (0.0dp)\n" +
                    "\tworkspaceTopPadding: 0.0px (0.0dp)\n" +
                    "\tworkspaceBottomPadding: 0.0px (0.0dp)\n" +
                    "\toverviewTaskMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizeGridPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskThumbnailTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsHeight: 0.0px (0.0dp)\n" +
                    "\toverviewActionsClaimedSpaceBelow: 0.0px (0.0dp)\n" +
                    "\toverviewActionsButtonSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewPageSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewRowSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewGridSideMargin: 0.0px (0.0dp)\n" +
                    "\tdropTargetBarTopMarginPx: 0.0px (0.0dp)\n" +
                    "\tdropTargetBarSizePx: 147.0px (56.0dp)\n" +
                    "\tdropTargetBarBottomMarginPx: 42.0px (16.0dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkTop(): 299.0px (113.90476dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkBottom(): 1457.0px (555.0476dp)\n" +
                    "\tworkspaceSpringLoadedMinNextPageVisiblePx: 63.0px (24.0dp)\n" +
                    "\tgetWorkspaceSpringLoadScale(): 0.8452555px (0.32200208dp)\n" +
                    "\tgetCellLayoutHeight(): 1370.0px (521.9048dp)\n" +
                    "\tgetCellLayoutWidth(): 1083.0px (412.57144dp)\n"
            )
    }

    @Test
    fun twoPanelLandscape() {
        initializeVarsForTwoPanel(
            deviceSpecs["twopanel-tablet"]!!,
            deviceSpecs["twopanel-phone"]!!,
            isLandscape = true
        )
        val dp = getDeviceProfileForGrid("4_by_4")
        dp.isTaskbarPresentInApps = true

        assertThat(dump(dp))
            .isEqualTo(
                "DeviceProfile:\n" +
                    "\t1 dp = 2.625 px\n" +
                    "\tisTablet:true\n" +
                    "\tisPhone:false\n" +
                    "\ttransposeLayoutWithOrientation:false\n" +
                    "\tisGestureMode:true\n" +
                    "\tisLandscape:true\n" +
                    "\tisMultiWindowMode:false\n" +
                    "\tisTwoPanels:true\n" +
                    "\twindowX: 0.0px (0.0dp)\n" +
                    "\twindowY: 0.0px (0.0dp)\n" +
                    "\twidthPx: 2208.0px (841.1429dp)\n" +
                    "\theightPx: 1840.0px (700.9524dp)\n" +
                    "\tavailableWidthPx: 2208.0px (841.1429dp)\n" +
                    "\tavailableHeightPx: 1730.0px (659.0476dp)\n" +
                    "\tmInsets.left: 0.0px (0.0dp)\n" +
                    "\tmInsets.top: 110.0px (41.904762dp)\n" +
                    "\tmInsets.right: 0.0px (0.0dp)\n" +
                    "\tmInsets.bottom: 0.0px (0.0dp)\n" +
                    "\taspectRatio:1.2\n" +
                    "\tisResponsiveGrid:false\n" +
                    "\tisScalableGrid:false\n" +
                    "\tinv.numRows: 4\n" +
                    "\tinv.numColumns: 4\n" +
                    "\tinv.numSearchContainerColumns: 4\n" +
                    "\tminCellSize: PointF(0.0, 0.0)dp\n" +
                    "\tcellWidthPx: 154.0px (58.666668dp)\n" +
                    "\tcellHeightPx: 218.0px (83.04762dp)\n" +
                    "\tgetCellSize().x: 270.0px (102.85714dp)\n" +
                    "\tgetCellSize().y: 342.0px (130.28572dp)\n" +
                    "\tcellLayoutBorderSpacePx Horizontal: 0.0px (0.0dp)\n" +
                    "\tcellLayoutBorderSpacePx Vertical: 0.0px (0.0dp)\n" +
                    "\tcellLayoutPaddingPx.left: 0.0px (0.0dp)\n" +
                    "\tcellLayoutPaddingPx.top: 0.0px (0.0dp)\n" +
                    "\tcellLayoutPaddingPx.right: 0.0px (0.0dp)\n" +
                    "\tcellLayoutPaddingPx.bottom: 0.0px (0.0dp)\n" +
                    "\ticonSizePx: 141.0px (53.714287dp)\n" +
                    "\ticonTextSizePx: 34.0px (12.952381dp)\n" +
                    "\ticonDrawablePaddingPx: 13.0px (4.952381dp)\n" +
                    "\tinv.numFolderRows: 3\n" +
                    "\tinv.numFolderColumns: 4\n" +
                    "\tfolderCellWidthPx: 189.0px (72.0dp)\n" +
                    "\tfolderCellHeightPx: 219.0px (83.42857dp)\n" +
                    "\tfolderChildIconSizePx: 141.0px (53.714287dp)\n" +
                    "\tfolderChildTextSizePx: 34.0px (12.952381dp)\n" +
                    "\tfolderChildDrawablePaddingPx: 5.0px (1.9047619dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.x: 0.0px (0.0dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.y: 0.0px (0.0dp)\n" +
                    "\tfolderContentPaddingLeftRight: 21.0px (8.0dp)\n" +
                    "\tfolderTopPadding: 63.0px (24.0dp)\n" +
                    "\tfolderFooterHeight: 147.0px (56.0dp)\n" +
                    "\tbottomSheetTopPadding: 110.0px (41.904762dp)\n" +
                    "\tbottomSheetOpenDuration: 500\n" +
                    "\tbottomSheetCloseDuration: 500\n" +
                    "\tbottomSheetWorkspaceScale: 0.97\n" +
                    "\tbottomSheetDepth: 1.0\n" +
                    "\tallAppsShiftRange: 1730.0px (659.0476dp)\n" +
                    "\tallAppsTopPadding: 110.0px (41.904762dp)\n" +
                    "\tallAppsOpenDuration: 500\n" +
                    "\tallAppsCloseDuration: 500\n" +
                    "\tallAppsIconSizePx: 141.0px (53.714287dp)\n" +
                    "\tallAppsIconTextSizePx: 34.0px (12.952381dp)\n" +
                    "\tallAppsIconDrawablePaddingPx: 21.0px (8.0dp)\n" +
                    "\tallAppsCellHeightPx: 315.0px (120.0dp)\n" +
                    "\tallAppsCellWidthPx: 183.0px (69.71429dp)\n" +
                    "\tallAppsBorderSpacePxX: 42.0px (16.0dp)\n" +
                    "\tallAppsBorderSpacePxY: 42.0px (16.0dp)\n" +
                    "\tnumShownAllAppsColumns: 8\n" +
                    "\tallAppsLeftRightPadding: 42.0px (16.0dp)\n" +
                    "\tallAppsLeftRightMargin: 183.0px (69.71429dp)\n" +
                    "\thotseatBarSizePx: 267.0px (101.71429dp)\n" +
                    "\tinv.hotseatColumnSpan: 4\n" +
                    "\thotseatCellHeightPx: 159.0px (60.57143dp)\n" +
                    "\thotseatBarBottomSpacePx: 126.0px (48.0dp)\n" +
                    "\thotseatBarSidePaddingStartPx: 0.0px (0.0dp)\n" +
                    "\thotseatBarSidePaddingEndPx: 0.0px (0.0dp)\n" +
                    "\thotseatBarEndOffset: 0.0px (0.0dp)\n" +
                    "\thotseatQsbSpace: 0.0px (0.0dp)\n" +
                    "\thotseatQsbHeight: 0.0px (0.0dp)\n" +
                    "\tspringLoadedHotseatBarTopMarginPx: 116.0px (44.190475dp)\n" +
                    "\tgetHotseatLayoutPadding(context).top: 0.0px (0.0dp)\n" +
                    "\tgetHotseatLayoutPadding(context).bottom: 108.0px (41.142857dp)\n" +
                    "\tgetHotseatLayoutPadding(context).left: 113.0px (43.04762dp)\n" +
                    "\tgetHotseatLayoutPadding(context).right: 113.0px (43.04762dp)\n" +
                    "\tnumShownHotseatIcons: 6\n" +
                    "\thotseatBorderSpace: 0.0px (0.0dp)\n" +
                    "\tisQsbInline: false\n" +
                    "\thotseatQsbWidth: 0.0px (0.0dp)\n" +
                    "\tisTaskbarPresent:false\n" +
                    "\tisTaskbarPresentInApps:true\n" +
                    "\ttaskbarHeight: 0.0px (0.0dp)\n" +
                    "\tstashedTaskbarHeight: 0.0px (0.0dp)\n" +
                    "\ttaskbarBottomMargin: 0.0px (0.0dp)\n" +
                    "\ttaskbarIconSize: 0.0px (0.0dp)\n" +
                    "\tdesiredWorkspaceHorizontalMarginPx: 21.0px (8.0dp)\n" +
                    "\tworkspacePadding.left: 21.0px (8.0dp)\n" +
                    "\tworkspacePadding.top: 30.0px (11.428572dp)\n" +
                    "\tworkspacePadding.right: 21.0px (8.0dp)\n" +
                    "\tworkspacePadding.bottom: 330.0px (125.71429dp)\n" +
                    "\ticonScale: 1.0px (0.3809524dp)\n" +
                    "\tcellScaleToFit : 1.0px (0.3809524dp)\n" +
                    "\textraSpace: 498.0px (189.71428dp)\n" +
                    "\tunscaled extraSpace: 498.0px (189.71428dp)\n" +
                    "\tmaxEmptySpace: 0.0px (0.0dp)\n" +
                    "\tworkspaceTopPadding: 0.0px (0.0dp)\n" +
                    "\tworkspaceBottomPadding: 0.0px (0.0dp)\n" +
                    "\toverviewTaskMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizeGridPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskThumbnailTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsHeight: 0.0px (0.0dp)\n" +
                    "\toverviewActionsClaimedSpaceBelow: 0.0px (0.0dp)\n" +
                    "\toverviewActionsButtonSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewPageSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewRowSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewGridSideMargin: 0.0px (0.0dp)\n" +
                    "\tdropTargetBarTopMarginPx: 0.0px (0.0dp)\n" +
                    "\tdropTargetBarSizePx: 147.0px (56.0dp)\n" +
                    "\tdropTargetBarBottomMarginPx: 42.0px (16.0dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkTop(): 299.0px (113.90476dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkBottom(): 1457.0px (555.0476dp)\n" +
                    "\tworkspaceSpringLoadedMinNextPageVisiblePx: 63.0px (24.0dp)\n" +
                    "\tgetWorkspaceSpringLoadScale(): 0.8452555px (0.32200208dp)\n" +
                    "\tgetCellLayoutHeight(): 1370.0px (521.9048dp)\n" +
                    "\tgetCellLayoutWidth(): 1083.0px (412.57144dp)\n"
            )
    }

    @Test
    fun twoPanelPortrait3Button() {
        initializeVarsForTwoPanel(
            deviceSpecs["twopanel-tablet"]!!,
            deviceSpecs["twopanel-phone"]!!,
            isGestureMode = false
        )
        val dp = getDeviceProfileForGrid("4_by_4")
        dp.isTaskbarPresentInApps = true

        assertThat(dump(dp))
            .isEqualTo(
                "DeviceProfile:\n" +
                    "\t1 dp = 2.625 px\n" +
                    "\tisTablet:true\n" +
                    "\tisPhone:false\n" +
                    "\ttransposeLayoutWithOrientation:false\n" +
                    "\tisGestureMode:false\n" +
                    "\tisLandscape:false\n" +
                    "\tisMultiWindowMode:false\n" +
                    "\tisTwoPanels:true\n" +
                    "\twindowX: 0.0px (0.0dp)\n" +
                    "\twindowY: 0.0px (0.0dp)\n" +
                    "\twidthPx: 1840.0px (700.9524dp)\n" +
                    "\theightPx: 2208.0px (841.1429dp)\n" +
                    "\tavailableWidthPx: 1840.0px (700.9524dp)\n" +
                    "\tavailableHeightPx: 2075.0px (790.4762dp)\n" +
                    "\tmInsets.left: 0.0px (0.0dp)\n" +
                    "\tmInsets.top: 133.0px (50.666668dp)\n" +
                    "\tmInsets.right: 0.0px (0.0dp)\n" +
                    "\tmInsets.bottom: 0.0px (0.0dp)\n" +
                    "\taspectRatio:1.2\n" +
                    "\tisResponsiveGrid:false\n" +
                    "\tisScalableGrid:false\n" +
                    "\tinv.numRows: 4\n" +
                    "\tinv.numColumns: 4\n" +
                    "\tinv.numSearchContainerColumns: 4\n" +
                    "\tminCellSize: PointF(0.0, 0.0)dp\n" +
                    "\tcellWidthPx: 154.0px (58.666668dp)\n" +
                    "\tcellHeightPx: 218.0px (83.04762dp)\n" +
                    "\tgetCellSize().x: 224.0px (85.333336dp)\n" +
                    "\tgetCellSize().y: 430.0px (163.80952dp)\n" +
                    "\tcellLayoutBorderSpacePx Horizontal: 0.0px (0.0dp)\n" +
                    "\tcellLayoutBorderSpacePx Vertical: 0.0px (0.0dp)\n" +
                    "\tcellLayoutPaddingPx.left: 0.0px (0.0dp)\n" +
                    "\tcellLayoutPaddingPx.top: 0.0px (0.0dp)\n" +
                    "\tcellLayoutPaddingPx.right: 0.0px (0.0dp)\n" +
                    "\tcellLayoutPaddingPx.bottom: 0.0px (0.0dp)\n" +
                    "\ticonSizePx: 141.0px (53.714287dp)\n" +
                    "\ticonTextSizePx: 34.0px (12.952381dp)\n" +
                    "\ticonDrawablePaddingPx: 13.0px (4.952381dp)\n" +
                    "\tinv.numFolderRows: 3\n" +
                    "\tinv.numFolderColumns: 4\n" +
                    "\tfolderCellWidthPx: 189.0px (72.0dp)\n" +
                    "\tfolderCellHeightPx: 219.0px (83.42857dp)\n" +
                    "\tfolderChildIconSizePx: 141.0px (53.714287dp)\n" +
                    "\tfolderChildTextSizePx: 34.0px (12.952381dp)\n" +
                    "\tfolderChildDrawablePaddingPx: 5.0px (1.9047619dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.x: 0.0px (0.0dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.y: 0.0px (0.0dp)\n" +
                    "\tfolderContentPaddingLeftRight: 21.0px (8.0dp)\n" +
                    "\tfolderTopPadding: 63.0px (24.0dp)\n" +
                    "\tfolderFooterHeight: 147.0px (56.0dp)\n" +
                    "\tbottomSheetTopPadding: 133.0px (50.666668dp)\n" +
                    "\tbottomSheetOpenDuration: 500\n" +
                    "\tbottomSheetCloseDuration: 500\n" +
                    "\tbottomSheetWorkspaceScale: 0.97\n" +
                    "\tbottomSheetDepth: 1.0\n" +
                    "\tallAppsShiftRange: 1826.0px (695.619dp)\n" +
                    "\tallAppsTopPadding: 382.0px (145.5238dp)\n" +
                    "\tallAppsOpenDuration: 500\n" +
                    "\tallAppsCloseDuration: 500\n" +
                    "\tallAppsIconSizePx: 141.0px (53.714287dp)\n" +
                    "\tallAppsIconTextSizePx: 34.0px (12.952381dp)\n" +
                    "\tallAppsIconDrawablePaddingPx: 21.0px (8.0dp)\n" +
                    "\tallAppsCellHeightPx: 315.0px (120.0dp)\n" +
                    "\tallAppsCellWidthPx: 183.0px (69.71429dp)\n" +
                    "\tallAppsBorderSpacePxX: 42.0px (16.0dp)\n" +
                    "\tallAppsBorderSpacePxY: 42.0px (16.0dp)\n" +
                    "\tnumShownAllAppsColumns: 8\n" +
                    "\tallAppsLeftRightPadding: 42.0px (16.0dp)\n" +
                    "\tallAppsLeftRightMargin: 1.0px (0.3809524dp)\n" +
                    "\thotseatBarSizePx: 267.0px (101.71429dp)\n" +
                    "\tinv.hotseatColumnSpan: 4\n" +
                    "\thotseatCellHeightPx: 159.0px (60.57143dp)\n" +
                    "\thotseatBarBottomSpacePx: 126.0px (48.0dp)\n" +
                    "\thotseatBarSidePaddingStartPx: 0.0px (0.0dp)\n" +
                    "\thotseatBarSidePaddingEndPx: 0.0px (0.0dp)\n" +
                    "\thotseatBarEndOffset: 0.0px (0.0dp)\n" +
                    "\thotseatQsbSpace: 0.0px (0.0dp)\n" +
                    "\thotseatQsbHeight: 0.0px (0.0dp)\n" +
                    "\tspringLoadedHotseatBarTopMarginPx: 171.0px (65.14286dp)\n" +
                    "\tgetHotseatLayoutPadding(context).top: 0.0px (0.0dp)\n" +
                    "\tgetHotseatLayoutPadding(context).bottom: 108.0px (41.142857dp)\n" +
                    "\tgetHotseatLayoutPadding(context).left: 98.0px (37.333332dp)\n" +
                    "\tgetHotseatLayoutPadding(context).right: 98.0px (37.333332dp)\n" +
                    "\tnumShownHotseatIcons: 6\n" +
                    "\thotseatBorderSpace: 0.0px (0.0dp)\n" +
                    "\tisQsbInline: false\n" +
                    "\thotseatQsbWidth: 0.0px (0.0dp)\n" +
                    "\tisTaskbarPresent:false\n" +
                    "\tisTaskbarPresentInApps:true\n" +
                    "\ttaskbarHeight: 0.0px (0.0dp)\n" +
                    "\tstashedTaskbarHeight: 0.0px (0.0dp)\n" +
                    "\ttaskbarBottomMargin: 0.0px (0.0dp)\n" +
                    "\ttaskbarIconSize: 0.0px (0.0dp)\n" +
                    "\tdesiredWorkspaceHorizontalMarginPx: 21.0px (8.0dp)\n" +
                    "\tworkspacePadding.left: 21.0px (8.0dp)\n" +
                    "\tworkspacePadding.top: 24.0px (9.142858dp)\n" +
                    "\tworkspacePadding.right: 21.0px (8.0dp)\n" +
                    "\tworkspacePadding.bottom: 330.0px (125.71429dp)\n" +
                    "\ticonScale: 1.0px (0.3809524dp)\n" +
                    "\tcellScaleToFit : 1.0px (0.3809524dp)\n" +
                    "\textraSpace: 849.0px (323.42856dp)\n" +
                    "\tunscaled extraSpace: 849.0px (323.42856dp)\n" +
                    "\tmaxEmptySpace: 0.0px (0.0dp)\n" +
                    "\tworkspaceTopPadding: 0.0px (0.0dp)\n" +
                    "\tworkspaceBottomPadding: 0.0px (0.0dp)\n" +
                    "\toverviewTaskMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizeGridPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskThumbnailTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsHeight: 0.0px (0.0dp)\n" +
                    "\toverviewActionsClaimedSpaceBelow: 0.0px (0.0dp)\n" +
                    "\toverviewActionsButtonSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewPageSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewRowSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewGridSideMargin: 0.0px (0.0dp)\n" +
                    "\tdropTargetBarTopMarginPx: 168.0px (64.0dp)\n" +
                    "\tdropTargetBarSizePx: 147.0px (56.0dp)\n" +
                    "\tdropTargetBarBottomMarginPx: 42.0px (16.0dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkTop(): 490.0px (186.66667dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkBottom(): 1770.0px (674.2857dp)\n" +
                    "\tworkspaceSpringLoadedMinNextPageVisiblePx: 63.0px (24.0dp)\n" +
                    "\tgetWorkspaceSpringLoadScale(): 0.7437536px (0.2833347dp)\n" +
                    "\tgetCellLayoutHeight(): 1721.0px (655.619dp)\n" +
                    "\tgetCellLayoutWidth(): 899.0px (342.4762dp)\n"
            )
    }

    @Test
    fun twoPanelPortrait() {
        initializeVarsForTwoPanel(deviceSpecs["twopanel-tablet"]!!, deviceSpecs["twopanel-phone"]!!)
        val dp = getDeviceProfileForGrid("4_by_4")
        dp.isTaskbarPresentInApps = true

        assertThat(dump(dp))
            .isEqualTo(
                "DeviceProfile:\n" +
                    "\t1 dp = 2.625 px\n" +
                    "\tisTablet:true\n" +
                    "\tisPhone:false\n" +
                    "\ttransposeLayoutWithOrientation:false\n" +
                    "\tisGestureMode:true\n" +
                    "\tisLandscape:false\n" +
                    "\tisMultiWindowMode:false\n" +
                    "\tisTwoPanels:true\n" +
                    "\twindowX: 0.0px (0.0dp)\n" +
                    "\twindowY: 0.0px (0.0dp)\n" +
                    "\twidthPx: 1840.0px (700.9524dp)\n" +
                    "\theightPx: 2208.0px (841.1429dp)\n" +
                    "\tavailableWidthPx: 1840.0px (700.9524dp)\n" +
                    "\tavailableHeightPx: 2075.0px (790.4762dp)\n" +
                    "\tmInsets.left: 0.0px (0.0dp)\n" +
                    "\tmInsets.top: 133.0px (50.666668dp)\n" +
                    "\tmInsets.right: 0.0px (0.0dp)\n" +
                    "\tmInsets.bottom: 0.0px (0.0dp)\n" +
                    "\taspectRatio:1.2\n" +
                    "\tisResponsiveGrid:false\n" +
                    "\tisScalableGrid:false\n" +
                    "\tinv.numRows: 4\n" +
                    "\tinv.numColumns: 4\n" +
                    "\tinv.numSearchContainerColumns: 4\n" +
                    "\tminCellSize: PointF(0.0, 0.0)dp\n" +
                    "\tcellWidthPx: 154.0px (58.666668dp)\n" +
                    "\tcellHeightPx: 218.0px (83.04762dp)\n" +
                    "\tgetCellSize().x: 224.0px (85.333336dp)\n" +
                    "\tgetCellSize().y: 430.0px (163.80952dp)\n" +
                    "\tcellLayoutBorderSpacePx Horizontal: 0.0px (0.0dp)\n" +
                    "\tcellLayoutBorderSpacePx Vertical: 0.0px (0.0dp)\n" +
                    "\tcellLayoutPaddingPx.left: 0.0px (0.0dp)\n" +
                    "\tcellLayoutPaddingPx.top: 0.0px (0.0dp)\n" +
                    "\tcellLayoutPaddingPx.right: 0.0px (0.0dp)\n" +
                    "\tcellLayoutPaddingPx.bottom: 0.0px (0.0dp)\n" +
                    "\ticonSizePx: 141.0px (53.714287dp)\n" +
                    "\ticonTextSizePx: 34.0px (12.952381dp)\n" +
                    "\ticonDrawablePaddingPx: 13.0px (4.952381dp)\n" +
                    "\tinv.numFolderRows: 3\n" +
                    "\tinv.numFolderColumns: 4\n" +
                    "\tfolderCellWidthPx: 189.0px (72.0dp)\n" +
                    "\tfolderCellHeightPx: 219.0px (83.42857dp)\n" +
                    "\tfolderChildIconSizePx: 141.0px (53.714287dp)\n" +
                    "\tfolderChildTextSizePx: 34.0px (12.952381dp)\n" +
                    "\tfolderChildDrawablePaddingPx: 5.0px (1.9047619dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.x: 0.0px (0.0dp)\n" +
                    "\tfolderCellLayoutBorderSpacePx.y: 0.0px (0.0dp)\n" +
                    "\tfolderContentPaddingLeftRight: 21.0px (8.0dp)\n" +
                    "\tfolderTopPadding: 63.0px (24.0dp)\n" +
                    "\tfolderFooterHeight: 147.0px (56.0dp)\n" +
                    "\tbottomSheetTopPadding: 133.0px (50.666668dp)\n" +
                    "\tbottomSheetOpenDuration: 500\n" +
                    "\tbottomSheetCloseDuration: 500\n" +
                    "\tbottomSheetWorkspaceScale: 0.97\n" +
                    "\tbottomSheetDepth: 1.0\n" +
                    "\tallAppsShiftRange: 1826.0px (695.619dp)\n" +
                    "\tallAppsTopPadding: 382.0px (145.5238dp)\n" +
                    "\tallAppsOpenDuration: 500\n" +
                    "\tallAppsCloseDuration: 500\n" +
                    "\tallAppsIconSizePx: 141.0px (53.714287dp)\n" +
                    "\tallAppsIconTextSizePx: 34.0px (12.952381dp)\n" +
                    "\tallAppsIconDrawablePaddingPx: 21.0px (8.0dp)\n" +
                    "\tallAppsCellHeightPx: 315.0px (120.0dp)\n" +
                    "\tallAppsCellWidthPx: 183.0px (69.71429dp)\n" +
                    "\tallAppsBorderSpacePxX: 42.0px (16.0dp)\n" +
                    "\tallAppsBorderSpacePxY: 42.0px (16.0dp)\n" +
                    "\tnumShownAllAppsColumns: 8\n" +
                    "\tallAppsLeftRightPadding: 42.0px (16.0dp)\n" +
                    "\tallAppsLeftRightMargin: 1.0px (0.3809524dp)\n" +
                    "\thotseatBarSizePx: 267.0px (101.71429dp)\n" +
                    "\tinv.hotseatColumnSpan: 4\n" +
                    "\thotseatCellHeightPx: 159.0px (60.57143dp)\n" +
                    "\thotseatBarBottomSpacePx: 126.0px (48.0dp)\n" +
                    "\thotseatBarSidePaddingStartPx: 0.0px (0.0dp)\n" +
                    "\thotseatBarSidePaddingEndPx: 0.0px (0.0dp)\n" +
                    "\thotseatBarEndOffset: 0.0px (0.0dp)\n" +
                    "\thotseatQsbSpace: 0.0px (0.0dp)\n" +
                    "\thotseatQsbHeight: 0.0px (0.0dp)\n" +
                    "\tspringLoadedHotseatBarTopMarginPx: 171.0px (65.14286dp)\n" +
                    "\tgetHotseatLayoutPadding(context).top: 0.0px (0.0dp)\n" +
                    "\tgetHotseatLayoutPadding(context).bottom: 108.0px (41.142857dp)\n" +
                    "\tgetHotseatLayoutPadding(context).left: 98.0px (37.333332dp)\n" +
                    "\tgetHotseatLayoutPadding(context).right: 98.0px (37.333332dp)\n" +
                    "\tnumShownHotseatIcons: 6\n" +
                    "\thotseatBorderSpace: 0.0px (0.0dp)\n" +
                    "\tisQsbInline: false\n" +
                    "\thotseatQsbWidth: 0.0px (0.0dp)\n" +
                    "\tisTaskbarPresent:false\n" +
                    "\tisTaskbarPresentInApps:true\n" +
                    "\ttaskbarHeight: 0.0px (0.0dp)\n" +
                    "\tstashedTaskbarHeight: 0.0px (0.0dp)\n" +
                    "\ttaskbarBottomMargin: 0.0px (0.0dp)\n" +
                    "\ttaskbarIconSize: 0.0px (0.0dp)\n" +
                    "\tdesiredWorkspaceHorizontalMarginPx: 21.0px (8.0dp)\n" +
                    "\tworkspacePadding.left: 21.0px (8.0dp)\n" +
                    "\tworkspacePadding.top: 24.0px (9.142858dp)\n" +
                    "\tworkspacePadding.right: 21.0px (8.0dp)\n" +
                    "\tworkspacePadding.bottom: 330.0px (125.71429dp)\n" +
                    "\ticonScale: 1.0px (0.3809524dp)\n" +
                    "\tcellScaleToFit : 1.0px (0.3809524dp)\n" +
                    "\textraSpace: 849.0px (323.42856dp)\n" +
                    "\tunscaled extraSpace: 849.0px (323.42856dp)\n" +
                    "\tmaxEmptySpace: 0.0px (0.0dp)\n" +
                    "\tworkspaceTopPadding: 0.0px (0.0dp)\n" +
                    "\tworkspaceBottomPadding: 0.0px (0.0dp)\n" +
                    "\toverviewTaskMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizePx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskIconDrawableSizeGridPx: 0.0px (0.0dp)\n" +
                    "\toverviewTaskThumbnailTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsTopMarginPx: 0.0px (0.0dp)\n" +
                    "\toverviewActionsHeight: 0.0px (0.0dp)\n" +
                    "\toverviewActionsClaimedSpaceBelow: 0.0px (0.0dp)\n" +
                    "\toverviewActionsButtonSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewPageSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewRowSpacing: 0.0px (0.0dp)\n" +
                    "\toverviewGridSideMargin: 0.0px (0.0dp)\n" +
                    "\tdropTargetBarTopMarginPx: 168.0px (64.0dp)\n" +
                    "\tdropTargetBarSizePx: 147.0px (56.0dp)\n" +
                    "\tdropTargetBarBottomMarginPx: 42.0px (16.0dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkTop(): 490.0px (186.66667dp)\n" +
                    "\tgetCellLayoutSpringLoadShrunkBottom(): 1770.0px (674.2857dp)\n" +
                    "\tworkspaceSpringLoadedMinNextPageVisiblePx: 63.0px (24.0dp)\n" +
                    "\tgetWorkspaceSpringLoadScale(): 0.7437536px (0.2833347dp)\n" +
                    "\tgetCellLayoutHeight(): 1721.0px (655.619dp)\n" +
                    "\tgetCellLayoutWidth(): 899.0px (342.4762dp)\n"
            )
    }

    private fun getDeviceProfileForGrid(gridName: String): DeviceProfile {
        return InvariantDeviceProfile(context, gridName).getDeviceProfile(context)
    }

    private fun dump(dp: DeviceProfile): String {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        dp.dump(context, "", printWriter)
        printWriter.flush()
        return stringWriter.toString()
    }
}
