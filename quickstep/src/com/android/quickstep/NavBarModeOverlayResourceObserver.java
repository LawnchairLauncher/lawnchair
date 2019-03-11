/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.quickstep;

import static android.content.Intent.ACTION_OVERLAY_CHANGED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.util.Log;

import com.android.systemui.shared.system.QuickStepContract;

/**
 * Observer for the resource config that specifies the navigation bar mode.
 */
public class NavBarModeOverlayResourceObserver extends BroadcastReceiver {

    private static final String TAG = "NavBarModeOverlayResourceObserver";

    private static final String NAV_BAR_INTERACTION_MODE_RES_NAME =
            "config_navBarInteractionMode";

    private final Context mContext;
    private final OnChangeListener mOnChangeListener;

    public NavBarModeOverlayResourceObserver(Context context, OnChangeListener listener) {
        mContext = context;
        mOnChangeListener = listener;
    }

    public void register() {
        IntentFilter filter = new IntentFilter(ACTION_OVERLAY_CHANGED);
        filter.addDataScheme("package");
        mContext.registerReceiver(this, filter);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        mOnChangeListener.onNavBarModeChanged(getSystemIntegerRes(context,
                NAV_BAR_INTERACTION_MODE_RES_NAME));
    }

    public interface OnChangeListener {
        void onNavBarModeChanged(int mode);
    }

    public static boolean isSwipeUpModeEnabled(Context context) {
        return QuickStepContract.isSwipeUpMode(getSystemIntegerRes(context,
                NAV_BAR_INTERACTION_MODE_RES_NAME));
    }

    public static boolean isEdgeToEdgeModeEnabled(Context context) {
        return QuickStepContract.isGesturalMode(getSystemIntegerRes(context,
                NAV_BAR_INTERACTION_MODE_RES_NAME));
    }

    private static int getSystemIntegerRes(Context context, String resName) {
        Resources res = context.getResources();
        int resId = res.getIdentifier(resName, "integer", "android");

        if (resId != 0) {
            return res.getInteger(resId);
        } else {
            Log.e(TAG, "Failed to get system resource ID. Incompatible framework version?");
            return -1;
        }
    }
}
