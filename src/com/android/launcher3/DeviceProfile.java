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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.android.launcher3.util.Thunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class DeviceProfile {

    public static interface DeviceProfileCallbacks {
        public void onAvailableSizeChanged(DeviceProfile grid);
    }

    public final InvariantDeviceProfile inv;
    private int iconDrawablePaddingOriginalPx;

    boolean isLandscape;
    public boolean isTablet;
    public boolean isLargeTablet;
    public boolean isLayoutRtl;

    boolean transposeLayoutWithOrientation;
    int desiredWorkspaceLeftRightMarginPx;
    public int edgeMarginPx;
    Rect defaultWidgetPadding;

    int widthPx;
    int heightPx;
    public int availableWidthPx;
    public int availableHeightPx;
    int defaultPageSpacingPx;

    int overviewModeMinIconZoneHeightPx;
    int overviewModeMaxIconZoneHeightPx;
    int overviewModeBarItemWidthPx;
    int overviewModeBarSpacerWidthPx;
    float overviewModeIconZoneRatio;
    float overviewModeScaleFactor;

    public int cellWidthPx;
    public int cellHeightPx;

    int iconDrawablePaddingPx;
    int allAppsCellWidthPx;
    int allAppsCellHeightPx;
    int allAppsCellPaddingPx;
    int folderBackgroundOffset;
    int folderIconSizePx;
    int folderCellWidthPx;
    int folderCellHeightPx;
    int hotseatCellWidthPx;
    int hotseatCellHeightPx;
    int hotseatIconSizePx;
    int hotseatBarHeightPx;
    int allAppsNumRows;
    int allAppsNumCols;
    int appsViewNumCols;
    int appsViewNumPredictiveCols;
    int searchBarSpaceWidthPx;
    int searchBarSpaceHeightPx;
    int pageIndicatorHeightPx;
    int allAppsButtonVisualSize;

    int iconSizePx;
    public int iconTextSizePx;
    int allAppsIconSizePx;
    int allAppsIconTextSizePx;

    float dragViewScale;

    int allAppsShortEdgeCount = -1;
    int allAppsLongEdgeCount = -1;

    private ArrayList<DeviceProfileCallbacks> mCallbacks = new ArrayList<DeviceProfileCallbacks>();

    public DeviceProfile(Context context, InvariantDeviceProfile inv) {
        // Determine the dynamic grid properties
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();

        Point realSize = new Point();
        display.getRealSize(realSize);
        DisplayMetrics dm = new DisplayMetrics();
        display.getMetrics(dm);

        this.inv = inv;
        init(context, realSize.x, realSize.y, dm.widthPixels, dm.heightPixels);
    }

    private void init(Context context, int wPx, int hPx, int awPx, int ahPx) {
        Resources res = context.getResources();
        DisplayMetrics dm = res.getDisplayMetrics();

        transposeLayoutWithOrientation =
                res.getBoolean(R.bool.hotseat_transpose_layout_with_orientation);

        ComponentName cn = new ComponentName(context.getPackageName(),
                this.getClass().getName());
        defaultWidgetPadding = AppWidgetHostView.getDefaultPaddingForWidget(context, cn, null);
        edgeMarginPx = res.getDimensionPixelSize(R.dimen.dynamic_grid_edge_margin);
        desiredWorkspaceLeftRightMarginPx = 2 * edgeMarginPx;
        pageIndicatorHeightPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_page_indicator_height);
        defaultPageSpacingPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_workspace_page_spacing);
        allAppsCellPaddingPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_all_apps_cell_padding);
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
        overviewModeScaleFactor =
                res.getInteger(R.integer.config_dynamic_grid_overview_scale_percentage) / 100f;

        iconDrawablePaddingOriginalPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_icon_drawable_padding);

        // AllApps uses the original non-scaled icon text size
        allAppsIconTextSizePx = Utilities.pxFromDp(inv.iconTextSize, dm);

        // AllApps uses the original non-scaled icon size
        allAppsIconSizePx = Utilities.pxFromDp(inv.iconSize, dm);

        // If the partner customization apk contains any grid overrides, apply them
        applyPartnerDeviceProfileOverrides(context, dm);

        // Calculate the remaining vars
        updateFromConfiguration(context, res, wPx, hPx, awPx, ahPx);
        updateAvailableDimensions(context);
        computeAllAppsButtonSize(context);
    }

    /**
     * Apply any Partner customization grid overrides.
     *
     * Currently we support: all apps row / column count.
     */
    private void applyPartnerDeviceProfileOverrides(Context ctx, DisplayMetrics dm) {
        Partner p = Partner.get(ctx.getPackageManager());
        if (p != null) {
            p.applyDeviceProfileOverrides(this);
        }
    }

    /**
     * Determine the exact visual footprint of the all apps button, taking into account scaling
     * and internal padding of the drawable.
     */
    private void computeAllAppsButtonSize(Context context) {
        Resources res = context.getResources();
        float padding = res.getInteger(R.integer.config_allAppsButtonPaddingPercent) / 100f;
        LauncherAppState app = LauncherAppState.getInstance();
        allAppsButtonVisualSize = (int) (hotseatIconSizePx * (1 - padding));
    }

    void addCallback(DeviceProfileCallbacks cb) {
        mCallbacks.add(cb);
        cb.onAvailableSizeChanged(this);
    }
    void removeCallback(DeviceProfileCallbacks cb) {
        mCallbacks.remove(cb);
    }

    private int getDeviceOrientation(Context context) {
        WindowManager windowManager =  (WindowManager)
                context.getSystemService(Context.WINDOW_SERVICE);
        Resources resources = context.getResources();
        DisplayMetrics dm = resources.getDisplayMetrics();
        Configuration config = resources.getConfiguration();
        int rotation = windowManager.getDefaultDisplay().getRotation();

        boolean isLandscape = (config.orientation == Configuration.ORIENTATION_LANDSCAPE) &&
                (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180);
        boolean isRotatedPortrait = (config.orientation == Configuration.ORIENTATION_PORTRAIT) &&
                (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270);
        if (isLandscape || isRotatedPortrait) {
            return CellLayout.LANDSCAPE;
        } else {
            return CellLayout.PORTRAIT;
        }
    }

    private void updateAvailableDimensions(Context context) {
        WindowManager windowManager =  (WindowManager)
                context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        Resources resources = context.getResources();
        DisplayMetrics dm = resources.getDisplayMetrics();
        Configuration config = resources.getConfiguration();

        // There are three possible configurations that the dynamic grid accounts for, portrait,
        // landscape with the nav bar at the bottom, and landscape with the nav bar at the side.
        // To prevent waiting for fitSystemWindows(), we make the observation that in landscape,
        // the height is the smallest height (either with the nav bar at the bottom or to the
        // side) and otherwise, the height is simply the largest possible height for a portrait
        // device.
        Point size = new Point();
        Point smallestSize = new Point();
        Point largestSize = new Point();
        display.getSize(size);
        display.getCurrentSizeRange(smallestSize, largestSize);
        availableWidthPx = size.x;
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            availableHeightPx = smallestSize.y;
        } else {
            availableHeightPx = largestSize.y;
        }

        // Check to see if the icons fit in the new available height.  If not, then we need to
        // shrink the icon size.
        float scale = 1f;
        int drawablePadding = iconDrawablePaddingOriginalPx;
        updateIconSize(1f, drawablePadding, resources, dm);
        float usedHeight = (cellHeightPx * inv.numRows);

        Rect workspacePadding = getWorkspacePadding();
        int maxHeight = (availableHeightPx - workspacePadding.top - workspacePadding.bottom);
        if (usedHeight > maxHeight) {
            scale = maxHeight / usedHeight;
            drawablePadding = 0;
        }
        updateIconSize(scale, drawablePadding, resources, dm);

        // Make the callbacks
        for (DeviceProfileCallbacks cb : mCallbacks) {
            cb.onAvailableSizeChanged(this);
        }
    }

    private void updateIconSize(float scale, int drawablePadding, Resources res,
                                DisplayMetrics dm) {
        iconSizePx = (int) (Utilities.pxFromDp(inv.iconSize, dm) * scale);
        iconTextSizePx = (int) (Utilities.pxFromSp(inv.iconTextSize, dm) * scale);
        iconDrawablePaddingPx = drawablePadding;
        hotseatIconSizePx = (int) (Utilities.pxFromDp(inv.hotseatIconSize, dm) * scale);

        // Search Bar
        searchBarSpaceWidthPx = Math.min(widthPx,
                res.getDimensionPixelSize(R.dimen.dynamic_grid_search_bar_max_width));
        searchBarSpaceHeightPx = getSearchBarTopOffset()
                + res.getDimensionPixelSize(R.dimen.dynamic_grid_search_bar_height);

        // Calculate the actual text height
        Paint textPaint = new Paint();
        textPaint.setTextSize(iconTextSizePx);
        FontMetrics fm = textPaint.getFontMetrics();
        cellWidthPx = iconSizePx;
        cellHeightPx = iconSizePx + iconDrawablePaddingPx + (int) Math.ceil(fm.bottom - fm.top);
        final float scaleDps = res.getDimensionPixelSize(R.dimen.dragViewScale);
        dragViewScale = (iconSizePx + scaleDps) / iconSizePx;

        // Hotseat
        hotseatBarHeightPx = iconSizePx + 4 * edgeMarginPx;
        hotseatCellWidthPx = iconSizePx;
        hotseatCellHeightPx = iconSizePx;

        // Folder
        folderCellWidthPx = cellWidthPx + 3 * edgeMarginPx;
        folderCellHeightPx = cellHeightPx + edgeMarginPx;
        folderBackgroundOffset = -edgeMarginPx;
        folderIconSizePx = iconSizePx + 2 * -folderBackgroundOffset;

        // All Apps
        allAppsCellWidthPx = allAppsIconSizePx;
        allAppsCellHeightPx = allAppsIconSizePx + drawablePadding + allAppsIconTextSizePx;
        int maxLongEdgeCellCount =
                res.getInteger(R.integer.config_dynamic_grid_max_long_edge_cell_count);
        int maxShortEdgeCellCount =
                res.getInteger(R.integer.config_dynamic_grid_max_short_edge_cell_count);
        int minEdgeCellCount =
                res.getInteger(R.integer.config_dynamic_grid_min_edge_cell_count);
        int maxRows = (isLandscape ? maxShortEdgeCellCount : maxLongEdgeCellCount);
        int maxCols = (isLandscape ? maxLongEdgeCellCount : maxShortEdgeCellCount);

        if (allAppsShortEdgeCount > 0 && allAppsLongEdgeCount > 0) {
            allAppsNumRows = isLandscape ? allAppsShortEdgeCount : allAppsLongEdgeCount;
            allAppsNumCols = isLandscape ? allAppsLongEdgeCount : allAppsShortEdgeCount;
        } else {
            allAppsNumRows = (availableHeightPx - pageIndicatorHeightPx) /
                    (allAppsCellHeightPx + allAppsCellPaddingPx);
            allAppsNumRows = Math.max(minEdgeCellCount, Math.min(maxRows, allAppsNumRows));
            allAppsNumCols = (availableWidthPx) / (allAppsCellWidthPx + allAppsCellPaddingPx);
            allAppsNumCols = Math.max(minEdgeCellCount, Math.min(maxCols, allAppsNumCols));
        }

        int appsContainerViewWidthPx = res.getDimensionPixelSize(R.dimen.apps_container_width);
        updateAppsViewNumCols(res, appsContainerViewWidthPx);
    }

    public boolean updateAppsViewNumCols(Resources res, int containerWidth) {
        int appsViewLeftMarginPx =
                res.getDimensionPixelSize(R.dimen.apps_grid_view_start_margin);
        int availableAppsWidthPx = (containerWidth > 0) ? containerWidth : availableWidthPx;
        int numAppsCols = (availableAppsWidthPx - appsViewLeftMarginPx) /
                (allAppsCellWidthPx + 2 * allAppsCellPaddingPx);
        int numPredictiveAppCols = isPhone() ? 4 : numAppsCols;
        if ((numAppsCols != appsViewNumCols) ||
                (numPredictiveAppCols != appsViewNumPredictiveCols)) {
            appsViewNumCols = numAppsCols;
            appsViewNumPredictiveCols = numPredictiveAppCols;
            return true;
        }
        return false;
    }

    void updateFromConfiguration(Context context, Resources resources, int wPx, int hPx,
                                 int awPx, int ahPx) {
        Configuration configuration = resources.getConfiguration();
        isLandscape = (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE);
        isTablet = resources.getBoolean(R.bool.is_tablet);
        isLargeTablet = resources.getBoolean(R.bool.is_large_tablet);
        isLayoutRtl = Utilities.isRtl(resources);
        widthPx = wPx;
        heightPx = hPx;
        availableWidthPx = awPx;
        availableHeightPx = ahPx;

        updateAvailableDimensions(context);
    }

    /** Returns the search bar top offset */
    int getSearchBarTopOffset() {
        if (isTablet() && !isVerticalBarLayout()) {
            return 4 * edgeMarginPx;
        } else {
            return 2 * edgeMarginPx;
        }
    }

    /** Returns the search bar bounds in the current orientation */
    Rect getSearchBarBounds() {
        return getSearchBarBounds(isLandscape ? CellLayout.LANDSCAPE : CellLayout.PORTRAIT);
    }
    /** Returns the search bar bounds in the specified orientation */
    Rect getSearchBarBounds(int orientation) {
        Rect bounds = new Rect();
        if (orientation == CellLayout.LANDSCAPE &&
                transposeLayoutWithOrientation) {
            if (isLayoutRtl) {
                bounds.set(availableWidthPx - searchBarSpaceHeightPx, edgeMarginPx,
                        availableWidthPx, availableHeightPx - edgeMarginPx);
            } else {
                bounds.set(0, edgeMarginPx, searchBarSpaceHeightPx,
                        availableHeightPx - edgeMarginPx);
            }
        } else {
            if (isTablet()) {
                // Pad the left and right of the workspace to ensure consistent spacing
                // between all icons
                int width = (orientation == CellLayout.LANDSCAPE)
                        ? Math.max(widthPx, heightPx)
                        : Math.min(widthPx, heightPx);
                // XXX: If the icon size changes across orientations, we will have to take
                //      that into account here too.
                int gap = (int) ((width - 2 * edgeMarginPx -
                        (inv.numColumns * cellWidthPx)) / (2 * (inv.numColumns + 1)));
                bounds.set(edgeMarginPx + gap, getSearchBarTopOffset(),
                        availableWidthPx - (edgeMarginPx + gap),
                        searchBarSpaceHeightPx);
            } else {
                bounds.set(desiredWorkspaceLeftRightMarginPx - defaultWidgetPadding.left,
                        getSearchBarTopOffset(),
                        availableWidthPx - (desiredWorkspaceLeftRightMarginPx -
                        defaultWidgetPadding.right), searchBarSpaceHeightPx);
            }
        }
        return bounds;
    }

    /** Returns the bounds of the workspace page indicators. */
    Rect getWorkspacePageIndicatorBounds(Rect insets) {
        Rect workspacePadding = getWorkspacePadding();
        if (isLandscape && transposeLayoutWithOrientation) {
            if (isLayoutRtl) {
                return new Rect(workspacePadding.left, workspacePadding.top,
                        workspacePadding.left + pageIndicatorHeightPx,
                        heightPx - workspacePadding.bottom - insets.bottom);
            } else {
                int pageIndicatorLeft = widthPx - workspacePadding.right;
                return new Rect(pageIndicatorLeft, workspacePadding.top,
                        pageIndicatorLeft + pageIndicatorHeightPx,
                        heightPx - workspacePadding.bottom - insets.bottom);
            }
        } else {
            int pageIndicatorTop = heightPx - insets.bottom - workspacePadding.bottom;
            return new Rect(workspacePadding.left, pageIndicatorTop,
                    widthPx - workspacePadding.right, pageIndicatorTop + pageIndicatorHeightPx);
        }
    }

    public int getWorkspaceGridHeight() {
        Rect p = getWorkspacePadding();
        return availableHeightPx - p.top - p.bottom;
    }

    public int getWorkspaceGridWidth() {
        Rect p = getWorkspacePadding();
        return availableWidthPx - p.left - p.right;
    }

    /** Returns the workspace padding in the specified orientation */
    Rect getWorkspacePadding() {
        return getWorkspacePadding(isLandscape ? CellLayout.LANDSCAPE : CellLayout.PORTRAIT);
    }

    Rect getWorkspacePadding(int orientation) {
        Rect searchBarBounds = getSearchBarBounds(orientation);
        Rect padding = new Rect();
        if (orientation == CellLayout.LANDSCAPE &&
                transposeLayoutWithOrientation) {
            // Pad the left and right of the workspace with search/hotseat bar sizes
            if (isLayoutRtl) {
                padding.set(hotseatBarHeightPx, edgeMarginPx,
                        searchBarBounds.width(), edgeMarginPx);
            } else {
                padding.set(searchBarBounds.width(), edgeMarginPx,
                        hotseatBarHeightPx, edgeMarginPx);
            }
        } else {
            if (isTablet()) {
                // Pad the left and right of the workspace to ensure consistent spacing
                // between all icons
                float gapScale = 1f + (dragViewScale - 1f) / 2f;
                int width = (orientation == CellLayout.LANDSCAPE)
                        ? Math.max(widthPx, heightPx)
                        : Math.min(widthPx, heightPx);
                int height = (orientation != CellLayout.LANDSCAPE)
                        ? Math.max(widthPx, heightPx)
                        : Math.min(widthPx, heightPx);
                int paddingTop = searchBarBounds.bottom;
                int paddingBottom = hotseatBarHeightPx + pageIndicatorHeightPx;
                int availableWidth = Math.max(0, width - (int) ((inv.numColumns * cellWidthPx) +
                        (inv.numColumns * gapScale * cellWidthPx)));
                int availableHeight = Math.max(0, height - paddingTop - paddingBottom
                        - (int) (2 * inv.numRows * cellHeightPx));
                padding.set(availableWidth / 2, paddingTop + availableHeight / 2,
                        availableWidth / 2, paddingBottom + availableHeight / 2);
            } else {
                // Pad the top and bottom of the workspace with search/hotseat bar sizes
                padding.set(desiredWorkspaceLeftRightMarginPx - defaultWidgetPadding.left,
                        searchBarBounds.bottom,
                        desiredWorkspaceLeftRightMarginPx - defaultWidgetPadding.right,
                        hotseatBarHeightPx + pageIndicatorHeightPx);
            }
        }
        return padding;
    }

    int getWorkspacePageSpacing(int orientation) {
        if ((orientation == CellLayout.LANDSCAPE &&
                transposeLayoutWithOrientation) || isLargeTablet()) {
            // In landscape mode the page spacing is set to the default.
            return defaultPageSpacingPx;
        } else {
            // In portrait, we want the pages spaced such that there is no
            // overhang of the previous / next page into the current page viewport.
            // We assume symmetrical padding in portrait mode.
            return Math.max(defaultPageSpacingPx, 2 * getWorkspacePadding().left);
        }
    }

    Rect getOverviewModeButtonBarRect() {
        int zoneHeight = (int) (overviewModeIconZoneRatio * availableHeightPx);
        zoneHeight = Math.min(overviewModeMaxIconZoneHeightPx,
                Math.max(overviewModeMinIconZoneHeightPx, zoneHeight));
        return new Rect(0, availableHeightPx - zoneHeight, 0, availableHeightPx);
    }

    float getOverviewModeScale() {
        Rect workspacePadding = getWorkspacePadding();
        Rect overviewBar = getOverviewModeButtonBarRect();
        int pageSpace = availableHeightPx - workspacePadding.top - workspacePadding.bottom;
        return (overviewModeScaleFactor * (pageSpace - overviewBar.height())) / pageSpace;
    }

    // The rect returned will be extended to below the system ui that covers the workspace
    Rect getHotseatRect() {
        if (isVerticalBarLayout()) {
            return new Rect(availableWidthPx - hotseatBarHeightPx, 0,
                    Integer.MAX_VALUE, availableHeightPx);
        } else {
            return new Rect(0, availableHeightPx - hotseatBarHeightPx,
                    availableWidthPx, Integer.MAX_VALUE);
        }
    }

    public static int calculateCellWidth(int width, int countX) {
        return width / countX;
    }
    public static int calculateCellHeight(int height, int countY) {
        return height / countY;
    }

    boolean isPhone() {
        return !isTablet && !isLargeTablet;
    }
    boolean isTablet() {
        return isTablet;
    }
    boolean isLargeTablet() {
        return isLargeTablet;
    }

    /**
     * When {@code true}, hotseat is on the bottom row when in landscape mode.
     * If {@code false}, hotseat is on the right column when in landscape mode.
     */
    boolean isVerticalBarLayout() {
        return isLandscape && transposeLayoutWithOrientation;
    }

    boolean shouldFadeAdjacentWorkspaceScreens() {
        return isVerticalBarLayout() || isLargeTablet();
    }

    int getVisibleChildCount(ViewGroup parent) {
        int visibleChildren = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChildAt(i).getVisibility() != View.GONE) {
                visibleChildren++;
            }
        }
        return visibleChildren;
    }

    public void layout(Launcher launcher) {
        FrameLayout.LayoutParams lp;
        boolean hasVerticalBarLayout = isVerticalBarLayout();

        // Layout the search bar space
        View searchBar = launcher.getSearchBar();
        lp = (FrameLayout.LayoutParams) searchBar.getLayoutParams();
        if (hasVerticalBarLayout) {
            // Vertical search bar space
            lp.gravity = Gravity.LEFT;
            lp.width = searchBarSpaceHeightPx;

            LinearLayout targets = (LinearLayout) searchBar.findViewById(R.id.drag_target_bar);
            targets.setOrientation(LinearLayout.VERTICAL);
            FrameLayout.LayoutParams targetsLp = (FrameLayout.LayoutParams) targets.getLayoutParams();
            targetsLp.gravity = Gravity.TOP;
            targetsLp.height = LayoutParams.WRAP_CONTENT;

        } else {
            // Horizontal search bar space
            lp.gravity = Gravity.TOP;
            lp.height = searchBarSpaceHeightPx;

            LinearLayout targets = (LinearLayout) searchBar.findViewById(R.id.drag_target_bar);
            targets.getLayoutParams().width = searchBarSpaceWidthPx;
        }
        searchBar.setLayoutParams(lp);

        // Layout the workspace
        PagedView workspace = (PagedView) launcher.findViewById(R.id.workspace);
        lp = (FrameLayout.LayoutParams) workspace.getLayoutParams();
        lp.gravity = Gravity.CENTER;
        int orientation = isLandscape ? CellLayout.LANDSCAPE : CellLayout.PORTRAIT;
        Rect padding = getWorkspacePadding(orientation);
        workspace.setLayoutParams(lp);
        workspace.setPadding(padding.left, padding.top, padding.right, padding.bottom);
        workspace.setPageSpacing(getWorkspacePageSpacing(orientation));

        // Layout the hotseat
        View hotseat = launcher.findViewById(R.id.hotseat);
        lp = (FrameLayout.LayoutParams) hotseat.getLayoutParams();
        if (hasVerticalBarLayout) {
            // Vertical hotseat
            lp.gravity = Gravity.END;
            lp.width = hotseatBarHeightPx;
            lp.height = LayoutParams.MATCH_PARENT;
            hotseat.findViewById(R.id.layout).setPadding(0, 2 * edgeMarginPx, 0, 2 * edgeMarginPx);
        } else if (isTablet()) {
            // Pad the hotseat with the workspace padding calculated above
            lp.gravity = Gravity.BOTTOM;
            lp.width = LayoutParams.MATCH_PARENT;
            lp.height = hotseatBarHeightPx;
            hotseat.setPadding(edgeMarginPx + padding.left, 0,
                    edgeMarginPx + padding.right,
                    2 * edgeMarginPx);
        } else {
            // For phones, layout the hotseat without any bottom margin
            // to ensure that we have space for the folders
            lp.gravity = Gravity.BOTTOM;
            lp.width = LayoutParams.MATCH_PARENT;
            lp.height = hotseatBarHeightPx;
            hotseat.findViewById(R.id.layout).setPadding(2 * edgeMarginPx, 0,
                    2 * edgeMarginPx, 0);
        }
        hotseat.setLayoutParams(lp);

        // Layout the page indicators
        View pageIndicator = launcher.findViewById(R.id.page_indicator);
        if (pageIndicator != null) {
            if (hasVerticalBarLayout) {
                // Hide the page indicators when we have vertical search/hotseat
                pageIndicator.setVisibility(View.GONE);
            } else {
                // Put the page indicators above the hotseat
                lp = (FrameLayout.LayoutParams) pageIndicator.getLayoutParams();
                lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                lp.width = LayoutParams.WRAP_CONTENT;
                lp.height = LayoutParams.WRAP_CONTENT;
                lp.bottomMargin = hotseatBarHeightPx;
                pageIndicator.setLayoutParams(lp);
            }
        }

        // Layout the Overview Mode
        ViewGroup overviewMode = launcher.getOverviewPanel();
        if (overviewMode != null) {
            Rect r = getOverviewModeButtonBarRect();
            lp = (FrameLayout.LayoutParams) overviewMode.getLayoutParams();
            lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;

            int visibleChildCount = getVisibleChildCount(overviewMode);
            int totalItemWidth = visibleChildCount * overviewModeBarItemWidthPx;
            int maxWidth = totalItemWidth + (visibleChildCount-1) * overviewModeBarSpacerWidthPx;

            lp.width = Math.min(availableWidthPx, maxWidth);
            lp.height = r.height();
            overviewMode.setLayoutParams(lp);

            if (lp.width > totalItemWidth && visibleChildCount > 1) {
                // We have enough space. Lets add some margin too.
                int margin = (lp.width - totalItemWidth) / (visibleChildCount-1);
                View lastChild = null;

                // Set margin of all visible children except the last visible child
                for (int i = 0; i < visibleChildCount; i++) {
                    if (lastChild != null) {
                        MarginLayoutParams clp = (MarginLayoutParams) lastChild.getLayoutParams();
                        if (isLayoutRtl) {
                            clp.leftMargin = margin;
                        } else {
                            clp.rightMargin = margin;
                        }
                        lastChild.setLayoutParams(clp);
                        lastChild = null;
                    }
                    View thisChild = overviewMode.getChildAt(i);
                    if (thisChild.getVisibility() != View.GONE) {
                        lastChild = thisChild;
                    }
                }
            }
        }
    }
}
