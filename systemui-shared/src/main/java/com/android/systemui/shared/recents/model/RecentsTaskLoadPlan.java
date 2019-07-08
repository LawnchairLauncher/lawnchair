/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.shared.recents.model;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.SparseBooleanArray;

import com.android.systemui.shared.recents.model.Task.TaskKey;
import com.android.systemui.shared.system.ActivityManagerWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * This class stores the loading state as it goes through multiple stages of loading:
 *   1) preloadRawTasks() will load the raw set of recents tasks from the system
 *   2) preloadPlan() will construct a new task stack with all metadata and only icons and
 *      thumbnails that are currently in the cache
 *   3) executePlan() will actually load and fill in the icons and thumbnails according to the load
 *      options specified, such that we can transition into the Recents activity seamlessly
 */
public class RecentsTaskLoadPlan {

    /** The set of conditions to preload tasks. */
    public static class PreloadOptions {
        public boolean loadTitles = true;
    }

    /** The set of conditions to load tasks. */
    public static class Options {
        public int runningTaskId = -1;
        public boolean loadIcons = true;
        public boolean loadThumbnails = false;
        public boolean onlyLoadForCache = false;
        public boolean onlyLoadPausedActivities = false;
        public int numVisibleTasks = 0;
        public int numVisibleTaskThumbnails = 0;
    }

    private final Context mContext;
    private final KeyguardManager mKeyguardManager;

    private List<ActivityManager.RecentTaskInfo> mRawTasks;
    private TaskStack mStack;

    private final SparseBooleanArray mTmpLockedUsers = new SparseBooleanArray();

    public RecentsTaskLoadPlan(Context context) {
        mContext = context;
        mKeyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
    }

    /**
     * Preloads the list of recent tasks from the system. After this call, the TaskStack will
     * have a list of all the recent tasks with their metadata, not including icons or
     * thumbnails which were not cached and have to be loaded.
     *
     * The tasks will be ordered by:
     * - least-recent to most-recent stack tasks
     *
     * Note: Do not lock, since this can be calling back to the loader, which separately also drives
     * this call (callers should synchronize on the loader before making this call).
     */
    public void preloadPlan(PreloadOptions opts, RecentsTaskLoader loader, int runningTaskId,
            int currentUserId) {
        Resources res = mContext.getResources();
        ArrayList<Task> allTasks = new ArrayList<>();
        if (mRawTasks == null) {
            mRawTasks = ActivityManagerWrapper.getInstance().getRecentTasks(
                    ActivityManager.getMaxRecentTasksStatic(), currentUserId);

            // Since the raw tasks are given in most-recent to least-recent order, we need to reverse it
            Collections.reverse(mRawTasks);
        }

        int taskCount = mRawTasks.size();
        for (int i = 0; i < taskCount; i++) {
            ActivityManager.RecentTaskInfo t = mRawTasks.get(i);

            // Compose the task key
            final ComponentName sourceComponent = t.origActivity != null
                    // Activity alias if there is one
                    ? t.origActivity
                    // The real activity if there is no alias (or the target if there is one)
                    : t.realActivity;
            final int windowingMode = t.configuration.windowConfiguration.getWindowingMode();
            TaskKey taskKey = new TaskKey(t.persistentId, windowingMode, t.baseIntent,
                    sourceComponent, t.userId, t.lastActiveTime);

            boolean isFreeformTask = windowingMode == WINDOWING_MODE_FREEFORM;
            boolean isStackTask = !isFreeformTask;
            boolean isLaunchTarget = taskKey.id == runningTaskId;

            ActivityInfo info = loader.getAndUpdateActivityInfo(taskKey);
            if (info == null) {
                continue;
            }

            // Load the title, icon, and color
            String title = opts.loadTitles
                    ? loader.getAndUpdateActivityTitle(taskKey, t.taskDescription)
                    : "";
            String titleDescription = opts.loadTitles
                    ? loader.getAndUpdateContentDescription(taskKey, t.taskDescription)
                    : "";
            Drawable icon = isStackTask
                    ? loader.getAndUpdateActivityIcon(taskKey, t.taskDescription, false)
                    : null;
            ThumbnailData thumbnail = loader.getAndUpdateThumbnail(taskKey,
                    false /* loadIfNotCached */, false /* storeInCache */);
            int activityColor = loader.getActivityPrimaryColor(t.taskDescription);
            int backgroundColor = loader.getActivityBackgroundColor(t.taskDescription);
            boolean isSystemApp = (info != null) &&
                    ((info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);

            // TODO: Refactor to not do this every preload
            if (mTmpLockedUsers.indexOfKey(t.userId) < 0) {
                mTmpLockedUsers.put(t.userId, mKeyguardManager.isDeviceLocked(t.userId));
            }
            boolean isLocked = mTmpLockedUsers.get(t.userId);

            // Add the task to the stack
            Task task = new Task(taskKey, icon,
                    thumbnail, title, titleDescription, activityColor, backgroundColor,
                    isLaunchTarget, isStackTask, isSystemApp, t.supportsSplitScreenMultiWindow,
                    t.taskDescription, t.resizeMode, t.topActivity, isLocked);

            allTasks.add(task);
        }

        // Initialize the stacks
        mStack = new TaskStack();
        mStack.setTasks(allTasks, false /* notifyStackChanges */);
    }

    /**
     * Called to apply the actual loading based on the specified conditions.
     *
     * Note: Do not lock, since this can be calling back to the loader, which separately also drives
     * this call (callers should synchronize on the loader before making this call).
     */
    public void executePlan(Options opts, RecentsTaskLoader loader) {
        Resources res = mContext.getResources();

        // Iterate through each of the tasks and load them according to the load conditions.
        ArrayList<Task> tasks = mStack.getTasks();
        int taskCount = tasks.size();
        for (int i = 0; i < taskCount; i++) {
            Task task = tasks.get(i);
            TaskKey taskKey = task.key;

            boolean isRunningTask = (task.key.id == opts.runningTaskId);
            boolean isVisibleTask = i >= (taskCount - opts.numVisibleTasks);
            boolean isVisibleThumbnail = i >= (taskCount - opts.numVisibleTaskThumbnails);

            // If requested, skip the running task
            if (opts.onlyLoadPausedActivities && isRunningTask) {
                continue;
            }

            if (opts.loadIcons && (isRunningTask || isVisibleTask)) {
                if (task.icon == null) {
                    task.icon = loader.getAndUpdateActivityIcon(taskKey, task.taskDescription,
                            true);
                }
            }
            if (opts.loadThumbnails && isVisibleThumbnail) {
                task.thumbnail = loader.getAndUpdateThumbnail(taskKey,
                        true /* loadIfNotCached */, true /* storeInCache */);
            }
        }
    }

    /**
     * Returns the TaskStack from the preloaded list of recent tasks.
     */
    public TaskStack getTaskStack() {
        return mStack;
    }

    /** Returns whether there are any tasks in any stacks. */
    public boolean hasTasks() {
        if (mStack != null) {
            return mStack.getTaskCount() > 0;
        }
        return false;
    }
}
