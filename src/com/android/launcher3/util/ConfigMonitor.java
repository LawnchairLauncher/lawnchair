package com.android.launcher3.util;

/**
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

/**
 * {@link BroadcastReceiver} which watches configuration changes and
 * restarts the process in case changes which affect the device profile occur.
 */
public class ConfigMonitor extends BroadcastReceiver implements DisplayListener {

    private static final String TAG = "ConfigMonitor";

    private final Point mTmpPoint1 = new Point();
    private final Point mTmpPoint2 = new Point();

    private final Context mContext;
    private final float mFontScale;
    private final int mDensity;

    private final int mDisplayId;
    private final Point mRealSize;
    private final Point mSmallestSize, mLargestSize;

    public ConfigMonitor(Context context) {
        mContext = context;

        Configuration config = context.getResources().getConfiguration();
        mFontScale = config.fontScale;
        mDensity = config.densityDpi;

        Display display = getDefaultDisplay(context);
        mDisplayId = display.getDisplayId();

        mRealSize = new Point();
        display.getRealSize(mRealSize);

        mSmallestSize = new Point();
        mLargestSize = new Point();
        display.getCurrentSizeRange(mSmallestSize, mLargestSize);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Configuration config = context.getResources().getConfiguration();
        if (mFontScale != config.fontScale || mDensity != config.densityDpi) {
            Log.d(TAG, "Configuration changed");
            killProcess();
        }
    }

    public void register() {
        mContext.registerReceiver(this, new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED));
        mContext.getSystemService(DisplayManager.class)
                .registerDisplayListener(this, new Handler(UiThreadHelper.getBackgroundLooper()));
    }

    @Override
    public void onDisplayAdded(int displayId) { }

    @Override
    public void onDisplayRemoved(int displayId) { }

    @Override
    public void onDisplayChanged(int displayId) {
        if (displayId != mDisplayId) {
            return;
        }
        Display display = getDefaultDisplay(mContext);
        display.getRealSize(mTmpPoint1);

        if (!mRealSize.equals(mTmpPoint1) && !mRealSize.equals(mTmpPoint1.y, mTmpPoint1.x)) {
            Log.d(TAG, String.format("Display size changed from %s to %s", mRealSize, mTmpPoint1));
            killProcess();
            return;
        }

        display.getCurrentSizeRange(mTmpPoint1, mTmpPoint2);
        if (!mSmallestSize.equals(mTmpPoint1) || !mLargestSize.equals(mTmpPoint2)) {
            Log.d(TAG, String.format("Available size changed from [%s, %s] to [%s, %s]",
                    mSmallestSize, mLargestSize, mTmpPoint1, mTmpPoint2));
            killProcess();
        }
    }

    private void killProcess() {
        Log.d(TAG, "restarting launcher");
        mContext.unregisterReceiver(this);
        mContext.getSystemService(DisplayManager.class).unregisterDisplayListener(this);
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    private Display getDefaultDisplay(Context context) {
        return context.getSystemService(WindowManager.class).getDefaultDisplay();
    }
}
