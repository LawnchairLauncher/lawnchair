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
package com.android.wm.shell.windowdecor.common

import android.annotation.DimenRes
import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.Bitmap
import android.os.Handler
import android.os.LocaleList
import android.os.UserHandle
import android.util.Slog
//import androidx.tracing.Trace
import com.android.internal.annotations.VisibleForTesting
import com.android.launcher3.icons.BaseIconFactory
import com.android.launcher3.icons.BaseIconFactory.Companion.MODE_DEFAULT
import com.android.launcher3.icons.IconProvider
import com.android.wm.shell.R
import com.android.wm.shell.common.UserProfileContexts
import com.android.wm.shell.shared.annotations.ShellBackgroundThread
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.sysui.UserChangeListener
import java.io.PrintWriter
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A utility and cache for window decoration UI resources. */
@ShellMainThread
class WindowDecorTaskResourceLoaderImpl(
    shellInit: ShellInit,
    private val shellController: ShellController,
    @ShellMainThread private val mainHandler: Handler,
    @ShellMainThread private val mainScope: CoroutineScope,
    @ShellMainThread private val mainDispatcher: CoroutineDispatcher,
    @ShellBackgroundThread private val bgDispatcher: CoroutineDispatcher,
    private val shellCommandHandler: ShellCommandHandler,
    private val userProfilesContexts: UserProfileContexts,
    private val iconProvider: IconProvider,
    private val headerIconFactory: BaseIconFactory,
    private val veilIconFactory: BaseIconFactory,
) : WindowDecorTaskResourceLoader {
    constructor(
        context: Context,
        shellInit: ShellInit,
        shellController: ShellController,
        mainHandler: Handler,
        mainScope: CoroutineScope,
        mainDispatcher: CoroutineDispatcher,
        bgDispatcher: CoroutineDispatcher,
        shellCommandHandler: ShellCommandHandler,
        userProfileContexts: UserProfileContexts,
    ) : this(
        shellInit,
        shellController,
        mainHandler,
        mainScope,
        mainDispatcher,
        bgDispatcher,
        shellCommandHandler,
        userProfileContexts,
        IconProvider(context),
        headerIconFactory = context.createIconFactory(R.dimen.desktop_mode_caption_icon_radius),
        veilIconFactory = context.createIconFactory(R.dimen.desktop_mode_resize_veil_icon_size),
    )

    /**
     * A map of task -> resources to prevent unnecessary binder calls and resource loading when
     * multiple window decorations need the same resources, for example, the app name or icon used
     * in the header and menu.
     */
    @VisibleForTesting val taskToResourceCache = HashMap<Int, AppResources>()
    /**
     * Keeps track of existing tasks with a window decoration. Useful to verify that requests to get
     * resources occur within the lifecycle of a window decoration, otherwise it'd be possible to
     * load a tasks resources into memory without a future signal to clean up the resource. See
     * [onWindowDecorClosed].
     */
    private val existingTasks = mutableSetOf<Int>()

    /**
     * A map of task -> localeList to keep track of the language of app name that's currently cached
     * in |taskToResourceCache|.
     */
    @VisibleForTesting val localeListOnCache = HashMap<Int, LocaleList>()

    init {
        shellInit.addInitCallback(this::onInit, this)
    }

    private fun onInit() {
        shellCommandHandler.addDumpCallback(this::dump, this)
        shellController.addUserChangeListener(
            object : UserChangeListener {
                override fun onUserChanged(newUserId: Int, userContext: Context) {
                    // No need to hold on to resources for tasks of another profile.
                    taskToResourceCache.clear()
                }
            }
        )
    }

    override suspend fun getNameAndHeaderIcon(
        taskInfo: RunningTaskInfo
    ): Pair<CharSequence, Bitmap> =
        withContext(mainDispatcher) {
            suspendCoroutine { cont ->
                getNameAndHeaderIcon(taskInfo) { name, headerIcon ->
                    cont.resumeWith(Result.success(Pair(name, headerIcon)))
                }
            }
        }

    override fun getNameAndHeaderIcon(
        taskInfo: RunningTaskInfo,
        callback: (CharSequence, Bitmap) -> Unit,
    ) {
        assertMainThread()
        checkWindowDecorExists(taskInfo)
        val cachedResources = taskToResourceCache[taskInfo.taskId]
        val localeListActiveOnCacheTime = localeListOnCache[taskInfo.taskId]
        if (
            cachedResources != null &&
                taskInfo.getConfiguration().getLocales().equals(localeListActiveOnCacheTime)
        ) {
            callback(cachedResources.appName, cachedResources.appIcon)
            return
        }

        mainScope.launch {
            val resources = loadAppResources(taskInfo)
            if (resources.shouldCacheResult) {
                taskToResourceCache[taskInfo.taskId] = resources
            }
            localeListOnCache[taskInfo.taskId] = taskInfo.getConfiguration().getLocales()
            callback(resources.appName, resources.appIcon)
        }
    }

    override suspend fun getVeilIcon(taskInfo: RunningTaskInfo): Bitmap =
        withContext(mainDispatcher) {
            checkWindowDecorExists(taskInfo)
            val cachedResources = taskToResourceCache[taskInfo.taskId]
            if (cachedResources != null) {
                return@withContext cachedResources.veilIcon
            }
            val resources = loadAppResources(taskInfo)
            if (resources.shouldCacheResult) {
                taskToResourceCache[taskInfo.taskId] = resources
            }
            localeListOnCache[taskInfo.taskId] = taskInfo.getConfiguration().getLocales()
            return@withContext resources.veilIcon
        }

    override fun onWindowDecorCreated(taskInfo: RunningTaskInfo) {
        assertMainThread()
        existingTasks.add(taskInfo.taskId)
    }

    override fun onWindowDecorClosed(taskInfo: RunningTaskInfo) {
        assertMainThread()
        existingTasks.remove(taskInfo.taskId)
        taskToResourceCache.remove(taskInfo.taskId)
        localeListOnCache.remove(taskInfo.taskId)
    }

    private fun checkWindowDecorExists(taskInfo: RunningTaskInfo) {
        check(existingTasks.contains(taskInfo.taskId)) {
            "Attempt to obtain resource for non-existent decoration"
        }
    }

    private suspend fun loadAppResources(taskInfo: RunningTaskInfo): AppResources =
        withContext(bgDispatcher) {
//            Trace.beginSection("$TAG#loadAppResources")
            try {
                val pm = userProfilesContexts.getOrCreate(taskInfo.userId).packageManager
                val activityInfo = getActivityInfo(taskInfo, pm)
                val appName = pm.getApplicationLabel(activityInfo.applicationInfo)
                val appIconDrawable = iconProvider.getIcon(activityInfo)
                val badgedAppIconDrawable =
                    pm.getUserBadgedIcon(appIconDrawable, taskInfo.userHandle())
                val appIcon =
                    headerIconFactory.createIconBitmap(badgedAppIconDrawable, /* scale= */ 1f)
                val veilIcon = veilIconFactory.createScaledBitmap(appIconDrawable, MODE_DEFAULT)
                return@withContext AppResources(
                    appName = appName,
                    appIcon = appIcon,
                    veilIcon = veilIcon,
                    shouldCacheResult = true,
                )
            } catch (e: NameNotFoundException) {
                Slog.e(TAG, "Failed to get app resources")
                val pm = userProfilesContexts.getOrCreate(taskInfo.userId).packageManager
                val defaultIconDrawable = pm.getDefaultActivityIcon()
                val appIcon =
                    headerIconFactory.createIconBitmap(defaultIconDrawable, /* scale= */ 1f)
                val veilIcon = veilIconFactory.createScaledBitmap(defaultIconDrawable, MODE_DEFAULT)
                // Do not cache the result when loading failed.
                return@withContext AppResources(
                    appName = "",
                    appIcon = appIcon,
                    veilIcon = veilIcon,
                    shouldCacheResult = false,
                )
            } finally {
//                Trace.endSection()
            }
        }

    private fun getActivityInfo(taskInfo: RunningTaskInfo, pm: PackageManager): ActivityInfo {
        return pm.getActivityInfo(taskInfo.component(), /* flags= */ 0)
    }

    private fun assertMainThread() {
        check(mainHandler.looper.isCurrentThread) { "Method must be called on $mainHandler" }
    }

    private fun RunningTaskInfo.component() = baseIntent.component!!

    private fun RunningTaskInfo.userHandle() = UserHandle.of(userId)

    data class AppResources(
        val appName: CharSequence,
        val appIcon: Bitmap,
        val veilIcon: Bitmap,
        val shouldCacheResult: Boolean,
    )

    private fun dump(pw: PrintWriter, prefix: String) {
        val innerPrefix = "$prefix  "
        pw.println("${prefix}$TAG")
        pw.println(innerPrefix + "appResourceCache=$taskToResourceCache")
        pw.println(innerPrefix + "existingTasks=$existingTasks")
    }

    companion object {
        private const val TAG = "AppResourceProvider"
    }
}

/** Creates an icon factory with the provided [dimensions]. */
fun Context.createIconFactory(@DimenRes dimensions: Int): BaseIconFactory {
    val densityDpi = resources.displayMetrics.densityDpi
    val iconSize = resources.getDimensionPixelSize(dimensions)
    return BaseIconFactory(this, densityDpi, iconSize)
}
