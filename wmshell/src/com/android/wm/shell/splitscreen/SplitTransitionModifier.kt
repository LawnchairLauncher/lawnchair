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

import android.app.ActivityManager.RunningTaskInfo
import android.graphics.Rect
import android.util.Log
import android.util.Slog
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.TransitionInfo
import android.window.WindowContainerToken
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.shared.split.SplitScreenConstants

/**
 * Utils class that modifies the changes for a given TransitionInfo.
 * Avoiding static classes to help keep testability. Let's try to avoid keeping any
 * state at the class level.
 */
class SplitTransitionModifier {
    private val TAG = "SplitTransitionModifier"

    /**
     * Adds a dim layer to the transition root. We do this by creating a Change and a SurfaceControl
     * that are reparented under the associated app layer (so that it moves together with the app
     * layer).
     */
    fun addDimLayerToTransition(
        info: TransitionInfo, show: Boolean,
        stage: StageTaskListener, bounds: Rect,
        t: SurfaceControl.Transaction
    ) {
        val stageTaskId = stage.runningTaskInfo.taskId
        val dimLayer = stage.mDimLayer
        if (dimLayer == null || !dimLayer.isValid) {
            Slog.w(TAG, "addDimLayerToTransition but leash was released or not created")
        } else {
            // Find the first change in the TransitionInfo that has a parentTaskId matching the
            // stage root. There should only be one such app layer, but since changes in
            // TransitionInfo are ordered from top to bottom (guaranteed by the system), we'll get
            // the top one in any case.
            var appLayerToken: WindowContainerToken? = null

            for (c in info.changes) {
                if (c.taskInfo?.parentTaskId == stageTaskId) {
                    appLayerToken = c.container
                    break
                }
            }

            if (appLayerToken == null) {
                Slog.w(TAG, "addDimLayerToTransition but no app layer found")
                return
            }

            val dimLayerCopy = SurfaceControl(dimLayer, "addDimLayerToTransition")

            val change = TransitionInfo.Change(null /* container */, dimLayerCopy)
            change.parent = appLayerToken
            change.setStartAbsBounds(bounds)
            change.setEndAbsBounds(bounds)
            change.mode =
                if (show) TRANSIT_TO_FRONT else TRANSIT_TO_BACK
            change.flags = SplitScreenConstants.FLAG_IS_DIM_LAYER
            info.addChange(change)

            t.reparent(dimLayerCopy, info.getChange(appLayerToken)?.leash)
            t.setLayer(dimLayerCopy, Int.MAX_VALUE)
            t.setPosition(dimLayerCopy, 0f, 0f)
            val crop = Rect(0, 0, bounds.width(), bounds.height())
            t.setCrop(dimLayerCopy, crop)
        }
    }

    /**
     * Adds [TransitionInfo.Change]s to [info] *IF* they are not already a part of it. This
     * will not modify top level or stage root tasks already part of [info]
     */
    fun addStageRootsToTransition(
        info: TransitionInfo,
        mainStage: StageTaskListener?,
        sideStage: StageTaskListener?,
        mainStageBounds: Rect,
        sideStageBounds: Rect,
        splitRootTask: RunningTaskInfo,
        splitRootLeash: SurfaceControl,
        splitRootBounds: Rect
    ) {
        if (mainStage == null || sideStage == null) {
            return
        }

        // Opening and closing tasks of main stage
        val mainStageChildren: List<TransitionInfo.Change>
        // Opening and closing tasks of side stage
        val sideStageChildren: List<TransitionInfo.Change>
        // No task parent :(
        val remainingChanges: List<TransitionInfo.Change>
        val mainStageChange: TransitionInfo.Change
        val sideStageChange: TransitionInfo.Change
        val splitRootChange: TransitionInfo.Change

        // If any children are opening, set the roots to be opening
        val mode = if (anySplitChangesToFront(info, mainStage, sideStage))
            TRANSIT_TO_FRONT else TRANSIT_TO_BACK

        // Add main stage root, set it as the parent for its children
        if (info.getChange(mainStage.mRootTaskInfo.token) != null) {
            mainStageChange = checkNotNull(info.getChange(mainStage.mRootTaskInfo.token))
            mainStageChildren = getChildrenForParent(info, mainStageChange, false /*setParent*/)
        } else {
            mainStageChange = getChangeForStageRoot(
                mainStage,
                mainStageBounds,
                mode,
                splitRootTask.token
            )
            mainStageChildren = getChildrenForParent(info, mainStageChange, true /*setParent*/)
        }

        // Add side stage root, set it as the parent for its children
        if (info.getChange(sideStage.mRootTaskInfo.token) != null) {
            sideStageChange = checkNotNull(info.getChange(sideStage.mRootTaskInfo.token))
            sideStageChildren = getChildrenForParent(info, sideStageChange, false /*setParent*/)
        } else {
            sideStageChange = getChangeForStageRoot(
                sideStage,
                sideStageBounds,
                mode,
                splitRootTask.token
            )
            sideStageChildren = getChildrenForParent(info, sideStageChange, true /*setParent*/)
        }

        // Add top level split root, set it as the parent for main and side stage roots
        splitRootChange = if (info.getChange(splitRootTask.token) != null) {
            checkNotNull(info.getChange(splitRootTask.token))
        } else {
            getChangeForSplitRoot(
                mode, splitRootTask,
                splitRootLeash, splitRootBounds
            )
        }
        // Explicitly set the parents of the stage roots because if either of the stage roots
        // weren't present or the top level split root wasn't present in the original transition,
        // the parent will be null.
        mainStageChange.parent = splitRootTask.token
        sideStageChange.parent = splitRootTask.token

        remainingChanges = info.changes.stream()
            .filter { change: TransitionInfo.Change ->
                // No parent AND not the top level split root (we'll add that separately)
                (change.taskInfo == null || change.taskInfo?.parentTaskId == -1) &&
                        (change.taskInfo?.taskId != splitRootChange.taskInfo?.taskId)
            }
            .toList()

        val finalList = mutableListOf<TransitionInfo.Change>()
        finalList.addAll(mainStageChildren)
        finalList.add(mainStageChange)
        finalList.addAll(sideStageChildren)
        finalList.add(sideStageChange)
        finalList.add(splitRootChange)
        finalList.addAll(remainingChanges)

        Log.v(TAG, "original change size: ${info.changes.size} finalSize: ${finalList.size}")
        info.changes.clear()
        info.changes.addAll(finalList)
    }

    /**
    * Returns true if any of the changes in [info] are opening and have a parent that is
    * either the main or side stage root.
    */
    private fun anySplitChangesToFront(info: TransitionInfo,
                                       mainStage: StageTaskListener,
                                       sideStage: StageTaskListener): Boolean {
        return info.changes.stream()
            .anyMatch { change: TransitionInfo.Change ->
                val isOpening = TransitionUtil.isOpeningMode(change.mode)
                val taskInfo = change.taskInfo
                val hasStageRootParent = taskInfo != null &&
                        (taskInfo.parentTaskId == mainStage.mRootTaskInfo.taskId ||
                                taskInfo.parentTaskId == sideStage.mRootTaskInfo.taskId)
                isOpening && hasStageRootParent
            }
    }

    /**
     * Creates and returns a [TransitionInfo.Change] for the top level split root (indicated
     * by [rootTaskInfo]) to the transition. Almost entirely similar to [getChangeForStageRoot] except
     * this does not set a parent for the new Change.
     */
    private fun getChangeForSplitRoot(
        transitMode: Int,
        rootTaskInfo: RunningTaskInfo,
        rootTaskLeash: SurfaceControl,
        rootBounds: Rect
    ): TransitionInfo.Change {
        val change = TransitionInfo.Change(
            rootTaskInfo.token,
            rootTaskLeash
        )
        change.taskInfo = rootTaskInfo
        change.mode = transitMode
        change.setStartAbsBounds(rootBounds)
        change.setEndAbsBounds(rootBounds)
        return change
    }

    /**
     * Creates and returns a [TransitionInfo.Change] for the individual stage roots
     * (indicated by [stage]). The [parentToken] should be that of the top level split root.
     */
    private fun getChangeForStageRoot(
        stage: StageTaskListener, bounds: Rect,
        transitMode: Int,
        parentToken: WindowContainerToken
    ): TransitionInfo.Change {
        val change = TransitionInfo.Change(
            stage.mRootTaskInfo.token,
            stage.mRootLeash
        )
        change.taskInfo = stage.mRootTaskInfo
        change.parent = parentToken
        change.mode = transitMode
        change.setStartAbsBounds(bounds)
        change.setEndAbsBounds(bounds)
        return change
    }

    /**
     * Given a [parentChange], this iterates over all changes in [info] and gets the children
     * for all changes where the change's parentTaskId matches [parentChange]s taskId.
     * If [setParent] is [true] then it will also set the child's parent WCT to that of the
     * [parentChange]
     *
     * @return [List] of all the changes that are children of [parentChange]
     */
    private fun getChildrenForParent(
        info: TransitionInfo,
        parentChange: TransitionInfo.Change,
        setParent: Boolean): List<TransitionInfo.Change> {
        val childrenOfChange = mutableListOf<TransitionInfo.Change>()
        info.changes.stream()
            .filter { change: TransitionInfo.Change ->
                change.taskInfo != null &&
                        change.taskInfo?.parentTaskId == parentChange.taskInfo?.taskId
            }
            .forEach { change: TransitionInfo.Change ->
                if (setParent) {
                    change.parent = parentChange.taskInfo?.token
                }
                childrenOfChange.add(change)
            }
        return childrenOfChange
    }
}