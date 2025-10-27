/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.biometrics;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.util.DisplayUtils;
import android.util.Log;
import android.util.RotationUtils;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.MotionEvent;
import android.view.Surface;

import com.android.systemui.biometrics.shared.model.UdfpsOverlayParams;
import com.android.systemui.shared.biometrics.R;

/** Utility class for working with udfps. */
public class UdfpsUtils {
    private static final String TAG = "UdfpsUtils";

    /**
     * Gets the scale factor representing the user's current resolution / the stable (default)
     * resolution.
     *
     * @param displayInfo The display information.
     */
    public float getScaleFactor(DisplayInfo displayInfo) {
        Display.Mode maxDisplayMode =
                DisplayUtils.getMaximumResolutionDisplayMode(displayInfo.supportedModes);
        float scaleFactor =
                DisplayUtils.getPhysicalPixelDisplaySizeRatio(
                        maxDisplayMode.getPhysicalWidth(),
                        maxDisplayMode.getPhysicalHeight(),
                        displayInfo.getNaturalWidth(),
                        displayInfo.getNaturalHeight()
                );
        return (scaleFactor == Float.POSITIVE_INFINITY) ? 1f : scaleFactor;
    }

    /**
     * Gets the touch in native coordinates.
     *
     * Maps the touch to portrait mode if the device is in landscape mode.
     *
     * @param idx                The pointer identifier.
     * @param event              The MotionEvent object containing full information about the event.
     * @param udfpsOverlayParams The [UdfpsOverlayParams] used.
     * @return The mapped touch event.
     */
    public Point getTouchInNativeCoordinates(int idx, MotionEvent event,
            UdfpsOverlayParams udfpsOverlayParams) {
        return getTouchInNativeCoordinates(idx, event, udfpsOverlayParams, true);
    }

    /**
     * Gets the touch in native coordinates.
     *
     * Optionally map the touch to portrait mode if the device is in landscape mode.
     *
     * @param idx                The pointer identifier.
     * @param event              The MotionEvent object containing full information about the event.
     * @param udfpsOverlayParams The [UdfpsOverlayParams] used.
     * @param rotateToPortrait   Whether to rotate the touch to portrait orientation.
     * @return The mapped touch event.
     */
    public Point getTouchInNativeCoordinates(int idx, MotionEvent event,
            UdfpsOverlayParams udfpsOverlayParams, boolean rotateToPortrait) {
        Point touch;
        if (rotateToPortrait) {
            touch = getPortraitTouch(idx, event, udfpsOverlayParams);
        } else {
            touch = new Point((int) event.getRawX(idx), (int) event.getRawY(idx));
        }

        // Scale the coordinates to native resolution.
        float scale = udfpsOverlayParams.getScaleFactor();
        touch.x = (int) (touch.x / scale);
        touch.y = (int) (touch.y / scale);
        return touch;
    }

    /**
     * @param idx                The pointer identifier.
     * @param event              The MotionEvent object containing full information about the event.
     * @param udfpsOverlayParams The [UdfpsOverlayParams] used.
     * @return Whether the touch event (that needs to be rotated to portrait) is within sensor area.
     */
    public boolean isWithinSensorArea(int idx, MotionEvent event,
            UdfpsOverlayParams udfpsOverlayParams) {
        return isWithinSensorArea(idx, event, udfpsOverlayParams, true);
    }

    /**
     * @param idx                The pointer identifier.
     * @param event              The MotionEvent object containing full information about the event.
     * @param udfpsOverlayParams The [UdfpsOverlayParams] used.
     * @param rotateTouchToPortrait Whether to rotate the touch coordinates to portrait.
     * @return Whether the touch event is within sensor area.
     */
    public boolean isWithinSensorArea(int idx, MotionEvent event,
            UdfpsOverlayParams udfpsOverlayParams, boolean rotateTouchToPortrait) {
        Point touch;
        if (rotateTouchToPortrait) {
            touch = getPortraitTouch(idx, event, udfpsOverlayParams);
        } else {
            touch = new Point((int) event.getRawX(idx), (int) event.getRawY(idx));
        }
        return udfpsOverlayParams.getSensorBounds().contains(touch.x, touch.y);
    }

    /**
     * This function computes the angle of touch relative to the sensor, rotated to portrait,
     * and maps the angle to a list of help messages which are announced if accessibility is
     * enabled.
     *
     * @return announcement string
     */
    public String onTouchOutsideOfSensorArea(boolean touchExplorationEnabled, Context context,
            int scaledTouchX, int scaledTouchY, UdfpsOverlayParams udfpsOverlayParams) {
        return onTouchOutsideOfSensorArea(touchExplorationEnabled, context, scaledTouchX,
                scaledTouchY, udfpsOverlayParams, true);
    }

    /**
     * This function computes the angle of touch relative to the sensor and maps the angle to a list
     * of help messages which are announced if accessibility is enabled.
     *
     * @return announcement string
     */
    public String onTouchOutsideOfSensorArea(boolean touchExplorationEnabled, Context context,
            int scaledTouchX, int scaledTouchY, UdfpsOverlayParams udfpsOverlayParams,
            boolean touchRotatedToPortrait) {
        if (!touchExplorationEnabled) {
            return null;
        }

        Resources resources = context.getResources();
        String[] touchHints = new String[] {
                resources.getString(R.string.udfps_accessibility_touch_hints_left),
                resources.getString(R.string.udfps_accessibility_touch_hints_down),
                resources.getString(R.string.udfps_accessibility_touch_hints_right),
                resources.getString(R.string.udfps_accessibility_touch_hints_up),
        };

        // Scale the coordinates to native resolution.
        float scale = udfpsOverlayParams.getScaleFactor();
        float scaledSensorX = udfpsOverlayParams.getSensorBounds().centerX() / scale;
        float scaledSensorY = udfpsOverlayParams.getSensorBounds().centerY() / scale;
        String theStr =
                onTouchOutsideOfSensorAreaImpl(
                        touchHints,
                        scaledTouchX,
                        scaledTouchY,
                        scaledSensorX,
                        scaledSensorY,
                        udfpsOverlayParams.getRotation(),
                        touchRotatedToPortrait
                );
        Log.v(TAG, "Announcing touch outside : $theStr");
        return theStr;
    }

    /**
     * This function computes the angle of touch relative to the sensor and maps the angle to a list
     * of help messages which are announced if accessibility is enabled.
     *
     * There are 4 quadrants of the circle (90 degree arcs)
     *
     * [315, 360] && [0, 45) -> touchHints[0] = "Move Fingerprint to the left" [45, 135) ->
     * touchHints[1] = "Move Fingerprint down" And so on.
     */
    private String onTouchOutsideOfSensorAreaImpl(String[] touchHints, float touchX,
            float touchY, float sensorX, float sensorY, int rotation, boolean rotatedToPortrait) {
        float xRelativeToSensor = touchX - sensorX;
        // Touch coordinates are with respect to the upper left corner, so reverse
        // this calculation
        float yRelativeToSensor = sensorY - touchY;
        double angleInRad = Math.atan2(yRelativeToSensor, xRelativeToSensor);
        // If the radians are negative, that means we are counting clockwise.
        // So we need to add 360 degrees
        if (angleInRad < 0.0) {
            angleInRad += 2.0 * Math.PI;
        }
        // rad to deg conversion
        double degrees = Math.toDegrees(angleInRad);
        double degreesPerBucket = 360.0 / touchHints.length;
        double halfBucketDegrees = degreesPerBucket / 2.0;
        // The mapping should be as follows
        // [315, 360] && [0, 45] -> 0
        // [45, 135]             -> 1
        int index = (int) ((degrees + halfBucketDegrees) % 360 / degreesPerBucket);
        index %= touchHints.length;

        if (rotatedToPortrait) {
            // A rotation of 90 degrees corresponds to increasing the index by 1.
            if (rotation == Surface.ROTATION_90) {
                index = (index + 1) % touchHints.length;
            }
            if (rotation == Surface.ROTATION_270) {
                index = (index + 3) % touchHints.length;
            }
        }

        return touchHints[index];
    }

    /**
     * Map the touch to portrait mode if the device is in landscape mode.
     */
    private Point getPortraitTouch(int idx, MotionEvent event,
            UdfpsOverlayParams udfpsOverlayParams) {
        Point portraitTouch = new Point((int) event.getRawX(idx), (int) event.getRawY(idx));
        int rot = udfpsOverlayParams.getRotation();
        if (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270) {
            RotationUtils.rotatePoint(
                    portraitTouch,
                    RotationUtils.deltaRotation(rot, Surface.ROTATION_0),
                    udfpsOverlayParams.getLogicalDisplayWidth(),
                    udfpsOverlayParams.getLogicalDisplayHeight()
            );
        }
        return portraitTouch;
    }
}
