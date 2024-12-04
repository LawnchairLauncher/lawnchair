/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.compatui;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.CameraCompatTaskInfo.CameraCompatControlState;
import android.app.TaskInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.Display;
import android.view.InsetsSourceControl;
import android.view.InsetsState;
import android.view.accessibility.AccessibilityManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayController.OnDisplaysChangedListener;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.DisplayInsetsController.OnInsetsChangedListener;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.DockStateReader;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.sysui.KeyguardChangeListener;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;

import dagger.Lazy;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Controller to show/update compat UI components on Tasks based on whether the foreground
 * activities are in compatibility mode.
 */
public class CompatUIController implements OnDisplaysChangedListener,
        DisplayImeController.ImePositionProcessor, KeyguardChangeListener {

    /** Callback for compat UI interaction. */
    public interface CompatUICallback {
        /** Called when the size compat restart button appears. */
        void onSizeCompatRestartButtonAppeared(int taskId);
        /** Called when the size compat restart button is clicked. */
        void onSizeCompatRestartButtonClicked(int taskId);
        /** Called when the camera compat control state is updated. */
        void onCameraControlStateUpdated(int taskId, @CameraCompatControlState int state);
    }

    private static final String TAG = "CompatUIController";

    // The time to wait before education and button hiding
    private static final int DISAPPEAR_DELAY_MS = 5000;

    /** Whether the IME is shown on display id. */
    private final Set<Integer> mDisplaysWithIme = new ArraySet<>(1);

    /** {@link PerDisplayOnInsetsChangedListener} by display id. */
    private final SparseArray<PerDisplayOnInsetsChangedListener> mOnInsetsChangedListeners =
            new SparseArray<>(0);

    /**
     * The active Compat Control UI layouts by task id.
     *
     * <p>An active layout is a layout that is eligible to be shown for the associated task but
     * isn't necessarily shown at a given time.
     */
    private final SparseArray<CompatUIWindowManager> mActiveCompatLayouts = new SparseArray<>(0);

    /**
     * {@link SparseArray} that maps task ids to {@link RestartDialogWindowManager} that are
     * currently visible
     */
    private final SparseArray<RestartDialogWindowManager> mTaskIdToRestartDialogWindowManagerMap =
            new SparseArray<>(0);

    /**
     * {@link Set} of task ids for which we need to display a restart confirmation dialog
     */
    private Set<Integer> mSetOfTaskIdsShowingRestartDialog = new HashSet<>();

    /**
     * The active user aspect ratio settings button layout if there is one (there can be at most
     * one active).
     */
    @Nullable
    private UserAspectRatioSettingsWindowManager mUserAspectRatioSettingsLayout;

    /**
     * The active Letterbox Education layout if there is one (there can be at most one active).
     *
     * <p>An active layout is a layout that is eligible to be shown for the associated task but
     * isn't necessarily shown at a given time.
     */
    @Nullable
    private LetterboxEduWindowManager mActiveLetterboxEduLayout;

    /**
     * The active Reachability UI layout.
     */
    @Nullable
    private ReachabilityEduWindowManager mActiveReachabilityEduLayout;

    /** Avoid creating display context frequently for non-default display. */
    private final SparseArray<WeakReference<Context>> mDisplayContextCache = new SparseArray<>(0);

    @NonNull
    private final Context mContext;
    @NonNull
    private final ShellController mShellController;
    @NonNull
    private final DisplayController mDisplayController;
    @NonNull
    private final DisplayInsetsController mDisplayInsetsController;
    @NonNull
    private final DisplayImeController mImeController;
    @NonNull
    private final SyncTransactionQueue mSyncQueue;
    @NonNull
    private final ShellExecutor mMainExecutor;
    @NonNull
    private final Lazy<Transitions> mTransitionsLazy;
    @NonNull
    private final DockStateReader mDockStateReader;
    @NonNull
    private final CompatUIConfiguration mCompatUIConfiguration;
    // Only show each hint once automatically in the process life.
    @NonNull
    private final CompatUIHintsState mCompatUIHintsState;
    @NonNull
    private final CompatUIShellCommandHandler mCompatUIShellCommandHandler;

    @NonNull
    private final Function<Integer, Integer> mDisappearTimeSupplier;

    @Nullable
    private CompatUICallback mCompatUICallback;

    // Indicates if the keyguard is currently showing, in which case compat UIs shouldn't
    // be shown.
    private boolean mKeyguardShowing;

    /**
     * The id of the task for the application we're currently attempting to show the user aspect
     * ratio settings button for, or have most recently shown the button for.
     */
    private int mTopActivityTaskId;

    /**
     * Whether the user aspect ratio settings button has been shown for the current application
     * associated with the task id stored in {@link CompatUIController#mTopActivityTaskId}.
     */
    private boolean mHasShownUserAspectRatioSettingsButton = false;

    /**
     * This is true when the rechability education is displayed for the first time.
     */
    private boolean mIsFirstReachabilityEducationRunning;

    public CompatUIController(@NonNull Context context,
            @NonNull ShellInit shellInit,
            @NonNull ShellController shellController,
            @NonNull DisplayController displayController,
            @NonNull DisplayInsetsController displayInsetsController,
            @NonNull DisplayImeController imeController,
            @NonNull SyncTransactionQueue syncQueue,
            @NonNull ShellExecutor mainExecutor,
            @NonNull Lazy<Transitions> transitionsLazy,
            @NonNull DockStateReader dockStateReader,
            @NonNull CompatUIConfiguration compatUIConfiguration,
            @NonNull CompatUIShellCommandHandler compatUIShellCommandHandler,
            @NonNull AccessibilityManager accessibilityManager) {
        mContext = context;
        mShellController = shellController;
        mDisplayController = displayController;
        mDisplayInsetsController = displayInsetsController;
        mImeController = imeController;
        mSyncQueue = syncQueue;
        mMainExecutor = mainExecutor;
        mTransitionsLazy = transitionsLazy;
        mCompatUIHintsState = new CompatUIHintsState();
        mDockStateReader = dockStateReader;
        mCompatUIConfiguration = compatUIConfiguration;
        mCompatUIShellCommandHandler = compatUIShellCommandHandler;
        mDisappearTimeSupplier = flags -> accessibilityManager.getRecommendedTimeoutMillis(
                DISAPPEAR_DELAY_MS, flags);
        shellInit.addInitCallback(this::onInit, this);
    }

    private void onInit() {
        mShellController.addKeyguardChangeListener(this);
        mDisplayController.addDisplayWindowListener(this);
        mImeController.addPositionProcessor(this);
        mCompatUIShellCommandHandler.onInit();
    }

    /** Sets the callback for Compat UI interactions. */
    public void setCompatUICallback(@NonNull CompatUICallback compatUiCallback) {
        mCompatUICallback = compatUiCallback;
    }

    /**
     * Called when the Task info changed. Creates and updates the compat UI if there is an
     * activity in size compat, or removes the UI if there is no size compat activity.
     *
     * @param taskInfo {@link TaskInfo} task the activity is in.
     * @param taskListener listener to handle the Task Surface placement.
     */
    public void onCompatInfoChanged(@NonNull TaskInfo taskInfo,
            @Nullable ShellTaskOrganizer.TaskListener taskListener) {
        if (taskInfo != null && !taskInfo.appCompatTaskInfo.topActivityInSizeCompat) {
            mSetOfTaskIdsShowingRestartDialog.remove(taskInfo.taskId);
        }

        if (taskInfo != null && taskListener != null) {
            updateActiveTaskInfo(taskInfo);
        }

        if (taskInfo.configuration == null || taskListener == null) {
            // Null token means the current foreground activity is not in compatibility mode.
            removeLayouts(taskInfo.taskId);
            return;
        }
        // We're showing the first reachability education so we ignore incoming TaskInfo
        // until the education flow has completed or we double tap. The double-tap
        // basically cancel all the onboarding flow. We don't have to ignore events in case
        // the app is in size compat mode.
        if (mIsFirstReachabilityEducationRunning) {
            if (!taskInfo.appCompatTaskInfo.isFromLetterboxDoubleTap
                    && !taskInfo.appCompatTaskInfo.topActivityInSizeCompat) {
                return;
            }
            mIsFirstReachabilityEducationRunning = false;
        }
        if (taskInfo.appCompatTaskInfo.topActivityBoundsLetterboxed) {
            if (taskInfo.appCompatTaskInfo.isLetterboxEducationEnabled) {
                createOrUpdateLetterboxEduLayout(taskInfo, taskListener);
            } else if (!taskInfo.appCompatTaskInfo.isFromLetterboxDoubleTap) {
                // In this case the app is letterboxed and the letterbox education
                // is disabled. In this case we need to understand if it's the first
                // time we show the reachability education. When this is happening
                // we need to ignore all the incoming TaskInfo until the education
                // completes. If we come from a double tap we follow the normal flow.
                final boolean topActivityPillarboxed =
                        taskInfo.appCompatTaskInfo.isTopActivityPillarboxed();
                final boolean isFirstTimeHorizontalReachabilityEdu = topActivityPillarboxed
                        && !mCompatUIConfiguration.hasSeenHorizontalReachabilityEducation(taskInfo);
                final boolean isFirstTimeVerticalReachabilityEdu = !topActivityPillarboxed
                        && !mCompatUIConfiguration.hasSeenVerticalReachabilityEducation(taskInfo);
                if (isFirstTimeHorizontalReachabilityEdu || isFirstTimeVerticalReachabilityEdu) {
                    mCompatUIConfiguration.setSeenLetterboxEducation(taskInfo.userId);
                    // We activate the first reachability education if the double-tap is enabled.
                    // If the double tap is not enabled (e.g. thin letterbox) we just set the value
                    // of the education being seen.
                    if (taskInfo.appCompatTaskInfo.isLetterboxDoubleTapEnabled) {
                        mIsFirstReachabilityEducationRunning = true;
                        createOrUpdateReachabilityEduLayout(taskInfo, taskListener);
                        return;
                    }
                }
            }
        }
        createOrUpdateCompatLayout(taskInfo, taskListener);
        createOrUpdateRestartDialogLayout(taskInfo, taskListener);
        if (mCompatUIConfiguration.getHasSeenLetterboxEducation(taskInfo.userId)) {
            if (taskInfo.appCompatTaskInfo.isLetterboxDoubleTapEnabled) {
                createOrUpdateReachabilityEduLayout(taskInfo, taskListener);
            }
            // The user aspect ratio button should not be handled when a new TaskInfo is
            // sent because of a double tap or when in multi-window mode.
            if (taskInfo.getWindowingMode() != WINDOWING_MODE_FULLSCREEN) {
                if (mUserAspectRatioSettingsLayout != null) {
                    mUserAspectRatioSettingsLayout.release();
                    mUserAspectRatioSettingsLayout = null;
                }
                return;
            }
            if (!taskInfo.appCompatTaskInfo.isFromLetterboxDoubleTap) {
                createOrUpdateUserAspectRatioSettingsLayout(taskInfo, taskListener);
            }
        }
    }

    @Override
    public void onDisplayAdded(int displayId) {
        addOnInsetsChangedListener(displayId);
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        mDisplayContextCache.remove(displayId);
        removeOnInsetsChangedListener(displayId);

        // Remove all compat UIs on the removed display.
        final List<Integer> toRemoveTaskIds = new ArrayList<>();
        forAllLayoutsOnDisplay(displayId, layout -> toRemoveTaskIds.add(layout.getTaskId()));
        for (int i = toRemoveTaskIds.size() - 1; i >= 0; i--) {
            removeLayouts(toRemoveTaskIds.get(i));
        }
    }

    private void addOnInsetsChangedListener(int displayId) {
        PerDisplayOnInsetsChangedListener listener = new PerDisplayOnInsetsChangedListener(
                displayId);
        listener.register();
        mOnInsetsChangedListeners.put(displayId, listener);
    }

    private void removeOnInsetsChangedListener(int displayId) {
        PerDisplayOnInsetsChangedListener listener = mOnInsetsChangedListeners.get(displayId);
        if (listener == null) {
            return;
        }
        listener.unregister();
        mOnInsetsChangedListeners.remove(displayId);
    }


    @Override
    public void onDisplayConfigurationChanged(int displayId, Configuration newConfig) {
        updateDisplayLayout(displayId);
    }

    private void updateDisplayLayout(int displayId) {
        final DisplayLayout displayLayout = mDisplayController.getDisplayLayout(displayId);
        forAllLayoutsOnDisplay(displayId, layout -> layout.updateDisplayLayout(displayLayout));
    }

    @Override
    public void onImeVisibilityChanged(int displayId, boolean isShowing) {
        if (isShowing) {
            mDisplaysWithIme.add(displayId);
        } else {
            mDisplaysWithIme.remove(displayId);
        }

        // Hide the compat UIs when input method is showing.
        forAllLayoutsOnDisplay(displayId,
                layout -> layout.updateVisibility(showOnDisplay(displayId)));
    }

    @Override
    public void onKeyguardVisibilityChanged(boolean visible, boolean occluded,
            boolean animatingDismiss) {
        mKeyguardShowing = visible;
        // Hide the compat UIs when keyguard is showing.
        forAllLayouts(layout -> layout.updateVisibility(showOnDisplay(layout.getDisplayId())));
    }

    /**
     * Invoked when a new task is created or the info of an existing task has changed. Updates the
     * shown status of the user aspect ratio settings button and the task id it relates to.
     */
    void updateActiveTaskInfo(@NonNull TaskInfo taskInfo) {
        // If the activity belongs to the task we are currently tracking, don't update any variables
        // as they are still relevant. Else, if the activity is visible and focused (the one the
        // user can see and is using), the user aspect ratio button can potentially be displayed so
        // start tracking the buttons visibility for this task.
        if (mTopActivityTaskId != taskInfo.taskId
                && !taskInfo.isTopActivityTransparent
                && taskInfo.isVisible && taskInfo.isFocused) {
            mTopActivityTaskId = taskInfo.taskId;
            setHasShownUserAspectRatioSettingsButton(false);
        }
    }

    /**
     * Informs the system that the user aspect ratio button has been displayed for the application
     * associated with the task id in {@link CompatUIController#mTopActivityTaskId}.
     */
    void setHasShownUserAspectRatioSettingsButton(boolean state) {
        mHasShownUserAspectRatioSettingsButton = state;
    }

    /**
     * Returns whether the user aspect ratio settings button has been show for the application
     * associated with the task id in {@link CompatUIController#mTopActivityTaskId}.
     */
    boolean hasShownUserAspectRatioSettingsButton() {
        return mHasShownUserAspectRatioSettingsButton;
    }

    /**
     * Returns the task id of the application we are currently attempting to show, of have most
     * recently shown, the user aspect ratio settings button for.
     */
    int getTopActivityTaskId() {
        return mTopActivityTaskId;
    }

    private boolean showOnDisplay(int displayId) {
        return !mKeyguardShowing && !isImeShowingOnDisplay(displayId);
    }

    private boolean isImeShowingOnDisplay(int displayId) {
        return mDisplaysWithIme.contains(displayId);
    }

    private void createOrUpdateCompatLayout(@NonNull TaskInfo taskInfo,
            @Nullable ShellTaskOrganizer.TaskListener taskListener) {
        CompatUIWindowManager layout = mActiveCompatLayouts.get(taskInfo.taskId);
        if (layout != null) {
            if (layout.needsToBeRecreated(taskInfo, taskListener)) {
                mActiveCompatLayouts.remove(taskInfo.taskId);
                layout.release();
            } else {
                // UI already exists, update the UI layout.
                if (!layout.updateCompatInfo(taskInfo, taskListener,
                        showOnDisplay(layout.getDisplayId()))) {
                    // The layout is no longer eligible to be shown, remove from active layouts.
                    mActiveCompatLayouts.remove(taskInfo.taskId);
                }
                return;
            }
        }

        // Create a new UI layout.
        final Context context = getOrCreateDisplayContext(taskInfo.displayId);
        if (context == null) {
            return;
        }
        layout = createCompatUiWindowManager(context, taskInfo, taskListener);
        if (layout.createLayout(showOnDisplay(taskInfo.displayId))) {
            // The new layout is eligible to be shown, add it the active layouts.
            mActiveCompatLayouts.put(taskInfo.taskId, layout);
        }
    }

    @VisibleForTesting
    CompatUIWindowManager createCompatUiWindowManager(Context context, TaskInfo taskInfo,
            ShellTaskOrganizer.TaskListener taskListener) {
        return new CompatUIWindowManager(context,
                taskInfo, mSyncQueue, mCompatUICallback, taskListener,
                mDisplayController.getDisplayLayout(taskInfo.displayId), mCompatUIHintsState,
                mCompatUIConfiguration, this::onRestartButtonClicked);
    }

    private void onRestartButtonClicked(
            Pair<TaskInfo, ShellTaskOrganizer.TaskListener> taskInfoState) {
        if (mCompatUIConfiguration.isRestartDialogEnabled()
                && mCompatUIConfiguration.shouldShowRestartDialogAgain(
                taskInfoState.first)) {
            // We need to show the dialog
            mSetOfTaskIdsShowingRestartDialog.add(taskInfoState.first.taskId);
            onCompatInfoChanged(taskInfoState.first, taskInfoState.second);
        } else {
            mCompatUICallback.onSizeCompatRestartButtonClicked(taskInfoState.first.taskId);
        }
    }

    private void createOrUpdateLetterboxEduLayout(@NonNull TaskInfo taskInfo,
            @Nullable ShellTaskOrganizer.TaskListener taskListener) {
        if (mActiveLetterboxEduLayout != null) {
            if (mActiveLetterboxEduLayout.needsToBeRecreated(taskInfo, taskListener)) {
                mActiveLetterboxEduLayout.release();
                mActiveLetterboxEduLayout = null;
            } else {
                if (!mActiveLetterboxEduLayout.updateCompatInfo(taskInfo, taskListener,
                        showOnDisplay(mActiveLetterboxEduLayout.getDisplayId()))) {
                    // The layout is no longer eligible to be shown, clear active layout.
                    mActiveLetterboxEduLayout.release();
                    mActiveLetterboxEduLayout = null;
                }
                return;
            }
        }
        // Create a new UI layout.
        final Context context = getOrCreateDisplayContext(taskInfo.displayId);
        if (context == null) {
            return;
        }
        LetterboxEduWindowManager newLayout = createLetterboxEduWindowManager(context, taskInfo,
                taskListener);
        if (newLayout.createLayout(showOnDisplay(taskInfo.displayId))) {
            // The new layout is eligible to be shown, make it the active layout.
            if (mActiveLetterboxEduLayout != null) {
                // Release the previous layout since at most one can be active.
                // Since letterbox education is only shown once to the user, releasing the previous
                // layout is only a precaution.
                mActiveLetterboxEduLayout.release();
            }
            mActiveLetterboxEduLayout = newLayout;
        }
    }

    @VisibleForTesting
    LetterboxEduWindowManager createLetterboxEduWindowManager(Context context, TaskInfo taskInfo,
            ShellTaskOrganizer.TaskListener taskListener) {
        return new LetterboxEduWindowManager(context, taskInfo,
                mSyncQueue, taskListener, mDisplayController.getDisplayLayout(taskInfo.displayId),
                mTransitionsLazy.get(),
                stateInfo -> createOrUpdateReachabilityEduLayout(stateInfo.first, stateInfo.second),
                mDockStateReader, mCompatUIConfiguration);
    }

    private void createOrUpdateRestartDialogLayout(@NonNull TaskInfo taskInfo,
            @Nullable ShellTaskOrganizer.TaskListener taskListener) {
        RestartDialogWindowManager layout =
                mTaskIdToRestartDialogWindowManagerMap.get(taskInfo.taskId);
        if (layout != null) {
            if (layout.needsToBeRecreated(taskInfo, taskListener)) {
                mTaskIdToRestartDialogWindowManagerMap.remove(taskInfo.taskId);
                layout.release();
            } else {
                layout.setRequestRestartDialog(
                        mSetOfTaskIdsShowingRestartDialog.contains(taskInfo.taskId));
                // UI already exists, update the UI layout.
                if (!layout.updateCompatInfo(taskInfo, taskListener,
                        showOnDisplay(layout.getDisplayId()))) {
                    // The layout is no longer eligible to be shown, remove from active layouts.
                    mTaskIdToRestartDialogWindowManagerMap.remove(taskInfo.taskId);
                }
                return;
            }
        }
        // Create a new UI layout.
        final Context context = getOrCreateDisplayContext(taskInfo.displayId);
        if (context == null) {
            return;
        }
        layout = createRestartDialogWindowManager(context, taskInfo, taskListener);
        layout.setRequestRestartDialog(
                mSetOfTaskIdsShowingRestartDialog.contains(taskInfo.taskId));
        if (layout.createLayout(showOnDisplay(taskInfo.displayId))) {
            // The new layout is eligible to be shown, add it the active layouts.
            mTaskIdToRestartDialogWindowManagerMap.put(taskInfo.taskId, layout);
        }
    }

    @VisibleForTesting
    RestartDialogWindowManager createRestartDialogWindowManager(Context context, TaskInfo taskInfo,
            ShellTaskOrganizer.TaskListener taskListener) {
        return new RestartDialogWindowManager(context, taskInfo, mSyncQueue, taskListener,
                mDisplayController.getDisplayLayout(taskInfo.displayId), mTransitionsLazy.get(),
                this::onRestartDialogCallback, this::onRestartDialogDismissCallback,
                mCompatUIConfiguration);
    }

    private void onRestartDialogCallback(
            Pair<TaskInfo, ShellTaskOrganizer.TaskListener> stateInfo) {
        mTaskIdToRestartDialogWindowManagerMap.remove(stateInfo.first.taskId);
        mCompatUICallback.onSizeCompatRestartButtonClicked(stateInfo.first.taskId);
    }

    private void onRestartDialogDismissCallback(
            Pair<TaskInfo, ShellTaskOrganizer.TaskListener> stateInfo) {
        mSetOfTaskIdsShowingRestartDialog.remove(stateInfo.first.taskId);
        onCompatInfoChanged(stateInfo.first, stateInfo.second);
    }

    private void createOrUpdateReachabilityEduLayout(@NonNull TaskInfo taskInfo,
            @Nullable ShellTaskOrganizer.TaskListener taskListener) {
        if (mActiveReachabilityEduLayout != null) {
            if (mActiveReachabilityEduLayout.needsToBeRecreated(taskInfo, taskListener)) {
                mActiveReachabilityEduLayout.release();
                mActiveReachabilityEduLayout = null;
            } else {
                // UI already exists, update the UI layout.
                if (!mActiveReachabilityEduLayout.updateCompatInfo(taskInfo, taskListener,
                        showOnDisplay(mActiveReachabilityEduLayout.getDisplayId()))) {
                    // The layout is no longer eligible to be shown, remove from active layouts.
                    mActiveReachabilityEduLayout.release();
                    mActiveReachabilityEduLayout = null;
                }
                return;
            }
        }
        // Create a new UI layout.
        final Context context = getOrCreateDisplayContext(taskInfo.displayId);
        if (context == null) {
            return;
        }
        ReachabilityEduWindowManager newLayout = createReachabilityEduWindowManager(context,
                taskInfo, taskListener);
        if (newLayout.createLayout(showOnDisplay(taskInfo.displayId))) {
            // The new layout is eligible to be shown, make it the active layout.
            if (mActiveReachabilityEduLayout != null) {
                // Release the previous layout since at most one can be active.
                // Since letterbox reachability education is only shown once to the user,
                // releasing the previous layout is only a precaution.
                mActiveReachabilityEduLayout.release();
            }
            mActiveReachabilityEduLayout = newLayout;
        }
    }

    @VisibleForTesting
    ReachabilityEduWindowManager createReachabilityEduWindowManager(Context context,
            TaskInfo taskInfo,
            ShellTaskOrganizer.TaskListener taskListener) {
        return new ReachabilityEduWindowManager(context, taskInfo, mSyncQueue,
                taskListener, mDisplayController.getDisplayLayout(taskInfo.displayId),
                mCompatUIConfiguration, mMainExecutor, this::onInitialReachabilityEduDismissed,
                mDisappearTimeSupplier);
    }

    private void onInitialReachabilityEduDismissed(@NonNull TaskInfo taskInfo,
            @NonNull ShellTaskOrganizer.TaskListener taskListener) {
        // We need to update the UI otherwise it will not be shown until the user relaunches the app
        mIsFirstReachabilityEducationRunning = false;
        createOrUpdateUserAspectRatioSettingsLayout(taskInfo, taskListener);
    }

    private void createOrUpdateUserAspectRatioSettingsLayout(@NonNull TaskInfo taskInfo,
            @Nullable ShellTaskOrganizer.TaskListener taskListener) {
        if (mUserAspectRatioSettingsLayout != null) {
            if (mUserAspectRatioSettingsLayout.needsToBeRecreated(taskInfo, taskListener)) {
                mUserAspectRatioSettingsLayout.release();
                mUserAspectRatioSettingsLayout = null;
            } else {
                // UI already exists, update the UI layout.
                if (!mUserAspectRatioSettingsLayout.updateCompatInfo(taskInfo, taskListener,
                        showOnDisplay(mUserAspectRatioSettingsLayout.getDisplayId()))) {
                    mUserAspectRatioSettingsLayout.release();
                    mUserAspectRatioSettingsLayout = null;
                }
                return;
            }
        }

        // Create a new UI layout.
        final Context context = getOrCreateDisplayContext(taskInfo.displayId);
        if (context == null) {
            return;
        }
        final UserAspectRatioSettingsWindowManager newLayout =
                createUserAspectRatioSettingsWindowManager(context, taskInfo, taskListener);
        if (newLayout.createLayout(showOnDisplay(taskInfo.displayId))) {
            // The new layout is eligible to be shown, add it the active layouts.
            mUserAspectRatioSettingsLayout = newLayout;
        }
    }

    @VisibleForTesting
    @NonNull
    UserAspectRatioSettingsWindowManager createUserAspectRatioSettingsWindowManager(
            @NonNull Context context, @NonNull TaskInfo taskInfo,
            @Nullable ShellTaskOrganizer.TaskListener taskListener) {
        return new UserAspectRatioSettingsWindowManager(context, taskInfo, mSyncQueue,
                taskListener, mDisplayController.getDisplayLayout(taskInfo.displayId),
                mCompatUIHintsState, this::launchUserAspectRatioSettings, mMainExecutor,
                mDisappearTimeSupplier, this::hasShownUserAspectRatioSettingsButton,
                this::setHasShownUserAspectRatioSettingsButton);
    }

    private void launchUserAspectRatioSettings(
            @NonNull TaskInfo taskInfo, @NonNull ShellTaskOrganizer.TaskListener taskListener) {
        final Intent intent = new Intent(Settings.ACTION_MANAGE_USER_ASPECT_RATIO_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        final ComponentName appComponent = taskInfo.topActivity;
        if (appComponent != null) {
            final Uri packageUri = Uri.parse("package:" + appComponent.getPackageName());
            intent.setData(packageUri);
        }
        final UserHandle userHandle = UserHandle.of(taskInfo.userId);
        mContext.startActivityAsUser(intent, userHandle);
    }

    private void removeLayouts(int taskId) {
        final CompatUIWindowManager compatLayout = mActiveCompatLayouts.get(taskId);
        if (compatLayout != null) {
            compatLayout.release();
            mActiveCompatLayouts.remove(taskId);
        }

        if (mActiveLetterboxEduLayout != null && mActiveLetterboxEduLayout.getTaskId() == taskId) {
            mActiveLetterboxEduLayout.release();
            mActiveLetterboxEduLayout = null;
        }

        final RestartDialogWindowManager restartLayout =
                mTaskIdToRestartDialogWindowManagerMap.get(taskId);
        if (restartLayout != null) {
            restartLayout.release();
            mTaskIdToRestartDialogWindowManagerMap.remove(taskId);
            mSetOfTaskIdsShowingRestartDialog.remove(taskId);
        }
        if (mActiveReachabilityEduLayout != null
                && mActiveReachabilityEduLayout.getTaskId() == taskId) {
            mActiveReachabilityEduLayout.release();
            mActiveReachabilityEduLayout = null;
        }

        if (mUserAspectRatioSettingsLayout != null
                && mUserAspectRatioSettingsLayout.getTaskId() == taskId) {
            mUserAspectRatioSettingsLayout.release();
            mUserAspectRatioSettingsLayout = null;
        }
    }

    private Context getOrCreateDisplayContext(int displayId) {
        if (displayId == Display.DEFAULT_DISPLAY) {
            return mContext;
        }
        Context context = null;
        final WeakReference<Context> ref = mDisplayContextCache.get(displayId);
        if (ref != null) {
            context = ref.get();
        }
        if (context == null) {
            Display display = mContext.getSystemService(DisplayManager.class).getDisplay(displayId);
            if (display != null) {
                context = mContext.createDisplayContext(display);
                mDisplayContextCache.put(displayId, new WeakReference<>(context));
            } else {
                Log.e(TAG, "Cannot get context for display " + displayId);
            }
        }
        return context;
    }

    private void forAllLayoutsOnDisplay(int displayId,
            Consumer<CompatUIWindowManagerAbstract> callback) {
        forAllLayouts(layout -> layout.getDisplayId() == displayId, callback);
    }

    private void forAllLayouts(Consumer<CompatUIWindowManagerAbstract> callback) {
        forAllLayouts(layout -> true, callback);
    }

    private void forAllLayouts(Predicate<CompatUIWindowManagerAbstract> condition,
            Consumer<CompatUIWindowManagerAbstract> callback) {
        for (int i = 0; i < mActiveCompatLayouts.size(); i++) {
            final int taskId = mActiveCompatLayouts.keyAt(i);
            final CompatUIWindowManager layout = mActiveCompatLayouts.get(taskId);
            if (layout != null && condition.test(layout)) {
                callback.accept(layout);
            }
        }
        if (mActiveLetterboxEduLayout != null && condition.test(mActiveLetterboxEduLayout)) {
            callback.accept(mActiveLetterboxEduLayout);
        }
        for (int i = 0; i < mTaskIdToRestartDialogWindowManagerMap.size(); i++) {
            final int taskId = mTaskIdToRestartDialogWindowManagerMap.keyAt(i);
            final RestartDialogWindowManager layout =
                    mTaskIdToRestartDialogWindowManagerMap.get(taskId);
            if (layout != null && condition.test(layout)) {
                callback.accept(layout);
            }
        }
        if (mActiveReachabilityEduLayout != null && condition.test(mActiveReachabilityEduLayout)) {
            callback.accept(mActiveReachabilityEduLayout);
        }
        if (mUserAspectRatioSettingsLayout != null && condition.test(
                mUserAspectRatioSettingsLayout)) {
            callback.accept(mUserAspectRatioSettingsLayout);
        }
    }

    /** An implementation of {@link OnInsetsChangedListener} for a given display id. */
    private class PerDisplayOnInsetsChangedListener implements OnInsetsChangedListener {
        final int mDisplayId;
        final InsetsState mInsetsState = new InsetsState();

        PerDisplayOnInsetsChangedListener(int displayId) {
            mDisplayId = displayId;
        }

        void register() {
            mDisplayInsetsController.addInsetsChangedListener(mDisplayId, this);
        }

        void unregister() {
            mDisplayInsetsController.removeInsetsChangedListener(mDisplayId, this);
        }

        @Override
        public void insetsChanged(InsetsState insetsState) {
            if (mInsetsState.equals(insetsState)) {
                return;
            }
            mInsetsState.set(insetsState);
            updateDisplayLayout(mDisplayId);
        }

        @Override
        public void insetsControlChanged(InsetsState insetsState,
                InsetsSourceControl[] activeControls) {
            insetsChanged(insetsState);
        }
    }

    /**
     * A class holding the state of the compat UI hints, which is shared between all compat UI
     * window managers.
     */
    static class CompatUIHintsState {
        boolean mHasShownSizeCompatHint;
        boolean mHasShownCameraCompatHint;
        boolean mHasShownUserAspectRatioSettingsButtonHint;
    }
}
