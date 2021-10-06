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

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.ActivityTracker;
import com.android.launcher3.util.UiThreadHelper;

/**
 * Utility class to manage launcher rotation
 */
public class RotationHelper implements OnSharedPreferenceChangeListener,
        DeviceProfile.OnDeviceProfileChangeListener {

    private static final String TAG = "RotationHelper";

    public static final String ALLOW_ROTATION_PREFERENCE_KEY = "pref_allowRotation";

    public static final int REQUEST_NONE = 0;
    public static final int REQUEST_ROTATE = 1;
    public static final int REQUEST_LOCK = 2;

    private BaseActivity mActivity;
    private SharedPreferences mSharedPrefs = null;

    private boolean mIgnoreAutoRotateSettings;
    private boolean mForceAllowRotationForTesting;
    private boolean mHomeRotationEnabled;

    /**
     * Rotation request made by
     * {@link ActivityTracker.SchedulerCallback}.
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

    public RotationHelper(BaseActivity activity) {
        mActivity = activity;
    }

    private void setIgnoreAutoRotateSettings(boolean ignoreAutoRotateSettings) {
        // On large devices we do not handle auto-rotate differently.
        mIgnoreAutoRotateSettings = ignoreAutoRotateSettings;
        if (!mIgnoreAutoRotateSettings) {
            if (mSharedPrefs == null) {
                mSharedPrefs = Utilities.getPrefs(mActivity);
                mSharedPrefs.registerOnSharedPreferenceChangeListener(this);
            }
            mHomeRotationEnabled = mSharedPrefs.getBoolean(ALLOW_ROTATION_PREFERENCE_KEY,
                    mActivity.getDeviceProfile().allowRotation);
        } else {
            if (mSharedPrefs != null) {
                mSharedPrefs.unregisterOnSharedPreferenceChangeListener(this);
                mSharedPrefs = null;
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (mDestroyed) return;
        boolean wasRotationEnabled = mHomeRotationEnabled;
        mHomeRotationEnabled = mSharedPrefs.getBoolean(ALLOW_ROTATION_PREFERENCE_KEY,
                mActivity.getDeviceProfile().allowRotation);
        if (mHomeRotationEnabled != wasRotationEnabled) {
            notifyChange();
        }
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        boolean ignoreAutoRotateSettings = dp.allowRotation;
        if (mIgnoreAutoRotateSettings != ignoreAutoRotateSettings) {
            setIgnoreAutoRotateSettings(ignoreAutoRotateSettings);
            notifyChange();
        }
    }

    public void setStateHandlerRequest(int request) {
        if (mStateHandlerRequest != request) {
            mStateHandlerRequest = request;
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
        mForceAllowRotationForTesting = allowRotation;
        notifyChange();
    }

    public void initialize() {
        if (!mInitialized) {
            mInitialized = true;
            setIgnoreAutoRotateSettings(mActivity.getDeviceProfile().allowRotation);
            mActivity.addOnDeviceProfileChangeListener(this);
            notifyChange();
        }
    }

    public void destroy() {
        if (!mDestroyed) {
            mDestroyed = true;
            mActivity.removeOnDeviceProfileChangeListener(this);
            mActivity = null;
            if (mSharedPrefs != null) {
                mSharedPrefs.unregisterOnSharedPreferenceChangeListener(this);
            }
        }
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
            UiThreadHelper.setOrientationAsync(mActivity, activityFlags);
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

    @Override
    public String toString() {
        return String.format("[mStateHandlerRequest=%d, mCurrentStateRequest=%d, "
                        + "mLastActivityFlags=%d, mIgnoreAutoRotateSettings=%b, "
                        + "mHomeRotationEnabled=%b, mForceAllowRotationForTesting=%b]",
                mStateHandlerRequest, mCurrentStateRequest, mLastActivityFlags,
                mIgnoreAutoRotateSettings, mHomeRotationEnabled, mForceAllowRotationForTesting);
    }
}
