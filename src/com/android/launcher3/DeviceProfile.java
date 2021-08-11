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

import static android.util.DisplayMetrics.DENSITY_DEVICE_STABLE;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;

import static com.android.launcher3.ResourceUtils.pxFromDp;
import static com.android.launcher3.Utilities.dpiFromPx;
import static com.android.launcher3.Utilities.pxFromSp;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.ICON_OVERLAP_FACTOR;
import static com.android.launcher3.util.WindowManagerCompat.MIN_TABLET_WIDTH;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.WindowInsets;
import android.view.WindowManager;

import com.android.launcher3.CellLayout.ContainerType;
import com.android.launcher3.DevicePaddings.DevicePadding;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.DotRenderer;
import com.android.launcher3.icons.GraphicsUtils;
import com.android.launcher3.icons.IconNormalizer;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.DisplayController.Info;
import com.android.launcher3.util.WindowBounds;

import java.io.PrintWriter;

@SuppressLint("NewApi")
public class DeviceProfile {

    private static final int DEFAULT_DOT_SIZE = 100;

    public final InvariantDeviceProfile inv;
    private final Info mInfo;
    private final DisplayMetrics mMetrics;

    // Device properties
    public final boolean isTablet;
    public final boolean isPhone;
    public final boolean transposeLayoutWithOrientation;
    public final boolean isTwoPanels;
    public final boolean allowRotation;

    // Device properties in current orientation
    public final boolean isLandscape;
    public final boolean isMultiWindowMode;

    public final int windowX;
    public final int windowY;
    public final int widthPx;
    public final int heightPx;
    public final int availableWidthPx;
    public final int availableHeightPx;

    public final float aspectRatio;

    public final boolean isScalableGrid;

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

    // To evenly space the icons, increase the left/right margins for tablets in portrait mode.
    private static final int PORTRAIT_TABLET_LEFT_RIGHT_PADDING_MULTIPLIER = 4;

    // Workspace
    public final int desiredWorkspaceLeftRightOriginalPx;
    public int desiredWorkspaceLeftRightMarginPx;
    public final int cellLayoutBorderSpacingOriginalPx;
    public int cellLayoutBorderSpacingPx;
    public final int cellLayoutPaddingLeftRightPx;
    public final int cellLayoutBottomPaddingPx;
    public final int edgeMarginPx;
    public float workspaceSpringLoadShrinkFactor;
    public final int workspaceSpringLoadedBottomSpace;

    private final int extraSpace;
    public int workspaceTopPadding;
    public int workspaceBottomPadding;
    public int extraHotseatBottomPadding;

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
    public int folderCellLayoutBorderSpacingPx;
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
    public int hotseatBarSizeExtraSpacePx;
    public final int numShownHotseatIcons;
    public int hotseatCellHeightPx;
    private final int hotseatExtraVerticalSize;
    // In portrait: size = height, in landscape: size = width
    public int hotseatBarSizePx;
    public int hotseatBarTopPaddingPx;
    public final int hotseatBarBottomPaddingPx;
    // Start is the side next to the nav bar, end is the side next to the workspace
    public final int hotseatBarSidePaddingStartPx;
    public final int hotseatBarSidePaddingEndPx;

    public final float qsbBottomMarginOriginalPx;
    public int qsbBottomMarginPx;

    // All apps
    public int allAppsOpenVerticalTranslate;
    public int allAppsCellHeightPx;
    public int allAppsCellWidthPx;
    public int allAppsIconSizePx;
    public int allAppsIconDrawablePaddingPx;
    public final int numShownAllAppsColumns;
    public float allAppsIconTextSizePx;

    // Overview
    public int overviewTaskMarginPx;
    public int overviewTaskIconSizePx;
    public int overviewTaskThumbnailTopMarginPx;
    public final int overviewActionsMarginThreeButtonPx;
    public final int overviewActionsMarginGesturePx;

    // Widgets
    public final PointF appWidgetScale = new PointF(1.0f, 1.0f);

    // Drop Target
    public int dropTargetBarSizePx;
    public int dropTargetDragPaddingPx;
    public int dropTargetTextSizePx;

    // Insets
    private final Rect mInsets = new Rect();
    public final Rect workspacePadding = new Rect();
    private final Rect mHotseatPadding = new Rect();
    // When true, nav bar is on the left side of the screen.
    private boolean mIsSeascape;

    // Notification dots
    public DotRenderer mDotRendererWorkSpace;
    public DotRenderer mDotRendererAllApps;

     // Taskbar
    public boolean isTaskbarPresent;
    public int taskbarSize;
    // How much of the bottom inset is due to Taskbar rather than other system elements.
    public int nonOverlappingTaskbarInset;

    // DragController
    public int flingToDeleteThresholdVelocity;

    DeviceProfile(Context context, InvariantDeviceProfile inv, Info info, WindowBounds windowBounds,
            boolean isMultiWindowMode, boolean transposeLayoutWithOrientation,
            boolean useTwoPanels) {

        this.inv = inv;
        this.isLandscape = windowBounds.isLandscape();
        this.isMultiWindowMode = isMultiWindowMode;
        this.transposeLayoutWithOrientation = transposeLayoutWithOrientation;
        windowX = windowBounds.bounds.left;
        windowY = windowBounds.bounds.top;

        isScalableGrid = inv.isScalable && !isVerticalBarLayout() && !isMultiWindowMode;

        // Determine sizes.
        widthPx = windowBounds.bounds.width();
        heightPx = windowBounds.bounds.height();
        availableWidthPx = windowBounds.availableSize.x;
        int nonFinalAvailableHeightPx = windowBounds.availableSize.y;

        mInfo = info;
        // If the device's pixel density was scaled (usually via settings for A11y), use the
        // original dimensions to determine if rotation is allowed of not.
        float originalSmallestWidth = dpiFromPx(Math.min(widthPx, heightPx), DENSITY_DEVICE_STABLE);
        allowRotation = originalSmallestWidth >= MIN_TABLET_WIDTH;
        // Tablet UI does not support emulated landscape.
        isTablet = allowRotation && info.isTablet(windowBounds);
        isPhone = !isTablet;
        isTwoPanels = isTablet && useTwoPanels;

        aspectRatio = ((float) Math.max(widthPx, heightPx)) / Math.min(widthPx, heightPx);
        boolean isTallDevice = Float.compare(aspectRatio, TALL_DEVICE_ASPECT_RATIO_THRESHOLD) >= 0;

        // Some more constants
        context = getContext(context, info, isVerticalBarLayout()
                ? Configuration.ORIENTATION_LANDSCAPE
                : Configuration.ORIENTATION_PORTRAIT);
        mMetrics = context.getResources().getDisplayMetrics();
        final Resources res = context.getResources();

        isTaskbarPresent = isTablet && FeatureFlags.ENABLE_TASKBAR.get();
        if (isTaskbarPresent) {
            // Taskbar will be added later, but provides bottom insets that we should subtract
            // from availableHeightPx.
            taskbarSize = res.getDimensionPixelSize(R.dimen.taskbar_size);
            WindowInsets windowInsets =
                    context.createWindowContext(
                            context.getSystemService(DisplayManager.class).getDisplay(mInfo.id),
                            TYPE_APPLICATION, null)
                    .getSystemService(WindowManager.class)
                    .getCurrentWindowMetrics().getWindowInsets();
            nonOverlappingTaskbarInset = taskbarSize - windowInsets.getSystemWindowInsetBottom();
            if (nonOverlappingTaskbarInset > 0) {
                nonFinalAvailableHeightPx -= nonOverlappingTaskbarInset;
            }
        }
        availableHeightPx = nonFinalAvailableHeightPx;

        edgeMarginPx = res.getDimensionPixelSize(R.dimen.dynamic_grid_edge_margin);

        desiredWorkspaceLeftRightMarginPx = isVerticalBarLayout() ? 0 : isScalableGrid
                ? res.getDimensionPixelSize(R.dimen.scalable_grid_left_right_margin)
                : res.getDimensionPixelSize(R.dimen.dynamic_grid_left_right_margin);
        desiredWorkspaceLeftRightOriginalPx = desiredWorkspaceLeftRightMarginPx;


        allAppsOpenVerticalTranslate = res.getDimensionPixelSize(
                R.dimen.all_apps_open_vertical_translate);

        folderLabelTextScale = res.getFloat(R.dimen.folder_label_text_scale);
        folderContentPaddingLeftRight =
                res.getDimensionPixelSize(R.dimen.folder_content_padding_left_right);
        folderContentPaddingTop = res.getDimensionPixelSize(R.dimen.folder_content_padding_top);

        setCellLayoutBorderSpacing(pxFromDp(inv.borderSpacing, mMetrics, 1f));
        cellLayoutBorderSpacingOriginalPx = cellLayoutBorderSpacingPx;
        folderCellLayoutBorderSpacingPx = cellLayoutBorderSpacingPx;

        int cellLayoutPaddingLeftRightMultiplier = !isVerticalBarLayout() && isTablet
                ? PORTRAIT_TABLET_LEFT_RIGHT_PADDING_MULTIPLIER : 1;
        int cellLayoutPadding = isScalableGrid
                ? 0
                : res.getDimensionPixelSize(R.dimen.dynamic_grid_cell_layout_padding);

        if (isTwoPanels) {
            cellLayoutPaddingLeftRightPx =
                    res.getDimensionPixelSize(R.dimen.two_panel_home_side_padding);
            cellLayoutBottomPaddingPx = 0;
        } else if (isLandscape) {
            cellLayoutPaddingLeftRightPx = 0;
            cellLayoutBottomPaddingPx = cellLayoutPadding;
        } else {
            cellLayoutPaddingLeftRightPx = cellLayoutPaddingLeftRightMultiplier * cellLayoutPadding;
            cellLayoutBottomPaddingPx = 0;
        }

        workspacePageIndicatorHeight = res.getDimensionPixelSize(
                R.dimen.workspace_page_indicator_height);
        mWorkspacePageIndicatorOverlapWorkspace =
                res.getDimensionPixelSize(R.dimen.workspace_page_indicator_overlap_workspace);

        iconDrawablePaddingOriginalPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_icon_drawable_padding);

        dropTargetBarSizePx = res.getDimensionPixelSize(R.dimen.dynamic_grid_drop_target_size);
        dropTargetDragPaddingPx = res.getDimensionPixelSize(R.dimen.drop_target_drag_padding);
        dropTargetTextSizePx = res.getDimensionPixelSize(R.dimen.drop_target_text_size);

        workspaceSpringLoadedBottomSpace =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_min_spring_loaded_space);

        workspaceCellPaddingXPx = res.getDimensionPixelSize(R.dimen.dynamic_grid_cell_padding_x);

        numShownHotseatIcons =
                isTwoPanels ? inv.numDatabaseHotseatIcons : inv.numShownHotseatIcons;
        numShownAllAppsColumns =
                isTwoPanels ? inv.numDatabaseAllAppsColumns : inv.numAllAppsColumns;
        hotseatBarSizeExtraSpacePx = 0;
        hotseatBarTopPaddingPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_hotseat_top_padding);
        hotseatBarBottomPaddingPx = (isTallDevice ? 0
                : res.getDimensionPixelSize(R.dimen.dynamic_grid_hotseat_bottom_non_tall_padding))
                + res.getDimensionPixelSize(R.dimen.dynamic_grid_hotseat_bottom_padding);
        hotseatBarSidePaddingEndPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_hotseat_side_padding);
        // Add a bit of space between nav bar and hotseat in vertical bar layout.
        hotseatBarSidePaddingStartPx = isVerticalBarLayout() ? workspacePageIndicatorHeight : 0;
        hotseatExtraVerticalSize =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_hotseat_extra_vertical_size);
        updateHotseatIconSize(pxFromDp(inv.iconSize, mMetrics, 1f));

        qsbBottomMarginOriginalPx = isScalableGrid
                ? res.getDimensionPixelSize(R.dimen.scalable_grid_qsb_bottom_margin)
                : 0;

        overviewTaskMarginPx = res.getDimensionPixelSize(R.dimen.overview_task_margin);
        overviewTaskIconSizePx =
                isTablet && FeatureFlags.ENABLE_OVERVIEW_GRID.get() ? res.getDimensionPixelSize(
                        R.dimen.task_thumbnail_icon_size_grid) : res.getDimensionPixelSize(
                        R.dimen.task_thumbnail_icon_size);
        overviewTaskThumbnailTopMarginPx = overviewTaskIconSizePx + overviewTaskMarginPx * 2;
        overviewActionsMarginGesturePx = res.getDimensionPixelSize(
                R.dimen.overview_actions_bottom_margin_gesture);
        overviewActionsMarginThreeButtonPx = res.getDimensionPixelSize(
                R.dimen.overview_actions_bottom_margin_three_button);

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
            extraHotseatBottomPadding = Math.round(paddingHotseatBottom * cellScaleToFit);

            hotseatBarSizePx += extraHotseatBottomPadding;

            qsbBottomMarginPx = Math.round(qsbBottomMarginOriginalPx * cellScaleToFit);
        } else if (!isVerticalBarLayout() && isPhone && isTallDevice) {
            // We increase the hotseat size when there is extra space.

            if (Float.compare(aspectRatio, TALLER_DEVICE_ASPECT_RATIO_THRESHOLD) >= 0
                    && extraSpace >= Utilities.dpToPx(TALL_DEVICE_EXTRA_SPACE_THRESHOLD_DP)) {
                // For taller devices, we will take a piece of the extra space from each row,
                // and add it to the space above and below the hotseat.

                // For devices with more extra space, we take a larger piece from each cell.
                int piece = extraSpace < Utilities.dpToPx(TALL_DEVICE_MORE_EXTRA_SPACE_THRESHOLD_DP)
                        ? 7 : 5;

                int extraSpace = ((getCellSize().y - iconSizePx - iconDrawablePaddingPx * 2)
                        * inv.numRows) / piece;

                workspaceTopPadding = extraSpace / 8;
                int halfLeftOver = (extraSpace - workspaceTopPadding) / 2;
                hotseatBarTopPaddingPx += halfLeftOver;
                hotseatBarSizeExtraSpacePx = halfLeftOver;
            } else {
                // ie. For a display with a large aspect ratio, we can keep the icons on the
                // workspace in portrait mode closer together by adding more height to the hotseat.
                // Note: This calculation was created after noticing a pattern in the design spec.
                hotseatBarSizeExtraSpacePx = getCellSize().y - iconSizePx
                        - iconDrawablePaddingPx * 2 - workspacePageIndicatorHeight;
            }

            updateHotseatIconSize(iconSizePx);

            // Recalculate the available dimensions using the new hotseat size.
            updateAvailableDimensions(res);
        }
        updateWorkspacePadding();

        flingToDeleteThresholdVelocity = res.getDimensionPixelSize(
                R.dimen.drag_flingToDeleteMinVelocity);

        // This is done last, after iconSizePx is calculated above.
        Path dotPath = GraphicsUtils.getShapePath(DEFAULT_DOT_SIZE);
        mDotRendererWorkSpace = new DotRenderer(iconSizePx, dotPath, DEFAULT_DOT_SIZE);
        mDotRendererAllApps = iconSizePx == allAppsIconSizePx ? mDotRendererWorkSpace :
                new DotRenderer(allAppsIconSizePx, dotPath, DEFAULT_DOT_SIZE);
    }

    private void updateHotseatIconSize(int hotseatIconSizePx) {
        // Ensure there is enough space for folder icons, which have a slightly larger radius.
        hotseatCellHeightPx = (int) Math.ceil(hotseatIconSizePx * ICON_OVERLAP_FACTOR);
        if (isVerticalBarLayout()) {
            hotseatBarSizePx = hotseatIconSizePx + hotseatBarSidePaddingStartPx
                    + hotseatBarSidePaddingEndPx;
        } else {
            hotseatBarSizePx = hotseatIconSizePx + hotseatBarTopPaddingPx
                    + hotseatBarBottomPaddingPx + (isScalableGrid ? 0 : hotseatExtraVerticalSize)
                    + hotseatBarSizeExtraSpacePx;
        }
    }

    private void setCellLayoutBorderSpacing(int borderSpacing) {
        cellLayoutBorderSpacingPx = isScalableGrid ? borderSpacing : 0;
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
                && cellLayoutBorderSpacingPx > widgetPadding.left
                && cellLayoutBorderSpacingPx > widgetPadding.top
                && cellLayoutBorderSpacingPx > widgetPadding.right
                && cellLayoutBorderSpacingPx > widgetPadding.bottom;
    }

    public Builder toBuilder(Context context) {
        WindowBounds bounds =
                new WindowBounds(widthPx, heightPx, availableWidthPx, availableHeightPx);
        bounds.bounds.offsetTo(windowX, windowY);
        return new Builder(context, inv, mInfo)
                .setWindowBounds(bounds)
                .setUseTwoPanels(isTwoPanels)
                .setMultiWindowMode(isMultiWindowMode);
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
        profile.updateWorkspacePadding();

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

    /**
     * Returns the amount of extra (or unused) vertical space.
     */
    private int updateAvailableDimensions(Resources res) {
        updateIconSize(1f, res);

        Point workspacePadding = getTotalWorkspacePadding();

        // Check to see if the icons fit within the available height.
        float usedHeight = getCellLayoutHeight();
        final int maxHeight = availableHeightPx - workspacePadding.y;
        float extraHeight = Math.max(0, maxHeight - usedHeight);
        float scaleY = maxHeight / usedHeight;
        boolean shouldScale = scaleY < 1f;

        float scaleX = 1f;
        if (isScalableGrid) {
            // We scale to fit the cellWidth and cellHeight in the available space.
            // The benefit of scalable grids is that we can get consistent aspect ratios between
            // devices.
            float usedWidth = (cellWidthPx * inv.numColumns)
                    + (cellLayoutBorderSpacingPx * (inv.numColumns - 1))
                    + (desiredWorkspaceLeftRightMarginPx * 2);
            // We do not subtract padding here, as we also scale the workspace padding if needed.
            scaleX = availableWidthPx / usedWidth;
            shouldScale = true;
        }

        if (shouldScale) {
            float scale = Math.min(scaleX, scaleY);
            updateIconSize(scale, res);
            extraHeight = Math.max(0, maxHeight - getCellLayoutHeight());
        }

        updateAvailableFolderCellDimensions(res);
        return Math.round(extraHeight);
    }

    private int getCellLayoutHeight() {
        return (cellHeightPx * inv.numRows) + (cellLayoutBorderSpacingPx * (inv.numRows - 1));
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
        float invIconSizeDp = isLandscape ? inv.landscapeIconSize : inv.iconSize;
        iconSizePx = Math.max(1, pxFromDp(invIconSizeDp, mMetrics, iconScale));
        float invIconTextSizeSp = isLandscape ? inv.landscapeIconTextSize : inv.iconTextSize;
        iconTextSizePx = (int) (pxFromSp(invIconTextSizeSp, mMetrics) * iconScale);
        iconDrawablePaddingPx = (int) (iconDrawablePaddingOriginalPx * iconScale);

        setCellLayoutBorderSpacing((int) (cellLayoutBorderSpacingOriginalPx * scale));

        if (isScalableGrid) {
            cellWidthPx = pxFromDp(inv.minCellWidth, mMetrics, scale);
            cellHeightPx = pxFromDp(inv.minCellHeight, mMetrics, scale);
            int cellContentHeight = iconSizePx + iconDrawablePaddingPx
                    + Utilities.calculateTextHeight(iconTextSizePx);
            cellYPaddingPx = Math.max(0, cellHeightPx - cellContentHeight) / 2;
            desiredWorkspaceLeftRightMarginPx = (int) (desiredWorkspaceLeftRightOriginalPx * scale);
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
        if (numShownAllAppsColumns != inv.numColumns) {
            allAppsIconSizePx = pxFromDp(inv.allAppsIconSize, mMetrics);
            allAppsIconTextSizePx = pxFromSp(inv.allAppsIconTextSize, mMetrics);
            allAppsIconDrawablePaddingPx = iconDrawablePaddingOriginalPx;
            autoResizeAllAppsCells();
        } else {
            allAppsIconSizePx = iconSizePx;
            allAppsIconTextSizePx = iconTextSizePx;
            allAppsIconDrawablePaddingPx = iconDrawablePaddingPx;
            allAppsCellHeightPx = getCellSize().y;
        }
        allAppsCellWidthPx = allAppsIconSizePx + allAppsIconDrawablePaddingPx;

        if (isVerticalLayout) {
            hideWorkspaceLabelsIfNotEnoughSpace();
        }

        // Hotseat
        updateHotseatIconSize(iconSizePx);

        if (!isVerticalLayout) {
            int expectedWorkspaceHeight = availableHeightPx - hotseatBarSizePx
                    - workspacePageIndicatorHeight - edgeMarginPx;
            float minRequiredHeight = dropTargetBarSizePx + workspaceSpringLoadedBottomSpace;
            workspaceSpringLoadShrinkFactor = Math.min(
                    res.getInteger(R.integer.config_workspaceSpringLoadShrinkPercentage) / 100.0f,
                    1 - (minRequiredHeight / expectedWorkspaceHeight));
        } else {
            workspaceSpringLoadShrinkFactor =
                    res.getInteger(R.integer.config_workspaceSpringLoadShrinkPercentage) / 100.0f;
        }

        // Folder icon
        folderIconSizePx = IconNormalizer.getNormalizedCircleSize(iconSizePx);
        folderIconOffsetYPx = (iconSizePx - folderIconSizePx) / 2;
    }

    private void updateAvailableFolderCellDimensions(Resources res) {
        updateFolderCellSize(1f, res);

        final int folderBottomPanelSize = res.getDimensionPixelSize(R.dimen.folder_label_height);

        // Don't let the folder get too close to the edges of the screen.
        int folderMargin = edgeMarginPx * 2;
        Point totalWorkspacePadding = getTotalWorkspacePadding();

        // Check if the icons fit within the available height.
        float contentUsedHeight = folderCellHeightPx * inv.numFolderRows
                + ((inv.numFolderRows - 1) * folderCellLayoutBorderSpacingPx);
        int contentMaxHeight = availableHeightPx - totalWorkspacePadding.y - folderBottomPanelSize
                - folderMargin - folderContentPaddingTop;
        float scaleY = contentMaxHeight / contentUsedHeight;

        // Check if the icons fit within the available width.
        float contentUsedWidth = folderCellWidthPx * inv.numFolderColumns
                + ((inv.numFolderColumns - 1) * folderCellLayoutBorderSpacingPx);
        int contentMaxWidth = availableWidthPx - totalWorkspacePadding.x - folderMargin
                - folderContentPaddingLeftRight * 2;
        float scaleX = contentMaxWidth / contentUsedWidth;

        float scale = Math.min(scaleX, scaleY);
        if (scale < 1f) {
            updateFolderCellSize(scale, res);
        }
    }

    private void updateFolderCellSize(float scale, Resources res) {
        float invIconSizeDp = isVerticalBarLayout() ? inv.landscapeIconSize : inv.iconSize;
        folderChildIconSizePx = Math.max(1, pxFromDp(invIconSizeDp, mMetrics, scale));
        folderChildTextSizePx = pxFromSp(inv.iconTextSize, mMetrics, scale);
        folderLabelTextSizePx = (int) (folderChildTextSizePx * folderLabelTextScale);

        int textHeight = Utilities.calculateTextHeight(folderChildTextSizePx);

        if (isScalableGrid) {
            int minWidth = folderChildIconSizePx + iconDrawablePaddingPx * 2;
            int minHeight = folderChildIconSizePx + iconDrawablePaddingPx * 2 + textHeight;

            folderCellWidthPx = (int) Math.max(minWidth, cellWidthPx * scale);
            folderCellHeightPx = (int) Math.max(minHeight, cellHeightPx * scale);

            int borderSpacing = (int) (cellLayoutBorderSpacingOriginalPx * scale);
            folderCellLayoutBorderSpacingPx = borderSpacing;
            folderContentPaddingLeftRight = borderSpacing;
            folderContentPaddingTop = borderSpacing;
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
        updateWorkspacePadding();
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
        // Since we are only concerned with the overall padding, layout direction does
        // not matter.
        Point padding = getTotalWorkspacePadding();
        result.x = calculateCellWidth(availableWidthPx - padding.x
                - cellLayoutPaddingLeftRightPx * 2, cellLayoutBorderSpacingPx, inv.numColumns);
        result.y = calculateCellHeight(availableHeightPx - padding.y
                - cellLayoutBottomPaddingPx, cellLayoutBorderSpacingPx, inv.numRows);
        return result;
    }

    public Point getTotalWorkspacePadding() {
        updateWorkspacePadding();
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
            int hotseatTop = isTaskbarPresent ? taskbarSize : hotseatBarSizePx;
            int paddingBottom = hotseatTop + workspacePageIndicatorHeight
                    + workspaceBottomPadding - mWorkspacePageIndicatorOverlapWorkspace;
            if (isTablet) {
                // Pad the left and right of the workspace to ensure consistent spacing
                // between all icons
                // The amount of screen space available for left/right padding.
                int availablePaddingX = Math.max(0, widthPx - ((inv.numColumns * cellWidthPx) +
                        ((inv.numColumns - 1) * cellWidthPx)));
                availablePaddingX = (int) Math.min(availablePaddingX,
                        widthPx * MAX_HORIZONTAL_PADDING_PERCENT);
                int hotseatVerticalPadding = isTaskbarPresent ? 0
                        : hotseatBarTopPaddingPx + hotseatBarBottomPaddingPx;
                int availablePaddingY = Math.max(0, heightPx - edgeMarginPx - paddingBottom
                        - (2 * inv.numRows * cellHeightPx) - hotseatVerticalPadding);
                padding.set(availablePaddingX / 2, edgeMarginPx + availablePaddingY / 2,
                        availablePaddingX / 2, paddingBottom + availablePaddingY / 2);

                if (isTwoPanels) {
                    padding.set(0, padding.top, 0, padding.bottom);
                }
            } else {
                // Pad the top and bottom of the workspace with search/hotseat bar sizes
                padding.set(desiredWorkspaceLeftRightMarginPx,
                        workspaceTopPadding + (isScalableGrid ? 0 : edgeMarginPx),
                        desiredWorkspaceLeftRightMarginPx,
                        paddingBottom);
            }
        }
    }

    public Rect getHotseatLayoutPadding() {
        if (isVerticalBarLayout()) {
            if (isSeascape()) {
                mHotseatPadding.set(mInsets.left + hotseatBarSidePaddingStartPx,
                        mInsets.top, hotseatBarSidePaddingEndPx, mInsets.bottom);
            } else {
                mHotseatPadding.set(hotseatBarSidePaddingEndPx, mInsets.top,
                        mInsets.right + hotseatBarSidePaddingStartPx, mInsets.bottom);
            }
        } else {
            // We want the edges of the hotseat to line up with the edges of the workspace, but the
            // icons in the hotseat are a different size, and so don't line up perfectly. To account
            // for this, we pad the left and right of the hotseat with half of the difference of a
            // workspace cell vs a hotseat cell.
            float workspaceCellWidth = (float) widthPx / inv.numColumns;
            float hotseatCellWidth = (float) widthPx / numShownHotseatIcons;
            int hotseatAdjustment = Math.round((workspaceCellWidth - hotseatCellWidth) / 2);
            mHotseatPadding.set(
                    hotseatAdjustment + workspacePadding.left + cellLayoutPaddingLeftRightPx
                            + mInsets.left,
                    hotseatBarTopPaddingPx,
                    hotseatAdjustment + workspacePadding.right + cellLayoutPaddingLeftRightPx
                            + mInsets.right,
                    hotseatBarSizePx - hotseatCellHeightPx - hotseatBarTopPaddingPx
                            + cellLayoutBottomPaddingPx + mInsets.bottom);
        }
        return mHotseatPadding;
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

    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "DeviceProfile:");
        writer.println(prefix + "\t1 dp = " + mMetrics.density + " px");

        writer.println(prefix + "\tallowRotation:" + allowRotation);
        writer.println(prefix + "\tisTablet:" + isTablet);
        writer.println(prefix + "\tisPhone:" + isPhone);
        writer.println(prefix + "\ttransposeLayoutWithOrientation:"
                + transposeLayoutWithOrientation);

        writer.println(prefix + "\tisLandscape:" + isLandscape);
        writer.println(prefix + "\tisMultiWindowMode:" + isMultiWindowMode);
        writer.println(prefix + "\tisTwoPanels:" + isTwoPanels);

        writer.println(prefix + pxToDpStr("windowX", windowX));
        writer.println(prefix + pxToDpStr("windowY", windowY));
        writer.println(prefix + pxToDpStr("widthPx", widthPx));
        writer.println(prefix + pxToDpStr("heightPx", heightPx));

        writer.println(prefix + pxToDpStr("availableWidthPx", availableWidthPx));
        writer.println(prefix + pxToDpStr("availableHeightPx", availableHeightPx));

        writer.println(prefix + "\taspectRatio:" + aspectRatio);

        writer.println(prefix + "\tisScalableGrid:" + isScalableGrid);

        writer.println(prefix + "\tinv.minCellWidth:" + inv.minCellWidth + "dp");
        writer.println(prefix + "\tinv.minCellHeight:" + inv.minCellHeight + "dp");

        writer.println(prefix + "\tinv.numColumns:" + inv.numColumns);
        writer.println(prefix + "\tinv.numRows:" + inv.numRows);

        writer.println(prefix + pxToDpStr("cellWidthPx", cellWidthPx));
        writer.println(prefix + pxToDpStr("cellHeightPx", cellHeightPx));

        writer.println(prefix + pxToDpStr("getCellSize().x", getCellSize().x));
        writer.println(prefix + pxToDpStr("getCellSize().y", getCellSize().y));

        writer.println(prefix + "\tinv.iconSize:" + inv.iconSize + "dp");
        writer.println(prefix + pxToDpStr("iconSizePx", iconSizePx));
        writer.println(prefix + pxToDpStr("iconTextSizePx", iconTextSizePx));
        writer.println(prefix + pxToDpStr("iconDrawablePaddingPx", iconDrawablePaddingPx));

        writer.println(prefix + pxToDpStr("folderCellWidthPx", folderCellWidthPx));
        writer.println(prefix + pxToDpStr("folderCellHeightPx", folderCellHeightPx));
        writer.println(prefix + pxToDpStr("folderChildIconSizePx", folderChildIconSizePx));
        writer.println(prefix + pxToDpStr("folderChildTextSizePx", folderChildTextSizePx));
        writer.println(prefix + pxToDpStr("folderChildDrawablePaddingPx",
                folderChildDrawablePaddingPx));
        writer.println(prefix + pxToDpStr("folderCellLayoutBorderSpacingPx",
                folderCellLayoutBorderSpacingPx));

        writer.println(prefix + pxToDpStr("cellLayoutBorderSpacingPx",
                cellLayoutBorderSpacingPx));
        writer.println(prefix + pxToDpStr("desiredWorkspaceLeftRightMarginPx",
                desiredWorkspaceLeftRightMarginPx));

        writer.println(prefix + pxToDpStr("allAppsIconSizePx", allAppsIconSizePx));
        writer.println(prefix + pxToDpStr("allAppsIconTextSizePx", allAppsIconTextSizePx));
        writer.println(prefix + pxToDpStr("allAppsIconDrawablePaddingPx",
                allAppsIconDrawablePaddingPx));
        writer.println(prefix + pxToDpStr("allAppsCellHeightPx", allAppsCellHeightPx));
        writer.println(prefix + "\tnumShownAllAppsColumns: " + numShownAllAppsColumns);

        writer.println(prefix + pxToDpStr("hotseatBarSizePx", hotseatBarSizePx));
        writer.println(prefix + pxToDpStr("hotseatCellHeightPx", hotseatCellHeightPx));
        writer.println(prefix + pxToDpStr("hotseatBarTopPaddingPx", hotseatBarTopPaddingPx));
        writer.println(prefix + pxToDpStr("hotseatBarBottomPaddingPx", hotseatBarBottomPaddingPx));
        writer.println(prefix + pxToDpStr("hotseatBarSidePaddingStartPx",
                hotseatBarSidePaddingStartPx));
        writer.println(prefix + pxToDpStr("hotseatBarSidePaddingEndPx",
                hotseatBarSidePaddingEndPx));
        writer.println(prefix + "\tnumShownHotseatIcons: " + numShownHotseatIcons);

        writer.println(prefix + "\tisTaskbarPresent:" + isTaskbarPresent);

        writer.println(prefix + pxToDpStr("taskbarSize", taskbarSize));
        writer.println(prefix + pxToDpStr("nonOverlappingTaskbarInset",
                nonOverlappingTaskbarInset));

        writer.println(prefix + pxToDpStr("workspacePadding.left", workspacePadding.left));
        writer.println(prefix + pxToDpStr("workspacePadding.top", workspacePadding.top));
        writer.println(prefix + pxToDpStr("workspacePadding.right", workspacePadding.right));
        writer.println(prefix + pxToDpStr("workspacePadding.bottom", workspacePadding.bottom));

        writer.println(prefix + pxToDpStr("iconScale", iconScale));
        writer.println(prefix + pxToDpStr("cellScaleToFit ", cellScaleToFit));
        writer.println(prefix + pxToDpStr("extraSpace", extraSpace));

        if (inv.devicePaddings != null) {
            int unscaledExtraSpace = (int) (extraSpace / iconScale);
            writer.println(prefix + pxToDpStr("maxEmptySpace",
                    inv.devicePaddings.getDevicePadding(unscaledExtraSpace).getMaxEmptySpacePx()));
        }
        writer.println(prefix + pxToDpStr("workspaceTopPadding", workspaceTopPadding));
        writer.println(prefix + pxToDpStr("workspaceBottomPadding", workspaceBottomPadding));
        writer.println(prefix + pxToDpStr("extraHotseatBottomPadding", extraHotseatBottomPadding));
    }

    private static Context getContext(Context c, Info info, int orientation) {
        Configuration config = new Configuration(c.getResources().getConfiguration());
        config.orientation = orientation;
        config.densityDpi = info.densityDpi;
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

    public static class Builder {
        private Context mContext;
        private InvariantDeviceProfile mInv;
        private Info mInfo;

        private WindowBounds mWindowBounds;
        private boolean mUseTwoPanels;

        private boolean mIsMultiWindowMode = false;
        private Boolean mTransposeLayoutWithOrientation;

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

        public DeviceProfile build() {
            if (mWindowBounds == null) {
                throw new IllegalArgumentException("Window bounds not set");
            }
            if (mTransposeLayoutWithOrientation == null) {
                mTransposeLayoutWithOrientation = !mInfo.isTablet(mWindowBounds);
            }
            return new DeviceProfile(mContext, mInv, mInfo, mWindowBounds,
                    mIsMultiWindowMode, mTransposeLayoutWithOrientation, mUseTwoPanels);
        }
    }

}
