/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.quickstep.util;

import static android.hardware.camera2.params.OutputConfiguration.ROTATION_180;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.Surface;

import androidx.annotation.IntDef;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.touch.PortraitPagedViewHandler;

import java.lang.annotation.Retention;

/**
 * Container to hold orientation/rotation related information for Launcher.
 * This is not meant to be an abstraction layer for applying different functionality between
 * the different orientation/rotations. For that see {@link PagedOrientationHandler}
 *
 * This class has initial default state assuming the device and foreground app have
 * no ({@link Surface#ROTATION_0} rotation.
 */
public final class RecentsOrientedState {

    @Retention(SOURCE)
    @IntDef({ROTATION_0, ROTATION_90, ROTATION_180, ROTATION_270})
    public @interface SurfaceRotation {}

    private PagedOrientationHandler mOrientationHandler = PagedOrientationHandler.PORTRAIT;

    private @SurfaceRotation int mTouchRotation = ROTATION_0;
    private @SurfaceRotation int mDisplayRotation = ROTATION_0;
    /**
     * If {@code true} we default to {@link PortraitPagedViewHandler} and don't support any fake
     * launcher orientations.
     */
    private boolean mDisableMultipleOrientations;

    private final Matrix mTmpMatrix = new Matrix();
    private final Matrix mTmpInverseMatrix = new Matrix();

    /**
     * Sets the appropriate {@link PagedOrientationHandler} for {@link #mOrientationHandler}
     * @param touchRotation The rotation the nav bar region that is touched is in
     * @param displayRotation Rotation of the display/device
     *
     * @return true if there was any change in the internal state as a result of this call,
     *         false otherwise
     */
    public boolean update(
            @SurfaceRotation int touchRotation, @SurfaceRotation int displayRotation) {
        if (!FeatureFlags.ENABLE_FIXED_ROTATION_TRANSFORM.get()) {
            return false;
        }
        if (mDisableMultipleOrientations) {
            return false;
        }
        if (mDisplayRotation == displayRotation && mTouchRotation == touchRotation) {
            return false;
        }

        mDisplayRotation = displayRotation;
        mTouchRotation = touchRotation;
        if (mTouchRotation == ROTATION_90) {
            mOrientationHandler = PagedOrientationHandler.LANDSCAPE;
        } else if (mTouchRotation == ROTATION_270) {
            mOrientationHandler = PagedOrientationHandler.SEASCAPE;
        } else {
            mOrientationHandler = PagedOrientationHandler.PORTRAIT;
        }
        return true;
    }

    public boolean areMultipleLayoutOrientationsDisabled() {
        return mDisableMultipleOrientations;
    }

    /**
     * Setting this preference will render future calls to {@link #update(int, int)} as a no-op.
     */
    public void disableMultipleOrientations(boolean disable) {
        mDisableMultipleOrientations = disable;
        if (disable) {
            mDisplayRotation = mTouchRotation = ROTATION_0;
            mOrientationHandler = PagedOrientationHandler.PORTRAIT;
        }
    }

    @SurfaceRotation
    public int getDisplayRotation() {
        return mDisplayRotation;
    }

    @SurfaceRotation
    public int getTouchRotation() {
        return mTouchRotation;
    }

    public int getTouchRotationDegrees() {
        switch (mTouchRotation) {
            case ROTATION_90:
                return 90;
            case ROTATION_180:
                return 180;
            case ROTATION_270:
                return 270;
            case ROTATION_0:
            default:
                return 0;
        }
    }

    public PagedOrientationHandler getOrientationHandler() {
        return mOrientationHandler;
    }

    /**
     * For landscape, since the navbar is already in a vertical position, we don't have to do any
     * rotations as the change in Y coordinate is what is read. We only flip the sign of the
     * y coordinate to make it match existing behavior of swipe to the top to go previous
     */
    public void flipVertical(MotionEvent ev) {
        mTmpMatrix.setScale(1, -1);
        ev.transform(mTmpMatrix);
    }

    /**
     * Creates a matrix to transform the given motion event specified by degrees.
     * If inverse is {@code true}, the inverse of that matrix will be applied
     */
    public void transformEvent(float degrees, MotionEvent ev, boolean inverse) {
        mTmpMatrix.setRotate(inverse ? -degrees : degrees);
        ev.transform(mTmpMatrix);

        // TODO: Add scaling back in based on degrees
        /*
        if (getWidth() > 0 && getHeight() > 0) {
            float scale = ((float) getWidth()) / getHeight();
            transform.postScale(scale, 1 / scale);
        }
        */
    }

    public void mapRectFromNormalOrientation(RectF src, int screenWidth, int screenHeight) {
        mTmpMatrix.reset();
        postDisplayRotation(mDisplayRotation, screenWidth, screenHeight, mTmpMatrix);
        mTmpMatrix.mapRect(src);
    }

    public void mapInverseRectFromNormalOrientation(RectF src, int screenWidth, int screenHeight) {
        mTmpMatrix.reset();
        postDisplayRotation(mDisplayRotation, screenWidth, screenHeight, mTmpMatrix);
        mTmpMatrix.invert(mTmpInverseMatrix);
        mTmpInverseMatrix.mapRect(src);
    }

    @SurfaceRotation
    public static int getRotationForUserDegreesRotated(float degrees) {
        int threshold = 70;
        if (degrees >= (360 - threshold) || degrees < (threshold)) {
            return ROTATION_0;
        } else if (degrees < (90 + threshold)) {
            return ROTATION_270;
        } else if (degrees < 180 + threshold) {
            return ROTATION_180;
        } else {
            return ROTATION_90;
        }
    }

    /**
     * Posts the transformation on the matrix representing the provided display rotation
     */
    public static void postDisplayRotation(@SurfaceRotation int displayRotation,
            float screenWidth, float screenHeight, Matrix out) {
        switch (displayRotation) {
            case ROTATION_0:
                return;
            case ROTATION_90:
                out.postRotate(270);
                out.postTranslate(0, screenWidth);
                break;
            case ROTATION_180:
                out.postRotate(180);
                out.postTranslate(screenHeight, screenWidth);
                break;
            case ROTATION_270:
                out.postRotate(90);
                out.postTranslate(screenHeight, 0);
                break;
        }
    }
}
