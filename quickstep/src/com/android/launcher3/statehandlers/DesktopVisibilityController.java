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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.os.SystemProperties;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.quickstep.GestureState;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.views.DesktopAppSelectView;
import com.android.wm.shell.desktopmode.IDesktopTaskListener;

/**
 * Controls the visibility of the workspace and the resumed / paused state when desktop mode
 * is enabled.
 */
public class DesktopVisibilityController {

    private static final String TAG = "DesktopVisController";
    private static final boolean DEBUG = false;
    private static final boolean IS_STASHING_ENABLED = SystemProperties.getBoolean(
            "persist.wm.debug.desktop_stashing", false);
    private final Launcher mLauncher;

    private boolean mFreeformTasksVisible;
    private boolean mInOverviewState;
    private boolean mGestureInProgress;

    @Nullable
    private IDesktopTaskListener mDesktopTaskListener;
    private DesktopAppSelectView mSelectAppToast;

    public DesktopVisibilityController(Launcher launcher) {
        mLauncher = launcher;
    }

    /**
     * Register a listener with System UI to receive updates about desktop tasks state
     */
    public void registerSystemUiListener() {
        mDesktopTaskListener = new IDesktopTaskListener.Stub() {
            @Override
            public void onVisibilityChanged(int displayId, boolean visible) {
                MAIN_EXECUTOR.execute(() -> {
                    if (displayId == mLauncher.getDisplayId()) {
                        if (DEBUG) {
                            Log.d(TAG, "desktop visibility changed value=" + visible);
                        }
                        setFreeformTasksVisible(visible);
                    }
                });
            }

            @Override
            public void onStashedChanged(int displayId, boolean stashed) {
                if (!IS_STASHING_ENABLED) {
                    return;
                }
                MAIN_EXECUTOR.execute(() -> {
                    if (displayId == mLauncher.getDisplayId()) {
                        if (DEBUG) {
                            Log.d(TAG, "desktop stashed changed value=" + stashed);
                        }
                        if (stashed) {
                            showSelectAppToast();
                        } else {
                            hideSelectAppToast();
                        }
                    }
                });
            }
        };
        SystemUiProxy.INSTANCE.get(mLauncher).setDesktopTaskListener(mDesktopTaskListener);
    }

    /**
     * Clear listener from System UI that was set with {@link #registerSystemUiListener()}
     */
    public void unregisterSystemUiListener() {
        SystemUiProxy.INSTANCE.get(mLauncher).setDesktopTaskListener(null);
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
    public boolean isRecentsGestureInProgress() {
        return mGestureInProgress;
    }

    /**
     * Notify controller that recents gesture has started.
     */
    public void setRecentsGestureStart() {
        if (!isDesktopModeSupported()) {
            return;
        }
        setRecentsGestureInProgress(true);
    }

    /**
     * Notify controller that recents gesture finished with the given
     * {@link com.android.quickstep.GestureState.GestureEndTarget}
     */
    public void setRecentsGestureEnd(@Nullable GestureState.GestureEndTarget endTarget) {
        if (!isDesktopModeSupported()) {
            return;
        }
        setRecentsGestureInProgress(false);

        if (endTarget == null) {
            // Gesture did not result in a new end target. Ensure launchers gets paused again.
            markLauncherPaused();
        }
    }

    private void setRecentsGestureInProgress(boolean gestureInProgress) {
        if (DEBUG) {
            Log.d(TAG, "setGestureInProgress: inProgress=" + gestureInProgress);
        }
        if (gestureInProgress != mGestureInProgress) {
            mGestureInProgress = gestureInProgress;
        }
    }

    /**
     * Handle launcher moving to home due to home gesture or home button press.
     */
    public void onHomeActionTriggered() {
        if (IS_STASHING_ENABLED && areFreeformTasksVisible()) {
            SystemUiProxy.INSTANCE.get(mLauncher).stashDesktopApps(mLauncher.getDisplayId());
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

    private void showSelectAppToast() {
        if (mSelectAppToast != null) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "show toast to select desktop apps");
        }
        Runnable onCloseCallback = () -> {
            SystemUiProxy.INSTANCE.get(mLauncher).hideStashedDesktopApps(mLauncher.getDisplayId());
        };
        mSelectAppToast = DesktopAppSelectView.show(mLauncher, onCloseCallback);
    }

    private void hideSelectAppToast() {
        if (mSelectAppToast == null) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "hide toast to select desktop apps");
        }
        mSelectAppToast.hide();
        mSelectAppToast = null;
    }
}
