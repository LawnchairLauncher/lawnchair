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

import android.appwidget.AppWidgetHostView;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;

import com.android.launcher3.config.FeatureFlags;

import java.util.ArrayList;

public class DeviceProfile {

    public interface LauncherLayoutChangeListener {
        void onLauncherLayoutChanged();
    }

    public final InvariantDeviceProfile inv;

    // Device properties
    public final boolean isTablet;
    public final boolean isLargeTablet;
    public final boolean isPhone;
    public final boolean transposeLayoutWithOrientation;

    // Device properties in current orientation
    public final boolean isLandscape;
    public final int widthPx;
    public final int heightPx;
    public final int availableWidthPx;
    public final int availableHeightPx;
    /**
     * The maximum amount of left/right workspace padding as a percentage of the screen width.
     * To be clear, this means that up to 7% of the screen width can be used as left padding, and
     * 7% of the screen width can be used as right padding.
     */
    private static final float MAX_HORIZONTAL_PADDING_PERCENT = 0.14f;

    // Overview mode
    private final int overviewModeMinIconZoneHeightPx;
    private final int overviewModeMaxIconZoneHeightPx;
    private final int overviewModeBarItemWidthPx;
    private final int overviewModeBarSpacerWidthPx;
    private final float overviewModeIconZoneRatio;

    // Workspace
    private int desiredWorkspaceLeftRightMarginPx;
    public final int edgeMarginPx;
    public final Rect defaultWidgetPadding;
    private final int defaultPageSpacingPx;
    private final int topWorkspacePadding;
    private float dragViewScale;
    public float workspaceSpringLoadShrinkFactor;
    public final int workspaceSpringLoadedBottomSpace;

    // Page indicator
    private final int pageIndicatorHeightPx;
    private final int pageIndicatorLandGutterLeftNavBarPx;
    private final int pageIndicatorLandGutterRightNavBarPx;
    private final int pageIndicatorLandWorkspaceOffsetPx;

    // Workspace icons
    public int iconSizePx;
    public int iconTextSizePx;
    public int iconDrawablePaddingPx;
    public int iconDrawablePaddingOriginalPx;

    public int cellWidthPx;
    public int cellHeightPx;

    // Folder
    public int folderBackgroundOffset;
    public int folderIconSizePx;
    public int folderIconPreviewPadding;
    public int folderCellWidthPx;
    public int folderCellHeightPx;
    public int folderChildDrawablePaddingPx;

    // Hotseat
    public int hotseatCellWidthPx;
    public int hotseatCellHeightPx;
    public int hotseatIconSizePx;
    private int hotseatBarHeightPx;
    private int hotseatBarTopPaddingPx;
    private int hotseatLandGutterPx;

    // All apps
    public int allAppsNumCols;
    public int allAppsNumPredictiveCols;
    public int allAppsButtonVisualSize;
    public int allAppsIconSizePx;
    public int allAppsIconDrawablePaddingPx;
    public float allAppsIconTextSizePx;

    // Containers
    private final int containerLeftPaddingPx;
    private final int containerRightPaddingPx;

    // Drop Target
    public int dropTargetBarSizePx;

    // Insets
    private Rect mInsets = new Rect();

    // Listeners
    private ArrayList<LauncherLayoutChangeListener> mListeners = new ArrayList<>();

    public DeviceProfile(Context context, InvariantDeviceProfile inv,
            Point minSize, Point maxSize,
            int width, int height, boolean isLandscape) {

        this.inv = inv;
        this.isLandscape = isLandscape;

        Resources res = context.getResources();
        DisplayMetrics dm = res.getDisplayMetrics();

        // Constants from resources
        isTablet = res.getBoolean(R.bool.is_tablet);
        isLargeTablet = res.getBoolean(R.bool.is_large_tablet);
        isPhone = !isTablet && !isLargeTablet;

        // Some more constants
        transposeLayoutWithOrientation =
                res.getBoolean(R.bool.hotseat_transpose_layout_with_orientation);

        ComponentName cn = new ComponentName(context.getPackageName(),
                this.getClass().getName());
        defaultWidgetPadding = AppWidgetHostView.getDefaultPaddingForWidget(context, cn, null);
        edgeMarginPx = res.getDimensionPixelSize(R.dimen.dynamic_grid_edge_margin);
        desiredWorkspaceLeftRightMarginPx = edgeMarginPx;
        pageIndicatorHeightPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_page_indicator_height);
        pageIndicatorLandGutterLeftNavBarPx = res.getDimensionPixelSize(
                R.dimen.dynamic_grid_page_indicator_gutter_width_left_nav_bar);
        pageIndicatorLandWorkspaceOffsetPx =
                res.getDimensionPixelSize(R.dimen.all_apps_caret_workspace_offset);
        pageIndicatorLandGutterRightNavBarPx = res.getDimensionPixelSize(
                R.dimen.dynamic_grid_page_indicator_gutter_width_right_nav_bar);
        defaultPageSpacingPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_workspace_page_spacing);
        topWorkspacePadding =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_workspace_top_padding);
        overviewModeMinIconZoneHeightPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_overview_min_icon_zone_height);
        overviewModeMaxIconZoneHeightPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_overview_max_icon_zone_height);
        overviewModeBarItemWidthPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_overview_bar_item_width);
        overviewModeBarSpacerWidthPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_overview_bar_spacer_width);
        overviewModeIconZoneRatio =
                res.getInteger(R.integer.config_dynamic_grid_overview_icon_zone_percentage) / 100f;
        iconDrawablePaddingOriginalPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_icon_drawable_padding);
        dropTargetBarSizePx = res.getDimensionPixelSize(R.dimen.dynamic_grid_drop_target_size);
        workspaceSpringLoadedBottomSpace =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_min_spring_loaded_space);
        hotseatBarHeightPx = res.getDimensionPixelSize(R.dimen.dynamic_grid_hotseat_height);
        hotseatBarTopPaddingPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_hotseat_top_padding);
        hotseatLandGutterPx = res.getDimensionPixelSize(R.dimen.dynamic_grid_hotseat_gutter_width);
        containerLeftPaddingPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_container_land_left_padding);
        containerRightPaddingPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_container_land_right_padding);

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

        // Calculate the remaining vars
        updateAvailableDimensions(dm, res);
        computeAllAppsButtonSize(context);
    }

    public void addLauncherLayoutChangedListener(LauncherLayoutChangeListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeLauncherLayoutChangedListener(LauncherLayoutChangeListener listener) {
        if (mListeners.contains(listener)) {
            mListeners.remove(listener);
        }
    }

    /**
     * Determine the exact visual footprint of the all apps button, taking into account scaling
     * and internal padding of the drawable.
     */
    private void computeAllAppsButtonSize(Context context) {
        Resources res = context.getResources();
        float padding = res.getInteger(R.integer.config_allAppsButtonPaddingPercent) / 100f;
        allAppsButtonVisualSize = (int) (hotseatIconSizePx * (1 - padding)) - context.getResources()
                        .getDimensionPixelSize(R.dimen.all_apps_button_scale_down);
    }

    private void updateAvailableDimensions(DisplayMetrics dm, Resources res) {
        // Check to see if the icons fit in the new available height.  If not, then we need to
        // shrink the icon size.
        float scale = 1f;
        int drawablePadding = iconDrawablePaddingOriginalPx;
        updateIconSize(1f, drawablePadding, res, dm);
        float usedHeight = (cellHeightPx * inv.numRows);

        int maxHeight = (availableHeightPx - getTotalWorkspacePadding().y);
        if (usedHeight > maxHeight) {
            scale = maxHeight / usedHeight;
            drawablePadding = 0;
        }
        updateIconSize(scale, drawablePadding, res, dm);
    }

    private void updateIconSize(float scale, int drawablePadding, Resources res,
                                DisplayMetrics dm) {
        iconSizePx = (int) (Utilities.pxFromDp(inv.iconSize, dm) * scale);
        iconTextSizePx = (int) (Utilities.pxFromSp(inv.iconTextSize, dm) * scale);
        iconDrawablePaddingPx = drawablePadding;
        hotseatIconSizePx = (int) (Utilities.pxFromDp(inv.hotseatIconSize, dm) * scale);
        allAppsIconSizePx = iconSizePx;
        allAppsIconDrawablePaddingPx = iconDrawablePaddingPx;
        allAppsIconTextSizePx = iconTextSizePx;

        cellWidthPx = iconSizePx;
        cellHeightPx = iconSizePx + iconDrawablePaddingPx
                + Utilities.calculateTextHeight(iconTextSizePx);
        final float scaleDps = !FeatureFlags.LAUNCHER3_LEGACY_WORKSPACE_DND ? 0f
                : res.getDimensionPixelSize(R.dimen.dragViewScale);
        dragViewScale = (iconSizePx + scaleDps) / iconSizePx;

        // Hotseat
        hotseatCellWidthPx = iconSizePx;
        hotseatCellHeightPx = iconSizePx;

        if (!isVerticalBarLayout()) {
            int expectedWorkspaceHeight = availableHeightPx - hotseatBarHeightPx
                    - pageIndicatorHeightPx - topWorkspacePadding;
            float minRequiredHeight = dropTargetBarSizePx + workspaceSpringLoadedBottomSpace;
            workspaceSpringLoadShrinkFactor = Math.min(
                    res.getInteger(R.integer.config_workspaceSpringLoadShrinkPercentage) / 100.0f,
                    1 - (minRequiredHeight / expectedWorkspaceHeight));
        } else {
            workspaceSpringLoadShrinkFactor =
                    res.getInteger(R.integer.config_workspaceSpringLoadShrinkPercentage) / 100.0f;
        }

        // Folder cell
        int cellPaddingX = res.getDimensionPixelSize(R.dimen.folder_cell_x_padding);
        int cellPaddingY = res.getDimensionPixelSize(R.dimen.folder_cell_y_padding);
        final int folderChildTextSize =
                Utilities.calculateTextHeight(res.getDimension(R.dimen.folder_child_text_size));

        final int folderBottomPanelSize =
                res.getDimensionPixelSize(R.dimen.folder_label_padding_top)
                 + res.getDimensionPixelSize(R.dimen.folder_label_padding_bottom)
                + Utilities.calculateTextHeight(res.getDimension(R.dimen.folder_label_text_size));

        // Don't let the folder get too close to the edges of the screen.
        folderCellWidthPx = Math.min(iconSizePx + 2 * cellPaddingX,
                (availableWidthPx - 4 * edgeMarginPx) / inv.numFolderColumns);
        folderCellHeightPx = Math.min(iconSizePx + 3 * cellPaddingY + folderChildTextSize,
                (availableHeightPx - 4 * edgeMarginPx - folderBottomPanelSize) / inv.numFolderRows);
        folderChildDrawablePaddingPx = Math.max(0,
                (folderCellHeightPx - iconSizePx - folderChildTextSize) / 3);

        // Folder icon
        folderBackgroundOffset = -edgeMarginPx;
        folderIconSizePx = iconSizePx + 2 * -folderBackgroundOffset;
        folderIconPreviewPadding = res.getDimensionPixelSize(R.dimen.folder_preview_padding);
    }

    public void updateInsets(Rect insets) {
        mInsets.set(insets);
    }

    public void updateAppsViewNumCols() {
        allAppsNumCols = allAppsNumPredictiveCols = inv.numColumns;
    }

    /** Returns the width and height of the search bar, ignoring any padding. */
    public Point getSearchBarDimensForWidgetOpts() {
        if (isVerticalBarLayout()) {
            return new Point(dropTargetBarSizePx, availableHeightPx - 2 * edgeMarginPx);
        } else {
            int gap;
            if (isTablet) {
                // Pad the left and right of the workspace to ensure consistent spacing
                // between all icons
                int width = getCurrentWidth();
                // XXX: If the icon size changes across orientations, we will have to take
                //      that into account here too.
                gap = ((width - 2 * edgeMarginPx
                        - (inv.numColumns * cellWidthPx)) / (2 * (inv.numColumns + 1)))
                        + edgeMarginPx;
            } else {
                gap = desiredWorkspaceLeftRightMarginPx - defaultWidgetPadding.right;
            }
            return new Point(availableWidthPx - 2 * gap, dropTargetBarSizePx);
        }
    }

    public Point getCellSize() {
        Point result = new Point();
        // Since we are only concerned with the overall padding, layout direction does
        // not matter.
        Point padding = getTotalWorkspacePadding();
        result.x = calculateCellWidth(availableWidthPx - padding.x, inv.numColumns);
        result.y = calculateCellHeight(availableHeightPx - padding.y, inv.numRows);
        return result;
    }

    public Point getTotalWorkspacePadding() {
        Rect padding = getWorkspacePadding(null);
        return new Point(padding.left + padding.right, padding.top + padding.bottom);
    }

    /**
     * Returns the workspace padding in the specified orientation.
     * Note that it assumes that while in verticalBarLayout, the nav bar is on the right, as such
     * this value is not reliable.
     * Use {@link #getTotalWorkspacePadding()} instead.
     */
    public Rect getWorkspacePadding(Rect recycle) {
        Rect padding = recycle == null ? new Rect() : recycle;
        if (isVerticalBarLayout()) {
            if (mInsets.left > 0) {
                padding.set(mInsets.left + pageIndicatorLandGutterLeftNavBarPx, 0,
                        hotseatBarHeightPx + hotseatLandGutterPx - mInsets.left, 2 * edgeMarginPx);
            } else {
                padding.set(pageIndicatorLandGutterRightNavBarPx, 0,
                        hotseatBarHeightPx + hotseatLandGutterPx, 2 * edgeMarginPx);
            }
        } else {
            int paddingBottom = hotseatBarHeightPx + pageIndicatorHeightPx;
            if (isTablet) {
                // Pad the left and right of the workspace to ensure consistent spacing
                // between all icons
                float gapScale = 1f + (dragViewScale - 1f) / 2f;
                int width = getCurrentWidth();
                int height = getCurrentHeight();
                // The amount of screen space available for left/right padding.
                int availablePaddingX = Math.max(0, width - (int) ((inv.numColumns * cellWidthPx) +
                        ((inv.numColumns - 1) * gapScale * cellWidthPx)));
                availablePaddingX = (int) Math.min(availablePaddingX,
                            width * MAX_HORIZONTAL_PADDING_PERCENT);
                int availablePaddingY = Math.max(0, height - topWorkspacePadding - paddingBottom
                        - (int) (2 * inv.numRows * cellHeightPx));
                padding.set(availablePaddingX / 2, topWorkspacePadding + availablePaddingY / 2,
                        availablePaddingX / 2, paddingBottom + availablePaddingY / 2);
            } else {
                // Pad the top and bottom of the workspace with search/hotseat bar sizes
                padding.set(desiredWorkspaceLeftRightMarginPx,
                        topWorkspacePadding,
                        desiredWorkspaceLeftRightMarginPx,
                        paddingBottom);
            }
        }
        return padding;
    }

    /**
     * @return the bounds for which the open folders should be contained within
     */
    public Rect getAbsoluteOpenFolderBounds() {
        if (isVerticalBarLayout()) {
            // Folders should only appear right of the drop target bar and left of the hotseat
            return new Rect(mInsets.left + dropTargetBarSizePx + edgeMarginPx,
                    mInsets.top,
                    mInsets.left + availableWidthPx - hotseatBarHeightPx - edgeMarginPx,
                    mInsets.top + availableHeightPx);
        } else {
            // Folders should only appear below the drop target bar and above the hotseat
            return new Rect(mInsets.left,
                    mInsets.top + dropTargetBarSizePx + edgeMarginPx,
                    mInsets.left + availableWidthPx,
                    mInsets.top + availableHeightPx - hotseatBarHeightPx - pageIndicatorHeightPx -
                            edgeMarginPx);
        }
    }

    private int getWorkspacePageSpacing() {
        if (isVerticalBarLayout() || isLargeTablet) {
            // In landscape mode the page spacing is set to the default.
            return defaultPageSpacingPx;
        } else {
            // In portrait, we want the pages spaced such that there is no
            // overhang of the previous / next page into the current page viewport.
            // We assume symmetrical padding in portrait mode.
            return Math.max(defaultPageSpacingPx, getWorkspacePadding(null).left + 1);
        }
    }

    int getOverviewModeButtonBarHeight() {
        int zoneHeight = (int) (overviewModeIconZoneRatio * availableHeightPx);
        zoneHeight = Math.min(overviewModeMaxIconZoneHeightPx,
                Math.max(overviewModeMinIconZoneHeightPx, zoneHeight));
        return zoneHeight;
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

    boolean shouldFadeAdjacentWorkspaceScreens() {
        return isVerticalBarLayout() || isLargeTablet;
    }

    private int getVisibleChildCount(ViewGroup parent) {
        int visibleChildren = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChildAt(i).getVisibility() != View.GONE) {
                visibleChildren++;
            }
        }
        return visibleChildren;
    }

    public void layout(Launcher launcher, boolean notifyListeners) {
        FrameLayout.LayoutParams lp;
        boolean hasVerticalBarLayout = isVerticalBarLayout();
        final boolean isLayoutRtl = Utilities.isRtl(launcher.getResources());

        // Layout the search bar space
        Point searchBarBounds = getSearchBarDimensForWidgetOpts();
        View searchBar = launcher.getDropTargetBar();
        lp = (FrameLayout.LayoutParams) searchBar.getLayoutParams();
        lp.width = searchBarBounds.x;
        lp.height = searchBarBounds.y;
        lp.topMargin = mInsets.top + edgeMarginPx;
        searchBar.setLayoutParams(lp);

        // Layout the workspace
        PagedView workspace = (PagedView) launcher.findViewById(R.id.workspace);
        Rect workspacePadding = getWorkspacePadding(null);
        workspace.setPadding(workspacePadding.left, workspacePadding.top, workspacePadding.right,
                workspacePadding.bottom);
        workspace.setPageSpacing(getWorkspacePageSpacing());

        View qsbContainer = launcher.getQsbContainer();
        lp = (FrameLayout.LayoutParams) qsbContainer.getLayoutParams();
        lp.topMargin = mInsets.top + workspacePadding.top;
        qsbContainer.setLayoutParams(lp);

        // Layout the hotseat
        Hotseat hotseat = (Hotseat) launcher.findViewById(R.id.hotseat);
        lp = (FrameLayout.LayoutParams) hotseat.getLayoutParams();
        // We want the edges of the hotseat to line up with the edges of the workspace, but the
        // icons in the hotseat are a different size, and so don't line up perfectly. To account for
        // this, we pad the left and right of the hotseat with half of the difference of a workspace
        // cell vs a hotseat cell.
        float workspaceCellWidth = (float) getCurrentWidth() / inv.numColumns;
        float hotseatCellWidth = (float) getCurrentWidth() / inv.numHotseatIcons;
        int hotseatAdjustment = Math.round((workspaceCellWidth - hotseatCellWidth) / 2);
        if (hasVerticalBarLayout) {
            // Vertical hotseat -- The hotseat is fixed in the layout to be on the right of the
            //                     screen regardless of RTL
            lp.gravity = Gravity.RIGHT;
            lp.width = hotseatBarHeightPx + mInsets.left + mInsets.right;
            lp.height = LayoutParams.MATCH_PARENT;
            hotseat.getLayout().setPadding(mInsets.left, mInsets.top, mInsets.right,
                    workspacePadding.bottom);
        } else if (isTablet) {
            // Pad the hotseat with the workspace padding calculated above
            lp.gravity = Gravity.BOTTOM;
            lp.width = LayoutParams.MATCH_PARENT;
            lp.height = hotseatBarHeightPx + mInsets.bottom;
            hotseat.getLayout().setPadding(hotseatAdjustment + workspacePadding.left,
                    hotseatBarTopPaddingPx, hotseatAdjustment + workspacePadding.right,
                    mInsets.bottom);
        } else {
            // For phones, layout the hotseat without any bottom margin
            // to ensure that we have space for the folders
            if (mInsets.bottom < hotseatBarTopPaddingPx) {
                hotseatBarTopPaddingPx = (mInsets.bottom + hotseatBarTopPaddingPx) / 2;
                mInsets.bottom = hotseatBarTopPaddingPx;
            }

            lp.gravity = Gravity.BOTTOM;
            lp.width = LayoutParams.MATCH_PARENT;
            lp.height = hotseatBarHeightPx + mInsets.bottom;
            hotseat.getLayout().setPadding(hotseatAdjustment + workspacePadding.left,
                    hotseatBarTopPaddingPx, hotseatAdjustment + workspacePadding.right,
                    mInsets.bottom);
        }
        hotseat.setLayoutParams(lp);

        // Layout the page indicators
        View pageIndicator = launcher.findViewById(R.id.page_indicator);
        if (pageIndicator != null) {
            lp = (FrameLayout.LayoutParams) pageIndicator.getLayoutParams();
            if (isVerticalBarLayout()) {
                if (mInsets.left > 0) {
                    lp.leftMargin = mInsets.left + pageIndicatorLandGutterLeftNavBarPx -
                            lp.width - pageIndicatorLandWorkspaceOffsetPx;
                } else if (mInsets.right > 0) {
                    lp.leftMargin = pageIndicatorLandGutterRightNavBarPx - lp.width -
                            pageIndicatorLandWorkspaceOffsetPx;
                }
                lp.bottomMargin = workspacePadding.bottom;
            } else {
                // Put the page indicators above the hotseat
                lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                lp.height = pageIndicatorHeightPx;
                lp.bottomMargin = hotseatBarHeightPx + mInsets.bottom;
            }
            pageIndicator.setLayoutParams(lp);
        }

        // Layout the Overview Mode
        ViewGroup overviewMode = launcher.getOverviewPanel();
        if (overviewMode != null) {
            lp = (FrameLayout.LayoutParams) overviewMode.getLayoutParams();
            lp.gravity = Gravity.LEFT | Gravity.BOTTOM;

            int visibleChildCount = getVisibleChildCount(overviewMode);
            int totalItemWidth = visibleChildCount * overviewModeBarItemWidthPx;
            int maxWidth = totalItemWidth + (visibleChildCount-1) * overviewModeBarSpacerWidthPx;

            lp.width = Math.min(availableWidthPx, maxWidth);
            lp.height = getOverviewModeButtonBarHeight();
            // Center the overview buttons on the workspace page
            lp.leftMargin = workspacePadding.left + (availableWidthPx -
                    workspacePadding.left - workspacePadding.right - lp.width) / 2;
            overviewMode.setLayoutParams(lp);
        }

        if (notifyListeners) {
            for (int i = mListeners.size() - 1; i >= 0; i--) {
                mListeners.get(i).onLauncherLayoutChanged();
            }
        }
    }

    private int getCurrentWidth() {
        return isLandscape
                ? Math.max(widthPx, heightPx)
                : Math.min(widthPx, heightPx);
    }

    private int getCurrentHeight() {
        return isLandscape
                ? Math.min(widthPx, heightPx)
                : Math.max(widthPx, heightPx);
    }


    /**
     * @return the left/right paddings for all containers.
     */
    public final int[] getContainerPadding(Context context) {
        Resources res = context.getResources();

        // No paddings for portrait phone
        if (isPhone && !isVerticalBarLayout()) {
            return new int[] {0, 0};
        }

        // In landscape, we match the width of the workspace
        int padding = (pageIndicatorLandGutterRightNavBarPx +
                hotseatBarHeightPx + hotseatLandGutterPx + mInsets.left) / 2;
        return new int[]{ padding, padding };
    }
}
