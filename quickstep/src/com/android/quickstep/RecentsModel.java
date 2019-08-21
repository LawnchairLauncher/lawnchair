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

import static com.android.quickstep.TaskUtils.checkCurrentOrManagedUserId;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.os.Build;
import android.os.HandlerThread;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.TaskStackChangeListener;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Singleton class to load and manage recents model.
 */
@TargetApi(Build.VERSION_CODES.O)
public class RecentsModel extends TaskStackChangeListener {

    private static final String TAG = "RecentsModel";

    // We do not need any synchronization for this variable as its only written on UI thread.
    public static final MainThreadInitializedObject<RecentsModel> INSTANCE =
            new MainThreadInitializedObject<>(RecentsModel::new);

    private final List<TaskThumbnailChangeListener> mThumbnailChangeListeners = new ArrayList<>();
    private final Context mContext;

    private ISystemUiProxy mSystemUiProxy;

    private final RecentTasksList mTaskList;
    private final TaskIconCache mIconCache;
    private final TaskThumbnailCache mThumbnailCache;

    private RecentsModel(Context context) {
        mContext = context;
        HandlerThread loaderThread = new HandlerThread("TaskThumbnailIconCache",
                Process.THREAD_PRIORITY_BACKGROUND);
        loaderThread.start();
        mTaskList = new RecentTasksList(context);
        mIconCache = new TaskIconCache(context, loaderThread.getLooper());
        mThumbnailCache = new TaskThumbnailCache(context, loaderThread.getLooper());
        ActivityManagerWrapper.getInstance().registerTaskStackListener(this);
    }

    public TaskIconCache getIconCache() {
        return mIconCache;
    }

    public TaskThumbnailCache getThumbnailCache() {
        return mThumbnailCache;
    }

    /**
     * Fetches the list of recent tasks.
     *
     * @param callback The callback to receive the task plan once its complete or null. This is
     *                always called on the UI thread.
     * @return the request id associated with this call.
     */
    public int getTasks(Consumer<ArrayList<Task>> callback) {
        return mTaskList.getTasks(false /* loadKeysOnly */, callback);
    }

    /**
     * @return The task id of the running task, or -1 if there is no current running task.
     */
    public static int getRunningTaskId() {
        ActivityManager.RunningTaskInfo runningTask =
                ActivityManagerWrapper.getInstance().getRunningTask();
        return runningTask != null ? runningTask.id : -1;
    }

    /**
     * @return Whether the provided {@param changeId} is the latest recent tasks list id.
     */
    public boolean isTaskListValid(int changeId) {
        return mTaskList.isTaskListValid(changeId);
    }

    /**
     * Finds and returns the task key associated with the given task id.
     *
     * @param callback The callback to receive the task key if it is found or null. This is always
     *                 called on the UI thread.
     */
    public void findTaskWithId(int taskId, Consumer<Task.TaskKey> callback) {
        mTaskList.getTasks(true /* loadKeysOnly */, (tasks) -> {
            for (Task task : tasks) {
                if (task.key.id == taskId) {
                    callback.accept(task.key);
                    return;
                }
            }
            callback.accept(null);
        });
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
        int runningTaskId = RecentsModel.getRunningTaskId();
        mTaskList.getTaskKeys(mThumbnailCache.getCacheSize(), tasks -> {
            for (Task task : tasks) {
                if (task.key.id == runningTaskId) {
                    // Skip the running task, it's not going to have an up-to-date snapshot by the
                    // time the user next enters overview
                    continue;
                }
                mThumbnailCache.updateThumbnailInCache(task);
            }
        });
    }

    @Override
    public void onTaskSnapshotChanged(int taskId, ThumbnailData snapshot) {
        mThumbnailCache.updateTaskSnapShot(taskId, snapshot);

        for (int i = mThumbnailChangeListeners.size() - 1; i >= 0; i--) {
            Task task = mThumbnailChangeListeners.get(i).onTaskThumbnailChanged(taskId, snapshot);
            if (task != null) {
                task.thumbnail = snapshot;
            }
        }
    }

    @Override
    public void onTaskRemoved(int taskId) {
        Task.TaskKey dummyKey = new Task.TaskKey(taskId, 0, null, null, 0, 0);
        mThumbnailCache.remove(dummyKey);
    }

    public void setSystemUiProxy(ISystemUiProxy systemUiProxy) {
        mSystemUiProxy = systemUiProxy;
    }

    public ISystemUiProxy getSystemUiProxy() {
        return mSystemUiProxy;
    }

    public void onTrimMemory(int level) {
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            mThumbnailCache.getHighResLoadingState().setVisible(false);
        }
        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            // Clear everything once we reach a low-mem situation
            mThumbnailCache.clear();
            mIconCache.clear();
        }
    }

    public void onOverviewShown(boolean fromHome, String tag) {
        if (mSystemUiProxy == null) {
            return;
        }
        try {
            mSystemUiProxy.onOverviewShown(fromHome);
        } catch (RemoteException e) {
            Log.w(tag,
                    "Failed to notify SysUI of overview shown from " + (fromHome ? "home" : "app")
                            + ": ", e);
        }
    }

    public void addThumbnailChangeListener(TaskThumbnailChangeListener listener) {
        mThumbnailChangeListeners.add(listener);
    }

    public void removeThumbnailChangeListener(TaskThumbnailChangeListener listener) {
        mThumbnailChangeListeners.remove(listener);
    }

    public interface TaskThumbnailChangeListener {

        Task onTaskThumbnailChanged(int taskId, ThumbnailData thumbnailData);
    }
}
