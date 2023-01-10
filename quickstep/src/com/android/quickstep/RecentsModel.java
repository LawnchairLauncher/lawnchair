/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep;

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.quickstep.TaskUtils.checkCurrentOrManagedUserId;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.icons.IconProvider;
import com.android.launcher3.icons.IconProvider.IconChangeListener;
import com.android.launcher3.util.Executors.SimpleThreadFactory;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.TaskVisualsChangeListener;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Singleton class to load and manage recents model.
 */
@TargetApi(Build.VERSION_CODES.O)
public class RecentsModel implements IconChangeListener, TaskStackChangeListener,
        TaskVisualsChangeListener {

    // We do not need any synchronization for this variable as its only written on UI thread.
    public static final MainThreadInitializedObject<RecentsModel> INSTANCE =
            new MainThreadInitializedObject<>(RecentsModel::new);

    private static final Executor RECENTS_MODEL_EXECUTOR = Executors.newSingleThreadExecutor(
            new SimpleThreadFactory("TaskThumbnailIconCache-", THREAD_PRIORITY_BACKGROUND));

    private final List<TaskVisualsChangeListener> mThumbnailChangeListeners = new ArrayList<>();
    private final Context mContext;

    private final RecentTasksList mTaskList;
    private final TaskIconCache mIconCache;
    private final TaskThumbnailCache mThumbnailCache;

    private RecentsModel(Context context) {
        mContext = context;
        mTaskList = new RecentTasksList(MAIN_EXECUTOR,
                context.getSystemService(KeyguardManager.class),
                SystemUiProxy.INSTANCE.get(context));

        IconProvider iconProvider = new IconProvider(context);
        mIconCache = new TaskIconCache(context, RECENTS_MODEL_EXECUTOR, iconProvider);
        mIconCache.registerTaskVisualsChangeListener(this);
        mThumbnailCache = new TaskThumbnailCache(context, RECENTS_MODEL_EXECUTOR);

        TaskStackChangeListeners.getInstance().registerTaskStackListener(this);
        iconProvider.registerIconChangeListener(this, MAIN_EXECUTOR.getHandler());
    }

    public TaskIconCache getIconCache() {
        return mIconCache;
    }

    public TaskThumbnailCache getThumbnailCache() {
        return mThumbnailCache;
    }

    /**
     * Fetches the list of recent tasks. Tasks are ordered by recency, with the latest active tasks
     * at the end of the list.
     *
     * @param callback The callback to receive the task plan once its complete or null. This is
     *                always called on the UI thread.
     * @return the request id associated with this call.
     */
    public int getTasks(Consumer<ArrayList<GroupTask>> callback) {
        return mTaskList.getTasks(false /* loadKeysOnly */, callback,
                RecentsFilterState.DEFAULT_FILTER);
    }


    /**
     * Fetches the list of recent tasks, based on a filter
     *
     * @param callback The callback to receive the task plan once its complete or null. This is
     *                always called on the UI thread.
     * @param filter  Returns true if a GroupTask should be included into the list passed into
     *                callback.
     * @return the request id associated with this call.
     */
    public int getTasks(Consumer<ArrayList<GroupTask>> callback, Predicate<GroupTask> filter) {
        return mTaskList.getTasks(false /* loadKeysOnly */, callback, filter);
    }

    /**
     * @return Whether the provided {@param changeId} is the latest recent tasks list id.
     */
    public boolean isTaskListValid(int changeId) {
        return mTaskList.isTaskListValid(changeId);
    }

    /**
     * @return Whether the task list is currently updating in the background
     */
    @VisibleForTesting
    public boolean isLoadingTasksInBackground() {
        return mTaskList.isLoadingTasksInBackground();
    }

    /**
     * Checks if a task has been removed or not.
     *
     * @param callback Receives true if task is removed, false otherwise
     * @param filter Returns true if GroupTask should be in the list of considerations
     */
    public void isTaskRemoved(int taskId, Consumer<Boolean> callback, Predicate<GroupTask> filter) {
        mTaskList.getTasks(true /* loadKeysOnly */, (taskGroups) -> {
            for (GroupTask group : taskGroups) {
                if (group.containsTask(taskId)) {
                    callback.accept(false);
                    return;
                }
            }
            callback.accept(true);
        }, filter);
    }

    @Override
    public void onTaskStackChangedBackground() {
        if (!mThumbnailCache.isPreloadingEnabled()) {
            // Skip if we aren't preloading
            return;
        }

        int currentUserId = Process.myUserHandle().getIdentifier();
        if (!checkCurrentOrManagedUserId(currentUserId, mContext)) {
            // Skip if we are not the current user
            return;
        }

        // Keep the cache up to date with the latest thumbnails
        ActivityManager.RunningTaskInfo runningTask =
                ActivityManagerWrapper.getInstance().getRunningTask();
        int runningTaskId = runningTask != null ? runningTask.id : -1;
        mTaskList.getTaskKeys(mThumbnailCache.getCacheSize(), taskGroups -> {
            for (GroupTask group : taskGroups) {
                if (group.containsTask(runningTaskId)) {
                    // Skip the running task, it's not going to have an up-to-date snapshot by the
                    // time the user next enters overview
                    continue;
                }
                mThumbnailCache.updateThumbnailInCache(group.task1);
                mThumbnailCache.updateThumbnailInCache(group.task2);
            }
        });
    }

    @Override
    public boolean onTaskSnapshotChanged(int taskId, ThumbnailData snapshot) {
        mThumbnailCache.updateTaskSnapShot(taskId, snapshot);

        for (int i = mThumbnailChangeListeners.size() - 1; i >= 0; i--) {
            Task task = mThumbnailChangeListeners.get(i).onTaskThumbnailChanged(taskId, snapshot);
            if (task != null) {
                task.thumbnail = snapshot;
            }
        }
        return true;
    }

    @Override
    public void onTaskRemoved(int taskId) {
        Task.TaskKey stubKey = new Task.TaskKey(taskId, 0, new Intent(), null, 0, 0);
        mThumbnailCache.remove(stubKey);
        mIconCache.onTaskRemoved(stubKey);
    }

    public void onTrimMemory(int level) {
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            mThumbnailCache.getHighResLoadingState().setVisible(false);
        }
        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            // Clear everything once we reach a low-mem situation
            mThumbnailCache.clear();
            mIconCache.clearCache();
        }
    }

    @Override
    public void onAppIconChanged(String packageName, UserHandle user) {
        mIconCache.invalidateCacheEntries(packageName, user);
        for (int i = mThumbnailChangeListeners.size() - 1; i >= 0; i--) {
            mThumbnailChangeListeners.get(i).onTaskIconChanged(packageName, user);
        }
    }

    @Override
    public void onTaskIconChanged(int taskId) {
        for (TaskVisualsChangeListener listener : mThumbnailChangeListeners) {
            listener.onTaskIconChanged(taskId);
        }
    }

    @Override
    public void onSystemIconStateChanged(String iconState) {
        mIconCache.clearCache();
    }

    /**
     * Adds a listener for visuals changes
     */
    public void addThumbnailChangeListener(TaskVisualsChangeListener listener) {
        mThumbnailChangeListeners.add(listener);
    }

    /**
     * Removes a previously added listener
     */
    public void removeThumbnailChangeListener(TaskVisualsChangeListener listener) {
        mThumbnailChangeListeners.remove(listener);
    }

    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "RecentsModel:");
        mTaskList.dump("  ", writer);
    }

    /**
     * Registers a listener for running tasks
     */
    public void registerRunningTasksListener(RunningTasksListener listener) {
        mTaskList.registerRunningTasksListener(listener);
    }

    /**
     * Removes the previously registered running tasks listener
     */
    public void unregisterRunningTasksListener() {
        mTaskList.unregisterRunningTasksListener();
    }

    /**
     * Gets the set of running tasks.
     */
    public ArrayList<ActivityManager.RunningTaskInfo> getRunningTasks() {
        return mTaskList.getRunningTasks();
    }

    /**
     * Listener for receiving running tasks changes
     */
    public interface RunningTasksListener {
        /**
         * Called when there's a change to running tasks
         */
        void onRunningTasksChanged();
    }
}
