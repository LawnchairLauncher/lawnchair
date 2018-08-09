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

package com.android.quickstep;

import android.content.res.Resources;
import android.util.Log;

public final class SwipeUpSetting {
    private static final String TAG = "SwipeUpSetting";

    private static final String SWIPE_UP_SETTING_AVAILABLE_RES_NAME =
            "config_swipe_up_gesture_setting_available";

    private static final String SWIPE_UP_ENABLED_DEFAULT_RES_NAME =
            "config_swipe_up_gesture_default";

    private static boolean getSystemBooleanRes(String resName) {
        Resources res = Resources.getSystem();
        int resId = res.getIdentifier(resName, "bool", "android");

        if (resId != 0) {
            return res.getBoolean(resId);
        } else {
            Log.e(TAG, "Failed to get system resource ID. Incompatible framework version?");
            return false;
        }
    }

    public static boolean isSwipeUpSettingAvailable() {
        return getSystemBooleanRes(SWIPE_UP_SETTING_AVAILABLE_RES_NAME);
    }

    public static boolean isSwipeUpEnabledDefaultValue() {
        return getSystemBooleanRes(SWIPE_UP_ENABLED_DEFAULT_RES_NAME);
    }
}
