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
import static android.provider.Settings.System.ACCELEROMETER_ROTATION;
import static android.provider.Settings.System.getUriFor;

import android.app.Activity;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;

import com.android.launcher3.R;

/**
 * Utility class to manage launcher rotation
 */
public class RotationHelper extends ContentObserver {

    public static final int REQUEST_NONE = 0;
    public static final int REQUEST_ROTATE = 1;
    public static final int REQUEST_LOCK = 2;

    private final Activity mActivity;
    private final ContentResolver mCr;

    private final boolean mIgnoreAutoRotateSettings;
    private boolean mAutoRotateEnabled;

    /**
     * Rotation request made by {@link InternalStateHandler}. This supersedes any other request.
     */
    private int mStateHandlerRequest = REQUEST_NONE;
    /**
     * Rotation request made by a Launcher State
     */
    private int mCurrentStateRequest = REQUEST_NONE;

    // This is used to defer setting rotation flags until the activity is being created
    private boolean mInitialized;
    public boolean mDestroyed;

    private int mLastActivityFlags = -1;

    public RotationHelper(Activity activity) {
        super(new Handler());
        mActivity = activity;

        // On large devices we do not handle auto-rotate differently.
        mIgnoreAutoRotateSettings = mActivity.getResources().getBoolean(R.bool.allow_rotation);
        if (!mIgnoreAutoRotateSettings) {
            mCr = mActivity.getContentResolver();
            mCr.registerContentObserver(getUriFor(ACCELEROMETER_ROTATION), false, this);
            mAutoRotateEnabled = Settings.System.getInt(mCr, ACCELEROMETER_ROTATION, 1) == 1;
        } else {
            mCr = null;
        }
    }

    @Override
    public void onChange(boolean selfChange) {
        mAutoRotateEnabled = Settings.System.getInt(mCr, ACCELEROMETER_ROTATION, 1) == 1;
        notifyChange();
    }

    public void setStateHandlerRequest(int request) {
        if (mStateHandlerRequest != request) {
            mStateHandlerRequest = request;
            notifyChange();
        }
    }

    public void setCurrentStateRequest(int request) {
        if (mCurrentStateRequest != request) {
            mCurrentStateRequest = request;
            notifyChange();
        }
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
            if (mCr != null) {
                mCr.unregisterContentObserver(this);
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
        } else if (mCurrentStateRequest == REQUEST_LOCK) {
            activityFlags = SCREEN_ORIENTATION_LOCKED;
        } else if (mIgnoreAutoRotateSettings || mCurrentStateRequest == REQUEST_ROTATE) {
            activityFlags = SCREEN_ORIENTATION_UNSPECIFIED;
        } else if (mAutoRotateEnabled) {
            // If auto rotation is on, lock to device orientation
            activityFlags = SCREEN_ORIENTATION_NOSENSOR;
        } else {
            // If auto rotation is off, allow rotation on the activity, in case the user is using
            // forced rotation.
            activityFlags = SCREEN_ORIENTATION_UNSPECIFIED;
        }
        if (activityFlags != mLastActivityFlags) {
            mLastActivityFlags = activityFlags;
            mActivity.setRequestedOrientation(mLastActivityFlags);
        }
    }
}
