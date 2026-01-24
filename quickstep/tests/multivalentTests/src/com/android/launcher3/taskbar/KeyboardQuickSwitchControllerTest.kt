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

package com.android.launcher3.taskbar

import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.os.Process
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.Display.DEFAULT_DISPLAY
import android.window.RemoteTransition
import androidx.test.core.app.ApplicationProvider
import com.android.launcher3.Flags.FLAG_ENABLE_ALT_TAB_KQS_FLATENNING
import com.android.launcher3.Flags.FLAG_ENABLE_ALT_TAB_KQS_ON_CONNECTED_DISPLAYS
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnMainSync
import com.android.launcher3.taskbar.rules.AllTaskbarSandboxModules
import com.android.launcher3.taskbar.rules.MockedRecentsModelHelper
import com.android.launcher3.taskbar.rules.MockedRecentsModelTestRule
import com.android.launcher3.taskbar.rules.SandboxParams
import com.android.launcher3.taskbar.rules.TaskbarSandboxComponent
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.android.launcher3.util.TestUtil.getOnUiThread
import com.android.quickstep.RecentsModel
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.util.DesktopTask
import com.android.quickstep.util.GroupTask
import com.android.quickstep.util.SingleTask
import com.android.quickstep.util.SlideInRemoteTransition
import com.android.quickstep.util.SplitTask
import com.android.systemui.shared.recents.model.Task
import com.android.wm.shell.desktopmode.IDesktopTaskListener
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource
import com.android.wm.shell.shared.desktopmode.DesktopTaskToFrontReason
import com.android.wm.shell.shared.split.SplitBounds
import com.android.wm.shell.shared.split.SplitScreenConstants
import com.google.common.truth.Truth.assertThat
import dagger.BindsInstance
import dagger.Component
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelTablet2023"])
@Ignore("b/413540825")
class KeyboardQuickSwitchControllerTest {
    private var systemUiProxySpy: SystemUiProxy? = null
    private var desktopTaskListener: IDesktopTaskListener? = null
    private val mockRecentsModelHelper: MockedRecentsModelHelper = MockedRecentsModelHelper()
    private val taskIdCaptor = argumentCaptor<Int>()
    private val transitionCaptor = argumentCaptor<RemoteTransition>()

    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()
    @get:Rule(order = 1)
    val context =
        TaskbarWindowSandboxContext.create(
            SandboxParams(
                {
                    spy(SystemUiProxy(ApplicationProvider.getApplicationContext())) { proxy ->
                        systemUiProxySpy = proxy
                        doAnswer { desktopTaskListener = it.getArgument(0) }
                            .whenever(proxy)
                            .setDesktopTaskListener(anyOrNull())
                    }
                },
                builderBase =
                    DaggerKeyboardQuickSwitchControllerComponent.builder()
                        .bindRecentsModel(mockRecentsModelHelper.mockRecentsModel),
            )
        )

    @get:Rule(order = 2) val recentsModel = MockedRecentsModelTestRule(mockRecentsModelHelper)
    @get:Rule(order = 3) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    @InjectController lateinit var keyboardQuickSwitchController: KeyboardQuickSwitchController

    private val isKqsShown: Boolean
        get() = getOnUiThread { keyboardQuickSwitchController.isShown }

    private val shownTaskIds: List<Int>
        get() = getOnUiThread { keyboardQuickSwitchController.shownTaskIds() }

    @Test
    fun noRecentTasks_noShownTaskIds() {
        triggerAltTab()

        assertThat(isKqsShown).isTrue()
        assertThat(shownTaskIds).isEmpty()
    }

    @Test
    fun onlySingleTasksPresent_shouldShowAllTaskIds() {
        updateRecentsModel(
            listOf(createSingleTask(PREVIOUS_TASK_ID), createSingleTask(RUNNING_TASK_ID))
        )

        triggerAltTab()

        assertThat(isKqsShown).isTrue()
        assertThat(shownTaskIds).containsExactly(RUNNING_TASK_ID, PREVIOUS_TASK_ID).inOrder()
    }

    @Test
    fun onlyDesktopTasksPresent_shouldShowAllTaskIds() {
        updateRecentsModel(listOf(createDesktopTask(listOf(RUNNING_TASK_ID, PREVIOUS_TASK_ID))))
        enableDesktopMode()

        triggerAltTab()

        assertThat(isKqsShown).isTrue()
        assertThat(shownTaskIds).containsExactly(RUNNING_TASK_ID, PREVIOUS_TASK_ID).inOrder()
    }

    @Test
    @DisableFlags(FLAG_ENABLE_ALT_TAB_KQS_FLATENNING)
    fun singleAndDesktopTasksPresent_notOnDesktopWithFlatenningOff_onlyShowSingleTaskIds() {
        updateRecentsModel(
            listOf(
                createDesktopTask(listOf(PREVIOUS_TASK_ID, OLDEST_TASK_ID)),
                createSingleTask(RUNNING_TASK_ID),
            )
        )

        triggerAltTab()

        assertThat(isKqsShown).isTrue()
        assertThat(shownTaskIds).containsExactly(RUNNING_TASK_ID)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_ALT_TAB_KQS_FLATENNING)
    fun singleAndDesktopTasksPresent_onDesktopWithFlatenningOff_onlyShowDesktopTaskIds() {
        updateRecentsModel(
            listOf(
                createDesktopTask(listOf(RUNNING_TASK_ID, OLDEST_TASK_ID)),
                createSingleTask(PREVIOUS_TASK_ID),
            )
        )
        enableDesktopMode()

        triggerAltTab()

        assertThat(isKqsShown).isTrue()
        assertThat(shownTaskIds).containsExactly(RUNNING_TASK_ID, OLDEST_TASK_ID).inOrder()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_ALT_TAB_KQS_FLATENNING)
    fun singleAndDesktopTasksPresent_onDesktopWithFlatenningOn_showAllTaskIds() {
        updateRecentsModel(
            listOf(
                createDesktopTask(listOf(RUNNING_TASK_ID, OLDEST_TASK_ID)),
                createSingleTask(PREVIOUS_TASK_ID),
            )
        )
        enableDesktopMode()

        triggerAltTab()

        assertThat(isKqsShown).isTrue()
        assertThat(shownTaskIds)
            .containsExactly(RUNNING_TASK_ID, PREVIOUS_TASK_ID, OLDEST_TASK_ID)
            .inOrder()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_ALT_TAB_KQS_FLATENNING)
    fun singleAndDesktopTasksPresent_notOnDesktopWithFlatenningOn_showAllTaskIds() {
        updateRecentsModel(
            listOf(
                createDesktopTask(listOf(PREVIOUS_TASK_ID, OLDEST_TASK_ID)),
                createSingleTask(RUNNING_TASK_ID),
            )
        )

        triggerAltTab()

        assertThat(isKqsShown).isTrue()
        assertThat(shownTaskIds)
            .containsExactly(RUNNING_TASK_ID, PREVIOUS_TASK_ID, OLDEST_TASK_ID)
            .inOrder()
    }

    @Test
    @DisableFlags(FLAG_ENABLE_ALT_TAB_KQS_FLATENNING, FLAG_ENABLE_ALT_TAB_KQS_ON_CONNECTED_DISPLAYS)
    fun multipleDesktopTasksPresent_onDesktopWithCdFlagOff_onlyShowCurrentDesktopTasks() {
        updateRecentsModel(
            listOf(
                createDesktopTask(listOf(RUNNING_TASK_ID)),
                createDesktopTask(listOf(PREVIOUS_TASK_ID)),
            )
        )
        enableDesktopMode()

        triggerAltTab()

        assertThat(isKqsShown).isTrue()
        assertThat(shownTaskIds).containsExactly(RUNNING_TASK_ID)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_ALT_TAB_KQS_FLATENNING)
    @EnableFlags(FLAG_ENABLE_ALT_TAB_KQS_ON_CONNECTED_DISPLAYS)
    fun multipleDesktopTasksPresent_onDesktopWithCdFlagON_showAllDesktopTasks() {
        updateRecentsModel(
            listOf(
                createDesktopTask(listOf(RUNNING_TASK_ID)),
                createDesktopTask(listOf(PREVIOUS_TASK_ID)),
            )
        )
        enableDesktopMode()

        triggerAltTab()

        assertThat(isKqsShown).isTrue()
        assertThat(shownTaskIds).containsExactly(RUNNING_TASK_ID, PREVIOUS_TASK_ID).inOrder()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_ALT_TAB_KQS_FLATENNING)
    fun splitAndSingleTaskPresent_withFlatenningOn_shouldSortTaskIds() {
        updateRecentsModel(
            listOf(
                createSplitTask(OLDEST_TASK_ID to RUNNING_TASK_ID),
                createSingleTask(PREVIOUS_TASK_ID),
            )
        )

        triggerAltTab()

        // Although single task is more recent than one of the split tasks, the split tasks should
        // be together. Furthermore, the shownTaskIds returns left split task first.
        assertThat(shownTaskIds)
            .containsExactly(OLDEST_TASK_ID, RUNNING_TASK_ID, PREVIOUS_TASK_ID)
            .inOrder()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_ALT_TAB_KQS_FLATENNING)
    fun launchDesktopApp_notOnDesktop_shouldCallSysUIProxyToStartSpecificApp() {
        val deskId = 1
        updateRecentsModel(listOf(createDesktopTask(listOf(PREVIOUS_TASK_ID), deskId)))

        triggerAltTabAndLaunchFocusedTask()

        val deskIdCaptor = argumentCaptor<Int>()
        verify(systemUiProxySpy)?.activateDesk(deskIdCaptor.capture(), transitionCaptor.capture())
        assertThat(deskIdCaptor.firstValue).isEqualTo(deskId)
        assertThat(transitionCaptor.firstValue.remoteTransition)
            .isInstanceOf(SlideInRemoteTransition::class.java)

        verify(systemUiProxySpy)
            ?.showDesktopApp(taskIdCaptor.capture(), eq(null), eq(DesktopTaskToFrontReason.ALT_TAB))
        assertThat(taskIdCaptor.firstValue).isEqualTo(PREVIOUS_TASK_ID)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_ALT_TAB_KQS_FLATENNING)
    fun launchSingleApp_onDesktop_shouldCallSysUIProxyToMoveToFullscreen() {
        updateRecentsModel(listOf(createSingleTask(PREVIOUS_TASK_ID)))
        enableDesktopMode()

        triggerAltTabAndLaunchFocusedTask()

        verify(systemUiProxySpy)
            ?.moveToFullscreen(
                taskIdCaptor.capture(),
                eq(DesktopModeTransitionSource.KEYBOARD_SHORTCUT),
                transitionCaptor.capture(),
            )
        assertThat(taskIdCaptor.firstValue).isEqualTo(PREVIOUS_TASK_ID)
        assertThat(transitionCaptor.firstValue.remoteTransition)
            .isInstanceOf(SlideInRemoteTransition::class.java)
    }

    private fun createSingleTask(taskId: Int) = SingleTask(createTask(taskId))

    private fun createSplitTask(taskIds: Pair<Int, Int>) =
        SplitTask(
            createTask(taskIds.first),
            createTask(taskIds.second),
            SplitBounds(
                /* leftTopBounds = */ Rect(),
                /* rightBottomBounds = */ Rect(),
                /* leftTopTaskId = */ 1,
                /* rightBottomTaskId = */ 2,
                /* snapPosition = */ SplitScreenConstants.SNAP_TO_2_50_50,
            ),
        )

    private fun createDesktopTask(taskIds: List<Int>, deskId: Int = 0) =
        DesktopTask(deskId, DEFAULT_DISPLAY, taskIds.map { createTask(it) })

    private fun enableDesktopMode() {
        whenever(DesktopVisibilityController.INSTANCE[context].isInDesktopMode(any()))
            .thenReturn(true)
    }

    /*
     * Returns a task with the given ID and a fake package name.
     *
     * Note: the task ID is added to last active time, thus higher task ID indicates a more recent
     * active task.
     */
    private fun createTask(taskId: Int): Task {
        return Task(
            Task.TaskKey(
                taskId,
                0,
                Intent().apply { `package` = "Fake${taskId}" },
                ComponentName("Fake${taskId}", ""),
                Process.myUserHandle().identifier,
                2000L + taskId,
            )
        )
    }

    private fun updateRecentsModel(tasks: List<GroupTask>) {
        recentsModel.updateRecentTasks(tasks)
        runOnMainSync { recentsModel.resolvePendingTaskRequests() }
    }

    private fun triggerAltTab() = runOnMainSync {
        keyboardQuickSwitchController.openQuickSwitchView()
        recentsModel.resolvePendingTaskRequests()
    }

    private fun triggerAltTabAndLaunchFocusedTask() {
        triggerAltTab()
        runOnMainSync { keyboardQuickSwitchController.launchFocusedTask() }
    }

    private companion object {
        const val OLDEST_TASK_ID = 1
        const val PREVIOUS_TASK_ID = 2
        const val RUNNING_TASK_ID = 3
    }
}

/** KeyboardQuickSwitchControllerComponent used to bind the RecentsModel. */
@LauncherAppSingleton
@Component(modules = [AllTaskbarSandboxModules::class])
interface KeyboardQuickSwitchControllerComponent : TaskbarSandboxComponent {

    @Component.Builder
    interface Builder : TaskbarSandboxComponent.Builder {
        @BindsInstance fun bindRecentsModel(model: RecentsModel): Builder

        override fun build(): KeyboardQuickSwitchControllerComponent
    }
}
