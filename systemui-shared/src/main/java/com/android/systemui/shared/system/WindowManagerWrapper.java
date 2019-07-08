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

package com.android.systemui.shared.system;

import static android.view.Display.DEFAULT_DISPLAY;

import android.app.WindowConfiguration;
import android.graphics.Rect;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;

import static android.view.WindowManagerPolicyConstants.NAV_BAR_RIGHT;
import static android.view.WindowManagerPolicyConstants.NAV_BAR_BOTTOM;
import static android.view.WindowManagerPolicyConstants.NAV_BAR_LEFT;

import com.android.systemui.shared.recents.view.AppTransitionAnimationSpecsFuture;
import com.android.systemui.shared.recents.view.RecentsTransition;

public class WindowManagerWrapper {

    private static final String TAG = "WindowManagerWrapper";

    public static final int TRANSIT_UNSET = WindowManager.TRANSIT_UNSET;
    public static final int TRANSIT_NONE = WindowManager.TRANSIT_NONE;
    public static final int TRANSIT_ACTIVITY_OPEN = WindowManager.TRANSIT_ACTIVITY_OPEN;
    public static final int TRANSIT_ACTIVITY_CLOSE = WindowManager.TRANSIT_ACTIVITY_CLOSE;
    public static final int TRANSIT_TASK_OPEN = WindowManager.TRANSIT_TASK_OPEN;
    public static final int TRANSIT_TASK_CLOSE = WindowManager.TRANSIT_TASK_CLOSE;
    public static final int TRANSIT_TASK_TO_FRONT = WindowManager.TRANSIT_TASK_TO_FRONT;
    public static final int TRANSIT_TASK_TO_BACK = WindowManager.TRANSIT_TASK_TO_BACK;
    public static final int TRANSIT_WALLPAPER_CLOSE = WindowManager.TRANSIT_WALLPAPER_CLOSE;
    public static final int TRANSIT_WALLPAPER_OPEN = WindowManager.TRANSIT_WALLPAPER_OPEN;
    public static final int TRANSIT_WALLPAPER_INTRA_OPEN =
            WindowManager.TRANSIT_WALLPAPER_INTRA_OPEN;
    public static final int TRANSIT_WALLPAPER_INTRA_CLOSE =
            WindowManager.TRANSIT_WALLPAPER_INTRA_CLOSE;
    public static final int TRANSIT_TASK_OPEN_BEHIND = WindowManager.TRANSIT_TASK_OPEN_BEHIND;
    public static final int TRANSIT_TASK_IN_PLACE = WindowManager.TRANSIT_TASK_IN_PLACE;
    public static final int TRANSIT_ACTIVITY_RELAUNCH = WindowManager.TRANSIT_ACTIVITY_RELAUNCH;
    public static final int TRANSIT_DOCK_TASK_FROM_RECENTS =
            WindowManager.TRANSIT_DOCK_TASK_FROM_RECENTS;
    public static final int TRANSIT_KEYGUARD_GOING_AWAY = WindowManager.TRANSIT_KEYGUARD_GOING_AWAY;
    public static final int TRANSIT_KEYGUARD_GOING_AWAY_ON_WALLPAPER =
            WindowManager.TRANSIT_KEYGUARD_GOING_AWAY_ON_WALLPAPER;
    public static final int TRANSIT_KEYGUARD_OCCLUDE = WindowManager.TRANSIT_KEYGUARD_OCCLUDE;
    public static final int TRANSIT_KEYGUARD_UNOCCLUDE = WindowManager.TRANSIT_KEYGUARD_UNOCCLUDE;

    public static final int NAV_BAR_POS_INVALID = -1;
    public static final int NAV_BAR_POS_LEFT = NAV_BAR_LEFT;
    public static final int NAV_BAR_POS_RIGHT = NAV_BAR_RIGHT;
    public static final int NAV_BAR_POS_BOTTOM = NAV_BAR_BOTTOM;

    public static final int ACTIVITY_TYPE_STANDARD = WindowConfiguration.ACTIVITY_TYPE_STANDARD;

    public static final int WINDOWING_MODE_UNDEFINED = WindowConfiguration.WINDOWING_MODE_UNDEFINED;
    public static final int WINDOWING_MODE_FULLSCREEN =
            WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
    public static final int WINDOWING_MODE_PINNED = WindowConfiguration.WINDOWING_MODE_PINNED;
    public static final int WINDOWING_MODE_SPLIT_SCREEN_PRIMARY =
            WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_PRIMARY;
    public static final int WINDOWING_MODE_SPLIT_SCREEN_SECONDARY =
            WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_SECONDARY;
    public static final int WINDOWING_MODE_FREEFORM = WindowConfiguration.WINDOWING_MODE_FREEFORM;

    private static final WindowManagerWrapper sInstance = new WindowManagerWrapper();

    public static WindowManagerWrapper getInstance() {
        return sInstance;
    }

    /**
     * @return the stable insets for the primary display.
     */
    public void getStableInsets(Rect outStableInsets) {
        try {
            WindowManagerGlobal.getWindowManagerService().getStableInsets(DEFAULT_DISPLAY,
                    outStableInsets);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get stable insets", e);
        }
    }

    /**
     * Overrides a pending app transition.
     */
    public void overridePendingAppTransitionMultiThumbFuture(
            AppTransitionAnimationSpecsFuture animationSpecFuture,
            Runnable animStartedCallback, Handler animStartedCallbackHandler, boolean scaleUp) {
        try {
            WindowManagerGlobal.getWindowManagerService()
                    .overridePendingAppTransitionMultiThumbFuture(animationSpecFuture.getFuture(),
                            RecentsTransition.wrapStartedListener(animStartedCallbackHandler,
                                    animStartedCallback), scaleUp);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to override pending app transition (multi-thumbnail future): ", e);
        }
    }

    public void overridePendingAppTransitionRemote(
            RemoteAnimationAdapterCompat remoteAnimationAdapter) {
        try {
            WindowManagerGlobal.getWindowManagerService().overridePendingAppTransitionRemote(
                    remoteAnimationAdapter.getWrapped());
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to override pending app transition (remote): ", e);
        }
    }

    /**
     * Enable or disable haptic feedback on the navigation bar buttons.
     */
    public void setNavBarVirtualKeyHapticFeedbackEnabled(boolean enabled) {
        try {
            WindowManagerGlobal.getWindowManagerService()
                    .setNavBarVirtualKeyHapticFeedbackEnabled(enabled);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to enable or disable navigation bar button haptics: ", e);
        }
    }

    public void setShelfHeight(boolean visible, int shelfHeight) {
        try {
            WindowManagerGlobal.getWindowManagerService().setShelfHeight(visible, shelfHeight);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to set shelf height");
        }
    }

    public void setRecentsVisibility(boolean visible) {
        try {
            WindowManagerGlobal.getWindowManagerService().setRecentsVisibility(visible);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to set recents visibility");
        }
    }

    /**
     * @return The side of the screen where navigation bar is positioned.
     * @see #NAV_BAR_POS_RIGHT
     * @see #NAV_BAR_POS_LEFT
     * @see #NAV_BAR_POS_BOTTOM
     * @see #NAV_BAR_POS_INVALID
     */
    public int getNavBarPosition() {
        try {
            return WindowManagerGlobal.getWindowManagerService().getNavBarPosition();
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to get nav bar position");
        }
        return NAV_BAR_POS_INVALID;
    }
}
