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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Point;
import android.util.Log;

import java.util.function.Consumer;

/**
 * {@link BroadcastReceiver} which watches configuration changes and
 * notifies the callback in case changes which affect the device profile occur.
 */
public class ConfigMonitor extends BroadcastReceiver implements
        DefaultDisplay.DisplayInfoChangeListener {

    private static final String TAG = "ConfigMonitor";

    private final Point mTmpPoint1 = new Point();
    private final Point mTmpPoint2 = new Point();

    private final Context mContext;
    private final float mFontScale;
    private final int mDensity;

    private final int mDisplayId;
    private final Point mRealSize;
    private final Point mSmallestSize, mLargestSize;

    private Consumer<Context> mCallback;

    public ConfigMonitor(Context context, Consumer<Context> callback) {
        mContext = context;

        Configuration config = context.getResources().getConfiguration();
        mFontScale = config.fontScale;
        mDensity = config.densityDpi;

        DefaultDisplay display = DefaultDisplay.INSTANCE.get(context);
        display.addChangeListener(this);
        DefaultDisplay.Info displayInfo = display.getInfo();
        mDisplayId = displayInfo.id;

        mRealSize = new Point(displayInfo.realSize);
        mSmallestSize = new Point(displayInfo.smallestSize);
        mLargestSize = new Point(displayInfo.largestSize);

        mCallback = callback;

        // Listen for configuration change
        mContext.registerReceiver(this, new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Configuration config = context.getResources().getConfiguration();
        if (mFontScale != config.fontScale || mDensity != config.densityDpi) {
            Log.d(TAG, "Configuration changed.");
            notifyChange();
        }
    }

    @Override
    public void onDisplayInfoChanged(DefaultDisplay.Info info, int flags) {
        if (info.id != mDisplayId) {
            return;
        }
        mTmpPoint1.set(info.realSize.x, info.realSize.y);
        if (!mRealSize.equals(mTmpPoint1) && !mRealSize.equals(mTmpPoint1.y, mTmpPoint1.x)) {
            Log.d(TAG, String.format("Display size changed from %s to %s", mRealSize, mTmpPoint1));
            notifyChange();
            return;
        }

        mTmpPoint1.set(info.smallestSize.x, info.smallestSize.y);
        mTmpPoint2.set(info.largestSize.x, info.largestSize.y);
        if (!mSmallestSize.equals(mTmpPoint1) || !mLargestSize.equals(mTmpPoint2)) {
            Log.d(TAG, String.format("Available size changed from [%s, %s] to [%s, %s]",
                    mSmallestSize, mLargestSize, mTmpPoint1, mTmpPoint2));
            notifyChange();
        }
    }

    private synchronized void notifyChange() {
        if (mCallback != null) {
            Consumer<Context> callback = mCallback;
            mCallback = null;
            MAIN_EXECUTOR.execute(() -> callback.accept(mContext));
        }
    }

    public void unregister() {
        try {
            mContext.unregisterReceiver(this);
            DefaultDisplay display = DefaultDisplay.INSTANCE.get(mContext);
            display.removeChangeListener(this);
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister config monitor", e);
        }
    }
}
