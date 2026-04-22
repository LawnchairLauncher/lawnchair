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

package com.android.wm.shell.bubbles

import android.app.ActivityManager
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.content.ComponentName
import android.content.Context
import android.platform.test.flag.junit.FlagsParameterization
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags.FLAG_EXCLUDE_TASK_FROM_RECENTS
import com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper
import com.android.wm.shell.taskview.TaskView
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class BubbleTaskViewTest(flags: FlagsParameterization) {

    @get:Rule
    val setFlagsRule = SetFlagsRule(flags)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val componentName = ComponentName(context, "TestClass")
    private val taskView = mock<TaskView>()
    private val bubbleTaskView = BubbleTaskView(taskView, directExecutor())

    @Test
    fun onTaskCreated_updatesState() {
        bubbleTaskView.listener.onTaskCreated(123, componentName)

        assertThat(bubbleTaskView.taskId).isEqualTo(123)
        assertThat(bubbleTaskView.componentName).isEqualTo(componentName)
        assertThat(bubbleTaskView.isCreated).isTrue()
    }

    @Test
    fun onTaskCreated_callsDelegateListener() {
        var actualTaskId = -1
        var actualComponentName: ComponentName? = null
        val delegateListener = object : TaskView.Listener {
            override fun onTaskCreated(taskId: Int, name: ComponentName) {
                actualTaskId = taskId
                actualComponentName = name
            }
        }
        bubbleTaskView.delegateListener = delegateListener

        bubbleTaskView.listener.onTaskCreated(123 /* taskId */, componentName)

        assertThat(actualTaskId).isEqualTo(123)
        assertThat(actualComponentName).isEqualTo(componentName)
    }

    @Test
    fun cleanup_invalidTaskId_removesTask() {
        bubbleTaskView.cleanup()
        verify(taskView).removeTask()
    }

    @Test
    fun cleanup_validTaskId_removesTask() {
        bubbleTaskView.listener.onTaskCreated(123 /* taskId */, componentName)

        bubbleTaskView.cleanup()

        verify(taskView).removeTask()
    }

    @Test
    fun cleanup_noneFullscreenTask_removesTask() {
        bubbleTaskView.listener.onTaskCreated(123 /* taskId */, componentName)

        bubbleTaskView.cleanup()

        verify(taskView, never()).unregisterTask()
        verify(taskView).removeTask()
    }

    @Test
    fun cleanup_fullscreenTask_removesOrUnregistersTask() {
        val fullScreenTaskInfo = ActivityManager.RunningTaskInfo().apply {
            configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
        }
        taskView.stub {
            on { taskInfo } doReturn fullScreenTaskInfo
        }
        bubbleTaskView.listener.onTaskCreated(123 /* taskId */, componentName)

        bubbleTaskView.cleanup()

        if (BubbleAnythingFlagHelper.enableCreateAnyBubbleWithForceExcludedFromRecents()) {
            verify(taskView).unregisterTask()
            verify(taskView, never()).removeTask()
        } else {
            verify(taskView, never()).unregisterTask()
            verify(taskView).removeTask()
        }
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams() = FlagsParameterization.allCombinationsOf(
            FLAG_ENABLE_CREATE_ANY_BUBBLE,
            FLAG_EXCLUDE_TASK_FROM_RECENTS,
        )
    }
}
