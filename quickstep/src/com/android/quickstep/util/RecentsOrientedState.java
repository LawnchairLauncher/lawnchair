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

import static com.android.launcher3.logging.LoggerUtils.extractObjectNameAndAddress;
import static com.android.launcher3.states.RotationHelper.ALLOW_ROTATION_PREFERENCE_KEY;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.quickstep.SysUINavigationMode.Mode.TWO_BUTTONS;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.util.WindowBounds;
import com.android.quickstep.BaseActivityInterface;
import com.android.quickstep.SysUINavigationMode;

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
    private @SurfaceRotation int mRecentsActivityRotation = ROTATION_0;
    private @SurfaceRotation int mRecentsRotation = ROTATION_0 - 1;

    // Launcher activity supports multiple orientation, but fallback activity does not
    private static final int FLAG_MULTIPLE_ORIENTATION_SUPPORTED_BY_ACTIVITY = 1 << 0;
    // Multiple orientation is only supported if density is < 600
    private static final int FLAG_MULTIPLE_ORIENTATION_SUPPORTED_BY_DENSITY = 1 << 1;
    // Shared prefs for rotation, only if activity supports it
    private static final int FLAG_HOME_ROTATION_ALLOWED_IN_PREFS = 1 << 2;
    // If the user has enabled system rotation
    private static final int FLAG_SYSTEM_ROTATION_ALLOWED = 1 << 3;
    // Multiple orientation is not supported in multiwindow mode
    private static final int FLAG_MULTIWINDOW_ROTATION_ALLOWED = 1 << 4;
    // Whether to rotation sensor is supported on the device
    private static final int FLAG_ROTATION_WATCHER_SUPPORTED = 1 << 5;
    // Whether to enable rotation watcher when multi-rotation is supported
    private static final int FLAG_ROTATION_WATCHER_ENABLED = 1 << 6;
    // Enable home rotation for UI tests, ignoring home rotation value from prefs
    private static final int FLAG_HOME_ROTATION_FORCE_ENABLED_FOR_TESTING = 1 << 7;
    // Whether the swipe gesture is running, so the recents would stay locked in the
    // current orientation
    private static final int FLAG_SWIPE_UP_NOT_RUNNING = 1 << 8;

    private static final int MASK_MULTIPLE_ORIENTATION_SUPPORTED_BY_DEVICE =
            FLAG_MULTIPLE_ORIENTATION_SUPPORTED_BY_ACTIVITY
            | FLAG_MULTIPLE_ORIENTATION_SUPPORTED_BY_DENSITY;

    // State for which rotation watcher will be enabled. We skip it when home rotation or
    // multi-window is enabled as in that case, activity itself rotates.
    private static final int VALUE_ROTATION_WATCHER_ENABLED =
            MASK_MULTIPLE_ORIENTATION_SUPPORTED_BY_DEVICE | FLAG_SYSTEM_ROTATION_ALLOWED
                    | FLAG_ROTATION_WATCHER_SUPPORTED | FLAG_ROTATION_WATCHER_ENABLED
                    | FLAG_SWIPE_UP_NOT_RUNNING;

    private final Context mContext;
    private final ContentResolver mContentResolver;
    private final SharedPreferences mSharedPrefs;
    private final OrientationEventListener mOrientationListener;

    private final Matrix mTmpMatrix = new Matrix();

    private int mFlags;
    private int mPreviousRotation = ROTATION_0;

    /**
     * @param rotationChangeListener Callback for receiving rotation events when rotation watcher
     *                              is enabled
     * @see #setRotationWatcherEnabled(boolean)
     */
    public RecentsOrientedState(Context context, BaseActivityInterface sizeStrategy,
            IntConsumer rotationChangeListener) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mSharedPrefs = Utilities.getPrefs(context);
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
        mFlags |= FLAG_SWIPE_UP_NOT_RUNNING;
        initFlags();
    }

    /**
     * Sets the rotation for the recents activity, which could affect the appearance of task view.
     * @see #update(int, int)
     */
    public boolean setRecentsRotation(@SurfaceRotation int recentsRotation) {
        mRecentsRotation = recentsRotation;
        return update(mTouchRotation, mDisplayRotation);
    }

    /**
     * Sets if the host is in multi-window mode
     */
    public void setMultiWindowMode(boolean isMultiWindow) {
        setFlag(FLAG_MULTIWINDOW_ROTATION_ALLOWED, isMultiWindow);
    }

    /**
     * Sets if the swipe up gesture is currently running or not
     */
    public boolean setGestureActive(boolean isGestureActive) {
        setFlag(FLAG_SWIPE_UP_NOT_RUNNING, !isGestureActive);
        return update(mTouchRotation, mDisplayRotation);
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
            @SurfaceRotation int touchRotation, @SurfaceRotation int displayRotation) {
        mRecentsActivityRotation = inferRecentsActivityRotation(displayRotation);
        mDisplayRotation = displayRotation;
        mTouchRotation = touchRotation;
        mPreviousRotation = touchRotation;

        PagedOrientationHandler oldHandler = mOrientationHandler;
        if (mRecentsActivityRotation == mTouchRotation
                || (canRecentsActivityRotate() && (mFlags & FLAG_SWIPE_UP_NOT_RUNNING) != 0)) {
            mOrientationHandler = PagedOrientationHandler.PORTRAIT;
            if (DEBUG) {
                Log.d(TAG, "current RecentsOrientedState: " + this);
            }
        } else if (mTouchRotation == ROTATION_90) {
            mOrientationHandler = PagedOrientationHandler.LANDSCAPE;
        } else if (mTouchRotation == ROTATION_270) {
            mOrientationHandler = PagedOrientationHandler.SEASCAPE;
        } else {
            mOrientationHandler = PagedOrientationHandler.PORTRAIT;
        }
        if (DEBUG) {
            Log.d(TAG, "current RecentsOrientedState: " + this);
        }
        return oldHandler != mOrientationHandler;
    }

    @SurfaceRotation
    private int inferRecentsActivityRotation(@SurfaceRotation int displayRotation) {
        if (isRecentsActivityRotationAllowed()) {
            return mRecentsRotation < ROTATION_0 ? displayRotation : mRecentsRotation;
        } else {
            return ROTATION_0;
        }
    }

    private void setFlag(int mask, boolean enabled) {
        boolean wasRotationEnabled = !TestProtocol.sDisableSensorRotation
                && (mFlags & VALUE_ROTATION_WATCHER_ENABLED) == VALUE_ROTATION_WATCHER_ENABLED
                && !canRecentsActivityRotate();
        if (enabled) {
            mFlags |= mask;
        } else {
            mFlags &= ~mask;
        }

        boolean isRotationEnabled = !TestProtocol.sDisableSensorRotation
                && (mFlags & VALUE_ROTATION_WATCHER_ENABLED) == VALUE_ROTATION_WATCHER_ENABLED
                && !canRecentsActivityRotate();
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
        if (ALLOW_ROTATION_PREFERENCE_KEY.equals(s)) {
            updateHomeRotationSetting();
        }
    }

    private void updateAutoRotateSetting() {
        setFlag(FLAG_SYSTEM_ROTATION_ALLOWED, Settings.System.getInt(mContentResolver,
                Settings.System.ACCELEROMETER_ROTATION, 1) == 1);
    }

    private void updateHomeRotationSetting() {
        setFlag(FLAG_HOME_ROTATION_ALLOWED_IN_PREFS,
                mSharedPrefs.getBoolean(ALLOW_ROTATION_PREFERENCE_KEY, false));
    }

    private void initFlags() {
        SysUINavigationMode.Mode currentMode = SysUINavigationMode.getMode(mContext);
        boolean rotationWatcherSupported = mOrientationListener.canDetectOrientation() &&
                currentMode != TWO_BUTTONS;
        setFlag(FLAG_ROTATION_WATCHER_SUPPORTED, rotationWatcherSupported);

        // initialize external flags
        updateAutoRotateSetting();
        updateHomeRotationSetting();
    }

    /**
     * Initializes any system values and registers corresponding change listeners. It must be
     * paired with {@link #destroyListeners()} call
     */
    public void initListeners() {
        if (isMultipleOrientationSupportedByDevice()) {
            mSharedPrefs.registerOnSharedPreferenceChangeListener(this);
            mContentResolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                    false, mSystemAutoRotateObserver);
        }
        initFlags();
    }

    /**
     * Unregisters any previously registered listeners.
     */
    public void destroyListeners() {
        if (isMultipleOrientationSupportedByDevice()) {
            mSharedPrefs.unregisterOnSharedPreferenceChangeListener(this);
            mContentResolver.unregisterContentObserver(mSystemAutoRotateObserver);
        }
        setRotationWatcherEnabled(false);
    }

    public void forceAllowRotationForTesting(boolean forceAllow) {
        setFlag(FLAG_HOME_ROTATION_FORCE_ENABLED_FOR_TESTING, forceAllow);
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
    public int getRecentsActivityRotation() {
        return mRecentsActivityRotation;
    }

    public boolean isMultipleOrientationSupportedByDevice() {
        return (mFlags & MASK_MULTIPLE_ORIENTATION_SUPPORTED_BY_DEVICE)
                == MASK_MULTIPLE_ORIENTATION_SUPPORTED_BY_DEVICE;
    }

    public boolean isRecentsActivityRotationAllowed() {
        // Activity rotation is allowed if the multi-simulated-rotation is not supported
        // (fallback recents or tablets) or activity rotation is enabled by various settings.
        return ((mFlags & MASK_MULTIPLE_ORIENTATION_SUPPORTED_BY_DEVICE)
                != MASK_MULTIPLE_ORIENTATION_SUPPORTED_BY_DEVICE)
                || (mFlags & (FLAG_HOME_ROTATION_ALLOWED_IN_PREFS
                        | FLAG_MULTIWINDOW_ROTATION_ALLOWED
                        | FLAG_HOME_ROTATION_FORCE_ENABLED_FOR_TESTING)) != 0;
    }

    /**
     * Returns true if the activity can rotate, if allowed by system rotation settings
     */
    public boolean canRecentsActivityRotate() {
        return (mFlags & FLAG_SYSTEM_ROTATION_ALLOWED) != 0 && isRecentsActivityRotationAllowed();
    }

    /**
     * Enables or disables the rotation watcher for listening to rotation callbacks
     */
    public void setRotationWatcherEnabled(boolean isEnabled) {
        setFlag(FLAG_ROTATION_WATCHER_ENABLED, isEnabled);
    }

    /**
     * Returns the scale and pivot so that the provided taskRect can fit the provided full size
     */
    public float getFullScreenScaleAndPivot(Rect taskView, DeviceProfile dp, PointF outPivot) {
        Rect insets = dp.getInsets();
        float fullWidth = dp.widthPx - insets.left - insets.right;
        float fullHeight = dp.heightPx - insets.top - insets.bottom;

        if (dp.isMultiWindowMode) {
            WindowBounds bounds = SplitScreenBounds.INSTANCE.getSecondaryWindowBounds(mContext);
            outPivot.set(bounds.availableSize.x, bounds.availableSize.y);
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
                if (degrees < (90 - threshold) ||
                        (degrees > (270 + threshold) && degrees < 360)) {
                    return ROTATION_0;
                }
                if (degrees > (90 + threshold) && degrees < 180) {
                    return ROTATION_180;
                }
                // flip from seascape to landscape
                if (degrees > (180 + threshold) && degrees < 360) {
                    return ROTATION_90;
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
                if (degrees < (270 - threshold) && degrees > 90) {
                    return ROTATION_180;
                }
                if (degrees > (270 + threshold) && degrees < 360
                        || (degrees >= 0 && degrees < threshold)) {
                    return ROTATION_0;
                }
                // flip from landscape to seascape
                if (degrees > threshold && degrees < 180) {
                    return ROTATION_270;
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

    @NonNull
    @Override
    public String toString() {
        boolean systemRotationOn = (mFlags & FLAG_SYSTEM_ROTATION_ALLOWED) != 0;
        return "["
                + "this=" + extractObjectNameAndAddress(super.toString())
                + " mOrientationHandler=" +
                    extractObjectNameAndAddress(mOrientationHandler.toString())
                + " mDisplayRotation=" + mDisplayRotation
                + " mTouchRotation=" + mTouchRotation
                + " mRecentsActivityRotation=" + mRecentsActivityRotation
                + " isRecentsActivityRotationAllowed=" + isRecentsActivityRotationAllowed()
                + " mSystemRotation=" + systemRotationOn
                + " mFlags=" + mFlags
                + "]";
    }

    /**
     * Returns the device profile based on expected launcher rotation
     */
    public DeviceProfile getLauncherDeviceProfile() {
        InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(mContext);
        // TODO also check the natural orientation is landscape or portrait
        return  (mRecentsActivityRotation == ROTATION_90
                || mRecentsActivityRotation == ROTATION_270)
                ? idp.landscapeProfile
                : idp.portraitProfile;
    }
}
