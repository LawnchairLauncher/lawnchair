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

import static android.Manifest.permission.WRITE_SECURE_SETTINGS;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.util.DisplayMetrics.DENSITY_DEVICE_STABLE;

import static com.android.launcher3.config.FeatureFlags.FLAG_ENABLE_FIXED_ROTATION_TRANSFORM;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.provider.Settings;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
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

    public static final int REQUEST_NONE = 0;
    public static final int REQUEST_ROTATE = 1;
    public static final int REQUEST_LOCK = 2;

    private final Activity mActivity;
    private final SharedPreferences mSharedPrefs;
    private final SharedPreferences mFeatureFlagsPrefs;

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

    private int mLastActivityFlags = -1;

    public RotationHelper(Activity activity) {
        mActivity = activity;

        // On large devices we do not handle auto-rotate differently.
        mIgnoreAutoRotateSettings = mActivity.getResources().getBoolean(R.bool.allow_rotation);
        if (!mIgnoreAutoRotateSettings) {
            mSharedPrefs = Utilities.getPrefs(mActivity);
            mSharedPrefs.registerOnSharedPreferenceChangeListener(this);
            mAutoRotateEnabled = mSharedPrefs.getBoolean(ALLOW_ROTATION_PREFERENCE_KEY,
                    getAllowRotationDefaultValue());
        } else {
            mSharedPrefs = null;
        }

        mContentResolver = activity.getContentResolver();
        mFeatureFlagsPrefs = Utilities.getFeatureFlagsPrefs(mActivity);
        mFeatureFlagsPrefs.registerOnSharedPreferenceChangeListener(this);
        updateForcedRotation(true);
    }

    /**
     * @param setValueFromPrefs If true, then {@link #mForcedRotation} will get set to the value
     *                          from the home developer settings. Otherwise it will not.
     *                          This is primarily to allow tests to set their own conditions.
     */
    private void updateForcedRotation(boolean setValueFromPrefs) {
        boolean isForcedRotation = mFeatureFlagsPrefs
                .getBoolean(FLAG_ENABLE_FIXED_ROTATION_TRANSFORM, true)
                && !getAllowRotationDefaultValue();
        if (mForcedRotation == isForcedRotation) {
            return;
        }
        if (setValueFromPrefs) {
            mForcedRotation = isForcedRotation;
        }
        UI_HELPER_EXECUTOR.execute(() -> {
            if (mActivity.checkSelfPermission(WRITE_SECURE_SETTINGS) == PERMISSION_GRANTED) {
                Settings.Global.putInt(mContentResolver, FIXED_ROTATION_TRANSFORM_SETTING_NAME,
                            mForcedRotation ? 1 : 0);
            }
        });
        for (ForcedRotationChangedListener listener : mForcedRotationChangedListeners) {
            listener.onForcedRotationChanged(mForcedRotation);
        }
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

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (FLAG_ENABLE_FIXED_ROTATION_TRANSFORM.equals(s)) {
            updateForcedRotation(true);
            return;
        }

        boolean wasRotationEnabled = mAutoRotateEnabled;
        mAutoRotateEnabled = mSharedPrefs.getBoolean(ALLOW_ROTATION_PREFERENCE_KEY,
                getAllowRotationDefaultValue());
        if (mAutoRotateEnabled != wasRotationEnabled) {
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
        mIgnoreAutoRotateSettings =
                allowRotation || mActivity.getResources().getBoolean(R.bool.allow_rotation);
        // TODO(b/150214193) Tests currently expect launcher to be able to be rotated
        //   Modify tests for this new behavior
        mForcedRotation = !allowRotation;
        updateForcedRotation(false);
        notifyChange();
    }

    public void initialize() {
        if (!mInitialized) {
            mInitialized = true;
            notifyChange();
        }
    }

    public void destroy() {
        if (!mDestroyed) {
            mDestroyed = true;
            if (mSharedPrefs != null) {
                mSharedPrefs.unregisterOnSharedPreferenceChangeListener(this);
            }
            mForcedRotationChangedListeners.clear();
            mFeatureFlagsPrefs.unregisterOnSharedPreferenceChangeListener(this);
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
            UiThreadHelper.setOrientationAsync(mActivity, activityFlags);
        }
    }

    @Override
    public String toString() {
        return String.format("[mStateHandlerRequest=%d, mCurrentStateRequest=%d,"
                + " mLastActivityFlags=%d, mIgnoreAutoRotateSettings=%b, mAutoRotateEnabled=%b]",
                mStateHandlerRequest, mCurrentStateRequest, mLastActivityFlags,
                mIgnoreAutoRotateSettings, mAutoRotateEnabled);
    }
}
