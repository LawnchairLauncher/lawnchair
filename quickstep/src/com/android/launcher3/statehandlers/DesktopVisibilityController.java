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

    private final Launcher mLauncher;

    private boolean mFreeformTasksVisible;
    private boolean mInOverviewState;

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
        if (freeformTasksVisible != mFreeformTasksVisible) {
            mFreeformTasksVisible = freeformTasksVisible;
            updateLauncherVisibility();
        }
    }

    /**
     * Sets whether the overview is visible and updates launcher visibility based on that.
     */
    public void setOverviewStateEnabled(boolean overviewStateEnabled) {
        if (overviewStateEnabled != mInOverviewState) {
            mInOverviewState = overviewStateEnabled;
            updateLauncherVisibility();
        }
    }

    /**
     * Updates launcher visibility and state to look like it is paused or resumed depending on
     * whether freeform windows are showing in desktop mode.
     */
    private void updateLauncherVisibility() {
        StatefulActivity<LauncherState> activity =
                QuickstepLauncher.ACTIVITY_TRACKER.getCreatedActivity();
        View workspaceView = mLauncher.getWorkspace();
        if (activity == null || workspaceView == null || !isDesktopModeSupported()) {
            return;
        }

        if (mFreeformTasksVisible) {
            workspaceView.setVisibility(View.INVISIBLE);
            if (!mInOverviewState) {
                // When freeform is visible & we're not in overview, we want launcher to appear
                // paused, this ensures that taskbar displays.
                activity.setPaused();
            }
        } else {
            workspaceView.setVisibility(View.VISIBLE);
            // If freeform isn't visible ensure that launcher appears resumed to behave normally.
            // Check activity state before calling setResumed(). Launcher may have been actually
            // paused (eg fullscreen task moved to front).
            // In this case we should not mark the activity as resumed.
            if (activity.isResumed()) {
                activity.setResumed();
            }
        }
    }
}
