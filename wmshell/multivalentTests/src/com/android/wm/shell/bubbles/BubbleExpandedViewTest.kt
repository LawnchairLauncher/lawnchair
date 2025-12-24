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

import android.content.ComponentName
import android.content.Context
import android.platform.test.annotations.EnableFlags
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_ANYTHING
import com.android.wm.shell.MockToken
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.bubbles.util.BubbleTestUtils.verifyEnterBubbleTransaction
import com.android.wm.shell.taskview.TaskView
import com.android.wm.shell.taskview.TaskViewController
import com.android.wm.shell.taskview.TaskViewTaskController
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify


/**
 * Tests for [BubbleExpandedView].
 *
 * Build/Install/Run:
 *  atest WMShellRobolectricTests:BubbleExpandedViewTest (on host)
 *  atest WMShellMultivalentTestsOnDevice:BubbleExpandedViewTest (on device)
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleExpandedViewTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val componentName = ComponentName(context, "TestClass")

    private val taskOrganizer = mock<ShellTaskOrganizer>()
    private val taskViewTaskToken: WindowContainerToken = MockToken.token()
    private var taskViewController = mock<TaskViewController>()
    private val taskViewTaskController = mock<TaskViewTaskController> {
        on { taskOrganizer } doReturn taskOrganizer
        on { taskToken } doReturn taskViewTaskToken
    }

    private lateinit var taskView: TaskView
    private lateinit var bubbleTaskView: BubbleTaskView
    private lateinit var expandedView: BubbleExpandedView

    @Before
    fun setUp() {
        ProtoLog.REQUIRE_PROTOLOGTOOL = false
        ProtoLog.init()

        taskView = TaskView(context, taskViewController, taskViewTaskController)
        bubbleTaskView = BubbleTaskView(taskView, directExecutor())

        expandedView = BubbleExpandedView(context).apply {
            initialize(
                mock<BubbleExpandedViewManager>(),
                mock<BubbleStackView>(),
                mock<BubblePositioner>(),
                false /* isOverflow */,
                bubbleTaskView,
            )
            setAnimating(true) // Skips setContentVisibility for testing.
        }
    }

    @Test
    fun getTaskId_onTaskCreated_returnsCorrectTaskId() {
        bubbleTaskView.listener.onTaskCreated(123 /* taskId */, componentName)

        assertThat(expandedView.taskId).isEqualTo(123)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_BUBBLE_ANYTHING)
    fun onTaskCreated_appliesWctToEnterBubble() {
        bubbleTaskView.listener.onTaskCreated(123 /* taskId */, componentName)

        val wctCaptor = argumentCaptor<WindowContainerTransaction>()
        verify(taskOrganizer).applyTransaction(wctCaptor.capture())
        val wct = wctCaptor.lastValue
        verifyEnterBubbleTransaction(
            wct,
            taskViewTaskToken.asBinder(),
            isAppBubble = false,
        )
    }
}
