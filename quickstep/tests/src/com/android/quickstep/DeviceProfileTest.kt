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
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for DeviceProfile.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceProfileTest : DeviceProfileBaseTest() {

    @Test
    fun phonePortrait3Button() {
        initializeVarsForPhone(isGestureMode = false)
        val dp = newDP()

        assertThat(dump(dp)).isEqualTo("DeviceProfile:\n" +
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
                "\tisScalableGrid:true\n" +
                "\tinv.numRows: 5\n" +
                "\tinv.numColumns: 4\n" +
                "\tinv.numSearchContainerColumns: 4\n" +
                "\tminCellSize: PointF(80.0, 104.0)dp\n" +
                "\tcellWidthPx: 210.0px (80.0dp)\n" +
                "\tcellHeightPx: 272.0px (103.61905dp)\n" +
                "\tgetCellSize().x: 210.0px (80.0dp)\n" +
                "\tgetCellSize().y: 272.0px (103.61905dp)\n" +
                "\tcellLayoutBorderSpacePx Horizontal: 42.0px (16.0dp)\n" +
                "\tcellLayoutBorderSpacePx Vertical: 42.0px (16.0dp)\n" +
                "\tcellLayoutPaddingPx.left: 28.0px (10.666667dp)\n" +
                "\tcellLayoutPaddingPx.top: 28.0px (10.666667dp)\n" +
                "\tcellLayoutPaddingPx.right: 28.0px (10.666667dp)\n" +
                "\tcellLayoutPaddingPx.bottom: 28.0px (10.666667dp)\n" +
                "\ticonSizePx: 157.0px (59.809525dp)\n" +
                "\ticonTextSizePx: 36.0px (13.714286dp)\n" +
                "\ticonDrawablePaddingPx: 17.0px (6.4761906dp)\n" +
                "\tfolderCellWidthPx: 210.0px (80.0dp)\n" +
                "\tfolderCellHeightPx: 247.0px (94.09524dp)\n" +
                "\tfolderChildIconSizePx: 158.0px (60.190475dp)\n" +
                "\tfolderChildTextSizePx: 37.0px (14.095238dp)\n" +
                "\tfolderChildDrawablePaddingPx: 13.0px (4.952381dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Horizontal: 42.0px (16.0dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Vertical: 42.0px (16.0dp)\n" +
                "\tfolderContentPaddingLeftRight: 42.0px (16.0dp)\n" +
                "\tfolderTopPadding: 63.0px (24.0dp)\n" +
                "\tbottomSheetTopPadding: 146.0px (55.61905dp)\n" +
                "\tallAppsShiftRange: 788.0px (300.1905dp)\n" +
                "\tallAppsTopPadding: 0.0px (0.0dp)\n" +
                "\tallAppsIconSizePx: 157.0px (59.809525dp)\n" +
                "\tallAppsIconTextSizePx: 37.0px (14.095238dp)\n" +
                "\tallAppsIconDrawablePaddingPx: 18.0px (6.857143dp)\n" +
                "\tallAppsCellHeightPx: 314.0px (119.61905dp)\n" +
                "\tallAppsCellWidthPx: 210.0px (80.0dp)\n" +
                "\tallAppsBorderSpacePxX: 42.0px (16.0dp)\n" +
                "\tallAppsBorderSpacePxY: 42.0px (16.0dp)\n" +
                "\tnumShownAllAppsColumns: 4\n" +
                "\tallAppsLeftRightPadding: 57.0px (21.714285dp)\n" +
                "\tallAppsLeftRightMargin: 0.0px (0.0dp)\n" +
                "\thotseatBarSizePx: 511.0px (194.66667dp)\n" +
                "\tinv.hotseatColumnSpan: 4\n" +
                "\thotseatCellHeightPx: 177.0px (67.42857dp)\n" +
                "\thotseatBarBottomSpacePx: 147.0px (56.0dp)\n" +
                "\thotseatBarSidePaddingStartPx: 0.0px (0.0dp)\n" +
                "\thotseatBarSidePaddingEndPx: 0.0px (0.0dp)\n" +
                "\thotseatBarEndOffset: 0.0px (0.0dp)\n" +
                "\thotseatQsbSpace: 74.0px (28.190475dp)\n" +
                "\thotseatQsbHeight: 165.0px (62.857143dp)\n" +
                "\tspringLoadedHotseatBarTopMarginPx: 200.0px (76.190475dp)\n" +
                "\tgetHotseatLayoutPadding(context).top: 0.0px (0.0dp)\n" +
                "\tgetHotseatLayoutPadding(context).bottom: 334.0px (127.2381dp)\n" +
                "\tgetHotseatLayoutPadding(context).left: 83.0px (31.619047dp)\n" +
                "\tgetHotseatLayoutPadding(context).right: 83.0px (31.619047dp)\n" +
                "\tnumShownHotseatIcons: 4\n" +
                "\thotseatBorderSpace: 95.0px (36.190475dp)\n" +
                "\tisQsbInline: false\n" +
                "\thotseatQsbWidth: 913.0px (347.8095dp)\n" +
                "\tisTaskbarPresent:false\n" +
                "\tisTaskbarPresentInApps:false\n" +
                "\ttaskbarSize: 0.0px (0.0dp)\n" +
                "\tdesiredWorkspaceHorizontalMarginPx: 57.0px (21.714285dp)\n" +
                "\tworkspacePadding.left: 29.0px (11.047619dp)\n" +
                "\tworkspacePadding.top: 67.0px (25.52381dp)\n" +
                "\tworkspacePadding.right: 29.0px (11.047619dp)\n" +
                "\tworkspacePadding.bottom: 504.0px (192.0dp)\n" +
                "\ticonScale: 0.9981516px (0.38024822dp)\n" +
                "\tcellScaleToFit : 0.9981516px (0.38024822dp)\n" +
                "\textraSpace: 211.0px (80.38095dp)\n" +
                "\tunscaled extraSpace: 211.39073px (80.5298dp)\n" +
                "\tmaxEmptySpace: 315.0px (120.0dp)\n" +
                "\tworkspaceTopPadding: 95.0px (36.190475dp)\n" +
                "\tworkspaceBottomPadding: 116.0px (44.190475dp)\n" +
                "\toverviewTaskMarginPx: 42.0px (16.0dp)\n" +
                "\toverviewTaskIconSizePx: 126.0px (48.0dp)\n" +
                "\toverviewTaskIconDrawableSizePx: 116.0px (44.190475dp)\n" +
                "\toverviewTaskIconDrawableSizeGridPx: 0.0px (0.0dp)\n" +
                "\toverviewTaskThumbnailTopMarginPx: 168.0px (64.0dp)\n" +
                "\toverviewActionsTopMarginPx: 63.0px (24.0dp)\n" +
                "\toverviewActionsHeight: 126.0px (48.0dp)\n" +
                "\toverviewActionsButtonSpacing: 95.0px (36.190475dp)\n" +
                "\toverviewPageSpacing: 42.0px (16.0dp)\n" +
                "\toverviewRowSpacing: 0.0px (0.0dp)\n" +
                "\toverviewGridSideMargin: 0.0px (0.0dp)\n" +
                "\tdropTargetBarTopMarginPx: 84.0px (32.0dp)\n" +
                "\tdropTargetBarSizePx: 147.0px (56.0dp)\n" +
                "\tdropTargetBarBottomMarginPx: 42.0px (16.0dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkTop(): 391.0px (148.95238dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkBottom(): 1689.0px (643.4286dp)\n" +
                "\tworkspaceSpringLoadedMinNextPageVisiblePx: 63.0px (24.0dp)\n" +
                "\tgetWorkspaceSpringLoadScale(): 0.81892747px (0.31197238dp)\n" +
                "\tgetCellLayoutHeight(): 1585.0px (603.8095dp)\n" +
                "\tgetCellLayoutWidth(): 1022.0px (389.33334dp)\n")
    }

    @Test
    fun phonePortrait() {
        initializeVarsForPhone()
        val dp = newDP()

        assertThat(dump(dp)).isEqualTo("DeviceProfile:\n" +
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
                "\tisScalableGrid:true\n" +
                "\tinv.numRows: 5\n" +
                "\tinv.numColumns: 4\n" +
                "\tinv.numSearchContainerColumns: 4\n" +
                "\tminCellSize: PointF(80.0, 104.0)dp\n" +
                "\tcellWidthPx: 210.0px (80.0dp)\n" +
                "\tcellHeightPx: 272.0px (103.61905dp)\n" +
                "\tgetCellSize().x: 210.0px (80.0dp)\n" +
                "\tgetCellSize().y: 272.0px (103.61905dp)\n" +
                "\tcellLayoutBorderSpacePx Horizontal: 42.0px (16.0dp)\n" +
                "\tcellLayoutBorderSpacePx Vertical: 42.0px (16.0dp)\n" +
                "\tcellLayoutPaddingPx.left: 28.0px (10.666667dp)\n" +
                "\tcellLayoutPaddingPx.top: 28.0px (10.666667dp)\n" +
                "\tcellLayoutPaddingPx.right: 28.0px (10.666667dp)\n" +
                "\tcellLayoutPaddingPx.bottom: 28.0px (10.666667dp)\n" +
                "\ticonSizePx: 157.0px (59.809525dp)\n" +
                "\ticonTextSizePx: 36.0px (13.714286dp)\n" +
                "\ticonDrawablePaddingPx: 17.0px (6.4761906dp)\n" +
                "\tfolderCellWidthPx: 210.0px (80.0dp)\n" +
                "\tfolderCellHeightPx: 247.0px (94.09524dp)\n" +
                "\tfolderChildIconSizePx: 158.0px (60.190475dp)\n" +
                "\tfolderChildTextSizePx: 37.0px (14.095238dp)\n" +
                "\tfolderChildDrawablePaddingPx: 13.0px (4.952381dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Horizontal: 42.0px (16.0dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Vertical: 42.0px (16.0dp)\n" +
                "\tfolderContentPaddingLeftRight: 42.0px (16.0dp)\n" +
                "\tfolderTopPadding: 63.0px (24.0dp)\n" +
                "\tbottomSheetTopPadding: 146.0px (55.61905dp)\n" +
                "\tallAppsShiftRange: 788.0px (300.1905dp)\n" +
                "\tallAppsTopPadding: 0.0px (0.0dp)\n" +
                "\tallAppsIconSizePx: 157.0px (59.809525dp)\n" +
                "\tallAppsIconTextSizePx: 37.0px (14.095238dp)\n" +
                "\tallAppsIconDrawablePaddingPx: 18.0px (6.857143dp)\n" +
                "\tallAppsCellHeightPx: 314.0px (119.61905dp)\n" +
                "\tallAppsCellWidthPx: 210.0px (80.0dp)\n" +
                "\tallAppsBorderSpacePxX: 42.0px (16.0dp)\n" +
                "\tallAppsBorderSpacePxY: 42.0px (16.0dp)\n" +
                "\tnumShownAllAppsColumns: 4\n" +
                "\tallAppsLeftRightPadding: 57.0px (21.714285dp)\n" +
                "\tallAppsLeftRightMargin: 0.0px (0.0dp)\n" +
                "\thotseatBarSizePx: 511.0px (194.66667dp)\n" +
                "\tinv.hotseatColumnSpan: 4\n" +
                "\thotseatCellHeightPx: 177.0px (67.42857dp)\n" +
                "\thotseatBarBottomSpacePx: 126.0px (48.0dp)\n" +
                "\thotseatBarSidePaddingStartPx: 0.0px (0.0dp)\n" +
                "\thotseatBarSidePaddingEndPx: 0.0px (0.0dp)\n" +
                "\thotseatBarEndOffset: 0.0px (0.0dp)\n" +
                "\thotseatQsbSpace: 95.0px (36.190475dp)\n" +
                "\thotseatQsbHeight: 165.0px (62.857143dp)\n" +
                "\tspringLoadedHotseatBarTopMarginPx: 200.0px (76.190475dp)\n" +
                "\tgetHotseatLayoutPadding(context).top: 0.0px (0.0dp)\n" +
                "\tgetHotseatLayoutPadding(context).bottom: 334.0px (127.2381dp)\n" +
                "\tgetHotseatLayoutPadding(context).left: 83.0px (31.619047dp)\n" +
                "\tgetHotseatLayoutPadding(context).right: 83.0px (31.619047dp)\n" +
                "\tnumShownHotseatIcons: 4\n" +
                "\thotseatBorderSpace: 95.0px (36.190475dp)\n" +
                "\tisQsbInline: false\n" +
                "\thotseatQsbWidth: 913.0px (347.8095dp)\n" +
                "\tisTaskbarPresent:false\n" +
                "\tisTaskbarPresentInApps:false\n" +
                "\ttaskbarSize: 0.0px (0.0dp)\n" +
                "\tdesiredWorkspaceHorizontalMarginPx: 57.0px (21.714285dp)\n" +
                "\tworkspacePadding.left: 29.0px (11.047619dp)\n" +
                "\tworkspacePadding.top: 67.0px (25.52381dp)\n" +
                "\tworkspacePadding.right: 29.0px (11.047619dp)\n" +
                "\tworkspacePadding.bottom: 567.0px (216.0dp)\n" +
                "\ticonScale: 0.9981516px (0.38024822dp)\n" +
                "\tcellScaleToFit : 0.9981516px (0.38024822dp)\n" +
                "\textraSpace: 211.0px (80.38095dp)\n" +
                "\tunscaled extraSpace: 211.39073px (80.5298dp)\n" +
                "\tmaxEmptySpace: 315.0px (120.0dp)\n" +
                "\tworkspaceTopPadding: 95.0px (36.190475dp)\n" +
                "\tworkspaceBottomPadding: 116.0px (44.190475dp)\n" +
                "\toverviewTaskMarginPx: 42.0px (16.0dp)\n" +
                "\toverviewTaskIconSizePx: 126.0px (48.0dp)\n" +
                "\toverviewTaskIconDrawableSizePx: 116.0px (44.190475dp)\n" +
                "\toverviewTaskIconDrawableSizeGridPx: 0.0px (0.0dp)\n" +
                "\toverviewTaskThumbnailTopMarginPx: 168.0px (64.0dp)\n" +
                "\toverviewActionsTopMarginPx: 63.0px (24.0dp)\n" +
                "\toverviewActionsHeight: 126.0px (48.0dp)\n" +
                "\toverviewActionsButtonSpacing: 95.0px (36.190475dp)\n" +
                "\toverviewPageSpacing: 42.0px (16.0dp)\n" +
                "\toverviewRowSpacing: 0.0px (0.0dp)\n" +
                "\toverviewGridSideMargin: 0.0px (0.0dp)\n" +
                "\tdropTargetBarTopMarginPx: 84.0px (32.0dp)\n" +
                "\tdropTargetBarSizePx: 147.0px (56.0dp)\n" +
                "\tdropTargetBarBottomMarginPx: 42.0px (16.0dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkTop(): 391.0px (148.95238dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkBottom(): 1689.0px (643.4286dp)\n" +
                "\tworkspaceSpringLoadedMinNextPageVisiblePx: 63.0px (24.0dp)\n" +
                "\tgetWorkspaceSpringLoadScale(): 0.81892747px (0.31197238dp)\n" +
                "\tgetCellLayoutHeight(): 1585.0px (603.8095dp)\n" +
                "\tgetCellLayoutWidth(): 1022.0px (389.33334dp)\n")
    }

    @Test
    fun tabletLandscape3Button() {
        initializeVarsForTablet(isLandscape = true, isGestureMode = false)
        val dp = newDP()
        dp.isTaskbarPresentInApps = true

        assertThat(dump(dp)).isEqualTo("DeviceProfile:\n" +
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
                "\tcellLayoutPaddingPx.top: 32.0px (16.0dp)\n" +
                "\tcellLayoutPaddingPx.right: 59.0px (29.5dp)\n" +
                "\tcellLayoutPaddingPx.bottom: 59.0px (29.5dp)\n" +
                "\ticonSizePx: 120.0px (60.0dp)\n" +
                "\ticonTextSizePx: 28.0px (14.0dp)\n" +
                "\ticonDrawablePaddingPx: 14.0px (7.0dp)\n" +
                "\tfolderCellWidthPx: 240.0px (120.0dp)\n" +
                "\tfolderCellHeightPx: 208.0px (104.0dp)\n" +
                "\tfolderChildIconSizePx: 120.0px (60.0dp)\n" +
                "\tfolderChildTextSizePx: 28.0px (14.0dp)\n" +
                "\tfolderChildDrawablePaddingPx: 16.0px (8.0dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Horizontal: 32.0px (16.0dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Vertical: 32.0px (16.0dp)\n" +
                "\tfolderContentPaddingLeftRight: 32.0px (16.0dp)\n" +
                "\tfolderTopPadding: 48.0px (24.0dp)\n" +
                "\tbottomSheetTopPadding: 104.0px (52.0dp)\n" +
                "\tallAppsShiftRange: 1496.0px (748.0dp)\n" +
                "\tallAppsTopPadding: 104.0px (52.0dp)\n" +
                "\tallAppsIconSizePx: 120.0px (60.0dp)\n" +
                "\tallAppsIconTextSizePx: 28.0px (14.0dp)\n" +
                "\tallAppsIconDrawablePaddingPx: 14.0px (7.0dp)\n" +
                "\tallAppsCellHeightPx: 284.0px (142.0dp)\n" +
                "\tallAppsCellWidthPx: 252.0px (126.0dp)\n" +
                "\tallAppsBorderSpacePxX: 32.0px (16.0dp)\n" +
                "\tallAppsBorderSpacePxY: 32.0px (16.0dp)\n" +
                "\tnumShownAllAppsColumns: 6\n" +
                "\tallAppsLeftRightPadding: 64.0px (32.0dp)\n" +
                "\tallAppsLeftRightMargin: 380.0px (190.0dp)\n" +
                "\thotseatBarSizePx: 200.0px (100.0dp)\n" +
                "\tinv.hotseatColumnSpan: 4\n" +
                "\thotseatCellHeightPx: 135.0px (67.5dp)\n" +
                "\thotseatBarBottomSpacePx: 80.0px (40.0dp)\n" +
                "\thotseatBarSidePaddingStartPx: 0.0px (0.0dp)\n" +
                "\thotseatBarSidePaddingEndPx: 0.0px (0.0dp)\n" +
                "\thotseatBarEndOffset: 705.0px (352.5dp)\n" +
                "\thotseatQsbSpace: 64.0px (32.0dp)\n" +
                "\thotseatQsbHeight: 126.0px (63.0dp)\n" +
                "\tspringLoadedHotseatBarTopMarginPx: 128.0px (64.0dp)\n" +
                "\tgetHotseatLayoutPadding(context).top: -8.0px (-4.0dp)\n" +
                "\tgetHotseatLayoutPadding(context).bottom: 73.0px (36.5dp)\n" +
                "\tgetHotseatLayoutPadding(context).left: 954.0px (477.0dp)\n" +
                "\tgetHotseatLayoutPadding(context).right: 705.0px (352.5dp)\n" +
                "\tnumShownHotseatIcons: 6\n" +
                "\thotseatBorderSpace: 36.0px (18.0dp)\n" +
                "\tisQsbInline: true\n" +
                "\thotseatQsbWidth: 619.0px (309.5dp)\n" +
                "\tisTaskbarPresent:true\n" +
                "\tisTaskbarPresentInApps:true\n" +
                "\ttaskbarSize: 120.0px (60.0dp)\n" +
                "\tdesiredWorkspaceHorizontalMarginPx: 240.0px (120.0dp)\n" +
                "\tworkspacePadding.left: 181.0px (90.5dp)\n" +
                "\tworkspacePadding.top: 0.0px (0.0dp)\n" +
                "\tworkspacePadding.right: 181.0px (90.5dp)\n" +
                "\tworkspacePadding.bottom: 237.0px (118.5dp)\n" +
                "\ticonScale: 1.0px (0.5dp)\n" +
                "\tcellScaleToFit : 1.0px (0.5dp)\n" +
                "\textraSpace: 104.0px (52.0dp)\n" +
                "\tunscaled extraSpace: 104.0px (52.0dp)\n" +
                "\tmaxEmptySpace: 200.0px (100.0dp)\n" +
                "\tworkspaceTopPadding: 32.0px (16.0dp)\n" +
                "\tworkspaceBottomPadding: 72.0px (36.0dp)\n" +
                "\toverviewTaskMarginPx: 32.0px (16.0dp)\n" +
                "\toverviewTaskIconSizePx: 96.0px (48.0dp)\n" +
                "\toverviewTaskIconDrawableSizePx: 88.0px (44.0dp)\n" +
                "\toverviewTaskIconDrawableSizeGridPx: 88.0px (44.0dp)\n" +
                "\toverviewTaskThumbnailTopMarginPx: 128.0px (64.0dp)\n" +
                "\toverviewActionsTopMarginPx: 40.0px (20.0dp)\n" +
                "\toverviewActionsHeight: 96.0px (48.0dp)\n" +
                "\toverviewActionsButtonSpacing: 72.0px (36.0dp)\n" +
                "\toverviewPageSpacing: 88.0px (44.0dp)\n" +
                "\toverviewRowSpacing: 72.0px (36.0dp)\n" +
                "\toverviewGridSideMargin: 128.0px (64.0dp)\n" +
                "\tdropTargetBarTopMarginPx: 0.0px (0.0dp)\n" +
                "\tdropTargetBarSizePx: 144.0px (72.0dp)\n" +
                "\tdropTargetBarBottomMarginPx: 64.0px (32.0dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkTop(): 312.0px (156.0dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkBottom(): 1272.0px (636.0dp)\n" +
                "\tworkspaceSpringLoadedMinNextPageVisiblePx: 48.0px (24.0dp)\n" +
                "\tgetWorkspaceSpringLoadScale(): 0.76250994px (0.38125497dp)\n" +
                "\tgetCellLayoutHeight(): 1259.0px (629.5dp)\n" +
                "\tgetCellLayoutWidth(): 2198.0px (1099.0dp)\n")
    }

    @Test
    fun tabletLandscape() {
        initializeVarsForTablet(isLandscape = true)
        val dp = newDP()
        dp.isTaskbarPresentInApps = true

        assertThat(dump(dp)).isEqualTo("DeviceProfile:\n" +
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
                "\tcellLayoutPaddingPx.top: 32.0px (16.0dp)\n" +
                "\tcellLayoutPaddingPx.right: 59.0px (29.5dp)\n" +
                "\tcellLayoutPaddingPx.bottom: 59.0px (29.5dp)\n" +
                "\ticonSizePx: 120.0px (60.0dp)\n" +
                "\ticonTextSizePx: 28.0px (14.0dp)\n" +
                "\ticonDrawablePaddingPx: 14.0px (7.0dp)\n" +
                "\tfolderCellWidthPx: 240.0px (120.0dp)\n" +
                "\tfolderCellHeightPx: 208.0px (104.0dp)\n" +
                "\tfolderChildIconSizePx: 120.0px (60.0dp)\n" +
                "\tfolderChildTextSizePx: 28.0px (14.0dp)\n" +
                "\tfolderChildDrawablePaddingPx: 16.0px (8.0dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Horizontal: 32.0px (16.0dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Vertical: 32.0px (16.0dp)\n" +
                "\tfolderContentPaddingLeftRight: 32.0px (16.0dp)\n" +
                "\tfolderTopPadding: 48.0px (24.0dp)\n" +
                "\tbottomSheetTopPadding: 104.0px (52.0dp)\n" +
                "\tallAppsShiftRange: 1496.0px (748.0dp)\n" +
                "\tallAppsTopPadding: 104.0px (52.0dp)\n" +
                "\tallAppsIconSizePx: 120.0px (60.0dp)\n" +
                "\tallAppsIconTextSizePx: 28.0px (14.0dp)\n" +
                "\tallAppsIconDrawablePaddingPx: 14.0px (7.0dp)\n" +
                "\tallAppsCellHeightPx: 284.0px (142.0dp)\n" +
                "\tallAppsCellWidthPx: 252.0px (126.0dp)\n" +
                "\tallAppsBorderSpacePxX: 32.0px (16.0dp)\n" +
                "\tallAppsBorderSpacePxY: 32.0px (16.0dp)\n" +
                "\tnumShownAllAppsColumns: 6\n" +
                "\tallAppsLeftRightPadding: 64.0px (32.0dp)\n" +
                "\tallAppsLeftRightMargin: 380.0px (190.0dp)\n" +
                "\thotseatBarSizePx: 200.0px (100.0dp)\n" +
                "\tinv.hotseatColumnSpan: 4\n" +
                "\thotseatCellHeightPx: 135.0px (67.5dp)\n" +
                "\thotseatBarBottomSpacePx: 80.0px (40.0dp)\n" +
                "\thotseatBarSidePaddingStartPx: 0.0px (0.0dp)\n" +
                "\thotseatBarSidePaddingEndPx: 0.0px (0.0dp)\n" +
                "\thotseatBarEndOffset: 0.0px (0.0dp)\n" +
                "\thotseatQsbSpace: 64.0px (32.0dp)\n" +
                "\thotseatQsbHeight: 126.0px (63.0dp)\n" +
                "\tspringLoadedHotseatBarTopMarginPx: 128.0px (64.0dp)\n" +
                "\tgetHotseatLayoutPadding(context).top: -8.0px (-4.0dp)\n" +
                "\tgetHotseatLayoutPadding(context).bottom: 73.0px (36.5dp)\n" +
                "\tgetHotseatLayoutPadding(context).left: 1040.0px (520.0dp)\n" +
                "\tgetHotseatLayoutPadding(context).right: 300.0px (150.0dp)\n" +
                "\tnumShownHotseatIcons: 6\n" +
                "\thotseatBorderSpace: 100.0px (50.0dp)\n" +
                "\tisQsbInline: true\n" +
                "\thotseatQsbWidth: 640.0px (320.0dp)\n" +
                "\tisTaskbarPresent:true\n" +
                "\tisTaskbarPresentInApps:true\n" +
                "\ttaskbarSize: 120.0px (60.0dp)\n" +
                "\tdesiredWorkspaceHorizontalMarginPx: 240.0px (120.0dp)\n" +
                "\tworkspacePadding.left: 181.0px (90.5dp)\n" +
                "\tworkspacePadding.top: 0.0px (0.0dp)\n" +
                "\tworkspacePadding.right: 181.0px (90.5dp)\n" +
                "\tworkspacePadding.bottom: 237.0px (118.5dp)\n" +
                "\ticonScale: 1.0px (0.5dp)\n" +
                "\tcellScaleToFit : 1.0px (0.5dp)\n" +
                "\textraSpace: 104.0px (52.0dp)\n" +
                "\tunscaled extraSpace: 104.0px (52.0dp)\n" +
                "\tmaxEmptySpace: 200.0px (100.0dp)\n" +
                "\tworkspaceTopPadding: 32.0px (16.0dp)\n" +
                "\tworkspaceBottomPadding: 72.0px (36.0dp)\n" +
                "\toverviewTaskMarginPx: 32.0px (16.0dp)\n" +
                "\toverviewTaskIconSizePx: 96.0px (48.0dp)\n" +
                "\toverviewTaskIconDrawableSizePx: 88.0px (44.0dp)\n" +
                "\toverviewTaskIconDrawableSizeGridPx: 88.0px (44.0dp)\n" +
                "\toverviewTaskThumbnailTopMarginPx: 128.0px (64.0dp)\n" +
                "\toverviewActionsTopMarginPx: 40.0px (20.0dp)\n" +
                "\toverviewActionsHeight: 96.0px (48.0dp)\n" +
                "\toverviewActionsButtonSpacing: 72.0px (36.0dp)\n" +
                "\toverviewPageSpacing: 88.0px (44.0dp)\n" +
                "\toverviewRowSpacing: 72.0px (36.0dp)\n" +
                "\toverviewGridSideMargin: 128.0px (64.0dp)\n" +
                "\tdropTargetBarTopMarginPx: 0.0px (0.0dp)\n" +
                "\tdropTargetBarSizePx: 144.0px (72.0dp)\n" +
                "\tdropTargetBarBottomMarginPx: 64.0px (32.0dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkTop(): 312.0px (156.0dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkBottom(): 1272.0px (636.0dp)\n" +
                "\tworkspaceSpringLoadedMinNextPageVisiblePx: 48.0px (24.0dp)\n" +
                "\tgetWorkspaceSpringLoadScale(): 0.76250994px (0.38125497dp)\n" +
                "\tgetCellLayoutHeight(): 1259.0px (629.5dp)\n" +
                "\tgetCellLayoutWidth(): 2198.0px (1099.0dp)\n")
    }

    @Test
    fun tabletPortrait3Button() {
        initializeVarsForTablet(isGestureMode = false)
        val dp = newDP()
        dp.isTaskbarPresentInApps = true

        assertThat(dump(dp)).isEqualTo("DeviceProfile:\n" +
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
                "\ticonDrawablePaddingPx: 14.0px (7.0dp)\n" +
                "\tfolderCellWidthPx: 240.0px (120.0dp)\n" +
                "\tfolderCellHeightPx: 208.0px (104.0dp)\n" +
                "\tfolderChildIconSizePx: 120.0px (60.0dp)\n" +
                "\tfolderChildTextSizePx: 28.0px (14.0dp)\n" +
                "\tfolderChildDrawablePaddingPx: 16.0px (8.0dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Horizontal: 32.0px (16.0dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Vertical: 32.0px (16.0dp)\n" +
                "\tfolderContentPaddingLeftRight: 32.0px (16.0dp)\n" +
                "\tfolderTopPadding: 48.0px (24.0dp)\n" +
                "\tbottomSheetTopPadding: 704.0px (352.0dp)\n" +
                "\tallAppsShiftRange: 1936.0px (968.0dp)\n" +
                "\tallAppsTopPadding: 624.0px (312.0dp)\n" +
                "\tallAppsIconSizePx: 120.0px (60.0dp)\n" +
                "\tallAppsIconTextSizePx: 28.0px (14.0dp)\n" +
                "\tallAppsIconDrawablePaddingPx: 14.0px (7.0dp)\n" +
                "\tallAppsCellHeightPx: 316.0px (158.0dp)\n" +
                "\tallAppsCellWidthPx: 192.0px (96.0dp)\n" +
                "\tallAppsBorderSpacePxX: 16.0px (8.0dp)\n" +
                "\tallAppsBorderSpacePxY: 32.0px (16.0dp)\n" +
                "\tnumShownAllAppsColumns: 6\n" +
                "\tallAppsLeftRightPadding: 56.0px (28.0dp)\n" +
                "\tallAppsLeftRightMargin: 128.0px (64.0dp)\n" +
                "\thotseatBarSizePx: 358.0px (179.0dp)\n" +
                "\tinv.hotseatColumnSpan: 6\n" +
                "\thotseatCellHeightPx: 135.0px (67.5dp)\n" +
                "\thotseatBarBottomSpacePx: 72.0px (36.0dp)\n" +
                "\thotseatBarSidePaddingStartPx: 0.0px (0.0dp)\n" +
                "\thotseatBarSidePaddingEndPx: 0.0px (0.0dp)\n" +
                "\thotseatBarEndOffset: 558.0px (279.0dp)\n" +
                "\thotseatQsbSpace: 64.0px (32.0dp)\n" +
                "\thotseatQsbHeight: 126.0px (63.0dp)\n" +
                "\tspringLoadedHotseatBarTopMarginPx: 216.0px (108.0dp)\n" +
                "\tgetHotseatLayoutPadding(context).top: 158.0px (79.0dp)\n" +
                "\tgetHotseatLayoutPadding(context).bottom: 65.0px (32.5dp)\n" +
                "\tgetHotseatLayoutPadding(context).left: 150.0px (75.0dp)\n" +
                "\tgetHotseatLayoutPadding(context).right: 558.0px (279.0dp)\n" +
                "\tnumShownHotseatIcons: 5\n" +
                "\thotseatBorderSpace: 73.0px (36.5dp)\n" +
                "\tisQsbInline: false\n" +
                "\thotseatQsbWidth: 1300.0px (650.0dp)\n" +
                "\tisTaskbarPresent:true\n" +
                "\tisTaskbarPresentInApps:true\n" +
                "\ttaskbarSize: 120.0px (60.0dp)\n" +
                "\tdesiredWorkspaceHorizontalMarginPx: 108.0px (54.0dp)\n" +
                "\tworkspacePadding.left: 36.0px (18.0dp)\n" +
                "\tworkspacePadding.top: 87.0px (43.5dp)\n" +
                "\tworkspacePadding.right: 36.0px (18.0dp)\n" +
                "\tworkspacePadding.bottom: 513.0px (256.5dp)\n" +
                "\ticonScale: 1.0px (0.5dp)\n" +
                "\tcellScaleToFit : 1.0px (0.5dp)\n" +
                "\textraSpace: 362.0px (181.0dp)\n" +
                "\tunscaled extraSpace: 362.0px (181.0dp)\n" +
                "\tmaxEmptySpace: 19998.0px (9999.0dp)\n" +
                "\tworkspaceTopPadding: 159.0px (79.5dp)\n" +
                "\tworkspaceBottomPadding: 203.0px (101.5dp)\n" +
                "\toverviewTaskMarginPx: 32.0px (16.0dp)\n" +
                "\toverviewTaskIconSizePx: 96.0px (48.0dp)\n" +
                "\toverviewTaskIconDrawableSizePx: 88.0px (44.0dp)\n" +
                "\toverviewTaskIconDrawableSizeGridPx: 88.0px (44.0dp)\n" +
                "\toverviewTaskThumbnailTopMarginPx: 128.0px (64.0dp)\n" +
                "\toverviewActionsTopMarginPx: 48.0px (24.0dp)\n" +
                "\toverviewActionsHeight: 96.0px (48.0dp)\n" +
                "\toverviewActionsButtonSpacing: 72.0px (36.0dp)\n" +
                "\toverviewPageSpacing: 88.0px (44.0dp)\n" +
                "\toverviewRowSpacing: 72.0px (36.0dp)\n" +
                "\toverviewGridSideMargin: 128.0px (64.0dp)\n" +
                "\tdropTargetBarTopMarginPx: 220.0px (110.0dp)\n" +
                "\tdropTargetBarSizePx: 144.0px (72.0dp)\n" +
                "\tdropTargetBarBottomMarginPx: 96.0px (48.0dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkTop(): 564.0px (282.0dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkBottom(): 1986.0px (993.0dp)\n" +
                "\tworkspaceSpringLoadedMinNextPageVisiblePx: 48.0px (24.0dp)\n" +
                "\tgetWorkspaceSpringLoadScale(): 0.76616377px (0.38308188dp)\n" +
                "\tgetCellLayoutHeight(): 1856.0px (928.0dp)\n" +
                "\tgetCellLayoutWidth(): 1528.0px (764.0dp)\n")
    }

    @Test
    fun tabletPortrait() {
        initializeVarsForTablet()
        val dp = newDP()
        dp.isTaskbarPresentInApps = true

        assertThat(dump(dp)).isEqualTo("DeviceProfile:\n" +
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
                "\ticonDrawablePaddingPx: 14.0px (7.0dp)\n" +
                "\tfolderCellWidthPx: 240.0px (120.0dp)\n" +
                "\tfolderCellHeightPx: 208.0px (104.0dp)\n" +
                "\tfolderChildIconSizePx: 120.0px (60.0dp)\n" +
                "\tfolderChildTextSizePx: 28.0px (14.0dp)\n" +
                "\tfolderChildDrawablePaddingPx: 16.0px (8.0dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Horizontal: 32.0px (16.0dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Vertical: 32.0px (16.0dp)\n" +
                "\tfolderContentPaddingLeftRight: 32.0px (16.0dp)\n" +
                "\tfolderTopPadding: 48.0px (24.0dp)\n" +
                "\tbottomSheetTopPadding: 704.0px (352.0dp)\n" +
                "\tallAppsShiftRange: 1936.0px (968.0dp)\n" +
                "\tallAppsTopPadding: 624.0px (312.0dp)\n" +
                "\tallAppsIconSizePx: 120.0px (60.0dp)\n" +
                "\tallAppsIconTextSizePx: 28.0px (14.0dp)\n" +
                "\tallAppsIconDrawablePaddingPx: 14.0px (7.0dp)\n" +
                "\tallAppsCellHeightPx: 316.0px (158.0dp)\n" +
                "\tallAppsCellWidthPx: 192.0px (96.0dp)\n" +
                "\tallAppsBorderSpacePxX: 16.0px (8.0dp)\n" +
                "\tallAppsBorderSpacePxY: 32.0px (16.0dp)\n" +
                "\tnumShownAllAppsColumns: 6\n" +
                "\tallAppsLeftRightPadding: 56.0px (28.0dp)\n" +
                "\tallAppsLeftRightMargin: 128.0px (64.0dp)\n" +
                "\thotseatBarSizePx: 358.0px (179.0dp)\n" +
                "\tinv.hotseatColumnSpan: 6\n" +
                "\thotseatCellHeightPx: 135.0px (67.5dp)\n" +
                "\thotseatBarBottomSpacePx: 72.0px (36.0dp)\n" +
                "\thotseatBarSidePaddingStartPx: 0.0px (0.0dp)\n" +
                "\thotseatBarSidePaddingEndPx: 0.0px (0.0dp)\n" +
                "\thotseatBarEndOffset: 0.0px (0.0dp)\n" +
                "\thotseatQsbSpace: 64.0px (32.0dp)\n" +
                "\thotseatQsbHeight: 126.0px (63.0dp)\n" +
                "\tspringLoadedHotseatBarTopMarginPx: 216.0px (108.0dp)\n" +
                "\tgetHotseatLayoutPadding(context).top: 158.0px (79.0dp)\n" +
                "\tgetHotseatLayoutPadding(context).bottom: 65.0px (32.5dp)\n" +
                "\tgetHotseatLayoutPadding(context).left: 150.0px (75.0dp)\n" +
                "\tgetHotseatLayoutPadding(context).right: 150.0px (75.0dp)\n" +
                "\tnumShownHotseatIcons: 6\n" +
                "\thotseatBorderSpace: 116.0px (58.0dp)\n" +
                "\tisQsbInline: false\n" +
                "\thotseatQsbWidth: 1300.0px (650.0dp)\n" +
                "\tisTaskbarPresent:true\n" +
                "\tisTaskbarPresentInApps:true\n" +
                "\ttaskbarSize: 120.0px (60.0dp)\n" +
                "\tdesiredWorkspaceHorizontalMarginPx: 108.0px (54.0dp)\n" +
                "\tworkspacePadding.left: 36.0px (18.0dp)\n" +
                "\tworkspacePadding.top: 87.0px (43.5dp)\n" +
                "\tworkspacePadding.right: 36.0px (18.0dp)\n" +
                "\tworkspacePadding.bottom: 513.0px (256.5dp)\n" +
                "\ticonScale: 1.0px (0.5dp)\n" +
                "\tcellScaleToFit : 1.0px (0.5dp)\n" +
                "\textraSpace: 362.0px (181.0dp)\n" +
                "\tunscaled extraSpace: 362.0px (181.0dp)\n" +
                "\tmaxEmptySpace: 19998.0px (9999.0dp)\n" +
                "\tworkspaceTopPadding: 159.0px (79.5dp)\n" +
                "\tworkspaceBottomPadding: 203.0px (101.5dp)\n" +
                "\toverviewTaskMarginPx: 32.0px (16.0dp)\n" +
                "\toverviewTaskIconSizePx: 96.0px (48.0dp)\n" +
                "\toverviewTaskIconDrawableSizePx: 88.0px (44.0dp)\n" +
                "\toverviewTaskIconDrawableSizeGridPx: 88.0px (44.0dp)\n" +
                "\toverviewTaskThumbnailTopMarginPx: 128.0px (64.0dp)\n" +
                "\toverviewActionsTopMarginPx: 48.0px (24.0dp)\n" +
                "\toverviewActionsHeight: 96.0px (48.0dp)\n" +
                "\toverviewActionsButtonSpacing: 72.0px (36.0dp)\n" +
                "\toverviewPageSpacing: 88.0px (44.0dp)\n" +
                "\toverviewRowSpacing: 72.0px (36.0dp)\n" +
                "\toverviewGridSideMargin: 128.0px (64.0dp)\n" +
                "\tdropTargetBarTopMarginPx: 220.0px (110.0dp)\n" +
                "\tdropTargetBarSizePx: 144.0px (72.0dp)\n" +
                "\tdropTargetBarBottomMarginPx: 96.0px (48.0dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkTop(): 564.0px (282.0dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkBottom(): 1986.0px (993.0dp)\n" +
                "\tworkspaceSpringLoadedMinNextPageVisiblePx: 48.0px (24.0dp)\n" +
                "\tgetWorkspaceSpringLoadScale(): 0.76616377px (0.38308188dp)\n" +
                "\tgetCellLayoutHeight(): 1856.0px (928.0dp)\n" +
                "\tgetCellLayoutWidth(): 1528.0px (764.0dp)\n")
    }

    @Test
    fun twoPanelLandscape3Button() {
        initializeVarsForTwoPanel(isLandscape = true, isGestureMode = false)
        val dp = newDP()
        dp.isTaskbarPresentInApps = true

        assertThat(dump(dp)).isEqualTo("DeviceProfile:\n" +
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
                "\tisScalableGrid:true\n" +
                "\tinv.numRows: 4\n" +
                "\tinv.numColumns: 4\n" +
                "\tinv.numSearchContainerColumns: 4\n" +
                "\tminCellSize: PointF(80.0, 102.0)dp\n" +
                "\tcellWidthPx: 210.0px (80.0dp)\n" +
                "\tcellHeightPx: 267.0px (101.71429dp)\n" +
                "\tgetCellSize().x: 210.0px (80.0dp)\n" +
                "\tgetCellSize().y: 267.0px (101.71429dp)\n" +
                "\tcellLayoutBorderSpacePx Horizontal: 52.0px (19.809525dp)\n" +
                "\tcellLayoutBorderSpacePx Vertical: 52.0px (19.809525dp)\n" +
                "\tcellLayoutPaddingPx.left: 26.0px (9.904762dp)\n" +
                "\tcellLayoutPaddingPx.top: 18.0px (6.857143dp)\n" +
                "\tcellLayoutPaddingPx.right: 26.0px (9.904762dp)\n" +
                "\tcellLayoutPaddingPx.bottom: 26.0px (9.904762dp)\n" +
                "\ticonSizePx: 157.0px (59.809525dp)\n" +
                "\ticonTextSizePx: 36.0px (13.714286dp)\n" +
                "\ticonDrawablePaddingPx: 17.0px (6.4761906dp)\n" +
                "\tfolderCellWidthPx: 210.0px (80.0dp)\n" +
                "\tfolderCellHeightPx: 247.0px (94.09524dp)\n" +
                "\tfolderChildIconSizePx: 158.0px (60.190475dp)\n" +
                "\tfolderChildTextSizePx: 37.0px (14.095238dp)\n" +
                "\tfolderChildDrawablePaddingPx: 13.0px (4.952381dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Horizontal: 42.0px (16.0dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Vertical: 42.0px (16.0dp)\n" +
                "\tfolderContentPaddingLeftRight: 42.0px (16.0dp)\n" +
                "\tfolderTopPadding: 63.0px (24.0dp)\n" +
                "\tbottomSheetTopPadding: 110.0px (41.904762dp)\n" +
                "\tallAppsShiftRange: 1730.0px (659.0476dp)\n" +
                "\tallAppsTopPadding: 110.0px (41.904762dp)\n" +
                "\tallAppsIconSizePx: 157.0px (59.809525dp)\n" +
                "\tallAppsIconTextSizePx: 37.0px (14.095238dp)\n" +
                "\tallAppsIconDrawablePaddingPx: 18.0px (6.857143dp)\n" +
                "\tallAppsCellHeightPx: 315.0px (120.0dp)\n" +
                "\tallAppsCellWidthPx: 210.0px (80.0dp)\n" +
                "\tallAppsBorderSpacePxX: 52.0px (19.809525dp)\n" +
                "\tallAppsBorderSpacePxY: 42.0px (16.0dp)\n" +
                "\tnumShownAllAppsColumns: 6\n" +
                "\tallAppsLeftRightPadding: 137.0px (52.190475dp)\n" +
                "\tallAppsLeftRightMargin: 207.0px (78.85714dp)\n" +
                "\thotseatBarSizePx: 417.0px (158.85715dp)\n" +
                "\tinv.hotseatColumnSpan: 6\n" +
                "\thotseatCellHeightPx: 177.0px (67.42857dp)\n" +
                "\thotseatBarBottomSpacePx: 53.0px (20.190475dp)\n" +
                "\thotseatBarSidePaddingStartPx: 0.0px (0.0dp)\n" +
                "\thotseatBarSidePaddingEndPx: 0.0px (0.0dp)\n" +
                "\thotseatBarEndOffset: 744.0px (283.42856dp)\n" +
                "\thotseatQsbSpace: 74.0px (28.190475dp)\n" +
                "\thotseatQsbHeight: 165.0px (62.857143dp)\n" +
                "\tspringLoadedHotseatBarTopMarginPx: 116.0px (44.190475dp)\n" +
                "\tgetHotseatLayoutPadding(context).top: 197.0px (75.04762dp)\n" +
                "\tgetHotseatLayoutPadding(context).bottom: 43.0px (16.380953dp)\n" +
                "\tgetHotseatLayoutPadding(context).left: 106.0px (40.38095dp)\n" +
                "\tgetHotseatLayoutPadding(context).right: 744.0px (283.42856dp)\n" +
                "\tnumShownHotseatIcons: 6\n" +
                "\thotseatBorderSpace: 83.0px (31.619047dp)\n" +
                "\tisQsbInline: false\n" +
                "\thotseatQsbWidth: 1467.0px (558.8571dp)\n" +
                "\tisTaskbarPresent:true\n" +
                "\tisTaskbarPresentInApps:true\n" +
                "\ttaskbarSize: 158.0px (60.190475dp)\n" +
                "\tdesiredWorkspaceHorizontalMarginPx: 79.0px (30.095238dp)\n" +
                "\tworkspacePadding.left: 53.0px (20.190475dp)\n" +
                "\tworkspacePadding.top: 0.0px (0.0dp)\n" +
                "\tworkspacePadding.right: 53.0px (20.190475dp)\n" +
                "\tworkspacePadding.bottom: 461.0px (175.61905dp)\n" +
                "\ticonScale: 0.99864316px (0.3804355dp)\n" +
                "\tcellScaleToFit : 0.99864316px (0.3804355dp)\n" +
                "\textraSpace: 57.0px (21.714285dp)\n" +
                "\tunscaled extraSpace: 57.077446px (21.74379dp)\n" +
                "\tmaxEmptySpace: 131.0px (49.904762dp)\n" +
                "\tworkspaceTopPadding: 18.0px (6.857143dp)\n" +
                "\tworkspaceBottomPadding: 39.0px (14.857142dp)\n" +
                "\toverviewTaskMarginPx: 32.0px (12.190476dp)\n" +
                "\toverviewTaskIconSizePx: 126.0px (48.0dp)\n" +
                "\toverviewTaskIconDrawableSizePx: 116.0px (44.190475dp)\n" +
                "\toverviewTaskIconDrawableSizeGridPx: 116.0px (44.190475dp)\n" +
                "\toverviewTaskThumbnailTopMarginPx: 158.0px (60.190475dp)\n" +
                "\toverviewActionsTopMarginPx: 32.0px (12.190476dp)\n" +
                "\toverviewActionsHeight: 126.0px (48.0dp)\n" +
                "\toverviewActionsButtonSpacing: 95.0px (36.190475dp)\n" +
                "\toverviewPageSpacing: 95.0px (36.190475dp)\n" +
                "\toverviewRowSpacing: 74.0px (28.190475dp)\n" +
                "\toverviewGridSideMargin: 168.0px (64.0dp)\n" +
                "\tdropTargetBarTopMarginPx: 0.0px (0.0dp)\n" +
                "\tdropTargetBarSizePx: 147.0px (56.0dp)\n" +
                "\tdropTargetBarBottomMarginPx: 42.0px (16.0dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkTop(): 299.0px (113.90476dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkBottom(): 1307.0px (497.90475dp)\n" +
                "\tworkspaceSpringLoadedMinNextPageVisiblePx: 63.0px (24.0dp)\n" +
                "\tgetWorkspaceSpringLoadScale(): 0.79432625px (0.30260047dp)\n" +
                "\tgetCellLayoutHeight(): 1269.0px (483.42856dp)\n" +
                "\tgetCellLayoutWidth(): 1051.0px (400.38095dp)\n")
    }

    @Test
    fun twoPanelLandscape() {
        initializeVarsForTwoPanel(isLandscape = true)
        val dp = newDP()
        dp.isTaskbarPresentInApps = true

        assertThat(dump(dp)).isEqualTo("DeviceProfile:\n" +
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
                "\tisScalableGrid:true\n" +
                "\tinv.numRows: 4\n" +
                "\tinv.numColumns: 4\n" +
                "\tinv.numSearchContainerColumns: 4\n" +
                "\tminCellSize: PointF(80.0, 102.0)dp\n" +
                "\tcellWidthPx: 210.0px (80.0dp)\n" +
                "\tcellHeightPx: 267.0px (101.71429dp)\n" +
                "\tgetCellSize().x: 210.0px (80.0dp)\n" +
                "\tgetCellSize().y: 267.0px (101.71429dp)\n" +
                "\tcellLayoutBorderSpacePx Horizontal: 52.0px (19.809525dp)\n" +
                "\tcellLayoutBorderSpacePx Vertical: 52.0px (19.809525dp)\n" +
                "\tcellLayoutPaddingPx.left: 26.0px (9.904762dp)\n" +
                "\tcellLayoutPaddingPx.top: 18.0px (6.857143dp)\n" +
                "\tcellLayoutPaddingPx.right: 26.0px (9.904762dp)\n" +
                "\tcellLayoutPaddingPx.bottom: 26.0px (9.904762dp)\n" +
                "\ticonSizePx: 157.0px (59.809525dp)\n" +
                "\ticonTextSizePx: 36.0px (13.714286dp)\n" +
                "\ticonDrawablePaddingPx: 17.0px (6.4761906dp)\n" +
                "\tfolderCellWidthPx: 210.0px (80.0dp)\n" +
                "\tfolderCellHeightPx: 247.0px (94.09524dp)\n" +
                "\tfolderChildIconSizePx: 158.0px (60.190475dp)\n" +
                "\tfolderChildTextSizePx: 37.0px (14.095238dp)\n" +
                "\tfolderChildDrawablePaddingPx: 13.0px (4.952381dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Horizontal: 42.0px (16.0dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Vertical: 42.0px (16.0dp)\n" +
                "\tfolderContentPaddingLeftRight: 42.0px (16.0dp)\n" +
                "\tfolderTopPadding: 63.0px (24.0dp)\n" +
                "\tbottomSheetTopPadding: 110.0px (41.904762dp)\n" +
                "\tallAppsShiftRange: 1730.0px (659.0476dp)\n" +
                "\tallAppsTopPadding: 110.0px (41.904762dp)\n" +
                "\tallAppsIconSizePx: 157.0px (59.809525dp)\n" +
                "\tallAppsIconTextSizePx: 37.0px (14.095238dp)\n" +
                "\tallAppsIconDrawablePaddingPx: 18.0px (6.857143dp)\n" +
                "\tallAppsCellHeightPx: 315.0px (120.0dp)\n" +
                "\tallAppsCellWidthPx: 210.0px (80.0dp)\n" +
                "\tallAppsBorderSpacePxX: 52.0px (19.809525dp)\n" +
                "\tallAppsBorderSpacePxY: 42.0px (16.0dp)\n" +
                "\tnumShownAllAppsColumns: 6\n" +
                "\tallAppsLeftRightPadding: 137.0px (52.190475dp)\n" +
                "\tallAppsLeftRightMargin: 207.0px (78.85714dp)\n" +
                "\thotseatBarSizePx: 417.0px (158.85715dp)\n" +
                "\tinv.hotseatColumnSpan: 6\n" +
                "\thotseatCellHeightPx: 177.0px (67.42857dp)\n" +
                "\thotseatBarBottomSpacePx: 53.0px (20.190475dp)\n" +
                "\thotseatBarSidePaddingStartPx: 0.0px (0.0dp)\n" +
                "\thotseatBarSidePaddingEndPx: 0.0px (0.0dp)\n" +
                "\thotseatBarEndOffset: 0.0px (0.0dp)\n" +
                "\thotseatQsbSpace: 74.0px (28.190475dp)\n" +
                "\thotseatQsbHeight: 165.0px (62.857143dp)\n" +
                "\tspringLoadedHotseatBarTopMarginPx: 116.0px (44.190475dp)\n" +
                "\tgetHotseatLayoutPadding(context).top: 197.0px (75.04762dp)\n" +
                "\tgetHotseatLayoutPadding(context).bottom: 43.0px (16.380953dp)\n" +
                "\tgetHotseatLayoutPadding(context).left: 370.0px (140.95238dp)\n" +
                "\tgetHotseatLayoutPadding(context).right: 370.0px (140.95238dp)\n" +
                "\tnumShownHotseatIcons: 6\n" +
                "\thotseatBorderSpace: 105.0px (40.0dp)\n" +
                "\tisQsbInline: false\n" +
                "\thotseatQsbWidth: 1467.0px (558.8571dp)\n" +
                "\tisTaskbarPresent:true\n" +
                "\tisTaskbarPresentInApps:true\n" +
                "\ttaskbarSize: 158.0px (60.190475dp)\n" +
                "\tdesiredWorkspaceHorizontalMarginPx: 79.0px (30.095238dp)\n" +
                "\tworkspacePadding.left: 53.0px (20.190475dp)\n" +
                "\tworkspacePadding.top: 0.0px (0.0dp)\n" +
                "\tworkspacePadding.right: 53.0px (20.190475dp)\n" +
                "\tworkspacePadding.bottom: 461.0px (175.61905dp)\n" +
                "\ticonScale: 0.99864316px (0.3804355dp)\n" +
                "\tcellScaleToFit : 0.99864316px (0.3804355dp)\n" +
                "\textraSpace: 57.0px (21.714285dp)\n" +
                "\tunscaled extraSpace: 57.077446px (21.74379dp)\n" +
                "\tmaxEmptySpace: 131.0px (49.904762dp)\n" +
                "\tworkspaceTopPadding: 18.0px (6.857143dp)\n" +
                "\tworkspaceBottomPadding: 39.0px (14.857142dp)\n" +
                "\toverviewTaskMarginPx: 32.0px (12.190476dp)\n" +
                "\toverviewTaskIconSizePx: 126.0px (48.0dp)\n" +
                "\toverviewTaskIconDrawableSizePx: 116.0px (44.190475dp)\n" +
                "\toverviewTaskIconDrawableSizeGridPx: 116.0px (44.190475dp)\n" +
                "\toverviewTaskThumbnailTopMarginPx: 158.0px (60.190475dp)\n" +
                "\toverviewActionsTopMarginPx: 32.0px (12.190476dp)\n" +
                "\toverviewActionsHeight: 126.0px (48.0dp)\n" +
                "\toverviewActionsButtonSpacing: 95.0px (36.190475dp)\n" +
                "\toverviewPageSpacing: 95.0px (36.190475dp)\n" +
                "\toverviewRowSpacing: 74.0px (28.190475dp)\n" +
                "\toverviewGridSideMargin: 168.0px (64.0dp)\n" +
                "\tdropTargetBarTopMarginPx: 0.0px (0.0dp)\n" +
                "\tdropTargetBarSizePx: 147.0px (56.0dp)\n" +
                "\tdropTargetBarBottomMarginPx: 42.0px (16.0dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkTop(): 299.0px (113.90476dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkBottom(): 1307.0px (497.90475dp)\n" +
                "\tworkspaceSpringLoadedMinNextPageVisiblePx: 63.0px (24.0dp)\n" +
                "\tgetWorkspaceSpringLoadScale(): 0.79432625px (0.30260047dp)\n" +
                "\tgetCellLayoutHeight(): 1269.0px (483.42856dp)\n" +
                "\tgetCellLayoutWidth(): 1051.0px (400.38095dp)\n")
    }

    @Test
    fun twoPanelPortrait3Button() {
        initializeVarsForTwoPanel(isGestureMode = false)
        val dp = newDP()
        dp.isTaskbarPresentInApps = true

        assertThat(dump(dp)).isEqualTo("DeviceProfile:\n" +
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
                "\tavailableHeightPx: 2098.0px (799.2381dp)\n" +
                "\tmInsets.left: 0.0px (0.0dp)\n" +
                "\tmInsets.top: 110.0px (41.904762dp)\n" +
                "\tmInsets.right: 0.0px (0.0dp)\n" +
                "\tmInsets.bottom: 0.0px (0.0dp)\n" +
                "\taspectRatio:1.2\n" +
                "\tisScalableGrid:true\n" +
                "\tinv.numRows: 4\n" +
                "\tinv.numColumns: 4\n" +
                "\tinv.numSearchContainerColumns: 4\n" +
                "\tminCellSize: PointF(68.0, 116.0)dp\n" +
                "\tcellWidthPx: 178.0px (67.809525dp)\n" +
                "\tcellHeightPx: 304.0px (115.809525dp)\n" +
                "\tgetCellSize().x: 178.0px (67.809525dp)\n" +
                "\tgetCellSize().y: 304.0px (115.809525dp)\n" +
                "\tcellLayoutBorderSpacePx Horizontal: 42.0px (16.0dp)\n" +
                "\tcellLayoutBorderSpacePx Vertical: 52.0px (19.809525dp)\n" +
                "\tcellLayoutPaddingPx.left: 21.0px (8.0dp)\n" +
                "\tcellLayoutPaddingPx.top: 21.0px (8.0dp)\n" +
                "\tcellLayoutPaddingPx.right: 21.0px (8.0dp)\n" +
                "\tcellLayoutPaddingPx.bottom: 21.0px (8.0dp)\n" +
                "\ticonSizePx: 136.0px (51.809525dp)\n" +
                "\ticonTextSizePx: 31.0px (11.809524dp)\n" +
                "\ticonDrawablePaddingPx: 17.0px (6.4761906dp)\n" +
                "\tfolderCellWidthPx: 210.0px (80.0dp)\n" +
                "\tfolderCellHeightPx: 247.0px (94.09524dp)\n" +
                "\tfolderChildIconSizePx: 158.0px (60.190475dp)\n" +
                "\tfolderChildTextSizePx: 37.0px (14.095238dp)\n" +
                "\tfolderChildDrawablePaddingPx: 13.0px (4.952381dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Horizontal: 42.0px (16.0dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Vertical: 42.0px (16.0dp)\n" +
                "\tfolderContentPaddingLeftRight: 42.0px (16.0dp)\n" +
                "\tfolderTopPadding: 63.0px (24.0dp)\n" +
                "\tbottomSheetTopPadding: 110.0px (41.904762dp)\n" +
                "\tallAppsShiftRange: 2098.0px (799.2381dp)\n" +
                "\tallAppsTopPadding: 110.0px (41.904762dp)\n" +
                "\tallAppsIconSizePx: 136.0px (51.809525dp)\n" +
                "\tallAppsIconTextSizePx: 31.0px (11.809524dp)\n" +
                "\tallAppsIconDrawablePaddingPx: 18.0px (6.857143dp)\n" +
                "\tallAppsCellHeightPx: 345.0px (131.42857dp)\n" +
                "\tallAppsCellWidthPx: 178.0px (67.809525dp)\n" +
                "\tallAppsBorderSpacePxX: 42.0px (16.0dp)\n" +
                "\tallAppsBorderSpacePxY: 73.0px (27.809525dp)\n" +
                "\tnumShownAllAppsColumns: 6\n" +
                "\tallAppsLeftRightPadding: 126.0px (48.0dp)\n" +
                "\tallAppsLeftRightMargin: 155.0px (59.04762dp)\n" +
                "\thotseatBarSizePx: 459.0px (174.85715dp)\n" +
                "\tinv.hotseatColumnSpan: 6\n" +
                "\thotseatCellHeightPx: 153.0px (58.285713dp)\n" +
                "\thotseatBarBottomSpacePx: 95.0px (36.190475dp)\n" +
                "\thotseatBarSidePaddingStartPx: 0.0px (0.0dp)\n" +
                "\thotseatBarSidePaddingEndPx: 0.0px (0.0dp)\n" +
                "\thotseatBarEndOffset: 660.0px (251.42857dp)\n" +
                "\thotseatQsbSpace: 95.0px (36.190475dp)\n" +
                "\thotseatQsbHeight: 165.0px (62.857143dp)\n" +
                "\tspringLoadedHotseatBarTopMarginPx: 171.0px (65.14286dp)\n" +
                "\tgetHotseatLayoutPadding(context).top: 219.0px (83.42857dp)\n" +
                "\tgetHotseatLayoutPadding(context).bottom: 87.0px (33.142857dp)\n" +
                "\tgetHotseatLayoutPadding(context).left: 78.0px (29.714285dp)\n" +
                "\tgetHotseatLayoutPadding(context).right: 660.0px (251.42857dp)\n" +
                "\tnumShownHotseatIcons: 6\n" +
                "\thotseatBorderSpace: 57.0px (21.714285dp)\n" +
                "\tisQsbInline: false\n" +
                "\thotseatQsbWidth: 1236.0px (470.85715dp)\n" +
                "\tisTaskbarPresent:true\n" +
                "\tisTaskbarPresentInApps:true\n" +
                "\ttaskbarSize: 158.0px (60.190475dp)\n" +
                "\tdesiredWorkspaceHorizontalMarginPx: 58.0px (22.095238dp)\n" +
                "\tworkspacePadding.left: 37.0px (14.095238dp)\n" +
                "\tworkspacePadding.top: 68.0px (25.904762dp)\n" +
                "\tworkspacePadding.right: 37.0px (14.095238dp)\n" +
                "\tworkspacePadding.bottom: 615.0px (234.28572dp)\n" +
                "\ticonScale: 0.9978308px (0.38012603dp)\n" +
                "\tcellScaleToFit : 0.9978308px (0.38012603dp)\n" +
                "\textraSpace: 235.0px (89.52381dp)\n" +
                "\tunscaled extraSpace: 235.51086px (89.71842dp)\n" +
                "\tmaxEmptySpace: 236.0px (89.90476dp)\n" +
                "\tworkspaceTopPadding: 89.0px (33.904762dp)\n" +
                "\tworkspaceBottomPadding: 146.0px (55.61905dp)\n" +
                "\toverviewTaskMarginPx: 32.0px (12.190476dp)\n" +
                "\toverviewTaskIconSizePx: 126.0px (48.0dp)\n" +
                "\toverviewTaskIconDrawableSizePx: 116.0px (44.190475dp)\n" +
                "\toverviewTaskIconDrawableSizeGridPx: 116.0px (44.190475dp)\n" +
                "\toverviewTaskThumbnailTopMarginPx: 158.0px (60.190475dp)\n" +
                "\toverviewActionsTopMarginPx: 63.0px (24.0dp)\n" +
                "\toverviewActionsHeight: 126.0px (48.0dp)\n" +
                "\toverviewActionsButtonSpacing: 95.0px (36.190475dp)\n" +
                "\toverviewPageSpacing: 95.0px (36.190475dp)\n" +
                "\toverviewRowSpacing: 74.0px (28.190475dp)\n" +
                "\toverviewGridSideMargin: 168.0px (64.0dp)\n" +
                "\tdropTargetBarTopMarginPx: 168.0px (64.0dp)\n" +
                "\tdropTargetBarSizePx: 147.0px (56.0dp)\n" +
                "\tdropTargetBarBottomMarginPx: 42.0px (16.0dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkTop(): 467.0px (177.90475dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkBottom(): 1578.0px (601.1429dp)\n" +
                "\tworkspaceSpringLoadedMinNextPageVisiblePx: 63.0px (24.0dp)\n" +
                "\tgetWorkspaceSpringLoadScale(): 0.785159px (0.29910818dp)\n" +
                "\tgetCellLayoutHeight(): 1415.0px (539.0476dp)\n" +
                "\tgetCellLayoutWidth(): 883.0px (336.38095dp)\n")
    }

    @Test
    fun twoPanelPortrait() {
        initializeVarsForTwoPanel()
        val dp = newDP()
        dp.isTaskbarPresentInApps = true

        assertThat(dump(dp)).isEqualTo("DeviceProfile:\n" +
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
                "\tavailableHeightPx: 2098.0px (799.2381dp)\n" +
                "\tmInsets.left: 0.0px (0.0dp)\n" +
                "\tmInsets.top: 110.0px (41.904762dp)\n" +
                "\tmInsets.right: 0.0px (0.0dp)\n" +
                "\tmInsets.bottom: 0.0px (0.0dp)\n" +
                "\taspectRatio:1.2\n" +
                "\tisScalableGrid:true\n" +
                "\tinv.numRows: 4\n" +
                "\tinv.numColumns: 4\n" +
                "\tinv.numSearchContainerColumns: 4\n" +
                "\tminCellSize: PointF(68.0, 116.0)dp\n" +
                "\tcellWidthPx: 178.0px (67.809525dp)\n" +
                "\tcellHeightPx: 304.0px (115.809525dp)\n" +
                "\tgetCellSize().x: 178.0px (67.809525dp)\n" +
                "\tgetCellSize().y: 304.0px (115.809525dp)\n" +
                "\tcellLayoutBorderSpacePx Horizontal: 42.0px (16.0dp)\n" +
                "\tcellLayoutBorderSpacePx Vertical: 52.0px (19.809525dp)\n" +
                "\tcellLayoutPaddingPx.left: 21.0px (8.0dp)\n" +
                "\tcellLayoutPaddingPx.top: 21.0px (8.0dp)\n" +
                "\tcellLayoutPaddingPx.right: 21.0px (8.0dp)\n" +
                "\tcellLayoutPaddingPx.bottom: 21.0px (8.0dp)\n" +
                "\ticonSizePx: 136.0px (51.809525dp)\n" +
                "\ticonTextSizePx: 31.0px (11.809524dp)\n" +
                "\ticonDrawablePaddingPx: 17.0px (6.4761906dp)\n" +
                "\tfolderCellWidthPx: 210.0px (80.0dp)\n" +
                "\tfolderCellHeightPx: 247.0px (94.09524dp)\n" +
                "\tfolderChildIconSizePx: 158.0px (60.190475dp)\n" +
                "\tfolderChildTextSizePx: 37.0px (14.095238dp)\n" +
                "\tfolderChildDrawablePaddingPx: 13.0px (4.952381dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Horizontal: 42.0px (16.0dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Vertical: 42.0px (16.0dp)\n" +
                "\tfolderContentPaddingLeftRight: 42.0px (16.0dp)\n" +
                "\tfolderTopPadding: 63.0px (24.0dp)\n" +
                "\tbottomSheetTopPadding: 110.0px (41.904762dp)\n" +
                "\tallAppsShiftRange: 2098.0px (799.2381dp)\n" +
                "\tallAppsTopPadding: 110.0px (41.904762dp)\n" +
                "\tallAppsIconSizePx: 136.0px (51.809525dp)\n" +
                "\tallAppsIconTextSizePx: 31.0px (11.809524dp)\n" +
                "\tallAppsIconDrawablePaddingPx: 18.0px (6.857143dp)\n" +
                "\tallAppsCellHeightPx: 345.0px (131.42857dp)\n" +
                "\tallAppsCellWidthPx: 178.0px (67.809525dp)\n" +
                "\tallAppsBorderSpacePxX: 42.0px (16.0dp)\n" +
                "\tallAppsBorderSpacePxY: 73.0px (27.809525dp)\n" +
                "\tnumShownAllAppsColumns: 6\n" +
                "\tallAppsLeftRightPadding: 126.0px (48.0dp)\n" +
                "\tallAppsLeftRightMargin: 155.0px (59.04762dp)\n" +
                "\thotseatBarSizePx: 459.0px (174.85715dp)\n" +
                "\tinv.hotseatColumnSpan: 6\n" +
                "\thotseatCellHeightPx: 153.0px (58.285713dp)\n" +
                "\thotseatBarBottomSpacePx: 95.0px (36.190475dp)\n" +
                "\thotseatBarSidePaddingStartPx: 0.0px (0.0dp)\n" +
                "\thotseatBarSidePaddingEndPx: 0.0px (0.0dp)\n" +
                "\thotseatBarEndOffset: 0.0px (0.0dp)\n" +
                "\thotseatQsbSpace: 95.0px (36.190475dp)\n" +
                "\thotseatQsbHeight: 165.0px (62.857143dp)\n" +
                "\tspringLoadedHotseatBarTopMarginPx: 171.0px (65.14286dp)\n" +
                "\tgetHotseatLayoutPadding(context).top: 219.0px (83.42857dp)\n" +
                "\tgetHotseatLayoutPadding(context).bottom: 87.0px (33.142857dp)\n" +
                "\tgetHotseatLayoutPadding(context).left: 302.0px (115.04762dp)\n" +
                "\tgetHotseatLayoutPadding(context).right: 302.0px (115.04762dp)\n" +
                "\tnumShownHotseatIcons: 6\n" +
                "\thotseatBorderSpace: 84.0px (32.0dp)\n" +
                "\tisQsbInline: false\n" +
                "\thotseatQsbWidth: 1236.0px (470.85715dp)\n" +
                "\tisTaskbarPresent:true\n" +
                "\tisTaskbarPresentInApps:true\n" +
                "\ttaskbarSize: 158.0px (60.190475dp)\n" +
                "\tdesiredWorkspaceHorizontalMarginPx: 58.0px (22.095238dp)\n" +
                "\tworkspacePadding.left: 37.0px (14.095238dp)\n" +
                "\tworkspacePadding.top: 68.0px (25.904762dp)\n" +
                "\tworkspacePadding.right: 37.0px (14.095238dp)\n" +
                "\tworkspacePadding.bottom: 615.0px (234.28572dp)\n" +
                "\ticonScale: 0.9978308px (0.38012603dp)\n" +
                "\tcellScaleToFit : 0.9978308px (0.38012603dp)\n" +
                "\textraSpace: 235.0px (89.52381dp)\n" +
                "\tunscaled extraSpace: 235.51086px (89.71842dp)\n" +
                "\tmaxEmptySpace: 236.0px (89.90476dp)\n" +
                "\tworkspaceTopPadding: 89.0px (33.904762dp)\n" +
                "\tworkspaceBottomPadding: 146.0px (55.61905dp)\n" +
                "\toverviewTaskMarginPx: 32.0px (12.190476dp)\n" +
                "\toverviewTaskIconSizePx: 126.0px (48.0dp)\n" +
                "\toverviewTaskIconDrawableSizePx: 116.0px (44.190475dp)\n" +
                "\toverviewTaskIconDrawableSizeGridPx: 116.0px (44.190475dp)\n" +
                "\toverviewTaskThumbnailTopMarginPx: 158.0px (60.190475dp)\n" +
                "\toverviewActionsTopMarginPx: 63.0px (24.0dp)\n" +
                "\toverviewActionsHeight: 126.0px (48.0dp)\n" +
                "\toverviewActionsButtonSpacing: 95.0px (36.190475dp)\n" +
                "\toverviewPageSpacing: 95.0px (36.190475dp)\n" +
                "\toverviewRowSpacing: 74.0px (28.190475dp)\n" +
                "\toverviewGridSideMargin: 168.0px (64.0dp)\n" +
                "\tdropTargetBarTopMarginPx: 168.0px (64.0dp)\n" +
                "\tdropTargetBarSizePx: 147.0px (56.0dp)\n" +
                "\tdropTargetBarBottomMarginPx: 42.0px (16.0dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkTop(): 467.0px (177.90475dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkBottom(): 1578.0px (601.1429dp)\n" +
                "\tworkspaceSpringLoadedMinNextPageVisiblePx: 63.0px (24.0dp)\n" +
                "\tgetWorkspaceSpringLoadScale(): 0.785159px (0.29910818dp)\n" +
                "\tgetCellLayoutHeight(): 1415.0px (539.0476dp)\n" +
                "\tgetCellLayoutWidth(): 883.0px (336.38095dp)\n")
    }

    @Test
    fun phoneVerticalBar3Button() {
        initializeVarsForPhone(isVerticalBar = true, isGestureMode = false)
        val dp = newDP()

        assertThat(dump(dp)).isEqualTo("DeviceProfile:\n" +
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
                "\tisScalableGrid:false\n" +
                "\tinv.numRows: 5\n" +
                "\tinv.numColumns: 4\n" +
                "\tinv.numSearchContainerColumns: 4\n" +
                "\tminCellSize: PointF(80.0, 104.0)dp\n" +
                "\tcellWidthPx: 153.0px (58.285713dp)\n" +
                "\tcellHeightPx: 160.0px (60.95238dp)\n" +
                "\tgetCellSize().x: 461.0px (175.61905dp)\n" +
                "\tgetCellSize().y: 193.0px (73.52381dp)\n" +
                "\tcellLayoutBorderSpacePx Horizontal: 0.0px (0.0dp)\n" +
                "\tcellLayoutBorderSpacePx Vertical: 0.0px (0.0dp)\n" +
                "\tcellLayoutPaddingPx.left: 53.0px (20.190475dp)\n" +
                "\tcellLayoutPaddingPx.top: 0.0px (0.0dp)\n" +
                "\tcellLayoutPaddingPx.right: 53.0px (20.190475dp)\n" +
                "\tcellLayoutPaddingPx.bottom: 40.0px (15.238095dp)\n" +
                "\ticonSizePx: 142.0px (54.095238dp)\n" +
                "\ticonTextSizePx: 0.0px (0.0dp)\n" +
                "\ticonDrawablePaddingPx: 0.0px (0.0dp)\n" +
                "\tfolderCellWidthPx: 175.0px (66.666664dp)\n" +
                "\tfolderCellHeightPx: 205.0px (78.09524dp)\n" +
                "\tfolderChildIconSizePx: 131.0px (49.904762dp)\n" +
                "\tfolderChildTextSizePx: 34.0px (12.952381dp)\n" +
                "\tfolderChildDrawablePaddingPx: 9.0px (3.4285715dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Horizontal: 42.0px (16.0dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Vertical: 42.0px (16.0dp)\n" +
                "\tfolderContentPaddingLeftRight: 21.0px (8.0dp)\n" +
                "\tfolderTopPadding: 42.0px (16.0dp)\n" +
                "\tbottomSheetTopPadding: 114.0px (43.42857dp)\n" +
                "\tallAppsShiftRange: 788.0px (300.1905dp)\n" +
                "\tallAppsTopPadding: 0.0px (0.0dp)\n" +
                "\tallAppsIconSizePx: 158.0px (60.190475dp)\n" +
                "\tallAppsIconTextSizePx: 37.0px (14.095238dp)\n" +
                "\tallAppsIconDrawablePaddingPx: 21.0px (8.0dp)\n" +
                "\tallAppsCellHeightPx: 329.0px (125.333336dp)\n" +
                "\tallAppsCellWidthPx: 200.0px (76.190475dp)\n" +
                "\tallAppsBorderSpacePxX: 42.0px (16.0dp)\n" +
                "\tallAppsBorderSpacePxY: 42.0px (16.0dp)\n" +
                "\tnumShownAllAppsColumns: 4\n" +
                "\tallAppsLeftRightPadding: 0.0px (0.0dp)\n" +
                "\tallAppsLeftRightMargin: 0.0px (0.0dp)\n" +
                "\thotseatBarSizePx: 247.0px (94.09524dp)\n" +
                "\tinv.hotseatColumnSpan: 4\n" +
                "\thotseatCellHeightPx: 160.0px (60.95238dp)\n" +
                "\thotseatBarBottomSpacePx: 126.0px (48.0dp)\n" +
                "\thotseatBarSidePaddingStartPx: 63.0px (24.0dp)\n" +
                "\thotseatBarSidePaddingEndPx: 42.0px (16.0dp)\n" +
                "\thotseatBarEndOffset: 0.0px (0.0dp)\n" +
                "\thotseatQsbSpace: 95.0px (36.190475dp)\n" +
                "\thotseatQsbHeight: 165.0px (62.857143dp)\n" +
                "\tspringLoadedHotseatBarTopMarginPx: 118.0px (44.95238dp)\n" +
                "\tgetHotseatLayoutPadding(context).top: 65.0px (24.761906dp)\n" +
                "\tgetHotseatLayoutPadding(context).bottom: 48.0px (18.285715dp)\n" +
                "\tgetHotseatLayoutPadding(context).left: 42.0px (16.0dp)\n" +
                "\tgetHotseatLayoutPadding(context).right: 189.0px (72.0dp)\n" +
                "\tnumShownHotseatIcons: 4\n" +
                "\thotseatBorderSpace: 0.0px (0.0dp)\n" +
                "\tisQsbInline: false\n" +
                "\thotseatQsbWidth: 1525.0px (580.9524dp)\n" +
                "\tisTaskbarPresent:false\n" +
                "\tisTaskbarPresentInApps:false\n" +
                "\ttaskbarSize: 0.0px (0.0dp)\n" +
                "\tdesiredWorkspaceHorizontalMarginPx: 0.0px (0.0dp)\n" +
                "\tworkspacePadding.left: 10.0px (3.8095238dp)\n" +
                "\tworkspacePadding.top: 0.0px (0.0dp)\n" +
                "\tworkspacePadding.right: 194.0px (73.90476dp)\n" +
                "\tworkspacePadding.bottom: 0.0px (0.0dp)\n" +
                "\ticonScale: 1.0px (0.3809524dp)\n" +
                "\tcellScaleToFit : 1.0px (0.3809524dp)\n" +
                "\textraSpace: 166.0px (63.238094dp)\n" +
                "\tunscaled extraSpace: 166.0px (63.238094dp)\n" +
                "\tmaxEmptySpace: 184.0px (70.09524dp)\n" +
                "\tworkspaceTopPadding: 0.0px (0.0dp)\n" +
                "\tworkspaceBottomPadding: 0.0px (0.0dp)\n" +
                "\toverviewTaskMarginPx: 42.0px (16.0dp)\n" +
                "\toverviewTaskIconSizePx: 126.0px (48.0dp)\n" +
                "\toverviewTaskIconDrawableSizePx: 116.0px (44.190475dp)\n" +
                "\toverviewTaskIconDrawableSizeGridPx: 0.0px (0.0dp)\n" +
                "\toverviewTaskThumbnailTopMarginPx: 168.0px (64.0dp)\n" +
                "\toverviewActionsTopMarginPx: 32.0px (12.190476dp)\n" +
                "\toverviewActionsHeight: 126.0px (48.0dp)\n" +
                "\toverviewActionsButtonSpacing: 95.0px (36.190475dp)\n" +
                "\toverviewPageSpacing: 42.0px (16.0dp)\n" +
                "\toverviewRowSpacing: 0.0px (0.0dp)\n" +
                "\toverviewGridSideMargin: 0.0px (0.0dp)\n" +
                "\tdropTargetBarTopMarginPx: 16.0px (6.095238dp)\n" +
                "\tdropTargetBarSizePx: 95.0px (36.190475dp)\n" +
                "\tdropTargetBarBottomMarginPx: 16.0px (6.095238dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkTop(): 201.0px (76.57143dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkBottom(): 983.0px (374.4762dp)\n" +
                "\tworkspaceSpringLoadedMinNextPageVisiblePx: 63.0px (24.0dp)\n" +
                "\tgetWorkspaceSpringLoadScale(): 0.777336px (0.296128dp)\n" +
                "\tgetCellLayoutHeight(): 1006.0px (383.2381dp)\n" +
                "\tgetCellLayoutWidth(): 1952.0px (743.619dp)\n")
    }

    @Test
    fun phoneVerticalBar() {
        initializeVarsForPhone(isVerticalBar = true)
        val dp = newDP()

        assertThat(dump(dp)).isEqualTo("DeviceProfile:\n" +
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
                "\tisScalableGrid:false\n" +
                "\tinv.numRows: 5\n" +
                "\tinv.numColumns: 4\n" +
                "\tinv.numSearchContainerColumns: 4\n" +
                "\tminCellSize: PointF(80.0, 104.0)dp\n" +
                "\tcellWidthPx: 153.0px (58.285713dp)\n" +
                "\tcellHeightPx: 160.0px (60.95238dp)\n" +
                "\tgetCellSize().x: 493.0px (187.80952dp)\n" +
                "\tgetCellSize().y: 180.0px (68.57143dp)\n" +
                "\tcellLayoutBorderSpacePx Horizontal: 0.0px (0.0dp)\n" +
                "\tcellLayoutBorderSpacePx Vertical: 0.0px (0.0dp)\n" +
                "\tcellLayoutPaddingPx.left: 53.0px (20.190475dp)\n" +
                "\tcellLayoutPaddingPx.top: 0.0px (0.0dp)\n" +
                "\tcellLayoutPaddingPx.right: 53.0px (20.190475dp)\n" +
                "\tcellLayoutPaddingPx.bottom: 40.0px (15.238095dp)\n" +
                "\ticonSizePx: 142.0px (54.095238dp)\n" +
                "\ticonTextSizePx: 0.0px (0.0dp)\n" +
                "\ticonDrawablePaddingPx: 0.0px (0.0dp)\n" +
                "\tfolderCellWidthPx: 159.0px (60.57143dp)\n" +
                "\tfolderCellHeightPx: 187.0px (71.2381dp)\n" +
                "\tfolderChildIconSizePx: 119.0px (45.333332dp)\n" +
                "\tfolderChildTextSizePx: 31.0px (11.809524dp)\n" +
                "\tfolderChildDrawablePaddingPx: 8.0px (3.047619dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Horizontal: 42.0px (16.0dp)\n" +
                "\tfolderCellLayoutBorderSpacePx Vertical: 42.0px (16.0dp)\n" +
                "\tfolderContentPaddingLeftRight: 21.0px (8.0dp)\n" +
                "\tfolderTopPadding: 42.0px (16.0dp)\n" +
                "\tbottomSheetTopPadding: 114.0px (43.42857dp)\n" +
                "\tallAppsShiftRange: 788.0px (300.1905dp)\n" +
                "\tallAppsTopPadding: 0.0px (0.0dp)\n" +
                "\tallAppsIconSizePx: 158.0px (60.190475dp)\n" +
                "\tallAppsIconTextSizePx: 37.0px (14.095238dp)\n" +
                "\tallAppsIconDrawablePaddingPx: 21.0px (8.0dp)\n" +
                "\tallAppsCellHeightPx: 329.0px (125.333336dp)\n" +
                "\tallAppsCellWidthPx: 200.0px (76.190475dp)\n" +
                "\tallAppsBorderSpacePxX: 42.0px (16.0dp)\n" +
                "\tallAppsBorderSpacePxY: 42.0px (16.0dp)\n" +
                "\tnumShownAllAppsColumns: 4\n" +
                "\tallAppsLeftRightPadding: 0.0px (0.0dp)\n" +
                "\tallAppsLeftRightMargin: 0.0px (0.0dp)\n" +
                "\thotseatBarSizePx: 247.0px (94.09524dp)\n" +
                "\tinv.hotseatColumnSpan: 4\n" +
                "\thotseatCellHeightPx: 160.0px (60.95238dp)\n" +
                "\thotseatBarBottomSpacePx: 126.0px (48.0dp)\n" +
                "\thotseatBarSidePaddingStartPx: 63.0px (24.0dp)\n" +
                "\thotseatBarSidePaddingEndPx: 42.0px (16.0dp)\n" +
                "\thotseatBarEndOffset: 0.0px (0.0dp)\n" +
                "\thotseatQsbSpace: 95.0px (36.190475dp)\n" +
                "\thotseatQsbHeight: 165.0px (62.857143dp)\n" +
                "\tspringLoadedHotseatBarTopMarginPx: 118.0px (44.95238dp)\n" +
                "\tgetHotseatLayoutPadding(context).top: 65.0px (24.761906dp)\n" +
                "\tgetHotseatLayoutPadding(context).bottom: 111.0px (42.285713dp)\n" +
                "\tgetHotseatLayoutPadding(context).left: 42.0px (16.0dp)\n" +
                "\tgetHotseatLayoutPadding(context).right: 63.0px (24.0dp)\n" +
                "\tnumShownHotseatIcons: 4\n" +
                "\thotseatBorderSpace: 0.0px (0.0dp)\n" +
                "\tisQsbInline: false\n" +
                "\thotseatQsbWidth: 1621.0px (617.5238dp)\n" +
                "\tisTaskbarPresent:false\n" +
                "\tisTaskbarPresentInApps:false\n" +
                "\ttaskbarSize: 0.0px (0.0dp)\n" +
                "\tdesiredWorkspaceHorizontalMarginPx: 0.0px (0.0dp)\n" +
                "\tworkspacePadding.left: 10.0px (3.8095238dp)\n" +
                "\tworkspacePadding.top: 0.0px (0.0dp)\n" +
                "\tworkspacePadding.right: 194.0px (73.90476dp)\n" +
                "\tworkspacePadding.bottom: 0.0px (0.0dp)\n" +
                "\ticonScale: 1.0px (0.3809524dp)\n" +
                "\tcellScaleToFit : 1.0px (0.3809524dp)\n" +
                "\textraSpace: 103.0px (39.238094dp)\n" +
                "\tunscaled extraSpace: 103.0px (39.238094dp)\n" +
                "\tmaxEmptySpace: 131.0px (49.904762dp)\n" +
                "\tworkspaceTopPadding: 0.0px (0.0dp)\n" +
                "\tworkspaceBottomPadding: 0.0px (0.0dp)\n" +
                "\toverviewTaskMarginPx: 42.0px (16.0dp)\n" +
                "\toverviewTaskIconSizePx: 126.0px (48.0dp)\n" +
                "\toverviewTaskIconDrawableSizePx: 116.0px (44.190475dp)\n" +
                "\toverviewTaskIconDrawableSizeGridPx: 0.0px (0.0dp)\n" +
                "\toverviewTaskThumbnailTopMarginPx: 168.0px (64.0dp)\n" +
                "\toverviewActionsTopMarginPx: 32.0px (12.190476dp)\n" +
                "\toverviewActionsHeight: 126.0px (48.0dp)\n" +
                "\toverviewActionsButtonSpacing: 95.0px (36.190475dp)\n" +
                "\toverviewPageSpacing: 42.0px (16.0dp)\n" +
                "\toverviewRowSpacing: 0.0px (0.0dp)\n" +
                "\toverviewGridSideMargin: 0.0px (0.0dp)\n" +
                "\tdropTargetBarTopMarginPx: 16.0px (6.095238dp)\n" +
                "\tdropTargetBarSizePx: 95.0px (36.190475dp)\n" +
                "\tdropTargetBarBottomMarginPx: 16.0px (6.095238dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkTop(): 201.0px (76.57143dp)\n" +
                "\tgetCellLayoutSpringLoadShrunkBottom(): 927.0px (353.14285dp)\n" +
                "\tworkspaceSpringLoadedMinNextPageVisiblePx: 63.0px (24.0dp)\n" +
                "\tgetWorkspaceSpringLoadScale(): 0.76988333px (0.2932889dp)\n" +
                "\tgetCellLayoutHeight(): 943.0px (359.2381dp)\n" +
                "\tgetCellLayoutWidth(): 2078.0px (791.619dp)\n")
    }
}