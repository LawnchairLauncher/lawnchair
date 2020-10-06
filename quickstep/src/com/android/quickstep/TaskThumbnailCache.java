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

import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.icons.cache.HandlerRunnable;
import com.android.launcher3.util.Preconditions;
import com.android.quickstep.util.TaskKeyLruCache;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.Task.TaskKey;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;

import java.util.ArrayList;
import java.util.function.Consumer;

public class TaskThumbnailCache {

    private final Handler mBackgroundHandler;

    private final int mCacheSize;
    private final TaskKeyLruCache<ThumbnailData> mCache;
    private final HighResLoadingState mHighResLoadingState;
    private final boolean mEnableTaskSnapshotPreloading;

    public static class HighResLoadingState {
        private boolean mForceHighResThumbnails;
        private boolean mVisible;
        private boolean mFlingingFast;
        private boolean mHighResLoadingEnabled;
        private ArrayList<HighResLoadingStateChangedCallback> mCallbacks = new ArrayList<>();

        public interface HighResLoadingStateChangedCallback {
            void onHighResLoadingStateChanged(boolean enabled);
        }

        private HighResLoadingState(Context context) {
            // If the device does not support low-res thumbnails, only attempt to load high-res
            // thumbnails
            mForceHighResThumbnails = !supportsLowResThumbnails();
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
            mHighResLoadingEnabled = mForceHighResThumbnails || (mVisible && !mFlingingFast);
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
        mEnableTaskSnapshotPreloading = res.getBoolean(R.bool.config_enableTaskSnapshotPreloading);
        mCache = new TaskKeyLruCache<>(mCacheSize);
    }

    /**
     * Synchronously fetches the thumbnail for the given {@param task} and puts it in the cache.
     */
    public void updateThumbnailInCache(Task task) {
        Preconditions.assertUIThread();
        // Fetch the thumbnail for this task and put it in the cache
        if (task.thumbnail == null) {
            updateThumbnailInBackground(task.key, true /* lowResolution */,
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

        boolean lowResolution = !mHighResLoadingState.isEnabled();
        if (task.thumbnail != null && (!task.thumbnail.reducedResolution || lowResolution)) {
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

    private ThumbnailLoadRequest updateThumbnailInBackground(TaskKey key, boolean lowResolution,
            Consumer<ThumbnailData> callback) {
        Preconditions.assertUIThread();

        ThumbnailData cachedThumbnail = mCache.getAndInvalidateIfModified(key);
        if (cachedThumbnail != null && (!cachedThumbnail.reducedResolution || lowResolution)) {
            // Already cached, lets use that thumbnail
            callback.accept(cachedThumbnail);
            return null;
        }

        ThumbnailLoadRequest request = new ThumbnailLoadRequest(mBackgroundHandler,
                lowResolution) {
            @Override
            public void run() {
                ThumbnailData thumbnail = ActivityManagerWrapper.getInstance().getTaskThumbnail(
                        key.id, lowResolution);

                MAIN_EXECUTOR.execute(() -> {
                    if (isCanceled()) {
                        // We don't call back to the provided callback in this case
                        return;
                    }

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
        return mEnableTaskSnapshotPreloading && mHighResLoadingState.mVisible;
    }

    public static abstract class ThumbnailLoadRequest extends HandlerRunnable {
        public final boolean mLowResolution;

        ThumbnailLoadRequest(Handler handler, boolean lowResolution) {
            super(handler, null);
            mLowResolution = lowResolution;
        }
    }

    /**
     * @return Whether device supports low-res thumbnails. Low-res files are an optimization
     * for faster load times of snapshots. Devices can optionally disable low-res files so that
     * they only store snapshots at high-res scale. The actual scale can be configured in
     * frameworks/base config overlay.
     */
    private static boolean supportsLowResThumbnails() {
        Resources res = Resources.getSystem();
        int resId = res.getIdentifier("config_lowResTaskSnapshotScale", "dimen", "android");
        if (resId != 0) {
            return 0 < res.getFloat(resId);
        }
        return true;
    }

}
