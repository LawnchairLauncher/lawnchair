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
import android.graphics.PointF;
import android.os.Build;
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

    private static final ArrayList<InvariantDeviceProfile> sDeviceProfiles = new ArrayList<>();
    static {
        sDeviceProfiles.add(new InvariantDeviceProfile("Super Short Stubby",
                255, 300,  2, 3, 2, 3, 48, 13, 3, 48, R.xml.default_workspace_4x4));
        sDeviceProfiles.add(new InvariantDeviceProfile("Shorter Stubby",
                255, 400,  3, 3, 3, 3, 48, 13, 3, 48, R.xml.default_workspace_4x4));
        sDeviceProfiles.add(new InvariantDeviceProfile("Short Stubby",
                275, 420,  3, 4, 3, 4, 48, 13, 5, 48, R.xml.default_workspace_4x4));
        sDeviceProfiles.add(new InvariantDeviceProfile("Stubby",
                255, 450,  3, 4, 3, 4, 48, 13, 5, 48, R.xml.default_workspace_4x4));
        sDeviceProfiles.add(new InvariantDeviceProfile("Nexus S",
                296, 491.33f,  4, 4, 4, 4, 48, 13, 5, 48, R.xml.default_workspace_4x4));
        sDeviceProfiles.add(new InvariantDeviceProfile("Nexus 4",
                335, 567,  4, 4, 4, 4, DEFAULT_ICON_SIZE_DP, 13, 5, 56, R.xml.default_workspace_4x4));
        sDeviceProfiles.add(new InvariantDeviceProfile("Nexus 5",
                359, 567,  4, 4, 4, 4, DEFAULT_ICON_SIZE_DP, 13, 5, 56, R.xml.default_workspace_4x4));
        sDeviceProfiles.add(new InvariantDeviceProfile("Large Phone",
                406, 694,  5, 5, 4, 4,  64, 14.4f,  5, 56, R.xml.default_workspace_5x5));
        // The tablet profile is odd in that the landscape orientation
        // also includes the nav bar on the side
        sDeviceProfiles.add(new InvariantDeviceProfile("Nexus 7",
                575, 904,  5, 6, 4, 5, 72, 14.4f,  7, 60, R.xml.default_workspace_5x6));
        // Larger tablet profiles always have system bars on the top & bottom
        sDeviceProfiles.add(new InvariantDeviceProfile("Nexus 10",
                727, 1207,  5, 6, 4, 5, 76, 14.4f,  7, 64, R.xml.default_workspace_5x6));
        sDeviceProfiles.add(new InvariantDeviceProfile("20-inch Tablet",
                1527, 2527,  7, 7, 6, 6, 100, 20,  7, 72, R.xml.default_workspace_4x4));
    }

    private class DeviceProfileQuery {
        InvariantDeviceProfile profile;
        float widthDps;
        float heightDps;
        float value;
        PointF dimens;

        DeviceProfileQuery(InvariantDeviceProfile p, float v) {
            widthDps = p.minWidthDps;
            heightDps = p.minHeightDps;
            value = v;
            dimens = new PointF(widthDps, heightDps);
            profile = p;
        }
    }

    // Profile-defining invariant properties
    String name;
    float minWidthDps;
    float minHeightDps;
    public int numRows;
    public int numColumns;
    public int numFolderRows;
    public int numFolderColumns;
    float iconSize;
    float iconTextSize;
    float numHotseatIcons;
    float hotseatIconSize;
    int defaultLayoutId;

    // Derived invariant properties
    int hotseatAllAppsRank;

    DeviceProfile landscapeProfile;
    DeviceProfile portraitProfile;

    InvariantDeviceProfile() {
    }

    InvariantDeviceProfile(String n, float w, float h, int r, int c, int fr, int fc,
            float is, float its, float hs, float his, int dlId) {
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
        iconSize = is;
        iconTextSize = its;
        numHotseatIcons = hs;
        hotseatIconSize = his;
        defaultLayoutId = dlId;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    InvariantDeviceProfile(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics dm = new DisplayMetrics();
        display.getMetrics(dm);

        Point smallestSize = new Point();
        Point largestSize = new Point();
        display.getCurrentSizeRange(smallestSize, largestSize);

        minWidthDps = Utilities.dpiFromPx(Math.min(smallestSize.x, smallestSize.y), dm);
        minHeightDps = Utilities.dpiFromPx(Math.min(largestSize.x, largestSize.y), dm);

        ArrayList<DeviceProfileQuery> points =
                new ArrayList<DeviceProfileQuery>();

        // Find the closes profile given the width/height
        for (InvariantDeviceProfile p : sDeviceProfiles) {
            points.add(new DeviceProfileQuery(p, 0f));
        }

        InvariantDeviceProfile closestProfile =
                findClosestDeviceProfile(minWidthDps, minHeightDps, points);

        // The following properties are inherited directly from the nearest archetypal profile
        numRows = closestProfile.numRows;
        numColumns = closestProfile.numColumns;
        numHotseatIcons = closestProfile.numHotseatIcons;
        hotseatAllAppsRank = (int) (numHotseatIcons / 2);
        defaultLayoutId = closestProfile.defaultLayoutId;
        numFolderRows = closestProfile.numFolderRows;
        numFolderColumns = closestProfile.numFolderColumns;


        // The following properties are interpolated based on proximity to nearby archetypal
        // profiles
        points.clear();
        for (InvariantDeviceProfile p : sDeviceProfiles) {
            points.add(new DeviceProfileQuery(p, p.iconSize));
        }
        iconSize = invDistWeightedInterpolate(minWidthDps, minHeightDps, points);
        points.clear();
        for (InvariantDeviceProfile p : sDeviceProfiles) {
            points.add(new DeviceProfileQuery(p, p.iconTextSize));
        }
        iconTextSize = invDistWeightedInterpolate(minWidthDps, minHeightDps, points);
        points.clear();
        for (InvariantDeviceProfile p : sDeviceProfiles) {
            points.add(new DeviceProfileQuery(p, p.hotseatIconSize));
        }
        hotseatIconSize = invDistWeightedInterpolate(minWidthDps, minHeightDps, points);

        // If the partner customization apk contains any grid overrides, apply them
        // Supported overrides: numRows, numColumns, iconSize
        applyPartnerDeviceProfileOverrides(context, dm);

        Point realSize = new Point();
        display.getRealSize(realSize);
        // The real size never changes. smallSide and largeSize will remain the
        // same in any orientation.
        int smallSide = Math.min(realSize.x, realSize.y);
        int largeSide = Math.max(realSize.x, realSize.y);

        landscapeProfile = new DeviceProfile(context, this, smallestSize, largestSize,
                largeSide, smallSide, true /* isLandscape */);
        portraitProfile = new DeviceProfile(context, this, smallestSize, largestSize,
                smallSide, largeSide, false /* isLandscape */);
    }

    /**
     * Apply any Partner customization grid overrides.
     *
     * Currently we support: all apps row / column count.
     */
    private void applyPartnerDeviceProfileOverrides(Context ctx, DisplayMetrics dm) {
        Partner p = Partner.get(ctx.getPackageManager());
        if (p != null) {
            p.applyInvariantDeviceProfileOverrides(this, dm);
        }
    }

    @Thunk float dist(PointF p0, PointF p1) {
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

    /** Returns the closest device profile given the width and height and a list of profiles */
    private InvariantDeviceProfile findClosestDeviceProfile(float width, float height,
                                                   ArrayList<DeviceProfileQuery> points) {
        return findClosestDeviceProfiles(width, height, points).get(0).profile;
    }

    /** Returns the closest device profiles ordered by closeness to the specified width and height */
    private ArrayList<DeviceProfileQuery> findClosestDeviceProfiles(float width, float height,
                                                   ArrayList<DeviceProfileQuery> points) {
        final PointF xy = new PointF(width, height);

        // Sort the profiles by their closeness to the dimensions
        ArrayList<DeviceProfileQuery> pointsByNearness = points;
        Collections.sort(pointsByNearness, new Comparator<DeviceProfileQuery>() {
            public int compare(DeviceProfileQuery a, DeviceProfileQuery b) {
                return (int) (dist(xy, a.dimens) - dist(xy, b.dimens));
            }
        });

        return pointsByNearness;
    }

    private float invDistWeightedInterpolate(float width, float height,
                ArrayList<DeviceProfileQuery> points) {
        float sum = 0;
        float weights = 0;
        float pow = 5;
        float kNearestNeighbors = 3;
        final PointF xy = new PointF(width, height);

        ArrayList<DeviceProfileQuery> pointsByNearness = findClosestDeviceProfiles(width, height,
                points);

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
}
