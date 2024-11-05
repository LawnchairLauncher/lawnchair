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

import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.taskview.TaskView

import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleTaskViewTest {

    private lateinit var bubbleTaskView: BubbleTaskView
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var taskView: TaskView

    @Before
    fun setUp() {
        taskView = mock()
        bubbleTaskView = BubbleTaskView(taskView, directExecutor())
    }

    @Test
    fun onTaskCreated_updatesState() {
        val componentName = ComponentName(context, "TestClass")
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

        val componentName = ComponentName(context, "TestClass")
        bubbleTaskView.listener.onTaskCreated(123, componentName)

        assertThat(actualTaskId).isEqualTo(123)
        assertThat(actualComponentName).isEqualTo(componentName)
    }

    @Test
    fun cleanup_invalidTaskId_doesNotRemoveTask() {
        bubbleTaskView.cleanup()
        verify(taskView, never()).removeTask()
    }

    @Test
    fun cleanup_validTaskId_removesTask() {
        val componentName = ComponentName(context, "TestClass")
        bubbleTaskView.listener.onTaskCreated(123, componentName)

        bubbleTaskView.cleanup()
        verify(taskView).removeTask()
    }
}
