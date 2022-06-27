/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.quickstep;

import static com.android.launcher3.states.RotationHelper.deltaRotation;
import static com.android.quickstep.util.RecentsOrientedState.postDisplayRotation;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.Log;
import android.view.MotionEvent;

public class OrientationRectF extends RectF {

    private static final String TAG = "OrientationRectF";
    private static final boolean DEBUG = false;

    private final int mRotation;
    private final float mHeight;
    private final float mWidth;

    private final Matrix mTmpMatrix = new Matrix();
    private final float[] mTmpPoint = new float[2];

    public OrientationRectF(float left, float top, float right, float bottom, int rotation) {
        super(left, top, right, bottom);
        mRotation = rotation;
        mHeight = bottom;
        mWidth = right;
    }

    @Override
    public String toString() {
        String s = super.toString();
        s += " rotation: " + mRotation;
        return s;
    }

    @Override
    public boolean contains(float x, float y) {
        // Mark bottom right as included in the Rect (copied from Rect src, added "=" in "<=")
        return left < right && top < bottom  // check for empty first
                && x >= left && x <= right && y >= top && y <= bottom;
    }

    public boolean applyTransformFromRotation(MotionEvent event, int currentRotation,
            boolean forceTransform) {
        return applyTransform(event, deltaRotation(currentRotation, mRotation), forceTransform);
    }

    public boolean applyTransformToRotation(MotionEvent event, int currentRotation,
            boolean forceTransform) {
        return applyTransform(event, deltaRotation(mRotation, currentRotation), forceTransform);
    }

    public boolean applyTransform(MotionEvent event, int deltaRotation, boolean forceTransform) {
        mTmpMatrix.reset();
        postDisplayRotation(deltaRotation, mHeight, mWidth, mTmpMatrix);
        if (forceTransform) {
            if (DEBUG) {
                Log.d(TAG, "Transforming rotation due to forceTransform, "
                        + "deltaRotation: " + deltaRotation
                        + "mRotation: " + mRotation
                        + " this: " + this);
            }
            event.applyTransform(mTmpMatrix);
            return true;
        }
        mTmpPoint[0] = event.getX();
        mTmpPoint[1] = event.getY();
        mTmpMatrix.mapPoints(mTmpPoint);

        if (DEBUG) {
            Log.d(TAG, "original: " + event.getX() + ", " + event.getY()
                    + " new: " + mTmpPoint[0] + ", " + mTmpPoint[1]
                    + " rect: " + this + " forceTransform: " + forceTransform
                    + " contains: " + contains(mTmpPoint[0], mTmpPoint[1])
                    + " this: " + this);
        }

        if (contains(mTmpPoint[0], mTmpPoint[1])) {
            event.applyTransform(mTmpMatrix);
            return true;
        }
        return false;
    }

    int getRotation() {
        return mRotation;
    }
}
