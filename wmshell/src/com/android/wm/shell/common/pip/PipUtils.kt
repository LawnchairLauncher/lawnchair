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
import android.app.RemoteAction
import android.app.TaskInfo
import android.app.WindowConfiguration
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.graphics.PointF
import android.graphics.Rect
import android.os.RemoteException
import android.util.DisplayMetrics
import android.util.Log
import android.util.Pair
import android.util.TypedValue
import android.window.DesktopExperienceFlags.ENABLE_DESKTOP_WINDOWING_PIP
import android.window.TaskSnapshot
import android.window.TransitionInfo
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.shared.pip.PipFlags
import com.android.wm.shell.Flags
import com.android.wm.shell.protolog.ShellProtoLogGroup
import java.io.PrintWriter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** A class that includes convenience methods.  */
object PipUtils {
    private const val TAG = "PipUtils"

    // Minimum difference between two floats (e.g. aspect ratios) to consider them not equal.
    // TODO b/377530560: Restore epsilon once a long term fix is merged for non-config-at-end issue.
    private const val EPSILON = 0.05f

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
     * Returns a pseudo source rect hint for animation purposes when app-provided one is invalid.
     * Resulting adjusted source rect hint lets the app icon in the content overlay to stay visible.
     */
    @JvmStatic
    fun getPseudoSourceRectHint(appBounds: Rect, aspectRatio: Float): Rect {
        val appBoundsAspRatio = appBounds.width().toFloat() / appBounds.height()
        val width: Int
        val height: Int
        var left = appBounds.left
        var top = appBounds.top
        if (appBoundsAspRatio < aspectRatio) {
            width = appBounds.width()
            height = (width / aspectRatio).roundToInt()
            top = appBounds.top + (appBounds.height() - height) / 2
        } else {
            height = appBounds.height()
            width = (height * aspectRatio).roundToInt()
            left = appBounds.left + (appBounds.width() - width) / 2
        }
        return Rect(left, top, left + width, top + height)
    }

    /**
     * Temporary rounding "outward" (ie. -1.2 -> -2) used for crop since it is an int. We lean
     * outward since, usually, child surfaces are, themselves, cropped, so we'd prefer to avoid
     * inadvertently cutting out content that would otherwise be visible.
     */
    private fun roundOut(`val`: Float): Int {
        return (if (`val` >= 0f) ceil(`val`) else floor(`val`)).toInt()
    }

    /**
     * Calculates the transform to apply on a UNTRANSFORMED (config-at-end) Activity surface in
     * order for it's hint-rect to occupy the same task-relative position/dimensions as it would
     * have at the end of the transition (post-configuration).
     *
     * This is intended to be used in tandem with [calcStartTransform] below applied to the parent
     * task. Applying both transforms simultaneously should result in the appearance of nothing
     * having happened yet.
     *
     * Only the task should be animated (into it's identity state) and then WMCore will reset the
     * activity transform in sync with its new configuration upon finish.
     *
     * Usage example:
     *     calcEndTransform(pipActivity, pipTask, scale, pos);
     *     t.setScale(pipActivity.getLeash(), scale.x, scale.y);
     *     t.setPosition(pipActivity.getLeash(), pos.x, pos.y);
     *
     * @see calcStartTransform
     */
    @JvmStatic
    fun calcEndTransform(pipActivity: TransitionInfo.Change, pipTask: TransitionInfo.Change,
        outScale: PointF, outPos: PointF) {
        val actStartBounds = pipActivity.startAbsBounds
        val actEndBounds = pipActivity.endAbsBounds
        val taskEndBounds = pipTask.endAbsBounds

        var hintRect = pipTask.taskInfo?.pictureInPictureParams?.sourceRectHint
        if (hintRect == null) {
            hintRect = Rect(actStartBounds)
            hintRect.offsetTo(0, 0)
        }

        // FA = final activity bounds (absolute)
        // FT = final task bounds (absolute)
        // SA = start activity bounds (absolute)
        // H = source hint (relative to start activity bounds)
        // We want to transform the activity so that when the task is at FT, H overlaps with FA

        // This scales the activity such that the hint rect has the same dimensions
        // as the final activity bounds.
        val hintToEndScaleX = (actEndBounds.width().toFloat()) / (hintRect.width().toFloat())
        val hintToEndScaleY = (actEndBounds.height().toFloat()) / (hintRect.height().toFloat())
        // top-left needs to be (FA.tl - FT.tl) - H.tl * hintToEnd . H is relative to the
        // activity; so, for example, if shrinking H to FA (hintToEnd < 1), then the tl of the
        // shrunk SA is closer to H than expected, so we need to reduce how much we offset SA
        // to get H.tl to match.
        val startActPosInTaskEndX =
            (actEndBounds.left - taskEndBounds.left) - hintRect.left * hintToEndScaleX
        val startActPosInTaskEndY =
            (actEndBounds.top - taskEndBounds.top) - hintRect.top * hintToEndScaleY
        outScale.set(hintToEndScaleX, hintToEndScaleY)
        outPos.set(startActPosInTaskEndX, startActPosInTaskEndY)
    }

    @JvmStatic
    fun isContentPip(pipTaskInfo: TaskInfo?): Boolean {
        if (pipTaskInfo == null) return false
        return pipTaskInfo.launchIntoPipHostTaskId != -1
    }

    /**
     * Calculates the transform and crop to apply on a Task surface in order for the config-at-end
     * activity inside it (original-size activity transformed to match it's hint rect to the final
     * Task bounds) to occupy the same world-space position/dimensions as it had before the
     * transition.
     *
     * Intended to be used in tandem with [calcEndTransform].
     *
     * Usage example:
     *     calcStartTransform(pipTask, scale, pos, crop);
     *     t.setScale(pipTask.getLeash(), scale.x, scale.y);
     *     t.setPosition(pipTask.getLeash(), pos.x, pos.y);
     *     t.setCrop(pipTask.getLeash(), crop);
     *
     * @see calcEndTransform
     */
    @JvmStatic
    fun calcStartTransform(pipTask: TransitionInfo.Change, outScale: PointF,
        outPos: PointF, outCrop: Rect) {
        val startBounds = pipTask.startAbsBounds
        val taskEndBounds = pipTask.endAbsBounds
        // For now, pip activity bounds always matches task bounds. If this ever changes, we'll
        // need to get the activity offset.
        val endBounds = taskEndBounds
        var hintRect = pipTask.taskInfo?.pictureInPictureParams?.sourceRectHint
        if (hintRect == null) {
            hintRect = Rect(startBounds)
            hintRect.offsetTo(0, 0)
        }

        // FA = final activity bounds (absolute)
        // FT = final task bounds (absolute)
        // SA = start activity bounds (absolute)
        // H = source hint (relative to start activity bounds)
        // We want to transform the activity so that when the task is at FT, H overlaps with FA

        // The scaling which takes the hint rect (H) in SA and matches it to FA
        val hintToEndScaleX = (endBounds.width().toFloat()) / (hintRect.width().toFloat())
        val hintToEndScaleY = (endBounds.height().toFloat()) / (hintRect.height().toFloat())

        // We want to set the transform on the END TASK surface to put the start activity
        // back to where it was.
        // First do backwards scale (which takes FA back to H)
        val endToHintScaleX = 1f / hintToEndScaleX
        val endToHintScaleY = 1f / hintToEndScaleY
        // Then top-left needs to place FA (relative to the FT) at H (relative to SA):
        //   so -(FA.tl - FT.tl) + SA.tl + H.tl
        //  but we have scaled up the task, so anything that was "within" the task needs to
        //  be scaled:
        //   so -(FA.tl - FT.tl)*endToHint + SA.tl + H.tl
        val endTaskPosForStartX = (-(endBounds.left - taskEndBounds.left) * endToHintScaleX
                + startBounds.left + hintRect.left)
        val endTaskPosForStartY = (-(endBounds.top - taskEndBounds.top) * endToHintScaleY
                + startBounds.top + hintRect.top)
        outScale.set(endToHintScaleX, endToHintScaleY)
        outPos.set(endTaskPosForStartX, endTaskPosForStartY)

        // now need to set crop to reveal the non-hint stuff. Again, hintrect is relative, so
        // we must apply outsets to reveal the *activity* content which is *inside* the task
        // and thus is scaled (ie. if activity is scaled down, each task-level pixel exposes
        // >1 activity-level pixels)
        // For example, the topleft crop would be:
        //   (FA.tl - FT.tl) - H.tl * hintToEnd
        //    ^ activity within task
        // bottomright can just use scaled activity size
        //   tl + scale(SA.size, hintToEnd)
        outCrop.left = roundOut((endBounds.left - taskEndBounds.left)
                - hintRect.left * hintToEndScaleX)
        outCrop.top = roundOut((endBounds.top - taskEndBounds.top) - hintRect.top * hintToEndScaleY)
        outCrop.right = roundOut(outCrop.left + startBounds.width() * hintToEndScaleX)
        outCrop.bottom = roundOut(outCrop.top + startBounds.height() * hintToEndScaleY)
    }

    /**
     * Returns true if the system theme is the dark theme.
     */
    @JvmStatic
    fun Context.isDarkSystemTheme(): Boolean {
        return (resources.configuration.uiMode and UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Dumps information held by this class.
     */
    @JvmStatic
    fun dump(pw: PrintWriter, prefix: String) {
        pw.println("$prefix$TAG")
        val innerPrefix1 = "$prefix  "
        val innerPrefix2 = "$innerPrefix1  "
        pw.println("${innerPrefix1}isPipUmoExperienceEnabled=${PipFlags.isPipUmoExperienceEnabled}")
        pw.println("${innerPrefix1}isPip2ExperimentEnabled=${PipFlags.isPip2ExperimentEnabled}")
        pw.println("${innerPrefix2}enablePip2=${Flags.enablePip2()}")
        pw.println("${innerPrefix2}enableDwPip=${ENABLE_DESKTOP_WINDOWING_PIP.isTrue}")
    }
}
