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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.icons.cache.HandlerRunnable;
import com.android.launcher3.util.Preconditions;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.Task.TaskKey;
import com.android.systemui.shared.recents.model.TaskKeyLruCache;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;

import java.util.ArrayList;
import java.util.function.Consumer;

public class TaskThumbnailCache {

    private final Handler mBackgroundHandler;

    private final int mCacheSize;
    private final ThumbnailCache mCache;
    private final HighResLoadingState mHighResLoadingState;

    public static class HighResLoadingState {
        private boolean mIsLowRamDevice;
        private boolean mVisible;
        private boolean mFlingingFast;
        private boolean mHighResLoadingEnabled;
        private ArrayList<HighResLoadingStateChangedCallback> mCallbacks = new ArrayList<>();

        public interface HighResLoadingStateChangedCallback {
            void onHighResLoadingStateChanged(boolean enabled);
        }

        private HighResLoadingState(Context context) {
            ActivityManager activityManager =
                    (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            mIsLowRamDevice = activityManager.isLowRamDevice();
        }

        public void addCallback(HighResLoadingStateChangedCallback callback) {
            mCallbacks.add(callback);
        }

        public void removeCallback(HighResLoadingStateChangedCallback callback) {
            mCallbacks.remove(callback);
        }

        public void setVisible(boolean visible) {
            mVisible = visible;
            updateState();
        }

        public void setFlingingFast(boolean flingingFast) {
            mFlingingFast = flingingFast;
            updateState();
        }

        public boolean isEnabled() {
            return mHighResLoadingEnabled;
        }

        private void updateState() {
            boolean prevState = mHighResLoadingEnabled;
            mHighResLoadingEnabled = !mIsLowRamDevice && mVisible && !mFlingingFast;
            if (prevState != mHighResLoadingEnabled) {
                for (int i = mCallbacks.size() - 1; i >= 0; i--) {
                    mCallbacks.get(i).onHighResLoadingStateChanged(mHighResLoadingEnabled);
                }
            }
        }
    }

    public TaskThumbnailCache(Context context, Looper backgroundLooper) {
        mBackgroundHandler = new Handler(backgroundLooper);
        mHighResLoadingState = new HighResLoadingState(context);

        Resources res = context.getResources();
        mCacheSize = res.getInteger(R.integer.recentsThumbnailCacheSize);
        mCache = new ThumbnailCache(mCacheSize);
    }

    /**
     * Synchronously fetches the thumbnail for the given {@param task} and puts it in the cache.
     */
    public void updateThumbnailInCache(Task task) {
        Preconditions.assertUIThread();
        // Fetch the thumbnail for this task and put it in the cache
        if (task.thumbnail == null) {
            updateThumbnailInBackground(task.key, true /* reducedResolution */,
                    t -> task.thumbnail = t);
        }
    }

    /**
     * Synchronously updates the thumbnail in the cache if it is already there.
     */
    public void updateTaskSnapShot(int taskId, ThumbnailData thumbnail) {
        Preconditions.assertUIThread();
        mCache.updateIfAlreadyInCache(taskId, thumbnail);
    }

    /**
     * Asynchronously fetches the icon and other task data for the given {@param task}.
     *
     * @param callback The callback to receive the task after its data has been populated.
     * @return A cancelable handle to the request
     */
    public ThumbnailLoadRequest updateThumbnailInBackground(
            Task task, Consumer<ThumbnailData> callback) {
        Preconditions.assertUIThread();

        boolean reducedResolution = !mHighResLoadingState.isEnabled();
        if (task.thumbnail != null && (!task.thumbnail.reducedResolution || reducedResolution)) {
            // Nothing to load, the thumbnail is already high-resolution or matches what the
            // request, so just callback
            callback.accept(task.thumbnail);
            return null;
        }


        return updateThumbnailInBackground(task.key, !mHighResLoadingState.isEnabled(), t -> {
            task.thumbnail = t;
            callback.accept(t);
        });
    }

    private ThumbnailLoadRequest updateThumbnailInBackground(TaskKey key, boolean reducedResolution,
            Consumer<ThumbnailData> callback) {
        Preconditions.assertUIThread();

        ThumbnailData cachedThumbnail = mCache.getAndInvalidateIfModified(key);
        if (cachedThumbnail != null && (!cachedThumbnail.reducedResolution || reducedResolution)) {
            // Already cached, lets use that thumbnail
            callback.accept(cachedThumbnail);
            return null;
        }

        ThumbnailLoadRequest request = new ThumbnailLoadRequest(mBackgroundHandler,
                reducedResolution) {
            @Override
            public void run() {
                ThumbnailData thumbnail = ActivityManagerWrapper.getInstance().getTaskThumbnail(
                        key.id, reducedResolution);
                if (isCanceled()) {
                    // We don't call back to the provided callback in this case
                    return;
                }
                MAIN_EXECUTOR.execute(() -> {
                    mCache.put(key, thumbnail);
                    callback.accept(thumbnail);
                    onEnd();
                });
            }
        };
        Utilities.postAsyncCallback(mBackgroundHandler, request);
        return request;
    }

    /**
     * Clears the cache.
     */
    public void clear() {
        mCache.evictAll();
    }

    /**
     * Removes the cached thumbnail for the given task.
     */
    public void remove(Task.TaskKey key) {
        mCache.remove(key);
    }

    /**
     * @return The cache size.
     */
    public int getCacheSize() {
        return mCacheSize;
    }

    /**
     * @return The mutable high-res loading state.
     */
    public HighResLoadingState getHighResLoadingState() {
        return mHighResLoadingState;
    }

    /**
     * @return Whether to enable background preloading of task thumbnails.
     */
    public boolean isPreloadingEnabled() {
        return !mHighResLoadingState.mIsLowRamDevice && mHighResLoadingState.mVisible;
    }

    public static abstract class ThumbnailLoadRequest extends HandlerRunnable {
        public final boolean reducedResolution;

        ThumbnailLoadRequest(Handler handler, boolean reducedResolution) {
            super(handler, null);
            this.reducedResolution = reducedResolution;
        }
    }

    private static class ThumbnailCache extends TaskKeyLruCache<ThumbnailData> {

        public ThumbnailCache(int cacheSize) {
            super(cacheSize);
        }

        /**
         * Updates the cache entry if it is already present in the cache
         */
        public void updateIfAlreadyInCache(int taskId, ThumbnailData thumbnailData) {
            ThumbnailData oldData = getCacheEntry(taskId);
            if (oldData != null) {
                putCacheEntry(taskId, thumbnailData);
            }
        }
    }
}
