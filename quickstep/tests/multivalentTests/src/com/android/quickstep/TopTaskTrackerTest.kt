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

package com.android.quickstep

import android.app.ActivityManager
import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.app.TaskInfo
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.internal.R
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.statehandlers.DesktopVisibilityController.Companion.INACTIVE_DESK_ID
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.quickstep.TopTaskTracker.HISTORY_SIZE
import com.android.window.flags.Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND
import com.android.window.flags.Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND
import com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING
import com.android.wm.shell.shared.GroupedTaskInfo
import com.android.wm.shell.shared.GroupedTaskInfo.TYPE_DESK
import com.android.wm.shell.shared.GroupedTaskInfo.TYPE_FULLSCREEN
import com.android.wm.shell.shared.GroupedTaskInfo.TYPE_SPLIT
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Test for [TopTaskTracker] */
@RunWith(AndroidJUnit4::class)
class TopTaskTrackerTest {

    @get:Rule val setFlagsRule = SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT)

    private val mockContext = mock<Context>()
    private val mockResources = mock<Resources>()

    private lateinit var topTaskTracker: TopTaskTracker

    @Before
    fun setUp() {
        doReturn(mockResources).whenever(mockContext).resources

        val mockDaggerSingletonTracker = mock<DaggerSingletonTracker>()
        val mockSystemUiProxy = mock<SystemUiProxy>()
        val mockDesktopVisibilityController = mock<DesktopVisibilityController>()

        topTaskTracker =
            TopTaskTracker(
                mockContext,
                mockDaggerSingletonTracker,
                mockSystemUiProxy,
                mockDesktopVisibilityController,
            )
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun getPlaceholderGroupedTaskInfo_shellTopTaskTrackingEnabled_noVisibleTasks() {
        val cachedTaskInfo = TopTaskTracker.CachedTaskInfo(null)
        val result = cachedTaskInfo.getPlaceholderGroupedTaskInfo(null)
        assertThat(result).isNull()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun getPlaceholderGroupedTaskInfo_shellTopTaskTrackingEnabled_withVisibleTasks() {
        val taskInfo = createTaskInfo(1, DEFAULT_DISPLAY)
        val groupedTaskInfo = GroupedTaskInfo.forFullscreenTasks(taskInfo)
        val cachedTaskInfo = TopTaskTracker.CachedTaskInfo(groupedTaskInfo)

        val result = cachedTaskInfo.getPlaceholderGroupedTaskInfo(null)
        assertThat(result).isEqualTo(groupedTaskInfo)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun getPlaceholderGroupedTaskInfo_shellTopTaskTrackingDisabled_noTasks() {
        val cachedTaskInfo =
            TopTaskTracker.CachedTaskInfo(
                emptyList(),
                mockContext,
                DEFAULT_DISPLAY,
                INACTIVE_DESK_ID,
            )

        val result = cachedTaskInfo.getPlaceholderGroupedTaskInfo(null)

        assertThat(result).isNull()
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun getPlaceholderGroupedTaskInfo_shellTopTaskTrackingDisabled_withFullscreenTask() {
        val taskInfo = createTaskInfo(1, DEFAULT_DISPLAY)
        val tasks = listOf(taskInfo)
        val cachedTaskInfo =
            TopTaskTracker.CachedTaskInfo(tasks, mockContext, DEFAULT_DISPLAY, INACTIVE_DESK_ID)

        val result = cachedTaskInfo.getPlaceholderGroupedTaskInfo(null)

        assertThat(result).isNotNull()
        assertThat(result!!.isBaseType(TYPE_FULLSCREEN)).isTrue()
        assertThat(result.taskInfo1).isEqualTo(taskInfo)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun getPlaceholderGroupedTaskInfo_shellTopTaskTrackingDisabled_withSplitTasks() {
        val taskInfo1 = createTaskInfo(1, DEFAULT_DISPLAY)
        val taskInfo2 = createTaskInfo(2, DEFAULT_DISPLAY)
        val tasks = listOf(taskInfo1, taskInfo2)
        val splitTaskIds = intArrayOf(1, 2)
        val cachedTaskInfo =
            TopTaskTracker.CachedTaskInfo(tasks, mockContext, DEFAULT_DISPLAY, INACTIVE_DESK_ID)

        val result = cachedTaskInfo.getPlaceholderGroupedTaskInfo(splitTaskIds)

        assertThat(result).isNotNull()
        assertThat(result!!.isBaseType(TYPE_SPLIT)).isTrue()
        assertThat(result.taskInfo1).isEqualTo(taskInfo1)
        assertThat(result.taskInfo2).isEqualTo(taskInfo2)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND, FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun getPlaceholderGroupedTaskInfo_shellTopTaskTrackingDisabled_withDesktopEnabled_noActiveDesk() {
        doReturn(true).whenever(mockResources).getBoolean(eq(R.bool.config_isDesktopModeSupported))
        doReturn(true)
            .whenever(mockResources)
            .getBoolean(eq(R.bool.config_canInternalDisplayHostDesktops))
        val taskInfo = createDesktopTaskInfo(1, DEFAULT_DISPLAY)
        val tasks = listOf(taskInfo)
        val cachedTaskInfo =
            TopTaskTracker.CachedTaskInfo(tasks, mockContext, DEFAULT_DISPLAY, INACTIVE_DESK_ID)

        val result = cachedTaskInfo.getPlaceholderGroupedTaskInfo(null)

        assertThat(result).isNotNull()
        assertThat(result!!.isBaseType(TYPE_FULLSCREEN)).isTrue()
        assertThat(result.deskId).isEqualTo(INACTIVE_DESK_ID)
        assertThat(result.taskInfo1).isEqualTo(taskInfo)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND, FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun getPlaceholderGroupedTaskInfo_shellTopTaskTrackingDisabled_withDesktopEnabled_withActiveDesk() {
        doReturn(true).whenever(mockResources).getBoolean(eq(R.bool.config_isDesktopModeSupported))
        doReturn(true)
            .whenever(mockResources)
            .getBoolean(eq(R.bool.config_canInternalDisplayHostDesktops))
        val activeDeskId = 10
        val taskInfo = createDesktopTaskInfo(1, DEFAULT_DISPLAY)
        val tasks = listOf(taskInfo)
        val cachedTaskInfo =
            TopTaskTracker.CachedTaskInfo(tasks, mockContext, DEFAULT_DISPLAY, activeDeskId)

        val result = cachedTaskInfo.getPlaceholderGroupedTaskInfo(null)

        assertThat(result).isNotNull()
        assertThat(result!!.isBaseType(TYPE_DESK)).isTrue()
        assertThat(result.deskId).isEqualTo(activeDeskId)
        assertThat(result.getTaskInfoList()).isEqualTo(tasks)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING, FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND)
    fun getPlaceholderGroupedTaskInfo_shellTopTaskTrackingDisabled_withDesktopDisabled_withActiveDesk() {
        doReturn(true).whenever(mockResources).getBoolean(eq(R.bool.config_isDesktopModeSupported))
        doReturn(true)
            .whenever(mockResources)
            .getBoolean(eq(R.bool.config_canInternalDisplayHostDesktops))
        val taskInfo = createDesktopTaskInfo(1, DEFAULT_DISPLAY)
        val tasks = listOf(taskInfo)
        val cachedTaskInfo =
            TopTaskTracker.CachedTaskInfo(tasks, mockContext, DEFAULT_DISPLAY, INACTIVE_DESK_ID)
        val result = cachedTaskInfo.getPlaceholderGroupedTaskInfo(null)

        assertThat(result).isNotNull()
        assertThat(result!!.isBaseType(TYPE_DESK)).isTrue()
        assertThat(result.deskId).isEqualTo(INACTIVE_DESK_ID)
        assertThat(result.taskInfo1).isEqualTo(taskInfo)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun getCachedTopTask_filtersOutBubbleTask() {
        val appBubbleTask = createBubbleTaskInfo(taskId = 100, appBubble = true)
        val convoBubbleTask = createBubbleTaskInfo(taskId = 101, appBubble = false)
        val normalTask = createTaskInfo(taskId = 102)

        topTaskTracker.handleTaskMovedToFront(normalTask)
        topTaskTracker.handleTaskMovedToFront(appBubbleTask)
        topTaskTracker.handleTaskMovedToFront(convoBubbleTask)

        val topTask =
            topTaskTracker.getCachedTopTask(/* filterOnlyVisibleRecents= */ false, DEFAULT_DISPLAY)

        assertThat(topTask.taskId).isEqualTo(normalTask.taskId)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun getCachedTopTask_allBubbles_noTopTask() {
        val convoBubbleTask = createBubbleTaskInfo(taskId = 100, appBubble = false)
        val appBubbleTask = createBubbleTaskInfo(taskId = 101, appBubble = true)

        topTaskTracker.handleTaskMovedToFront(convoBubbleTask)
        topTaskTracker.handleTaskMovedToFront(appBubbleTask)

        val topTask =
            topTaskTracker.getCachedTopTask(/* filterOnlyVisibleRecents= */ false, DEFAULT_DISPLAY)

        assertThat(topTask.taskId).isEqualTo(INVALID_TASK_ID)
    }

    @Test
    fun getAllTasks_tasksMoreThanHistorySize_onlyFreeFormTasksNotRemoved() {
        val freeformTaskCount = HISTORY_SIZE * 2
        val fullScreenTaskCount = HISTORY_SIZE + 2
        var taskId = 0
        val freeformTasks = mutableListOf<TaskInfo>()
        repeat(freeformTaskCount) {
            val task = createTaskInfo(taskId = ++taskId, windowingMode = WINDOWING_MODE_FREEFORM)
            freeformTasks.add(task)
            topTaskTracker.handleTaskMovedToFront(task)
        }
        val fullScreenTasks = mutableListOf<TaskInfo>()
        repeat(fullScreenTaskCount) {
            val task = createTaskInfo(taskId = ++taskId, windowingMode = WINDOWING_MODE_FULLSCREEN)
            fullScreenTasks.add(task)
            topTaskTracker.handleTaskMovedToFront(task)
        }

        val cachedInfo =
            topTaskTracker.getCachedTopTask(/* filterOnlyVisibleRecents= */ false, DEFAULT_DISPLAY)
        val tasks = cachedInfo.mAllCachedTasks

        val expectedTasks = freeformTasks + fullScreenTasks.takeLast(HISTORY_SIZE)
        assertThat(tasks).containsExactlyElementsIn(expectedTasks)
    }

    private fun createTaskInfo(
        taskId: Int,
        displayId: Int = DEFAULT_DISPLAY,
        windowingMode: Int = WINDOWING_MODE_FULLSCREEN,
    ) =
        ActivityManager.RunningTaskInfo().apply {
            this.taskId = taskId
            this.displayId = displayId
            this.baseIntent = Intent()
            this.baseActivity = ComponentName("test", "test")
            this.configuration.windowConfiguration.activityType = ACTIVITY_TYPE_STANDARD
            this.configuration.windowConfiguration.windowingMode = windowingMode
        }

    private fun createDesktopTaskInfo(taskId: Int, displayId: Int) =
        createTaskInfo(taskId, displayId, WINDOWING_MODE_FREEFORM)

    private fun createBubbleTaskInfo(
        taskId: Int,
        appBubble: Boolean,
        displayId: Int = DEFAULT_DISPLAY,
    ): TaskInfo {
        val taskInfo = createTaskInfo(taskId, displayId)
        taskInfo.isAppBubble = appBubble
        if (!appBubble) {
            taskInfo.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_MULTI_WINDOW
            taskInfo.configuration.windowConfiguration.isAlwaysOnTop = true
        }
        return taskInfo
    }
}
