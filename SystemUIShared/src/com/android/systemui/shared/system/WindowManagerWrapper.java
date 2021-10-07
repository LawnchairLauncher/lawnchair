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
import android.view.InsetsState;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;

import com.android.systemui.shared.recents.view.AppTransitionAnimationSpecsFuture;
import com.android.systemui.shared.recents.view.RecentsTransition;

public class WindowManagerWrapper {

    private static final String TAG = "WindowManagerWrapper";

    public static final int TRANSIT_UNSET = WindowManager.TRANSIT_OLD_UNSET;
    public static final int TRANSIT_NONE = WindowManager.TRANSIT_OLD_NONE;
    public static final int TRANSIT_ACTIVITY_OPEN = WindowManager.TRANSIT_OLD_ACTIVITY_OPEN;
    public static final int TRANSIT_ACTIVITY_CLOSE = WindowManager.TRANSIT_OLD_ACTIVITY_CLOSE;
    public static final int TRANSIT_TASK_OPEN = WindowManager.TRANSIT_OLD_TASK_OPEN;
    public static final int TRANSIT_TASK_CLOSE = WindowManager.TRANSIT_OLD_TASK_CLOSE;
    public static final int TRANSIT_TASK_TO_FRONT = WindowManager.TRANSIT_OLD_TASK_TO_FRONT;
    public static final int TRANSIT_TASK_TO_BACK = WindowManager.TRANSIT_OLD_TASK_TO_BACK;
    public static final int TRANSIT_WALLPAPER_CLOSE = WindowManager.TRANSIT_OLD_WALLPAPER_CLOSE;
    public static final int TRANSIT_WALLPAPER_OPEN = WindowManager.TRANSIT_OLD_WALLPAPER_OPEN;
    public static final int TRANSIT_WALLPAPER_INTRA_OPEN =
            WindowManager.TRANSIT_OLD_WALLPAPER_INTRA_OPEN;
    public static final int TRANSIT_WALLPAPER_INTRA_CLOSE =
            WindowManager.TRANSIT_OLD_WALLPAPER_INTRA_CLOSE;
    public static final int TRANSIT_TASK_OPEN_BEHIND = WindowManager.TRANSIT_OLD_TASK_OPEN_BEHIND;
    public static final int TRANSIT_ACTIVITY_RELAUNCH = WindowManager.TRANSIT_OLD_ACTIVITY_RELAUNCH;
    public static final int TRANSIT_KEYGUARD_GOING_AWAY =
            WindowManager.TRANSIT_OLD_KEYGUARD_GOING_AWAY;
    public static final int TRANSIT_KEYGUARD_GOING_AWAY_ON_WALLPAPER =
            WindowManager.TRANSIT_OLD_KEYGUARD_GOING_AWAY_ON_WALLPAPER;
    public static final int TRANSIT_KEYGUARD_OCCLUDE = WindowManager.TRANSIT_OLD_KEYGUARD_OCCLUDE;
    public static final int TRANSIT_KEYGUARD_UNOCCLUDE =
            WindowManager.TRANSIT_OLD_KEYGUARD_UNOCCLUDE;

    public static final int NAV_BAR_POS_INVALID = NAV_BAR_INVALID;
    public static final int NAV_BAR_POS_LEFT = NAV_BAR_LEFT;
    public static final int NAV_BAR_POS_RIGHT = NAV_BAR_RIGHT;
    public static final int NAV_BAR_POS_BOTTOM = NAV_BAR_BOTTOM;

    public static final int ACTIVITY_TYPE_STANDARD = WindowConfiguration.ACTIVITY_TYPE_STANDARD;

    public static final int WINDOWING_MODE_UNDEFINED = WindowConfiguration.WINDOWING_MODE_UNDEFINED;
    public static final int WINDOWING_MODE_FULLSCREEN =
            WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
    public static final int WINDOWING_MODE_MULTI_WINDOW =
            WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;

    public static final int WINDOWING_MODE_SPLIT_SCREEN_PRIMARY =
            WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_PRIMARY;
    public static final int WINDOWING_MODE_SPLIT_SCREEN_SECONDARY =
            WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_SECONDARY;
    public static final int WINDOWING_MODE_FREEFORM = WindowConfiguration.WINDOWING_MODE_FREEFORM;

    public static final int ITYPE_EXTRA_NAVIGATION_BAR = InsetsState.ITYPE_EXTRA_NAVIGATION_BAR;
    public static final int ITYPE_LEFT_TAPPABLE_ELEMENT = InsetsState.ITYPE_LEFT_TAPPABLE_ELEMENT;
    public static final int ITYPE_TOP_TAPPABLE_ELEMENT = InsetsState.ITYPE_TOP_TAPPABLE_ELEMENT;
    public static final int ITYPE_RIGHT_TAPPABLE_ELEMENT = InsetsState.ITYPE_RIGHT_TAPPABLE_ELEMENT;
    public static final int ITYPE_BOTTOM_TAPPABLE_ELEMENT =
            InsetsState.ITYPE_BOTTOM_TAPPABLE_ELEMENT;

    private static final WindowManagerWrapper sInstance = new WindowManagerWrapper();

    public static WindowManagerWrapper getInstance() {
        return sInstance;
    }


    /**
     * Sets {@param providesInsetsTypes} as the inset types provided by {@param params}.
     * @param params The window layout params.
     * @param providesInsetsTypes The inset types we would like this layout params to provide.
     */
    public void setProvidesInsetsTypes(WindowManager.LayoutParams params,
            int[] providesInsetsTypes) {
        params.providesInsetsTypes = providesInsetsTypes;
    }

    /**
     *  Sets if app requested fixed orientation should be ignored for given displayId.
     */
    public void setIgnoreOrientationRequest(int displayId, boolean ignoreOrientationRequest) {
        try {
            WindowManagerGlobal.getWindowManagerService().setIgnoreOrientationRequest(
                    displayId, ignoreOrientationRequest);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to setIgnoreOrientationRequest()", e);
        }
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

    public void setRecentsVisibility(boolean visible) {
        try {
            WindowManagerGlobal.getWindowManagerService().setRecentsVisibility(visible);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to set recents visibility");
        }
    }

    @Deprecated
    public void setPipVisibility(final boolean visible) {
        // To be removed
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
     * Mirrors a specified display. The SurfaceControl returned is the root of the mirrored
     * hierarchy.
     *
     * @param displayId The id of the display to mirror
     * @return The SurfaceControl for the root of the mirrored hierarchy.
     */
    public SurfaceControl mirrorDisplay(final int displayId) {
        try {
            SurfaceControl outSurfaceControl = new SurfaceControl();
            WindowManagerGlobal.getWindowManagerService().mirrorDisplay(displayId,
                    outSurfaceControl);
            return outSurfaceControl;
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to reach window manager", e);
        }
        return null;
    }
}
