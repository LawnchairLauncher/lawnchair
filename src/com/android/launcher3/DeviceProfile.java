/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import static com.android.launcher3.InvariantDeviceProfile.INDEX_DEFAULT;
import static com.android.launcher3.InvariantDeviceProfile.INDEX_LANDSCAPE;
import static com.android.launcher3.InvariantDeviceProfile.INDEX_TWO_PANEL_LANDSCAPE;
import static com.android.launcher3.InvariantDeviceProfile.INDEX_TWO_PANEL_PORTRAIT;
import static com.android.launcher3.ResourceUtils.pxFromDp;
import static com.android.launcher3.Utilities.dpiFromPx;
import static com.android.launcher3.Utilities.pxFromSp;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.ICON_OVERLAP_FACTOR;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Surface;

import com.android.launcher3.CellLayout.ContainerType;
import com.android.launcher3.DevicePaddings.DevicePadding;
import com.android.launcher3.icons.DotRenderer;
import com.android.launcher3.icons.GraphicsUtils;
import com.android.launcher3.icons.IconNormalizer;
import com.android.launcher3.touch.PortraitPagedViewHandler;
import com.android.launcher3.uioverrides.ApiWrapper;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.DisplayController.Info;
import com.android.launcher3.util.WindowBounds;

import java.io.PrintWriter;
import java.util.List;

@SuppressLint("NewApi")
public class DeviceProfile {

    private static final int DEFAULT_DOT_SIZE = 100;
    // Ratio of empty space, qsb should take up to appear visually centered.
    private final float mQsbCenterFactor;

    public final InvariantDeviceProfile inv;
    private final Info mInfo;
    private final DisplayMetrics mMetrics;

    // Device properties
    public final boolean isTablet;
    public final boolean isPhone;
    public final boolean transposeLayoutWithOrientation;
    public final boolean isTwoPanels;
    public final boolean isQsbInline;

    // Device properties in current orientation
    public final boolean isLandscape;
    public final boolean isMultiWindowMode;
    public final boolean isGestureMode;

    public final int windowX;
    public final int windowY;
    public final int widthPx;
    public final int heightPx;
    public final int availableWidthPx;
    public final int availableHeightPx;
    public final int rotationHint;

    public final float aspectRatio;

    public final boolean isScalableGrid;
    private final int mTypeIndex;

    /**
     * The maximum amount of left/right workspace padding as a percentage of the screen width.
     * To be clear, this means that up to 7% of the screen width can be used as left padding, and
     * 7% of the screen width can be used as right padding.
     */
    private static final float MAX_HORIZONTAL_PADDING_PERCENT = 0.14f;

    private static final float TALL_DEVICE_ASPECT_RATIO_THRESHOLD = 2.0f;
    private static final float TALLER_DEVICE_ASPECT_RATIO_THRESHOLD = 2.15f;
    private static final float TALL_DEVICE_EXTRA_SPACE_THRESHOLD_DP = 252;
    private static final float TALL_DEVICE_MORE_EXTRA_SPACE_THRESHOLD_DP = 268;

    // Workspace
    public final int desiredWorkspaceHorizontalMarginOriginalPx;
    public int desiredWorkspaceHorizontalMarginPx;
    public int gridVisualizationPaddingX;
    public int gridVisualizationPaddingY;
    public Point cellLayoutBorderSpaceOriginalPx;
    public Point cellLayoutBorderSpacePx;
    public Rect cellLayoutPaddingPx = new Rect();

    public final int edgeMarginPx;
    public final float workspaceContentScale;
    private float mWorkspaceSpringLoadShrunkTop;
    private float mWorkspaceSpringLoadShrunkBottom;
    public final int workspaceSpringLoadedMinNextPageVisiblePx;

    private final int extraSpace;
    public int workspaceTopPadding;
    public int workspaceBottomPadding;

    // Workspace page indicator
    public final int workspacePageIndicatorHeight;
    private final int mWorkspacePageIndicatorOverlapWorkspace;

    // Workspace icons
    public float iconScale;
    public int iconSizePx;
    public int iconTextSizePx;
    public int iconDrawablePaddingPx;
    public int iconDrawablePaddingOriginalPx;

    public float cellScaleToFit;
    public int cellWidthPx;
    public int cellHeightPx;
    public int workspaceCellPaddingXPx;

    public int cellYPaddingPx;

    // Folder
    public float folderLabelTextScale;
    public int folderLabelTextSizePx;
    public int folderIconSizePx;
    public int folderIconOffsetYPx;

    // Folder content
    public Point folderCellLayoutBorderSpacePx;
    public int folderCellLayoutBorderSpaceOriginalPx;
    public int folderContentPaddingLeftRight;
    public int folderContentPaddingTop;

    // Folder cell
    public int folderCellWidthPx;
    public int folderCellHeightPx;

    // Folder child
    public int folderChildIconSizePx;
    public int folderChildTextSizePx;
    public int folderChildDrawablePaddingPx;

    // Hotseat
    public final int numShownHotseatIcons;
    public int hotseatCellHeightPx;
    public final boolean areNavButtonsInline;
    // In portrait: size = height, in landscape: size = width
    public int hotseatBarSizePx;
    public int hotseatBarBottomSpacePx;
    public int hotseatBarEndOffset;
    public int hotseatQsbSpace;
    public int springLoadedHotseatBarTopMarginPx;
    // Start is the side next to the nav bar, end is the side next to the workspace
    public final int hotseatBarSidePaddingStartPx;
    public final int hotseatBarSidePaddingEndPx;
    public final int hotseatQsbHeight;
    public final int hotseatQsbVisualHeight;
    private final int hotseatQsbShadowHeight;
    public int hotseatBorderSpace;

    public int qsbWidth; // only used when isQsbInline

    // All apps
    public Point allAppsBorderSpacePx;
    public int allAppsShiftRange;
    public int allAppsTopPadding;
    public int bottomSheetTopPadding;
    public int allAppsCellHeightPx;
    public int allAppsCellWidthPx;
    public int allAppsIconSizePx;
    public int allAppsIconDrawablePaddingPx;
    public int allAppsLeftRightPadding;
    public int allAppsLeftRightMargin;
    public final int numShownAllAppsColumns;
    public float allAppsIconTextSizePx;

    // Overview
    public int overviewTaskMarginPx;
    public int overviewTaskMarginGridPx;
    public int overviewTaskIconSizePx;
    public int overviewTaskIconDrawableSizePx;
    public int overviewTaskIconDrawableSizeGridPx;
    public int overviewTaskThumbnailTopMarginPx;
    public final int overviewActionsHeight;
    public final int overviewActionsTopMarginPx;
    public final int overviewActionsButtonSpacing;
    public int overviewPageSpacing;
    public int overviewRowSpacing;
    public int overviewGridSideMargin;
    public final Rect overviewGridRect = new Rect();
    public final Rect overviewTaskRect = new Rect();
    public final float overviewTaskWorkspaceScale;
    public final Point overviewGridTaskDimension = new Point();
    public final Rect overviewModalTaskRect = new Rect();
    public final float overviewModalTaskScale;

    // Widgets
    public final PointF appWidgetScale = new PointF(1.0f, 1.0f);

    // Drop Target
    public int dropTargetBarSizePx;
    public int dropTargetBarTopMarginPx;
    public int dropTargetBarBottomMarginPx;
    public int dropTargetDragPaddingPx;
    public int dropTargetTextSizePx;
    public int dropTargetHorizontalPaddingPx;
    public int dropTargetVerticalPaddingPx;
    public int dropTargetGapPx;
    public int dropTargetButtonWorkspaceEdgeGapPx;

    // Insets
    private final Rect mInsets = new Rect();
    public final Rect workspacePadding = new Rect();
    private final Rect mHotseatBarPadding = new Rect();
    // When true, nav bar is on the left side of the screen.
    private boolean mIsSeascape;

    // Notification dots
    public DotRenderer mDotRendererWorkSpace;
    public DotRenderer mDotRendererAllApps;

    // Tasks
    public final PointF taskDimension = new PointF();

    // Taskbar
    public boolean isTaskbarPresent;
    // Whether Taskbar will inset the bottom of apps by taskbarSize.
    public boolean isTaskbarPresentInApps;
    public int taskbarSize;
    public int stashedTaskbarSize;

    // DragController
    public int flingToDeleteThresholdVelocity;

    /** TODO: Once we fully migrate to staged split, remove "isMultiWindowMode" */
    DeviceProfile(Context context, InvariantDeviceProfile inv, Info info, WindowBounds windowBounds,
            boolean isMultiWindowMode, boolean transposeLayoutWithOrientation,
            boolean useTwoPanels, boolean isGestureMode) {

        this.inv = inv;
        this.isLandscape = windowBounds.isLandscape();
        this.isMultiWindowMode = isMultiWindowMode;
        this.transposeLayoutWithOrientation = transposeLayoutWithOrientation;
        this.isGestureMode = isGestureMode;
        windowX = windowBounds.bounds.left;
        windowY = windowBounds.bounds.top;
        this.rotationHint = windowBounds.rotationHint;
        mInsets.set(windowBounds.insets);

        isScalableGrid = inv.isScalable && !isVerticalBarLayout() && !isMultiWindowMode;
        // Determine device posture.
        mInfo = info;
        isTablet = info.isTablet(windowBounds);
        isPhone = !isTablet;
        isTwoPanels = isTablet && useTwoPanels;
        isTaskbarPresent = isTablet && ApiWrapper.TASKBAR_DRAWN_IN_PROCESS;

        // Some more constants.
        context = getContext(context, info, isVerticalBarLayout() || (isTablet && isLandscape)
                        ? Configuration.ORIENTATION_LANDSCAPE
                        : Configuration.ORIENTATION_PORTRAIT,
                windowBounds);
        final Resources res = context.getResources();
        mMetrics = res.getDisplayMetrics();

        // Determine sizes.
        widthPx = windowBounds.bounds.width();
        heightPx = windowBounds.bounds.height();
        availableWidthPx = windowBounds.availableSize.x;
        availableHeightPx = windowBounds.availableSize.y;

        aspectRatio = ((float) Math.max(widthPx, heightPx)) / Math.min(widthPx, heightPx);
        boolean isTallDevice = Float.compare(aspectRatio, TALL_DEVICE_ASPECT_RATIO_THRESHOLD) >= 0;
        mQsbCenterFactor = res.getFloat(R.dimen.qsb_center_factor);

        if (isTwoPanels) {
            if (isLandscape) {
                mTypeIndex = INDEX_TWO_PANEL_LANDSCAPE;
            } else {
                mTypeIndex = INDEX_TWO_PANEL_PORTRAIT;
            }
        } else {
            if (isLandscape) {
                mTypeIndex = INDEX_LANDSCAPE;
            } else {
                mTypeIndex = INDEX_DEFAULT;
            }
        }

        if (isTaskbarPresent) {
            taskbarSize = res.getDimensionPixelSize(R.dimen.taskbar_size);
            stashedTaskbarSize = res.getDimensionPixelSize(R.dimen.taskbar_stashed_size);
        }

        edgeMarginPx = res.getDimensionPixelSize(R.dimen.dynamic_grid_edge_margin);
        workspaceContentScale = res.getFloat(R.dimen.workspace_content_scale);

        desiredWorkspaceHorizontalMarginPx = getHorizontalMarginPx(inv, res);
        desiredWorkspaceHorizontalMarginOriginalPx = desiredWorkspaceHorizontalMarginPx;
        gridVisualizationPaddingX = res.getDimensionPixelSize(
                R.dimen.grid_visualization_horizontal_cell_spacing);
        gridVisualizationPaddingY = res.getDimensionPixelSize(
                R.dimen.grid_visualization_vertical_cell_spacing);

        bottomSheetTopPadding = mInsets.top // statusbar height
                + res.getDimensionPixelSize(R.dimen.bottom_sheet_extra_top_padding)
                + (isTablet ? 0 : edgeMarginPx); // phones need edgeMarginPx additional padding

        allAppsTopPadding = isTablet ? bottomSheetTopPadding : 0;
        allAppsShiftRange = isTablet
                ? heightPx - allAppsTopPadding
                : res.getDimensionPixelSize(R.dimen.all_apps_starting_vertical_translate);
        folderLabelTextScale = res.getFloat(R.dimen.folder_label_text_scale);
        folderContentPaddingLeftRight =
                res.getDimensionPixelSize(R.dimen.folder_content_padding_left_right);
        folderContentPaddingTop = res.getDimensionPixelSize(R.dimen.folder_content_padding_top);

        cellLayoutBorderSpacePx = getCellLayoutBorderSpace(inv);
        allAppsBorderSpacePx = new Point(
                pxFromDp(inv.allAppsBorderSpaces[mTypeIndex].x, mMetrics),
                pxFromDp(inv.allAppsBorderSpaces[mTypeIndex].y, mMetrics));
        cellLayoutBorderSpaceOriginalPx = new Point(cellLayoutBorderSpacePx);
        folderCellLayoutBorderSpaceOriginalPx = pxFromDp(inv.folderBorderSpace, mMetrics);
        folderCellLayoutBorderSpacePx = new Point(folderCellLayoutBorderSpaceOriginalPx,
                folderCellLayoutBorderSpaceOriginalPx);

        workspacePageIndicatorHeight = res.getDimensionPixelSize(
                R.dimen.workspace_page_indicator_height);
        mWorkspacePageIndicatorOverlapWorkspace =
                res.getDimensionPixelSize(R.dimen.workspace_page_indicator_overlap_workspace);

        iconDrawablePaddingOriginalPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_icon_drawable_padding);

        dropTargetBarSizePx = res.getDimensionPixelSize(R.dimen.dynamic_grid_drop_target_size);
        dropTargetBarTopMarginPx = res.getDimensionPixelSize(R.dimen.drop_target_top_margin);
        dropTargetBarBottomMarginPx = res.getDimensionPixelSize(R.dimen.drop_target_bottom_margin);
        dropTargetDragPaddingPx = res.getDimensionPixelSize(R.dimen.drop_target_drag_padding);
        dropTargetTextSizePx = res.getDimensionPixelSize(R.dimen.drop_target_text_size);
        dropTargetHorizontalPaddingPx = res.getDimensionPixelSize(
                R.dimen.drop_target_button_drawable_horizontal_padding);
        dropTargetVerticalPaddingPx = res.getDimensionPixelSize(
                R.dimen.drop_target_button_drawable_vertical_padding);
        dropTargetGapPx = res.getDimensionPixelSize(R.dimen.drop_target_button_gap);
        dropTargetButtonWorkspaceEdgeGapPx = res.getDimensionPixelSize(
                R.dimen.drop_target_button_workspace_edge_gap);

        workspaceSpringLoadedMinNextPageVisiblePx = res.getDimensionPixelSize(
                R.dimen.dynamic_grid_spring_loaded_min_next_space_visible);

        workspaceCellPaddingXPx = res.getDimensionPixelSize(R.dimen.dynamic_grid_cell_padding_x);

        hotseatQsbHeight = res.getDimensionPixelSize(R.dimen.qsb_widget_height);
        hotseatQsbShadowHeight = res.getDimensionPixelSize(R.dimen.qsb_shadow_height);
        hotseatQsbVisualHeight = hotseatQsbHeight - 2 * hotseatQsbShadowHeight;

        // Whether QSB might be inline in appropriate orientation (e.g. landscape).
        boolean canQsbInline = (isTwoPanels ? inv.inlineQsb[INDEX_TWO_PANEL_PORTRAIT]
                || inv.inlineQsb[INDEX_TWO_PANEL_LANDSCAPE]
                : inv.inlineQsb[INDEX_DEFAULT] || inv.inlineQsb[INDEX_LANDSCAPE])
                && hotseatQsbHeight > 0;
        isQsbInline = inv.inlineQsb[mTypeIndex] && canQsbInline;

        // We shrink hotseat sizes regardless of orientation, if nav buttons are inline and QSB
        // might be inline in either orientations, to keep hotseat size consistent across rotation.
        areNavButtonsInline = isTaskbarPresent && !isGestureMode;
        if (areNavButtonsInline && canQsbInline) {
            numShownHotseatIcons = inv.numShrunkenHotseatIcons;
        } else {
            numShownHotseatIcons =
                    isTwoPanels ? inv.numDatabaseHotseatIcons : inv.numShownHotseatIcons;
        }

        numShownAllAppsColumns =
                isTwoPanels ? inv.numDatabaseAllAppsColumns : inv.numAllAppsColumns;

        int hotseatBarBottomSpace = pxFromDp(inv.hotseatBarBottomSpace[mTypeIndex], mMetrics);
        int minQsbMargin = res.getDimensionPixelSize(R.dimen.min_qsb_margin);
        hotseatQsbSpace = pxFromDp(inv.hotseatQsbSpace[mTypeIndex], mMetrics);
        // Have a little space between the inset and the QSB
        if (mInsets.bottom + minQsbMargin > hotseatBarBottomSpace) {
            int availableSpace = hotseatQsbSpace - (mInsets.bottom - hotseatBarBottomSpace);

            // Only change the spaces if there is space
            if (availableSpace > 0) {
                // Make sure there is enough space between hotseat/QSB and QSB/navBar
                if (availableSpace < minQsbMargin * 2) {
                    minQsbMargin = availableSpace / 2;
                    hotseatQsbSpace = minQsbMargin;
                } else {
                    hotseatQsbSpace -= minQsbMargin;
                }
            }
            hotseatBarBottomSpacePx = mInsets.bottom + minQsbMargin;

        } else {
            hotseatBarBottomSpacePx = hotseatBarBottomSpace;
        }

        springLoadedHotseatBarTopMarginPx = res.getDimensionPixelSize(
                R.dimen.spring_loaded_hotseat_top_margin);
        hotseatBarSidePaddingEndPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_hotseat_side_padding);
        // Add a bit of space between nav bar and hotseat in vertical bar layout.
        hotseatBarSidePaddingStartPx = isVerticalBarLayout() ? workspacePageIndicatorHeight : 0;
        updateHotseatSizes(pxFromDp(inv.iconSize[INDEX_DEFAULT], mMetrics));
        if (areNavButtonsInline) {
            /*
             * 3 nav buttons +
             * Little space at the end for contextual buttons +
             * Little space between icons and nav buttons
             */
            hotseatBarEndOffset = 3 * res.getDimensionPixelSize(R.dimen.taskbar_nav_buttons_size)
                    + res.getDimensionPixelSize(R.dimen.taskbar_contextual_button_margin)
                    + res.getDimensionPixelSize(R.dimen.taskbar_hotseat_nav_spacing);
        } else {
            hotseatBarEndOffset = 0;
        }

        overviewTaskMarginPx = res.getDimensionPixelSize(R.dimen.overview_task_margin);
        overviewTaskMarginGridPx = res.getDimensionPixelSize(R.dimen.overview_task_margin_grid);
        overviewTaskIconSizePx = res.getDimensionPixelSize(R.dimen.task_thumbnail_icon_size);
        overviewTaskIconDrawableSizePx =
                res.getDimensionPixelSize(R.dimen.task_thumbnail_icon_drawable_size);
        overviewTaskIconDrawableSizeGridPx =
                res.getDimensionPixelSize(R.dimen.task_thumbnail_icon_drawable_size_grid);
        overviewTaskThumbnailTopMarginPx = overviewTaskIconSizePx + overviewTaskMarginPx * 2;
        overviewActionsTopMarginPx = res.getDimensionPixelSize(R.dimen.overview_actions_top_margin);
        overviewPageSpacing = res.getDimensionPixelSize(R.dimen.overview_page_spacing);
        overviewActionsButtonSpacing = res.getDimensionPixelSize(
                R.dimen.overview_actions_button_spacing);
        overviewActionsHeight = res.getDimensionPixelSize(R.dimen.overview_actions_height);
        // Grid task's top margin is only overviewTaskIconSizePx + overviewTaskMarginGridPx, but
        // overviewTaskThumbnailTopMarginPx is applied to all TaskThumbnailView, so exclude the
        // extra  margin when calculating row spacing.
        int extraTopMargin = overviewTaskThumbnailTopMarginPx - overviewTaskIconSizePx
                - overviewTaskMarginGridPx;
        overviewRowSpacing = res.getDimensionPixelSize(R.dimen.overview_grid_row_spacing)
                - extraTopMargin;
        overviewGridSideMargin = res.getDimensionPixelSize(R.dimen.overview_grid_side_margin);

        // Calculate all of the remaining variables.
        extraSpace = updateAvailableDimensions(res);

        // Now that we have all of the variables calculated, we can tune certain sizes.
        if (isScalableGrid && inv.devicePaddings != null) {
            // Paddings were created assuming no scaling, so we first unscale the extra space.
            int unscaledExtraSpace = (int) (extraSpace / cellScaleToFit);
            DevicePadding padding = inv.devicePaddings.getDevicePadding(unscaledExtraSpace);

            int paddingWorkspaceTop = padding.getWorkspaceTopPadding(unscaledExtraSpace);
            int paddingWorkspaceBottom = padding.getWorkspaceBottomPadding(unscaledExtraSpace);
            int paddingHotseatBottom = padding.getHotseatBottomPadding(unscaledExtraSpace);

            workspaceTopPadding = Math.round(paddingWorkspaceTop * cellScaleToFit);
            workspaceBottomPadding = Math.round(paddingWorkspaceBottom * cellScaleToFit);
        }

        int cellLayoutPadding =
                isTwoPanels ? cellLayoutBorderSpacePx.x / 2 : res.getDimensionPixelSize(
                        R.dimen.cell_layout_padding);
        cellLayoutPaddingPx = new Rect(cellLayoutPadding, cellLayoutPadding, cellLayoutPadding,
                cellLayoutPadding);
        updateWorkspacePadding();

        // Hotseat and QSB width depends on updated cellSize and workspace padding
        hotseatBorderSpace = calculateHotseatBorderSpace();
        qsbWidth = calculateQsbWidth();

        flingToDeleteThresholdVelocity = res.getDimensionPixelSize(
                R.dimen.drag_flingToDeleteMinVelocity);

        // This is done last, after iconSizePx is calculated above.
        Path dotPath = GraphicsUtils.getShapePath(DEFAULT_DOT_SIZE);
        mDotRendererWorkSpace = new DotRenderer(iconSizePx, dotPath, DEFAULT_DOT_SIZE);
        mDotRendererAllApps = iconSizePx == allAppsIconSizePx ? mDotRendererWorkSpace :
                new DotRenderer(allAppsIconSizePx, dotPath, DEFAULT_DOT_SIZE);

        // Grid and Task size calculations
        calculateGridSize();
        getTaskDimension();
        calculateTaskSize(res);
        calculateGridTaskSize();
        calculateModalTaskSize(res);
        overviewModalTaskScale =
                Math.min(overviewModalTaskRect.height() / (float) overviewTaskRect.height(),
                        overviewModalTaskRect.width() / (float) overviewTaskRect.width());
        overviewTaskWorkspaceScale = (float) overviewTaskRect.height() / getCellLayoutHeight();
    }

    private void calculateGridSize() {
        int topMargin = overviewTaskThumbnailTopMarginPx;
        int bottomMargin = getOverviewActionsClaimedSpace();
        int sideMargin = overviewGridSideMargin;

        overviewGridRect.set(0, 0, widthPx, heightPx);
        overviewGridRect.inset(Math.max(mInsets.left, sideMargin), mInsets.top + topMargin,
                Math.max(mInsets.right, sideMargin), Math.max(mInsets.bottom, bottomMargin));
    }

    private void calculateTaskSize(Resources res) {
        int overviewMinNextPrevSize =
                res.getDimensionPixelSize(R.dimen.overview_minimum_next_prev_size);
        float overviewMaxScale = res.getFloat(R.dimen.overview_max_scale);
        Rect containerRect = new Rect();
        if (isTablet) {
            containerRect.set(overviewGridRect);
        } else {
            int taskMargin = overviewTaskMarginPx;
            containerRect.set(0, 0, widthPx, heightPx);
            containerRect.inset(mInsets.left, mInsets.top, mInsets.right, mInsets.bottom);
            int minimumHorizontalPadding = overviewMinNextPrevSize + taskMargin;
            containerRect.inset(minimumHorizontalPadding, overviewTaskThumbnailTopMarginPx,
                    minimumHorizontalPadding, getOverviewActionsClaimedSpace());
        }
        float scale = Math.min(
                containerRect.width() / taskDimension.x,
                containerRect.height() / taskDimension.y);
        scale = Math.min(scale, overviewMaxScale);
        int outWidth = Math.round(scale * taskDimension.x);
        int outHeight = Math.round(scale * taskDimension.y);
        Gravity.apply(Gravity.CENTER, outWidth, outHeight, containerRect, overviewTaskRect);
    }

    private void calculateGridTaskSize() {
        float rowHeight =
                (overviewTaskRect.height() + overviewTaskThumbnailTopMarginPx - overviewRowSpacing)
                        / 2f;

        float scale = (rowHeight - overviewTaskThumbnailTopMarginPx) / taskDimension.y;
        overviewGridTaskDimension.set(
                Math.round(scale * taskDimension.x), Math.round(scale * taskDimension.y));
    }

    /**
     * Returns a Rect the size of a grid task with the correct positioning within the screen.
     *
     * @param isRecentsRtl is true when device is in LTR, false when in RTL, as grid tasks are only
     *                     supported on tablets, which use PortraitPagedViewHandler.
     */
    public Rect getOverviewGridTaskRect(boolean isRecentsRtl) {
        Rect outRect = new Rect();
        int gravity = Gravity.TOP;
        gravity |= isRecentsRtl ? Gravity.RIGHT : Gravity.LEFT;
        Gravity.apply(gravity, overviewGridTaskDimension.x, overviewGridTaskDimension.y,
                overviewTaskRect, outRect);
        return outRect;
    }

    private void calculateModalTaskSize(Resources res) {
        float overviewModalMaxScale = res.getFloat(R.dimen.overview_modal_max_scale);
        Rect potentialTaskRect = new Rect(0, 0, widthPx, heightPx);
        potentialTaskRect.inset(mInsets.left, mInsets.top, mInsets.right, mInsets.bottom);
        int minimumHorizontalPadding = Math.round(
                (availableWidthPx - overviewTaskRect.width() * overviewModalMaxScale) / 2);
        potentialTaskRect.inset(
                minimumHorizontalPadding,
                overviewTaskMarginPx,
                minimumHorizontalPadding,
                heightPx - overviewTaskRect.bottom - mInsets.bottom);
        float scale = Math.min(
                potentialTaskRect.width() / taskDimension.x,
                potentialTaskRect.height() / taskDimension.y);
        int outWidth = Math.round(scale * taskDimension.x);
        int outHeight = Math.round(scale * taskDimension.y);
        Gravity.apply(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, outWidth, outHeight,
                potentialTaskRect, overviewModalTaskRect);
    }

    private void getTaskDimension() {
        float taskHeight = heightPx;
        if (isTablet) {
            taskHeight -= taskbarSize;
        }
        taskDimension.set(widthPx, taskHeight);
    }

    /**
     * QSB width is always calculated because when in 3 button nav the width doesn't follow the
     * width of the hotseat.
     */
    private int calculateQsbWidth() {
        if (isQsbInline) {
            int columns = getPanelCount() * inv.numColumns;
            return getIconToIconWidthForColumns(columns)
                    - iconSizePx * numShownHotseatIcons
                    - hotseatBorderSpace * numShownHotseatIcons;
        } else {
            int columns = inv.hotseatColumnSpan[mTypeIndex];
            return getIconToIconWidthForColumns(columns);
        }
    }

    private int getIconToIconWidthForColumns(int columns) {
        return columns * getCellSize().x
                + (columns - 1) * cellLayoutBorderSpacePx.x
                - (getCellSize().x - iconSizePx);  // left and right cell space
    }

    private int getHorizontalMarginPx(InvariantDeviceProfile idp, Resources res) {
        if (isVerticalBarLayout()) {
            return 0;
        }

        return isScalableGrid
                ? pxFromDp(idp.horizontalMargin[mTypeIndex], mMetrics)
                : res.getDimensionPixelSize(R.dimen.dynamic_grid_left_right_margin);
    }

    /** Updates hotseatCellHeightPx and hotseatBarSizePx */
    private void updateHotseatSizes(int hotseatIconSizePx) {
        // Ensure there is enough space for folder icons, which have a slightly larger radius.
        hotseatCellHeightPx = (int) Math.ceil(hotseatIconSizePx * ICON_OVERLAP_FACTOR);

        if (isVerticalBarLayout()) {
            hotseatBarSizePx = hotseatIconSizePx + hotseatBarSidePaddingStartPx
                    + hotseatBarSidePaddingEndPx;
        } else if (isQsbInline) {
            hotseatBarSizePx = Math.max(hotseatIconSizePx, hotseatQsbVisualHeight)
                    + hotseatBarBottomSpacePx;
        } else {
            hotseatBarSizePx = hotseatIconSizePx
                    + hotseatQsbSpace
                    + hotseatQsbVisualHeight
                    + hotseatBarBottomSpacePx;
        }
    }

    private Point getCellLayoutBorderSpace(InvariantDeviceProfile idp) {
        return getCellLayoutBorderSpace(idp, 1f);
    }

    private Point getCellLayoutBorderSpace(InvariantDeviceProfile idp, float scale) {
        if (!isScalableGrid) {
            return new Point(0, 0);
        }

        int horizontalSpacePx = pxFromDp(idp.borderSpaces[mTypeIndex].x, mMetrics, scale);
        int verticalSpacePx = pxFromDp(idp.borderSpaces[mTypeIndex].y, mMetrics, scale);

        return new Point(horizontalSpacePx, verticalSpacePx);
    }

    public Info getDisplayInfo() {
        return mInfo;
    }

    /**
     * We inset the widget padding added by the system and instead rely on the border spacing
     * between cells to create reliable consistency between widgets
     */
    public boolean shouldInsetWidgets() {
        Rect widgetPadding = inv.defaultWidgetPadding;

        // Check all sides to ensure that the widget won't overlap into another cell, or into
        // status bar.
        return workspaceTopPadding > widgetPadding.top
                && cellLayoutBorderSpacePx.x > widgetPadding.left
                && cellLayoutBorderSpacePx.y > widgetPadding.top
                && cellLayoutBorderSpacePx.x > widgetPadding.right
                && cellLayoutBorderSpacePx.y > widgetPadding.bottom;
    }

    public Builder toBuilder(Context context) {
        WindowBounds bounds = new WindowBounds(
                widthPx, heightPx, availableWidthPx, availableHeightPx, rotationHint);
        bounds.bounds.offsetTo(windowX, windowY);
        bounds.insets.set(mInsets);
        return new Builder(context, inv, mInfo)
                .setWindowBounds(bounds)
                .setUseTwoPanels(isTwoPanels)
                .setMultiWindowMode(isMultiWindowMode)
                .setGestureMode(isGestureMode);
    }

    public DeviceProfile copy(Context context) {
        return toBuilder(context).build();
    }

    /**
     * TODO: Move this to the builder as part of setMultiWindowMode
     */
    public DeviceProfile getMultiWindowProfile(Context context, WindowBounds windowBounds) {
        DeviceProfile profile = toBuilder(context)
                .setWindowBounds(windowBounds)
                .setMultiWindowMode(true)
                .build();

        profile.hideWorkspaceLabelsIfNotEnoughSpace();

        // We use these scales to measure and layout the widgets using their full invariant profile
        // sizes and then draw them scaled and centered to fit in their multi-window mode cellspans.
        float appWidgetScaleX = (float) profile.getCellSize().x / getCellSize().x;
        float appWidgetScaleY = (float) profile.getCellSize().y / getCellSize().y;
        profile.appWidgetScale.set(appWidgetScaleX, appWidgetScaleY);

        return profile;
    }

    /**
     * Checks if there is enough space for labels on the workspace.
     * If there is not, labels on the Workspace are hidden.
     * It is important to call this method after the All Apps variables have been set.
     */
    private void hideWorkspaceLabelsIfNotEnoughSpace() {
        float iconTextHeight = Utilities.calculateTextHeight(iconTextSizePx);
        float workspaceCellPaddingY = getCellSize().y - iconSizePx - iconDrawablePaddingPx
                - iconTextHeight;

        // We want enough space so that the text is closer to its corresponding icon.
        if (workspaceCellPaddingY < iconTextHeight) {
            iconTextSizePx = 0;
            iconDrawablePaddingPx = 0;
            cellHeightPx = (int) Math.ceil(iconSizePx * ICON_OVERLAP_FACTOR);
            autoResizeAllAppsCells();
        }
    }

    /**
     * Re-computes the all-apps cell size to be independent of workspace
     */
    public void autoResizeAllAppsCells() {
        int textHeight = Utilities.calculateTextHeight(allAppsIconTextSizePx);
        int topBottomPadding = textHeight;
        allAppsCellHeightPx = allAppsIconSizePx + allAppsIconDrawablePaddingPx
                + textHeight + (topBottomPadding * 2);
    }

    private void updateAllAppsContainerWidth(Resources res) {
        int cellLayoutHorizontalPadding =
                (cellLayoutPaddingPx.left + cellLayoutPaddingPx.right) / 2;
        if (isTablet) {
            allAppsLeftRightPadding =
                    res.getDimensionPixelSize(R.dimen.all_apps_bottom_sheet_horizontal_padding);

            int usedWidth = (allAppsCellWidthPx * numShownAllAppsColumns)
                    + (allAppsBorderSpacePx.x * (numShownAllAppsColumns - 1))
                    + allAppsLeftRightPadding * 2;
            allAppsLeftRightMargin = Math.max(1, (availableWidthPx - usedWidth) / 2);
        } else {
            allAppsLeftRightPadding =
                    desiredWorkspaceHorizontalMarginPx + cellLayoutHorizontalPadding;
        }
    }

    /**
     * Returns the amount of extra (or unused) vertical space.
     */
    private int updateAvailableDimensions(Resources res) {
        updateIconSize(1f, res);

        updateWorkspacePadding();

        // Check to see if the icons fit within the available height.
        float usedHeight = getCellLayoutHeightSpecification();
        final int maxHeight = getCellLayoutHeight();
        float extraHeight = Math.max(0, maxHeight - usedHeight);
        float scaleY = maxHeight / usedHeight;
        boolean shouldScale = scaleY < 1f;

        float scaleX = 1f;
        if (isScalableGrid) {
            // We scale to fit the cellWidth and cellHeight in the available space.
            // The benefit of scalable grids is that we can get consistent aspect ratios between
            // devices.
            float usedWidth =
                    getCellLayoutWidthSpecification() + (desiredWorkspaceHorizontalMarginPx * 2);
            // We do not subtract padding here, as we also scale the workspace padding if needed.
            scaleX = availableWidthPx / usedWidth;
            shouldScale = true;
        }

        if (shouldScale) {
            float scale = Math.min(scaleX, scaleY);
            updateIconSize(scale, res);
            extraHeight = Math.max(0, maxHeight - getCellLayoutHeightSpecification());
        }

        updateAvailableFolderCellDimensions(res);
        return Math.round(extraHeight);
    }

    private int getCellLayoutHeightSpecification() {
        return (cellHeightPx * inv.numRows) + (cellLayoutBorderSpacePx.y * (inv.numRows - 1))
                + cellLayoutPaddingPx.top + cellLayoutPaddingPx.bottom;
    }

    private int getCellLayoutWidthSpecification() {
        int numColumns = getPanelCount() * inv.numColumns;
        return (cellWidthPx * numColumns) + (cellLayoutBorderSpacePx.x * (numColumns - 1))
                + cellLayoutPaddingPx.left + cellLayoutPaddingPx.right;
    }

    /**
     * Updating the iconSize affects many aspects of the launcher layout, such as: iconSizePx,
     * iconTextSizePx, iconDrawablePaddingPx, cellWidth/Height, allApps* variants,
     * hotseat sizes, workspaceSpringLoadedShrinkFactor, folderIconSizePx, and folderIconOffsetYPx.
     */
    public void updateIconSize(float scale, Resources res) {
        // Icon scale should never exceed 1, otherwise pixellation may occur.
        iconScale = Math.min(1f, scale);
        cellScaleToFit = scale;

        // Workspace
        final boolean isVerticalLayout = isVerticalBarLayout();
        float invIconSizeDp = inv.iconSize[mTypeIndex];
        float invIconTextSizeSp = inv.iconTextSize[mTypeIndex];

        iconSizePx = Math.max(1, pxFromDp(invIconSizeDp, mMetrics, iconScale));
        iconTextSizePx = (int) (pxFromSp(invIconTextSizeSp, mMetrics) * iconScale);
        iconDrawablePaddingPx = (int) (iconDrawablePaddingOriginalPx * iconScale);

        cellLayoutBorderSpacePx = getCellLayoutBorderSpace(inv, scale);

        if (isScalableGrid) {
            cellWidthPx = pxFromDp(inv.minCellSize[mTypeIndex].x, mMetrics, scale);
            cellHeightPx = pxFromDp(inv.minCellSize[mTypeIndex].y, mMetrics, scale);
            int cellContentHeight = iconSizePx + iconDrawablePaddingPx
                    + Utilities.calculateTextHeight(iconTextSizePx);
            cellYPaddingPx = Math.max(0, cellHeightPx - cellContentHeight) / 2;
            desiredWorkspaceHorizontalMarginPx =
                    (int) (desiredWorkspaceHorizontalMarginOriginalPx * scale);
        } else {
            cellWidthPx = iconSizePx + iconDrawablePaddingPx;
            cellHeightPx = (int) Math.ceil(iconSizePx * ICON_OVERLAP_FACTOR)
                    + iconDrawablePaddingPx
                    + Utilities.calculateTextHeight(iconTextSizePx);
            int cellPaddingY = (getCellSize().y - cellHeightPx) / 2;
            if (iconDrawablePaddingPx > cellPaddingY && !isVerticalLayout
                    && !isMultiWindowMode) {
                // Ensures that the label is closer to its corresponding icon. This is not an issue
                // with vertical bar layout or multi-window mode since the issue is handled
                // separately with their calls to {@link #adjustToHideWorkspaceLabels}.
                cellHeightPx -= (iconDrawablePaddingPx - cellPaddingY);
                iconDrawablePaddingPx = cellPaddingY;
            }
        }

        // All apps
        updateAllAppsIconSize(scale, res);

        updateHotseatSizes(iconSizePx);

        // Folder icon
        folderIconSizePx = IconNormalizer.getNormalizedCircleSize(iconSizePx);
        folderIconOffsetYPx = (iconSizePx - folderIconSizePx) / 2;
    }

    /**
     * Hotseat width spans a certain number of columns on scalable grids.
     * This method calculates the space between the icons to achieve that width.
     */
    private int calculateHotseatBorderSpace() {
        if (!isScalableGrid) return 0;
        //TODO(http://b/228998082) remove this when 3 button spaces are fixed
        if (areNavButtonsInline) {
            return pxFromDp(inv.hotseatBorderSpaces[mTypeIndex], mMetrics);
        } else {
            int columns = inv.hotseatColumnSpan[mTypeIndex];
            float hotseatWidthPx = getIconToIconWidthForColumns(columns);
            float hotseatIconsTotalPx = iconSizePx * numShownHotseatIcons;
            return (int) (hotseatWidthPx - hotseatIconsTotalPx) / (numShownHotseatIcons - 1);
        }
    }


    /**
     * Updates the iconSize for allApps* variants.
     */
    private void updateAllAppsIconSize(float scale, Resources res) {
        allAppsBorderSpacePx = new Point(
                pxFromDp(inv.allAppsBorderSpaces[mTypeIndex].x, mMetrics, scale),
                pxFromDp(inv.allAppsBorderSpaces[mTypeIndex].y, mMetrics, scale));
        // AllApps cells don't have real space between cells,
        // so we add the border space to the cell height
        allAppsCellHeightPx = pxFromDp(inv.allAppsCellSize[mTypeIndex].y, mMetrics, scale)
                + allAppsBorderSpacePx.y;
        // but width is just the cell,
        // the border is added in #updateAllAppsContainerWidth
        if (isScalableGrid) {
            allAppsIconSizePx =
                    pxFromDp(inv.allAppsIconSize[mTypeIndex], mMetrics, scale);
            allAppsIconTextSizePx =
                    pxFromSp(inv.allAppsIconTextSize[mTypeIndex], mMetrics, scale);
            allAppsIconDrawablePaddingPx = iconDrawablePaddingOriginalPx;
            allAppsCellWidthPx = pxFromDp(inv.allAppsCellSize[mTypeIndex].x, mMetrics, scale);
        } else {
            float invIconSizeDp = inv.allAppsIconSize[mTypeIndex];
            float invIconTextSizeSp = inv.allAppsIconTextSize[mTypeIndex];
            allAppsIconSizePx = Math.max(1, pxFromDp(invIconSizeDp, mMetrics, scale));
            allAppsIconTextSizePx = (int) (pxFromSp(invIconTextSizeSp, mMetrics) * scale);
            allAppsIconDrawablePaddingPx =
                    res.getDimensionPixelSize(R.dimen.all_apps_icon_drawable_padding);
            allAppsCellWidthPx = allAppsIconSizePx + (2 * allAppsIconDrawablePaddingPx);
        }

        updateAllAppsContainerWidth(res);
        if (isVerticalBarLayout()) {
            hideWorkspaceLabelsIfNotEnoughSpace();
        }
    }

    private void updateAvailableFolderCellDimensions(Resources res) {
        updateFolderCellSize(1f, res);

        final int folderBottomPanelSize = res.getDimensionPixelSize(R.dimen.folder_label_height);

        // Don't let the folder get too close to the edges of the screen.
        int folderMargin = edgeMarginPx * 2;
        Point totalWorkspacePadding = getTotalWorkspacePadding();

        // Check if the icons fit within the available height.
        float contentUsedHeight = folderCellHeightPx * inv.numFolderRows
                + ((inv.numFolderRows - 1) * folderCellLayoutBorderSpacePx.y);
        int contentMaxHeight = availableHeightPx - totalWorkspacePadding.y - folderBottomPanelSize
                - folderMargin - folderContentPaddingTop;
        float scaleY = contentMaxHeight / contentUsedHeight;

        // Check if the icons fit within the available width.
        float contentUsedWidth = folderCellWidthPx * inv.numFolderColumns
                + ((inv.numFolderColumns - 1) * folderCellLayoutBorderSpacePx.x);
        int contentMaxWidth = availableWidthPx - totalWorkspacePadding.x - folderMargin
                - folderContentPaddingLeftRight * 2;
        float scaleX = contentMaxWidth / contentUsedWidth;

        float scale = Math.min(scaleX, scaleY);
        if (scale < 1f) {
            updateFolderCellSize(scale, res);
        }
    }

    private void updateFolderCellSize(float scale, Resources res) {
        float invIconSizeDp = isVerticalBarLayout()
                ? inv.iconSize[INDEX_LANDSCAPE]
                : inv.iconSize[INDEX_DEFAULT];
        folderChildIconSizePx = Math.max(1, pxFromDp(invIconSizeDp, mMetrics, scale));
        folderChildTextSizePx =
                pxFromSp(inv.iconTextSize[INDEX_DEFAULT], mMetrics, scale);
        folderLabelTextSizePx = (int) (folderChildTextSizePx * folderLabelTextScale);

        int textHeight = Utilities.calculateTextHeight(folderChildTextSizePx);

        if (isScalableGrid) {
            int minWidth = folderChildIconSizePx + iconDrawablePaddingPx * 2;
            int minHeight = folderChildIconSizePx + iconDrawablePaddingPx * 2 + textHeight;

            folderCellWidthPx = (int) Math.max(minWidth, cellWidthPx * scale);
            folderCellHeightPx = (int) Math.max(minHeight, cellHeightPx * scale);

            int scaledSpace = (int) (folderCellLayoutBorderSpaceOriginalPx * scale);
            folderCellLayoutBorderSpacePx = new Point(scaledSpace, scaledSpace);
            folderContentPaddingLeftRight = scaledSpace;
            folderContentPaddingTop = scaledSpace;
        } else {
            int cellPaddingX = (int) (res.getDimensionPixelSize(R.dimen.folder_cell_x_padding)
                    * scale);
            int cellPaddingY = (int) (res.getDimensionPixelSize(R.dimen.folder_cell_y_padding)
                    * scale);

            folderCellWidthPx = folderChildIconSizePx + 2 * cellPaddingX;
            folderCellHeightPx = folderChildIconSizePx + 2 * cellPaddingY + textHeight;
        }

        folderChildDrawablePaddingPx = Math.max(0,
                (folderCellHeightPx - folderChildIconSizePx - textHeight) / 3);
    }

    public void updateInsets(Rect insets) {
        mInsets.set(insets);
    }

    /**
     * The current device insets. This is generally same as the insets being dispatched to
     * {@link Insettable} elements, but can differ if the element is using a different profile.
     */
    public Rect getInsets() {
        return mInsets;
    }

    public Point getCellSize() {
        return getCellSize(null);
    }

    public Point getCellSize(Point result) {
        if (result == null) {
            result = new Point();
        }

        int shortcutAndWidgetContainerWidth =
                getCellLayoutWidth() - (cellLayoutPaddingPx.left + cellLayoutPaddingPx.right);
        result.x = calculateCellWidth(shortcutAndWidgetContainerWidth, cellLayoutBorderSpacePx.x,
                inv.numColumns);
        int shortcutAndWidgetContainerHeight =
                getCellLayoutHeight() - (cellLayoutPaddingPx.top + cellLayoutPaddingPx.bottom);
        result.y = calculateCellHeight(shortcutAndWidgetContainerHeight, cellLayoutBorderSpacePx.y,
                inv.numRows);
        return result;
    }

    /**
     * Gets the number of panels within the workspace.
     */
    public int getPanelCount() {
        return isTwoPanels ? 2 : 1;
    }

    /**
     * Gets the space in px from the bottom of last item in the vertical-bar hotseat to the
     * bottom of the screen.
     */
    private int getVerticalHotseatLastItemBottomOffset() {
        int cellHeight = calculateCellHeight(
                heightPx - mHotseatBarPadding.top - mHotseatBarPadding.bottom, hotseatBorderSpace,
                numShownHotseatIcons);
        int extraIconEndSpacing = (cellHeight - iconSizePx) / 2;
        return extraIconEndSpacing + mHotseatBarPadding.bottom;
    }

    /**
     * Gets the scaled top of the workspace in px for the spring-loaded edit state.
     */
    public float getCellLayoutSpringLoadShrunkTop() {
        mWorkspaceSpringLoadShrunkTop = mInsets.top + dropTargetBarTopMarginPx + dropTargetBarSizePx
                + dropTargetBarBottomMarginPx;
        return mWorkspaceSpringLoadShrunkTop;
    }

    /**
     * Gets the scaled bottom of the workspace in px for the spring-loaded edit state.
     */
    public float getCellLayoutSpringLoadShrunkBottom() {
        int topOfHotseat = hotseatBarSizePx + springLoadedHotseatBarTopMarginPx;
        mWorkspaceSpringLoadShrunkBottom =
                heightPx - (isVerticalBarLayout() ? getVerticalHotseatLastItemBottomOffset()
                        : topOfHotseat);
        return mWorkspaceSpringLoadShrunkBottom;
    }

    /**
     * Gets the scale of the workspace for the spring-loaded edit state.
     */
    public float getWorkspaceSpringLoadScale() {
        float scale = (getCellLayoutSpringLoadShrunkBottom() - getCellLayoutSpringLoadShrunkTop())
                / getCellLayoutHeight();
        scale = Math.min(scale, 1f);

        // Reduce scale if next pages would not be visible after scaling the workspace
        int workspaceWidth = availableWidthPx;
        float scaledWorkspaceWidth = workspaceWidth * scale;
        float maxAvailableWidth = workspaceWidth - (2 * workspaceSpringLoadedMinNextPageVisiblePx);
        if (scaledWorkspaceWidth > maxAvailableWidth) {
            scale *= maxAvailableWidth / scaledWorkspaceWidth;
        }
        return scale;
    }

    /**
     * Gets the width of a single Cell Layout, aka a single panel within a Workspace.
     *
     * <p>This is the width of a Workspace, less its horizontal padding. Note that two-panel
     * layouts have two Cell Layouts per workspace.
     */
    public int getCellLayoutWidth() {
        return (availableWidthPx - getTotalWorkspacePadding().x) / getPanelCount();
    }

    /**
     * Gets the height of a single Cell Layout, aka a single panel within a Workspace.
     *
     * <p>This is the height of a Workspace, less its vertical padding.
     */
    public int getCellLayoutHeight() {
        return availableHeightPx - getTotalWorkspacePadding().y;
    }

    public Point getTotalWorkspacePadding() {
        return new Point(workspacePadding.left + workspacePadding.right,
                workspacePadding.top + workspacePadding.bottom);
    }

    /**
     * Updates {@link #workspacePadding} as a result of any internal value change to reflect the
     * new workspace padding
     */
    private void updateWorkspacePadding() {
        Rect padding = workspacePadding;
        if (isVerticalBarLayout()) {
            padding.top = 0;
            padding.bottom = edgeMarginPx;
            if (isSeascape()) {
                padding.left = hotseatBarSizePx;
                padding.right = hotseatBarSidePaddingStartPx;
            } else {
                padding.left = hotseatBarSidePaddingStartPx;
                padding.right = hotseatBarSizePx;
            }
        } else {
            // Pad the bottom of the workspace with hotseat bar
            // and leave a bit of space in case a widget go all the way down
            int paddingBottom = hotseatBarSizePx + workspaceBottomPadding
                    + workspacePageIndicatorHeight - mWorkspacePageIndicatorOverlapWorkspace
                    - mInsets.bottom;
            int paddingTop = workspaceTopPadding + (isScalableGrid ? 0 : edgeMarginPx);
            int paddingSide = desiredWorkspaceHorizontalMarginPx;

            padding.set(paddingSide, paddingTop, paddingSide, paddingBottom);
        }
        insetPadding(workspacePadding, cellLayoutPaddingPx);
    }

    private void insetPadding(Rect paddings, Rect insets) {
        insets.left = Math.min(insets.left, paddings.left);
        paddings.left -= insets.left;

        insets.top = Math.min(insets.top, paddings.top);
        paddings.top -= insets.top;

        insets.right = Math.min(insets.right, paddings.right);
        paddings.right -= insets.right;

        insets.bottom = Math.min(insets.bottom, paddings.bottom);
        paddings.bottom -= insets.bottom;
    }

    /**
     * Returns the padding for hotseat view
     */
    public Rect getHotseatLayoutPadding(Context context) {
        if (isVerticalBarLayout()) {
            // The hotseat icons will be placed in the middle of the hotseat cells.
            // Changing the hotseatCellHeightPx is not affecting hotseat icon positions
            // in vertical bar layout.
            // Workspace icons are moved up by a small factor. The variable diffOverlapFactor
            // is set to account for that difference.
            float diffOverlapFactor = iconSizePx * (ICON_OVERLAP_FACTOR - 1) / 2;
            int paddingTop = Math.max((int) (mInsets.top + cellLayoutPaddingPx.top
                    - diffOverlapFactor), 0);
            int paddingBottom = Math.max((int) (mInsets.bottom + cellLayoutPaddingPx.bottom
                    + diffOverlapFactor), 0);

            if (isSeascape()) {
                mHotseatBarPadding.set(mInsets.left + hotseatBarSidePaddingStartPx, paddingTop,
                        hotseatBarSidePaddingEndPx, paddingBottom);
            } else {
                mHotseatBarPadding.set(hotseatBarSidePaddingEndPx, paddingTop,
                        mInsets.right + hotseatBarSidePaddingStartPx, paddingBottom);
            }
        } else if (isTaskbarPresent) {
            // Center the QSB vertically with hotseat
            int hotseatBarBottomPadding = getHotseatBarBottomPadding();
            int hotseatBarTopPadding =
                    hotseatBarSizePx - hotseatBarBottomPadding - hotseatCellHeightPx;

            // Push icons to the side
            int additionalQsbSpace = isQsbInline ? qsbWidth + hotseatBorderSpace : 0;
            int requiredWidth = iconSizePx * numShownHotseatIcons
                    + hotseatBorderSpace * (numShownHotseatIcons - 1)
                    + additionalQsbSpace;
            int hotseatWidth = Math.min(requiredWidth, availableWidthPx - hotseatBarEndOffset);
            int sideSpacing = (availableWidthPx - hotseatWidth) / 2;

            mHotseatBarPadding.set(sideSpacing, hotseatBarTopPadding, sideSpacing,
                    hotseatBarBottomPadding);

            boolean isRtl = Utilities.isRtl(context.getResources());
            if (isRtl) {
                mHotseatBarPadding.right += additionalQsbSpace;
            } else {
                mHotseatBarPadding.left += additionalQsbSpace;
            }

            if (hotseatBarEndOffset > sideSpacing) {
                int diff = isRtl
                        ? sideSpacing - hotseatBarEndOffset
                        : hotseatBarEndOffset - sideSpacing;
                mHotseatBarPadding.left -= diff;
                mHotseatBarPadding.right += diff;
            }
        } else if (isScalableGrid) {
            int sideSpacing = (availableWidthPx - qsbWidth) / 2;
            mHotseatBarPadding.set(sideSpacing,
                    0,
                    sideSpacing,
                    getHotseatBarBottomPadding());
        } else {
            // We want the edges of the hotseat to line up with the edges of the workspace, but the
            // icons in the hotseat are a different size, and so don't line up perfectly. To account
            // for this, we pad the left and right of the hotseat with half of the difference of a
            // workspace cell vs a hotseat cell.
            float workspaceCellWidth = (float) widthPx / inv.numColumns;
            float hotseatCellWidth = (float) widthPx / numShownHotseatIcons;
            int hotseatAdjustment = Math.round((workspaceCellWidth - hotseatCellWidth) / 2);
            mHotseatBarPadding.set(
                    hotseatAdjustment + workspacePadding.left + cellLayoutPaddingPx.left
                            + mInsets.left,
                    0,
                    hotseatAdjustment + workspacePadding.right + cellLayoutPaddingPx.right
                            + mInsets.right,
                    getHotseatBarBottomPadding());
        }
        return mHotseatBarPadding;
    }

    /**
     * Returns the number of pixels the QSB is translated from the bottom of the screen.
     */
    public int getQsbOffsetY() {
        if (isQsbInline) {
            return getHotseatBarBottomPadding() - ((hotseatQsbHeight - hotseatCellHeightPx) / 2);
        } else if (isTaskbarPresent) { // QSB on top
            return hotseatBarSizePx - hotseatQsbHeight + hotseatQsbShadowHeight;
        } else {
            return hotseatBarBottomSpacePx - hotseatQsbShadowHeight;
        }
    }

    /**
     * Returns the number of pixels the hotseat is translated from the bottom of the screen.
     */
    private int getHotseatBarBottomPadding() {
        if (isTaskbarPresent) { // QSB on top or inline
            return hotseatBarBottomSpacePx - (Math.abs(hotseatCellHeightPx - iconSizePx) / 2);
        } else {
            return hotseatBarSizePx - hotseatCellHeightPx;
        }
    }

    /**
     * Returns the number of pixels the taskbar is translated from the bottom of the screen.
     */
    public int getTaskbarOffsetY() {
        int taskbarIconBottomSpace = (taskbarSize - iconSizePx) / 2;
        int launcherIconBottomSpace =
                Math.min((hotseatCellHeightPx - iconSizePx) / 2, gridVisualizationPaddingY);
        return getHotseatBarBottomPadding() + launcherIconBottomSpace - taskbarIconBottomSpace;
    }

    /**
     * Returns the number of pixels required below OverviewActions excluding insets.
     */
    public int getOverviewActionsClaimedSpaceBelow() {
        if (isTaskbarPresent && !isGestureMode) {
            // Align vertically to where nav buttons are.
            return ((taskbarSize - overviewActionsHeight) / 2) + getTaskbarOffsetY();
        }

        return isTaskbarPresent ? stashedTaskbarSize : mInsets.bottom;
    }

    /** Gets the space that the overview actions will take, including bottom margin. */
    public int getOverviewActionsClaimedSpace() {
        return overviewActionsTopMarginPx + overviewActionsHeight
                + getOverviewActionsClaimedSpaceBelow();
    }

    /**
     * @return the bounds for which the open folders should be contained within
     */
    public Rect getAbsoluteOpenFolderBounds() {
        if (isVerticalBarLayout()) {
            // Folders should only appear right of the drop target bar and left of the hotseat
            return new Rect(mInsets.left + dropTargetBarSizePx + edgeMarginPx,
                    mInsets.top,
                    mInsets.left + availableWidthPx - hotseatBarSizePx - edgeMarginPx,
                    mInsets.top + availableHeightPx);
        } else {
            // Folders should only appear below the drop target bar and above the hotseat
            int hotseatTop = isTaskbarPresent ? taskbarSize : hotseatBarSizePx;
            return new Rect(mInsets.left + edgeMarginPx,
                    mInsets.top + dropTargetBarSizePx + edgeMarginPx,
                    mInsets.left + availableWidthPx - edgeMarginPx,
                    mInsets.top + availableHeightPx - hotseatTop
                            - workspacePageIndicatorHeight - edgeMarginPx);
        }
    }

    public static int calculateCellWidth(int width, int borderSpacing, int countX) {
        return (width - ((countX - 1) * borderSpacing)) / countX;
    }

    public static int calculateCellHeight(int height, int borderSpacing, int countY) {
        return (height - ((countY - 1) * borderSpacing)) / countY;
    }

    /**
     * When {@code true}, the device is in landscape mode and the hotseat is on the right column.
     * When {@code false}, either device is in portrait mode or the device is in landscape mode and
     * the hotseat is on the bottom row.
     */
    public boolean isVerticalBarLayout() {
        return isLandscape && transposeLayoutWithOrientation;
    }

    /**
     * Updates orientation information and returns true if it has changed from the previous value.
     */
    public boolean updateIsSeascape(Context context) {
        if (isVerticalBarLayout()) {
            boolean isSeascape = DisplayController.INSTANCE.get(context)
                    .getInfo().rotation == Surface.ROTATION_270;
            if (mIsSeascape != isSeascape) {
                mIsSeascape = isSeascape;
                // Hotseat changing sides requires updating workspace left/right paddings
                updateWorkspacePadding();
                return true;
            }
        }
        return false;
    }

    public boolean isSeascape() {
        return isVerticalBarLayout() && mIsSeascape;
    }

    public boolean shouldFadeAdjacentWorkspaceScreens() {
        return isVerticalBarLayout();
    }

    public int getCellContentHeight(@ContainerType int containerType) {
        switch (containerType) {
            case CellLayout.WORKSPACE:
                return cellHeightPx;
            case CellLayout.FOLDER:
                return folderCellHeightPx;
            case CellLayout.HOTSEAT:
                // The hotseat is the only container where the cell height is going to be
                // different from the content within that cell.
                return iconSizePx;
            default:
                // ??
                return 0;
        }
    }

    private String pxToDpStr(String name, float value) {
        return "\t" + name + ": " + value + "px (" + dpiFromPx(value, mMetrics.densityDpi) + "dp)";
    }

    // LINT.IfChange
    /** Dumps various DeviceProfile variables to the specified writer. */
    public void dump(Context context, String prefix, PrintWriter writer) {
        writer.println(prefix + "DeviceProfile:");
        writer.println(prefix + "\t1 dp = " + mMetrics.density + " px");

        writer.println(prefix + "\tisTablet:" + isTablet);
        writer.println(prefix + "\tisPhone:" + isPhone);
        writer.println(prefix + "\ttransposeLayoutWithOrientation:"
                + transposeLayoutWithOrientation);
        writer.println(prefix + "\tisGestureMode:" + isGestureMode);

        writer.println(prefix + "\tisLandscape:" + isLandscape);
        writer.println(prefix + "\tisMultiWindowMode:" + isMultiWindowMode);
        writer.println(prefix + "\tisTwoPanels:" + isTwoPanels);

        writer.println(prefix + pxToDpStr("windowX", windowX));
        writer.println(prefix + pxToDpStr("windowY", windowY));
        writer.println(prefix + pxToDpStr("widthPx", widthPx));
        writer.println(prefix + pxToDpStr("heightPx", heightPx));
        writer.println(prefix + pxToDpStr("availableWidthPx", availableWidthPx));
        writer.println(prefix + pxToDpStr("availableHeightPx", availableHeightPx));
        writer.println(prefix + pxToDpStr("mInsets.left", mInsets.left));
        writer.println(prefix + pxToDpStr("mInsets.top", mInsets.top));
        writer.println(prefix + pxToDpStr("mInsets.right", mInsets.right));
        writer.println(prefix + pxToDpStr("mInsets.bottom", mInsets.bottom));

        writer.println(prefix + "\taspectRatio:" + aspectRatio);

        writer.println(prefix + "\tisScalableGrid:" + isScalableGrid);

        writer.println(prefix + "\tinv.numRows: " + inv.numRows);
        writer.println(prefix + "\tinv.numColumns: " + inv.numColumns);
        writer.println(prefix + "\tinv.numSearchContainerColumns: "
                + inv.numSearchContainerColumns);

        writer.println(prefix + "\tminCellSize: " + inv.minCellSize[mTypeIndex] + "dp");

        writer.println(prefix + pxToDpStr("cellWidthPx", cellWidthPx));
        writer.println(prefix + pxToDpStr("cellHeightPx", cellHeightPx));

        writer.println(prefix + pxToDpStr("getCellSize().x", getCellSize().x));
        writer.println(prefix + pxToDpStr("getCellSize().y", getCellSize().y));

        writer.println(prefix + pxToDpStr("cellLayoutBorderSpacePx Horizontal",
                cellLayoutBorderSpacePx.x));
        writer.println(prefix + pxToDpStr("cellLayoutBorderSpacePx Vertical",
                cellLayoutBorderSpacePx.y));
        writer.println(
                prefix + pxToDpStr("cellLayoutPaddingPx.left", cellLayoutPaddingPx.left));
        writer.println(
                prefix + pxToDpStr("cellLayoutPaddingPx.top", cellLayoutPaddingPx.top));
        writer.println(
                prefix + pxToDpStr("cellLayoutPaddingPx.right", cellLayoutPaddingPx.right));
        writer.println(
                prefix + pxToDpStr("cellLayoutPaddingPx.bottom", cellLayoutPaddingPx.bottom));

        writer.println(prefix + pxToDpStr("iconSizePx", iconSizePx));
        writer.println(prefix + pxToDpStr("iconTextSizePx", iconTextSizePx));
        writer.println(prefix + pxToDpStr("iconDrawablePaddingPx", iconDrawablePaddingPx));

        writer.println(prefix + pxToDpStr("folderCellWidthPx", folderCellWidthPx));
        writer.println(prefix + pxToDpStr("folderCellHeightPx", folderCellHeightPx));
        writer.println(prefix + pxToDpStr("folderChildIconSizePx", folderChildIconSizePx));
        writer.println(prefix + pxToDpStr("folderChildTextSizePx", folderChildTextSizePx));
        writer.println(prefix + pxToDpStr("folderChildDrawablePaddingPx",
                folderChildDrawablePaddingPx));
        writer.println(prefix + pxToDpStr("folderCellLayoutBorderSpaceOriginalPx",
                folderCellLayoutBorderSpaceOriginalPx));
        writer.println(prefix + pxToDpStr("folderCellLayoutBorderSpacePx Horizontal",
                folderCellLayoutBorderSpacePx.x));
        writer.println(prefix + pxToDpStr("folderCellLayoutBorderSpacePx Vertical",
                folderCellLayoutBorderSpacePx.y));

        writer.println(prefix + pxToDpStr("bottomSheetTopPadding", bottomSheetTopPadding));

        writer.println(prefix + pxToDpStr("allAppsShiftRange", allAppsShiftRange));
        writer.println(prefix + pxToDpStr("allAppsTopPadding", allAppsTopPadding));
        writer.println(prefix + pxToDpStr("allAppsIconSizePx", allAppsIconSizePx));
        writer.println(prefix + pxToDpStr("allAppsIconTextSizePx", allAppsIconTextSizePx));
        writer.println(prefix + pxToDpStr("allAppsIconDrawablePaddingPx",
                allAppsIconDrawablePaddingPx));
        writer.println(prefix + pxToDpStr("allAppsCellHeightPx", allAppsCellHeightPx));
        writer.println(prefix + pxToDpStr("allAppsCellWidthPx", allAppsCellWidthPx));
        writer.println(prefix + pxToDpStr("allAppsBorderSpacePxX", allAppsBorderSpacePx.x));
        writer.println(prefix + pxToDpStr("allAppsBorderSpacePxY", allAppsBorderSpacePx.y));
        writer.println(prefix + "\tnumShownAllAppsColumns: " + numShownAllAppsColumns);
        writer.println(
                prefix + pxToDpStr("allAppsLeftRightPadding", allAppsLeftRightPadding));
        writer.println(prefix + pxToDpStr("allAppsLeftRightMargin", allAppsLeftRightMargin));

        writer.println(prefix + pxToDpStr("hotseatBarSizePx", hotseatBarSizePx));
        writer.println(prefix + "\tinv.hotseatColumnSpan: " + inv.hotseatColumnSpan[mTypeIndex]);
        writer.println(prefix + pxToDpStr("hotseatCellHeightPx", hotseatCellHeightPx));
        writer.println(
                prefix + pxToDpStr("hotseatBarBottomSpacePx", hotseatBarBottomSpacePx));
        writer.println(prefix + pxToDpStr("hotseatBarSidePaddingStartPx",
                hotseatBarSidePaddingStartPx));
        writer.println(prefix + pxToDpStr("hotseatBarSidePaddingEndPx",
                hotseatBarSidePaddingEndPx));
        writer.println(prefix + pxToDpStr("hotseatBarEndOffset", hotseatBarEndOffset));
        writer.println(prefix + pxToDpStr("hotseatQsbSpace", hotseatQsbSpace));
        writer.println(prefix + pxToDpStr("hotseatQsbHeight", hotseatQsbHeight));
        writer.println(prefix + pxToDpStr("springLoadedHotseatBarTopMarginPx",
                springLoadedHotseatBarTopMarginPx));
        writer.println(prefix + pxToDpStr("mHotseatBarPadding.top", mHotseatBarPadding.top));
        writer.println(
                prefix + pxToDpStr("mHotseatBarPadding.bottom", mHotseatBarPadding.bottom));
        writer.println(
                prefix + pxToDpStr("mHotseatBarPadding.left", mHotseatBarPadding.left));
        writer.println(
                prefix + pxToDpStr("mHotseatBarPadding.right", mHotseatBarPadding.right));
        writer.println(prefix + "\tnumShownHotseatIcons: " + numShownHotseatIcons);
        writer.println(prefix + pxToDpStr("hotseatBorderSpace", hotseatBorderSpace));
        writer.println(prefix + "\tisQsbInline: " + isQsbInline);
        writer.println(prefix + pxToDpStr("qsbWidth", qsbWidth));

        writer.println(prefix + "\tisTaskbarPresent:" + isTaskbarPresent);
        writer.println(prefix + "\tisTaskbarPresentInApps:" + isTaskbarPresentInApps);
        writer.println(prefix + pxToDpStr("taskbarSize", taskbarSize));

        writer.println(prefix + pxToDpStr("desiredWorkspaceHorizontalMarginPx",
                desiredWorkspaceHorizontalMarginPx));
        writer.println(prefix + pxToDpStr("workspacePadding.left", workspacePadding.left));
        writer.println(prefix + pxToDpStr("workspacePadding.top", workspacePadding.top));
        writer.println(prefix + pxToDpStr("workspacePadding.right", workspacePadding.right));
        writer.println(
                prefix + pxToDpStr("workspacePadding.bottom", workspacePadding.bottom));

        writer.println(prefix + pxToDpStr("iconScale", iconScale));
        writer.println(prefix + pxToDpStr("cellScaleToFit ", cellScaleToFit));
        writer.println(prefix + pxToDpStr("extraSpace", extraSpace));
        writer.println(
                prefix + pxToDpStr("unscaled extraSpace", extraSpace / iconScale));

        if (inv.devicePaddings != null) {
            int unscaledExtraSpace = (int) (extraSpace / iconScale);
            writer.println(prefix + pxToDpStr("maxEmptySpace",
                    inv.devicePaddings.getDevicePadding(unscaledExtraSpace).getMaxEmptySpacePx()));
        }
        writer.println(prefix + pxToDpStr("workspaceTopPadding", workspaceTopPadding));
        writer.println(prefix + pxToDpStr("workspaceBottomPadding", workspaceBottomPadding));

        writer.println(prefix + pxToDpStr("overviewTaskMarginPx", overviewTaskMarginPx));
        writer.println(
                prefix + pxToDpStr("overviewTaskMarginGridPx", overviewTaskMarginGridPx));
        writer.println(prefix + pxToDpStr("overviewTaskIconSizePx", overviewTaskIconSizePx));
        writer.println(prefix + pxToDpStr("overviewTaskIconDrawableSizePx",
                overviewTaskIconDrawableSizePx));
        writer.println(prefix + pxToDpStr("overviewTaskIconDrawableSizeGridPx",
                overviewTaskIconDrawableSizeGridPx));
        writer.println(prefix + pxToDpStr("overviewTaskThumbnailTopMarginPx",
                overviewTaskThumbnailTopMarginPx));
        writer.println(prefix + pxToDpStr("overviewActionsTopMarginPx",
                overviewActionsTopMarginPx));
        writer.println(prefix + pxToDpStr("overviewActionsHeight",
                overviewActionsHeight));
        writer.println(prefix + pxToDpStr("overviewActionsButtonSpacing",
                overviewActionsButtonSpacing));
        writer.println(prefix + pxToDpStr("overviewPageSpacing", overviewPageSpacing));
        writer.println(prefix + pxToDpStr("overviewRowSpacing", overviewRowSpacing));
        writer.println(prefix + pxToDpStr("overviewGridSideMargin", overviewGridSideMargin));

        writer.println(
                prefix + pxToDpStr("dropTargetBarTopMarginPx", dropTargetBarTopMarginPx));
        writer.println(prefix + pxToDpStr("dropTargetBarSizePx", dropTargetBarSizePx));
        writer.println(prefix
                + pxToDpStr("dropTargetBarBottomMarginPx", dropTargetBarBottomMarginPx));

        writer.println(prefix
                + pxToDpStr("workspaceSpringLoadShrunkTop", mWorkspaceSpringLoadShrunkTop));
        writer.println(prefix + pxToDpStr("workspaceSpringLoadShrunkBottom",
                mWorkspaceSpringLoadShrunkBottom));
        writer.println(prefix + pxToDpStr("workspaceSpringLoadedMinNextPageVisiblePx",
                workspaceSpringLoadedMinNextPageVisiblePx));
        writer.println(prefix
                + pxToDpStr("getWorkspaceSpringLoadScale()", getWorkspaceSpringLoadScale()));
        writer.println(prefix + pxToDpStr("getCellLayoutHeight()", getCellLayoutHeight()));
        writer.println(prefix + pxToDpStr("getCellLayoutWidth()", getCellLayoutWidth()));

        writer.println(prefix + pxToDpStr("overviewGridRect.left", overviewGridRect.left));
        writer.println(prefix + pxToDpStr("overviewGridRect.top", overviewGridRect.top));
        writer.println(prefix + pxToDpStr("overviewGridRect.right", overviewGridRect.right));
        writer.println(prefix
                + pxToDpStr("overviewGridRect.bottom", overviewGridRect.bottom));
        writer.println(prefix + pxToDpStr("taskDimension.x", taskDimension.x));
        writer.println(prefix + pxToDpStr("taskDimension.y", taskDimension.y));
        writer.println(prefix + pxToDpStr("overviewTaskRect.left", overviewTaskRect.left));
        writer.println(prefix + pxToDpStr("overviewTaskRect.top", overviewTaskRect.top));
        writer.println(prefix + pxToDpStr("overviewTaskRect.right", overviewTaskRect.right));
        writer.println(prefix
                + pxToDpStr("overviewTaskRect.bottom", overviewTaskRect.bottom));
        writer.println(prefix
                + pxToDpStr("overviewGridTaskDimension.x", overviewGridTaskDimension.x));
        writer.println(prefix
                + pxToDpStr("overviewGridTaskDimension.y", overviewGridTaskDimension.y));
        writer.println(prefix
                + pxToDpStr("overviewModalTaskRect.left", overviewModalTaskRect.left));
        writer.println(prefix
                + pxToDpStr("overviewModalTaskRect.top", overviewModalTaskRect.top));
        writer.println(prefix
                + pxToDpStr("overviewModalTaskRect.right", overviewModalTaskRect.right));
        writer.println(prefix
                + pxToDpStr("overviewModalTaskRect.bottom", overviewModalTaskRect.bottom));
        boolean isRecentsRtl =
                PortraitPagedViewHandler.PORTRAIT.getRecentsRtlSetting(context.getResources());
        writer.println(prefix
                + pxToDpStr("getOverviewGridTaskRect(" + isRecentsRtl + ").left",
                getOverviewGridTaskRect(isRecentsRtl).left));
        writer.println(prefix
                + pxToDpStr("getOverviewGridTaskRect(" + isRecentsRtl + ").top",
                getOverviewGridTaskRect(isRecentsRtl).top));
        writer.println(prefix
                + pxToDpStr("getOverviewGridTaskRect(" + isRecentsRtl + ").right",
                getOverviewGridTaskRect(isRecentsRtl).right));
        writer.println(prefix
                + pxToDpStr("getOverviewGridTaskRect(" + isRecentsRtl + ").bottom",
                getOverviewGridTaskRect(isRecentsRtl).bottom));
        writer.println(
                prefix + pxToDpStr("overviewTaskWorkspaceScale", overviewTaskWorkspaceScale));
        writer.println(prefix + pxToDpStr("overviewModalTaskScale", overviewModalTaskScale));
    }
    // LINT.ThenChange(
    //     packages/apps/Launcher3/quickstep/tests/src/com/android/quickstep/DeviceProfilePhoneTest.kt,
    //     packages/apps/Launcher3/quickstep/tests/src/com/android/quickstep/DeviceProfileVerticalBarTest.kt,
    //     packages/apps/Launcher3/quickstep/tests/src/com/android/quickstep/DeviceProfilePhone3ButtonTest.kt,
    //     packages/apps/Launcher3/quickstep/tests/src/com/android/quickstep/DeviceProfileVerticalBar3ButtonTest.kt,
    //     packages/apps/Launcher3/quickstep/tests/src/com/android/quickstep/DeviceProfileTabletLandscapeTest.kt,
    //     packages/apps/Launcher3/quickstep/tests/src/com/android/quickstep/DeviceProfileTabletPortraitTest.kt,
    //     packages/apps/Launcher3/quickstep/tests/src/com/android/quickstep/DeviceProfileTabletLandscape3ButtonTest.kt,
    //     packages/apps/Launcher3/quickstep/tests/src/com/android/quickstep/DeviceProfileTabletPortrait3ButtonTest.kt,
    //     packages/apps/Launcher3/quickstep/tests/src/com/android/quickstep/DeviceProfileTwoPanelLandscapeTest.kt,
    //     packages/apps/Launcher3/quickstep/tests/src/com/android/quickstep/DeviceProfileTwoPanelPortraitTest.kt,
    //     packages/apps/Launcher3/quickstep/tests/src/com/android/quickstep/DeviceProfileTwoPanelLandscape3ButtonTest.kt,
    //     packages/apps/Launcher3/quickstep/tests/src/com/android/quickstep/DeviceProfileTwoPanelPortrait3ButtonTest.kt)

    private static Context getContext(Context c, Info info, int orientation, WindowBounds bounds) {
        Configuration config = new Configuration(c.getResources().getConfiguration());
        config.orientation = orientation;
        config.densityDpi = info.getDensityDpi();
        config.smallestScreenWidthDp = (int) info.smallestSizeDp(bounds);
        return c.createConfigurationContext(config);
    }

    /**
     * Callback when a component changes the DeviceProfile associated with it, as a result of
     * configuration change
     */
    public interface OnDeviceProfileChangeListener {

        /**
         * Called when the device profile is reassigned. Note that for layout and measurements, it
         * is sufficient to listen for inset changes. Use this callback when you need to perform
         * a one time operation.
         */
        void onDeviceProfileChanged(DeviceProfile dp);
    }

    /** Allows registering listeners for {@link DeviceProfile} changes. */
    public interface DeviceProfileListenable {

        /** The current device profile. */
        DeviceProfile getDeviceProfile();

        /** Registered {@link OnDeviceProfileChangeListener} instances. */
        List<OnDeviceProfileChangeListener> getOnDeviceProfileChangeListeners();

        /** Notifies listeners of a {@link DeviceProfile} change. */
        default void dispatchDeviceProfileChanged() {
            DeviceProfile deviceProfile = getDeviceProfile();
            List<OnDeviceProfileChangeListener> listeners = getOnDeviceProfileChangeListeners();
            for (int i = listeners.size() - 1; i >= 0; i--) {
                listeners.get(i).onDeviceProfileChanged(deviceProfile);
            }
        }

        /** Register listener for {@link DeviceProfile} changes. */
        default void addOnDeviceProfileChangeListener(OnDeviceProfileChangeListener listener) {
            getOnDeviceProfileChangeListeners().add(listener);
        }

        /** Unregister listener for {@link DeviceProfile} changes. */
        default void removeOnDeviceProfileChangeListener(OnDeviceProfileChangeListener listener) {
            getOnDeviceProfileChangeListeners().remove(listener);
        }
    }

    public static class Builder {
        private Context mContext;
        private InvariantDeviceProfile mInv;
        private Info mInfo;

        private WindowBounds mWindowBounds;
        private boolean mUseTwoPanels;

        private boolean mIsMultiWindowMode = false;
        private Boolean mTransposeLayoutWithOrientation;
        private Boolean mIsGestureMode;

        public Builder(Context context, InvariantDeviceProfile inv, Info info) {
            mContext = context;
            mInv = inv;
            mInfo = info;
        }

        public Builder setMultiWindowMode(boolean isMultiWindowMode) {
            mIsMultiWindowMode = isMultiWindowMode;
            return this;
        }

        public Builder setUseTwoPanels(boolean useTwoPanels) {
            mUseTwoPanels = useTwoPanels;
            return this;
        }


        public Builder setWindowBounds(WindowBounds bounds) {
            mWindowBounds = bounds;
            return this;
        }

        public Builder setTransposeLayoutWithOrientation(boolean transposeLayoutWithOrientation) {
            mTransposeLayoutWithOrientation = transposeLayoutWithOrientation;
            return this;
        }

        public Builder setGestureMode(boolean isGestureMode) {
            mIsGestureMode = isGestureMode;
            return this;
        }

        public DeviceProfile build() {
            if (mWindowBounds == null) {
                throw new IllegalArgumentException("Window bounds not set");
            }
            if (mTransposeLayoutWithOrientation == null) {
                mTransposeLayoutWithOrientation = !mInfo.isTablet(mWindowBounds);
            }
            if (mIsGestureMode == null) {
                mIsGestureMode = DisplayController.getNavigationMode(mContext).hasGestures;
            }
            return new DeviceProfile(mContext, mInv, mInfo, mWindowBounds, mIsMultiWindowMode,
                    mTransposeLayoutWithOrientation, mUseTwoPanels, mIsGestureMode);
        }
    }

}
