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

import static android.view.OrientationEventListener.ORIENTATION_UNKNOWN;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import static com.android.launcher3.LauncherPrefs.ALLOW_ROTATION;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.SettingsCache.ROTATION_SETTING_URI;
import static com.android.quickstep.BaseActivityInterface.getTaskDimension;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.SettingsCache;
import com.android.quickstep.BaseContainerInterface;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskAnimationManager;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;

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
public class RecentsOrientedState implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "RecentsOrientedState";
    private static final boolean DEBUG = false;

    @Retention(SOURCE)
    @IntDef({ROTATION_0, ROTATION_90, ROTATION_180, ROTATION_270})
    public @interface SurfaceRotation {}

    private RecentsPagedOrientationHandler mOrientationHandler =
            RecentsPagedOrientationHandler.PORTRAIT;

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
    // Ignore shared prefs for home rotation rotation, allowing it in if the activity supports it
    private static final int FLAG_IGNORE_ALLOW_HOME_ROTATION_PREF = 1 << 9;

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
    private final BaseContainerInterface mContainerInterface;
    private final OrientationEventListener mOrientationListener;
    private final SettingsCache mSettingsCache;
    private final SettingsCache.OnChangeListener mRotationChangeListener =
            isEnabled -> updateAutoRotateSetting();

    private final Matrix mTmpMatrix = new Matrix();

    private int mFlags;
    private int mPreviousRotation = ROTATION_0;
    private boolean mListenersInitialized = false;

    // Combined int which encodes the full state.
    private int mStateId = 0;

    /**
     * @param rotationChangeListener Callback for receiving rotation events when rotation watcher
     *                              is enabled
     * @see #setRotationWatcherEnabled(boolean)
     */
    public RecentsOrientedState(Context context, BaseContainerInterface containerInterface,
            IntConsumer rotationChangeListener) {
        mContext = context;
        mContainerInterface = containerInterface;
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

        mFlags = mContainerInterface.rotationSupportedByActivity
                ? FLAG_MULTIPLE_ORIENTATION_SUPPORTED_BY_ACTIVITY : 0;

        mFlags |= FLAG_SWIPE_UP_NOT_RUNNING;
        mSettingsCache = SettingsCache.INSTANCE.get(mContext);
        initFlags();
    }

    public BaseContainerInterface getContainerInterface() {
        return mContainerInterface;
    }

    /**
     * Sets the device profile for the current state.
     */
    public void setDeviceProfile(DeviceProfile deviceProfile) {
        boolean oldMultipleOrientationsSupported = isMultipleOrientationSupportedByDevice();
        setFlag(FLAG_MULTIPLE_ORIENTATION_SUPPORTED_BY_DENSITY, !deviceProfile.isTablet);
        if (mListenersInitialized) {
            boolean newMultipleOrientationsSupported = isMultipleOrientationSupportedByDevice();
            // If isMultipleOrientationSupportedByDevice is changed, init or destroy listeners
            // accordingly.
            if (newMultipleOrientationsSupported != oldMultipleOrientationsSupported) {
                if (newMultipleOrientationsSupported) {
                    initMultipleOrientationListeners();
                } else {
                    destroyMultipleOrientationListeners();
                }
            }
        }
    }

    /**
     * Sets the rotation for the recents activity, which could affect the appearance of task view.
     * @see #update(int, int)
     */
    public boolean setRecentsRotation(@SurfaceRotation int recentsRotation) {
        mRecentsRotation = recentsRotation;
        return updateHandler();
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
        return setFlag(FLAG_SWIPE_UP_NOT_RUNNING, !isGestureActive);
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
        mDisplayRotation = displayRotation;
        mTouchRotation = touchRotation;
        mPreviousRotation = touchRotation;
        return updateHandler();
    }

    private boolean updateHandler() {
        mRecentsActivityRotation = inferRecentsActivityRotation(mDisplayRotation);
        if (mRecentsActivityRotation == mTouchRotation || isRecentsActivityRotationAllowed()) {
            mOrientationHandler = RecentsPagedOrientationHandler.PORTRAIT;
        } else if (mTouchRotation == ROTATION_90) {
            mOrientationHandler = RecentsPagedOrientationHandler.LANDSCAPE;
        } else if (mTouchRotation == ROTATION_270) {
            mOrientationHandler = RecentsPagedOrientationHandler.SEASCAPE;
        } else {
            mOrientationHandler = RecentsPagedOrientationHandler.PORTRAIT;
        }
        if (DEBUG) {
            Log.d(TAG, "current RecentsOrientedState: " + this);
        }

        int oldStateId = mStateId;
        // Each SurfaceRotation value takes two bits
        mStateId = (((((mFlags << 2)
                | mDisplayRotation) << 2)
                | mTouchRotation) << 3)
                | (mRecentsRotation < 0 ? 7 : mRecentsRotation);
        return mStateId != oldStateId;
    }

    @SurfaceRotation
    private int inferRecentsActivityRotation(@SurfaceRotation int displayRotation) {
        if (isRecentsActivityRotationAllowed()) {
            return mRecentsRotation < 0 ? displayRotation : mRecentsRotation;
        } else {
            return ROTATION_0;
        }
    }

    private boolean setFlag(int mask, boolean enabled) {
        boolean wasRotationEnabled = !TestProtocol.sDisableSensorRotation
                && (mFlags & VALUE_ROTATION_WATCHER_ENABLED) == VALUE_ROTATION_WATCHER_ENABLED
                && !isRecentsActivityRotationAllowed();
        if (enabled) {
            mFlags |= mask;
        } else {
            mFlags &= ~mask;
        }

        boolean isRotationEnabled = !TestProtocol.sDisableSensorRotation
                && (mFlags & VALUE_ROTATION_WATCHER_ENABLED) == VALUE_ROTATION_WATCHER_ENABLED
                && !isRecentsActivityRotationAllowed();
        if (wasRotationEnabled != isRotationEnabled) {
            UI_HELPER_EXECUTOR.execute(() -> {
                if (isRotationEnabled) {
                    mOrientationListener.enable();
                } else {
                    mOrientationListener.disable();
                }
            });
        }
        return updateHandler();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (LauncherPrefs.ALLOW_ROTATION.getSharedPrefKey().equals(s)) {
            updateHomeRotationSetting();
        }
    }

    private void updateAutoRotateSetting() {
        setFlag(FLAG_SYSTEM_ROTATION_ALLOWED,
                mSettingsCache.getValue(ROTATION_SETTING_URI, 1));
    }

    private void updateHomeRotationSetting() {
        boolean homeRotationEnabled = LauncherPrefs.get(mContext).get(ALLOW_ROTATION);
        setFlag(FLAG_HOME_ROTATION_ALLOWED_IN_PREFS, homeRotationEnabled);
        SystemUiProxy.INSTANCE.get(mContext).setHomeRotationEnabled(homeRotationEnabled);
    }

    private void initFlags() {
        setFlag(FLAG_ROTATION_WATCHER_SUPPORTED, mOrientationListener.canDetectOrientation());

        // initialize external flags
        updateAutoRotateSetting();
        updateHomeRotationSetting();
    }

    private void initMultipleOrientationListeners() {
        LauncherPrefs.get(mContext).addListener(this, ALLOW_ROTATION);
        mSettingsCache.register(ROTATION_SETTING_URI, mRotationChangeListener);
        updateAutoRotateSetting();
    }

    private void destroyMultipleOrientationListeners() {
        LauncherPrefs.get(mContext).removeListener(this, ALLOW_ROTATION);
        mSettingsCache.unregister(ROTATION_SETTING_URI, mRotationChangeListener);
    }

    /**
     * Initializes any system values and registers corresponding change listeners. It must be
     * paired with {@link #destroyListeners()} call
     */
    public void initListeners() {
        mListenersInitialized = true;
        if (isMultipleOrientationSupportedByDevice()) {
            initMultipleOrientationListeners();
        }
        initFlags();
    }

    /**
     * Unregisters any previously registered listeners.
     */
    public void destroyListeners() {
        mListenersInitialized = false;
        if (isMultipleOrientationSupportedByDevice()) {
            destroyMultipleOrientationListeners();
        }
        setRotationWatcherEnabled(false);
    }

    public void forceAllowRotationForTesting(boolean forceAllow) {
        setFlag(FLAG_HOME_ROTATION_FORCE_ENABLED_FOR_TESTING, forceAllow);
    }

    @SurfaceRotation
    public int getDisplayRotation() {
        if (TaskAnimationManager.SHELL_TRANSITIONS_ROTATION) {
            // When shell transitions are enabled, both the display and activity rotations should
            // be the same once the gesture starts
            return mRecentsActivityRotation;
        }
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

    /**
     * Returns an id that can be used to tracking internal changes
     */
    public int getStateId() {
        return mStateId;
    }

    public boolean isMultipleOrientationSupportedByDevice() {
        return (mFlags & MASK_MULTIPLE_ORIENTATION_SUPPORTED_BY_DEVICE)
                == MASK_MULTIPLE_ORIENTATION_SUPPORTED_BY_DEVICE;
    }

    public void ignoreAllowHomeRotationPreference() {
        setFlag(FLAG_IGNORE_ALLOW_HOME_ROTATION_PREF, true);
    }

    public boolean isRecentsActivityRotationAllowed() {
        // Activity rotation is allowed if the multi-simulated-rotation is not supported
        // (fallback recents or tablets) or activity rotation is enabled by various settings.
        return ((mFlags & MASK_MULTIPLE_ORIENTATION_SUPPORTED_BY_DEVICE)
                != MASK_MULTIPLE_ORIENTATION_SUPPORTED_BY_DEVICE)
                || (mFlags & (FLAG_IGNORE_ALLOW_HOME_ROTATION_PREF
                        | FLAG_HOME_ROTATION_ALLOWED_IN_PREFS
                        | FLAG_MULTIWINDOW_ROTATION_ALLOWED
                        | FLAG_HOME_ROTATION_FORCE_ENABLED_FOR_TESTING)) != 0;
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
        getTaskDimension(mContext, dp, outPivot);
        float scale = Math.min(outPivot.x / taskView.width(), outPivot.y / taskView.height());
        if (scale == 1) {
            outPivot.set(taskView.centerX(), taskView.centerY());
        } else {
            float factor = scale / (scale - 1);
            outPivot.set(taskView.left * factor, taskView.top * factor);
        }
        return scale;
    }

    public RecentsPagedOrientationHandler getOrientationHandler() {
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

    /**
     * Contrary to {@link #postDisplayRotation}.
     */
    public static void preDisplayRotation(@SurfaceRotation int displayRotation,
            float screenWidth, float screenHeight, Matrix out) {
        switch (displayRotation) {
            case ROTATION_0:
                return;
            case ROTATION_90:
                out.postRotate(90);
                out.postTranslate(screenWidth, 0);
                break;
            case ROTATION_180:
                out.postRotate(180);
                out.postTranslate(screenHeight, screenWidth);
                break;
            case ROTATION_270:
                out.postRotate(270);
                out.postTranslate(0, screenHeight);
                break;
        }
    }

    @NonNull
    @Override
    public String toString() {
        boolean systemRotationOn = (mFlags & FLAG_SYSTEM_ROTATION_ALLOWED) != 0;
        return "["
                + "this=" + nameAndAddress(this)
                + " mOrientationHandler=" + nameAndAddress(mOrientationHandler)
                + " mDisplayRotation=" + mDisplayRotation
                + " mTouchRotation=" + mTouchRotation
                + " mRecentsActivityRotation=" + mRecentsActivityRotation
                + " mRecentsRotation=" + mRecentsRotation
                + " isRecentsActivityRotationAllowed=" + isRecentsActivityRotationAllowed()
                + " mSystemRotation=" + systemRotationOn
                + " mStateId=" + mStateId
                + " mFlags=" + mFlags
                + "]";
    }

    /**
     * Returns the device profile based on expected launcher rotation
     */
    public DeviceProfile getLauncherDeviceProfile() {
        InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(mContext);
        Point currentSize = DisplayController.INSTANCE.get(mContext).getInfo().currentSize;

        int width, height;
        if ((mRecentsActivityRotation == ROTATION_90 || mRecentsActivityRotation == ROTATION_270)) {
            width = Math.max(currentSize.x, currentSize.y);
            height = Math.min(currentSize.x, currentSize.y);
        } else {
            width = Math.min(currentSize.x, currentSize.y);
            height = Math.max(currentSize.x, currentSize.y);
        }
        return idp.getBestMatch(width, height, mRecentsActivityRotation);
    }

    private static String nameAndAddress(Object obj) {
        return obj.getClass().getSimpleName() + "@" + obj.hashCode();
    }
}
