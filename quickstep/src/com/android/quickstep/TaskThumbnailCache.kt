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
package com.android.quickstep

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.android.launcher3.Flags.enableCoroutineThreadingImprovements
import com.android.launcher3.R
import com.android.launcher3.util.CancellableTask
import com.android.launcher3.util.Executors
import com.android.launcher3.util.OverviewReleaseFlags.enableGridOnlyOverview
import com.android.launcher3.util.Preconditions
import com.android.launcher3.util.coroutines.DispatcherProvider
import com.android.quickstep.recents.di.RecentsDependencies
import com.android.quickstep.recents.di.inject
import com.android.quickstep.task.thumbnail.data.TaskThumbnailDataSource
import com.android.quickstep.util.TaskKeyByLastActiveTimeCache
import com.android.quickstep.util.TaskKeyCache
import com.android.quickstep.util.TaskKeyLruCache
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.systemui.shared.system.ActivityManagerWrapper
import java.util.concurrent.Executor
import java.util.function.Consumer
import kotlinx.coroutines.withContext

class TaskThumbnailCache
@VisibleForTesting
internal constructor(
    private val context: Context,
    private val bgExecutor: Executor,
    private val cache: TaskKeyCache<ThumbnailData>,
) : TaskThumbnailDataSource {
    val highResLoadingState = HighResLoadingState()
    private val enableTaskSnapshotPreloading =
        context.resources.getBoolean(R.bool.config_enableTaskSnapshotPreloading)
    val dispatcherProvider: DispatcherProvider by RecentsDependencies.inject()

    @JvmOverloads
    constructor(
        context: Context,
        bgExecutor: Executor,
        cacheSize: Int = context.resources.getInteger(R.integer.recentsThumbnailCacheSize),
    ) : this(
        context,
        bgExecutor,
        if (enableGridOnlyOverview()) TaskKeyByLastActiveTimeCache(cacheSize)
        else TaskKeyLruCache(cacheSize),
    )

    /**
     * Synchronously fetches the thumbnail for the given task at the specified resolution level, and
     * puts it in the cache.
     */
    fun updateThumbnailInCache(task: Task?, lowResolution: Boolean) {
        task ?: return

        Preconditions.assertUIThread()
        // Fetch the thumbnail for this task and put it in the cache
        if (task.thumbnail == null) {
            getThumbnailInBackground(task.key, lowResolution) { t: ThumbnailData? ->
                task.thumbnail = t
            }
        }
    }

    /** Synchronously updates the thumbnail in the cache if it is already there. */
    fun updateTaskSnapShot(taskId: Int, thumbnail: ThumbnailData?) {
        Preconditions.assertUIThread()
        cache.updateIfAlreadyInCache(taskId, thumbnail)
    }

    // TODO(b/387496731): Add ensureActive() calls if they show performance benefit
    /**
     * Retrieves a thumbnail for the provided `task` on the current thread. This should not be
     * called from the main thread.
     */
    @WorkerThread
    override suspend fun getThumbnail(task: Task): ThumbnailData? {
        val lowResolution: Boolean = !highResLoadingState.isEnabled
        // Check task for thumbnail
        val taskThumbnail: ThumbnailData? = task.thumbnail
        if (
            taskThumbnail?.thumbnail != null && (!taskThumbnail.reducedResolution || lowResolution)
        ) {
            return taskThumbnail
        }

        // Check cache for thumbnail
        val cachedThumbnail: ThumbnailData? = cache.getAndInvalidateIfModified(task.key)
        if (
            cachedThumbnail?.thumbnail != null &&
                (!cachedThumbnail.reducedResolution || lowResolution)
        ) {
            return cachedThumbnail
        }

        return withContext(dispatcherProvider.ioBackground) {
            // Get thumbnail from system
            var thumbnailData =
                ActivityManagerWrapper.getInstance().getTaskThumbnail(task.key.id, lowResolution)
            if (thumbnailData.thumbnail == null && !enableCoroutineThreadingImprovements()) {
                thumbnailData = ActivityManagerWrapper.getInstance().takeTaskThumbnail(task.key.id)
            }

            // Avoid an async timing issue that a low res entry replaces an existing high
            // res entry in high res enabled state, so we check before putting it to cache
            if (
                enableGridOnlyOverview() &&
                    thumbnailData.reducedResolution &&
                    highResLoadingState.isEnabled
            ) {
                val newCachedThumbnail = cache.getAndInvalidateIfModified(task.key)
                if (
                    newCachedThumbnail?.thumbnail != null && !newCachedThumbnail.reducedResolution
                ) {
                    return@withContext newCachedThumbnail
                }
            }
            cache.put(task.key, thumbnailData)
            return@withContext thumbnailData
        }
    }

    /**
     * Asynchronously fetches the thumbnail for the given `task`.
     *
     * @param callback The callback to receive the task after its data has been populated.
     * @return a cancelable handle to the request
     */
    fun getThumbnailInBackground(
        task: Task,
        callback: Consumer<ThumbnailData>,
    ): CancellableTask<ThumbnailData>? {
        Preconditions.assertUIThread()

        val lowResolution = !highResLoadingState.isEnabled
        val taskThumbnail = task.thumbnail
        if (
            taskThumbnail?.thumbnail != null && (!taskThumbnail.reducedResolution || lowResolution)
        ) {
            // Nothing to load, the thumbnail is already high-resolution or matches what the
            // request, so just callback
            callback.accept(taskThumbnail)
            return null
        }

        return getThumbnailInBackground(task.key, !highResLoadingState.isEnabled, callback)
    }

    /**
     * Updates cache size and remove excess entries if current size is more than new cache size.
     *
     * @return whether cache size has increased
     */
    fun updateCacheSizeAndRemoveExcess(): Boolean {
        val newSize = context.resources.getInteger(R.integer.recentsThumbnailCacheSize)
        val oldSize = cache.maxSize
        if (newSize == oldSize) {
            // Return if no change in size
            return false
        }

        cache.updateCacheSizeAndRemoveExcess(newSize)
        return newSize > oldSize
    }

    private fun getThumbnailInBackground(
        key: TaskKey,
        lowResolution: Boolean,
        callback: Consumer<ThumbnailData>,
    ): CancellableTask<ThumbnailData>? {
        Preconditions.assertUIThread()

        val cachedThumbnail = cache.getAndInvalidateIfModified(key)
        if (
            cachedThumbnail?.thumbnail != null &&
                (!cachedThumbnail.reducedResolution || lowResolution)
        ) {
            // Already cached, lets use that thumbnail
            callback.accept(cachedThumbnail)
            return null
        }

        val request =
            CancellableTask(
                {
                    val thumbnailData =
                        ActivityManagerWrapper.getInstance().getTaskThumbnail(key.id, lowResolution)
                    if (thumbnailData.thumbnail != null) thumbnailData
                    else ActivityManagerWrapper.getInstance().takeTaskThumbnail(key.id)
                },
                Executors.MAIN_EXECUTOR,
                Consumer { result: ThumbnailData ->
                    // Avoid an async timing issue that a low res entry replaces an existing high
                    // res entry in high res enabled state, so we check before putting it to cache
                    if (
                        enableGridOnlyOverview() &&
                            result.reducedResolution &&
                            highResLoadingState.isEnabled
                    ) {
                        val newCachedThumbnail = cache.getAndInvalidateIfModified(key)
                        if (
                            newCachedThumbnail?.thumbnail != null &&
                                !newCachedThumbnail.reducedResolution
                        ) {
                            return@Consumer
                        }
                    }
                    cache.put(key, result)
                    callback.accept(result)
                },
            )
        bgExecutor.execute(request)
        return request
    }

    /** Clears the cache. */
    fun clear() {
        cache.evictAll()
    }

    /** Removes the cached thumbnail for the given task. */
    fun remove(key: TaskKey) {
        cache.remove(key)
    }

    /** Returns The cache size. */
    fun getCacheSize() = cache.maxSize

    /** Returns Whether to enable background preloading of task thumbnails. */
    fun isPreloadingEnabled() = enableTaskSnapshotPreloading && highResLoadingState.visible
}
