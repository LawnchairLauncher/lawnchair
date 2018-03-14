/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Point;
import android.view.Display;
import android.view.View.AccessibilityDelegate;

import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.util.SystemUiController;

import java.util.ArrayList;

public abstract class BaseActivity extends Activity {

    private final ArrayList<OnDeviceProfileChangeListener> mDPChangeListeners = new ArrayList<>();

    protected DeviceProfile mDeviceProfile;
    protected UserEventDispatcher mUserEventDispatcher;
    protected SystemUiController mSystemUiController;

    private boolean mStarted;

    public DeviceProfile getDeviceProfile() {
        return mDeviceProfile;
    }

    public AccessibilityDelegate getAccessibilityDelegate() {
        return null;
    }

    public final UserEventDispatcher getUserEventDispatcher() {
        if (mUserEventDispatcher == null) {
            mUserEventDispatcher = UserEventDispatcher.newInstance(this, mDeviceProfile);
        }
        return mUserEventDispatcher;
    }

    public boolean isInMultiWindowModeCompat() {
        return Utilities.ATLEAST_NOUGAT && isInMultiWindowMode();
    }

    public static BaseActivity fromContext(Context context) {
        if (context instanceof BaseActivity) {
            return (BaseActivity) context;
        }
        return ((BaseActivity) ((ContextWrapper) context).getBaseContext());
    }

    public SystemUiController getSystemUiController() {
        if (mSystemUiController == null) {
            mSystemUiController = new SystemUiController(getWindow());
        }
        return mSystemUiController;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onStart() {
        mStarted = true;
        super.onStart();
    }

    @Override
    protected void onStop() {
        mStarted = false;
        super.onStop();
    }

    public boolean isStarted() {
        return mStarted;
    }

    public void addOnDeviceProfileChangeListener(OnDeviceProfileChangeListener listener) {
        mDPChangeListeners.add(listener);
    }

    protected void dispatchDeviceProfileChanged() {
        int count = mDPChangeListeners.size();
        for (int i = 0; i < count; i++) {
            mDPChangeListeners.get(i).onDeviceProfileChanged(mDeviceProfile);
        }
    }

    /**
     * Sets the device profile, adjusting it accordingly in case of multi-window
     */
    protected void setDeviceProfile(DeviceProfile dp) {
        mDeviceProfile = dp;
        if (isInMultiWindowModeCompat()) {
            Display display = getWindowManager().getDefaultDisplay();
            Point mwSize = new Point();
            display.getSize(mwSize);
            mDeviceProfile = mDeviceProfile.getMultiWindowProfile(this, mwSize);
        }
    }
}
