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

package com.android.wm.shell.windowdecor;

import android.app.ActivityManager;
import android.view.SurfaceControl;

import com.android.wm.shell.freeform.FreeformTaskTransitionStarter;
import com.android.wm.shell.splitscreen.SplitScreenController;

/**
 * The interface used by some {@link com.android.wm.shell.ShellTaskOrganizer.TaskListener} to help
 * customize {@link WindowDecoration}. Its implementations are responsible to interpret user's
 * interactions with UI widgets in window decorations and send corresponding requests to system
 * servers.
 */
public interface WindowDecorViewModel {
    /**
     * Sets the transition starter that starts freeform task transitions. Only called when
     * {@link com.android.wm.shell.transition.Transitions#ENABLE_SHELL_TRANSITIONS} is {@code true}.
     *
     * @param transitionStarter the transition starter that starts freeform task transitions
     */
    void setFreeformTaskTransitionStarter(FreeformTaskTransitionStarter transitionStarter);

    /**
     * Sets the {@link SplitScreenController} if available.
     */
    void setSplitScreenController(SplitScreenController splitScreenController);

    /**
     * Creates a window decoration for the given task. Can be {@code null} for Fullscreen tasks but
     * not Freeform ones.
     *
     * @param taskInfo    the initial task info of the task
     * @param taskSurface the surface of the task
     * @param startT      the start transaction to be applied before the transition
     * @param finishT     the finish transaction to restore states after the transition
     * @return {@code true} if window decoration was created, {@code false} otherwise
     */
    boolean onTaskOpening(
            ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl taskSurface,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT);

    /**
     * Notifies a task info update on the given task, with the window decoration created previously
     * for this task by {@link #onTaskOpening}.
     *
     * @param taskInfo the new task info of the task
     */
    void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo);

    /**
     * Notifies a task has vanished, which can mean that the task changed windowing mode or was
     * removed.
     *
     * @param taskInfo the task info of the task
     */
    void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo);

    /**
     * Notifies a transition is about to start about the given task to give the window decoration a
     * chance to prepare for this transition. Unlike {@link #onTaskInfoChanged}, this method creates
     * a window decoration if one does not exist but is required.
     *
     * @param taskInfo    the initial task info of the task
     * @param taskSurface the surface of the task
     * @param startT      the start transaction to be applied before the transition
     * @param finishT     the finish transaction to restore states after the transition
     */
    void onTaskChanging(
            ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl taskSurface,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT);

    /**
     * Notifies that the given task is about to close to give the window decoration a chance to
     * prepare for this transition.
     *
     * @param taskInfo the initial task info of the task
     * @param startT   the start transaction to be applied before the transition
     * @param finishT  the finish transaction to restore states after the transition
     */
    void onTaskClosing(
            ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT);

    /**
     * Destroys the window decoration of the give task.
     *
     * @param taskInfo the info of the task
     */
    void destroyWindowDecoration(ActivityManager.RunningTaskInfo taskInfo);
}