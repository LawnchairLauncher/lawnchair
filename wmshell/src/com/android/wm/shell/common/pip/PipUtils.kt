/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.wm.shell.common.pip

import android.app.ActivityTaskManager
import android.app.AppGlobals
import android.app.RemoteAction
import android.app.WindowConfiguration
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.RemoteException
import android.os.SystemProperties
import android.util.DisplayMetrics
import android.util.Log
import android.util.Pair
import android.util.TypedValue
import android.window.TaskSnapshot
import com.android.internal.protolog.common.ProtoLog
import com.android.wm.shell.Flags
import com.android.wm.shell.protolog.ShellProtoLogGroup
import kotlin.math.abs

/** A class that includes convenience methods.  */
object PipUtils {
    private const val TAG = "PipUtils"

    // Minimum difference between two floats (e.g. aspect ratios) to consider them not equal.
    private const val EPSILON = 1e-7

    /**
     * @return the ComponentName and user id of the top non-SystemUI activity in the pinned stack.
     * The component name may be null if no such activity exists.
     */
    @JvmStatic
    fun getTopPipActivity(context: Context): Pair<ComponentName?, Int> {
        try {
            val sysUiPackageName = context.packageName
            val pinnedTaskInfo = ActivityTaskManager.getService().getRootTaskInfo(
                WindowConfiguration.WINDOWING_MODE_PINNED,
                WindowConfiguration.ACTIVITY_TYPE_UNDEFINED
            )
            if (pinnedTaskInfo?.childTaskIds != null && pinnedTaskInfo.childTaskIds.isNotEmpty()) {
                for (i in pinnedTaskInfo.childTaskNames.indices.reversed()) {
                    val cn = ComponentName.unflattenFromString(
                        pinnedTaskInfo.childTaskNames[i]
                    )
                    if (cn != null && cn.packageName != sysUiPackageName) {
                        return Pair(cn, pinnedTaskInfo.childTaskUserIds[i])
                    }
                }
            }
        } catch (e: RemoteException) {
            ProtoLog.w(
                ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: Unable to get pinned stack.", TAG
            )
        }
        return Pair(null, 0)
    }

    /**
     * @return the pixels for a given dp value.
     */
    @JvmStatic
    fun dpToPx(dpValue: Float, dm: DisplayMetrics?): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpValue, dm).toInt()
    }

    /**
     * @return true if the aspect ratios differ
     */
    @JvmStatic
    fun aspectRatioChanged(aspectRatio1: Float, aspectRatio2: Float): Boolean {
        return abs(aspectRatio1 - aspectRatio2) > EPSILON
    }

    /**
     * Checks whether title, description and intent match.
     * Comparing icons would be good, but using equals causes false negatives
     */
    @JvmStatic
    fun remoteActionsMatch(action1: RemoteAction?, action2: RemoteAction?): Boolean {
        if (action1 === action2) return true
        if (action1 == null || action2 == null) return false
        return action1.isEnabled == action2.isEnabled &&
                action1.shouldShowIcon() == action2.shouldShowIcon() &&
                action1.title == action2.title &&
                action1.contentDescription == action2.contentDescription &&
                action1.actionIntent == action2.actionIntent
    }

    /**
     * Returns true if the actions in the lists match each other according to
     * [ ][PipUtils.remoteActionsMatch], including their position.
     */
    @JvmStatic
    fun remoteActionsChanged(list1: List<RemoteAction?>?, list2: List<RemoteAction?>?): Boolean {
        if (list1 == null && list2 == null) {
            return false
        }
        if (list1 == null || list2 == null) {
            return true
        }
        if (list1.size != list2.size) {
            return true
        }
        for (i in list1.indices) {
            if (!remoteActionsMatch(list1[i], list2[i])) {
                return true
            }
        }
        return false
    }

    /** @return [TaskSnapshot] for a given task id.
     */
    @JvmStatic
    fun getTaskSnapshot(taskId: Int, isLowResolution: Boolean): TaskSnapshot? {
        return if (taskId <= 0) null else try {
            ActivityTaskManager.getService().getTaskSnapshot(taskId, isLowResolution)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to get task snapshot, taskId=$taskId", e)
            null
        }
    }


    /**
     * Returns a fake source rect hint for animation purposes when app-provided one is invalid.
     * Resulting adjusted source rect hint lets the app icon in the content overlay to stay visible.
     */
    @JvmStatic
    fun getEnterPipWithOverlaySrcRectHint(appBounds: Rect, aspectRatio: Float): Rect {
        val appBoundsAspRatio = appBounds.width().toFloat() / appBounds.height()
        val width: Int
        val height: Int
        var left = 0
        var top = 0
        if (appBoundsAspRatio < aspectRatio) {
            width = appBounds.width()
            height = Math.round(width / aspectRatio)
            top = (appBounds.height() - height) / 2
        } else {
            height = appBounds.height()
            width = Math.round(height * aspectRatio)
            left = (appBounds.width() - width) / 2
        }
        return Rect(left, top, left + width, top + height)
    }

    private var isPip2ExperimentEnabled: Boolean? = null

    /**
     * Returns true if PiP2 implementation should be used. Besides the trunk stable flag,
     * system property can be used to override this read only flag during development.
     * It's currently limited to phone form factor, i.e., not enabled on ARC / TV.
     */
    @JvmStatic
    fun isPip2ExperimentEnabled(): Boolean {
        if (isPip2ExperimentEnabled == null) {
            val isArc = AppGlobals.getPackageManager().hasSystemFeature(
                "org.chromium.arc", 0)
            val isTv = AppGlobals.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_LEANBACK, 0)
            isPip2ExperimentEnabled = SystemProperties.getBoolean(
                    "persist.wm_shell.pip2", false) ||
                    (Flags.enablePip2Implementation() && !isArc && !isTv)
        }
        return isPip2ExperimentEnabled as Boolean
    }
}