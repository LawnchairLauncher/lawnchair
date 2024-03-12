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

import static android.view.View.VISIBLE;

import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.window.flags.Flags.enableDesktopWindowingWallpaperActivity;

import android.os.Debug;
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

import java.util.HashSet;
import java.util.Set;

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
    private final Set<DesktopVisibilityListener> mDesktopVisibilityListeners = new HashSet<>();

    private int mVisibleDesktopTasksCount;
    private boolean mInOverviewState;
    private boolean mBackgroundStateEnabled;
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
            public void onTasksVisibilityChanged(int displayId, int visibleTasksCount) {
                MAIN_EXECUTOR.execute(() -> {
                    if (displayId == mLauncher.getDisplayId()) {
                        if (DEBUG) {
                            Log.d(TAG, "desktop visible tasks count changed=" + visibleTasksCount);
                        }
                        setVisibleDesktopTasksCount(visibleTasksCount);
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
     * Whether desktop tasks are visible in desktop mode.
     */
    public boolean areDesktopTasksVisible() {
        boolean desktopTasksVisible = mVisibleDesktopTasksCount > 0;
        if (DEBUG) {
            Log.d(TAG, "areDesktopTasksVisible: desktopVisible=" + desktopTasksVisible
                    + " overview=" + mInOverviewState);
        }
        return desktopTasksVisible && !mInOverviewState;
    }

    /**
     * Number of visible desktop windows in desktop mode.
     */
    public int getVisibleDesktopTasksCount() {
        return mVisibleDesktopTasksCount;
    }

    /** Registers a listener for Desktop Mode visibility updates. */
    public void registerDesktopVisibilityListener(DesktopVisibilityListener listener) {
        mDesktopVisibilityListeners.add(listener);
    }

    /** Removes a previously registered Desktop Mode visibility listener. */
    public void unregisterDesktopVisibilityListener(DesktopVisibilityListener listener) {
        mDesktopVisibilityListeners.remove(listener);
    }

    /**
     * Sets the number of desktop windows that are visible and updates launcher visibility based on
     * it.
     */
    public void setVisibleDesktopTasksCount(int visibleTasksCount) {
        if (DEBUG) {
            Log.d(TAG, "setVisibleDesktopTasksCount: visibleTasksCount=" + visibleTasksCount
                    + " currentValue=" + mVisibleDesktopTasksCount);
        }

        if (visibleTasksCount != mVisibleDesktopTasksCount) {
            final boolean wasVisible = mVisibleDesktopTasksCount > 0;
            final boolean isVisible = visibleTasksCount > 0;
            final boolean wereDesktopTasksVisibleBefore = areDesktopTasksVisible();
            mVisibleDesktopTasksCount = visibleTasksCount;
            final boolean areDesktopTasksVisibleNow = areDesktopTasksVisible();
            if (wereDesktopTasksVisibleBefore != areDesktopTasksVisibleNow) {
                notifyDesktopVisibilityListeners(areDesktopTasksVisibleNow);
            }

            if (!enableDesktopWindowingWallpaperActivity() && wasVisible != isVisible) {
                // TODO: b/333533253 - Remove after flag rollout
                if (mVisibleDesktopTasksCount > 0) {
                    setLauncherViewsVisibility(View.INVISIBLE);
                    if (!mInOverviewState) {
                        // When desktop tasks are visible & we're not in overview, we want launcher
                        // to appear paused, this ensures that taskbar displays.
                        markLauncherPaused();
                    }
                } else {
                    setLauncherViewsVisibility(View.VISIBLE);
                    // If desktop tasks aren't visible, ensure that launcher appears resumed to
                    // behave normally.
                    markLauncherResumed();
                }
            }
        }
    }

    /**
     * Process launcher state change and update launcher view visibility based on desktop state
     */
    public void onLauncherStateChanged(LauncherState state) {
        if (DEBUG) {
            Log.d(TAG, "onLauncherStateChanged: newState=" + state);
        }
        setBackgroundStateEnabled(state == BACKGROUND_APP);
        // Desktop visibility tracks overview and background state separately
        setOverviewStateEnabled(state != BACKGROUND_APP && state.overviewUi);
    }

    private void setOverviewStateEnabled(boolean overviewStateEnabled) {
        if (DEBUG) {
            Log.d(TAG, "setOverviewStateEnabled: enabled=" + overviewStateEnabled
                    + " currentValue=" + mInOverviewState);
        }
        if (overviewStateEnabled != mInOverviewState) {
            final boolean wereDesktopTasksVisibleBefore = areDesktopTasksVisible();
            mInOverviewState = overviewStateEnabled;
            final boolean areDesktopTasksVisibleNow = areDesktopTasksVisible();
            if (wereDesktopTasksVisibleBefore != areDesktopTasksVisibleNow) {
                notifyDesktopVisibilityListeners(areDesktopTasksVisibleNow);
            }

            if (enableDesktopWindowingWallpaperActivity()) {
                return;
            }
            // TODO: b/333533253 - Clean up after flag rollout

            if (mInOverviewState) {
                setLauncherViewsVisibility(View.VISIBLE);
                markLauncherResumed();
            } else if (areDesktopTasksVisibleNow && !mGestureInProgress) {
                // Switching out of overview state and gesture finished.
                // If desktop tasks are still visible, hide launcher again.
                setLauncherViewsVisibility(View.INVISIBLE);
                markLauncherPaused();
            }
        }
    }

    private void notifyDesktopVisibilityListeners(boolean areDesktopTasksVisible) {
        if (DEBUG) {
            Log.d(TAG, "notifyDesktopVisibilityListeners: visible=" + areDesktopTasksVisible);
        }
        for (DesktopVisibilityListener listener : mDesktopVisibilityListeners) {
            listener.onDesktopVisibilityChanged(areDesktopTasksVisible);
        }
    }

    /**
     * TODO: b/333533253 - Remove after flag rollout
     */
    private void setBackgroundStateEnabled(boolean backgroundStateEnabled) {
        if (DEBUG) {
            Log.d(TAG, "setBackgroundStateEnabled: enabled=" + backgroundStateEnabled
                    + " currentValue=" + mBackgroundStateEnabled);
        }
        if (backgroundStateEnabled != mBackgroundStateEnabled) {
            mBackgroundStateEnabled = backgroundStateEnabled;
            if (mBackgroundStateEnabled) {
                setLauncherViewsVisibility(View.VISIBLE);
                markLauncherResumed();
            } else if (areDesktopTasksVisible() && !mGestureInProgress) {
                // Switching out of background state. If desktop tasks are visible, pause launcher.
                setLauncherViewsVisibility(View.INVISIBLE);
                markLauncherPaused();
            }
        }
    }

    /**
     * Whether recents gesture is currently in progress.
     *
     * TODO: b/333533253 - Remove after flag rollout
     */
    public boolean isRecentsGestureInProgress() {
        return mGestureInProgress;
    }

    /**
     * Notify controller that recents gesture has started.
     *
     * TODO: b/333533253 - Remove after flag rollout
     */
    public void setRecentsGestureStart() {
        if (DEBUG) {
            Log.d(TAG, "setRecentsGestureStart");
        }
        setRecentsGestureInProgress(true);
    }

    /**
     * Notify controller that recents gesture finished with the given
     * {@link com.android.quickstep.GestureState.GestureEndTarget}
     *
     * TODO: b/333533253 - Remove after flag rollout
     */
    public void setRecentsGestureEnd(@Nullable GestureState.GestureEndTarget endTarget) {
        if (DEBUG) {
            Log.d(TAG, "setRecentsGestureEnd: endTarget=" + endTarget);
        }
        setRecentsGestureInProgress(false);

        if (endTarget == null) {
            // Gesture did not result in a new end target. Ensure launchers gets paused again.
            markLauncherPaused();
        }
    }

    /**
     * TODO: b/333533253 - Remove after flag rollout
     */
    private void setRecentsGestureInProgress(boolean gestureInProgress) {
        if (gestureInProgress != mGestureInProgress) {
            mGestureInProgress = gestureInProgress;
        }
    }

    /**
     * Handle launcher moving to home due to home gesture or home button press.
     */
    public void onHomeActionTriggered() {
        if (IS_STASHING_ENABLED && areDesktopTasksVisible()) {
            SystemUiProxy.INSTANCE.get(mLauncher).stashDesktopApps(mLauncher.getDisplayId());
        }
    }

    /**
     * TODO: b/333533253 - Remove after flag rollout
     */
    private void setLauncherViewsVisibility(int visibility) {
        if (enableDesktopWindowingWallpaperActivity()) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "setLauncherViewsVisibility: visibility=" + visibility + " "
                    + Debug.getCaller());
        }
        View workspaceView = mLauncher.getWorkspace();
        if (workspaceView != null) {
            workspaceView.setVisibility(visibility);
        }
        View dragLayer = mLauncher.getDragLayer();
        if (dragLayer != null) {
            dragLayer.setVisibility(visibility);
        }
        if (mLauncher instanceof QuickstepLauncher ql && ql.getTaskbarUIController() != null
                && mVisibleDesktopTasksCount != 0) {
            ql.getTaskbarUIController().onLauncherVisibilityChanged(visibility == VISIBLE);
        }
    }

    /**
     * TODO: b/333533253 - Remove after flag rollout
     */
    private void markLauncherPaused() {
        if (enableDesktopWindowingWallpaperActivity()) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "markLauncherPaused " + Debug.getCaller());
        }
        StatefulActivity<LauncherState> activity =
                QuickstepLauncher.ACTIVITY_TRACKER.getCreatedActivity();
        if (activity != null) {
            activity.setPaused();
        }
    }

    /**
     * TODO: b/333533253 - Remove after flag rollout
     */
    private void markLauncherResumed() {
        if (enableDesktopWindowingWallpaperActivity()) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "markLauncherResumed " + Debug.getCaller());
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

    /** A listener for when the user enters/exits Desktop Mode. */
    public interface DesktopVisibilityListener {
        /**
         * Callback for when the user enters or exits Desktop Mode
         *
         * @param visible whether Desktop Mode is now visible
         */
        void onDesktopVisibilityChanged(boolean visible);
    }
}
