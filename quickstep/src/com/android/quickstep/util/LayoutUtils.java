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
package com.android.quickstep.util;

import static com.android.launcher3.config.FeatureFlags.ENABLE_OVERVIEW_ACTIONS;
import static com.android.quickstep.SysUINavigationMode.removeShelfFromOverview;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.quickstep.LauncherActivityInterface;
import com.android.quickstep.SysUINavigationMode;

public class LayoutUtils {

    /**
     * The height for the swipe up motion
     */
    public static float getDefaultSwipeHeight(Context context, DeviceProfile dp) {
        float swipeHeight = dp.allAppsCellHeightPx - dp.allAppsIconTextSizePx;
        if (SysUINavigationMode.getMode(context) == SysUINavigationMode.Mode.NO_BUTTON) {
            swipeHeight -= dp.getInsets().bottom;
        }
        return swipeHeight;
    }

    public static int getShelfTrackingDistance(Context context, DeviceProfile dp) {
        // Track the bottom of the window.
        if (ENABLE_OVERVIEW_ACTIONS.get() && removeShelfFromOverview(context)) {
            Rect taskSize = new Rect();
            LauncherActivityInterface.INSTANCE.calculateTaskSize(context, dp, taskSize);
            return (dp.heightPx - taskSize.height()) / 2;
        }
        int shelfHeight = dp.hotseatBarSizePx + dp.getInsets().bottom;
        int spaceBetweenShelfAndRecents = (int) context.getResources().getDimension(
                R.dimen.task_card_vert_space);
        return shelfHeight + spaceBetweenShelfAndRecents;
    }

    /**
     * Gets the scale that should be applied to the TaskView so that it matches the target
     * TODO: Remove this method
     */
    public static float getTaskScale(RecentsOrientedState orientedState,
            float srcWidth, float srcHeight, float targetWidth, float targetHeight) {
        if (orientedState == null
                || orientedState.isHomeRotationAllowed()
                || orientedState.isDisplayPhoneNatural()) {
            return srcWidth / targetWidth;
        } else {
            return srcHeight / targetHeight;
        }
    }

    /**
     * Recursively sets view and all children enabled/disabled.
     * @param viewGroup Top most parent view to change.
     * @param enabled True = enable, False = disable.
     */
    public static void setViewEnabled(ViewGroup viewGroup, boolean enabled) {
        viewGroup.setEnabled(enabled);
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof ViewGroup) {
                setViewEnabled((ViewGroup) child, enabled);
            } else {
                child.setEnabled(enabled);
            }
        }
    }
}
