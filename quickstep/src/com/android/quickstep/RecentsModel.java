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
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.Preconditions;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.TaskStackChangeListener;
import java.util.ArrayList;
import java.util.function.Consumer;

import androidx.annotation.WorkerThread;

/**
 * Singleton class to load and manage recents model.
 */
@TargetApi(Build.VERSION_CODES.O)
public class RecentsModel extends TaskStackChangeListener {

    private static final String TAG = "RecentsModel";

    // We do not need any synchronization for this variable as its only written on UI thread.
    public static final MainThreadInitializedObject<RecentsModel> INSTANCE =
            new MainThreadInitializedObject<>(c -> new RecentsModel(c));

    private final SparseArray<Bundle> mCachedAssistData = new SparseArray<>(1);
    private final ArrayList<AssistDataListener> mAssistDataListeners = new ArrayList<>();

    private final Context mContext;
    private final MainThreadExecutor mMainThreadExecutor;

    private ISystemUiProxy mSystemUiProxy;
    private boolean mClearAssistCacheOnStackChange = true;

    private final RecentTasksList mTaskList;
    private final TaskIconCache mIconCache;
    private final TaskThumbnailCache mThumbnailCache;

    private float mWindowCornerRadius = -1;
    private Boolean mSupportsRoundedCornersOnWindows;

    private RecentsModel(Context context) {
        mContext = context;

        mMainThreadExecutor = new MainThreadExecutor();

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
    public void onTaskStackChanged() {
        Preconditions.assertUIThread();
        if (mClearAssistCacheOnStackChange) {
            mCachedAssistData.clear();
        } else {
            mClearAssistCacheOnStackChange = true;
        }
    }

    public void setSystemUiProxy(ISystemUiProxy systemUiProxy) {
        mSystemUiProxy = systemUiProxy;
    }

    public ISystemUiProxy getSystemUiProxy() {
        return mSystemUiProxy;
    }

    public float getWindowCornerRadius() {
        // The window corner radius is expressed in pixels and won't change if the
        // display density changes. It's safe to cache the value.
        if (mWindowCornerRadius == -1) {
            if (mSystemUiProxy != null) {
                try {
                    mWindowCornerRadius = mSystemUiProxy.getWindowCornerRadius();
                } catch (RemoteException e) {
                    Log.w(TAG, "Connection to ISystemUIProxy was lost, ignoring window corner "
                            + "radius");
                    return 0;
                }
            } else {
                Log.w(TAG, "ISystemUIProxy is null, ignoring window corner radius");
                return 0;
            }
        }
        return mWindowCornerRadius;
    }

    public boolean supportsRoundedCornersOnWindows() {
        if (mSupportsRoundedCornersOnWindows == null) {
            if (mSystemUiProxy != null) {
                try {
                    mSupportsRoundedCornersOnWindows =
                            mSystemUiProxy.supportsRoundedCornersOnWindows();
                } catch (RemoteException e) {
                    Log.w(TAG, "Connection to ISystemUIProxy was lost, ignoring window corner "
                            + "radius");
                    return false;
                }
            } else {
                Log.w(TAG, "ISystemUIProxy is null, ignoring window corner radius");
                return false;
            }
        }

        return mSupportsRoundedCornersOnWindows;
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

    public void resetAssistCache() {
        mCachedAssistData.clear();
    }

    @WorkerThread
    public void preloadAssistData(int taskId, Bundle data) {
        mMainThreadExecutor.execute(() -> {
            mCachedAssistData.put(taskId, data);
            // We expect a stack change callback after the assist data is set. So ignore the
            // very next stack change callback.
            mClearAssistCacheOnStackChange = false;

            int count = mAssistDataListeners.size();
            for (int i = 0; i < count; i++) {
                mAssistDataListeners.get(i).onAssistDataReceived(taskId);
            }
        });
    }

    public Bundle getAssistData(int taskId) {
        Preconditions.assertUIThread();
        return mCachedAssistData.get(taskId);
    }

    public void addAssistDataListener(AssistDataListener listener) {
        mAssistDataListeners.add(listener);
    }

    public void removeAssistDataListener(AssistDataListener listener) {
        mAssistDataListeners.remove(listener);
    }

    /**
     * Callback for receiving assist data
     */
    public interface AssistDataListener {

        void onAssistDataReceived(int taskId);
    }
}
