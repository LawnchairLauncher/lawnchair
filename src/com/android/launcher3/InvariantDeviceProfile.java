/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import com.android.launcher3.util.Thunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class InvariantDeviceProfile {

    // This is a static that we use for the default icon size on a 4/5-inch phone
    private static float DEFAULT_ICON_SIZE_DP = 60;

    private static final float ICON_SIZE_DEFINED_IN_APP_DP = 48;

    // Constants that affects the interpolation curve between statically defined device profile
    // buckets.
    private static float KNEARESTNEIGHBOR = 3;
    private static float WEIGHT_POWER = 5;

    // used to offset float not being able to express extremely small weights in extreme cases.
    private static float WEIGHT_EFFICIENT = 100000f;

    // Profile-defining invariant properties
    String name;
    float minWidthDps;
    float minHeightDps;

    /**
     * Number of icons per row and column in the workspace.
     */
    public int numRows;
    public int numColumns;

    /**
     * The minimum number of predicted apps in all apps.
     */
    int minAllAppsPredictionColumns;

    /**
     * Number of icons per row and column in the folder.
     */
    public int numFolderRows;
    public int numFolderColumns;
    public float iconSize;
    public int iconBitmapSize;
    public int fillResIconDpi;
    public float iconTextSize;

    /**
     * Number of icons inside the hotseat area.
     */
    public int numHotseatIcons;
    float hotseatIconSize;
    int defaultLayoutId;

    // Derived invariant properties
    public int hotseatAllAppsRank;

    DeviceProfile landscapeProfile;
    DeviceProfile portraitProfile;

    public InvariantDeviceProfile() {
    }

    public InvariantDeviceProfile(InvariantDeviceProfile p) {
        this(p.name, p.minWidthDps, p.minHeightDps, p.numRows, p.numColumns,
                p.numFolderRows, p.numFolderColumns, p.minAllAppsPredictionColumns,
                p.iconSize, p.iconTextSize, p.numHotseatIcons, p.hotseatIconSize,
                p.defaultLayoutId);
    }

    InvariantDeviceProfile(String n, float w, float h, int r, int c, int fr, int fc, int maapc,
            float is, float its, int hs, float his, int dlId) {
        // Ensure that we have an odd number of hotseat items (since we need to place all apps)
        if (hs % 2 == 0) {
            throw new RuntimeException("All Device Profiles must have an odd number of hotseat spaces");
        }

        name = n;
        minWidthDps = w;
        minHeightDps = h;
        numRows = r;
        numColumns = c;
        numFolderRows = fr;
        numFolderColumns = fc;
        minAllAppsPredictionColumns = maapc;
        iconSize = is;
        iconTextSize = its;
        numHotseatIcons = hs;
        hotseatIconSize = his;
        defaultLayoutId = dlId;
    }

    @TargetApi(23)
    InvariantDeviceProfile(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics dm = new DisplayMetrics();
        display.getMetrics(dm);

        Point smallestSize = new Point();
        Point largestSize = new Point();
        display.getCurrentSizeRange(smallestSize, largestSize);

        // This guarantees that width < height
        minWidthDps = Utilities.dpiFromPx(Math.min(smallestSize.x, smallestSize.y), dm);
        minHeightDps = Utilities.dpiFromPx(Math.min(largestSize.x, largestSize.y), dm);

        ArrayList<InvariantDeviceProfile> closestProfiles =
                findClosestDeviceProfiles(minWidthDps, minHeightDps, getPredefinedDeviceProfiles());
        InvariantDeviceProfile interpolatedDeviceProfileOut =
                invDistWeightedInterpolate(minWidthDps,  minHeightDps, closestProfiles);

        InvariantDeviceProfile closestProfile = closestProfiles.get(0);
        numRows = closestProfile.numRows;
        numColumns = closestProfile.numColumns;
        numHotseatIcons = closestProfile.numHotseatIcons;
        hotseatAllAppsRank = (int) (numHotseatIcons / 2);
        defaultLayoutId = closestProfile.defaultLayoutId;
        numFolderRows = closestProfile.numFolderRows;
        numFolderColumns = closestProfile.numFolderColumns;
        minAllAppsPredictionColumns = closestProfile.minAllAppsPredictionColumns;

        iconSize = interpolatedDeviceProfileOut.iconSize;
        iconBitmapSize = Utilities.pxFromDp(iconSize, dm);
        iconTextSize = interpolatedDeviceProfileOut.iconTextSize;
        hotseatIconSize = interpolatedDeviceProfileOut.hotseatIconSize;
        fillResIconDpi = getLauncherIconDensity(iconBitmapSize);

        // If the partner customization apk contains any grid overrides, apply them
        // Supported overrides: numRows, numColumns, iconSize
        applyPartnerDeviceProfileOverrides(context, dm);

        Point realSize = new Point();
        display.getRealSize(realSize);
        // The real size never changes. smallSide and largeSide will remain the
        // same in any orientation.
        int smallSide = Math.min(realSize.x, realSize.y);
        int largeSide = Math.max(realSize.x, realSize.y);

        landscapeProfile = new DeviceProfile(context, this, smallestSize, largestSize,
                largeSide, smallSide, true /* isLandscape */);
        portraitProfile = new DeviceProfile(context, this, smallestSize, largestSize,
                smallSide, largeSide, false /* isLandscape */);
    }

    ArrayList<InvariantDeviceProfile> getPredefinedDeviceProfiles() {
        ArrayList<InvariantDeviceProfile> predefinedDeviceProfiles = new ArrayList<>();
        // width, height, #rows, #columns, #folder rows, #folder columns,
        // iconSize, iconTextSize, #hotseat, #hotseatIconSize, defaultLayoutId.
        predefinedDeviceProfiles.add(new InvariantDeviceProfile("Super Short Stubby",
                255, 300,     2, 3, 2, 3, 3, 48, 13, 3, 48, R.xml.default_workspace_3x3));
        predefinedDeviceProfiles.add(new InvariantDeviceProfile("Shorter Stubby",
                255, 400,     3, 3, 3, 3, 3, 48, 13, 3, 48, R.xml.default_workspace_3x3));
        predefinedDeviceProfiles.add(new InvariantDeviceProfile("Short Stubby",
                275, 420,     3, 4, 3, 4, 4, 48, 13, 5, 48, R.xml.default_workspace_4x4));
        predefinedDeviceProfiles.add(new InvariantDeviceProfile("Stubby",
                255, 450,     3, 4, 3, 4, 4, 48, 13, 5, 48, R.xml.default_workspace_4x4));
        predefinedDeviceProfiles.add(new InvariantDeviceProfile("Nexus S",
                296, 491.33f, 4, 4, 4, 4, 4, 48, 13, 5, 48, R.xml.default_workspace_4x4));
        predefinedDeviceProfiles.add(new InvariantDeviceProfile("Nexus 4",
                359, 567,     4, 4, 4, 4, 4, DEFAULT_ICON_SIZE_DP, 13, 5, 56, R.xml.default_workspace_4x4));
        predefinedDeviceProfiles.add(new InvariantDeviceProfile("Nexus 5",
                335, 567,     4, 4, 4, 4, 4, DEFAULT_ICON_SIZE_DP, 13, 5, 56, R.xml.default_workspace_4x4));
        predefinedDeviceProfiles.add(new InvariantDeviceProfile("Large Phone",
                406, 694,     5, 5, 4, 4, 4, 64, 14.4f,  5, 56, R.xml.default_workspace_5x5));
        // The tablet profile is odd in that the landscape orientation
        // also includes the nav bar on the side
        predefinedDeviceProfiles.add(new InvariantDeviceProfile("Nexus 7",
                575, 904,     5, 6, 4, 5, 4, 72, 14.4f,  7, 60, R.xml.default_workspace_5x6));
        // Larger tablet profiles always have system bars on the top & bottom
        predefinedDeviceProfiles.add(new InvariantDeviceProfile("Nexus 10",
                727, 1207,    5, 6, 4, 5, 4, 76, 14.4f,  7, 76, R.xml.default_workspace_5x6));
        predefinedDeviceProfiles.add(new InvariantDeviceProfile("20-inch Tablet",
                1527, 2527,   7, 7, 6, 6, 4, 100, 20,  7, 72, R.xml.default_workspace_5x6));
        return predefinedDeviceProfiles;
    }

    private int getLauncherIconDensity(int requiredSize) {
        // Densities typically defined by an app.
        int[] densityBuckets = new int[] {
                DisplayMetrics.DENSITY_LOW,
                DisplayMetrics.DENSITY_MEDIUM,
                DisplayMetrics.DENSITY_TV,
                DisplayMetrics.DENSITY_HIGH,
                DisplayMetrics.DENSITY_XHIGH,
                DisplayMetrics.DENSITY_XXHIGH,
                DisplayMetrics.DENSITY_XXXHIGH
        };

        int density = DisplayMetrics.DENSITY_XXXHIGH;
        for (int i = densityBuckets.length - 1; i >= 0; i--) {
            float expectedSize = ICON_SIZE_DEFINED_IN_APP_DP * densityBuckets[i]
                    / DisplayMetrics.DENSITY_DEFAULT;
            if (expectedSize >= requiredSize) {
                density = densityBuckets[i];
            }
        }

        return density;
    }

    /**
     * Apply any Partner customization grid overrides.
     *
     * Currently we support: all apps row / column count.
     */
    private void applyPartnerDeviceProfileOverrides(Context context, DisplayMetrics dm) {
        Partner p = Partner.get(context.getPackageManager());
        if (p != null) {
            p.applyInvariantDeviceProfileOverrides(this, dm);
        }
    }

    @Thunk float dist(float x0, float y0, float x1, float y1) {
        return (float) Math.hypot(x1 - x0, y1 - y0);
    }

    /**
     * Returns the closest device profiles ordered by closeness to the specified width and height
     */
    // Package private visibility for testing.
    ArrayList<InvariantDeviceProfile> findClosestDeviceProfiles(
            final float width, final float height, ArrayList<InvariantDeviceProfile> points) {

        // Sort the profiles by their closeness to the dimensions
        ArrayList<InvariantDeviceProfile> pointsByNearness = points;
        Collections.sort(pointsByNearness, new Comparator<InvariantDeviceProfile>() {
            public int compare(InvariantDeviceProfile a, InvariantDeviceProfile b) {
                return Float.compare(dist(width, height, a.minWidthDps, a.minHeightDps),
                        dist(width, height, b.minWidthDps, b.minHeightDps));
            }
        });

        return pointsByNearness;
    }

    // Package private visibility for testing.
    InvariantDeviceProfile invDistWeightedInterpolate(float width, float height,
                ArrayList<InvariantDeviceProfile> points) {
        float weights = 0;

        InvariantDeviceProfile p = points.get(0);
        if (dist(width, height, p.minWidthDps, p.minHeightDps) == 0) {
            return p;
        }

        InvariantDeviceProfile out = new InvariantDeviceProfile();
        for (int i = 0; i < points.size() && i < KNEARESTNEIGHBOR; ++i) {
            p = new InvariantDeviceProfile(points.get(i));
            float w = weight(width, height, p.minWidthDps, p.minHeightDps, WEIGHT_POWER);
            weights += w;
            out.add(p.multiply(w));
        }
        return out.multiply(1.0f/weights);
    }

    private void add(InvariantDeviceProfile p) {
        iconSize += p.iconSize;
        iconTextSize += p.iconTextSize;
        hotseatIconSize += p.hotseatIconSize;
    }

    private InvariantDeviceProfile multiply(float w) {
        iconSize *= w;
        iconTextSize *= w;
        hotseatIconSize *= w;
        return this;
    }

    private float weight(float x0, float y0, float x1, float y1, float pow) {
        float d = dist(x0, y0, x1, y1);
        if (Float.compare(d, 0f) == 0) {
            return Float.POSITIVE_INFINITY;
        }
        return (float) (WEIGHT_EFFICIENT / Math.pow(d, pow));
    }
}