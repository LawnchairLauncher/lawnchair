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

package com.android.wm.shell.unfold.animation;

import android.app.TaskInfo;
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;

/**
 * Interface for classes that handle animations of tasks when folding or unfolding
 * foldable devices.
 */
public interface UnfoldTaskAnimator {
    /**
     * Initializes the animator, this should be called once in the lifetime of the animator
     */
    default void init() {}

    /**
     * Starts the animator, it might start listening for some events from the system.
     * Applying animation should be done only when animator is started.
     * Animator could be started/stopped several times.
     */
    default void start() {}

    /**
     * Stops the animator, it could unsubscribe from system events.
     */
    default void stop() {}

    /**
     * If this method returns true then task updates will be propagated to
     * the animator using the onTaskAppeared/Changed/Vanished callbacks.
     * @return true if this task should be animated by this animator
     */
    default boolean isApplicableTask(TaskInfo taskInfo) {
        return false;
    }

    /**
     * Called whenever a task applicable to this animator appeared
     * (isApplicableTask returns true for this task)
     *
     * @param taskInfo info of the appeared task
     * @param leash surface of the task
     */
    default void onTaskAppeared(TaskInfo taskInfo, SurfaceControl leash) {}

    /**
     * Called whenever a task applicable to this animator changed
     * @param taskInfo info of the changed task
     */
    default void onTaskChanged(TaskInfo taskInfo) {}

    /**
     * Called whenever a task applicable to this animator vanished
     * @param taskInfo info of the vanished task
     */
    default void onTaskVanished(TaskInfo taskInfo) {}

    /**
     * @return true if there tasks that could be potentially animated
     */
    default boolean hasActiveTasks() {
        return false;
    }

    /**
     * Clears all registered tasks in the animator
     */
    default void clearTasks() {}

    /**
     * Apply task surfaces transformations based on the current unfold progress
     * @param progress unfold transition progress
     * @param transaction to write changes to
     */
    default void applyAnimationProgress(float progress, Transaction transaction) {}

    /**
     * Apply task surfaces transformations that should be set before starting the animation
     * @param transaction to write changes to
     */
    default void prepareStartTransaction(Transaction transaction) {}

    /**
     * Apply task surfaces transformations that should be set after finishing the animation
     * @param transaction to write changes to
     */
    default void prepareFinishTransaction(Transaction transaction) {}

    /**
     * Resets task surface to its initial transformation
     * @param transaction to write changes to
     */
    default void resetSurface(TaskInfo taskInfo, Transaction transaction) {}

    /**
     * Resets all task surfaces to their initial transformations
     * @param transaction to write changes to
     */
    default void resetAllSurfaces(Transaction transaction) {}
}
