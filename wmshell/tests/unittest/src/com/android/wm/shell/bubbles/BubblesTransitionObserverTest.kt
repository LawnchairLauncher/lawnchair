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
import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.content.ComponentName
import android.platform.test.annotations.EnableFlags
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.view.WindowManager.TransitionType
import android.window.ActivityTransitionInfo
import android.window.TransitionInfo
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
import com.android.wm.shell.Flags.FLAG_ENABLE_ENTER_SPLIT_REMOVE_BUBBLE
import com.android.wm.shell.MockToken
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.bubbles.util.BubbleTestUtils.verifyExitBubbleTransaction
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.taskview.TaskView
import com.android.wm.shell.taskview.TaskViewTaskController
import com.android.wm.shell.taskview.TaskViewTransitions
import com.android.wm.shell.transition.TransitionInfoBuilder
import com.android.wm.shell.transition.TransitionInfoBuilder.Companion.DEFAULT_DISPLAY_ID
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import java.util.Optional
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verifyNoInteractions

/**
 * Unit tests of [BubblesTransitionObserver].
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:BubblesTransitionObserverTest
 */
@SmallTest
@RunWith(TestParameterInjector::class)
class BubblesTransitionObserverTest : ShellTestCase() {

    private val bubble = mock<Bubble> {
        on { taskId } doReturn 1
    }
    private val bubbleData = mock<BubbleData> {
        on { isExpanded } doReturn true
        on { selectedBubble } doReturn bubble
        on { hasBubbles() } doReturn true
    }
    private val bubbleController = mock<BubbleController> {
        on { isStackAnimating } doReturn false
    }
    private val taskViewTransitions = mock<TaskViewTransitions>()
    private val splitScreenController = mock<SplitScreenController> {
        on { isTaskRootOrStageRoot(any()) } doReturn false
    }
    private val transitionObserver =
        BubblesTransitionObserver(
            bubbleController,
            bubbleData,
            taskViewTransitions,
            { Optional.of(splitScreenController) },
        )

    @Test
    fun testOnTransitionReady_openWithTaskTransition_collapsesStack() {
        val info = createTaskTransition(TRANSIT_OPEN, taskId = 2)

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verify(bubbleData).setExpanded(false)
    }

    @Test
    fun testOnTransitionReady_noneBubbleActivityTransition_collapsesStack() {
        val info = createActivityTransition(TRANSIT_OPEN, taskId = 2)

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verify(bubbleData).setExpanded(false)
    }

    @Test
    fun testOnTransitionReady_expandedBubbleActivityTransition_doesNotCollapseStack() {
        val info = createActivityTransition(TRANSIT_OPEN, taskId = 1)

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verify(bubbleData, never()).setExpanded(false)
    }

    @Test
    fun testOnTransitionReady_activityTransitionOnAnotherDisplay_doesNotCollapseStack() {
        val displayId = 1 // not DEFAULT_DISPLAY
        val info = createActivityTransition(TRANSIT_OPEN, taskId = 1, displayId)

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verify(bubbleData, never()).setExpanded(false)
    }

    @Test
    fun testOnTransitionReady_openTaskOnAnotherDisplay_doesNotCollapseStack() {
        val displayId = 1 // not DEFAULT_DISPLAY
        val info = createTaskTransition(TRANSIT_OPEN, taskId = 2, displayId)

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verify(bubbleData, never()).setExpanded(false)
    }

    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @Test
    fun testOnTransitionReady_openTaskByBubble_doesNotCollapseStack() {
        val taskInfo = createTaskInfo(taskId = 2)
        bubbleController.stub {
            on { shouldBeAppBubble(taskInfo) } doReturn true // Launched by another bubble.
        }
        val info = createTaskTransition(TRANSIT_OPEN, taskInfo)

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verify(bubbleData, never()).setExpanded(false)
    }

    @Test
    fun testOnTransitionReady_toFront_collapsesStack() {
        val info = createTaskTransition(TRANSIT_TO_FRONT, taskId = 2)

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verify(bubbleData).setExpanded(false)
    }

    @Test
    fun testOnTransitionReady_noTaskInfoNoActivityInfo_skip() {
        val info = createTaskTransition(TRANSIT_TO_FRONT, taskInfo = null) // Null task info

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verify(bubbleData, never()).setExpanded(false)
    }

    @Test
    fun testOnTransitionReady_noTaskId_skip(@TestParameter tc: InvalidTaskIdTestCase) {
        transitionObserver.onTransitionReady(mock(), tc.info, mock(), mock())

        verify(bubbleData, never()).setExpanded(false)
    }

    @Test
    fun testOnTransitionReady_notOpening_skip(@TestParameter tc: TransitNotOpeningTestCase) {
        transitionObserver.onTransitionReady(mock(), tc.info, mock(), mock())

        verify(bubbleData, never()).setExpanded(false)
        verifyNoInteractions(splitScreenController)
    }

    @Test
    fun testOnTransitionReady_stackAnimating_skip() {
        bubbleController.stub {
            on { isStackAnimating } doReturn true // Stack is animating
        }
        val info = createTaskTransition(TRANSIT_OPEN, taskId = 2)

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verify(bubbleData, never()).setExpanded(false)
    }

    @Test
    fun testOnTransitionReady_stackNotExpanded_skip() {
        bubbleData.stub {
            on { isExpanded } doReturn false // Stack is not expanded
        }
        val info = createTaskTransition(TRANSIT_TO_FRONT, taskId = 2)

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verify(bubbleData, never()).setExpanded(false)
    }

    @Test
    fun testOnTransitionReady_noSelectedBubble_skip() {
        bubbleData.stub {
            on { selectedBubble } doReturn null // No selected bubble
        }
        val info = createTaskTransition(TRANSIT_OPEN, taskId = 2)

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verify(bubbleData, never()).setExpanded(false)
    }

    @Test
    fun testOnTransitionReady_openingMatchesExpanded_skip() {
        // What's moving to front is the same as the opened bubble.
        val info = createTaskTransition(TRANSIT_TO_FRONT, taskId = 1)

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verify(bubbleData, never()).setExpanded(false)
    }

    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE, FLAG_ENABLE_ENTER_SPLIT_REMOVE_BUBBLE)
    @Test
    fun testOnTransitionReady_bubbleMovingToSplit_removeBubble() {
        val taskOrganizer = mock<ShellTaskOrganizer>()
        val taskViewTaskController = mock<TaskViewTaskController> {
            on { this.taskOrganizer } doReturn taskOrganizer
        }
        val taskView = mock<TaskView> {
            on { controller } doReturn taskViewTaskController
        }
        bubble.stub {
            on { this.taskView } doReturn taskView
        }
        bubbleData.stub {
            on { getBubbleInStackWithTaskId(bubble.taskId) } doReturn bubble
        }
        splitScreenController.stub {
            on { isTaskRootOrStageRoot(10) } doReturn true
        }
        val taskInfo =
            createTaskInfo(taskId = 1).apply {
                this.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_MULTI_WINDOW
                this.parentTaskId = 10
            }
        val info = createTaskTransition(TRANSIT_TO_FRONT, taskInfo)

        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        // Check that we remove the taskView
        verify(taskViewTaskController).notifyTaskRemovalStarted(taskInfo)
        val wctCaptor = argumentCaptor<WindowContainerTransaction>()
        // And clean up bubble specific overrides on a task
        verify(taskOrganizer).applyTransaction(wctCaptor.capture())
        verifyExitBubbleTransaction(
            wctCaptor.firstValue,
            taskInfo.token.asBinder(), /* captionInsetsOwner */
            null,
        )
    }

    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE, FLAG_ENABLE_ENTER_SPLIT_REMOVE_BUBBLE)
    @Test
    fun testOnTransitionReady_noBubbles_doesNotCheckForSplitState() {
        bubbleData.stub {
            on { hasBubbles() } doReturn false
        }
        val info = createTaskTransition(TRANSIT_TO_FRONT, taskId = 1)
        transitionObserver.onTransitionReady(mock(), info, mock(), mock())

        verifyNoInteractions(splitScreenController)
    }

    // Transits that aren't opening.
    enum class TransitNotOpeningTestCase(
        @TransitionType private val changeType: Int,
        private val taskId: Int,
    ) {
        CHANGE(TRANSIT_CHANGE, taskId = 2),
        CLOSE(TRANSIT_CLOSE, taskId = 3),
        BACK(TRANSIT_TO_BACK, taskId = 4);

        val info: TransitionInfo
            get() = createTaskTransition(changeType, taskId)
    }

    // Invalid task id.
    enum class InvalidTaskIdTestCase(
        private val transitionCreator: (changeType: Int, taskId: Int) -> TransitionInfo,
    ) {
        ACTIVITY_TRANSITION(transitionCreator = ::createActivityTransition),
        TASK_TRANSITION(transitionCreator = ::createTaskTransition);

        val info: TransitionInfo
            get() = transitionCreator(TRANSIT_OPEN, INVALID_TASK_ID)
    }

    companion object {
        private val COMPONENT = ComponentName("com.example.app", "com.example.app.MainActivity")

        private fun createTaskTransition(
            @TransitionType changeType: Int,
            taskId: Int,
            displayId: Int = DEFAULT_DISPLAY_ID,
        ) = createTaskTransition(changeType, taskInfo = createTaskInfo(taskId), displayId)

        private fun createTaskTransition(
            @TransitionType changeType: Int,
            taskInfo: ActivityManager.RunningTaskInfo?,
            displayId: Int = DEFAULT_DISPLAY_ID,
        ) = TransitionInfoBuilder(TRANSIT_OPEN, displayId = displayId)
            .addChange(changeType, taskInfo)
            .build()

        private fun createActivityTransition(
            @TransitionType changeType: Int,
            taskId: Int,
            displayId: Int = DEFAULT_DISPLAY_ID,
        ) = TransitionInfoBuilder(TRANSIT_OPEN, displayId = displayId)
            .addChange(changeType, ActivityTransitionInfo(COMPONENT, taskId))
            .build()

        private fun createTaskInfo(taskId: Int) = ActivityManager.RunningTaskInfo().apply {
            this.taskId = taskId
            this.token = MockToken().token()
            this.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
        }
    }
}
