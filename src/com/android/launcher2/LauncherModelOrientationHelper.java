/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.launcher2;

import android.content.Context;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

public class LauncherModelOrientationHelper {

    static final String TAG = "LauncherModelOrientationHelper";

    public class Coordinates {
        public Coordinates(int newX, int newY) {
            x = newX;
            y = newY;
        }

        public int x;
        public int y;
    }

    private int mOrientation;
    private int mLocalDeviceWidth;
    private int mLocalDeviceHeight;
    private int mPreviousOrientation;
    private int mPreviousLocalDeviceWidth;
    private int mPreviousLocalDeviceHeight;
    private int mCanonicalDeviceWidth;
    private int mCanonicalDeviceHeight;

    protected LauncherModelOrientationHelper(Context ctx) {
        updateOrientation(ctx);
    }

    public int getCurrentOrientation() {
        return mOrientation;
    }

    public int getPreviousOrientationRelativeToCurrent() {
        int orientationDifference = -(mOrientation - mPreviousOrientation);

        if (Math.abs(orientationDifference) > 180) {
            orientationDifference = (int) -Math.signum(orientationDifference)
                    * (360 - Math.abs(orientationDifference));
        }
        return orientationDifference;
    }

    private void updateLocalDeviceDimensions() {
        mPreviousLocalDeviceHeight = mLocalDeviceHeight;
        mPreviousLocalDeviceWidth = mLocalDeviceWidth;

        if (mOrientation % 180 != 0) {
            mLocalDeviceWidth = mCanonicalDeviceHeight;
            mLocalDeviceHeight = mCanonicalDeviceWidth;
        } else {
            mLocalDeviceWidth = mCanonicalDeviceWidth;
            mLocalDeviceHeight = mCanonicalDeviceHeight;
        }
    }

    public void updateOrientation(Context ctx) {
        Display display = ((WindowManager) ctx
                .getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();

        mPreviousOrientation = mOrientation;
        switch (display.getRotation()) {
        case Surface.ROTATION_0:
            mOrientation = 0;
            break;
        case Surface.ROTATION_90:
            mOrientation = 90;
            break;
        case Surface.ROTATION_180:
            mOrientation = 180;
            break;
        case Surface.ROTATION_270:
            mOrientation = 270;
            break;
        }
        updateLocalDeviceDimensions();
    }

    public void updateDeviceDimensions(int deviceWidth, int deviceHeight) {
        mCanonicalDeviceWidth = deviceWidth;
        mCanonicalDeviceHeight = deviceHeight;

        updateLocalDeviceDimensions();
    }

    public Coordinates getLocalCoordinatesFromPreviousLocalCoordinates(
            int cellX, int cellY, int spanX, int spanY) {
        return getTransformedLayoutParams(cellX, cellY, spanX, spanY,
                getPreviousOrientationRelativeToCurrent(),
                mPreviousLocalDeviceWidth, mPreviousLocalDeviceHeight);
    }

    public Coordinates getCanonicalCoordinates(ItemInfo localItem) {
        return getTransformedLayoutParams(localItem.cellX, localItem.cellY,
                localItem.spanX, localItem.spanY, mOrientation,
                mLocalDeviceWidth, mLocalDeviceHeight);
    }

    public Coordinates getCanonicalCoordinates(int cellX, int cellY,
            int spanX, int spanY) {
        return getTransformedLayoutParams(cellX, cellY, spanX, spanY,
                mOrientation, mLocalDeviceWidth, mLocalDeviceHeight);
    }

    public Coordinates getLocalCoordinates(int cellX, int cellY, int spanX,
            int spanY) {
        return getTransformedLayoutParams(cellX, cellY, spanX, spanY,
                -mOrientation, mCanonicalDeviceWidth, mCanonicalDeviceHeight);
    }

    public int getLocalDeviceWidth() {
        return mLocalDeviceWidth;
    }

    public int getLocalDeviceHeight() {
        return mLocalDeviceHeight;
    }

    /**
     * Transform the coordinates based on the current device rotation
     */
    private Coordinates getTransformedLayoutParams(int cellX, int cellY,
            int spanX, int spanY, int deviceRotationClockwise,
            int initialDeviceWidth, int initialDeviceHeight) {
        if (LauncherApplication.isScreenXLarge()) {
            int x = cellX;
            int y = cellY;
            int width = spanX;
            int height = spanY;
            int finalDeviceWidth = initialDeviceWidth;
            int finalDeviceHeight = initialDeviceHeight;

            // item rotation is opposite of device rotation to maintain an
            // absolute
            // spatial layout
            double phi = Math.toRadians(-deviceRotationClockwise);

            double x1 = x + width / 2.0f - initialDeviceWidth / 2.0f;
            double y1 = y + height / 2.0f - initialDeviceHeight / 2.0f;

            // multiply x and y by a clockwise rotation matrix
            double x2 = x1 * Math.cos(phi) + y1 * Math.sin(phi);
            double y2 = -x1 * Math.sin(phi) + y1 * Math.cos(phi);

            // Get the rotated device dimensions
            if (deviceRotationClockwise % 180 != 0) {
                finalDeviceWidth = initialDeviceHeight;
                finalDeviceHeight = initialDeviceWidth;
            }

            x2 = x2 + finalDeviceWidth / 2.0f - width / 2.0f;
            y2 = y2 + finalDeviceHeight / 2.0f - height / 2.0f;

            return new Coordinates((int) Math.round(x2), (int) Math.round(y2));
        } else {
            return new Coordinates(cellX, cellY);
        }
    }
}
