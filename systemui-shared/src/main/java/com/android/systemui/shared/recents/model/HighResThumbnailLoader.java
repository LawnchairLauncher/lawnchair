/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.shared.recents.model;

import static android.os.Process.setThreadPriority;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.ArraySet;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.shared.recents.model.Task.TaskCallbacks;
import com.android.systemui.shared.system.ActivityManagerWrapper;

import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * Loader class that loads full-resolution thumbnails when appropriate.
 */
public class HighResThumbnailLoader implements TaskCallbacks {

    private final ActivityManagerWrapper mActivityManager;

    @GuardedBy("mLoadQueue")
    private final ArrayDeque<Task> mLoadQueue = new ArrayDeque<>();
    @GuardedBy("mLoadQueue")
    private final ArraySet<Task> mLoadingTasks = new ArraySet<>();
    @GuardedBy("mLoadQueue")
    private boolean mLoaderIdling;

    private final ArrayList<Task> mVisibleTasks = new ArrayList<>();

    private final Thread mLoadThread;
    private final Handler mMainThreadHandler;
    private final boolean mIsLowRamDevice;
    private boolean mLoading;
    private boolean mVisible;
    private boolean mFlingingFast;
    private boolean mTaskLoadQueueIdle;

    public HighResThumbnailLoader(ActivityManagerWrapper activityManager, Looper looper,
            boolean isLowRamDevice) {
        mActivityManager = activityManager;
        mMainThreadHandler = new Handler(looper);
        mLoadThread = new Thread(mLoader, "Recents-HighResThumbnailLoader");
        mLoadThread.start();
        mIsLowRamDevice = isLowRamDevice;
    }

    public void setVisible(boolean visible) {
        if (mIsLowRamDevice) {
            return;
        }
        mVisible = visible;
        updateLoading();
    }

    public void setFlingingFast(boolean flingingFast) {
        if (mFlingingFast == flingingFast || mIsLowRamDevice) {
            return;
        }
        mFlingingFast = flingingFast;
        updateLoading();
    }

    /**
     * Sets whether the other task load queue is idling. Avoid double-loading bitmaps by not
     * starting this queue until the other queue is idling.
     */
    public void setTaskLoadQueueIdle(boolean idle) {
        if (mIsLowRamDevice) {
            return;
        }
        mTaskLoadQueueIdle = idle;
        updateLoading();
    }

    @VisibleForTesting
    boolean isLoading() {
        return mLoading;
    }

    private void updateLoading() {
        setLoading(mVisible && !mFlingingFast && mTaskLoadQueueIdle);
    }

    private void setLoading(boolean loading) {
        if (loading == mLoading) {
            return;
        }
        synchronized (mLoadQueue) {
            mLoading = loading;
            if (!loading) {
                stopLoading();
            } else {
                startLoading();
            }
        }
    }

    @GuardedBy("mLoadQueue")
    private void startLoading() {
        for (int i = mVisibleTasks.size() - 1; i >= 0; i--) {
            Task t = mVisibleTasks.get(i);
            if ((t.thumbnail == null || t.thumbnail.reducedResolution)
                    && !mLoadQueue.contains(t) && !mLoadingTasks.contains(t)) {
                mLoadQueue.add(t);
            }
        }
        mLoadQueue.notifyAll();
    }

    @GuardedBy("mLoadQueue")
    private void stopLoading() {
        mLoadQueue.clear();
        mLoadQueue.notifyAll();
    }

    /**
     * Needs to be called when a task becomes visible. Note that this is different from
     * {@link TaskCallbacks#onTaskDataLoaded} as this method should only be called once when it
     * becomes visible, whereas onTaskDataLoaded can be called multiple times whenever some data
     * has been updated.
     */
    public void onTaskVisible(Task t) {
        t.addCallback(this);
        mVisibleTasks.add(t);
        if ((t.thumbnail == null || t.thumbnail.reducedResolution) && mLoading) {
            synchronized (mLoadQueue) {
                mLoadQueue.add(t);
                mLoadQueue.notifyAll();
            }
        }
    }

    /**
     * Needs to be called when a task becomes visible. See {@link #onTaskVisible} why this is
     * different from {@link TaskCallbacks#onTaskDataUnloaded()}
     */
    public void onTaskInvisible(Task t) {
        t.removeCallback(this);
        mVisibleTasks.remove(t);
        synchronized (mLoadQueue) {
            mLoadQueue.remove(t);
        }
    }

    @VisibleForTesting
    void waitForLoaderIdle() {
        while (true) {
            synchronized (mLoadQueue) {
                if (mLoadQueue.isEmpty() && mLoaderIdling) {
                    return;
                }
            }
            SystemClock.sleep(100);
        }
    }

    @Override
    public void onTaskDataLoaded(Task task, ThumbnailData thumbnailData) {
        if (thumbnailData != null && !thumbnailData.reducedResolution) {
            synchronized (mLoadQueue) {
                mLoadQueue.remove(task);
            }
        }
    }

    @Override
    public void onTaskDataUnloaded() {
    }

    @Override
    public void onTaskWindowingModeChanged() {
    }

    private final Runnable mLoader = new Runnable() {

        @Override
        public void run() {
            setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND + 1);
            while (true) {
                Task next = null;
                synchronized (mLoadQueue) {
                    if (!mLoading || mLoadQueue.isEmpty()) {
                        try {
                            mLoaderIdling = true;
                            mLoadQueue.wait();
                            mLoaderIdling = false;
                        } catch (InterruptedException e) {
                            // Don't care.
                        }
                    } else {
                        next = mLoadQueue.poll();
                        if (next != null) {
                            mLoadingTasks.add(next);
                        }
                    }
                }
                if (next != null) {
                    loadTask(next);
                }
            }
        }

        private void loadTask(Task t) {
            ThumbnailData thumbnail = mActivityManager.getTaskThumbnail(t.key.id,
                    false /* reducedResolution */);
            mMainThreadHandler.post(() -> {
                synchronized (mLoadQueue) {
                    mLoadingTasks.remove(t);
                }
                if (mVisibleTasks.contains(t)) {
                    t.notifyTaskDataLoaded(thumbnail, t.icon);
                }
            });
        }
    };
}
