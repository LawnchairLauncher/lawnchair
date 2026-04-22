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

package com.android.wm.shell.splitscreen

import android.content.Context
import com.android.internal.protolog.ProtoLog
import com.android.launcher3.icons.IconProvider
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.protolog.ShellProtoLogGroup
import com.android.wm.shell.shared.split.SplitScreenConstants
import com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_NONE
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_INDEX_0
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_INDEX_1
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_INDEX_2
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED
import com.android.wm.shell.shared.split.SplitScreenConstants.SnapPosition
import com.android.wm.shell.shared.split.SplitScreenConstants.SplitIndex
import com.android.wm.shell.shared.split.SplitScreenConstants.SplitPosition
import com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_A
import com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_B
import com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_C
import com.android.wm.shell.splitscreen.SplitScreen.stageTypeToString
import com.android.wm.shell.windowdecor.WindowDecorViewModel
import java.util.Collections
import java.util.Optional

/**
 * Responsible for creating [StageTaskListener]s and maintaining their ordering on screen.
 * Must be notified whenever stages positions change via swapping or starting/ending tasks
 */
class StageOrderOperator (
        context: Context,
        taskOrganizer: ShellTaskOrganizer,
        displayId: Int,
        stageCallbacks: StageTaskListener.StageListenerCallbacks,
        syncQueue: SyncTransactionQueue,
        iconProvider: IconProvider,
        windowDecorViewModel: Optional<WindowDecorViewModel>
    ) {

    private val MAX_STAGES = 3
    /**
     * This somewhat acts as a replacement to stageTypes in the intermediary, so we want to start
     * it after the @StageType constant values just to be safe and avoid potentially subtle bugs.
     */
    private var stageIds = listOf(STAGE_TYPE_A, STAGE_TYPE_B, STAGE_TYPE_C)

    /**
     * Active Stages, this list represent the current, ordered list of stages that are
     * currently visible to the user. This map should be empty if the user is currently
     * not in split screen. Note that this is different than if split screen is visible, which
     * is determined by [StageListenerImpl.mVisible].
     * Split stages can be active and in the background
     */
    val activeStages = mutableListOf<StageTaskListener>()
    val allStages = mutableListOf<StageTaskListener>()
    var isActive: Boolean = false
    var isVisible: Boolean = false
    @SnapPosition private var currentLayout: Int = SNAP_TO_NONE

    init {
        for(i in 0 until MAX_STAGES) {
            allStages.add(StageTaskListener(context,
                taskOrganizer,
                displayId,
                stageCallbacks,
                syncQueue,
                iconProvider,
                windowDecorViewModel,
                stageIds[i])
            )
        }
    }

    /**
     * Updates internal state to keep record of "active" stages. Note that this does NOT call
     * [StageTaskListener.activate] on the stages.
     */
    fun onEnteringSplit(@SnapPosition goingToLayout: Int) {
        if (goingToLayout == currentLayout) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
                "Entering Split requested same layout split is in: %d", goingToLayout)
            return
        }
        val freeStages: List<StageTaskListener> =
            allStages.filterNot { activeStages.contains(it) }
        when(goingToLayout) {
            SplitScreenConstants.SNAP_TO_2_50_50,
            SplitScreenConstants.SNAP_TO_2_33_66,
            SplitScreenConstants.SNAP_TO_2_66_33 -> {
                if (activeStages.size < 2) {
                    // take from allStages and add into activeStages
                    for (i in 0 until (2 - activeStages.size)) {
                        val stage = freeStages[i]
                        activeStages.add(stage)
                    }
                }
            }
        }
        ProtoLog.d(
            ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
            "Activated stages: %d ids=%s",
            activeStages.size,
            activeStages.joinToString(",") { stageTypeToString(it.id) }
        )
        isActive = true
    }

    fun onExitingSplit() {
        activeStages.clear()
        isActive = false
    }

    /**
     * Given a legacy [SplitPosition] returns one of the stages from the actives stages.
     * If there are no active stages and [checkAllStagesIfNotActive] is not true, then will return
     * null
     */
    fun getStageForLegacyPosition(@SplitPosition position: Int,
                                  checkAllStagesIfNotActive : Boolean = false) :
            StageTaskListener? {
        if (activeStages.size != 2 && !checkAllStagesIfNotActive) {
            return null
        }
        val listToCheck = if (activeStages.isEmpty() and checkAllStagesIfNotActive)
            allStages else
            activeStages
        if (position == SPLIT_POSITION_TOP_OR_LEFT) {
            return listToCheck[0]
        } else if (position == SPLIT_POSITION_BOTTOM_OR_RIGHT) {
            return listToCheck[1]
        } else {
            throw IllegalArgumentException("No stage for invalid position")
        }
    }

    /**
     * This will swap the stages for the two stages on either side of the given divider.
     * Note: This will keep [activeStages] and [allStages] in sync by swapping both of them
     * If there are no [activeStages] then this will be a no-op.
     *
     * TODO(b/379984874): Take in a divider identifier to determine which array indices to swap
     */
    fun onDoubleTappedDivider() {
        if (activeStages.isEmpty()) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
                "Stages not active, ignoring swap request")
            return
        }

        Collections.swap(activeStages, 0, 1)
        Collections.swap(allStages, 0, 1)
    }

    /**
     * Returns a legacy split position for the given stage. If no stages are active then this will
     * return [SPLIT_POSITION_UNDEFINED]
     */
    @SplitPosition
    fun getLegacyPositionForStage(stage: StageTaskListener) : Int {
        if (allStages[0] == stage) {
            return SPLIT_POSITION_TOP_OR_LEFT
        } else if (allStages[1] == stage) {
            return SPLIT_POSITION_BOTTOM_OR_RIGHT
        } else {
            return SPLIT_POSITION_UNDEFINED
        }
    }

    /**
     * Returns the stageId from a given splitIndex. This will default to checking from all stages if
     * [isActive] is false, otherwise will only check active stages.
     */
    fun getStageForIndex(@SplitIndex splitIndex: Int) : StageTaskListener {
        // Probably should do a check for index to be w/in the bounds of the current split layout
        // that we're currently in
        val listToCheck = if (isActive) activeStages else allStages
        if (splitIndex == SPLIT_INDEX_0) {
            return listToCheck[0]
        } else if (splitIndex == SPLIT_INDEX_1) {
            return listToCheck[1]
        } else if (splitIndex == SPLIT_INDEX_2) {
            return listToCheck[2]
        } else {
            // Though I guess what if we're adding to the end? Maybe that indexing needs to be
            // resolved elsewhere
            throw IllegalStateException("No stage for the given splitIndex")
        }
    }
}