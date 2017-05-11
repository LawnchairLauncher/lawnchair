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
import android.util.Log;

/**
 * {@link BroadcastReceiver} which watches configuration changes and
 * restarts the process in case changes which affect the device profile occur.
 */
public class ConfigMonitor extends BroadcastReceiver {

    private final Context mContext;
    private final float mFontScale;
    private final int mDensity;

    public ConfigMonitor(Context context) {
        mContext = context;

        Configuration config = context.getResources().getConfiguration();
        mFontScale = config.fontScale;
        mDensity = config.densityDpi;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Configuration config = context.getResources().getConfiguration();
        if (mFontScale != config.fontScale || mDensity != config.densityDpi) {
            Log.d("ConfigMonitor", "Configuration changed, restarting launcher");
            mContext.unregisterReceiver(this);
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    public void register() {
        mContext.registerReceiver(this, new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED));
    }
}
