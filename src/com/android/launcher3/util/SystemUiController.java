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

package com.android.launcher3.util;

import android.view.View;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

/**
 * Utility class to manage various window flags to control system UI.
 */
public class SystemUiController {

    // Various UI states in increasing order of priority
    public static final int UI_STATE_BASE_WINDOW = 0;
    public static final int UI_STATE_SCRIM_VIEW = 1;
    public static final int UI_STATE_WIDGET_BOTTOM_SHEET = 2;
    public static final int UI_STATE_FULLSCREEN_TASK = 3;
    public static final int UI_STATE_ALL_APPS = 4;

    public static final int FLAG_LIGHT_NAV = 1 << 0;
    public static final int FLAG_DARK_NAV = 1 << 1;
    public static final int FLAG_LIGHT_STATUS = 1 << 2;
    public static final int FLAG_DARK_STATUS = 1 << 3;

    /**
     * Security type based on WifiConfiguration.KeyMgmt
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {
            FLAG_LIGHT_NAV,
            FLAG_DARK_NAV,
            FLAG_LIGHT_STATUS,
            FLAG_DARK_STATUS,
    })
    public @interface SystemUiControllerFlags {}

    private final View mView;
    private final int[] mStates = new int[5];

    public SystemUiController(View view) {
        mView = view;
    }

    public void updateUiState(int uiState, boolean isLight) {
        updateUiState(uiState, isLight
                ? (FLAG_LIGHT_NAV | FLAG_LIGHT_STATUS) : (FLAG_DARK_NAV | FLAG_DARK_STATUS));
    }

    public void updateUiState(int uiState, @SystemUiControllerFlags int flags) {
        if (mStates[uiState] == flags) {
            return;
        }
        mStates[uiState] = flags;

        int oldFlags = mView.getSystemUiVisibility();
        // Apply the state flags in priority order
        int newFlags = oldFlags;
        for (int stateFlag : mStates) {
            newFlags = getSysUiVisibilityFlags(stateFlag, newFlags);
        }
        if (newFlags != oldFlags) {
            mView.setSystemUiVisibility(newFlags);
        }
    }

    /**
     * Returns the sysui visibility for the base layer
     */
    public int getBaseSysuiVisibility() {
        return getSysUiVisibilityFlags(
                mStates[UI_STATE_BASE_WINDOW], mView.getSystemUiVisibility());
    }

    private int getSysUiVisibilityFlags(int stateFlag, int currentVisibility) {
        if ((stateFlag & FLAG_LIGHT_NAV) != 0) {
            currentVisibility |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else if ((stateFlag & FLAG_DARK_NAV) != 0) {
            currentVisibility &= ~(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        if ((stateFlag & FLAG_LIGHT_STATUS) != 0) {
            currentVisibility |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        } else if ((stateFlag & FLAG_DARK_STATUS) != 0) {
            currentVisibility &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        return currentVisibility;
    }

    @Override
    public String toString() {
        return "mStates=" + Arrays.toString(mStates);
    }
}
