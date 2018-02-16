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

import static com.android.systemui.shared.system.NavigationBarCompat.FLAG_HIDE_BACK_BUTTON;

import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import com.android.systemui.shared.recents.ISystemUiProxy;

/**
 * Sets overview interaction flags, such as:
 *
 *   - FLAG_DISABLE_QUICK_SCRUB
 *   - FLAG_DISABLE_SWIPE_UP
 *   - FLAG_HIDE_BACK_BUTTON
 *   - FLAG_SHOW_OVERVIEW_BUTTON
 *
 * @see com.android.systemui.shared.system.NavigationBarCompat.InteractionType and associated flags.
 */
public class OverviewInteractionState {

    private static final String TAG = "OverviewFlags";

    private static int sFlags;

    public static void setBackButtonVisible(Context context, boolean visible) {
        updateOverviewInteractionFlag(context, FLAG_HIDE_BACK_BUTTON, !visible);
    }

    private static void updateOverviewInteractionFlag(Context context, int flag, boolean enabled) {
        if (enabled) {
            sFlags |= flag;
        } else {
            sFlags &= ~flag;
        }

        ISystemUiProxy systemUiProxy = RecentsModel.getInstance(context).getSystemUiProxy();
        if (systemUiProxy == null) {
            Log.w(TAG, "Unable to update overview interaction flags; not bound to service");
            return;
        }
        try {
            systemUiProxy.setInteractionState(sFlags);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to update overview interaction flags", e);
        }
    }
}
