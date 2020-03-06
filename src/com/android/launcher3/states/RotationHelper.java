/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.states;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
import static android.util.DisplayMetrics.DENSITY_DEVICE_STABLE;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.WindowManager;

import com.android.launcher3.Launcher;
import com.android.launcher3.PagedView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.UiThreadHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to manage launcher rotation
 */
public class RotationHelper implements OnSharedPreferenceChangeListener {

    public static final String ALLOW_ROTATION_PREFERENCE_KEY = "pref_allowRotation";

    public static final String FIXED_ROTATION_TRANSFORM_SETTING_NAME = "fixed_rotation_transform";
    private final ContentResolver mContentResolver;

    /**
     * Listener to receive changes when {@link #FIXED_ROTATION_TRANSFORM_SETTING_NAME} flag changes.
     */
    public interface ForcedRotationChangedListener {
        void onForcedRotationChanged(boolean isForcedRotation);
    }

    public static boolean getAllowRotationDefaultValue() {
        // If the device's pixel density was scaled (usually via settings for A11y), use the
        // original dimensions to determine if rotation is allowed of not.
        Resources res = Resources.getSystem();
        int originalSmallestWidth = res.getConfiguration().smallestScreenWidthDp
                * res.getDisplayMetrics().densityDpi / DENSITY_DEVICE_STABLE;
        return originalSmallestWidth >= 600;
    }


    private final ContentObserver mContentObserver =
        new ContentObserver(MAIN_EXECUTOR.getHandler()) {
            @Override
            public void onChange(boolean selfChange) {
                boolean forcedRotation = Utilities.isForcedRotation(mLauncher);
                PagedView.sFlagForcedRotation = forcedRotation;
                updateForcedRotation();
                for (ForcedRotationChangedListener listener : mForcedRotationChangedListeners) {
                    listener.onForcedRotationChanged(forcedRotation);
                }
            }
        };

    public static final int REQUEST_NONE = 0;
    public static final int REQUEST_ROTATE = 1;
    public static final int REQUEST_LOCK = 2;

    private final Launcher mLauncher;
    private final SharedPreferences mPrefs;

    private boolean mIgnoreAutoRotateSettings;
    private boolean mAutoRotateEnabled;
    private boolean mForcedRotation;
    private List<ForcedRotationChangedListener> mForcedRotationChangedListeners = new ArrayList<>();

    /**
     * Rotation request made by
     * {@link com.android.launcher3.util.ActivityTracker.SchedulerCallback}.
     * This supersedes any other request.
     */
    private int mStateHandlerRequest = REQUEST_NONE;
    /**
     * Rotation request made by an app transition
     */
    private int mCurrentTransitionRequest = REQUEST_NONE;
    /**
     * Rotation request made by a Launcher State
     */
    private int mCurrentStateRequest = REQUEST_NONE;

    // This is used to defer setting rotation flags until the activity is being created
    private boolean mInitialized;
    private boolean mDestroyed;
    private boolean mRotationHasDifferentUI;

    private int mLastActivityFlags = -1;

    public RotationHelper(Launcher launcher) {
        mLauncher = launcher;

        // On large devices we do not handle auto-rotate differently.
        mIgnoreAutoRotateSettings = mLauncher.getResources().getBoolean(R.bool.allow_rotation);
        updateForcedRotation();
        if (!mIgnoreAutoRotateSettings) {
            mPrefs = Utilities.getPrefs(mLauncher);
            mPrefs.registerOnSharedPreferenceChangeListener(this);
            mAutoRotateEnabled = mPrefs.getBoolean(ALLOW_ROTATION_PREFERENCE_KEY,
                    getAllowRotationDefaultValue());
        } else {
            mPrefs = null;
        }

        // TODO(b/150260456) Add this in home settings as well
        mContentResolver = launcher.getContentResolver();
        mContentResolver.registerContentObserver(Settings.Global.getUriFor(
            FIXED_ROTATION_TRANSFORM_SETTING_NAME), false, mContentObserver);
    }

    private void updateForcedRotation() {
        mForcedRotation = !getAllowRotationDefaultValue() && Utilities.isForcedRotation(mLauncher);
    }

    /**
     * will not be called when first registering the listener.
     */
    public void addForcedRotationCallback(ForcedRotationChangedListener listener) {
        mForcedRotationChangedListeners.add(listener);
    }

    public void removeForcedRotationCallback(ForcedRotationChangedListener listener) {
        mForcedRotationChangedListeners.remove(listener);
    }

    public void setRotationHadDifferentUI(boolean rotationHasDifferentUI) {
        mRotationHasDifferentUI = rotationHasDifferentUI;
    }

    public boolean homeScreenCanRotate() {
        return mRotationHasDifferentUI || mIgnoreAutoRotateSettings || mAutoRotateEnabled
                || mStateHandlerRequest != REQUEST_NONE
                || mLauncher.getDeviceProfile().isMultiWindowMode;
    }

    public void updateRotationAnimation() {
        if (FeatureFlags.FAKE_LANDSCAPE_UI.get()) {
            WindowManager.LayoutParams lp = mLauncher.getWindow().getAttributes();
            int oldAnim = lp.rotationAnimation;
            lp.rotationAnimation = homeScreenCanRotate()
                    ? WindowManager.LayoutParams.ROTATION_ANIMATION_ROTATE
                    : WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS;
            if (oldAnim != lp.rotationAnimation) {
                mLauncher.getWindow().setAttributes(lp);
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        boolean wasRotationEnabled = mAutoRotateEnabled;
        mAutoRotateEnabled = mPrefs.getBoolean(ALLOW_ROTATION_PREFERENCE_KEY,
                getAllowRotationDefaultValue());
        if (mAutoRotateEnabled != wasRotationEnabled) {

            notifyChange();
            updateRotationAnimation();
            mLauncher.reapplyUi();
        }
    }

    public void setStateHandlerRequest(int request) {
        if (mStateHandlerRequest != request) {
            mStateHandlerRequest = request;
            updateRotationAnimation();
            notifyChange();
        }
    }

    public void setCurrentTransitionRequest(int request) {
        if (mCurrentTransitionRequest != request) {
            mCurrentTransitionRequest = request;
            notifyChange();
        }
    }

    public void setCurrentStateRequest(int request) {
        if (mCurrentStateRequest != request) {
            mCurrentStateRequest = request;
            notifyChange();
        }
    }

    // Used by tests only.
    public void forceAllowRotationForTesting(boolean allowRotation) {
        mIgnoreAutoRotateSettings =
                allowRotation || mLauncher.getResources().getBoolean(R.bool.allow_rotation);
        notifyChange();
    }

    public void initialize() {
        if (!mInitialized) {
            mInitialized = true;
            notifyChange();
            updateRotationAnimation();
        }
    }

    public void destroy() {
        if (!mDestroyed) {
            mDestroyed = true;
            if (mPrefs != null) {
                mPrefs.unregisterOnSharedPreferenceChangeListener(this);
            }
            if (mContentResolver != null) {
                mContentResolver.unregisterContentObserver(mContentObserver);
            }
            mForcedRotationChangedListeners.clear();
        }
    }

    private void notifyChange() {
        if (!mInitialized || mDestroyed) {
            return;
        }

        final int activityFlags;
        if (mForcedRotation) {
            // TODO(b/150214193) Properly address this
            activityFlags = SCREEN_ORIENTATION_PORTRAIT;
        } else if (mStateHandlerRequest != REQUEST_NONE) {
            activityFlags = mStateHandlerRequest == REQUEST_LOCK ?
                    SCREEN_ORIENTATION_LOCKED : SCREEN_ORIENTATION_UNSPECIFIED;
        } else if (mCurrentTransitionRequest != REQUEST_NONE) {
            activityFlags = mCurrentTransitionRequest == REQUEST_LOCK ?
                    SCREEN_ORIENTATION_LOCKED : SCREEN_ORIENTATION_UNSPECIFIED;
        } else if (mCurrentStateRequest == REQUEST_LOCK) {
            activityFlags = SCREEN_ORIENTATION_LOCKED;
        } else if (mIgnoreAutoRotateSettings || mCurrentStateRequest == REQUEST_ROTATE
                || mAutoRotateEnabled) {
            activityFlags = SCREEN_ORIENTATION_UNSPECIFIED;
        } else {
            // If auto rotation is off, allow rotation on the activity, in case the user is using
            // forced rotation.
            activityFlags = SCREEN_ORIENTATION_NOSENSOR;
        }
        if (activityFlags != mLastActivityFlags) {
            mLastActivityFlags = activityFlags;
            UiThreadHelper.setOrientationAsync(mLauncher, activityFlags);
        }
    }

    public static int getDegreesFromRotation(int rotation) {
        int degrees;
        switch (rotation) {
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
            case Surface.ROTATION_0:
            default:
                degrees = 0;
                break;
        }
        return degrees;
    }

    public static int getRotationFromDegrees(int degrees) {
        int threshold = 70;
        if (degrees >= (360 - threshold) || degrees < (threshold)) {
            return Surface.ROTATION_0;
        } else if (degrees < (90 + threshold)) {
            return Surface.ROTATION_270;
        } else if (degrees < 180 + threshold) {
            return Surface.ROTATION_180;
        } else {
            return Surface.ROTATION_90;
        }
    }

    /**
     * @return how many factors {@param newRotation} is rotated 90 degrees clockwise.
     * E.g. 1->Rotated by 90 degrees clockwise, 2->Rotated 180 clockwise...
     * A value of 0 means no rotation has been applied
     */
    public static int deltaRotation(int oldRotation, int newRotation) {
        int delta = newRotation - oldRotation;
        if (delta < 0) delta += 4;
        return delta;
    }

    /**
     * Creates a matrix to transform the given motion event specified by degrees.
     * If {@param inverse} is {@code true}, the inverse of that matrix will be applied
     */
    public static void transformEvent(float degrees, MotionEvent ev, boolean inverse) {
        Matrix transform = new Matrix();
        transform.setRotate(degrees);
        if (inverse) {
            Matrix inv = new Matrix();
            transform.invert(inv);
            ev.transform(inv);
        } else {
            ev.transform(transform);
        }
        // TODO: Add scaling back in based on degrees
//        if (getWidth() > 0 && getHeight() > 0) {
//            float scale = ((float) getWidth()) / getHeight();
//            transform.postScale(scale, 1 / scale);
//        }
    }

    /**
     * TODO(b/149658423): Have {@link com.android.quickstep.OrientationTouchTransformer
     *   also use this}
     */
    public static Matrix getRotationMatrix(int screenWidth, int screenHeight, int displayRotation) {
        Matrix m = new Matrix();
        switch (displayRotation) {
            case Surface.ROTATION_0:
                return m;
            case Surface.ROTATION_90:
                m.setRotate(360 - RotationHelper.getDegreesFromRotation(displayRotation));
                m.postTranslate(0, screenWidth);
                break;
            case Surface.ROTATION_270:
                m.setRotate(360 - RotationHelper.getDegreesFromRotation(displayRotation));
                m.postTranslate(screenHeight, 0);
                break;
        }
        return m;
    }

    public static void mapRectFromNormalOrientation(RectF src, int screenWidth, int screenHeight,
        int displayRotation) {
        Matrix m = RotationHelper.getRotationMatrix(screenWidth, screenHeight, displayRotation);
        m.mapRect(src);
    }

    public static void mapInverseRectFromNormalOrientation(RectF src, int screenWidth,
        int screenHeight, int displayRotation) {
        Matrix m = RotationHelper.getRotationMatrix(screenWidth, screenHeight, displayRotation);
        Matrix inverse = new Matrix();
        m.invert(inverse);
        inverse.mapRect(src);
    }

    public static void getTargetRectForRotation(Rect srcOut, int screenWidth, int screenHeight,
        int displayRotation) {
        RectF wrapped = new RectF(srcOut);
        Matrix m = RotationHelper.getRotationMatrix(screenWidth, screenHeight, displayRotation);
        m.mapRect(wrapped);
        wrapped.round(srcOut);
    }

    public static boolean isRotationLandscape(int rotation) {
        return rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90;
    }

    @Override
    public String toString() {
        return String.format("[mStateHandlerRequest=%d, mCurrentStateRequest=%d,"
                + " mLastActivityFlags=%d, mIgnoreAutoRotateSettings=%b, mAutoRotateEnabled=%b]",
                mStateHandlerRequest, mCurrentStateRequest, mLastActivityFlags,
                mIgnoreAutoRotateSettings, mAutoRotateEnabled);
    }
}
