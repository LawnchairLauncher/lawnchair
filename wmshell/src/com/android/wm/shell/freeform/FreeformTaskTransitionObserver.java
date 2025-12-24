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

package com.android.wm.shell.freeform;

import static com.android.wm.shell.transition.Transitions.TRANSIT_START_RECENTS_TRANSITION;

import android.app.ActivityManager;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.DesktopExperienceFlags;
import android.window.DesktopModeFlags;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.wm.shell.desktopmode.DesktopBackNavTransitionObserver;
import com.android.wm.shell.desktopmode.DesktopImeHandler;
import com.android.wm.shell.desktopmode.DesktopImmersiveController;
import com.android.wm.shell.desktopmode.DesktopInOrderTransitionObserver;
import com.android.wm.shell.desktopmode.DesktopModeLoggerTransitionObserver;
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer;
import com.android.wm.shell.desktopmode.multidesks.DesksTransitionObserver;
import com.android.wm.shell.shared.desktopmode.DesktopState;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.FocusTransitionObserver;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The {@link Transitions.TransitionHandler} that handles freeform task launches, closes, maximizing
 * and restoring transitions. It also reports transitions so that window decorations can be a part
 * of transitions.
 */
public class FreeformTaskTransitionObserver implements Transitions.TransitionObserver {
    private final Transitions mTransitions;
    private final Optional<DesktopImmersiveController> mDesktopImmersiveController;
    private final WindowDecorViewModel mWindowDecorViewModel;
    private final Optional<TaskChangeListener> mTaskChangeListener;
    private final FocusTransitionObserver mFocusTransitionObserver;
    private final DesksOrganizer mDesksOrganizer;
    private final Optional<DesksTransitionObserver> mDesksTransitionObserver;
    private final Optional<DesktopImeHandler> mDesktopImeHandler;
    private final Optional<DesktopBackNavTransitionObserver> mDesktopBackNavTransitionObserver;
    private final Optional<DesktopInOrderTransitionObserver> mDesktopInOrderTransitionObserver;
    private final DesktopModeLoggerTransitionObserver mDesktopModeLoggerTransitionObserver;

    private final Map<IBinder, List<ActivityManager.RunningTaskInfo>> mTransitionToTaskInfo =
            new HashMap<>();
    private final Map<Integer, ActivityManager.RunningTaskInfo> mPendingHiddenTasks =
            new HashMap<>();
    private IBinder mTransientTransition;

    public FreeformTaskTransitionObserver(
            ShellInit shellInit,
            Transitions transitions,
            Optional<DesktopImmersiveController> desktopImmersiveController,
            WindowDecorViewModel windowDecorViewModel,
            Optional<TaskChangeListener> taskChangeListener,
            FocusTransitionObserver focusTransitionObserver,
            DesksOrganizer desksOrganizer,
            Optional<DesksTransitionObserver> desksTransitionObserver,
            DesktopState desktopState,
            Optional<DesktopImeHandler> desktopImeHandler,
            Optional<DesktopBackNavTransitionObserver> desktopBackNavTransitionObserver,
            Optional<DesktopInOrderTransitionObserver> desktopInOrderTransitionObserver,
            DesktopModeLoggerTransitionObserver desktopModeLoggerTransitionObserver) {
        mTransitions = transitions;
        mDesktopImmersiveController = desktopImmersiveController;
        mWindowDecorViewModel = windowDecorViewModel;
        mTaskChangeListener = taskChangeListener;
        mFocusTransitionObserver = focusTransitionObserver;
        mDesksOrganizer = desksOrganizer;
        mDesksTransitionObserver = desksTransitionObserver;
        mDesktopImeHandler = desktopImeHandler;
        mDesktopBackNavTransitionObserver = desktopBackNavTransitionObserver;
        mDesktopInOrderTransitionObserver = desktopInOrderTransitionObserver;
        mDesktopModeLoggerTransitionObserver = desktopModeLoggerTransitionObserver;
        if (FreeformComponents.requiresFreeformComponents(desktopState)) {
            shellInit.addInitCallback(this::onInit, this);
        }
    }

    @VisibleForTesting
    void onInit() {
        mTransitions.registerObserver(this);
    }

    @Override
    public void onTransitionReady(
            @NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT) {
        if (DesktopExperienceFlags.ENABLE_INORDER_TRANSITION_CALLBACKS_FOR_DESKTOP.isTrue()) {
            mDesktopInOrderTransitionObserver.ifPresent(
                    o -> o.onTransitionReady(transition, info, startT, finishT));
        } else {
            // Update desk state first, otherwise [TaskChangeListener] may update desktop task state
            // under an outdated active desk if a desk switch and a task update happen in the same
            // transition, such as when unminimizing a task from an inactive desk.
            mDesksTransitionObserver.ifPresent(o -> o.onTransitionReady(transition, info));
            if (DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue()) {
                // TODO(b/367268953): Remove when DesktopTaskListener is introduced and the
                //  repository
                //  is updated from there **before** the |mWindowDecorViewModel| methods are
                //  invoked.
                //  Otherwise window decoration relayout won't run with the immersive state up to
                //  date.
                mDesktopImmersiveController.ifPresent(
                        h -> h.onTransitionReady(transition, info, startT, finishT));
            }
            // Update focus state first to ensure the correct state can be queried from listeners.
            // TODO(371503964): Remove this once the unified task repository is ready.
            mFocusTransitionObserver.updateFocusState(info);

            // Call after the focus state update to have the correct focused window.
            mDesktopImeHandler.ifPresent(o -> o.onTransitionReady(transition, info));
            mDesktopBackNavTransitionObserver.ifPresent(o -> o.onTransitionReady(transition, info));
            mDesktopModeLoggerTransitionObserver.onTransitionReady(transition, info, startT,
                    finishT);

        }
        final ArrayList<ActivityManager.RunningTaskInfo> taskInfoList = new ArrayList<>();
        final ArrayList<WindowContainerToken> taskParents = new ArrayList<>();
        final ArrayList<TransitionInfo.Change> filteredChanges = new ArrayList<>();

        for (TransitionInfo.Change change : info.getChanges()) {
            if (shouldSkipChange(info, change, taskParents)) continue;
            filteredChanges.add(change);
        }

        if (DesktopExperienceFlags.ENABLE_WINDOWING_TASK_STACK_ORDER_BUGFIX.isTrue()) {
            for (TransitionInfo.Change change : filteredChanges.reversed()) {
                notifyChange(transition, info, startT, finishT, change, taskInfoList);
            }
        } else {
            for (TransitionInfo.Change change : filteredChanges) {
                notifyChange(transition, info, startT, finishT, change, taskInfoList);
            }
        }

        mTransitionToTaskInfo.put(transition, taskInfoList);
    }

    private void notifyChange(
            @NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT,
            TransitionInfo.Change change,
            ArrayList<ActivityManager.RunningTaskInfo> taskInfoList) {
        switch (change.getMode()) {
            case WindowManager.TRANSIT_OPEN:
                onOpenTransitionReady(change, startT, finishT);
                break;
            case WindowManager.TRANSIT_TO_FRONT:
                onToFrontTransitionReady(change, startT, finishT);
                break;
            case WindowManager.TRANSIT_CHANGE:
                onChangeTransitionReady(change, startT, finishT);
                break;
            case WindowManager.TRANSIT_TO_BACK: {
                if (info.getType() == TRANSIT_START_RECENTS_TRANSITION) {
                    mTransientTransition = transition;
                }
                onToBackTransitionReady(change, startT, finishT);
                break;
            }
            case WindowManager.TRANSIT_CLOSE: {
                taskInfoList.add(change.getTaskInfo());
                onCloseTransitionReady(change, startT, finishT);
                break;
            }
            default:
                break;
        }
    }

    private boolean shouldSkipChange(
            @NonNull TransitionInfo info,
            TransitionInfo.Change change,
            ArrayList<WindowContainerToken> taskParents) {
        if ((change.getFlags() & TransitionInfo.FLAG_IS_WALLPAPER) != 0) {
            return true;
        }

        // Skip desk changes so that window decorations are not added to desk root tasks
        if (DesktopExperienceFlags.ENABLE_NO_WINDOW_DECORATION_FOR_DESKS.isTrue()
                && mDesksOrganizer.isDeskChange(change)) {
            return true;
        }

        final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
        if (taskInfo == null || taskInfo.taskId == -1) {
            return true;
        }
        // Filter out non-leaf tasks. Freeform/fullscreen don't nest tasks, but split-screen
        // does, so this prevents adding duplicate captions in that scenario.
        if (change.getParent() != null
                && info.getChange(change.getParent()).getTaskInfo() != null) {
            // This logic relies on 2 assumptions: 1 is that child tasks will be visited before
            // parents (due to how z-order works). 2 is that no non-tasks are interleaved
            // between tasks (hierarchically).
            taskParents.add(change.getParent());
        }
        return taskParents.contains(change.getContainer());
    }

    private void onOpenTransitionReady(
            TransitionInfo.Change change,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        mTaskChangeListener.ifPresent(listener -> listener.onTaskOpening(change.getTaskInfo()));
        mWindowDecorViewModel.onTaskOpening(
                change.getTaskInfo(), change.getLeash(), startT, finishT);
        mPendingHiddenTasks.remove(change.getTaskInfo().taskId);
    }

    private void onCloseTransitionReady(
            TransitionInfo.Change change,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        mTaskChangeListener.ifPresent(listener -> listener.onTaskClosing(change.getTaskInfo()));
        mWindowDecorViewModel.onTaskClosing(change.getTaskInfo(), startT, finishT);
    }

    private void onChangeTransitionReady(
            TransitionInfo.Change change,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        mTaskChangeListener.ifPresent(listener -> listener.onTaskChanging(change.getTaskInfo()));
        mWindowDecorViewModel.onTaskChanging(
                change.getTaskInfo(), change.getLeash(), startT, finishT);
        mPendingHiddenTasks.remove(change.getTaskInfo().taskId);
    }

    private void onToFrontTransitionReady(
            TransitionInfo.Change change,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        mTaskChangeListener.ifPresent(
                listener -> listener.onTaskMovingToFront(change.getTaskInfo()));
        mWindowDecorViewModel.onTaskChanging(
                change.getTaskInfo(), change.getLeash(), startT, finishT);
        mPendingHiddenTasks.remove(change.getTaskInfo().taskId);
    }

    private void onToBackTransitionReady(
            TransitionInfo.Change change,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        if (mTransientTransition != null) {
            // The tasks will be transiently hidden, which means they are still visible.
            mPendingHiddenTasks.put(change.getTaskInfo().taskId, change.getTaskInfo());
        } else {
            mTaskChangeListener.ifPresent(
                    listener -> listener.onTaskMovingToBack(change.getTaskInfo()));
        }
        mWindowDecorViewModel.onTaskChanging(
                change.getTaskInfo(), change.getLeash(), startT, finishT);
    }

    @Override
    public void onTransitionStarting(@NonNull IBinder transition) {
        if (DesktopExperienceFlags.ENABLE_INORDER_TRANSITION_CALLBACKS_FOR_DESKTOP.isTrue()) {
            mDesktopInOrderTransitionObserver.ifPresent(o -> o.onTransitionStarting(transition));
        } else {
            if (DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue()) {
                // TODO(b/367268953): Remove when DesktopTaskListener is introduced.
                mDesktopImmersiveController.ifPresent(h -> h.onTransitionStarting(transition));
            }
        }
    }

    @Override
    public void onTransitionMerged(@NonNull IBinder merged, @NonNull IBinder playing) {
        if (DesktopExperienceFlags.ENABLE_INORDER_TRANSITION_CALLBACKS_FOR_DESKTOP.isTrue()) {
            mDesktopInOrderTransitionObserver.ifPresent(o -> o.onTransitionMerged(merged, playing));
        } else {
            mDesksTransitionObserver.ifPresent(o -> o.onTransitionMerged(merged, playing));
            if (DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue()) {
                // TODO(b/367268953): Remove when DesktopTaskListener is introduced.
                mDesktopImmersiveController.ifPresent(h -> h.onTransitionMerged(merged, playing));
            }
        }

        final List<ActivityManager.RunningTaskInfo> infoOfMerged =
                mTransitionToTaskInfo.get(merged);
        if (infoOfMerged == null) {
            // We are adding window decorations of the merged transition to them of the playing
            // transition so if there is none of them there is nothing to do.
            return;
        }
        mTransitionToTaskInfo.remove(merged);

        final List<ActivityManager.RunningTaskInfo> infoOfPlaying =
                mTransitionToTaskInfo.get(playing);
        if (infoOfPlaying != null) {
            infoOfPlaying.addAll(infoOfMerged);
        } else {
            mTransitionToTaskInfo.put(playing, infoOfMerged);
        }
    }

    @Override
    public void onTransitionFinished(@NonNull IBinder transition, boolean aborted) {
        if (DesktopExperienceFlags.ENABLE_INORDER_TRANSITION_CALLBACKS_FOR_DESKTOP.isTrue()) {
            mDesktopInOrderTransitionObserver.ifPresent(
                    o -> o.onTransitionFinished(transition, aborted));
        } else {
            mDesksTransitionObserver.ifPresent(o -> o.onTransitionFinished(transition));
            if (DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue()) {
                // TODO(b/367268953): Remove when DesktopTaskListener is introduced.
                mDesktopImmersiveController.ifPresent(
                        h -> h.onTransitionFinished(transition, aborted));
            }
            mDesktopModeLoggerTransitionObserver.onTransitionFinished(transition, aborted);
        }

        final List<ActivityManager.RunningTaskInfo> taskInfo =
                mTransitionToTaskInfo.getOrDefault(transition, Collections.emptyList());
        mTransitionToTaskInfo.remove(transition);
        for (int i = 0; i < taskInfo.size(); ++i) {
            mWindowDecorViewModel.destroyWindowDecoration(taskInfo.get(i));
        }

        if (transition == mTransientTransition) {
            for (ActivityManager.RunningTaskInfo task : mPendingHiddenTasks.values()) {
                mTaskChangeListener.ifPresent(it -> it.onTaskMovingToBack(task));
            }
            mPendingHiddenTasks.clear();
            mTransientTransition = null;
        }
    }
}
