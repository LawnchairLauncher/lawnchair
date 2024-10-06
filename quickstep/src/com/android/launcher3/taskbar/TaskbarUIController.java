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
package com.android.launcher3.taskbar;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_APP;
import static com.android.quickstep.OverviewCommandHelper.TYPE_HIDE;

import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.window.RemoteTransition;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.quickstep.OverviewCommandHelper;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.TISBindHelper;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.quickstep.views.TaskView.TaskContainer;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.stream.Stream;

/**
 * Base class for providing different taskbar UI
 */
public class TaskbarUIController {
    public static final TaskbarUIController DEFAULT = new TaskbarUIController();

    // Initialized in init.
    protected TaskbarControllers mControllers;

    @CallSuper
    protected void init(TaskbarControllers taskbarControllers) {
        mControllers = taskbarControllers;
    }

    @CallSuper
    protected void onDestroy() {
        mControllers = null;
    }

    protected boolean isTaskbarTouchable() {
        return true;
    }

    /**
     * This should only be called by TaskbarStashController so that a
     * TaskbarUIController can
     * disable stashing. All other controllers should use
     * {@link TaskbarStashController#supportsVisualStashing()} as the source of
     * truth.
     */
    public boolean supportsVisualStashing() {
        return true;
    }

    protected void onStashedInAppChanged() {
    }

    /**
     * Called when taskbar icon layout bounds change.
     */
    protected void onIconLayoutBoundsChanged() {
    }

    protected String getTaskbarUIControllerName() {
        return "TaskbarUIController";
    }

    /** Called when an icon is launched. */
    @CallSuper
    public void onTaskbarIconLaunched(ItemInfo item) {
        // When launching from Taskbar, e.g. from Overview, set FLAG_IN_APP immediately
        // instead of
        // waiting for onPause, to reduce potential visual noise during the app open
        // transition.
        if (mControllers.taskbarStashController == null)
            return;
        mControllers.taskbarStashController.updateStateForFlag(FLAG_IN_APP, true);
        mControllers.taskbarStashController.applyState();
    }

    public View getRootView() {
        return mControllers.taskbarActivityContext.getDragLayer();
    }

    /**
     * Called when swiping from the bottom nav region in fully gestural mode.
     * 
     * @param inProgress True if the animation started, false if we just settled on
     *                   an end target.
     */
    public void setSystemGestureInProgress(boolean inProgress) {
        mControllers.taskbarStashController.setSystemGestureInProgress(inProgress);
    }

    /**
     * Manually closes the overlay window.
     */
    public void hideOverlayWindow() {
        if (!DisplayController.isTransientTaskbar(mControllers.taskbarActivityContext)
                || mControllers.taskbarAllAppsController.isOpen()) {
            mControllers.taskbarOverlayController.hideWindow();
        }
    }

    /**
     * User expands PiP to full-screen (or split-screen) mode, try to hide the
     * Taskbar.
     */
    public void onExpandPip() {
        if (mControllers != null) {
            final TaskbarStashController stashController = mControllers.taskbarStashController;
            stashController.updateStateForFlag(FLAG_IN_APP, true);
            stashController.applyState();
        }
    }

    /**
     * SysUI flags updated, see QuickStepContract.SYSUI_STATE_* values.
     */
    public void updateStateForSysuiFlags(@SystemUiStateFlags long sysuiFlags) {
    }

    /**
     * Returns {@code true} iff taskbar is stashed.
     */
    public boolean isTaskbarStashed() {
        return mControllers.taskbarStashController.isStashed();
    }

    /**
     * Returns {@code true} iff taskbar All Apps is open.
     */
    public boolean isTaskbarAllAppsOpen() {
        return mControllers.taskbarAllAppsController.isOpen();
    }

    /**
     * Called at the end of the swipe gesture on Transient taskbar.
     */
    public void startTranslationSpring() {
        mControllers.taskbarActivityContext.startTranslationSpring();
    }

    /**
     * @param ev MotionEvent in screen coordinates.
     * 
     * @return Whether any Taskbar item could handle the given MotionEvent if given
     *         the chance.
     */
    public boolean isEventOverAnyTaskbarItem(MotionEvent ev) {
        return mControllers.taskbarViewController.isEventOverAnyItem(ev)
                || mControllers.navbarButtonsViewController.isEventOverAnyItem(ev);
    }

    /**
     * Checks if the given {@link MotionEvent} is over the bubble bar stash handle.
     */
    public boolean isEventOverBubbleBarStashHandle(MotionEvent ev) {
        return mControllers.bubbleControllers.map(
                bubbleControllers -> bubbleControllers.bubbleStashController.isEventOverStashHandle(ev))
                .orElse(false);
    }

    /**
     * Returns true if icons should be aligned to hotseat in the current transition.
     */
    public boolean isIconAlignedWithHotseat() {
        return false;
    }

    /**
     * Returns true if hotseat icons are on top of view hierarchy when aligned in
     * the current state.
     */
    public boolean isHotseatIconOnTopWhenAligned() {
        return true;
    }

    /** Returns {@code true} if Taskbar is currently within overview. */
    protected boolean isInOverviewUi() {
        return false;
    }

    /**
     * Returns {@code true} if Home All Apps available instead of Taskbar All Apps.
     */
    protected boolean canToggleHomeAllApps() {
        return false;
    }

    @CallSuper
    protected void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(String.format(
                "%sTaskbarUIController: using an instance of %s",
                prefix,
                getTaskbarUIControllerName()));
    }

    /**
     * Returns RecentsView. Overwritten in LauncherTaskbarUIController and
     * FallbackTaskbarUIController with Launcher-specific implementations. Returns
     * null for other
     * UI controllers (like DesktopTaskbarUIController) that don't have a
     * RecentsView.
     */
    public @Nullable RecentsView getRecentsView() {
        return null;
    }

    public void startSplitSelection(SplitConfigurationOptions.SplitSelectSource splitSelectSource) {
        RecentsView recentsView = getRecentsView();
        if (recentsView == null) {
            return;
        }

        recentsView.getSplitSelectController().findLastActiveTasksAndRunCallback(
                Collections.singletonList(splitSelectSource.itemInfo.getComponentKey()),
                false /* findExactPairMatch */,
                foundTasks -> {
                    @Nullable
                    Task foundTask = foundTasks[0];
                    splitSelectSource.alreadyRunningTaskId = foundTask == null
                            ? INVALID_TASK_ID
                            : foundTask.key.id;
                    splitSelectSource.animateCurrentTaskDismissal = foundTask != null;
                    recentsView.initiateSplitSelect(splitSelectSource);
                });
    }

    /**
     * Uses the clicked Taskbar icon to launch a second app for splitscreen.
     */
    public void triggerSecondAppForSplit(ItemInfoWithIcon info, Intent intent, View startingView) {
        RecentsView recents = getRecentsView();
        recents.getSplitSelectController().findLastActiveTasksAndRunCallback(
                Collections.singletonList(info.getComponentKey()),
                false /* findExactPairMatch */,
                foundTasks -> {
                    @Nullable
                    Task foundTask = foundTasks[0];
                    if (foundTask != null) {
                        TaskView foundTaskView = recents.getTaskViewByTaskId(foundTask.key.id);
                        // TODO (b/266482558): This additional null check is needed because there
                        // are times when our Tasks list doesn't match our TaskViews list (like when
                        // a tile is removed during {@link RecentsView#applyLoadPlan()}. A clearer
                        // state management system is in the works so that we don't need to rely on
                        // null checks as much. See comments at ag/21152798.
                        if (foundTaskView != null) {
                            // There is already a running app of this type, use that as second app.
                            // Get index of task (0 or 1), in case it's a GroupedTaskView
                            TaskContainer taskContainer = foundTaskView.getTaskContainerById(foundTask.key.id);
                            recents.confirmSplitSelect(
                                    foundTaskView,
                                    foundTask,
                                    taskContainer.getIconView().getDrawable(),
                                    taskContainer.getThumbnailViewDeprecated(),
                                    taskContainer.getThumbnailViewDeprecated().getThumbnail(),
                                    null /* intent */,
                                    null /* user */,
                                    info);
                            return;
                        }
                    }

                    // No running app of that type, create a new instance as second app.
                    recents.confirmSplitSelect(
                            null /* containerTaskView */,
                            null /* task */,
                            new BitmapDrawable(info.bitmap.icon),
                            startingView,
                            null /* thumbnail */,
                            intent,
                            info.user,
                            info);
                });
    }

    /**
     * Opens the Keyboard Quick Switch View.
     *
     * This will set the focus to the first task from the right (from the left in
     * RTL)
     */
    public void openQuickSwitchView() {
        mControllers.keyboardQuickSwitchController.openQuickSwitchView();
    }

    /**
     * Launches the focused task and closes the Keyboard Quick Switch View.
     *
     * If the overlay or view are closed, or the overview task is focused, then
     * Overview is
     * launched. If the overview task is launched, then the first hidden task is
     * focused.
     *
     * @return the index of what task should be focused in ; -1 iff Overview
     *         shouldn't be launched
     */
    public int launchFocusedTask() {
        int focusedTaskIndex = mControllers.keyboardQuickSwitchController.launchFocusedTask();
        mControllers.keyboardQuickSwitchController.closeQuickSwitchView();
        return focusedTaskIndex;
    }

    /**
     * Launches the given task in split-screen.
     */
    public void launchSplitTasks(
            @NonNull GroupTask groupTask, @Nullable RemoteTransition remoteTransition) {
    }

    /**
     * Returns the matching view (if any) in the taskbar.
     * 
     * @param view The view to match.
     */
    public @Nullable View findMatchingView(View view) {
        if (!(view.getTag() instanceof ItemInfo)) {
            return null;
        }
        ItemInfo info = (ItemInfo) view.getTag();
        if (info.container != CONTAINER_HOTSEAT && info.container != CONTAINER_HOTSEAT_PREDICTION) {
            return null;
        }

        // Taskbar has the same items as the hotseat and we can use screenId to find the
        // match.
        int screenId = info.screenId;
        View[] views = mControllers.taskbarViewController.getIconViews();
        for (int i = views.length - 1; i >= 0; --i) {
            if (views[i] != null
                    && views[i].getTag() instanceof ItemInfo
                    && ((ItemInfo) views[i].getTag()).screenId == screenId) {
                return views[i];
            }
        }
        return null;
    }

    /**
     * Callback for when launcher state transition completes after user swipes to
     * home.
     * 
     * @param finalState The final state of the transition.
     */
    public void onStateTransitionCompletedAfterSwipeToHome(LauncherState finalState) {
        // Overridden
    }

    /**
     * Refreshes the resumed state of this ui controller.
     */
    public void refreshResumedState() {
    }

    /**
     * Returns a stream of split screen menu options appropriate to the device.
     */
    Stream<SystemShortcut.Factory<BaseTaskbarContext>> getSplitMenuOptions() {
        return Utilities
                .getSplitPositionOptions(mControllers.taskbarActivityContext.getDeviceProfile())
                .stream()
                .map(mControllers.taskbarPopupController::createSplitShortcutFactory);
    }

    /** Adjusts the hotseat for the bubble bar. */
    public void adjustHotseatForBubbleBar(boolean isBubbleBarVisible) {
    }

    @Nullable
    protected TISBindHelper getTISBindHelper() {
        return null;
    }

    /**
     * Launches the focused task in the Keyboard Quick Switch view through the
     * OverviewCommandHelper
     * <p>
     * Use this helper method when the focused task may be the overview task.
     */
    public void launchKeyboardFocusedTask() {
        TISBindHelper tisBindHelper = getTISBindHelper();
        if (tisBindHelper == null) {
            return;
        }
        OverviewCommandHelper overviewCommandHelper = tisBindHelper.getOverviewCommandHelper();
        if (overviewCommandHelper == null) {
            return;
        }
        overviewCommandHelper.addCommand(TYPE_HIDE);
    }

    /**
     * Adjusts the taskbar based on the visibility of the launcher.
     * 
     * @param isVisible True if launcher is visible, false otherwise.
     */
    public void onLauncherVisibilityChanged(boolean isVisible) {
        mControllers.taskbarStashController.updateStateForFlag(FLAG_IN_APP, !isVisible);
        mControllers.taskbarStashController.applyState();
    }

    /**
     * Request for UI controller to ignore animations for the next callback for the
     * end of recents
     * animation
     */
    public void setSkipNextRecentsAnimEnd() {
        // Overridden
    }

    /**
     * Sets whether the user is going home based on the current gesture.
     */
    public void setUserIsGoingHome(boolean isGoingHome) {
        mControllers.taskbarStashController.setUserIsGoingHome(isGoingHome);
    }
}
