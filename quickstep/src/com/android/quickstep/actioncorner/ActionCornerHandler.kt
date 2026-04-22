/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.quickstep.actioncorner

import android.app.ActivityOptions
import android.content.Context
import android.window.SplashScreen
import com.android.launcher3.Flags.enableReversibleHomeActionCorner
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.util.ActivityOptionsWrapper
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.quickstep.OverviewCommandHelper
import com.android.quickstep.OverviewCommandHelper.CommandType
import com.android.quickstep.OverviewCommandHelper.CommandType.TOGGLE_OVERVIEW_PREVIOUS
import com.android.quickstep.OverviewComponentObserver
import com.android.quickstep.RecentsModel
import com.android.quickstep.TopTaskTracker
import com.android.quickstep.util.AnimUtils
import com.android.quickstep.util.GroupTask
import com.android.quickstep.util.SingleTask
import com.android.quickstep.util.SplitTask
import com.android.quickstep.views.RecentsViewContainer
import com.android.systemui.shared.system.ActivityManagerWrapper
import com.android.systemui.shared.system.actioncorner.ActionCornerConstants.Action
import com.android.systemui.shared.system.actioncorner.ActionCornerConstants.HOME
import com.android.systemui.shared.system.actioncorner.ActionCornerConstants.OVERVIEW
import com.android.wm.shell.shared.GroupedTaskInfo
import com.android.wm.shell.shared.split.SplitScreenConstants
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.Executor
import java.util.function.Predicate

/**
 * Handles actions triggered from action corners that are mapped to specific functionalities.
 * Launcher supports both overview and home actions.
 */
class ActionCornerHandler
@AssistedInject
constructor(
    @ApplicationContext private val context: Context,
    private val overviewComponentObserver: OverviewComponentObserver,
    private val topTaskTracker: TopTaskTracker,
    private val recentsModel: RecentsModel,
    private val activityManagerWrapper: ActivityManagerWrapper,
    @LightweightBackground private val executor: Executor,
    @Assisted private val overviewCommandHelper: OverviewCommandHelper,
) {
    @AssistedFactory
    interface Factory {
        fun create(overviewCommandHelper: OverviewCommandHelper): ActionCornerHandler
    }

    private val displayToPreviousScreenMap = HashMap<Int, PreviousScreen>()

    fun handleAction(@Action action: Int, displayId: Int) {
        when (action) {
            // TODO(b/410798748): handle projected mode when launching overview
            OVERVIEW -> overviewCommandHelper.addCommandsForAllDisplays(TOGGLE_OVERVIEW_PREVIOUS)
            HOME ->
                if (enableReversibleHomeActionCorner()) {
                    handleHomeAction(displayId)
                } else {
                    overviewCommandHelper.addCommand(CommandType.HOME, displayId)
                }
            else -> {}
        }
    }

    private fun handleHomeAction(displayId: Int) {
        val topTask =
            topTaskTracker.getCachedTopTask(/* filterOnlyVisibleRecents= */ false, displayId)
        val groupTask =
            topTask.getPlaceholderGroupedTaskInfo(topTaskTracker.runningSplitTaskIds) ?: return
        if (!groupTask.isBaseType(GroupedTaskInfo.TYPE_DESK)) {
            if (
                topTask.isHomeTask &&
                    getRecentsViewContainer(displayId)?.isRecentsViewVisible == false
            ) {
                goToPreviousScreen(displayId)
            } else {
                storeCurrentScreen(displayId, groupTask)
                overviewCommandHelper.addCommand(CommandType.HOME, displayId)
            }
        } else {
            // TODO(b/416664984): handle reversible home action for desktop mode
        }
    }

    private fun goToPreviousScreen(displayId: Int) {
        val previousScreen = displayToPreviousScreenMap[displayId]
        if (previousScreen is PreviousScreen.OverviewScreen) {
            overviewCommandHelper.addCommand(CommandType.TOGGLE, displayId)
            displayToPreviousScreenMap.remove(displayId)
        } else {
            // Get it from recentsModel so we have the latest split bound for split task. It also
            // checks if the stored task is valid, it would not go to previous screen if the stored
            // task is not the latest task.
            recentsModel.getTasks(Predicate { it.displayId == displayId }) {
                val latestTask = it.last()
                val isSameAsStoredTask =
                    when (previousScreen) {
                        is PreviousScreen.SingleTaskScreen -> {
                            latestTask is SingleTask &&
                                latestTask.task.key.id == previousScreen.taskId
                        }

                        is PreviousScreen.SplitTaskScreen -> {
                            latestTask is SplitTask &&
                                latestTask.topLeftTask.key.id == previousScreen.taskId1 &&
                                latestTask.bottomRightTask.key.id == previousScreen.taskId2
                        }

                        else -> {
                            false
                        }
                    }
                if (isSameAsStoredTask) {
                    launchGroupTask(latestTask, displayId)
                } else {
                    // Remove the stored task as it is outdated.
                    displayToPreviousScreenMap.remove(displayId)
                }
            }
        }
    }

    private fun storeCurrentScreen(displayId: Int, groupTask: GroupedTaskInfo) {
        if (getRecentsViewContainer(displayId)?.isRecentsViewVisible == true) {
            displayToPreviousScreenMap[displayId] = PreviousScreen.OverviewScreen
        } else {
            if (groupTask.isBaseType(GroupedTaskInfo.TYPE_SPLIT)) {
                displayToPreviousScreenMap[displayId] =
                    PreviousScreen.SplitTaskScreen(
                        groupTask.taskInfo1!!.taskId,
                        groupTask.taskInfo2!!.taskId,
                    )
            } else if (groupTask.isBaseType(GroupedTaskInfo.TYPE_FULLSCREEN)) {
                displayToPreviousScreenMap[displayId] =
                    PreviousScreen.SingleTaskScreen(groupTask.taskInfo1!!.taskId)
            }
        }
    }

    private fun launchGroupTask(task: GroupTask, displayId: Int) {
        when (task) {
            is SingleTask ->
                executor.execute {
                    val activityOptions: ActivityOptions =
                        makeDefaultActivityOptions(displayId) ?: return@execute
                    activityManagerWrapper.startActivityFromRecents(
                        task.task.key.id,
                        activityOptions,
                    )
                }
            is SplitTask -> {
                val splitSelectStateController =
                    getRecentsViewContainer(displayId)?.splitSelectStateController
                splitSelectStateController?.launchExistingSplitPair(
                    /* groupedTaskView= */ null,
                    task.topLeftTask.key.id,
                    task.bottomRightTask.key.id,
                    SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT,
                    /* callback= */ { splitSelectStateController.resetState() },
                    /* freezeTaskList= */ false,
                    task.splitBounds?.snapPosition ?: SplitScreenConstants.SNAP_TO_2_50_50,
                )
            }
        }
    }

    private fun getRecentsViewContainer(displayId: Int): RecentsViewContainer? =
        overviewComponentObserver.getContainerInterface(displayId)?.getCreatedContainer()

    private fun makeDefaultActivityOptions(displayId: Int): ActivityOptions? {
        val callbacks = RunnableList()
        val options = ActivityOptions.makeCustomAnimation(context, 0, 0)
        options.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_UNDEFINED)
        options.setPendingIntentBackgroundActivityStartMode(
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
        )

        val recentsViewContainer = getRecentsViewContainer(displayId) ?: return null
        val endCallback = AnimUtils.completeRunnableListCallback(callbacks, recentsViewContainer)
        options.setOnAnimationAbortListener(endCallback)
        options.setOnAnimationFinishedListener(endCallback)
        return ActivityOptionsWrapper(options, callbacks).options
    }
}
