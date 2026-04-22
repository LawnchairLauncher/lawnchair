/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.transition;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.INVALID_DISPLAY;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.window.DesktopExperienceFlags.ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS;
import static android.window.TransitionInfo.FLAG_IS_DISPLAY;
import static android.window.TransitionInfo.FLAG_MOVED_TO_TOP;

import static com.android.wm.shell.transition.Transitions.TransitionObserver;

import android.annotation.NonNull;
import android.app.ActivityManager.RunningTaskInfo;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.IndentingPrintWriter;
import android.util.Slog;
import android.util.SparseArray;
import android.window.DesktopExperienceFlags;
import android.window.TransitionInfo;

import com.android.wm.shell.shared.FocusTransitionListener;
import com.android.wm.shell.shared.IFocusTransitionListener;
import com.android.wm.shell.shared.TransitionUtil.LeafTaskFilter;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * The {@link TransitionObserver} that observes for transitions involving focus switch.
 * It reports transitions to callers outside of the process via {@link IFocusTransitionListener},
 * and callers within the process via {@link FocusTransitionListener}.
 */
public class FocusTransitionObserver {
    private static final String TAG = FocusTransitionObserver.class.getSimpleName();

    private IFocusTransitionListener mRemoteListener;
    private final Map<FocusTransitionListener, Executor> mLocalListeners =
            new HashMap<>();

    private int mFocusedDisplayId = DEFAULT_DISPLAY;
    private final SparseArray<RunningTaskInfo> mFocusedTaskOnDisplay = new SparseArray<>();

    private final ArraySet<RunningTaskInfo> mTmpTasksToBeNotified = new ArraySet<>();

    public FocusTransitionObserver() {}

    /**
     * Update display/window focus state from the given transition info and notifies changes if any.
     */
    public void updateFocusState(@NonNull TransitionInfo info) {
        if (!ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS.isTrue()) {
            return;
        }
        final SparseArray<RunningTaskInfo> lastTransitionFocusedTasks =
                mFocusedTaskOnDisplay.clone();

        // Find all leaf tasks in the transition.
        final List<Integer> leafTasks = new ArrayList<>();
        final LeafTaskFilter leafTaskFilter = new LeafTaskFilter();
        for (int i = 0; i < info.getChanges().size(); i++) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (leafTaskFilter.test(change)) {
                leafTasks.add(change.getTaskInfo().taskId);
            }
        }
        final List<TransitionInfo.Change> changes = info.getChanges();
        // Iterate in reverse, so front-most tasks are processed last.
        for (int i = changes.size() - 1; i >= 0; i--) {
            final TransitionInfo.Change change = changes.get(i);

            final RunningTaskInfo task = change.getTaskInfo();
            final boolean updateTaskFocus =
                    DesktopExperienceFlags.EXCLUDE_DESK_ROOTS_FROM_DESKTOP_TASKS.isTrue()
                            ? (task != null && leafTasks.contains(task.taskId))
                            : task != null;
            if (updateTaskFocus) {
                if (change.hasFlags(FLAG_MOVED_TO_TOP) || change.getMode() == TRANSIT_OPEN) {
                    updateFocusedTaskPerDisplay(task, task.displayId);
                } else {
                    // Update focus assuming that any task moved to another display is focused in
                    // the new display.
                    // TODO(sahok): remove this logic when b/388665104 is fixed
                    final boolean isBeyondDisplay = change.getStartDisplayId() != INVALID_DISPLAY
                            && change.getEndDisplayId() != INVALID_DISPLAY
                            && change.getStartDisplayId() != change.getEndDisplayId();

                    RunningTaskInfo lastTransitionFocusedTaskOnStartDisplay =
                            lastTransitionFocusedTasks.get(change.getStartDisplayId());
                    final boolean isLastTransitionFocused =
                            lastTransitionFocusedTaskOnStartDisplay != null
                                    && task.taskId
                                            == lastTransitionFocusedTaskOnStartDisplay.taskId;
                    if (change.getMode() == TRANSIT_CHANGE && isBeyondDisplay
                            && isLastTransitionFocused) {
                        // The task have moved to another display and keeps its focus.
                        // MOVE_TO_TOP is not reported but we need to update the focused task in
                        // the end display.
                        updateFocusedTaskPerDisplay(task, change.getEndDisplayId());
                    }
                }
            }


            if (change.hasFlags(FLAG_IS_DISPLAY) && change.hasFlags(FLAG_MOVED_TO_TOP)) {
                if (mFocusedDisplayId != change.getEndDisplayId()) {
                    updateFocusedDisplay(change.getEndDisplayId());
                }
            }
        }
        mTmpTasksToBeNotified.forEach(this::notifyTaskFocusChanged);
        mTmpTasksToBeNotified.clear();
    }

    private void updateFocusedTaskPerDisplay(RunningTaskInfo task, int displayId) {
        final RunningTaskInfo lastFocusedTaskOnDisplay =
                mFocusedTaskOnDisplay.get(displayId);
        if (lastFocusedTaskOnDisplay != null) {
            mTmpTasksToBeNotified.add(lastFocusedTaskOnDisplay);
        }
        mTmpTasksToBeNotified.add(task);
        mFocusedTaskOnDisplay.put(displayId, task);
    }

    private void updateFocusedDisplay(int endDisplayId) {
        final RunningTaskInfo lastGloballyFocusedTask =
                mFocusedTaskOnDisplay.get(mFocusedDisplayId);
        if (lastGloballyFocusedTask != null) {
            mTmpTasksToBeNotified.add(lastGloballyFocusedTask);
        }
        mFocusedDisplayId = endDisplayId;
        notifyFocusedDisplayChanged();
        final RunningTaskInfo currentGloballyFocusedTask =
                mFocusedTaskOnDisplay.get(mFocusedDisplayId);
        if (currentGloballyFocusedTask != null) {
            mTmpTasksToBeNotified.add(currentGloballyFocusedTask);
        }
    }

    /**
     * Sets the focus transition listener that receives any transitions resulting in focus switch.
     * This is for calls from outside the Shell, within the host process.
     *
     */
    public void setLocalFocusTransitionListener(FocusTransitionListener listener,
            Executor executor) {
        if (!ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS.isTrue()) {
            return;
        }
        mLocalListeners.put(listener, executor);
        executor.execute(() -> {
            listener.onFocusedDisplayChanged(mFocusedDisplayId);
            mTmpTasksToBeNotified.forEach(this::notifyTaskFocusChanged);
        });
    }

    /**
     * Unsets the focus transition listener that receives any transitions resulting in focus switch.
     * This is for calls from outside the Shell, within the host process.
     *
     */
    public void unsetLocalFocusTransitionListener(FocusTransitionListener listener) {
        if (!ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS.isTrue()) {
            return;
        }
        mLocalListeners.remove(listener);
    }

    /**
     * Sets the focus transition listener that receives any transitions resulting in focus switch.
     * This is for calls from outside the host process.
     */
    public void setRemoteFocusTransitionListener(Transitions transitions,
            IFocusTransitionListener listener) {
        if (!ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS.isTrue()) {
            return;
        }
        mRemoteListener = listener;
        notifyFocusedDisplayChangedToRemote();
    }

    private void notifyTaskFocusChanged(RunningTaskInfo task) {
        final boolean isFocusedOnDisplay = isFocusedOnDisplay(task);
        final boolean isFocusedGlobally = hasGlobalFocus(task);
        mLocalListeners.forEach((listener, executor) ->
                executor.execute(() -> listener.onFocusedTaskChanged(task, isFocusedOnDisplay,
                        isFocusedGlobally)));
    }

    private void notifyFocusedDisplayChanged() {
        notifyFocusedDisplayChangedToRemote();
        mLocalListeners.forEach((listener, executor) ->
                executor.execute(() -> {
                    listener.onFocusedDisplayChanged(mFocusedDisplayId);
                }));
    }

    private void notifyFocusedDisplayChangedToRemote() {
        if (mRemoteListener != null) {
            try {
                mRemoteListener.onFocusedDisplayChanged(mFocusedDisplayId);
            } catch (RemoteException e) {
                Slog.w(TAG, "Failed call notifyFocusedDisplayChangedToRemote", e);
            }
        }
    }

    private boolean isFocusedOnDisplay(@NonNull RunningTaskInfo task) {
        if (!ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS.isTrue()) {
            return task.isFocused;
        }
        final RunningTaskInfo focusedTaskOnDisplay = mFocusedTaskOnDisplay.get(task.displayId);
        return focusedTaskOnDisplay != null && focusedTaskOnDisplay.taskId == task.taskId;
    }

    /** Returns the globally focused display id. */
    public int getGloballyFocusedDisplayId() {
        if (!ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS.isTrue()
                || mFocusedDisplayId == INVALID_DISPLAY) {
            return INVALID_DISPLAY;
        }
        return mFocusedDisplayId;
    }

    /**
     * Gets the globally focused task ID.
     */
    public int getGloballyFocusedTaskId() {
        if (!ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS.isTrue()
                || mFocusedDisplayId == INVALID_DISPLAY) {
            return INVALID_TASK_ID;
        }
        final RunningTaskInfo globallyFocusedTask = mFocusedTaskOnDisplay.get(mFocusedDisplayId);
        return globallyFocusedTask != null ? globallyFocusedTask.taskId : INVALID_TASK_ID;
    }

    /**
     * Checks whether the given task has focused globally on the system.
     * (Note {@link RunningTaskInfo#isFocused} represents per-display focus.)
     */
    public boolean hasGlobalFocus(@NonNull RunningTaskInfo task) {
        if (!ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS.isTrue()) {
            return task.isFocused;
        }
        return task.displayId == mFocusedDisplayId && isFocusedOnDisplay(task);
    }

    /** Dumps focused display and tasks. */
    public void dump(PrintWriter originalWriter, String prefix) {
        final IndentingPrintWriter writer =
                new IndentingPrintWriter(originalWriter, "    ", prefix);
        writer.println("FocusTransitionObserver:");
        writer.increaseIndent();
        writer.printf("currentFocusedDisplayId=%d\n", mFocusedDisplayId);
        writer.println("currentFocusedTaskOnDisplay:");
        writer.increaseIndent();
        for (int i = 0; i < mFocusedTaskOnDisplay.size(); i++) {
            writer.printf("Display #%d: taskId=%d topActivity=%s\n",
                    mFocusedTaskOnDisplay.keyAt(i),
                    mFocusedTaskOnDisplay.valueAt(i).taskId,
                    mFocusedTaskOnDisplay.valueAt(i).topActivity);
        }
    }
}
