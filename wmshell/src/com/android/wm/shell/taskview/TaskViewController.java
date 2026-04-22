/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.wm.shell.taskview;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.Rect;
import android.view.SurfaceControl;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import com.android.wm.shell.ShellTaskOrganizer;

/**
 * Interface which provides methods to control TaskView properties and state.
 *
 * <ul>
 *     <li>To start an activity based task view, use {@link #startActivity}</li>
 *
 *     <li>To start an activity (represented by {@link ShortcutInfo}) based task view, use
 *     {@link #startShortcutActivity}
 *     </li>
 *
 *     <li>To start a root-task based task view, use {@link #startRootTask}.
 *     This method is special as it doesn't create a root task and instead expects that the
 *     launch root task is already created and started. This method just attaches the taskInfo to
 *     the TaskView.
 *     </li>
 * </ul>
 */
public interface TaskViewController {
    /** Registers a TaskView with this controller. */
    void registerTaskView(@NonNull TaskViewTaskController tv);

    /** Un-registers a TaskView from this controller. */
    void unregisterTaskView(@NonNull TaskViewTaskController tv);

    /**
     * Launch an activity represented by {@link ShortcutInfo}.
     * <p>The owner of this container must be allowed to access the shortcut information,
     * as defined in {@link LauncherApps#hasShortcutHostPermission()} to use this method.
     *
     * @param destination  the TaskView to start the shortcut into.
     * @param shortcut     the shortcut used to launch the activity.
     * @param options      options for the activity.
     * @param launchBounds the bounds (window size and position) that the activity should be
     *                     launched in, in pixels and in screen coordinates.
     */
    void startShortcutActivity(@NonNull TaskViewTaskController destination,
            @NonNull ShortcutInfo shortcut,
            @NonNull ActivityOptions options, @Nullable Rect launchBounds);

    /**
     * Launch a new activity into a TaskView
     *
     * @param destination   The TaskView to start the activity into.
     * @param pendingIntent Intent used to launch an activity.
     * @param fillInIntent  Additional Intent data, see {@link Intent#fillIn Intent.fillIn()}
     * @param options       options for the activity.
     * @param launchBounds  the bounds (window size and position) that the activity should be
     *                      launched in, in pixels and in screen coordinates.
     */
    void startActivity(@NonNull TaskViewTaskController destination,
            @NonNull PendingIntent pendingIntent, @Nullable Intent fillInIntent,
            @NonNull ActivityOptions options, @Nullable Rect launchBounds);

    /**
     * Attaches the given root task {@code taskInfo} in the task view.
     *
     * <p> Since {@link ShellTaskOrganizer#createRootTask(CreateRootTaskRequest,
     * ShellTaskOrganizer.TaskListener)} does not use the shell transitions flow, this method is
     * used as an entry point for an already-created root-task in the task view.
     *
     * @param destination The TaskView to put the root-task into.
     * @param taskInfo    the task info of the root task.
     * @param leash       the {@link android.content.pm.ShortcutInfo.Surface} of the root task
     * @param wct         The Window container work that should happen as part of this set up.
     */
    void startRootTask(@NonNull TaskViewTaskController destination,
            ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash,
            @Nullable WindowContainerTransaction wct);

    /**
     * Closes a taskview and removes the task from window manager. This task will not appear in
     * recents.
     */
    void removeTaskView(@NonNull TaskViewTaskController taskView,
            @Nullable WindowContainerToken taskToken);

    /**
     * Moves the current task in TaskView out of the view and back to fullscreen.
     */
    void moveTaskViewToFullscreen(@NonNull TaskViewTaskController taskView);

    /**
     * Starts a new transition to make the given {@code taskView} visible and optionally change
     * the task order.
     *
     * @param taskView the task view which the visibility is being changed for
     * @param visible  the new visibility of the task view
     */
    void setTaskViewVisible(TaskViewTaskController taskView, boolean visible);

    /**
     * Sets the task bounds to {@code boundsOnScreen}.
     * Usually called when the taskview's position or size has changed.
     *
     * @param boundsOnScreen the on screen bounds of the surface view.
     */
    void setTaskBounds(TaskViewTaskController taskView, Rect boundsOnScreen);
}
