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
package com.android.wm.shell.common.pip;

import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;

/**
 * Helper class to calculate the new size given two-fingers pinch to resize.
 */
public class PipPinchResizingAlgorithm {

    private static final int PINCH_RESIZE_MAX_ANGLE_ROTATION = 45;
    private static final float OVERROTATE_DAMP_FACTOR = 0.4f;
    private static final float ANGLE_THRESHOLD = 5f;
    private static final float OVERRESIZE_DAMP_FACTOR = 0.25f;

    private final PointF mTmpDownVector = new PointF();
    private final PointF mTmpLastVector = new PointF();
    private final PointF mTmpDownCentroid = new PointF();
    private final PointF mTmpLastCentroid = new PointF();

    /**
     * Updates resizeBoundsOut with the new bounds of the PIP, and returns the angle in
     * degrees that the PIP should be rotated.
     */
    public float calculateBoundsAndAngle(PointF downPoint, PointF downSecondPoint,
            PointF lastPoint, PointF lastSecondPoint, Point minSize, Point maxSize,
            Rect initialBounds, Rect resizeBoundsOut) {
        float downDist = (float) Math.hypot(downSecondPoint.x - downPoint.x,
                downSecondPoint.y - downPoint.y);
        float dist = (float) Math.hypot(lastSecondPoint.x - lastPoint.x,
                lastSecondPoint.y - lastPoint.y);
        float minScale = getMinScale(initialBounds, minSize);
        float maxScale = getMaxScale(initialBounds, maxSize);
        float overStretchMin = minScale - dist / downDist > 0 ? minScale - dist / downDist : 0;
        float overStretchMax = dist / downDist - maxScale > 0 ? dist / downDist - maxScale : 0;
        float scale = Math.max(minScale - overStretchMin * OVERRESIZE_DAMP_FACTOR,
                Math.min(maxScale + overStretchMax * OVERRESIZE_DAMP_FACTOR, dist / downDist));

        // Scale the bounds by the change in distance between the points
        resizeBoundsOut.set(initialBounds);
        scaleRectAboutCenter(resizeBoundsOut, scale);

        // Translate by the centroid movement
        getCentroid(downPoint, downSecondPoint, mTmpDownCentroid);
        getCentroid(lastPoint, lastSecondPoint, mTmpLastCentroid);
        resizeBoundsOut.offset((int) (mTmpLastCentroid.x - mTmpDownCentroid.x),
                (int) (mTmpLastCentroid.y - mTmpDownCentroid.y));

        // Calculate the angle
        mTmpDownVector.set(downSecondPoint.x - downPoint.x,
                downSecondPoint.y - downPoint.y);
        mTmpLastVector.set(lastSecondPoint.x - lastPoint.x,
                lastSecondPoint.y - lastPoint.y);
        float angle = (float) Math.atan2(cross(mTmpDownVector, mTmpLastVector),
                dot(mTmpDownVector, mTmpLastVector));
        return constrainRotationAngle((float) Math.toDegrees(angle));
    }

    private float getMinScale(Rect bounds, Point minSize) {
        return Math.max((float) minSize.x / bounds.width(), (float) minSize.y / bounds.height());
    }

    private float getMaxScale(Rect bounds, Point maxSize) {
        return Math.min((float) maxSize.x / bounds.width(), (float) maxSize.y / bounds.height());
    }

    private float constrainRotationAngle(float angle) {
        // Remove some degrees so that user doesn't immediately start rotating until a threshold
        return Math.signum(angle) * Math.max(0, (Math.abs(dampedRotate(angle)) - ANGLE_THRESHOLD));
    }

    /**
     * Given the current rotation angle, dampen it so that as it approaches the maximum angle,
     * dampen it.
     */
    private float dampedRotate(float amount) {
        if (Float.compare(amount, 0) == 0) return 0;

        float f = amount / PINCH_RESIZE_MAX_ANGLE_ROTATION;
        f = f / (Math.abs(f)) * (overRotateInfluenceCurve(Math.abs(f)));

        // Clamp this factor, f, to -1 < f < 1
        if (Math.abs(f) >= 1) {
            f /= Math.abs(f);
        }
        return OVERROTATE_DAMP_FACTOR * f * PINCH_RESIZE_MAX_ANGLE_ROTATION;
    }

    /**
     * Returns a value that corresponds to y = (f - 1)^3 + 1.
     */
    private float overRotateInfluenceCurve(float f) {
        f -= 1.0f;
        return f * f * f + 1.0f;
    }

    private void getCentroid(PointF p1, PointF p2, PointF centroidOut) {
        centroidOut.set((p2.x + p1.x) / 2, (p2.y + p1.y) / 2);
    }

    private float dot(PointF p1, PointF p2) {
        return p1.x * p2.x + p1.y * p2.y;
    }

    private float cross(PointF p1, PointF p2) {
        return p1.x * p2.y - p1.y * p2.x;
    }

    private void scaleRectAboutCenter(Rect r, float scale) {
        if (scale != 1.0f) {
            int cx = r.centerX();
            int cy = r.centerY();
            r.offset(-cx, -cy);
            r.scale(scale);
            r.offset(cx, cy);
        }
    }
}
