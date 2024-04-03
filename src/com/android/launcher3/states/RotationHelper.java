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
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
import static android.util.DisplayMetrics.DENSITY_DEVICE_STABLE;

import static com.android.launcher3.LauncherPrefs.ALLOW_ROTATION;
import static com.android.launcher3.Utilities.dpiFromPx;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.window.WindowManagerProxy.MIN_TABLET_WIDTH;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.util.DisplayController;

/**
 * Utility class to manage launcher rotation
 */
public class RotationHelper implements OnSharedPreferenceChangeListener,
        DisplayController.DisplayInfoChangeListener {

    public static final String ALLOW_ROTATION_PREFERENCE_KEY = "pref_allowRotation";

    /**
     * Returns the default value of {@link #ALLOW_ROTATION_PREFERENCE_KEY} preference.
     */
    public static boolean getAllowRotationDefaultValue(DisplayController.Info info) {
        // If the device's pixel density was scaled (usually via settings for A11y), use the
        // original dimensions to determine if rotation is allowed of not.
        float originalSmallestWidth = dpiFromPx(Math.min(info.currentSize.x, info.currentSize.y),
                DENSITY_DEVICE_STABLE);
        return originalSmallestWidth >= MIN_TABLET_WIDTH;
    }

    public static final int REQUEST_NONE = 0;
    public static final int REQUEST_ROTATE = 1;
    public static final int REQUEST_LOCK = 2;

    @NonNull
    private final BaseActivity mActivity;
    private final Handler mRequestOrientationHandler;

    private boolean mIgnoreAutoRotateSettings;
    private boolean mForceAllowRotationForTesting;
    private boolean mHomeRotationEnabled;

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

    // Initialize mLastActivityFlags to a value not used by SCREEN_ORIENTATION flags
    private int mLastActivityFlags = -999;

    public RotationHelper(@NonNull BaseActivity activity) {
        mActivity = activity;
        mRequestOrientationHandler =
                new Handler(UI_HELPER_EXECUTOR.getLooper(), this::setOrientationAsync);
    }

    private void setIgnoreAutoRotateSettings(boolean ignoreAutoRotateSettings) {
        if (mDestroyed) return;
        // On large devices we do not handle auto-rotate differently.
        mIgnoreAutoRotateSettings = ignoreAutoRotateSettings;
        if (!mIgnoreAutoRotateSettings) {
            mHomeRotationEnabled = LauncherPrefs.get(mActivity).get(ALLOW_ROTATION);
            LauncherPrefs.get(mActivity).addListener(this, ALLOW_ROTATION);
        } else {
            LauncherPrefs.get(mActivity).removeListener(this, ALLOW_ROTATION);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (mDestroyed || mIgnoreAutoRotateSettings) return;
        boolean wasRotationEnabled = mHomeRotationEnabled;
        mHomeRotationEnabled = LauncherPrefs.get(mActivity).get(ALLOW_ROTATION);
        if (mHomeRotationEnabled != wasRotationEnabled) {
            notifyChange();
        }
    }

    @Override
    public void onDisplayInfoChanged(Context context, DisplayController.Info info, int flags) {
        if (mDestroyed) return;
        boolean ignoreAutoRotateSettings = info.isTablet(info.realBounds);
        if (mIgnoreAutoRotateSettings != ignoreAutoRotateSettings) {
            setIgnoreAutoRotateSettings(ignoreAutoRotateSettings);
            notifyChange();
        }
    }

    public void setStateHandlerRequest(int request) {
        if (mDestroyed || mStateHandlerRequest == request) return;
        mStateHandlerRequest = request;
        notifyChange();
    }

    public void setCurrentTransitionRequest(int request) {
        if (mDestroyed || mCurrentTransitionRequest == request) return;
        mCurrentTransitionRequest = request;
        notifyChange();
    }

    public void setCurrentStateRequest(int request) {
        if (mDestroyed || mCurrentStateRequest == request) return;
        mCurrentStateRequest = request;
        notifyChange();
    }

    // Used by tests only.
    public void forceAllowRotationForTesting(boolean allowRotation) {
        if (mDestroyed) return;
        mForceAllowRotationForTesting = allowRotation;
        notifyChange();
    }

    public void initialize() {
        if (mInitialized) return;
        mInitialized = true;
        DisplayController displayController = DisplayController.INSTANCE.get(mActivity);
        DisplayController.Info info = displayController.getInfo();
        setIgnoreAutoRotateSettings(info.isTablet(info.realBounds));
        displayController.addChangeListener(this);
        notifyChange();
    }

    public void destroy() {
        if (mDestroyed) return;
        mDestroyed = true;
        DisplayController.INSTANCE.get(mActivity).removeChangeListener(this);
        LauncherPrefs.get(mActivity).removeListener(this, ALLOW_ROTATION);
    }

    private void notifyChange() {
        if (!mInitialized || mDestroyed) {
            return;
        }

        final int activityFlags;
        if (mStateHandlerRequest != REQUEST_NONE) {
            activityFlags = mStateHandlerRequest == REQUEST_LOCK ?
                    SCREEN_ORIENTATION_LOCKED : SCREEN_ORIENTATION_UNSPECIFIED;
        } else if (mCurrentTransitionRequest != REQUEST_NONE) {
            activityFlags = mCurrentTransitionRequest == REQUEST_LOCK ?
                    SCREEN_ORIENTATION_LOCKED : SCREEN_ORIENTATION_UNSPECIFIED;
        } else if (mCurrentStateRequest == REQUEST_LOCK) {
            activityFlags = SCREEN_ORIENTATION_LOCKED;
        } else if (mIgnoreAutoRotateSettings || mCurrentStateRequest == REQUEST_ROTATE
                || mHomeRotationEnabled || mForceAllowRotationForTesting) {
            activityFlags = SCREEN_ORIENTATION_UNSPECIFIED;
        } else {
            // If auto rotation is off, allow rotation on the activity, in case the user is using
            // forced rotation.
            activityFlags = SCREEN_ORIENTATION_NOSENSOR;
        }
        if (activityFlags != mLastActivityFlags) {
            mLastActivityFlags = activityFlags;
            mRequestOrientationHandler.sendEmptyMessage(activityFlags);
        }
    }

    @WorkerThread
    private boolean setOrientationAsync(Message msg) {
        if (mDestroyed) return true;
        mActivity.setRequestedOrientation(msg.what);
        return true;
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

    @Override
    public String toString() {
        return String.format("[mStateHandlerRequest=%d, mCurrentStateRequest=%d, "
                        + "mLastActivityFlags=%d, mIgnoreAutoRotateSettings=%b, "
                        + "mHomeRotationEnabled=%b, mForceAllowRotationForTesting=%b,"
                        + " mDestroyed=%b]",
                mStateHandlerRequest, mCurrentStateRequest, mLastActivityFlags,
                mIgnoreAutoRotateSettings, mHomeRotationEnabled, mForceAllowRotationForTesting,
                mDestroyed);
    }
}
