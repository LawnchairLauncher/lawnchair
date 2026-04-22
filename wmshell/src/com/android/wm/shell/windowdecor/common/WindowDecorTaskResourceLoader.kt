/*
 * Copyright (C) 2024 The Android Open Source Project
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
import android.os.LocaleList
import android.os.UserHandle
import android.util.Slog
import androidx.tracing.Trace
import com.android.internal.annotations.VisibleForTesting
import com.android.launcher3.icons.BaseIconFactory
import com.android.launcher3.icons.BaseIconFactory.MODE_DEFAULT
import com.android.launcher3.icons.IconProvider
import com.android.wm.shell.R
import com.android.wm.shell.common.UserProfileContexts
import com.android.wm.shell.shared.annotations.ShellBackgroundThread
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.sysui.UserChangeListener
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap

/**
 * A utility and cache for window decoration UI resources.
 */
class WindowDecorTaskResourceLoader(
    shellInit: ShellInit,
    private val shellController: ShellController,
    private val shellCommandHandler: ShellCommandHandler,
    private val userProfilesContexts: UserProfileContexts,
    private val iconProvider: IconProvider,
    private val headerIconFactory: BaseIconFactory,
    private val veilIconFactory: BaseIconFactory,
) {
    constructor(
        context: Context,
        shellInit: ShellInit,
        shellController: ShellController,
        shellCommandHandler: ShellCommandHandler,
        userProfileContexts: UserProfileContexts,
    ) : this(
        shellInit,
        shellController,
        shellCommandHandler,
        userProfileContexts,
        IconProvider(context),
        headerIconFactory = context.createIconFactory(R.dimen.desktop_mode_caption_icon_radius),
        veilIconFactory = context.createIconFactory(R.dimen.desktop_mode_resize_veil_icon_size),
    )

    /**
     * A map of task -> resources to prevent unnecessary binder calls and resource loading
     * when multiple window decorations need the same resources, for example, the app name or icon
     * used in the header and menu.
     */
    @VisibleForTesting
    val taskToResourceCache = ConcurrentHashMap<Int, AppResources>()
    /**
     * Keeps track of existing tasks with a window decoration. Useful to verify that requests to
     * get resources occur within the lifecycle of a window decoration, otherwise it'd be possible
     * to load a tasks resources into memory without a future signal to clean up the resource.
     * See [onWindowDecorClosed].
     */
    private val existingTasks = mutableSetOf<Int>()

    /**
     * A map of task -> localeList to keep track of the language of app name that's currently
     * cached in |taskToResourceCache|.
     */
    @VisibleForTesting
    val localeListOnCache = ConcurrentHashMap<Int, LocaleList>()

    init {
        shellInit.addInitCallback(this::onInit, this)
    }

    private fun onInit() {
        shellCommandHandler.addDumpCallback(this::dump, this)
        shellController.addUserChangeListener(object : UserChangeListener {
            override fun onUserChanged(newUserId: Int, userContext: Context) {
                // No need to hold on to resources for tasks of another profile.
                taskToResourceCache.clear()
            }
        })
    }

    /** Returns the user readable name for this task. */
    @ShellBackgroundThread
    fun getName(taskInfo: RunningTaskInfo): CharSequence {
        checkWindowDecorExists(taskInfo)
        val cachedResources = taskToResourceCache[taskInfo.taskId]
        val localeListActiveOnCacheTime = localeListOnCache[taskInfo.taskId]
        if (cachedResources != null &&
            taskInfo.getConfiguration().getLocales().equals(localeListActiveOnCacheTime)) {
            return cachedResources.appName
        }
        val resources = loadAppResources(taskInfo)
        localeListOnCache[taskInfo.taskId] = taskInfo.getConfiguration().getLocales()
        return resources.appName
    }

    /** Returns the icon for use by the app header and menus for this task. */
    @ShellBackgroundThread
    fun getHeaderIcon(taskInfo: RunningTaskInfo): Bitmap {
        checkWindowDecorExists(taskInfo)
        val cachedResources = taskToResourceCache[taskInfo.taskId]
        if (cachedResources != null) {
            return cachedResources.appIcon
        }
        val resources = loadAppResources(taskInfo)
        localeListOnCache[taskInfo.taskId] = taskInfo.getConfiguration().getLocales()
        return resources.appIcon
    }

    /** Returns the icon for use by the resize veil for this task. */
    @ShellBackgroundThread
    fun getVeilIcon(taskInfo: RunningTaskInfo): Bitmap {
        checkWindowDecorExists(taskInfo)
        val cachedResources = taskToResourceCache[taskInfo.taskId]
        if (cachedResources != null) {
            return cachedResources.veilIcon
        }
        val resources = loadAppResources(taskInfo)
        localeListOnCache[taskInfo.taskId] = taskInfo.getConfiguration().getLocales()
        return resources.veilIcon
    }

    /** Called when a window decoration for this task is created. */
    fun onWindowDecorCreated(taskInfo: RunningTaskInfo) {
        existingTasks.add(taskInfo.taskId)
    }

    /** Called when a window decoration for this task is closed. */
    fun onWindowDecorClosed(taskInfo: RunningTaskInfo) {
        existingTasks.remove(taskInfo.taskId)
        taskToResourceCache.remove(taskInfo.taskId)
        localeListOnCache.remove(taskInfo.taskId)
    }

    private fun checkWindowDecorExists(taskInfo: RunningTaskInfo) {
        check(existingTasks.contains(taskInfo.taskId)) {
            "Attempt to obtain resource for non-existent decoration"
        }
    }

    private fun loadAppResources(taskInfo: RunningTaskInfo): AppResources {
        Trace.beginSection("$TAG#loadAppResources")
        try {
            val pm =
                userProfilesContexts
                .getOrCreate(taskInfo.userId)
                .createPackageContext(taskInfo.component().packageName, /* flags= */ 0)
                .packageManager
            val activityInfo = getActivityInfo(taskInfo, pm)
            val appName = pm.getApplicationLabel(activityInfo.applicationInfo)
            val appIconDrawable = iconProvider.getIcon(activityInfo)
            val badgedAppIconDrawable = pm.getUserBadgedIcon(appIconDrawable, taskInfo.userHandle())
            val appIcon = headerIconFactory.createIconBitmap(badgedAppIconDrawable, /* scale= */ 1f)
            val veilIcon = veilIconFactory.createScaledBitmap(appIconDrawable, MODE_DEFAULT)
            val appResources =
                AppResources(appName = appName, appIcon = appIcon, veilIcon = veilIcon)
            taskToResourceCache[taskInfo.taskId] = appResources
            return appResources
        } catch (e: NameNotFoundException) {
            Slog.e(TAG, "Failed to get app resources")
            val pm =
                userProfilesContexts
                .getOrCreate(taskInfo.userId)
                .packageManager
            val defaultIconDrawable = pm.getDefaultActivityIcon()
            val appIcon = headerIconFactory.createIconBitmap(defaultIconDrawable, /* scale= */ 1f)
            val veilIcon = veilIconFactory.createScaledBitmap(defaultIconDrawable, MODE_DEFAULT)
            // Do not cache the result when loading failed.
            return AppResources(appName = "", appIcon = appIcon, veilIcon = veilIcon)
        } finally {
            Trace.endSection()
        }
    }

    private fun getActivityInfo(taskInfo: RunningTaskInfo, pm: PackageManager): ActivityInfo {
        return pm.getActivityInfo(taskInfo.component(), /* flags= */ 0)
    }

    private fun RunningTaskInfo.component() = baseIntent.component!!

    private fun RunningTaskInfo.userHandle() = UserHandle.of(userId)

    data class AppResources(val appName: CharSequence, val appIcon: Bitmap, val veilIcon: Bitmap)

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
