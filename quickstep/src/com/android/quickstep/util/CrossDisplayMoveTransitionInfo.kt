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
package com.android.quickstep.util

import android.app.WindowConfiguration
import android.graphics.Rect
import android.util.Log
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.TransitionInfo

/** A utility class to parse TransitionInfo for a cross-display move. */
data class CrossDisplayMoveTransitionInfo
internal constructor(
    @JvmField val taskMovingBetweenDisplays: TransitionInfo.Change,
    @JvmField val srcDisplayId: Int,
    @JvmField val dstDisplayId: Int,

    // dstTaskLeash is the leash for the *actual* moving task.
    // (As we want re-layouts, etc to be visible on the destination display.)
    @JvmField val dstTaskLeash: SurfaceControl,

    // srcTaskLeash is the leash for the *screenshot* of the moving task.
    // (As we do not want re-layouts, etc to be visible on the source display.)
    //
    // if srcRoot is null, srcTaskLeash will also be null
    // if srcRoot is not null, srcTaskLeash *may or may not* be null.
    @JvmField val srcTaskLeash: SurfaceControl?,

    @JvmField val srcBounds: Rect,
    @JvmField val dstBounds: Rect,
    @JvmField val srcRoot: TransitionInfo.Root?,
    @JvmField val dstRoot: TransitionInfo.Root,

    // This is attached to the default display, which may be either SRC or DST.
    @JvmField val launcherToFront: TransitionInfo.Change?,
) {
    companion object {
        private const val TAG = "CrossDisplayMoveTransitionInfo"

        /**
         * Returns the Change object for the task moving between displays, or null if not found.
         */
        @JvmStatic
        fun getCrossDisplayMove(info: TransitionInfo): TransitionInfo.Change? =
            info.changes.find {
                it.taskInfo != null && it.startDisplayId != it.endDisplayId
            }

        private fun findRoot(info: TransitionInfo, displayId: Int): TransitionInfo.Root? {
            for (i in 0 until info.rootCount) {
                val root = info.getRoot(i)
                if (root.displayId == displayId) {
                    return root
                }
            }
            return null
        }

        private fun findOpeningLauncher(
            info: TransitionInfo,
            taskToIgnore: TransitionInfo.Change
        ): TransitionInfo.Change? =
            info.changes.find {
                it !== taskToIgnore &&
                    it.taskInfo?.activityType == WindowConfiguration.ACTIVITY_TYPE_HOME &&
                    (it.mode == TRANSIT_OPEN || it.mode == TRANSIT_TO_FRONT)
            }

        /**
         * Creates a new CrossDisplayMoveTransitionInfo, or returns null if the transition is not a
         * valid cross-display move.
         */
        @JvmStatic
        fun create(
            info: TransitionInfo,
        ): CrossDisplayMoveTransitionInfo? {
            val taskMovingBetweenDisplays = getCrossDisplayMove(info)
            if (taskMovingBetweenDisplays == null) {
                Log.e(TAG, "Failed to find task moving between displays")
                return null
            }
            val srcDisplayId = taskMovingBetweenDisplays.startDisplayId
            val dstDisplayId = taskMovingBetweenDisplays.endDisplayId
            val srcRoot = findRoot(info, srcDisplayId)
            val dstRoot = findRoot(info, taskMovingBetweenDisplays.endDisplayId)
            if (dstRoot == null) {
                Log.e(TAG, "Failed to find dstRoot")
                return null
            }
            val srcTaskLeash = if (srcRoot != null) taskMovingBetweenDisplays.snapshot else null

            return CrossDisplayMoveTransitionInfo(
                taskMovingBetweenDisplays = taskMovingBetweenDisplays,
                srcDisplayId = srcDisplayId,
                dstDisplayId = dstDisplayId,
                dstTaskLeash = taskMovingBetweenDisplays.leash,
                srcTaskLeash = srcTaskLeash,
                srcBounds = taskMovingBetweenDisplays.startAbsBounds,
                dstBounds = taskMovingBetweenDisplays.endAbsBounds,
                srcRoot = srcRoot,
                dstRoot = dstRoot,
                launcherToFront = findOpeningLauncher(info, taskMovingBetweenDisplays)
            )
        }
    }
}