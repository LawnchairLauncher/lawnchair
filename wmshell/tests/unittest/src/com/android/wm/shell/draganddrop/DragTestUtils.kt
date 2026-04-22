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
package com.android.wm.shell.draganddrop

import android.app.ActivityManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.ClipData
import android.content.ClipDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.os.Process
import org.mockito.Mockito
import java.util.Random

/**
 * Convenience methods for drag tests.
 */
object DragTestUtils {
    /**
     * Creates an app-based clip data that is by default resizeable.
     */
    @JvmStatic
    fun createAppClipData(mimeType: String): ClipData {
        val clipDescription = ClipDescription(mimeType, arrayOf(mimeType))
        val i = Intent()
        when (mimeType) {
            ClipDescription.MIMETYPE_APPLICATION_SHORTCUT -> {
                i.putExtra(Intent.EXTRA_PACKAGE_NAME, "package")
                i.putExtra(Intent.EXTRA_SHORTCUT_ID, "shortcut_id")
            }

            ClipDescription.MIMETYPE_APPLICATION_TASK -> i.putExtra(Intent.EXTRA_TASK_ID, 12345)
            ClipDescription.MIMETYPE_APPLICATION_ACTIVITY -> {
                val pi = Mockito.mock(PendingIntent::class.java)
                Mockito.doReturn(Process.myUserHandle()).`when`(pi).creatorUserHandle
                i.putExtra(ClipDescription.EXTRA_PENDING_INTENT, pi)
            }
        }
        i.putExtra(Intent.EXTRA_USER, Process.myUserHandle())
        val item = ClipData.Item(i)
        item.activityInfo = ActivityInfo()
        item.activityInfo.applicationInfo = ApplicationInfo()
        val data = ClipData(clipDescription, item)
        setClipDataResizeable(data, true)
        return data
    }

    /**
     * Creates an intent-based clip data that is by default resizeable.
     */
    @JvmStatic
    fun createIntentClipData(intent: PendingIntent): ClipData {
        val clipDescription = ClipDescription(
            "Intent",
            arrayOf(ClipDescription.MIMETYPE_TEXT_INTENT)
        )
        val item = ClipData.Item.Builder()
            .setIntentSender(intent.intentSender)
            .build()
        item.activityInfo = ActivityInfo()
        item.activityInfo.applicationInfo = ApplicationInfo()
        val data = ClipData(clipDescription, item)
        setClipDataResizeable(data, true)
        return data
    }

    /**
     * Creates a new launchable intent.
     */
    @JvmStatic
    fun createLaunchableIntent(context: Context) : PendingIntent {
        val fakeActivityIntent = Intent()
        fakeActivityIntent.component = ComponentName("com.android.wm.shell.draganddrop",
            "FakeActivity")
        return PendingIntent.getActivity(context, 0, fakeActivityIntent, FLAG_IMMUTABLE)
    }

    /**
     * Sets the given clip data to be resizeable.
     */
    @JvmStatic
    fun setClipDataResizeable(data: ClipData, resizeable: Boolean) {
        data.getItemAt(0).activityInfo.resizeMode = if (resizeable)
            ActivityInfo.RESIZE_MODE_RESIZEABLE
        else
            ActivityInfo.RESIZE_MODE_UNRESIZEABLE
    }

    /**
     * Creates a task info with the given params.
     */
    @JvmStatic
    fun createTaskInfo(winMode: Int, actType: Int): ActivityManager.RunningTaskInfo {
        return createTaskInfo(winMode, actType, false)
    }

    /**
     * Creates a task info with the given params.
     */
    @JvmStatic
    fun createTaskInfo(winMode: Int, actType: Int, alwaysOnTop: Boolean = false):
            ActivityManager.RunningTaskInfo {
        val info = ActivityManager.RunningTaskInfo()
        info.taskId = Random().nextInt()
        info.configuration.windowConfiguration.activityType = actType
        info.configuration.windowConfiguration.windowingMode = winMode
        info.configuration.windowConfiguration.isAlwaysOnTop = alwaysOnTop
        info.isVisible = true
        info.isResizeable = true
        info.baseActivity = ComponentName(
            "com.android.wm.shell",
            ".ActivityWithMode$winMode"
        )
        info.baseIntent = Intent()
        info.baseIntent.setComponent(info.baseActivity)
        val activityInfo = ActivityInfo()
        activityInfo.packageName = info.baseActivity!!.packageName
        activityInfo.name = info.baseActivity!!.className
        info.topActivityInfo = activityInfo
        return info
    }
}
