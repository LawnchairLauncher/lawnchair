/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.window.DesktopModeFlags.ENABLE_TASKBAR_OVERFLOW;

import static com.android.launcher3.taskbar.TaskbarDesktopExperienceFlags.enableAltTabKqsFlatenning;
import static com.android.launcher3.taskbar.TaskbarDesktopExperienceFlags.enableAltTabKqsOnConnectedDisplays;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.R;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayDragLayer;
import com.android.launcher3.util.TouchController;
import com.android.quickstep.RecentsFilterState;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.util.DesktopTask;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.util.SingleTask;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.wm.shell.shared.desktopmode.DesktopState;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handles initialization of the {@link KeyboardQuickSwitchViewController}.
 */
public final class KeyboardQuickSwitchController implements
        TaskbarControllers.LoggableTaskbarController, TouchController {

    @VisibleForTesting
    public static final int MAX_TASKS = 6;

    @NonNull private final ControllerCallbacks mControllerCallbacks = new ControllerCallbacks();
    // Callback used to notify when the KQS view is closed.
    @Nullable private Runnable mOnClosed;

    // Used to keep track of the last requested task list id, so that we do not request to load the
    // tasks again if we have already requested it and the task list has not changed
    private int mTaskListChangeId = -1;
    // Only empty before the recent tasks list has been loaded the first time
    @NonNull private List<GroupTask> mTasks = new ArrayList<>();
    // Set of task IDs filtered out of tasks in recents model to generate list of tasks to show in
    // the Keyboard Quick Switch view. Non empty only if the view has been shown in response to
    // toggling taskbar overflow button.
    @NonNull private Set<Integer> mExcludedTaskIds = Collections.emptySet();

    private int mNumHiddenTasks = 0;

    // Initialized in init
    private TaskbarControllers mControllers;
    @Nullable private RecentsModel mModel;
    private boolean mIsProjectedMode;

    @Nullable private KeyboardQuickSwitchViewController mQuickSwitchViewController;
    @Nullable private TaskbarOverlayContext mOverlayContext;

    private boolean mHasDesktopTask = false;
    private boolean mWasDesktopTaskFilteredOut = false;

    /** Initialize the controller. */
    public void init(@NonNull TaskbarControllers controllers) {
        mControllers = controllers;
        mModel = RecentsModel.INSTANCE.get(controllers.taskbarActivityContext);
        mIsProjectedMode = DesktopState.fromContext(
                mControllers.taskbarActivityContext).isProjectedMode();
    }

    void onConfigurationChanged(@ActivityInfo.Config int configChanges) {
        if (mQuickSwitchViewController == null) {
            return;
        }
        if ((configChanges & (ActivityInfo.CONFIG_KEYBOARD
                | ActivityInfo.CONFIG_KEYBOARD_HIDDEN)) != 0) {
            mQuickSwitchViewController.closeQuickSwitchView(true);
            return;
        }
        int currentFocusedIndex = mQuickSwitchViewController.getCurrentFocusedIndex();
        boolean wasOpenedFromTaskbar = mQuickSwitchViewController.wasOpenedFromTaskbar();
        onDestroy();
        if (currentFocusedIndex != -1) {
            mControllers.taskbarActivityContext.getMainThreadHandler().post(
                    () -> openQuickSwitchView(currentFocusedIndex, mExcludedTaskIds,
                            wasOpenedFromTaskbar));
        }
    }

    void openQuickSwitchView() {
        openQuickSwitchView(-1);
    }

    /**
     * Opens or closes the view in response to taskbar action. The view shows a filtered list of
     * tasks.
     * @param taskIdsToExclude A list of tasks to exclude in the opened view.
     * @param onClosed A callback used to notify when the KQS view is closed.
     */
    void toggleQuickSwitchViewForTaskbar(@NonNull Set<Integer> taskIdsToExclude,
            @NonNull Runnable onClosed) {
        mOnClosed = onClosed;

        // Close the view if its shown, and was opened from the taskbar.
        if (mQuickSwitchViewController != null
                && !mQuickSwitchViewController.isCloseAnimationRunning()
                && mQuickSwitchViewController.wasOpenedFromTaskbar()) {
            closeQuickSwitchView(true);
            return;
        }

        openQuickSwitchView(-1, taskIdsToExclude, true);
    }

    private void openQuickSwitchView(int currentFocusedIndex) {
        openQuickSwitchView(currentFocusedIndex, Collections.emptySet(), false);
    }

    private void openQuickSwitchView(int currentFocusedIndex,
            @NonNull Set<Integer> taskIdsToExclude,
            boolean wasOpenedFromTaskbar) {
        if (mQuickSwitchViewController != null) {
            if (!mQuickSwitchViewController.isCloseAnimationRunning()) {
                if (mQuickSwitchViewController.wasOpenedFromTaskbar() == wasOpenedFromTaskbar) {
                    return;
                }

                // Relayout the KQS view instead of recreating a new one if it is the current
                // trigger surface is different than the previous one.
                final int currentFocusIndexOverride =
                        currentFocusedIndex == -1 && !mControllerCallbacks.isFirstTaskRunning()
                                ? 0 : currentFocusedIndex;

                // Skip the task reload if the list is not changed.
                if (!mModel.isTaskListValid(mTaskListChangeId) || !taskIdsToExclude.equals(
                        mExcludedTaskIds)) {
                    final boolean shouldShowDesktopTasks = mControllers.taskbarDesktopModeController
                            .shouldShowDesktopTasksInTaskbar();
                    mExcludedTaskIds = taskIdsToExclude;
                    mTaskListChangeId = mModel.getTasks(
                            shouldShowDesktopTasks ? RecentsFilterState.EMPTY_FILTER
                                    : RecentsFilterState.getDesktopTaskFilter(),
                            (tasks) -> {
                                processLoadedTasks(wasOpenedFromTaskbar, tasks, taskIdsToExclude);
                                mQuickSwitchViewController.updateQuickSwitchView(
                                        mTasks,
                                        wasOpenedFromTaskbar ? 0 : mNumHiddenTasks,
                                        currentFocusIndexOverride,
                                        mHasDesktopTask,
                                        mWasDesktopTaskFilteredOut);
                            });
                }

                mQuickSwitchViewController.updateLayoutForSurface(wasOpenedFromTaskbar,
                        currentFocusIndexOverride);
                return;
            } else {
                // Allow the KQS to be reopened during the close animation to make it more
                // responsive.
                closeQuickSwitchView(false);
            }
        }

        mOverlayContext = mControllers.taskbarOverlayController.requestWindow();
        if (ENABLE_TASKBAR_OVERFLOW.isTrue()) {
            mOverlayContext.getDragLayer().addTouchController(this);
        }
        KeyboardQuickSwitchView keyboardQuickSwitchView =
                (KeyboardQuickSwitchView) mOverlayContext.getLayoutInflater()
                        .inflate(
                                R.layout.keyboard_quick_switch_view,
                                mOverlayContext.getDragLayer(),
                                /* attachToRoot= */ false);
        mQuickSwitchViewController = new KeyboardQuickSwitchViewController(
                mControllers, mOverlayContext, keyboardQuickSwitchView, mControllerCallbacks);

        final boolean shouldShowDesktopTasks = mControllers.taskbarDesktopModeController
                .shouldShowDesktopTasksInTaskbar();

        if (mModel.isTaskListValid(mTaskListChangeId)
                && taskIdsToExclude.equals(mExcludedTaskIds)) {
            // When we are opening the KQS with no focus override, check if the first task is
            // running. If not, focus that first task.
            mQuickSwitchViewController.openQuickSwitchView(
                    mTasks,
                    wasOpenedFromTaskbar ? 0 : mNumHiddenTasks,
                    /* updateTasks= */ false,
                    currentFocusedIndex == -1 && !mControllerCallbacks.isFirstTaskRunning()
                            ? 0 : currentFocusedIndex,
                    shouldShowDesktopTasks,
                    mHasDesktopTask,
                    mWasDesktopTaskFilteredOut,
                    wasOpenedFromTaskbar);
            return;
        }

        mExcludedTaskIds = taskIdsToExclude;
        mTaskListChangeId = mModel.getTasks(
                shouldShowDesktopTasks ? RecentsFilterState.EMPTY_FILTER
                        : RecentsFilterState.getDesktopTaskFilter(),
                (tasks) -> {
                    processLoadedTasks(wasOpenedFromTaskbar, tasks, taskIdsToExclude);
                    // Check if the first task is running after the recents model has updated so
                    // that we use the correct index.
                    mQuickSwitchViewController.openQuickSwitchView(
                            mTasks,
                            wasOpenedFromTaskbar ? 0 : mNumHiddenTasks,
                            /* updateTasks= */ true,
                            currentFocusedIndex == -1 && !mControllerCallbacks.isFirstTaskRunning()
                                    ? 0 : currentFocusedIndex,
                            shouldShowDesktopTasks,
                            mHasDesktopTask,
                            mWasDesktopTaskFilteredOut,
                            wasOpenedFromTaskbar);
                });
    }

    private boolean shouldIncludeTask(GroupTask task, Set<Integer> taskIdsToExclude) {
        return !ENABLE_TASKBAR_OVERFLOW.isTrue()
                || task.getTasks().stream().noneMatch(t -> taskIdsToExclude.contains(t.key.id));
    }

    private void processLoadedTasks(boolean openedFromTaskbar, List<GroupTask> tasks,
            Set<Integer> taskIdsToExclude) {
        mHasDesktopTask = false;
        mWasDesktopTaskFilteredOut = false;

        if (enableAltTabKqsFlatenning.isTrue() && !openedFromTaskbar) {
            processLoadedTasksCombined(tasks, taskIdsToExclude);
        } else if (mControllers.taskbarDesktopModeController.shouldShowDesktopTasksInTaskbar()) {
            processLoadedTasksOnDesktop(tasks, taskIdsToExclude);
        } else {
            processLoadedTasksOutsideDesktop(tasks, taskIdsToExclude);
        }
    }

    private void processLoadedTasksCombined(List<GroupTask> tasks, Set<Integer> taskIdsToExclude) {
        List<GroupTask> allTasks = tasks.stream()
                .flatMap(task -> {
                    // In case of DesktopTasks, convert each contained task into a new DesktopTask
                    // this way the view controller will be able to show a thumbnail in KQS view.
                    if (task instanceof DesktopTask desktopTask) {
                        return desktopTask.getTasks().stream()
                                .map(singleTask -> new DesktopTask(desktopTask.getDeskId(),
                                        desktopTask.getDisplayId(), List.of(singleTask)));
                    }

                    return Stream.of(task);
                })
                .filter(task -> shouldIncludeTask(task, taskIdsToExclude))
                .filter(this::shouldIncludeTaskBasedOnProjectedMode)
                .toList();

        mTasks = allTasks.stream()
                .sorted(combinedTasksComparator())
                .limit(MAX_TASKS)
                .toList();
        mNumHiddenTasks = Math.max(0, allTasks.size() - MAX_TASKS);
    }

    private boolean shouldIncludeTaskBasedOnProjectedMode(GroupTask task) {
        // When not in projected mode, include tasks from all displays
        if (!mIsProjectedMode) {
            return true;
        }

        int primaryDisplayId = mControllers.taskbarActivityContext.getPrimaryDisplayId();

        // When on primary device in projected mode, only show tasks from the primary device.
        if (mControllers.taskbarActivityContext.isPrimaryDisplay()) {
            return task.getDisplayId() == primaryDisplayId;
        }

        // When on connected display with primary device in projected mode, only include tasks that
        // are not on primary device.
        return task.getDisplayId() != primaryDisplayId;
    }

    private static Comparator<GroupTask> combinedTasksComparator() {
        return Comparator.comparingLong((GroupTask groupTask) ->
                        groupTask.getTasks().stream()
                                .map(task -> task.key.lastActiveTime)
                                .max(Comparator.naturalOrder())
                                // Empty tasks list shouldn't be possible so return -1 in that case.
                                .orElse(-1L))
                .reversed();
    }

    private void processLoadedTasksOutsideDesktop(List<GroupTask> tasks,
            Set<Integer> taskIdsToExclude) {
        // Only store MAX_TASK tasks, from most to least recent
        Collections.reverse(tasks);
        mTasks = tasks.stream()
                .filter(task -> !(task instanceof DesktopTask)
                        && shouldIncludeTask(task, taskIdsToExclude))
                .limit(MAX_TASKS)
                .collect(Collectors.toList());

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i) instanceof DesktopTask) {
                mHasDesktopTask = true;
                if (i < mTasks.size()) {
                    mWasDesktopTaskFilteredOut = true;
                }
                break;
            }
        }

        mNumHiddenTasks = Math.max(0,
                tasks.size() - (mWasDesktopTaskFilteredOut ? 1 : 0) - MAX_TASKS);
    }

    private void processLoadedTasksOnDesktop(List<GroupTask> tasks, Set<Integer> taskIdsToExclude) {
        // Find all desktop tasks.
        List<DesktopTask> desktopTasks = tasks.stream()
                .filter(t -> t instanceof DesktopTask)
                .map(t -> (DesktopTask) t)
                .toList();

        // Apps on the connected displays seem to be in different Desktop tasks even with the
        // multiple desktops flag disabled. So, until multiple desktops is implemented the following
        // should help with team-fooding Alt+tab on connected displays. Post multiple desktop,
        // further changes maybe required to support launching selected desktops.
        if (enableAltTabKqsOnConnectedDisplays.isTrue()) {
            mTasks = desktopTasks.stream()
                    .flatMap(t -> t.getTasks().stream())
                    .map(SingleTask::new)
                    .filter(task -> shouldIncludeTask(task, taskIdsToExclude))
                    .collect(Collectors.toList());

            mNumHiddenTasks = Math.max(0, tasks.size() - desktopTasks.size());
        } else if (!desktopTasks.isEmpty()) {
            mTasks = desktopTasks.get(0).getTasks().stream()
                    .map(SingleTask::new)
                    .filter(task -> shouldIncludeTask(task, taskIdsToExclude))
                    .collect(Collectors.toList());
            // All other tasks, apart from the grouped desktop task, are hidden
            mNumHiddenTasks = Math.max(0, tasks.size() - 1);
        } else {
            // Desktop tasks were visible, but the recents entry is missing. Fall back to empty list
            mTasks = Collections.emptyList();
            mNumHiddenTasks = tasks.size();
        }
    }

    void closeQuickSwitchView() {
        closeQuickSwitchView(true);
    }

    void closeQuickSwitchView(boolean animate) {
        if (mQuickSwitchViewController == null) {
            return;
        }
        mQuickSwitchViewController.closeQuickSwitchView(animate);
    }

    /**
     * See {@link TaskbarUIController#launchFocusedTask()}
     */
    int launchFocusedTask() {
        // Return -1 so that the RecentsView is not incorrectly opened when the user closes the
        // quick switch view by tapping the screen or when there are no recent tasks.
        return mQuickSwitchViewController == null || mTasks.isEmpty()
                ? -1 : mQuickSwitchViewController.launchFocusedTask();
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (mQuickSwitchViewController == null
                || mOverlayContext == null
                || !ENABLE_TASKBAR_OVERFLOW.isTrue()) {
            return false;
        }

        TaskbarOverlayDragLayer dragLayer = mOverlayContext.getDragLayer();
        if (ev.getAction() == MotionEvent.ACTION_DOWN
                && !mQuickSwitchViewController.isEventOverKeyboardQuickSwitch(dragLayer, ev)) {
            closeQuickSwitchView(true);
        }
        return false;
    }

    void onDestroy() {
        if (mQuickSwitchViewController != null) {
            mQuickSwitchViewController.onDestroy();
        }
    }

    @VisibleForTesting
    boolean isShownFromTaskbar() {
        return isShown() && mQuickSwitchViewController.wasOpenedFromTaskbar();
    }

    @VisibleForTesting
    boolean isShown() {
        return mQuickSwitchViewController != null
                && !mQuickSwitchViewController.isCloseAnimationRunning();
    }

    @VisibleForTesting
    List<Integer> shownTaskIds() {
        if (!isShown()) {
            return Collections.emptyList();
        }

        return mTasks.stream().flatMap(
                groupTask -> groupTask.getTasks().stream().map(task -> task.key.id)).toList();
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "KeyboardQuickSwitchController:");

        pw.println(prefix + "\tisOpen=" + (mQuickSwitchViewController != null));
        pw.println(prefix + "\tmNumHiddenTasks=" + mNumHiddenTasks);
        pw.println(prefix + "\tmTaskListChangeId=" + mTaskListChangeId);
        pw.println(prefix + "\tmHasDesktopTask=" + mHasDesktopTask);
        pw.println(prefix + "\tmWasDesktopTaskFilteredOut=" + mWasDesktopTaskFilteredOut);
        pw.println(prefix + "\tmTasks=[");
        for (GroupTask task : mTasks) {
            int count = 0;
            for (Task t : task.getTasks()) {
                ComponentName cn = t.getTopComponent();
                pw.println(prefix + "\t\tt" + (++count) + ": (id=" + t.key.id
                        + "; package=" + (cn != null ? cn.getPackageName() + ")" : "no package)"));
            }
        }
        pw.println(prefix + "\t]");

        if (mQuickSwitchViewController != null) {
            mQuickSwitchViewController.dumpLogs(prefix + '\t', pw);
        }
    }

    class ControllerCallbacks {

        @Nullable
        GroupTask getTaskAt(int index) {
            return index < 0 || index >= mTasks.size() ? null : mTasks.get(index);
        }

        void updateThumbnailInBackground(Task task, Consumer<ThumbnailData> callback) {
            mModel.getThumbnailCache().getThumbnailInBackground(task,
                    thumbnailData -> {
                        task.thumbnail = thumbnailData;
                        callback.accept(thumbnailData);
                    });
        }

        void updateIconInBackground(Task task, Consumer<Task> callback) {
            mModel.getIconCache().getIconInBackground(task, (icon, contentDescription, title) -> {
                task.icon = icon;
                task.titleDescription = contentDescription;
                task.title = title;
                callback.accept(task);
            });
        }

        void onCloseStarted() {
            if (mOnClosed != null) {
                mOnClosed.run();
                mOnClosed = null;
            }
        }

        void onCloseComplete() {
            if (ENABLE_TASKBAR_OVERFLOW.isTrue() && mOverlayContext != null) {
                mOverlayContext.getDragLayer()
                        .removeTouchController(KeyboardQuickSwitchController.this);
            }
            mOverlayContext = null;
            mQuickSwitchViewController = null;
        }

        boolean isTaskRunning(@Nullable GroupTask task) {
            if (task == null) {
                return false;
            }
            ActivityManager.RunningTaskInfo runningTaskInfo =
                    ActivityManagerWrapper.getInstance().getRunningTask();
            if (runningTaskInfo == null) {
                return false;
            }

            int runningTaskId = runningTaskInfo.taskId;
            return task.containsTask(runningTaskId);
        }

        boolean isFirstTaskRunning() {
            return isTaskRunning(getTaskAt(0));
        }

        boolean isAspectRatioSquare() {
            return mControllers != null && LayoutUtils.isAspectRatioSquare(
                    mControllers.taskbarActivityContext.getDeviceProfile().getDeviceProperties().getAspectRatio());
        }
    }
}
