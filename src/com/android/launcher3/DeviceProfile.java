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
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import com.android.launcher3.settings.SettingsProvider;

class DeviceProfileQuery {
    float widthDps;
    float heightDps;
    float value;
    PointF dimens;

    DeviceProfileQuery(float w, float h, float v) {
        widthDps = w;
        heightDps = h;
        value = v;
        dimens = new PointF(w, h);
    }
}

public class DeviceProfile {
    public static interface DeviceProfileCallbacks {
        public void onAvailableSizeChanged(DeviceProfile grid);
    }

    public final static int GRID_SIZE_MAX = 3;
    public final static int GRID_SIZE_MIN = 2;

    public enum GridSize {
        Comfortable(0),
        Cozy(1),
        Condensed(2),
        Custom(3);

        private final int mValue;
        private GridSize(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }

        public static GridSize getModeForValue(int value) {
            switch (value) {
                case 1:
                    return Cozy;
                case 2:
                    return Condensed;
                case 3:
                    return Custom;
                default :
                    return Comfortable;
            }
        }
    }

    String name;
    float minWidthDps;
    float minHeightDps;
    float numRows;
    float numColumns;
    int numRowsBase;
    int numColumnsBase;
    float numHotseatIcons;
    private float iconSize;
    private float iconTextSize;
    private int iconDrawablePaddingOriginalPx;
    private float hotseatIconSize;

    boolean isLandscape;
    boolean isTablet;
    boolean isLargeTablet;
    boolean isLayoutRtl;
    boolean transposeLayoutWithOrientation;

    int desiredWorkspaceLeftRightMarginPx;
    int edgeMarginPx;
    Rect defaultWidgetPadding;

    int widthPx;
    int heightPx;
    int availableWidthPx;
    int availableHeightPx;
    int defaultPageSpacingPx;

    int overviewModeMinIconZoneHeightPx;
    int overviewModeMaxIconZoneHeightPx;
    int overviewModeBarItemWidthPx;
    int overviewModeBarSpacerWidthPx;
    float overviewModeIconZoneRatio;
    float overviewModeScaleFactor;

    int iconSizePx;
    int iconTextSizePx;
    int iconDrawablePaddingPx;
    int cellWidthPx;
    int cellHeightPx;
    int allAppsIconSizePx;
    int allAppsIconTextSizePx;
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
    int hotseatAllAppsRank;
    int allAppsNumRows;
    int allAppsNumCols;
    int searchBarSpaceWidthPx;
    int searchBarSpaceMaxWidthPx;
    int searchBarSpaceHeightPx;
    int searchBarHeightPx;
    int pageIndicatorHeightPx;


    boolean searchBarVisible;

    float dragViewScale;

    private ArrayList<DeviceProfileCallbacks> mCallbacks = new ArrayList<DeviceProfileCallbacks>();

    DeviceProfile(String n, float w, float h, float r, float c,
                  float is, float its, float hs, float his) {
        // Ensure that we have an odd number of hotseat items (since we need to place all apps)
        if (!LauncherAppState.isDisableAllApps() && hs % 2 == 0) {
            throw new RuntimeException("All Device Profiles must have an odd number of hotseat spaces");
        }

        name = n;
        minWidthDps = w;
        minHeightDps = h;
        numRows = r;
        numColumns = c;
        iconSize = is;
        iconTextSize = its;
        numHotseatIcons = hs;
        hotseatIconSize = his;
    }

    DeviceProfile(Context context,
                  ArrayList<DeviceProfile> profiles,
                  float minWidth, float minHeight,
                  int wPx, int hPx,
                  int awPx, int ahPx,
                  Resources res) {
        DisplayMetrics dm = res.getDisplayMetrics();
        ArrayList<DeviceProfileQuery> points =
                new ArrayList<DeviceProfileQuery>();
        transposeLayoutWithOrientation =
                res.getBoolean(R.bool.hotseat_transpose_layout_with_orientation);
        minWidthDps = minWidth;
        minHeightDps = minHeight;

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

        // Interpolate the rows
        for (DeviceProfile p : profiles) {
            points.add(new DeviceProfileQuery(p.minWidthDps, p.minHeightDps, p.numRows));
        }
        numRows = Math.round(invDistWeightedInterpolate(minWidth, minHeight, points));
        numRowsBase = (int) numRows;
        int gridResize = SettingsProvider.getIntCustomDefault(context,
                SettingsProvider.SETTINGS_UI_DYNAMIC_GRID_SIZE, 0);
        if (GridSize.getModeForValue(gridResize) != GridSize.Custom) {
            numRows += gridResize;
        } else {
            int iTempNumberOfRows = SettingsProvider.getIntCustomDefault(context,
                    SettingsProvider.SETTINGS_UI_HOMESCREEN_ROWS, (int)numRows);
            if (iTempNumberOfRows > 0) {
                numRows = iTempNumberOfRows;
            }
        }
        // Interpolate the columns
        points.clear();
        for (DeviceProfile p : profiles) {
            points.add(new DeviceProfileQuery(p.minWidthDps, p.minHeightDps, p.numColumns));
        }
        numColumns = Math.round(invDistWeightedInterpolate(minWidth, minHeight, points));
        numColumnsBase = (int) numColumns;
        if (GridSize.getModeForValue(gridResize) != GridSize.Custom) {
            numColumns += gridResize;
        } else {
            int iTempNumberOfColumns = SettingsProvider.getIntCustomDefault(context,
                    SettingsProvider.SETTINGS_UI_HOMESCREEN_COLUMNS, (int)numColumns);
            if (iTempNumberOfColumns > 0) {
                numColumns = iTempNumberOfColumns;
            }
        }
        // Interpolate the hotseat length
        points.clear();
        for (DeviceProfile p : profiles) {
            points.add(new DeviceProfileQuery(p.minWidthDps, p.minHeightDps, p.numHotseatIcons));
        }
        numHotseatIcons = Math.round(invDistWeightedInterpolate(minWidth, minHeight, points));
        hotseatAllAppsRank = (int) (numHotseatIcons / 2);

        // Interpolate the icon size
        points.clear();
        for (DeviceProfile p : profiles) {
            points.add(new DeviceProfileQuery(p.minWidthDps, p.minHeightDps, p.iconSize));
        }
        iconSize = invDistWeightedInterpolate(minWidth, minHeight, points);
        // AllApps uses the original non-scaled icon size
        allAppsIconSizePx = DynamicGrid.pxFromDp(iconSize, dm);

        // Interpolate the icon text size
        points.clear();
        for (DeviceProfile p : profiles) {
            points.add(new DeviceProfileQuery(p.minWidthDps, p.minHeightDps, p.iconTextSize));
        }
        iconTextSize = invDistWeightedInterpolate(minWidth, minHeight, points);
        iconDrawablePaddingOriginalPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_icon_drawable_padding);
        // AllApps uses the original non-scaled icon text size
        allAppsIconTextSizePx = DynamicGrid.pxFromDp(iconTextSize, dm);

        // Interpolate the hotseat icon size
        points.clear();
        for (DeviceProfile p : profiles) {
            points.add(new DeviceProfileQuery(p.minWidthDps, p.minHeightDps, p.hotseatIconSize));
        }
        // Hotseat
        hotseatIconSize = invDistWeightedInterpolate(minWidth, minHeight, points);

        // Calculate the remaining vars
        updateFromConfiguration(context, res, wPx, hPx, awPx, ahPx);
        updateAvailableDimensions(context);

        // Search Bar
        searchBarVisible = SettingsProvider.getBoolean(context, SettingsProvider.SETTINGS_UI_HOMESCREEN_SEARCH,
                R.bool.preferences_interface_homescreen_search_default);
        searchBarSpaceWidthPx = Math.min(searchBarSpaceMaxWidthPx, widthPx);
        searchBarSpaceHeightPx = 2 * edgeMarginPx + (searchBarVisible ? searchBarHeightPx  : 2 * edgeMarginPx);
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
        float usedHeight = (cellHeightPx * numRows);

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

    private void updateIconSize(float scale, int drawablePadding, Resources resources,
                                DisplayMetrics dm) {
        iconSizePx = (int) (DynamicGrid.pxFromDp(iconSize, dm) * scale);
        iconTextSizePx = (int) (DynamicGrid.pxFromSp(iconTextSize, dm) * scale);
        iconDrawablePaddingPx = drawablePadding;
        hotseatIconSizePx = (int) (DynamicGrid.pxFromDp(hotseatIconSize, dm) * scale);

        // Search Bar
        searchBarSpaceWidthPx = Math.min(searchBarSpaceMaxWidthPx, widthPx);
        searchBarSpaceMaxWidthPx = resources.getDimensionPixelSize(R.dimen.dynamic_grid_search_bar_max_width);
        searchBarHeightPx = resources.getDimensionPixelSize(R.dimen.dynamic_grid_search_bar_height);
        searchBarSpaceHeightPx = searchBarHeightPx + getSearchBarTopOffset();

        // Calculate the actual text height
        Paint textPaint = new Paint();
        textPaint.setTextSize(iconTextSizePx);
        FontMetrics fm = textPaint.getFontMetrics();
        cellWidthPx = iconSizePx;
        cellHeightPx = iconSizePx + iconDrawablePaddingPx + (int) Math.ceil(fm.bottom - fm.top);
        final float scaleDps = resources.getDimensionPixelSize(R.dimen.dragViewScale);
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
        Rect padding = getWorkspacePadding(isLandscape ?
                CellLayout.LANDSCAPE : CellLayout.PORTRAIT);
        int pageIndicatorOffset =
                resources.getDimensionPixelSize(R.dimen.apps_customize_page_indicator_offset);

        if (isPhone()) {
            searchBarSpaceWidthPx = Math.min(searchBarSpaceMaxWidthPx, widthPx);
        } else {
            searchBarSpaceWidthPx = widthPx - (isLandscape ? 3 : 1) * iconSizePx;
        }

        allAppsCellWidthPx = allAppsIconSizePx;
        allAppsCellHeightPx = allAppsIconSizePx + drawablePadding + iconTextSizePx;
        int maxLongEdgeCellCount =
                resources.getInteger(R.integer.config_dynamic_grid_max_long_edge_cell_count);
        int maxShortEdgeCellCount =
                resources.getInteger(R.integer.config_dynamic_grid_max_short_edge_cell_count);
        int minEdgeCellCount =
                resources.getInteger(R.integer.config_dynamic_grid_min_edge_cell_count);
        int maxRows = (isLandscape ? maxShortEdgeCellCount : maxLongEdgeCellCount);
        int maxCols = (isLandscape ? maxLongEdgeCellCount : maxShortEdgeCellCount);

        allAppsNumRows = (availableHeightPx - pageIndicatorHeightPx) /
                (allAppsCellHeightPx + allAppsCellPaddingPx);
        allAppsNumRows = Math.max(minEdgeCellCount, Math.min(maxRows, allAppsNumRows));
        allAppsNumCols = (availableWidthPx) /
                (allAppsCellWidthPx + allAppsCellPaddingPx);
        allAppsNumCols = Math.max(minEdgeCellCount, Math.min(maxCols, allAppsNumCols));
    }

    void updateFromConfiguration(Context context, Resources resources, int wPx, int hPx,
                                 int awPx, int ahPx) {
        Configuration configuration = resources.getConfiguration();
        isLandscape = (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE);
        isTablet = resources.getBoolean(R.bool.is_tablet);
        isLargeTablet = resources.getBoolean(R.bool.is_large_tablet);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            isLayoutRtl = (configuration.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
        } else {
            isLayoutRtl = false;
        }
        widthPx = wPx;
        heightPx = hPx;
        availableWidthPx = awPx;
        availableHeightPx = ahPx;

        updateAvailableDimensions(context);
    }

    private float dist(PointF p0, PointF p1) {
        return (float) Math.sqrt((p1.x - p0.x)*(p1.x-p0.x) +
                (p1.y-p0.y)*(p1.y-p0.y));
    }

    private float weight(PointF a, PointF b,
                        float pow) {
        float d = dist(a, b);
        if (d == 0f) {
            return Float.POSITIVE_INFINITY;
        }
        return (float) (1f / Math.pow(d, pow));
    }

    private float invDistWeightedInterpolate(float width, float height,
                ArrayList<DeviceProfileQuery> points) {
        float sum = 0;
        float weights = 0;
        float pow = 5;
        float kNearestNeighbors = 3;
        final PointF xy = new PointF(width, height);

        ArrayList<DeviceProfileQuery> pointsByNearness = points;
        Collections.sort(pointsByNearness, new Comparator<DeviceProfileQuery>() {
            public int compare(DeviceProfileQuery a, DeviceProfileQuery b) {
                return (int) (dist(xy, a.dimens) - dist(xy, b.dimens));
            }
        });

        for (int i = 0; i < pointsByNearness.size(); ++i) {
            DeviceProfileQuery p = pointsByNearness.get(i);
            if (i < kNearestNeighbors) {
                float w = weight(xy, p.dimens, pow);
                if (w == Float.POSITIVE_INFINITY) {
                    return p.value;
                }
                weights += w;
            }
        }

        for (int i = 0; i < pointsByNearness.size(); ++i) {
            DeviceProfileQuery p = pointsByNearness.get(i);
            if (i < kNearestNeighbors) {
                float w = weight(xy, p.dimens, pow);
                sum += w * p.value / weights;
            }
        }

        return sum;
    }

    /** Returns the search bar top offset */
    int getSearchBarTopOffset() {
        if (isTablet() && !isVerticalBarLayout()) {
            return searchBarVisible ? 4 * edgeMarginPx : 0;
        } else {
            return searchBarVisible ? 2 * edgeMarginPx : 0;
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
                        (numColumns * cellWidthPx)) / (2 * (numColumns + 1)));
                bounds.set(edgeMarginPx + gap, getSearchBarTopOffset(),
                        availableWidthPx - (edgeMarginPx + gap),
                        searchBarVisible ? searchBarSpaceHeightPx : edgeMarginPx);
            } else {
                bounds.set(desiredWorkspaceLeftRightMarginPx - defaultWidgetPadding.left,
                        getSearchBarTopOffset(),
                        availableWidthPx - (desiredWorkspaceLeftRightMarginPx -
                        defaultWidgetPadding.right), searchBarVisible ? searchBarSpaceHeightPx : edgeMarginPx);
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
                int availableWidth = Math.max(0, width - (int) ((numColumns * cellWidthPx) +
                        (numColumns * gapScale * cellWidthPx)));
                int availableHeight = Math.max(0, height - paddingTop - paddingBottom
                        - (int) (2 * numRows * cellHeightPx));
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

    int calculateCellWidth(int width, int countX) {
        return width / countX;
    }
    int calculateCellHeight(int height, int countY) {
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

    int calculateOverviewModeWidth(int visibleChildCount) {
        return visibleChildCount * overviewModeBarItemWidthPx +
                (visibleChildCount-1) * overviewModeBarSpacerWidthPx;
    }

    public void layout(Launcher launcher) {
        // Update search bar for live settings
        searchBarVisible = SettingsProvider.getBoolean(launcher, SettingsProvider.SETTINGS_UI_HOMESCREEN_SEARCH,
                R.bool.preferences_interface_homescreen_search_default);
        searchBarSpaceHeightPx = 2 * edgeMarginPx + (searchBarVisible ? searchBarHeightPx : 2 * edgeMarginPx);
        FrameLayout.LayoutParams lp;
        Resources res = launcher.getResources();
        boolean hasVerticalBarLayout = isVerticalBarLayout();

        // Layout the search bar space
        View searchBar = launcher.getSearchBar();
        lp = (FrameLayout.LayoutParams) searchBar.getLayoutParams();
        if (hasVerticalBarLayout) {
            // Vertical search bar space
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.width = searchBarSpaceHeightPx;
            lp.height = LayoutParams.WRAP_CONTENT;
            searchBar.setPadding(
                    0, 2 * edgeMarginPx, 0,
                    2 * edgeMarginPx);

            searchBar.setVisibility(searchBarVisible ? View.VISIBLE : View.GONE);
            LinearLayout targets = (LinearLayout) searchBar.findViewById(R.id.drag_target_bar);
            targets.setOrientation(LinearLayout.VERTICAL);
        } else {
            // Horizontal search bar space
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.width = searchBarSpaceWidthPx;
            lp.height = searchBarSpaceHeightPx;
            searchBar.setPadding(
                    2 * edgeMarginPx,
                    getSearchBarTopOffset(),
                    2 * edgeMarginPx, 0);
        }
        searchBar.setLayoutParams(lp);

        // Layout the drop target icons
        LinearLayout dropTargetBar = (LinearLayout) launcher.getSearchBar().getDropTargetBar();
        if (hasVerticalBarLayout) {
            dropTargetBar.setOrientation(LinearLayout.VERTICAL);
        } else {
            dropTargetBar.setOrientation(LinearLayout.HORIZONTAL);
        }

        // Layout the search bar
        View qsbBar = launcher.getQsbBar();
        qsbBar.setVisibility(searchBarVisible ? View.VISIBLE : View.GONE);
        LayoutParams vglp = qsbBar.getLayoutParams();
        vglp.width = LayoutParams.MATCH_PARENT;
        vglp.height = LayoutParams.MATCH_PARENT;
        qsbBar.setLayoutParams(vglp);

        // Layout the voice proxy
        View voiceButtonProxy = launcher.findViewById(R.id.voice_button_proxy);
        if (voiceButtonProxy != null) {
            if (hasVerticalBarLayout) {
                // TODO: MOVE THIS INTO SEARCH BAR MEASURE
            } else {
                lp = (FrameLayout.LayoutParams) voiceButtonProxy.getLayoutParams();
                lp.gravity = Gravity.TOP | Gravity.END;
                lp.width = (widthPx - searchBarSpaceWidthPx) / 2 +
                        2 * iconSizePx;
                lp.height = searchBarSpaceHeightPx;
            }
        }

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
                lp.bottomMargin = Math.max(hotseatBarHeightPx, lp.bottomMargin);
                pageIndicator.setLayoutParams(lp);
            }
        }

        // Layout the apps customize
        View appsCustomize = launcher.findViewById(R.id.apps_customize_pane_content);
        lp = (FrameLayout.LayoutParams) appsCustomize.getLayoutParams();
        lp.gravity = Gravity.CENTER;
        appsCustomize.setLayoutParams(lp);

        // Layout AllApps
        AppsCustomizeLayout host = (AppsCustomizeLayout)
                launcher.findViewById(R.id.apps_customize_pane);
        if (host != null) {
            // Center the all apps page indicator
            int pageIndicatorHeight = (int) (pageIndicatorHeightPx * Math.min(1f,
                    (allAppsIconSizePx / DynamicGrid.DEFAULT_ICON_SIZE_PX)));
            pageIndicator = host.findViewById(R.id.apps_customize_page_indicator);
            if (pageIndicator != null) {
                lp = (FrameLayout.LayoutParams) pageIndicator.getLayoutParams();
                lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                lp.width = LayoutParams.WRAP_CONTENT;
                lp.height = pageIndicatorHeight;
                pageIndicator.setLayoutParams(lp);
            }

            AppsCustomizePagedView pagedView = (AppsCustomizePagedView)
                    host.findViewById(R.id.apps_customize_pane_content);
            padding = new Rect();
            if (pagedView != null) {
                // Constrain the dimensions of all apps so that it does not span the full width
                int paddingLR = (availableWidthPx - (allAppsCellWidthPx * allAppsNumCols)) /
                        (2 * (allAppsNumCols + 1));
                int paddingTB = (availableHeightPx - (allAppsCellHeightPx * allAppsNumRows)) /
                        (2 * (allAppsNumRows + 1));
                paddingLR = Math.min(paddingLR, (int)((paddingLR + paddingTB) * 0.75f));
                paddingTB = Math.min(paddingTB, (int)((paddingLR + paddingTB) * 0.75f));
                int maxAllAppsWidth = (allAppsNumCols * (allAppsCellWidthPx + 2 * paddingLR));
                int gridPaddingLR = (availableWidthPx - maxAllAppsWidth) / 2;
                // Only adjust the side paddings on landscape phones, or tablets
                if ((isTablet() || isLandscape) && gridPaddingLR > (allAppsCellWidthPx / 4)) {
                    padding.left = padding.right = gridPaddingLR;
                }
                // The icons are centered, so we can't just offset by the page indicator height
                // because the empty space will actually be pageIndicatorHeight + paddingTB
                padding.bottom = Math.max(0, pageIndicatorHeight - paddingTB);
                pagedView.setAllAppsPadding(padding);
                pagedView.setWidgetsPageIndicatorPadding(pageIndicatorHeight);
            }
        }

        // Layout the Overview Mode
//        ViewGroup overviewMode = launcher.getOverviewPanel();
//        if (overviewMode != null) {
//            Rect r = getOverviewModeButtonBarRect();
//            lp = (FrameLayout.LayoutParams) overviewMode.getLayoutParams();
//            lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
//            lp.width = Math.min(availableWidthPx,
//                    calculateOverviewModeWidth(getVisibleChildCount(overviewMode)));
//            lp.height = r.height();
//            overviewMode.setLayoutParams(lp);
//        }
    }
}
