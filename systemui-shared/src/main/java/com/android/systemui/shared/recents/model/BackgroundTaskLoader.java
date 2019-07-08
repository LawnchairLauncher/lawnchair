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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.android.systemui.shared.system.ActivityManagerWrapper;

/**
 * Background task resource loader
 */
class BackgroundTaskLoader implements Runnable {
    static String TAG = "BackgroundTaskLoader";
    static boolean DEBUG = false;

    private Context mContext;
    private final HandlerThread mLoadThread;
    private final Handler mLoadThreadHandler;
    private final Handler mMainThreadHandler;

    private final TaskResourceLoadQueue mLoadQueue;
    private final IconLoader mIconLoader;

    private boolean mStarted;
    private boolean mCancelled;
    private boolean mWaitingOnLoadQueue;

    private final OnIdleChangedListener mOnIdleChangedListener;

    /** Constructor, creates a new loading thread that loads task resources in the background */
    public BackgroundTaskLoader(TaskResourceLoadQueue loadQueue,
            IconLoader iconLoader, OnIdleChangedListener onIdleChangedListener) {
        mLoadQueue = loadQueue;
        mIconLoader = iconLoader;
        mMainThreadHandler = new Handler();
        mOnIdleChangedListener = onIdleChangedListener;
        mLoadThread = new HandlerThread("Recents-TaskResourceLoader",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        mLoadThread.start();
        mLoadThreadHandler = new Handler(mLoadThread.getLooper());
    }

    /** Restarts the loader thread */
    void start(Context context) {
        mContext = context;
        mCancelled = false;
        if (!mStarted) {
            // Start loading on the load thread
            mStarted = true;
            mLoadThreadHandler.post(this);
        } else {
            // Notify the load thread to start loading again
            synchronized (mLoadThread) {
                mLoadThread.notifyAll();
            }
        }
    }

    /** Requests the loader thread to stop after the current iteration */
    void stop() {
        // Mark as cancelled for the thread to pick up
        mCancelled = true;
        // If we are waiting for the load queue for more tasks, then we can just reset the
        // Context now, since nothing is using it
        if (mWaitingOnLoadQueue) {
            mContext = null;
        }
    }

    @Override
    public void run() {
        while (true) {
            if (mCancelled) {
                // We have to unset the context here, since the background thread may be using it
                // when we call stop()
                mContext = null;
                // If we are cancelled, then wait until we are started again
                synchronized(mLoadThread) {
                    try {
                        mLoadThread.wait();
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                    }
                }
            } else {
                // If we've stopped the loader, then fall through to the above logic to wait on
                // the load thread
                processLoadQueueItem();

                // If there are no other items in the list, then just wait until something is added
                if (!mCancelled && mLoadQueue.isEmpty()) {
                    synchronized(mLoadQueue) {
                        try {
                            mWaitingOnLoadQueue = true;
                            mMainThreadHandler.post(
                                    () -> mOnIdleChangedListener.onIdleChanged(true));
                            mLoadQueue.wait();
                            mMainThreadHandler.post(
                                    () -> mOnIdleChangedListener.onIdleChanged(false));
                            mWaitingOnLoadQueue = false;
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    /**
     * This needs to be in a separate method to work around an surprising interpreter behavior:
     * The register will keep the local reference to cachedThumbnailData even if it falls out of
     * scope. Putting it into a method fixes this issue.
     */
    private void processLoadQueueItem() {
        // Load the next item from the queue
        final Task t = mLoadQueue.nextTask();
        if (t != null) {
            final Drawable icon = mIconLoader.getIcon(t);
            if (DEBUG) Log.d(TAG, "Loading thumbnail: " + t.key);
            final ThumbnailData thumbnailData =
                    ActivityManagerWrapper.getInstance().getTaskThumbnail(t.key.id,
                            true /* reducedResolution */);

            if (!mCancelled) {
                // Notify that the task data has changed
                mMainThreadHandler.post(
                        () -> t.notifyTaskDataLoaded(thumbnailData, icon));
            }
        }
    }

    interface OnIdleChangedListener {
        void onIdleChanged(boolean idle);
    }
}
