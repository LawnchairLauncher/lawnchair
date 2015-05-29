/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.launcher3;

import android.graphics.PointF;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.util.FocusLogic;

import java.util.ArrayList;

/**
 * Tests the {@link DeviceProfile} and {@link InvariantDeviceProfile}.
 */
@SmallTest
public class InvariantDeviceProfileTest extends AndroidTestCase {

    private static final String TAG = "DeviceProfileTest";
    private static final boolean DEBUG = false;

    private InvariantDeviceProfile mInvariantProfile;
    private ArrayList<InvariantDeviceProfile> mPredefinedDeviceProfiles;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInvariantProfile = new InvariantDeviceProfile(getContext());
        mPredefinedDeviceProfiles = mInvariantProfile.getPredefinedDeviceProfiles();
    }

    @Override
    protected void tearDown() throws Exception {
        // Nothing to tear down as this class only tests static methods.
    }

    public void testFindClosestDeviceProfile2() {
        for (InvariantDeviceProfile idf: mPredefinedDeviceProfiles) {
            ArrayList<InvariantDeviceProfile> closestProfiles =
                    mInvariantProfile.findClosestDeviceProfiles(
                            idf.minWidthDps, idf.minHeightDps, mPredefinedDeviceProfiles);
            assertTrue(closestProfiles.get(0).equals(idf));
        }
    }

    /**
     * Used to print out how the invDistWeightedInterpolate works between device profiles to
     * tweak the two constants that control how the interpolation curve is shaped.
     */
    public void testInvInterpolation() {

        InvariantDeviceProfile p1 = mPredefinedDeviceProfiles.get(7); // e.g., Large Phone
        InvariantDeviceProfile p2 = mPredefinedDeviceProfiles.get(8); // e.g., Nexus 7

        ArrayList<PointF> pts = createInterpolatedPoints(
                new PointF(p1.minWidthDps, p1.minHeightDps),
                new PointF(p2.minWidthDps, p2.minHeightDps),
                20f);

        for (int i = 0; i < pts.size(); i++) {
            ArrayList<InvariantDeviceProfile> closestProfiles =
                    mInvariantProfile.findClosestDeviceProfiles(
                            pts.get(i).x, pts.get(i).y, mPredefinedDeviceProfiles);
            InvariantDeviceProfile result =
                    mInvariantProfile.invDistWeightedInterpolate(
                            pts.get(i).x, pts.get(i).y, closestProfiles);
            if (DEBUG) {
                Log.d(TAG, String.format("width x height = (%f, %f)] iconSize = %f",
                        pts.get(i).x, pts.get(i).y, result.iconSize));
            }
        }
    }

    private ArrayList<PointF> createInterpolatedPoints(PointF a, PointF b, float numPts) {
        ArrayList<PointF> result = new ArrayList<PointF>();
        result.add(a);
        for (float i = 1; i < numPts; i = i + 1.0f) {
            result.add(new PointF((b.x * i +  a.x * (numPts - i)) / numPts,
                    (b.y * i + a.y * (numPts - i)) / numPts));
        }
        result.add(b);
        return result;
    }

    /**
     * Ensures that system calls (e.g., WindowManager, DisplayMetrics) that require contexts are
     * properly working to generate minimum width and height of the display.
     */
    public void test_hammerhead() {
        if (!android.os.Build.DEVICE.equals("hammerhead")) {
            return;
        }
        assertEquals(mInvariantProfile.numRows, 4);
        assertEquals(mInvariantProfile.numColumns, 4);
        assertEquals((int) mInvariantProfile.numHotseatIcons, 5);

        DeviceProfile landscapeProfile = mInvariantProfile.landscapeProfile;
        DeviceProfile portraitProfile = mInvariantProfile.portraitProfile;

        assertEquals(portraitProfile.allAppsNumCols, 3);
        assertEquals(landscapeProfile.allAppsNumCols, 5); // not used
    }

    // Add more tests for other devices, however, running them once on a single device is enough
    // for verifying that for a platform version, the WindowManager and DisplayMetrics is
    // working as intended.
}
