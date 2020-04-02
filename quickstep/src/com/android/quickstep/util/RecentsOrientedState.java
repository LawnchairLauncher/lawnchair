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

import static android.Manifest.permission.WRITE_SECURE_SETTINGS;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.util.DisplayMetrics.DENSITY_DEVICE_STABLE;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import static com.android.launcher3.config.FeatureFlags.FLAG_ENABLE_FIXED_ROTATION_TRANSFORM;
import static com.android.launcher3.states.RotationHelper.ALLOW_ROTATION_PREFERENCE_KEY;
import static com.android.launcher3.states.RotationHelper.FIXED_ROTATION_TRANSFORM_SETTING_NAME;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;

import androidx.annotation.IntDef;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.touch.PortraitPagedViewHandler;

import java.lang.annotation.Retention;
import java.util.ArrayList;
import java.util.List;

/**
 * Container to hold orientation/rotation related information for Launcher.
 * This is not meant to be an abstraction layer for applying different functionality between
 * the different orientation/rotations. For that see {@link PagedOrientationHandler}
 *
 * This class has initial default state assuming the device and foreground app have
 * no ({@link Surface#ROTATION_0} rotation.
 */
public final class RecentsOrientedState implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "RecentsOrientedState";
    private static final boolean DEBUG = false;

    private ContentObserver mSystemAutoRotateObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateAutoRotateSetting();
        }
    };
    @Retention(SOURCE)
    @IntDef({ROTATION_0, ROTATION_90, ROTATION_180, ROTATION_270})
    public @interface SurfaceRotation {}

    private PagedOrientationHandler mOrientationHandler = PagedOrientationHandler.PORTRAIT;

    private @SurfaceRotation int mTouchRotation = ROTATION_0;
    private @SurfaceRotation int mDisplayRotation = ROTATION_0;
    private @SurfaceRotation int mLauncherRotation = Surface.ROTATION_0;

    public interface SystemRotationChangeListener {
        void onSystemRotationChanged(boolean enabled);
    }

    /**
     * If {@code true} we default to {@link PortraitPagedViewHandler} and don't support any fake
     * launcher orientations.
     */
    private boolean mDisableMultipleOrientations;
    private boolean mIsHomeRotationAllowed;
    private boolean mIsSystemRotationAllowed;

    private final ContentResolver mContentResolver;
    private final SharedPreferences mSharedPrefs;
    private final boolean mAllowConfigurationDefaultValue;

    private List<SystemRotationChangeListener> mSystemRotationChangeListeners = new ArrayList<>();

    private final Matrix mTmpMatrix = new Matrix();
    private final Matrix mTmpInverseMatrix = new Matrix();

    public RecentsOrientedState(Context context) {
        mContentResolver = context.getContentResolver();
        mSharedPrefs = Utilities.getPrefs(context);

        Resources res = context.getResources();
        int originalSmallestWidth = res.getConfiguration().smallestScreenWidthDp
                * res.getDisplayMetrics().densityDpi / DENSITY_DEVICE_STABLE;
        mAllowConfigurationDefaultValue = originalSmallestWidth >= 600;

        boolean isForcedRotation = Utilities.getFeatureFlagsPrefs(context)
                .getBoolean(FLAG_ENABLE_FIXED_ROTATION_TRANSFORM, true)
                && !mAllowConfigurationDefaultValue;
        UI_HELPER_EXECUTOR.execute(() -> {
            if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PERMISSION_GRANTED) {
                Settings.Global.putInt(mContentResolver, FIXED_ROTATION_TRANSFORM_SETTING_NAME,
                        isForcedRotation ? 1 : 0);
            }
        });
        disableMultipleOrientations(!isForcedRotation);
    }

    /**
     * Sets the appropriate {@link PagedOrientationHandler} for {@link #mOrientationHandler}
     * @param touchRotation The rotation the nav bar region that is touched is in
     * @param displayRotation Rotation of the display/device
     *
     * @return true if there was any change in the internal state as a result of this call,
     *         false otherwise
     */
    public boolean update(
            @SurfaceRotation int touchRotation, @SurfaceRotation int displayRotation,
            @SurfaceRotation int launcherRotation) {
        if (!FeatureFlags.ENABLE_FIXED_ROTATION_TRANSFORM.get()) {
            return false;
        }
        if (mDisableMultipleOrientations) {
            return false;
        }
        if (mDisplayRotation == displayRotation && mTouchRotation == touchRotation
                && launcherRotation == mLauncherRotation) {
            return false;
        }

        mLauncherRotation = launcherRotation;
        mDisplayRotation = displayRotation;
        mTouchRotation = touchRotation;

        if ((mIsHomeRotationAllowed && mIsSystemRotationAllowed) ||
                mLauncherRotation == mTouchRotation) {
            // TODO(b/153476489) Need to determine when launcher is rotated
            mOrientationHandler = PagedOrientationHandler.HOME_ROTATED;
            if (DEBUG) {
                Log.d(TAG, "Set Orientation Handler: " + mOrientationHandler);
            }
            return true;
        }

        if (mTouchRotation == ROTATION_90) {
            mOrientationHandler = PagedOrientationHandler.LANDSCAPE;
        } else if (mTouchRotation == ROTATION_270) {
            mOrientationHandler = PagedOrientationHandler.SEASCAPE;
        } else {
            mOrientationHandler = PagedOrientationHandler.PORTRAIT;
        }
        if (DEBUG) {
            Log.d(TAG, "Set Orientation Handler: " + mOrientationHandler);
        }
        return true;
    }

    /**
     * Setting this preference renders future calls to {@link #update(int, int, int)} as a no-op.
     */
    public void disableMultipleOrientations(boolean disable) {
        mDisableMultipleOrientations = disable;
        if (disable) {
            mDisplayRotation = mTouchRotation = ROTATION_0;
            mOrientationHandler = PagedOrientationHandler.PORTRAIT;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        updateHomeRotationSetting();
    }

    private void updateAutoRotateSetting() {
        try {
            mIsSystemRotationAllowed = Settings.System.getInt(mContentResolver,
                    Settings.System.ACCELEROMETER_ROTATION) == 1;
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "autorotate setting not found", e);
        }

        for (SystemRotationChangeListener listener : mSystemRotationChangeListeners) {
            listener.onSystemRotationChanged(mIsSystemRotationAllowed);
        }
    }

    private void updateHomeRotationSetting() {
        mIsHomeRotationAllowed = mSharedPrefs.getBoolean(ALLOW_ROTATION_PREFERENCE_KEY,
                mAllowConfigurationDefaultValue);
    }

    public void addSystemRotationChangeListener(SystemRotationChangeListener listener) {
        mSystemRotationChangeListeners.add(listener);
        listener.onSystemRotationChanged(mIsSystemRotationAllowed);
    }

    public void removeSystemRotationChangeListener(SystemRotationChangeListener listener) {
        mSystemRotationChangeListeners.remove(listener);
    }

    public void init() {
        mSharedPrefs.registerOnSharedPreferenceChangeListener(this);
        mContentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                false, mSystemAutoRotateObserver);
        updateAutoRotateSetting();
        updateHomeRotationSetting();
    }

    public void destroy() {
        mSharedPrefs.unregisterOnSharedPreferenceChangeListener(this);
        mContentResolver.unregisterContentObserver(mSystemAutoRotateObserver);
        mSystemRotationChangeListeners.clear();
    }

    @SurfaceRotation
    public int getDisplayRotation() {
        return mDisplayRotation;
    }

    @SurfaceRotation
    public int getTouchRotation() {
        return mTouchRotation;
    }

    @SurfaceRotation
    public int getLauncherRotation() {
        return mLauncherRotation;
    }

    public boolean areMultipleLayoutOrientationsDisabled() {
        return mDisableMultipleOrientations;
    }

    public boolean isSystemRotationAllowed() {
        return mIsSystemRotationAllowed;
    }

    public boolean isHomeRotationAllowed() {
        return mIsHomeRotationAllowed;
    }

    public boolean canLauncherRotate() {
        return isSystemRotationAllowed() && isHomeRotationAllowed();
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

    /**
     * Returns the scale and pivot so that the provided taskRect can fit the provided full size
     */
    public float getFullScreenScaleAndPivot(Rect taskView, DeviceProfile dp, PointF outPivot) {
        Rect insets = dp.getInsets();
        float fullWidth = dp.widthPx - insets.left - insets.right;
        float fullHeight = dp.heightPx - insets.top - insets.bottom;
        final float scale = LayoutUtils.getTaskScale(this,
                fullWidth, fullHeight, taskView.width(), taskView.height());

        if (scale == 1) {
            outPivot.set(fullWidth / 2, fullHeight / 2);
        } else {
            float factor = scale / (scale - 1);
            outPivot.set(taskView.left * factor, taskView.top * factor);
        }
        return scale;
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
        mapRectFromRotation(mDisplayRotation, src, screenWidth, screenHeight);
    }

    public void mapRectFromRotation(int rotation, RectF src, int screenWidth, int screenHeight) {
        mTmpMatrix.reset();
        postDisplayRotation(rotation, screenWidth, screenHeight, mTmpMatrix);
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

    public boolean isDisplayPhoneNatural() {
        return mDisplayRotation == Surface.ROTATION_0 || mDisplayRotation == Surface.ROTATION_180;
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
