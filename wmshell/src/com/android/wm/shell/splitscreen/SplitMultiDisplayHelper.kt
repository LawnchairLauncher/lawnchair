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

package com.android.wm.shell.splitscreen

import android.app.ActivityManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.SurfaceControl
import android.window.TransitionInfo
import com.android.internal.protolog.ProtoLog
import com.android.window.flags.Flags
import com.android.wm.shell.common.split.SplitLayout
import com.android.wm.shell.protolog.ShellProtoLogGroup

/**
 * Helper class for managing split-screen functionality across multiple displays.
 */
class SplitMultiDisplayHelper(private val displayManager: DisplayManager) {

    /**
     * A map that stores the [SplitTaskHierarchy] associated with each display ID.
     * The keys are display IDs (integers), and the values are [SplitTaskHierarchy] objects,
     * which encapsulate the information needed to manage split-screen tasks on that display.
     */
    private val displayTaskMap: MutableMap<Int, SplitTaskHierarchy> = mutableMapOf()

    /**
     * A list of all currently connected display ids.
     * This is saved to avoid repeatedly querying the display manager. Display ids here does not
     * support disconnect/reconnect update at the moment.
     *
     * TODO: b/415861490 - support updating displayIds for split screen when connecting/disconnecting
     */
    private var displayIds: ArrayList<Int>? = null

    /**
     * SplitTaskHierarchy is a class that encapsulates the components required
     * for managing split-screen functionality on a specific display.
     */
    data class SplitTaskHierarchy(
        var rootTaskInfo: ActivityManager.RunningTaskInfo? = null,
        var mainStage: StageTaskListener? = null,
        var sideStage: StageTaskListener? = null,
        var rootTaskLeash: SurfaceControl? = null,
        var splitLayout: SplitLayout? = null
    )

    /**
     * Returns a cached list of all currently connected display IDs if available, otherwise query
     * the system for the latest display ids.
     *
     * @return An ArrayList of display IDs.
     */
    fun getCachedOrSystemDisplayIds(): ArrayList<Int> {
        if (displayIds == null) {
            val ids = ArrayList<Int>()
            displayManager.displays?.forEach { display ->
                ids.add(display.displayId)
            }
            displayIds = ids
        }

        return checkNotNull(displayIds)
    }

    /**
     * Swaps the [SplitTaskHierarchy] objects associated with two different display IDs.
     *
     * @param firstDisplayId  The ID of the first display.
     * @param secondDisplayId The ID of the second display.
     */
    fun swapDisplayTaskHierarchy(firstDisplayId: Int, secondDisplayId: Int) {
        if (!displayTaskMap.containsKey(firstDisplayId) || !displayTaskMap.containsKey(secondDisplayId)) {
            ProtoLog.w(
                ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
                "Attempted to swap task hierarchies for invalid display IDs: %d, %d",
                firstDisplayId,
                secondDisplayId
            )
            return
        }

        if (firstDisplayId == secondDisplayId) {
            return
        }

        val firstHierarchy = displayTaskMap[firstDisplayId]
        val secondHierarchy = displayTaskMap[secondDisplayId]

        displayTaskMap[firstDisplayId] = checkNotNull(secondHierarchy)
        displayTaskMap[secondDisplayId] = checkNotNull(firstHierarchy)
    }

    /**
     * Gets the root task info for the given display ID.
     *
     * @param displayId The ID of the display.
     * @return The root task info, or null if not found.
     */
    fun getDisplayRootTaskInfo(displayId: Int): ActivityManager.RunningTaskInfo? {
        val targetDisplayId = if (Flags.enableMultiDisplaySplit()) displayId else DEFAULT_DISPLAY
        return displayTaskMap[targetDisplayId]?.rootTaskInfo
    }

    /**
     * Sets the root task info for the given display ID.
     *
     * @param displayId    The ID of the display.
     * @param rootTaskInfo The root task info to set.
     */
    fun setDisplayRootTaskInfo(
        displayId: Int,
        rootTaskInfo: ActivityManager.RunningTaskInfo?
    ) {
        val targetDisplayId = if (Flags.enableMultiDisplaySplit()) displayId else DEFAULT_DISPLAY
        val hierarchy = displayTaskMap.computeIfAbsent(targetDisplayId) { SplitTaskHierarchy() }
        hierarchy.rootTaskInfo = rootTaskInfo
    }

    fun getDisplayRootTaskLeash(displayId: Int): SurfaceControl? {
        val targetDisplayId = if (Flags.enableMultiDisplaySplit()) displayId else DEFAULT_DISPLAY
        return displayTaskMap[targetDisplayId]?.rootTaskLeash
    }

    fun setDisplayRootTaskLeash(
        displayId: Int,
        leash: SurfaceControl?
    ) {
        val targetDisplayId = if (Flags.enableMultiDisplaySplit()) displayId else DEFAULT_DISPLAY
        val hierarchy = displayTaskMap.computeIfAbsent(targetDisplayId) { SplitTaskHierarchy() }
        hierarchy.rootTaskLeash = leash
    }

    companion object {
        /**
         * Returns the display ID associated with the first change in the given [TransitionInfo].
         * It prioritize the end display ID of the change. If the end display ID is invalid, fall back
         * to the start display ID. If TransitionInfo has no changes, or if the first change has both
         * invalid end display ID and invalid start display ID, this method returns DEFAULT_DISPLAY.
         *
         * @param info the [TransitionInfo] containing transition changes
         * @return a valid display ID, or DEFAULT_DISPLAY as a fallback
         */
        @JvmStatic
        fun getTransitionDisplayId(info: TransitionInfo): Int {
            if (!Flags.enableMultiDisplaySplit()) (
                return DEFAULT_DISPLAY
            )

            if (info.changes.isEmpty()) {
                return DEFAULT_DISPLAY
            }
            // TODO: b/393217881 - take in a specific change instead of the first change for
            //  multi display split related tasks.
            val change: TransitionInfo.Change = info.changes.first()
            var displayId = change.endDisplayId
            if (displayId == Display.INVALID_DISPLAY) {
                displayId = change.startDisplayId
            }
            return if (displayId != Display.INVALID_DISPLAY) displayId else DEFAULT_DISPLAY
        }
    }
}