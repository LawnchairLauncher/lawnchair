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
import static android.view.WindowManagerPolicyConstants.NAV_BAR_BOTTOM;
import static android.view.WindowManagerPolicyConstants.NAV_BAR_INVALID;
import static android.view.WindowManagerPolicyConstants.NAV_BAR_LEFT;
import static android.view.WindowManagerPolicyConstants.NAV_BAR_RIGHT;

import android.app.WindowConfiguration;
import android.graphics.Rect;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.IPinnedStackListener;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;

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

    public static final int NAV_BAR_POS_INVALID = NAV_BAR_INVALID;
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

    /**
     * Forwarder to which we can add multiple pinned stack listeners. Each listener will receive
     * updates from the window manager service.
     */
    private PinnedStackListenerForwarder mPinnedStackListenerForwarder =
            new PinnedStackListenerForwarder();

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
            AppTransitionAnimationSpecsFuture animationSpecFuture, Runnable animStartedCallback,
            Handler animStartedCallbackHandler, boolean scaleUp, int displayId) {
        try {
            WindowManagerGlobal.getWindowManagerService()
                    .overridePendingAppTransitionMultiThumbFuture(animationSpecFuture.getFuture(),
                            RecentsTransition.wrapStartedListener(animStartedCallbackHandler,
                                    animStartedCallback), scaleUp, displayId);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to override pending app transition (multi-thumbnail future): ", e);
        }
    }

    public void overridePendingAppTransitionRemote(
            RemoteAnimationAdapterCompat remoteAnimationAdapter, int displayId) {
        try {
            WindowManagerGlobal.getWindowManagerService().overridePendingAppTransitionRemote(
                    remoteAnimationAdapter.getWrapped(), displayId);
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

    public void setPipVisibility(final boolean visible) {
        try {
            WindowManagerGlobal.getWindowManagerService().setPipVisibility(visible);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to reach window manager", e);
        }
    }

    /**
     * @param displayId the id of display to check if there is a software navigation bar.
     *
     * @return whether there is a soft nav bar on specific display.
     */
    public boolean hasSoftNavigationBar(int displayId) {
        try {
            return WindowManagerGlobal.getWindowManagerService().hasNavigationBar(displayId);
        } catch (RemoteException e) {
            return false;
        }
    }

    /**
     * @return The side of the screen where navigation bar is positioned.
     * @see #NAV_BAR_POS_RIGHT
     * @see #NAV_BAR_POS_LEFT
     * @see #NAV_BAR_POS_BOTTOM
     * @see #NAV_BAR_POS_INVALID
     */
    public int getNavBarPosition(int displayId) {
        try {
            return WindowManagerGlobal.getWindowManagerService().getNavBarPosition(displayId);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to get nav bar position");
        }
        return NAV_BAR_POS_INVALID;
    }

    /**
     * Registers a docked stack listener with the system.
     */
    public void registerDockedStackListener(DockedStackListenerCompat listener) {
        try {
            WindowManagerGlobal.getWindowManagerService().registerDockedStackListener(
                    listener.mListener);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to register docked stack listener");
        }
    }

    /**
     * Adds a pinned stack listener, which will receive updates from the window manager service
     * along with any other pinned stack listeners that were added via this method.
     */
    public void addPinnedStackListener(IPinnedStackListener listener) throws RemoteException {
        mPinnedStackListenerForwarder.addListener(listener);
        WindowManagerGlobal.getWindowManagerService().registerPinnedStackListener(
                DEFAULT_DISPLAY, mPinnedStackListenerForwarder);
    }

    /**
     * Removes a pinned stack listener.
     */
    public void removePinnedStackListener(IPinnedStackListener listener) {
        mPinnedStackListenerForwarder.removeListener(listener);
    }
}
