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

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Paint.FontMetrics;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;


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

class DeviceProfile {
    String name;
    float minWidthDps;
    float minHeightDps;
    float numRows;
    float numColumns;
    float iconSize;
    float iconTextSize;
    float numHotseatIcons;
    float hotseatIconSize;

    boolean isLandscape;
    boolean isTablet;
    boolean isLargeTablet;
    boolean transposeLayoutWithOrientation;

    int edgeMarginPx;

    int widthPx;
    int heightPx;
    int availableWidthPx;
    int availableHeightPx;
    int iconSizePx;
    int iconTextSizePx;
    int cellWidthPx;
    int cellHeightPx;
    int folderBackgroundOffset;
    int folderIconSizePx;
    int folderCellWidthPx;
    int folderCellHeightPx;
    int hotseatCellWidthPx;
    int hotseatCellHeightPx;
    int hotseatIconSizePx;
    int hotseatBarHeightPx;
    int searchBarSpaceWidthPx;
    int searchBarSpaceMaxWidthPx;
    int searchBarSpaceHeightPx;
    int searchBarHeightPx;
    int pageIndicatorHeightPx;

    DeviceProfile(String n, float w, float h, float r, float c,
                  float is, float its, float hs, float his) {
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

    DeviceProfile(ArrayList<DeviceProfile> profiles,
                  float minWidth, float minHeight,
                  int wPx, int hPx,
                  int awPx, int ahPx,
                  Resources resources) {
        DisplayMetrics dm = resources.getDisplayMetrics();
        ArrayList<DeviceProfileQuery> points =
                new ArrayList<DeviceProfileQuery>();
        transposeLayoutWithOrientation =
                resources.getBoolean(R.bool.hotseat_transpose_layout_with_orientation);
        minWidthDps = minWidth;
        minHeightDps = minHeight;

        edgeMarginPx = resources.getDimensionPixelSize(R.dimen.dynamic_grid_edge_margin);
        pageIndicatorHeightPx = resources.getDimensionPixelSize(R.dimen.dynamic_grid_page_indicator_height);

        // Interpolate the rows
        for (DeviceProfile p : profiles) {
            points.add(new DeviceProfileQuery(p.minWidthDps, p.minHeightDps, p.numRows));
        }
        numRows = Math.round(invDistWeightedInterpolate(minWidth, minHeight, points));
        // Interpolate the columns
        points.clear();
        for (DeviceProfile p : profiles) {
            points.add(new DeviceProfileQuery(p.minWidthDps, p.minHeightDps, p.numColumns));
        }
        numColumns = Math.round(invDistWeightedInterpolate(minWidth, minHeight, points));
        // Interpolate the icon size
        points.clear();
        for (DeviceProfile p : profiles) {
            points.add(new DeviceProfileQuery(p.minWidthDps, p.minHeightDps, p.iconSize));
        }
        iconSize = invDistWeightedInterpolate(minWidth, minHeight, points);
        iconSizePx = DynamicGrid.pxFromDp(iconSize, dm);

        // Interpolate the icon text size
        points.clear();
        for (DeviceProfile p : profiles) {
            points.add(new DeviceProfileQuery(p.minWidthDps, p.minHeightDps, p.iconTextSize));
        }
        iconTextSize = invDistWeightedInterpolate(minWidth, minHeight, points);
        iconTextSizePx = DynamicGrid.pxFromSp(iconTextSize, dm);

        // Interpolate the hotseat size
        points.clear();
        for (DeviceProfile p : profiles) {
            points.add(new DeviceProfileQuery(p.minWidthDps, p.minHeightDps, p.numHotseatIcons));
        }
        numHotseatIcons = Math.round(invDistWeightedInterpolate(minWidth, minHeight, points));
        // Interpolate the hotseat icon size
        points.clear();
        for (DeviceProfile p : profiles) {
            points.add(new DeviceProfileQuery(p.minWidthDps, p.minHeightDps, p.hotseatIconSize));
        }
        // Hotseat
        hotseatIconSize = invDistWeightedInterpolate(minWidth, minHeight, points);
        hotseatIconSizePx = DynamicGrid.pxFromDp(hotseatIconSize, dm);

        // Calculate other vars based on Configuration
        updateFromConfiguration(resources, wPx, hPx, awPx, ahPx);

        // Search Bar
        searchBarSpaceMaxWidthPx = resources.getDimensionPixelSize(R.dimen.dynamic_grid_search_bar_max_width);
        searchBarHeightPx = resources.getDimensionPixelSize(R.dimen.dynamic_grid_search_bar_height);
        searchBarSpaceWidthPx = Math.min(searchBarSpaceMaxWidthPx, widthPx);
        searchBarSpaceHeightPx = searchBarHeightPx + 2 * edgeMarginPx;

        // Calculate the actual text height
        Paint textPaint = new Paint();
        textPaint.setTextSize(iconTextSizePx);
        FontMetrics fm = textPaint.getFontMetrics();
        cellWidthPx = iconSizePx;
        cellHeightPx = iconSizePx + (int) Math.ceil(fm.bottom - fm.top);

        // At this point, if the cells do not fit into the available height, then we need
        // to shrink the icon size
        /*
        Rect padding = getWorkspacePadding(isLandscape ?
                CellLayout.LANDSCAPE : CellLayout.PORTRAIT);
        int h = (int) (numRows * cellHeightPx) + padding.top + padding.bottom;
        if (h > availableHeightPx) {
            float delta = h - availableHeightPx;
            int deltaPx = (int) Math.ceil(delta / numRows);
            iconSizePx -= deltaPx;
            iconSize = DynamicGrid.dpiFromPx(iconSizePx, dm);
            cellWidthPx = iconSizePx;
            cellHeightPx = iconSizePx + (int) Math.ceil(fm.bottom - fm.top);
        }
        */

        // Hotseat
        hotseatBarHeightPx = iconSizePx + 4 * edgeMarginPx;
        hotseatCellWidthPx = iconSizePx;
        hotseatCellHeightPx = iconSizePx;

        // Folder
        folderCellWidthPx = cellWidthPx + 3 * edgeMarginPx;
        folderCellHeightPx = cellHeightPx + edgeMarginPx;
        folderBackgroundOffset = -edgeMarginPx;
        folderIconSizePx = iconSizePx + 2 * -folderBackgroundOffset;
    }

    void updateFromConfiguration(Resources resources, int wPx, int hPx,
                                 int awPx, int ahPx) {
        DisplayMetrics dm = resources.getDisplayMetrics();
        isLandscape = (resources.getConfiguration().orientation ==
                Configuration.ORIENTATION_LANDSCAPE);
        isTablet = resources.getBoolean(R.bool.is_tablet);
        isLargeTablet = resources.getBoolean(R.bool.is_large_tablet);
        widthPx = wPx;
        heightPx = hPx;
        availableWidthPx = awPx;
        availableHeightPx = ahPx;
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

    Rect getWorkspacePadding(int orientation) {
        Rect padding = new Rect();
        if (orientation == CellLayout.LANDSCAPE &&
                transposeLayoutWithOrientation) {
            // Pad the left and right of the workspace with search/hotseat bar sizes
            padding.set(searchBarSpaceHeightPx, edgeMarginPx,
                    hotseatBarHeightPx, edgeMarginPx);
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
                padding.set(edgeMarginPx + gap,
                        searchBarSpaceHeightPx,
                        edgeMarginPx + gap,
                        hotseatBarHeightPx + pageIndicatorHeightPx);
            } else {
                // Pad the top and bottom of the workspace with search/hotseat bar sizes
                padding.set(edgeMarginPx,
                        searchBarSpaceHeightPx,
                        edgeMarginPx,
                        hotseatBarHeightPx + pageIndicatorHeightPx);
            }
        }
        return padding;
    }

    int calculateCellWidth(int width, int countX) {
        return width / countX;
    }
    int calculateCellHeight(int height, int countY) {
        return height / countY;
    }

    boolean isTablet() {
        return isTablet;
    }

    boolean isLargeTablet() {
        return isLargeTablet;
    }

    public void layout(Launcher launcher) {
        FrameLayout.LayoutParams lp;
        Resources res = launcher.getResources();
        boolean hasVerticalBarLayout = isLandscape &&
                res.getBoolean(R.bool.hotseat_transpose_layout_with_orientation);

        // Layout the search bar space
        View searchBarSpace = launcher.findViewById(R.id.qsb_bar);
        lp = (FrameLayout.LayoutParams) searchBarSpace.getLayoutParams();
        if (hasVerticalBarLayout) {
            // Vertical search bar
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.width = searchBarSpaceHeightPx;
            lp.height = LayoutParams.MATCH_PARENT;
            searchBarSpace.setPadding(
                    0, 2 * edgeMarginPx, 0,
                    2 * edgeMarginPx);
        } else {
            // Horizontal search bar
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.width = searchBarSpaceWidthPx;
            lp.height = searchBarSpaceHeightPx;
            searchBarSpace.setPadding(
                    2 * edgeMarginPx,
                    2 * edgeMarginPx,
                    2 * edgeMarginPx, 0);
        }
        searchBarSpace.setLayoutParams(lp);

        // Layout the search bar
        View searchBar = launcher.getQsbBar();
        lp = (FrameLayout.LayoutParams) searchBar.getLayoutParams();
        lp.width = LayoutParams.MATCH_PARENT;
        lp.height = LayoutParams.MATCH_PARENT;
        searchBar.setLayoutParams(lp);

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
        View workspace = launcher.findViewById(R.id.workspace);
        lp = (FrameLayout.LayoutParams) workspace.getLayoutParams();
        lp.gravity = Gravity.CENTER;
        Rect padding = getWorkspacePadding(isLandscape
                ? CellLayout.LANDSCAPE
                : CellLayout.PORTRAIT);
        workspace.setPadding(padding.left, padding.top,
                padding.right, padding.bottom);
        workspace.setLayoutParams(lp);

        // Layout the hotseat
        View hotseat = launcher.findViewById(R.id.hotseat);
        lp = (FrameLayout.LayoutParams) hotseat.getLayoutParams();
        if (hasVerticalBarLayout) {
            // Vertical hotseat
            lp.gravity = Gravity.RIGHT;
            lp.width = hotseatBarHeightPx;
            lp.height = LayoutParams.MATCH_PARENT;
            hotseat.setPadding(0, 2 * edgeMarginPx,
                    2 * edgeMarginPx, 2 * edgeMarginPx);
        } else if (isTablet()) {
            // Pad the hotseat with the grid gap calculated above
            int gridGap = (int) ((widthPx - 2 * edgeMarginPx -
                    (numColumns * cellWidthPx)) / (2 * (numColumns + 1)));
            int gridWidth = (int) ((numColumns * cellWidthPx) +
                    ((numColumns - 1) * gridGap));
            int hotseatGap = (int) Math.max(0,
                    (gridWidth - (numHotseatIcons * hotseatCellWidthPx))
                            / (numHotseatIcons - 1));
            lp.gravity = Gravity.BOTTOM;
            lp.width = LayoutParams.MATCH_PARENT;
            lp.height = hotseatBarHeightPx;
            hotseat.setPadding(2 * edgeMarginPx + gridGap + hotseatGap, 0,
                    2 * edgeMarginPx + gridGap + hotseatGap,
                    2 * edgeMarginPx);
        } else {
            // For phones, layout the hotseat without any bottom margin
            // to ensure that we have space for the folders
            lp.gravity = Gravity.BOTTOM;
            lp.width = LayoutParams.MATCH_PARENT;
            lp.height = hotseatBarHeightPx;
            hotseat.setPadding(2 * edgeMarginPx, 0,
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
                lp.height = pageIndicatorHeightPx;
                lp.bottomMargin = hotseatBarHeightPx;
                pageIndicator.setLayoutParams(lp);
            }
        }
    }
}

public class DynamicGrid {
    @SuppressWarnings("unused")
    private static final String TAG = "DynamicGrid";

    private DeviceProfile mProfile;
    private float mMinWidth;
    private float mMinHeight;

    public static float dpiFromPx(int size, DisplayMetrics metrics){
        float densityRatio = (float) metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT;
        return (size / densityRatio);
    }
    public static int pxFromDp(float size, DisplayMetrics metrics) {
        return (int) Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                size, metrics));
    }
    public static int pxFromSp(float size, DisplayMetrics metrics) {
        return (int) Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                size, metrics));
    }

    public DynamicGrid(Resources resources, int minWidthPx, int minHeightPx,
                       int widthPx, int heightPx,
                       int awPx, int ahPx) {
        DisplayMetrics dm = resources.getDisplayMetrics();
        ArrayList<DeviceProfile> deviceProfiles =
                new ArrayList<DeviceProfile>();
        // Our phone profiles include the bar sizes in each orientation
        deviceProfiles.add(new DeviceProfile("Super Short Stubby",
                255, 300,  2, 3,  48, 12,  4, 48));
        deviceProfiles.add(new DeviceProfile("Shorter Stubby",
                255, 400,  3, 3,  48, 12,  4, 48));
        deviceProfiles.add(new DeviceProfile("Short Stubby",
                275, 420,  3, 4,  48, 12,  4, 48));
        deviceProfiles.add(new DeviceProfile("Stubby",
                255, 450,  3, 4,  48, 12,  4, 48));
        deviceProfiles.add(new DeviceProfile("Nexus S",
                296, 491.33f,  4, 4,  48, 12,  4, 48));
        deviceProfiles.add(new DeviceProfile("Nexus 4",
                359, 518,  4, 4,  60, 12,  5, 56));
        // The tablet profile is odd in that the landscape orientation
        // also includes the nav bar on the side
        deviceProfiles.add(new DeviceProfile("Nexus 7",
                575, 904,  6, 6,  72, 14.4f,  7, 60));
        // Larger tablet profiles always have system bars on the top & bottom
        deviceProfiles.add(new DeviceProfile("Nexus 10",
                727, 1207,  5, 8,  80, 14.4f,  9, 64));
        /*
        deviceProfiles.add(new DeviceProfile("Nexus 7",
                600, 960,  5, 5,  72, 14.4f,  5, 60));
        deviceProfiles.add(new DeviceProfile("Nexus 10",
                800, 1280,  5, 5,  80, 14.4f,  6, 64));
         */
        deviceProfiles.add(new DeviceProfile("20-inch Tablet",
                1527, 2527,  7, 7,  100, 20,  7, 72));
        mMinWidth = dpiFromPx(minWidthPx, dm);
        mMinHeight = dpiFromPx(minHeightPx, dm);
        mProfile = new DeviceProfile(deviceProfiles,
                mMinWidth, mMinHeight,
                widthPx, heightPx,
                awPx, ahPx,
                resources);
    }

    DeviceProfile getDeviceProfile() {
        return mProfile;
    }

    public String toString() {
        return "-------- DYNAMIC GRID ------- \n" +
                "Wd: " + mProfile.minWidthDps + ", Hd: " + mProfile.minHeightDps +
                ", W: " + mProfile.widthPx + ", H: " + mProfile.heightPx +
                " [r: " + mProfile.numRows + ", c: " + mProfile.numColumns +
                ", is: " + mProfile.iconSizePx + ", its: " + mProfile.iconTextSize +
                ", cw: " + mProfile.cellWidthPx + ", ch: " + mProfile.cellHeightPx +
                ", hc: " + mProfile.numHotseatIcons + ", his: " + mProfile.hotseatIconSizePx + "]";
    }
}
