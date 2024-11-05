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

import android.app.ActivityManager;
import android.content.Context;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@link Transitions.TransitionHandler} that handles freeform task launches, closes,
 * maximizing and restoring transitions. It also reports transitions so that window decorations can
 * be a part of transitions.
 */
public class FreeformTaskTransitionObserver implements Transitions.TransitionObserver {
    private final Transitions mTransitions;
    private final WindowDecorViewModel mWindowDecorViewModel;

    private final Map<IBinder, List<ActivityManager.RunningTaskInfo>> mTransitionToTaskInfo =
            new HashMap<>();

    public FreeformTaskTransitionObserver(
            Context context,
            ShellInit shellInit,
            Transitions transitions,
            WindowDecorViewModel windowDecorViewModel) {
        mTransitions = transitions;
        mWindowDecorViewModel = windowDecorViewModel;
        if (Transitions.ENABLE_SHELL_TRANSITIONS && FreeformComponents.isFreeformEnabled(context)) {
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
        final ArrayList<ActivityManager.RunningTaskInfo> taskInfoList = new ArrayList<>();
        final ArrayList<WindowContainerToken> taskParents = new ArrayList<>();
        for (TransitionInfo.Change change : info.getChanges()) {
            if ((change.getFlags() & TransitionInfo.FLAG_IS_WALLPAPER) != 0) {
                continue;
            }

            final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
            if (taskInfo == null || taskInfo.taskId == -1) {
                continue;
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
            if (taskParents.contains(change.getContainer())) {
                continue;
            }

            switch (change.getMode()) {
                case WindowManager.TRANSIT_OPEN:
                    onOpenTransitionReady(change, startT, finishT);
                    break;
                case WindowManager.TRANSIT_TO_FRONT:
                    onToFrontTransitionReady(change, startT, finishT);
                    break;
                case WindowManager.TRANSIT_CLOSE: {
                    taskInfoList.add(change.getTaskInfo());
                    onCloseTransitionReady(change, startT, finishT);
                    break;
                }
                case WindowManager.TRANSIT_CHANGE:
                    onChangeTransitionReady(change, startT, finishT);
                    break;
            }
        }
        mTransitionToTaskInfo.put(transition, taskInfoList);
    }

    private void onOpenTransitionReady(
            TransitionInfo.Change change,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        mWindowDecorViewModel.onTaskOpening(
                change.getTaskInfo(), change.getLeash(), startT, finishT);
    }

    private void onCloseTransitionReady(
            TransitionInfo.Change change,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        mWindowDecorViewModel.onTaskClosing(change.getTaskInfo(), startT, finishT);
    }

    private void onChangeTransitionReady(
            TransitionInfo.Change change,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        mWindowDecorViewModel.onTaskChanging(
                change.getTaskInfo(), change.getLeash(), startT, finishT);
    }

    private void onToFrontTransitionReady(
            TransitionInfo.Change change,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        mWindowDecorViewModel.onTaskChanging(
                change.getTaskInfo(), change.getLeash(), startT, finishT);
    }

    @Override
    public void onTransitionStarting(@NonNull IBinder transition) {}

    @Override
    public void onTransitionMerged(@NonNull IBinder merged, @NonNull IBinder playing) {
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
        final List<ActivityManager.RunningTaskInfo> taskInfo =
                mTransitionToTaskInfo.getOrDefault(transition, Collections.emptyList());
        mTransitionToTaskInfo.remove(transition);
        for (int i = 0; i < taskInfo.size(); ++i) {
            mWindowDecorViewModel.destroyWindowDecoration(taskInfo.get(i));
        }
    }
}