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

import static android.util.DisplayMetrics.DENSITY_DEVICE_STABLE;
import static android.view.OrientationEventListener.ORIENTATION_UNKNOWN;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import static com.android.launcher3.states.RotationHelper.ALLOW_ROTATION_PREFERENCE_KEY;
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
import android.view.OrientationEventListener;
import android.view.Surface;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.touch.PagedOrientationHandler;

import java.lang.annotation.Retention;
import java.util.function.IntConsumer;

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
    private static final boolean DEBUG = true;

    private static final String FIXED_ROTATION_TRANSFORM_SETTING_NAME = "fixed_rotation_transform";

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

    // Launcher activity supports multiple orientation, but fallback activity does not
    private static final int FLAG_MULTIPLE_ORIENTATION_SUPPORTED_BY_ACTIVITY = 1 << 0;
    // Multiple orientation is only supported if density is < 600
    private static final int FLAG_MULTIPLE_ORIENTATION_SUPPORTED_BY_DENSITY = 1 << 1;
    // Feature flag controlling the multi-orientation feature
    private static final int FLAG_MULTIPLE_ORIENTATION_SUPPORTED_BY_FLAG = 1 << 2;
    // Shared prefs for rotation, only if activity supports it
    private static final int FLAG_HOME_ROTATION_ALLOWED_IN_PREFS = 1 << 3;
    // If the user has enabled system rotation
    private static final int FLAG_SYSTEM_ROTATION_ALLOWED = 1 << 4;
    // Multiple orientation is not supported in multiwindow mode
    private static final int FLAG_MULTIWINDOW_ROTATION_ALLOWED = 1 << 5;
    // Whether to rotation sensor is supported on the device
    private static final int FLAG_ROTATION_WATCHER_SUPPORTED = 1 << 6;
    // Whether to enable rotation watcher when multi-rotation is supported
    private static final int FLAG_ROTATION_WATCHER_ENABLED = 1 << 7;

    private static final int MASK_MULTIPLE_ORIENTATION_SUPPORTED_BY_DEVICE =
            FLAG_MULTIPLE_ORIENTATION_SUPPORTED_BY_ACTIVITY
            | FLAG_MULTIPLE_ORIENTATION_SUPPORTED_BY_DENSITY
            | FLAG_MULTIPLE_ORIENTATION_SUPPORTED_BY_FLAG;

    // State for which rotation watcher will be enabled. We skip it when home rotation or
    // multi-window is enabled as in that case, activity itself rotates.
    private static final int VALUE_ROTATION_WATCHER_ENABLED =
            MASK_MULTIPLE_ORIENTATION_SUPPORTED_BY_DEVICE | FLAG_SYSTEM_ROTATION_ALLOWED
                    | FLAG_ROTATION_WATCHER_SUPPORTED | FLAG_ROTATION_WATCHER_ENABLED;

    private final Context mContext;
    private final ContentResolver mContentResolver;
    private final SharedPreferences mSharedPrefs;
    private final OrientationEventListener mOrientationListener;
    private final WindowSizeStrategy mSizeStrategy;

    private final Matrix mTmpMatrix = new Matrix();
    private final Matrix mTmpInverseMatrix = new Matrix();

    private int mFlags;
    private int mPreviousRotation = ROTATION_0;

    /**
     * @param rotationChangeListener Callback for receiving rotation events when rotation watcher
     *                              is enabled
     * @see #setRotationWatcherEnabled(boolean)
     */
    public RecentsOrientedState(Context context, WindowSizeStrategy sizeStrategy,
            IntConsumer rotationChangeListener) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mSharedPrefs = Utilities.getPrefs(context);
        mSizeStrategy = sizeStrategy;
        mOrientationListener = new OrientationEventListener(context) {
            @Override
            public void onOrientationChanged(int degrees) {
                int newRotation = getRotationForUserDegreesRotated(degrees, mPreviousRotation);
                if (newRotation != mPreviousRotation) {
                    mPreviousRotation = newRotation;
                    rotationChangeListener.accept(newRotation);
                }
            }
        };

        mFlags = sizeStrategy.rotationSupportedByActivity
                ? FLAG_MULTIPLE_ORIENTATION_SUPPORTED_BY_ACTIVITY : 0;

        Resources res = context.getResources();
        int originalSmallestWidth = res.getConfiguration().smallestScreenWidthDp
                * res.getDisplayMetrics().densityDpi / DENSITY_DEVICE_STABLE;
        if (originalSmallestWidth < 600) {
            mFlags |= FLAG_MULTIPLE_ORIENTATION_SUPPORTED_BY_DENSITY;
        }
        if (isFixedRotationTransformEnabled(context)) {
            mFlags |= FLAG_MULTIPLE_ORIENTATION_SUPPORTED_BY_FLAG;
        }
        if (mOrientationListener.canDetectOrientation()) {
            mFlags |= FLAG_ROTATION_WATCHER_SUPPORTED;
        }
    }

    /**
     * Sets if the host is in multi-window mode
     */
    public void setMultiWindowMode(boolean isMultiWindow) {
        setFlag(FLAG_MULTIWINDOW_ROTATION_ALLOWED, isMultiWindow);
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
        if (!isMultipleOrientationSupportedByDevice()) {
            return false;
        }
        if (mDisplayRotation == displayRotation && mTouchRotation == touchRotation
                && launcherRotation == mLauncherRotation) {
            return false;
        }

        mLauncherRotation = launcherRotation;
        mDisplayRotation = displayRotation;
        mTouchRotation = touchRotation;

        if (canLauncherRotate() || mLauncherRotation == mTouchRotation) {
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

    private void setFlag(int mask, boolean enabled) {
        boolean wasRotationEnabled = !TestProtocol.sDisableSensorRotation
                && mFlags == VALUE_ROTATION_WATCHER_ENABLED;
        if (enabled) {
            mFlags |= mask;
        } else {
            mFlags &= ~mask;
        }

        boolean isRotationEnabled = !TestProtocol.sDisableSensorRotation
                && mFlags == VALUE_ROTATION_WATCHER_ENABLED;
        if (wasRotationEnabled != isRotationEnabled) {
            UI_HELPER_EXECUTOR.execute(() -> {
                if (isRotationEnabled) {
                    mOrientationListener.enable();
                } else {
                    mOrientationListener.disable();
                }
            });
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        updateHomeRotationSetting();
    }

    private void updateAutoRotateSetting() {
        setFlag(FLAG_SYSTEM_ROTATION_ALLOWED, Settings.System.getInt(mContentResolver,
                Settings.System.ACCELEROMETER_ROTATION, 1) == 1);
    }

    private void updateHomeRotationSetting() {
        setFlag(FLAG_HOME_ROTATION_ALLOWED_IN_PREFS,
                mSharedPrefs.getBoolean(ALLOW_ROTATION_PREFERENCE_KEY, false));
    }

    /**
     * Initializes aany system values and registers corresponding change listeners. It must be
     * paired with {@link #destroy()} call
     */
    public void init() {
        if (isMultipleOrientationSupportedByDevice()) {
            mSharedPrefs.registerOnSharedPreferenceChangeListener(this);
            mContentResolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                    false, mSystemAutoRotateObserver);
        }
        initWithoutListeners();
    }

    /**
     * Unregisters any previously registered listeners.
     */
    public void destroy() {
        if (isMultipleOrientationSupportedByDevice()) {
            mSharedPrefs.unregisterOnSharedPreferenceChangeListener(this);
            mContentResolver.unregisterContentObserver(mSystemAutoRotateObserver);
        }
        setRotationWatcherEnabled(false);
    }

    /**
     * Initializes the OrientationState without attaching any listeners. This can be used when
     * the object is short lived.
     */
    public void initWithoutListeners() {
        updateAutoRotateSetting();
        updateHomeRotationSetting();
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

    public boolean isMultipleOrientationSupportedByDevice() {
        return (mFlags & MASK_MULTIPLE_ORIENTATION_SUPPORTED_BY_DEVICE)
                == MASK_MULTIPLE_ORIENTATION_SUPPORTED_BY_DEVICE;
    }

    public boolean isHomeRotationAllowed() {
        return (mFlags & (FLAG_HOME_ROTATION_ALLOWED_IN_PREFS | FLAG_MULTIWINDOW_ROTATION_ALLOWED))
                != 0;
    }

    public boolean canLauncherRotate() {
        return (mFlags & FLAG_SYSTEM_ROTATION_ALLOWED) != 0 && isHomeRotationAllowed();
    }

    /**
     * Enables or disables the rotation watcher for listening to rotation callbacks
     */
    public void setRotationWatcherEnabled(boolean isEnabled) {
        setFlag(FLAG_ROTATION_WATCHER_ENABLED, isEnabled);
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

        if (dp.isMultiWindowMode) {
            mSizeStrategy.getMultiWindowSize(mContext, dp, outPivot);
        } else {
            outPivot.set(fullWidth, fullHeight);
        }
        float scale = Math.min(outPivot.x / taskView.width(), outPivot.y / taskView.height());
        // We also scale the preview as part of fullScreenParams, so account for that as well.
        if (fullWidth > 0) {
            scale = scale * dp.widthPx / fullWidth;
        }

        if (scale == 1) {
            outPivot.set(fullWidth / 2, fullHeight / 2);
        } else if (dp.isMultiWindowMode) {
            float denominator = 1 / (scale - 1);
            // Ensure that the task aligns to right bottom for the root view
            float y = (scale * taskView.bottom - fullHeight) * denominator;
            float x = (scale * taskView.right - fullWidth) * denominator;
            outPivot.set(x, y);
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
    public static int getRotationForUserDegreesRotated(float degrees, int currentRotation) {
        if (degrees == ORIENTATION_UNKNOWN) {
            return currentRotation;
        }

        int threshold = 70;
        switch (currentRotation) {
            case ROTATION_0:
                if (degrees > 180 && degrees < (360 - threshold)) {
                    return ROTATION_90;
                }
                if (degrees < 180 && degrees > threshold) {
                    return ROTATION_270;
                }
                break;
            case ROTATION_270:
                if (degrees < (90 - threshold)) {
                    return ROTATION_0;
                }
                if (degrees > (90 + threshold)) {
                    return ROTATION_180;
                }
                break;
            case ROTATION_180:
                if (degrees < (180 - threshold)) {
                    return ROTATION_270;
                }
                if (degrees > (180 + threshold)) {
                    return ROTATION_90;
                }
                break;
            case ROTATION_90:
                if (degrees < (270 - threshold)) {
                    return ROTATION_180;
                }
                if (degrees > (270 + threshold)) {
                    return ROTATION_0;
                }
                break;
        }

        return currentRotation;
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

    /**
     * Returns true if system can keep Launcher fixed to portrait layout even if the
     * foreground app is rotated
     */
    public static boolean isFixedRotationTransformEnabled(Context context) {
        return Settings.Global.getInt(
                context.getContentResolver(), FIXED_ROTATION_TRANSFORM_SETTING_NAME, 1) == 1;
    }

    @NonNull
    @Override
    public String toString() {
        boolean systemRotationOn = (mFlags & FLAG_SYSTEM_ROTATION_ALLOWED) != 0;
        return "["
                + "mDisplayRotation=" + mDisplayRotation
                + " mTouchRotation=" + mTouchRotation
                + " mLauncherRotation=" + mLauncherRotation
                + " mHomeRotation=" + isHomeRotationAllowed()
                + " mSystemRotation=" + systemRotationOn
                + " mFlags=" + mFlags
                + " mOrientationHandler=" + mOrientationHandler
                + "]";
    }
}
