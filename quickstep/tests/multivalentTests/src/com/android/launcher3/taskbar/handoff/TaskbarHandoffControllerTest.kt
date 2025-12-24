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

package com.android.launcher3.taskbar.handoff

import android.animation.AnimatorTestRule
import android.companion.Flags
import android.companion.datatransfer.continuity.RemoteTask
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.TRANSIENT
import com.android.launcher3.taskbar.rules.TaskbarModeRule.TaskbarMode
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.verify

/** Tests for [TaskbarHandoffController]. */
@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelTablet2023"])
@EnableFlags(android.companion.Flags.FLAG_ENABLE_TASK_CONTINUITY)
class TaskbarHandoffControllerTest {

    @get:Rule(order = 1) val setFlagsRule = SetFlagsRule()
    @get:Rule(order = 2) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 3) val taskbarModeRule = TaskbarModeRule(context)
    @get:Rule(order = 4) val animatorTestRule = AnimatorTestRule(this)
    @get:Rule(order = 5) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)
    @InjectController lateinit var controller: TaskbarHandoffController

    @Test
    @TaskbarMode(TRANSIENT)
    @EnableFlags(Flags.FLAG_ENABLE_TASK_CONTINUITY)
    fun init_registersListener() {
        verify(context.taskContinuityManagerMock).registerRemoteTaskListener(any(), any())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TASK_CONTINUITY)
    fun onRemoteTasksChanged_updatesSuggestions() {
        val task1 = createRemoteTask(1, "Task 1")
        val task2 = createRemoteTask(2, "Task 2")

        controller.onRemoteTasksChanged(listOf(task1, task2))

        val suggestions = controller.suggestions
        assertThat(suggestions).hasSize(2)
        assertThat(suggestions.map { it.deviceId }).containsExactly(1, 2).inOrder()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TASK_CONTINUITY)
    fun getSuggestions_afterUpdate_returnsSuggestions() {
        assertThat(controller.suggestions).isEmpty()

        val task = createRemoteTask(1, "Task")

        controller.onRemoteTasksChanged(listOf(task))

        assertThat(controller.suggestions).hasSize(1)
        assertThat(controller.suggestions.first().deviceId).isEqualTo(1)

        controller.onDestroy()

        assertThat(controller.suggestions).isEmpty()
    }

    private fun createRemoteTask(deviceId: Int, label: String): RemoteTask {
        return RemoteTask.Builder(1).setDeviceId(deviceId).setLabel(label).build()
    }
}
