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

package com.android.wm.shell.bubbles

import android.app.ActivityManager
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.os.Binder
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_ANYTHING
import com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
import com.android.wm.shell.MockToken
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.bubbles.util.BubbleTestUtils.verifyExitBubbleTransaction
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.taskview.TaskView
import com.android.wm.shell.taskview.TaskViewTaskController
import java.util.Optional
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

/**
 * Unit tests for [BubbleTaskStackListener].
 *
 * Build/Install/Run:
 *  atest WMShellRobolectricTests:BubbleTaskStackListenerTest (on host)
 *  atest WMShellMultivalentTestsOnDevice:BubbleTaskStackListenerTest (on device)
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleTaskStackListenerTest {

    @get:Rule
    val setFlagsRule = SetFlagsRule()

    private val mockTaskViewTaskController = mock<TaskViewTaskController> {
        on { taskOrganizer } doReturn mock<ShellTaskOrganizer>()
    }
    private val mockTaskView = mock<TaskView> {
        on { controller } doReturn mockTaskViewTaskController
    }
    private val bubble = mock<Bubble> {
        on { taskView } doReturn mockTaskView
    }
    private val bubbleController = mock<BubbleController>()
    private val bubbleData = mock<BubbleData>()
    private val splitScreenController = mock<SplitScreenController>()
    private val bubbleTaskStackListener = BubbleTaskStackListener(
        bubbleController,
        bubbleData,
        { Optional.of(splitScreenController) },
    )
    private val bubbleTaskId = 123
    private val bubbleTaskToken: WindowContainerToken = MockToken.token()
    private val task = ActivityManager.RunningTaskInfo().apply {
        taskId = bubbleTaskId
        token = bubbleTaskToken
    }

    @Before
    fun setUp() {
        ProtoLog.REQUIRE_PROTOLOGTOOL = false
        ProtoLog.init()
    }

    @Test
    fun onActivityRestartAttempt_inStackAppBubbleRestart_selectsAndExpandsStack() {
        bubbleData.stub {
            on { getBubbleInStackWithTaskId(bubbleTaskId) } doReturn bubble
        }

        bubbleTaskStackListener.onActivityRestartAttempt(
            task,
            homeTaskVisible = false,
            clearedTask = false,
            wasVisible = false,
        )

        verify(bubbleData).setSelectedBubbleAndExpandStack(bubble)
    }

    @Test
    fun onActivityRestartAttempt_inStackAppBubbleMovingToFront_doesNothing() {
        task.configuration.windowConfiguration.activityType = ACTIVITY_TYPE_STANDARD
        bubbleController.stub {
            on { shouldBeAppBubble(task) } doReturn true
        }
        bubbleData.stub {
            on { getBubbleInStackWithTaskId(bubbleTaskId) } doReturn bubble
        }

        bubbleTaskStackListener.onActivityRestartAttempt(
            task,
            homeTaskVisible = false,
            clearedTask = false,
            wasVisible = false,
        )

        verify(bubbleData, never()).setSelectedBubbleAndExpandStack(bubble)
    }

    @Test
    @EnableFlags(
        FLAG_ENABLE_CREATE_ANY_BUBBLE,
        FLAG_ENABLE_BUBBLE_ANYTHING,
    )
    fun onActivityRestartAttempt_inStackAppBubbleToFullscreen_notifiesTaskRemoval() {
        val captionInsetsOwner = Binder()
        mockTaskView.stub {
            on { getCaptionInsetsOwner() } doReturn captionInsetsOwner
        }
        task.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
        bubbleData.stub {
            on { getBubbleInStackWithTaskId(bubbleTaskId) } doReturn bubble
        }

        bubbleTaskStackListener.onActivityRestartAttempt(
            task,
            homeTaskVisible = false,
            clearedTask = false,
            wasVisible = false,
        )

        val taskViewTaskController = bubble.taskView.controller
        val taskOrganizer = taskViewTaskController.taskOrganizer
        val wct = argumentCaptor<WindowContainerTransaction>().let { wctCaptor ->
            verify(taskOrganizer).applyTransaction(wctCaptor.capture())
            wctCaptor.lastValue
        }
        verifyExitBubbleTransaction(wct, bubbleTaskToken.asBinder(), captionInsetsOwner)
        verify(taskViewTaskController).notifyTaskRemovalStarted(task)
    }

    @Test
    @EnableFlags(
        FLAG_ENABLE_CREATE_ANY_BUBBLE,
        FLAG_ENABLE_BUBBLE_ANYTHING,
    )
    fun onActivityRestartAttempt_inStackAppBubbleToSplit_doesNothing() {
        task.parentTaskId = 456
        bubbleData.stub {
            on { getBubbleInStackWithTaskId(bubbleTaskId) } doReturn bubble
        }

        splitScreenController.stub {
            on { isTaskRootOrStageRoot(456) } doReturn true
        }

        val taskViewTaskController = bubble.taskView.controller
        val taskOrganizer = taskViewTaskController.taskOrganizer
        clearInvocations(taskViewTaskController)

        bubbleTaskStackListener.onActivityRestartAttempt(
            task,
            homeTaskVisible = false,
            clearedTask = false,
            wasVisible = false,
        )

        verifyNoInteractions(taskOrganizer)
        verifyNoInteractions(taskViewTaskController)
    }

    @Test
    @EnableFlags(
        FLAG_ENABLE_CREATE_ANY_BUBBLE,
        FLAG_ENABLE_BUBBLE_ANYTHING,
    )
    fun onTaskMovedToFront_inStackAppBubbleToFullscreen_notifiesTaskRemoval() {
        task.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
        bubbleData.stub {
            on { getBubbleInStackWithTaskId(bubbleTaskId) } doReturn bubble
        }

        bubbleTaskStackListener.onTaskMovedToFront(task)

        val taskViewTaskController = bubble.taskView.controller
        val taskOrganizer = taskViewTaskController.taskOrganizer
        val wct = argumentCaptor<WindowContainerTransaction>().let { wctCaptor ->
            verify(taskOrganizer).applyTransaction(wctCaptor.capture())
            wctCaptor.lastValue
        }
        verifyExitBubbleTransaction(wct, bubbleTaskToken.asBinder())
        verify(taskViewTaskController).notifyTaskRemovalStarted(task)
    }
}
