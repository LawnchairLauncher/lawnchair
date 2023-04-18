/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.statehandlers;

import android.os.SystemProperties;
import android.util.Log;
import android.view.View;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.uioverrides.QuickstepLauncher;

/**
 * Controls the visibility of the workspace and the resumed / paused state when desktop mode
 * is enabled.
 */
public class DesktopVisibilityController {

    private static final String TAG = "DesktopVisController";
    private static final boolean DEBUG = false;

    private final Launcher mLauncher;

    private boolean mFreeformTasksVisible;
    private boolean mInOverviewState;
    private boolean mGestureInProgress;

    public DesktopVisibilityController(Launcher launcher) {
        mLauncher = launcher;
    }

    /**
     * Whether desktop mode is supported.
     */
    private boolean isDesktopModeSupported() {
        return SystemProperties.getBoolean("persist.wm.debug.desktop_mode", false)
                || SystemProperties.getBoolean("persist.wm.debug.desktop_mode_2", false);
    }

    /**
     * Whether freeform windows are visible in desktop mode.
     */
    public boolean areFreeformTasksVisible() {
        return mFreeformTasksVisible;
    }

    /**
     * Sets whether freeform windows are visible and updates launcher visibility based on that.
     */
    public void setFreeformTasksVisible(boolean freeformTasksVisible) {
        if (DEBUG) {
            Log.d(TAG, "setFreeformTasksVisible: visible=" + freeformTasksVisible);
        }
        if (!isDesktopModeSupported()) {
            return;
        }
        if (freeformTasksVisible != mFreeformTasksVisible) {
            mFreeformTasksVisible = freeformTasksVisible;
            if (mFreeformTasksVisible) {
                setLauncherViewsVisibility(View.INVISIBLE);
                if (!mInOverviewState) {
                    // When freeform is visible & we're not in overview, we want launcher to appear
                    // paused, this ensures that taskbar displays.
                    markLauncherPaused();
                }
            } else {
                setLauncherViewsVisibility(View.VISIBLE);
                // If freeform isn't visible ensure that launcher appears resumed to behave
                // normally.
                markLauncherResumed();
            }
        }
    }

    /**
     * Sets whether the overview is visible and updates launcher visibility based on that.
     */
    public void setOverviewStateEnabled(boolean overviewStateEnabled) {
        if (DEBUG) {
            Log.d(TAG, "setOverviewStateEnabled: enabled=" + overviewStateEnabled);
        }
        if (!isDesktopModeSupported()) {
            return;
        }
        if (overviewStateEnabled != mInOverviewState) {
            mInOverviewState = overviewStateEnabled;
            if (mInOverviewState) {
                setLauncherViewsVisibility(View.VISIBLE);
                markLauncherResumed();
            } else if (mFreeformTasksVisible) {
                setLauncherViewsVisibility(View.INVISIBLE);
                markLauncherPaused();
            }
        }
    }

    /**
     * Whether recents gesture is currently in progress.
     */
    public boolean isGestureInProgress() {
        return mGestureInProgress;
    }

    /**
     * Sets whether recents gesture is in progress.
     */
    public void setGestureInProgress(boolean gestureInProgress) {
        if (DEBUG) {
            Log.d(TAG, "setGestureInProgress: inProgress=" + gestureInProgress);
        }
        if (!isDesktopModeSupported()) {
            return;
        }
        if (gestureInProgress != mGestureInProgress) {
            mGestureInProgress = gestureInProgress;
        }
    }

    private void setLauncherViewsVisibility(int visibility) {
        if (DEBUG) {
            Log.d(TAG, "setLauncherViewsVisibility: visibility=" + visibility);
        }
        View workspaceView = mLauncher.getWorkspace();
        if (workspaceView != null) {
            workspaceView.setVisibility(visibility);
        }
        View dragLayer = mLauncher.getDragLayer();
        if (dragLayer != null) {
            dragLayer.setVisibility(visibility);
        }
    }

    private void markLauncherPaused() {
        if (DEBUG) {
            Log.d(TAG, "markLauncherPaused");
        }
        StatefulActivity<LauncherState> activity =
                QuickstepLauncher.ACTIVITY_TRACKER.getCreatedActivity();
        if (activity != null) {
            activity.setPaused();
        }
    }

    private void markLauncherResumed() {
        if (DEBUG) {
            Log.d(TAG, "markLauncherResumed");
        }
        StatefulActivity<LauncherState> activity =
                QuickstepLauncher.ACTIVITY_TRACKER.getCreatedActivity();
        // Check activity state before calling setResumed(). Launcher may have been actually
        // paused (eg fullscreen task moved to front).
        // In this case we should not mark the activity as resumed.
        if (activity != null && activity.isResumed()) {
            activity.setResumed();
        }
    }
}
