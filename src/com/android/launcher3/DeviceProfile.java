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

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.view.Surface;

import com.android.launcher3.CellLayout.ContainerType;
import com.android.launcher3.graphics.IconShape;
import com.android.launcher3.icons.DotRenderer;
import com.android.launcher3.icons.IconNormalizer;
import com.android.launcher3.util.DefaultDisplay;
import com.android.launcher3.util.WindowBounds;

public class DeviceProfile {

    private static final float TABLET_MIN_DPS = 600;
    private static final float LARGE_TABLET_MIN_DPS = 720;


    public final InvariantDeviceProfile inv;
    private final DefaultDisplay.Info mInfo;

    // Device properties
    public final boolean isTablet;
    public final boolean isLargeTablet;
    public final boolean isPhone;
    public final boolean transposeLayoutWithOrientation;

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

    /**
     * The maximum amount of left/right workspace padding as a percentage of the screen width.
     * To be clear, this means that up to 7% of the screen width can be used as left padding, and
     * 7% of the screen width can be used as right padding.
     */
    private static final float MAX_HORIZONTAL_PADDING_PERCENT = 0.14f;

    private static final float TALL_DEVICE_ASPECT_RATIO_THRESHOLD = 2.0f;

    // To evenly space the icons, increase the left/right margins for tablets in portrait mode.
    private static final int PORTRAIT_TABLET_LEFT_RIGHT_PADDING_MULTIPLIER = 4;

    // Workspace
    public final int desiredWorkspaceLeftRightMarginPx;
    public final int cellLayoutPaddingLeftRightPx;
    public final int cellLayoutBottomPaddingPx;
    public final int edgeMarginPx;
    public float workspaceSpringLoadShrinkFactor;
    public final int workspaceSpringLoadedBottomSpace;

    // Workspace page indicator
    public final int workspacePageIndicatorHeight;
    private final int mWorkspacePageIndicatorOverlapWorkspace;

    // Workspace icons
    public int iconSizePx;
    public int iconTextSizePx;
    public int iconDrawablePaddingPx;
    public int iconDrawablePaddingOriginalPx;

    public int cellWidthPx;
    public int cellHeightPx;
    public int workspaceCellPaddingXPx;

    // Folder
    public int folderIconSizePx;
    public int folderIconOffsetYPx;

    // Folder cell
    public int folderCellWidthPx;
    public int folderCellHeightPx;

    // Folder child
    public int folderChildIconSizePx;
    public int folderChildTextSizePx;
    public int folderChildDrawablePaddingPx;

    // Hotseat
    public int hotseatCellHeightPx;
    // In portrait: size = height, in landscape: size = width
    public int hotseatBarSizePx;
    public final int hotseatBarTopPaddingPx;
    public int hotseatBarBottomPaddingPx;
    // Start is the side next to the nav bar, end is the side next to the workspace
    public final int hotseatBarSidePaddingStartPx;
    public final int hotseatBarSidePaddingEndPx;

    // All apps
    public int allAppsCellHeightPx;
    public int allAppsCellWidthPx;
    public int allAppsIconSizePx;
    public int allAppsIconDrawablePaddingPx;
    public float allAppsIconTextSizePx;

    // Widgets
    public final PointF appWidgetScale = new PointF(1.0f, 1.0f);

    // Drop Target
    public int dropTargetBarSizePx;

    // Insets
    private final Rect mInsets = new Rect();
    public final Rect workspacePadding = new Rect();
    private final Rect mHotseatPadding = new Rect();
    // When true, nav bar is on the left side of the screen.
    private boolean mIsSeascape;

    // Notification dots
    public DotRenderer mDotRendererWorkSpace;
    public DotRenderer mDotRendererAllApps;

    DeviceProfile(Context context, InvariantDeviceProfile inv, DefaultDisplay.Info info,
            Point minSize, Point maxSize, int width, int height, boolean isLandscape,
            boolean isMultiWindowMode, boolean transposeLayoutWithOrientation,
            Point windowPosition) {

        this.inv = inv;
        this.isLandscape = isLandscape;
        this.isMultiWindowMode = isMultiWindowMode;
        windowX = windowPosition.x;
        windowY = windowPosition.y;

        // Determine sizes.
        widthPx = width;
        heightPx = height;
        if (isLandscape) {
            availableWidthPx = maxSize.x;
            availableHeightPx = minSize.y;
        } else {
            availableWidthPx = minSize.x;
            availableHeightPx = maxSize.y;
        }

        mInfo = info;

        // Constants from resources
        float swDPs = Utilities.dpiFromPx(
                Math.min(info.smallestSize.x, info.smallestSize.y), info.metrics);
        isTablet = swDPs >= TABLET_MIN_DPS;
        isLargeTablet = swDPs >= LARGE_TABLET_MIN_DPS;
        isPhone = !isTablet && !isLargeTablet;
        aspectRatio = ((float) Math.max(widthPx, heightPx)) / Math.min(widthPx, heightPx);
        boolean isTallDevice = Float.compare(aspectRatio, TALL_DEVICE_ASPECT_RATIO_THRESHOLD) >= 0;

        // Some more constants
        this.transposeLayoutWithOrientation = transposeLayoutWithOrientation;

        context = getContext(context, info, isVerticalBarLayout()
                ? Configuration.ORIENTATION_LANDSCAPE
                : Configuration.ORIENTATION_PORTRAIT);
        final Resources res = context.getResources();

        edgeMarginPx = res.getDimensionPixelSize(R.dimen.dynamic_grid_edge_margin);
        desiredWorkspaceLeftRightMarginPx = isVerticalBarLayout() ? 0 : edgeMarginPx;

        int cellLayoutPaddingLeftRightMultiplier = !isVerticalBarLayout() && isTablet
                ? PORTRAIT_TABLET_LEFT_RIGHT_PADDING_MULTIPLIER : 1;
        int cellLayoutPadding = res.getDimensionPixelSize(R.dimen.dynamic_grid_cell_layout_padding);
        if (isLandscape) {
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
        workspaceSpringLoadedBottomSpace =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_min_spring_loaded_space);

        workspaceCellPaddingXPx = res.getDimensionPixelSize(R.dimen.dynamic_grid_cell_padding_x);

        hotseatBarTopPaddingPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_hotseat_top_padding);
        hotseatBarBottomPaddingPx = (isTallDevice ? 0
                : res.getDimensionPixelSize(R.dimen.dynamic_grid_hotseat_bottom_non_tall_padding))
                + res.getDimensionPixelSize(R.dimen.dynamic_grid_hotseat_bottom_padding);
        hotseatBarSidePaddingEndPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_hotseat_side_padding);
        // Add a bit of space between nav bar and hotseat in vertical bar layout.
        hotseatBarSidePaddingStartPx = isVerticalBarLayout() ? workspacePageIndicatorHeight : 0;
        hotseatBarSizePx = ResourceUtils.pxFromDp(inv.iconSize, mInfo.metrics)
                + (isVerticalBarLayout()
                ? (hotseatBarSidePaddingStartPx + hotseatBarSidePaddingEndPx)
                : (res.getDimensionPixelSize(R.dimen.dynamic_grid_hotseat_extra_vertical_size)
                        + hotseatBarTopPaddingPx + hotseatBarBottomPaddingPx));

        // Calculate all of the remaining variables.
        updateAvailableDimensions(res);

        // Now that we have all of the variables calculated, we can tune certain sizes.
        if (!isVerticalBarLayout() && isPhone && isTallDevice) {
            // We increase the hotseat size when there is extra space.
            // ie. For a display with a large aspect ratio, we can keep the icons on the workspace
            // in portrait mode closer together by adding more height to the hotseat.
            // Note: This calculation was created after noticing a pattern in the design spec.
            int extraSpace = getCellSize().y - iconSizePx - iconDrawablePaddingPx * 2
                    - workspacePageIndicatorHeight;
            hotseatBarSizePx += extraSpace;
            hotseatBarBottomPaddingPx += extraSpace;

            // Recalculate the available dimensions using the new hotseat size.
            updateAvailableDimensions(res);
        }
        updateWorkspacePadding();

        // This is done last, after iconSizePx is calculated above.
        mDotRendererWorkSpace = new DotRenderer(iconSizePx, IconShape.getShapePath(),
                IconShape.DEFAULT_PATH_SIZE);
        mDotRendererAllApps = iconSizePx == allAppsIconSizePx ? mDotRendererWorkSpace :
                new DotRenderer(allAppsIconSizePx, IconShape.getShapePath(),
                        IconShape.DEFAULT_PATH_SIZE);
    }

    public Builder toBuilder(Context context) {
        Point size = new Point(availableWidthPx, availableHeightPx);
        return new Builder(context, inv, mInfo)
                .setSizeRange(size, size)
                .setSize(widthPx, heightPx)
                .setWindowPosition(windowX, windowY)
                .setMultiWindowMode(isMultiWindowMode);
    }

    public DeviceProfile copy(Context context) {
        return toBuilder(context).build();
    }

    /**
     * TODO: Move this to the builder as part of setMultiWindowMode
     */
    public DeviceProfile getMultiWindowProfile(Context context, WindowBounds windowBounds) {
        // We take the minimum sizes of this profile and it's multi-window variant to ensure that
        // the system decor is always excluded.
        Point mwSize = new Point(Math.min(availableWidthPx, windowBounds.availableSize.x),
                Math.min(availableHeightPx, windowBounds.availableSize.y));

        DeviceProfile profile = toBuilder(context)
                .setSizeRange(mwSize, mwSize)
                .setSize(windowBounds.bounds.width(), windowBounds.bounds.height())
                .setWindowPosition(windowBounds.bounds.left, windowBounds.bounds.top)
                .setMultiWindowMode(true)
                .build();

        // If there isn't enough vertical cell padding with the labels displayed, hide the labels.
        float workspaceCellPaddingY = profile.getCellSize().y - profile.iconSizePx
                - iconDrawablePaddingPx - profile.iconTextSizePx;
        if (workspaceCellPaddingY < profile.iconDrawablePaddingPx * 2) {
            profile.adjustToHideWorkspaceLabels();
        }

        // We use these scales to measure and layout the widgets using their full invariant profile
        // sizes and then draw them scaled and centered to fit in their multi-window mode cellspans.
        float appWidgetScaleX = (float) profile.getCellSize().x / getCellSize().x;
        float appWidgetScaleY = (float) profile.getCellSize().y / getCellSize().y;
        profile.appWidgetScale.set(appWidgetScaleX, appWidgetScaleY);
        profile.updateWorkspacePadding();

        return profile;
    }

    /**
     * Inverse of {@link #getMultiWindowProfile(Context, WindowBounds)}
     * @return device profile corresponding to the current orientation in non multi-window mode.
     */
    public DeviceProfile getFullScreenProfile() {
        return isLandscape ? inv.landscapeProfile : inv.portraitProfile;
    }

    /**
     * Adjusts the profile so that the labels on the Workspace are hidden.
     * It is important to call this method after the All Apps variables have been set.
     */
    private void adjustToHideWorkspaceLabels() {
        iconTextSizePx = 0;
        iconDrawablePaddingPx = 0;
        cellHeightPx = iconSizePx;
        autoResizeAllAppsCells();
    }

    /**
     * Re-computes the all-apps cell size to be independent of workspace
     */
    public void autoResizeAllAppsCells() {
        int topBottomPadding = allAppsIconDrawablePaddingPx * (isVerticalBarLayout() ? 2 : 1);
        allAppsCellHeightPx = allAppsIconSizePx + allAppsIconDrawablePaddingPx
                + Utilities.calculateTextHeight(allAppsIconTextSizePx)
                + topBottomPadding * 2;
    }

    private void updateAvailableDimensions(Resources res) {
        updateIconSize(1f, res);

        // Check to see if the icons fit within the available height.  If not, then scale down.
        float usedHeight = (cellHeightPx * inv.numRows);
        int maxHeight = (availableHeightPx - getTotalWorkspacePadding().y);
        if (usedHeight > maxHeight) {
            float scale = maxHeight / usedHeight;
            updateIconSize(scale, res);
        }
        updateAvailableFolderCellDimensions(res);
    }

    /**
     * Updating the iconSize affects many aspects of the launcher layout, such as: iconSizePx,
     * iconTextSizePx, iconDrawablePaddingPx, cellWidth/Height, allApps* variants,
     * hotseat sizes, workspaceSpringLoadedShrinkFactor, folderIconSizePx, and folderIconOffsetYPx.
     */
    private void updateIconSize(float scale, Resources res) {
        // Workspace
        final boolean isVerticalLayout = isVerticalBarLayout();
        float invIconSizeDp = isVerticalLayout ? inv.landscapeIconSize : inv.iconSize;
        iconSizePx = Math.max(1, (int) (ResourceUtils.pxFromDp(invIconSizeDp, mInfo.metrics)
                * scale));
        iconTextSizePx = (int) (Utilities.pxFromSp(inv.iconTextSize, mInfo.metrics) * scale);
        iconDrawablePaddingPx = (int) (iconDrawablePaddingOriginalPx * scale);

        cellHeightPx = iconSizePx + iconDrawablePaddingPx
                + Utilities.calculateTextHeight(iconTextSizePx);
        int cellYPadding = (getCellSize().y - cellHeightPx) / 2;
        if (iconDrawablePaddingPx > cellYPadding && !isVerticalLayout
                && !isMultiWindowMode) {
            // Ensures that the label is closer to its corresponding icon. This is not an issue
            // with vertical bar layout or multi-window mode since the issue is handled separately
            // with their calls to {@link #adjustToHideWorkspaceLabels}.
            cellHeightPx -= (iconDrawablePaddingPx - cellYPadding);
            iconDrawablePaddingPx = cellYPadding;
        }
        cellWidthPx = iconSizePx + iconDrawablePaddingPx;

        // All apps
        if (allAppsHasDifferentNumColumns()) {
            allAppsIconSizePx = ResourceUtils.pxFromDp(inv.allAppsIconSize, mInfo.metrics);
            allAppsIconTextSizePx = Utilities.pxFromSp(inv.allAppsIconTextSize, mInfo.metrics);
            allAppsIconDrawablePaddingPx = iconDrawablePaddingOriginalPx;
            // We use 4 below to ensure labels are closer to their corresponding icon.
            allAppsCellHeightPx = Math.round(allAppsIconSizePx + allAppsIconTextSizePx
                    + (4 * allAppsIconDrawablePaddingPx));
        } else {
            allAppsIconSizePx = iconSizePx;
            allAppsIconTextSizePx = iconTextSizePx;
            allAppsIconDrawablePaddingPx = iconDrawablePaddingPx;
            allAppsCellHeightPx = getCellSize().y;
        }
        allAppsCellWidthPx = allAppsIconSizePx + allAppsIconDrawablePaddingPx;

        if (isVerticalBarLayout()) {
            // Always hide the Workspace text with vertical bar layout.
            adjustToHideWorkspaceLabels();
        }

        // Hotseat
        if (isVerticalLayout) {
            hotseatBarSizePx = iconSizePx + hotseatBarSidePaddingStartPx
                    + hotseatBarSidePaddingEndPx;
        }
        hotseatCellHeightPx = iconSizePx;

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
        int folderBottomPanelSize = res.getDimensionPixelSize(R.dimen.folder_label_padding_top)
                + res.getDimensionPixelSize(R.dimen.folder_label_padding_bottom)
                + Utilities.calculateTextHeight(res.getDimension(R.dimen.folder_label_text_size));

        updateFolderCellSize(1f, res);

        // Don't let the folder get too close to the edges of the screen.
        int folderMargin = edgeMarginPx * 2;
        Point totalWorkspacePadding = getTotalWorkspacePadding();

        // Check if the icons fit within the available height.
        float contentUsedHeight = folderCellHeightPx * inv.numFolderRows;
        int contentMaxHeight = availableHeightPx - totalWorkspacePadding.y - folderBottomPanelSize
                - folderMargin;
        float scaleY = contentMaxHeight / contentUsedHeight;

        // Check if the icons fit within the available width.
        float contentUsedWidth = folderCellWidthPx * inv.numFolderColumns;
        int contentMaxWidth = availableWidthPx - totalWorkspacePadding.x - folderMargin;
        float scaleX = contentMaxWidth / contentUsedWidth;

        float scale = Math.min(scaleX, scaleY);
        if (scale < 1f) {
            updateFolderCellSize(scale, res);
        }
    }

    private void updateFolderCellSize(float scale, Resources res) {
        folderChildIconSizePx = (int) (ResourceUtils.pxFromDp(inv.iconSize, mInfo.metrics) * scale);
        folderChildTextSizePx =
                (int) (res.getDimensionPixelSize(R.dimen.folder_child_text_size) * scale);

        int textHeight = Utilities.calculateTextHeight(folderChildTextSizePx);
        int cellPaddingX = (int) (res.getDimensionPixelSize(R.dimen.folder_cell_x_padding) * scale);
        int cellPaddingY = (int) (res.getDimensionPixelSize(R.dimen.folder_cell_y_padding) * scale);

        folderCellWidthPx = folderChildIconSizePx + 2 * cellPaddingX;
        folderCellHeightPx = folderChildIconSizePx + 2 * cellPaddingY + textHeight;
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
        return getCellSize(inv.numColumns, inv.numRows);
    }

    private Point getCellSize(int numColumns, int numRows) {
        Point result = new Point();
        // Since we are only concerned with the overall padding, layout direction does
        // not matter.
        Point padding = getTotalWorkspacePadding();
        result.x = calculateCellWidth(availableWidthPx - padding.x
                - cellLayoutPaddingLeftRightPx * 2, numColumns);
        result.y = calculateCellHeight(availableHeightPx - padding.y
                - cellLayoutBottomPaddingPx, numRows);
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
            int paddingBottom = hotseatBarSizePx + workspacePageIndicatorHeight
                    - mWorkspacePageIndicatorOverlapWorkspace;
            if (isTablet) {
                // Pad the left and right of the workspace to ensure consistent spacing
                // between all icons
                // The amount of screen space available for left/right padding.
                int availablePaddingX = Math.max(0, widthPx - ((inv.numColumns * cellWidthPx) +
                        ((inv.numColumns - 1) * cellWidthPx)));
                availablePaddingX = (int) Math.min(availablePaddingX,
                        widthPx * MAX_HORIZONTAL_PADDING_PERCENT);
                int availablePaddingY = Math.max(0, heightPx - edgeMarginPx - paddingBottom
                        - (2 * inv.numRows * cellHeightPx) - hotseatBarTopPaddingPx
                        - hotseatBarBottomPaddingPx);
                padding.set(availablePaddingX / 2, edgeMarginPx + availablePaddingY / 2,
                        availablePaddingX / 2, paddingBottom + availablePaddingY / 2);
            } else {
                // Pad the top and bottom of the workspace with search/hotseat bar sizes
                padding.set(desiredWorkspaceLeftRightMarginPx,
                        edgeMarginPx,
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
            float hotseatCellWidth = (float) widthPx / inv.numHotseatIcons;
            int hotseatAdjustment = Math.round((workspaceCellWidth - hotseatCellWidth) / 2);
            mHotseatPadding.set(
                    hotseatAdjustment + workspacePadding.left + cellLayoutPaddingLeftRightPx
                            + mInsets.left,
                    hotseatBarTopPaddingPx,
                    hotseatAdjustment + workspacePadding.right + cellLayoutPaddingLeftRightPx
                            + mInsets.right,
                    hotseatBarBottomPaddingPx + mInsets.bottom + cellLayoutBottomPaddingPx);
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
            return new Rect(mInsets.left + edgeMarginPx,
                    mInsets.top + dropTargetBarSizePx + edgeMarginPx,
                    mInsets.left + availableWidthPx - edgeMarginPx,
                    mInsets.top + availableHeightPx - hotseatBarSizePx
                            - workspacePageIndicatorHeight - edgeMarginPx);
        }
    }

    public static int calculateCellWidth(int width, int countX) {
        return width / countX;
    }
    public static int calculateCellHeight(int height, int countY) {
        return height / countY;
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
     * Returns true when the number of workspace columns and all apps columns differs.
     */
    private boolean allAppsHasDifferentNumColumns() {
        return inv.numAllAppsColumns != inv.numColumns;
    }

    /**
     * Updates orientation information and returns true if it has changed from the previous value.
     */
    public boolean updateIsSeascape(Context context) {
        if (isVerticalBarLayout()) {
            boolean isSeascape = DefaultDisplay.INSTANCE.get(context).getInfo().rotation
                    == Surface.ROTATION_270;
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
        return isVerticalBarLayout() || isLargeTablet;
    }

    public int getCellHeight(@ContainerType int containerType) {
        switch (containerType) {
            case CellLayout.WORKSPACE:
                return cellHeightPx;
            case CellLayout.FOLDER:
                return folderCellHeightPx;
            case CellLayout.HOTSEAT:
                return hotseatCellHeightPx;
            default:
                // ??
                return 0;
        }
    }

    private static Context getContext(Context c, DefaultDisplay.Info info, int orientation) {
        Configuration config = new Configuration(c.getResources().getConfiguration());
        config.orientation = orientation;
        config.densityDpi = info.metrics.densityDpi;
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
        private DefaultDisplay.Info mInfo;

        private final Point mWindowPosition = new Point();
        private Point mMinSize, mMaxSize;
        private int mWidth, mHeight;

        private boolean mIsLandscape;
        private boolean mIsMultiWindowMode = false;
        private boolean mTransposeLayoutWithOrientation;

        public Builder(Context context, InvariantDeviceProfile inv, DefaultDisplay.Info info) {
            mContext = context;
            mInv = inv;
            mInfo = info;
            mTransposeLayoutWithOrientation = context.getResources()
                    .getBoolean(R.bool.hotseat_transpose_layout_with_orientation);
        }

        public Builder setSizeRange(Point minSize, Point maxSize) {
            mMinSize = minSize;
            mMaxSize = maxSize;
            return this;
        }

        public Builder setSize(int width, int height) {
            mWidth = width;
            mHeight = height;
            mIsLandscape = mWidth > mHeight;
            return this;
        }

        public Builder setMultiWindowMode(boolean isMultiWindowMode) {
            mIsMultiWindowMode = isMultiWindowMode;
            return this;
        }

        /**
         * Sets the window position if not full-screen
         */
        public Builder setWindowPosition(int x, int y) {
            mWindowPosition.set(x, y);
            return this;
        }

        public Builder setTransposeLayoutWithOrientation(boolean transposeLayoutWithOrientation) {
            mTransposeLayoutWithOrientation = transposeLayoutWithOrientation;
            return this;
        }

        public DeviceProfile build() {
            return new DeviceProfile(mContext, mInv, mInfo, mMinSize, mMaxSize,
                    mWidth, mHeight, mIsLandscape, mIsMultiWindowMode,
                    mTransposeLayoutWithOrientation, mWindowPosition);
        }
    }

}
