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

package com.android.wm.shell.freeform;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;

import static com.android.wm.shell.ShellTaskOrganizer.TASK_LISTENER_TYPE_FREEFORM;

import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.util.SparseArray;
import android.view.SurfaceControl;
import android.window.DesktopExperienceFlags;
import android.window.DesktopModeFlags;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.LaunchAdjacentController;
import com.android.wm.shell.desktopmode.DesktopModeLoggerTransitionObserver;
import com.android.wm.shell.desktopmode.DesktopRepository;
import com.android.wm.shell.desktopmode.DesktopTasksController;
import com.android.wm.shell.desktopmode.DesktopUserRepositories;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.desktopmode.DesktopState;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;

import java.io.PrintWriter;
import java.util.Optional;

/**
 * {@link ShellTaskOrganizer.TaskListener} for {@link
 * ShellTaskOrganizer#TASK_LISTENER_TYPE_FREEFORM}.
 */
public class FreeformTaskListener implements ShellTaskOrganizer.TaskListener,
        ShellTaskOrganizer.FocusListener {
    private static final String TAG = "FreeformTaskListener";

    private final Context mContext;
    private final ShellTaskOrganizer mShellTaskOrganizer;
    private final Optional<DesktopUserRepositories> mDesktopUserRepositories;
    private final Optional<DesktopTasksController> mDesktopTasksController;
    private final DesktopModeLoggerTransitionObserver mDesktopModeLoggerTransitionObserver;
    private final WindowDecorViewModel mWindowDecorationViewModel;
    private final LaunchAdjacentController mLaunchAdjacentController;
    private final Optional<TaskChangeListener> mTaskChangeListener;
    private final DesktopState mDesktopState;

    private final SparseArray<State> mTasks = new SparseArray<>();

    public FreeformTaskListener(
            Context context,
            ShellInit shellInit,
            ShellTaskOrganizer shellTaskOrganizer,
            Optional<DesktopUserRepositories> desktopUserRepositories,
            Optional<DesktopTasksController> desktopTasksController,
            DesktopModeLoggerTransitionObserver desktopModeLoggerTransitionObserver,
            LaunchAdjacentController launchAdjacentController,
            WindowDecorViewModel windowDecorationViewModel,
            Optional<TaskChangeListener> taskChangeListener,
            DesktopState desktopState) {
        mContext = context;
        mShellTaskOrganizer = shellTaskOrganizer;
        mWindowDecorationViewModel = windowDecorationViewModel;
        mDesktopUserRepositories = desktopUserRepositories;
        mDesktopTasksController = desktopTasksController;
        mDesktopModeLoggerTransitionObserver = desktopModeLoggerTransitionObserver;
        mLaunchAdjacentController = launchAdjacentController;
        mTaskChangeListener = taskChangeListener;
        mDesktopState = desktopState;
        if (FreeformComponents.requiresFreeformComponents(desktopState)) {
            shellInit.addInitCallback(this::onInit, this);
        }
    }

    private void onInit() {
        mShellTaskOrganizer.addListenerForType(this, TASK_LISTENER_TYPE_FREEFORM);
        if (mDesktopState.canEnterDesktopMode()) {
            mShellTaskOrganizer.addFocusListener(this);
        }
    }

    @Override
    public void onTaskAppeared(RunningTaskInfo taskInfo, SurfaceControl leash) {
        if (mTasks.get(taskInfo.taskId) != null) {
            throw new IllegalStateException("Task appeared more than once: #" + taskInfo.taskId);
        }
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TASK_ORG, "Freeform Task Appeared: #%d",
                taskInfo.taskId);
        final State state = new State();
        state.mTaskInfo = taskInfo;
        state.mLeash = leash;
        mTasks.put(taskInfo.taskId, state);

        if (!DesktopExperienceFlags.ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS.isTrue()
                && mDesktopState.canEnterDesktopMode()) {
            mDesktopUserRepositories.ifPresent(userRepositories -> {
                DesktopRepository currentRepo = userRepositories.getProfile(taskInfo.userId);
                currentRepo.addTask(taskInfo.displayId, taskInfo.taskId, taskInfo.isVisible,
                        taskInfo.configuration.windowConfiguration.getBounds());
            });
        }
        updateLaunchAdjacentController();
    }

    @Override
    public void onTaskVanished(RunningTaskInfo taskInfo) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TASK_ORG, "Freeform Task Vanished: #%d",
                taskInfo.taskId);
        mTasks.remove(taskInfo.taskId);

        if (!DesktopExperienceFlags.ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS.isTrue()
                && mDesktopState.canEnterDesktopMode()
                && mDesktopUserRepositories.isPresent()) {
            DesktopRepository repository =
                    mDesktopUserRepositories.get().getProfile(taskInfo.userId);
            boolean isMinimized = repository.isMinimizedTask(taskInfo.taskId);
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE,
                    "Freeform Task Vanished: #%d, isMinimized=%b",
                    taskInfo.taskId, isMinimized);
            // TODO: b/370038902 - Handle Activity#finishAndRemoveTask.
            if (!DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION.isTrue()
                    || !isMinimized) {
                // A task that's vanishing should be removed:
                // - If it's not yet minimized. It can be minimized when a back navigation is
                // triggered on a task and the task is closing. It will be marked as minimized in
                // [DesktopTasksTransitionObserver] before it gets here.
                repository.removeClosingTask(taskInfo.taskId);
                repository.removeTask(taskInfo.taskId);
            }
        }
        // TODO: b/367268649 - This listener shouldn't need to call the transition observer directly
        // for logging once the logic in the observer is moved.
        mDesktopModeLoggerTransitionObserver.onTaskVanished(taskInfo);
        mWindowDecorationViewModel.onTaskVanished(taskInfo);
        updateLaunchAdjacentController();
    }

    @Override
    public void onTaskInfoChanged(RunningTaskInfo taskInfo) {
        final State state = mTasks.get(taskInfo.taskId);

        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TASK_ORG, "Freeform Task Info Changed: #%d",
                taskInfo.taskId);
        mDesktopTasksController.ifPresent(c -> c.onTaskInfoChanged(taskInfo));
        mWindowDecorationViewModel.onTaskInfoChanged(taskInfo);
        state.mTaskInfo = taskInfo;
        if (mDesktopState.canEnterDesktopMode()) {
            if (DesktopExperienceFlags.ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS.isTrue()) {
                // Pass task info changes to the [TaskChangeListener] since [TransitionsObserver]
                // does not propagate all task info changes.
                mTaskChangeListener.ifPresent(listener ->
                        listener.onNonTransitionTaskChanging(taskInfo));
            } else if (mDesktopUserRepositories.isPresent()) {
                DesktopRepository currentRepo =
                        mDesktopUserRepositories.get().getProfile(taskInfo.userId);
                currentRepo.updateTask(taskInfo.displayId, taskInfo.taskId,
                        taskInfo.isVisible,
                        taskInfo.configuration.windowConfiguration.getBounds());
            }
        }
        updateLaunchAdjacentController();
    }

    private void updateLaunchAdjacentController() {
        if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue()) {
            // With multiple desks, freeform tasks are children of a root task controlled by
            // DesksOrganizer, so toggling launch-adjacent should be managed there.
            return;
        }
        for (int i = 0; i < mTasks.size(); i++) {
            if (mTasks.valueAt(i).mTaskInfo.isVisible) {
                mLaunchAdjacentController.setLaunchAdjacentEnabled(false);
                return;
            }
        }
        mLaunchAdjacentController.setLaunchAdjacentEnabled(true);
    }

    @Override
    public void onFocusTaskChanged(RunningTaskInfo taskInfo) {
        if (taskInfo.getWindowingMode() != WINDOWING_MODE_FREEFORM
                || DesktopExperienceFlags.ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS.isTrue()) {
            return;
        }
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TASK_ORG,
                "Freeform Task Focus Changed: #%d focused=%b",
                taskInfo.taskId, taskInfo.isFocused);
        if (mDesktopState.canEnterDesktopMode() && taskInfo.isFocused
                && mDesktopUserRepositories.isPresent()) {
            DesktopRepository repository =
                mDesktopUserRepositories.get().getProfile(taskInfo.userId);
            repository.addTask(taskInfo.displayId, taskInfo.taskId, taskInfo.isVisible,
                    taskInfo.configuration.windowConfiguration.getBounds());
        }
    }

    @Override
    public void attachChildSurfaceToTask(int taskId, SurfaceControl.Builder b) {
        b.setParent(findTaskSurface(taskId));
    }

    @Override
    public void reparentChildSurfaceToTask(int taskId, SurfaceControl sc,
            SurfaceControl.Transaction t) {
        t.reparent(sc, findTaskSurface(taskId));
    }

    private SurfaceControl findTaskSurface(int taskId) {
        if (!mTasks.contains(taskId)) {
            throw new IllegalArgumentException("There is no surface for taskId=" + taskId);
        }
        return mTasks.get(taskId).mLeash;
    }

    @Override
    public void dump(PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + this);
        pw.println(innerPrefix + mTasks.size() + " tasks");
    }

    @Override
    public String toString() {
        return TAG;
    }

    private static class State {
        RunningTaskInfo mTaskInfo;
        SurfaceControl mLeash;
    }
}
