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

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.util.SparseArray
import androidx.annotation.WorkerThread
import androidx.core.graphics.drawable.toDrawable
import com.android.launcher3.Flags.enableRefactorTaskThumbnail
import com.android.launcher3.Flags.enableTaskbarRecentsThemedIcons
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.icons.BaseIconFactory
import com.android.launcher3.icons.BaseIconFactory.IconOptions
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.IconProvider
import com.android.launcher3.icons.LauncherIcons
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.CancellableTask
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.DisplayController.DisplayInfoChangeListener
import com.android.launcher3.util.Executors
import com.android.launcher3.util.FlagOp
import com.android.launcher3.util.OverviewReleaseFlags.enableOverviewIconMenu
import com.android.launcher3.util.Preconditions
import com.android.launcher3.util.coroutines.DispatcherProvider
import com.android.quickstep.task.thumbnail.data.TaskIconDataSource
import com.android.quickstep.util.IconLabelUtil.getBadgedContentDescription
import com.android.quickstep.util.TaskKeyLruCache
import com.android.quickstep.util.TaskVisualsChangeListener
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.android.systemui.shared.system.PackageManagerWrapper
import java.util.concurrent.Executor
import kotlinx.coroutines.withContext

/** Manages the caching of task icons and related data. */
class TaskIconCache(
    private val context: Context,
    private val bgExecutor: Executor,
    private val iconProvider: IconProvider,
    displayController: DisplayController,
    val dispatcherProvider: DispatcherProvider,
) : TaskIconDataSource, DisplayInfoChangeListener {
    private val recentsIconCacheSize = context.resources.getInteger(R.integer.recentsIconCacheSize)
    private var iconCache: TaskKeyLruCache<TaskCacheEntry>? = null
    // TODO: b/431811298 - Make non-null when flag is cleaned up.
    private var bitmapInfoCache: TaskKeyLruCache<TaskBitmapInfoCacheEntry>? = null

    private val defaultIcons = SparseArray<BitmapInfo>()
    private var defaultIconBase: BitmapInfo? = null

    private var _iconFactory: BaseIconFactory? = null
    @get:WorkerThread
    private val iconFactory: BaseIconFactory
        get() =
            if (enableTaskbarRecentsThemedIcons()) LauncherIcons.obtain(context)
            else if (enableRefactorTaskThumbnail()) createIconFactory()
            else _iconFactory ?: createIconFactory().also { _iconFactory = it }

    var taskVisualsChangeListener: TaskVisualsChangeListener? = null

    init {
        if (enableTaskbarRecentsThemedIcons()) {
            bitmapInfoCache = TaskKeyLruCache(recentsIconCacheSize)
        } else {
            iconCache = TaskKeyLruCache(recentsIconCacheSize)
        }
        // TODO (b/397205964): this will need to be updated when we support caches for different
        //  displays.
        displayController.addChangeListener(this)
    }

    override fun onDisplayInfoChanged(context: Context, info: DisplayController.Info, flags: Int) {
        if ((flags and DisplayController.CHANGE_DENSITY) != 0) {
            clearCache()
        }
    }

    // TODO(b/387496731): Add ensureActive() calls if they show performance benefit
    override suspend fun getIcon(task: Task): TaskCacheEntry {
        task.icon?.let { icon ->
            // Nothing to load, the icon is already loaded
            return TaskCacheEntry(icon, task.titleDescription ?: "", task.title ?: "")
        }

        // Return from cache if present
        bitmapInfoCache?.getAndInvalidateIfModified(task.key)?.let {
            return it.toTaskCacheEntry(context)
        }
        iconCache?.getAndInvalidateIfModified(task.key)?.let {
            return it
        }

        return withContext(dispatcherProvider.ioBackground) {
            val entry =
                if (enableTaskbarRecentsThemedIcons()) {
                    getBitmapInfoCacheEntry(task)
                        .apply {
                            task.icon = bitmapInfo.newIcon(context)
                            task.titleDescription = contentDescription
                            task.title = title
                        }
                        .toTaskCacheEntry(context)
                } else {
                    getCacheEntry(task).apply {
                        task.icon = icon
                        task.titleDescription = contentDescription
                        task.title = title
                    }
                }
            dispatchIconUpdate(task.key.id)
            return@withContext entry
        }
    }

    /**
     * Asynchronously fetches the icon and other [task] data, returned through [callback].
     *
     * Returns a [CancellableTask] to cancel the request.
     */
    fun getIconInBackground(task: Task, callback: GetTaskIconCallback): CancellableTask<*>? {
        Preconditions.assertUIThread()
        task.icon?.let {
            // Nothing to load, the icon is already loaded
            callback.onTaskIconReceived(it, task.titleDescription ?: "", task.title ?: "")
            return null
        }

        if (enableTaskbarRecentsThemedIcons()) {
            return getBitmapInfoInBackground(task) { bitmapInfo, title, contentDescription ->
                val icon = bitmapInfo.newIcon(context)
                task.icon = icon
                callback.onTaskIconReceived(icon, title, contentDescription)
            }
        }

        iconCache?.getAndInvalidateIfModified(task.key)?.let {
            task.icon = it.icon
            task.titleDescription = it.contentDescription
            task.title = it.title

            callback.onTaskIconReceived(it.icon, it.contentDescription, it.title)
            return null
        }
        val request =
            CancellableTask(
                { getCacheEntry(task) },
                Executors.MAIN_EXECUTOR,
                { result: TaskCacheEntry ->
                    task.icon = result.icon
                    task.titleDescription = result.contentDescription
                    task.title = result.title

                    callback.onTaskIconReceived(
                        result.icon,
                        result.contentDescription,
                        result.title,
                    )
                    dispatchIconUpdate(task.key.id)
                },
            )
        bgExecutor.execute(request)
        return request
    }

    /**
     * Asynchronously fetches the icon [BitmapInfo] and other [task] data, returned through
     * [callback].
     *
     * Returns a [CancellableTask] to cancel the request.
     */
    fun getBitmapInfoInBackground(
        task: Task,
        callback: GetTaskBitmapInfoCallback,
    ): CancellableTask<*>? {
        Preconditions.assertUIThread()

        bitmapInfoCache?.getAndInvalidateIfModified(task.key)?.let {
            task.titleDescription = it.contentDescription
            task.title = it.title
            callback.onBitmapInfoReceived(it.bitmapInfo, it.contentDescription, it.title)
            return null
        }

        val request =
            CancellableTask(
                { getBitmapInfoCacheEntry(task) },
                Executors.MAIN_EXECUTOR,
                { result: TaskBitmapInfoCacheEntry ->
                    task.titleDescription = result.contentDescription
                    task.title = result.title
                    callback.onBitmapInfoReceived(
                        result.bitmapInfo,
                        result.contentDescription,
                        result.title,
                    )
                    dispatchIconUpdate(task.key.id)
                },
            )
        bgExecutor.execute(request)
        return request
    }

    /** Clears the icon cache */
    fun clearCache() {
        // Clear on caller and background thread. The cache clears are synchronized.
        resetFactory()
        bgExecutor.execute { resetFactory() }
    }

    fun onTaskRemoved(taskKey: TaskKey) {
        bitmapInfoCache?.remove(taskKey)
        iconCache?.remove(taskKey)
    }

    fun invalidateCacheEntries(pkg: String, handle: UserHandle) {
        bgExecutor.execute {
            val keyCheck = { key: TaskKey ->
                pkg == key.packageName && handle.identifier == key.userId
            }
            bitmapInfoCache?.removeAll(keyCheck)
            iconCache?.removeAll(keyCheck)
        }
    }

    @WorkerThread
    private fun createIconFactory() =
        BaseIconFactory(
            context,
            DisplayController.INSTANCE.get(context).info.densityDpi,
            context.resources.getDimensionPixelSize(R.dimen.task_icon_cache_default_icon_size),
        )

    @WorkerThread
    private fun getCacheEntry(task: Task): TaskCacheEntry {
        val key = task.key
        val activityInfo =
            PackageManagerWrapper.getInstance().getActivityInfo(key.component, key.userId)
        val entryIcon = getBitmapInfo(task).newIcon(context)

        return when {
            // Skip loading the content description if the activity no longer exists
            activityInfo == null -> TaskCacheEntry(entryIcon)
            enableOverviewIconMenu() ->
                TaskCacheEntry(
                    entryIcon,
                    getBadgedContentDescription(
                        context,
                        activityInfo,
                        task.key.userId,
                        task.taskDescription,
                    ),
                    Utilities.trim(activityInfo.loadLabel(context.packageManager)),
                )
            else ->
                TaskCacheEntry(
                    entryIcon,
                    getBadgedContentDescription(
                        context,
                        activityInfo,
                        task.key.userId,
                        task.taskDescription,
                    ),
                )
        }.also { iconCache?.put(task.key, it) }
    }

    @WorkerThread
    private fun getBitmapInfoCacheEntry(task: Task): TaskBitmapInfoCacheEntry {
        val key = task.key
        val activityInfo =
            PackageManagerWrapper.getInstance().getActivityInfo(key.component, key.userId)
        val bitmapInfo = getBitmapInfo(task)

        return when {
            // Skip loading the content description if the activity no longer exists
            activityInfo == null -> TaskBitmapInfoCacheEntry(bitmapInfo)
            enableOverviewIconMenu() ->
                TaskBitmapInfoCacheEntry(
                    bitmapInfo,
                    getBadgedContentDescription(
                        context,
                        activityInfo,
                        task.key.userId,
                        task.taskDescription,
                    ),
                    Utilities.trim(activityInfo.loadLabel(context.packageManager)),
                )
            else ->
                TaskBitmapInfoCacheEntry(
                    bitmapInfo,
                    getBadgedContentDescription(
                        context,
                        activityInfo,
                        task.key.userId,
                        task.taskDescription,
                    ),
                )
        }.also { bitmapInfoCache?.put(task.key, it) }
    }

    private fun getIcon(desc: ActivityManager.TaskDescription, userId: Int): Bitmap? =
        desc.inMemoryIcon
            ?: ActivityManager.TaskDescription.loadTaskDescriptionIcon(desc.iconFilename, userId)

    @WorkerThread
    private fun getBitmapInfo(task: Task): BitmapInfo {
        val desc = task.taskDescription
        val key = task.key

        // Load icon
        val icon = getIcon(desc, key.userId)
        return if (icon != null) {
            getBitmapInfo(
                icon.toDrawable(context.resources),
                key.userId,
                desc.primaryColor,
                false, /* isInstantApp */
            )
        } else {
            val activityInfo =
                PackageManagerWrapper.getInstance().getActivityInfo(key.component, key.userId)
            if (activityInfo != null) {
                getBitmapInfo(
                    iconProvider.getIcon(activityInfo),
                    key.userId,
                    desc.primaryColor,
                    activityInfo.applicationInfo.isInstantApp,
                )
            } else {
                getDefaultBitmapInfo(key.userId)
            }
        }
    }

    @WorkerThread
    private fun getDefaultBitmapInfo(userId: Int): BitmapInfo {
        synchronized(defaultIcons) {
            val defaultIconBase =
                defaultIconBase ?: iconFactory.use { it.makeDefaultIcon(iconProvider) }
            val index: Int = defaultIcons.indexOfKey(userId)
            return if (index >= 0) {
                defaultIcons.valueAt(index)
            } else {
                val info =
                    defaultIconBase.withFlags(
                        UserCache.INSTANCE.get(context)
                            .getUserInfo(UserHandle.of(userId))
                            .applyBitmapInfoFlags(FlagOp.NO_OP)
                    )
                defaultIcons[userId] = info
                info
            }
        }
    }

    @WorkerThread
    private fun getBitmapInfo(
        drawable: Drawable,
        userId: Int,
        primaryColor: Int,
        isInstantApp: Boolean,
    ): BitmapInfo {
        iconFactory.use { iconFactory ->
            // User version code O, so that the icon is always wrapped in an adaptive icon container
            return iconFactory.createBadgedIconBitmap(
                drawable,
                IconOptions()
                    .setUser(UserCache.INSTANCE.get(context).getUserInfo(UserHandle.of(userId)))
                    .setInstantApp(isInstantApp)
                    .setExtractedColor(0)
                    .setWrapperBackgroundColor(primaryColor),
            )
        }
    }

    @WorkerThread
    private fun resetFactory() {
        _iconFactory = null
        bitmapInfoCache?.evictAll()
        iconCache?.evictAll()
    }

    data class TaskCacheEntry(
        val icon: Drawable,
        val contentDescription: String = "",
        val title: String = "",
    )

    private data class TaskBitmapInfoCacheEntry(
        val bitmapInfo: BitmapInfo,
        val contentDescription: String = "",
        val title: String = "",
    ) {
        fun toTaskCacheEntry(context: Context): TaskCacheEntry {
            return TaskCacheEntry(bitmapInfo.newIcon(context), contentDescription, title)
        }
    }

    /** Callback used when retrieving app icons from cache. */
    fun interface GetTaskIconCallback {
        /** Called when task icon is retrieved. */
        fun onTaskIconReceived(icon: Drawable, contentDescription: String, title: String)
    }

    /** Callback used when retrieving app [BitmapInfo] instances from cache. */
    fun interface GetTaskBitmapInfoCallback {
        /** Called when task [BitmapInfo] is retrieved. */
        fun onBitmapInfoReceived(bitmapInfo: BitmapInfo, contentDescription: String, title: String)
    }

    fun registerTaskVisualsChangeListener(newListener: TaskVisualsChangeListener?) {
        taskVisualsChangeListener = newListener
    }

    fun removeTaskVisualsChangeListener() {
        taskVisualsChangeListener = null
    }

    private fun dispatchIconUpdate(taskId: Int) {
        taskVisualsChangeListener?.onTaskIconChanged(taskId)
    }
}
