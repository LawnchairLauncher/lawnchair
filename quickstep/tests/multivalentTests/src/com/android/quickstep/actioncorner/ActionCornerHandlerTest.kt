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

import android.app.ActivityManager.RecentTaskInfo
import android.graphics.Rect
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.Display.DEFAULT_DISPLAY
import android.window.IWindowContainerToken
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags.FLAG_ENABLE_REVERSIBLE_HOME_ACTION_CORNER
import com.android.launcher3.LauncherState
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.android.quickstep.BaseActivityInterface
import com.android.quickstep.OverviewCommandHelper
import com.android.quickstep.OverviewComponentObserver
import com.android.quickstep.RecentsModel
import com.android.quickstep.TopTaskTracker
import com.android.quickstep.util.GroupTask
import com.android.quickstep.util.SingleTask
import com.android.quickstep.util.SplitSelectStateController
import com.android.quickstep.util.SplitTask
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.system.ActivityManagerWrapper
import com.android.systemui.shared.system.actioncorner.ActionCornerConstants.HOME
import com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING
import com.android.wm.shell.shared.GroupedTaskInfo
import com.android.wm.shell.shared.split.SplitBounds
import com.android.wm.shell.shared.split.SplitScreenConstants
import java.util.function.Consumer
import java.util.function.Predicate
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(LauncherMultivalentJUnit::class)
@DisableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
@EnableFlags(FLAG_ENABLE_REVERSIBLE_HOME_ACTION_CORNER)
class ActionCornerHandlerTest {

    @get:Rule val context = SandboxApplication()
    @get:Rule val mSetFlagsRule = SetFlagsRule()

    private val overviewCommandHelper: OverviewCommandHelper = mock()
    private val overviewComponentObserver: OverviewComponentObserver = mock()
    private val containerInterface: BaseActivityInterface<LauncherState, QuickstepLauncher> = mock()
    private val recentsViewContainer: QuickstepLauncher = mock()
    private val splitSelectStateController: SplitSelectStateController = mock()

    private val topTaskTracker: TopTaskTracker = mock()

    private val recentsModel: RecentsModel = mock()
    private val activityManagerWrapper: ActivityManagerWrapper = mock()

    private val bgExecutor = UI_HELPER_EXECUTOR
    private val topTask: TopTaskTracker.CachedTaskInfo = mock()
    private val actionCornerHandler: ActionCornerHandler =
        ActionCornerHandler(
            context,
            overviewComponentObserver,
            topTaskTracker,
            recentsModel,
            activityManagerWrapper,
            bgExecutor,
            overviewCommandHelper,
        )

    @Before
    fun setup() {
        whenever(topTaskTracker.getCachedTopTask(false, DEFAULT_DISPLAY)).thenReturn(topTask)
        whenever(containerInterface.getCreatedContainer()).thenReturn(recentsViewContainer)
        whenever(overviewComponentObserver.getContainerInterface(any()))
            .thenReturn(containerInterface)
        whenever(recentsViewContainer.lifecycle).thenReturn(mock())
        whenever(recentsViewContainer.splitSelectStateController)
            .thenReturn(splitSelectStateController)
    }

    @Test
    fun inSingleTask_homeActionCornerTwice_goHomeThenGoBackToSingleTask() {
        whenever(topTask.isHomeTask).thenReturn(false)
        val taskInfo = createTaskInfo(1)
        mockFullScreenTopTask(taskInfo)

        actionCornerHandler.handleAction(HOME, DEFAULT_DISPLAY)

        verify(overviewCommandHelper)
            .addCommand(OverviewCommandHelper.CommandType.HOME, DEFAULT_DISPLAY)

        // Toggle back to the task from home
        mockAtHome()
        mockRecentsModelTasks(listOf(SingleTask(Task.from(taskInfo))))

        actionCornerHandler.handleAction(HOME, DEFAULT_DISPLAY)

        TestUtil.runOnExecutorSync(bgExecutor) {}
        verify(activityManagerWrapper).startActivityFromRecents(eq(1), any())
    }

    @Test
    fun inSplitTask_homeActionCornerTwice_goHomeThenGoBackToSplitTask() {
        whenever(topTask.isHomeTask).thenReturn(false)
        val taskInfo1 = createTaskInfo(1)
        val taskInfo2 = createTaskInfo(2)
        mockSplitTopTask(taskInfo1, taskInfo2)

        actionCornerHandler.handleAction(HOME, DEFAULT_DISPLAY)

        verify(overviewCommandHelper)
            .addCommand(OverviewCommandHelper.CommandType.HOME, DEFAULT_DISPLAY)

        actionCornerHandler.handleAction(HOME, DEFAULT_DISPLAY)

        mockAtHome()
        val splitBounds = SplitBounds(Rect(), Rect(), 1, 2, SplitScreenConstants.SNAP_TO_2_33_66)
        mockRecentsModelTasks(
            listOf(SplitTask(Task.from(taskInfo1), Task.from(taskInfo2), splitBounds))
        )
        actionCornerHandler.handleAction(HOME, DEFAULT_DISPLAY)

        verify(splitSelectStateController)
            .launchExistingSplitPair(
                eq(null),
                eq(taskInfo1.taskId),
                eq(taskInfo2.taskId),
                anyInt(),
                any(),
                eq(false),
                eq(SplitScreenConstants.SNAP_TO_2_33_66),
            )
    }

    @Test
    fun atHome_homeActionCorner_doNothing() {
        mockAtHome()

        actionCornerHandler.handleAction(HOME, DEFAULT_DISPLAY)

        verify(overviewCommandHelper, never())
            .addCommand(OverviewCommandHelper.CommandType.HOME, DEFAULT_DISPLAY)
        verify(activityManagerWrapper, never()).startActivityFromRecents(eq(1), any())
    }

    @Test
    fun inSingleTask_homeActionCorner_changeTask_goHome_homeActionCorner_doNothing() {
        whenever(topTask.isHomeTask).thenReturn(false)
        val taskInfo = createTaskInfo(1)
        mockFullScreenTopTask(taskInfo)
        actionCornerHandler.handleAction(HOME, DEFAULT_DISPLAY)

        // Change task
        val taskInfo2 = createTaskInfo(2)
        mockFullScreenTopTask(taskInfo2)
        mockRecentsModelTasks(listOf(SingleTask(Task.from(taskInfo2))))
        mockAtHome()

        // Do not go back to any task because the latest task was changed after going home by
        // action corner
        actionCornerHandler.handleAction(HOME, DEFAULT_DISPLAY)
        verify(activityManagerWrapper, never()).startActivityFromRecents(anyInt(), any())
    }

    private fun mockFullScreenTopTask(taskInfo: RecentTaskInfo) {
        val groupedTask = GroupedTaskInfo.forFullscreenTasks(taskInfo)
        whenever(topTaskTracker.runningSplitTaskIds).thenReturn(intArrayOf())
        whenever(topTask.getPlaceholderGroupedTaskInfo(any())).thenReturn(groupedTask)
    }

    private fun mockSplitTopTask(task1: RecentTaskInfo, task2: RecentTaskInfo) {
        val splitGroupedTask = GroupedTaskInfo.forSplitTasks(task1, task2, null)
        whenever(topTaskTracker.runningSplitTaskIds)
            .thenReturn(intArrayOf(task1.getTaskId(), task2.getTaskId()))
        whenever(topTask.getPlaceholderGroupedTaskInfo(any())).thenReturn(splitGroupedTask)
    }

    private fun mockAtHome() {
        whenever(topTask.isHomeTask).thenReturn(true)
        whenever(recentsViewContainer.isRecentsViewVisible).thenReturn(false)
    }

    private fun mockRecentsModelTasks(tasks: List<GroupTask>) {
        whenever(recentsModel.getTasks(any(), any<Consumer<List<GroupTask>>>())).thenAnswer {
            invocation ->
            val filter = invocation.getArgument<Predicate<GroupTask>>(0)
            val callback = invocation.getArgument<Consumer<ArrayList<GroupTask>>>(1)
            callback.accept(ArrayList(tasks.stream().filter(filter).toList()))
            0
        }
    }

    private fun createTaskInfo(id: Int) =
        RecentTaskInfo().apply {
            taskId = id
            displayId = DEFAULT_DISPLAY
            token = WindowContainerToken(Mockito.mock(IWindowContainerToken::class.java))
        }
}
