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

package com.android.quickstep

import android.content.Intent
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.filters.SmallTest
import com.android.app.displaylib.DisplayRepository
import com.android.app.displaylib.fakes.FakePerDisplayRepository
import com.android.launcher3.LauncherState
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.statemanager.StatefulActivity
import com.android.launcher3.taskbar.TaskbarInteractor
import com.android.launcher3.taskbar.TaskbarManager
import com.android.launcher3.taskbar.TaskbarUIController
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.TestDispatcherProvider
import com.android.quickstep.OverviewCommandHelper.CommandInfo
import com.android.quickstep.OverviewCommandHelper.CommandInfo.CommandStatus
import com.android.quickstep.OverviewCommandHelper.CommandType
import com.android.quickstep.OverviewCommandHelper.Companion.TOGGLE_PREVIOUS_TIMEOUT_MS
import com.android.quickstep.views.KeyboardFocusTask
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.RecentsViewUtils
import com.android.quickstep.views.TaskView
import com.android.window.flags.Flags as WindowFlags
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.spy
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(LauncherMultivalentJUnit::class)
@OptIn(ExperimentalCoroutinesApi::class)
class OverviewCommandHelperTest {

    @get:Rule val mSetFlagsRule: SetFlagsRule = SetFlagsRule()

    private lateinit var sut: OverviewCommandHelper
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)

    private var pendingCallbacksWithDelays = mutableListOf<Long>()

    private val displayRepository: DisplayRepository = mock()
    private val executeCommandDisplayIds = mutableListOf<Int>()

    private val recentView: RecentsView<*, *> = mock()
    private val stateManager: StateManager<LauncherState, StatefulActivity<LauncherState>> = mock()
    private val containerInterface: BaseActivityInterface<LauncherState, QuickstepLauncher> = mock()
    private val taskAnimationManager: TaskAnimationManager = mock()
    private val touchInteractionService: TouchInteractionService = mock()
    private val taskbarManager: TaskbarManager = mock()
    private val taskbarUIController: TaskbarUIController = mock()
    private val taskbarInteractor: TaskbarInteractor = TaskbarInteractor(taskbarUIController)
    private val launcher: QuickstepLauncher = mock()
    private var elapsedRealtime = 100L
    private val systemUiProxy: SystemUiProxy = mock()

    private fun setupDefaultDisplay() {
        whenever(displayRepository.displayIds).thenReturn(MutableStateFlow(setOf(DEFAULT_DISPLAY)))
    }

    private fun setupMultipleDisplays() {
        whenever(displayRepository.displayIds)
            .thenReturn(MutableStateFlow(setOf(DEFAULT_DISPLAY, 1)))
    }

    @Suppress("UNCHECKED_CAST")
    @Before
    fun setup() {
        setupDefaultDisplay()

        val overviewComponentObserver = mock<OverviewComponentObserver>()
        whenever(overviewComponentObserver.getContainerInterface(any()))
            .thenReturn(containerInterface)
        whenever(overviewComponentObserver.getHomeIntent(any())).thenReturn(mock<Intent>())
        whenever(recentView.getStateManager()).thenReturn(stateManager)
        whenever(containerInterface.switchToRecentsIfVisible(any())).thenReturn(true)
        whenever(launcher.getOverviewPanel<RecentsView<*, *>>()).thenReturn(recentView)
        whenever(containerInterface.createdContainer).thenReturn(launcher)
        whenever(containerInterface.taskbarInteractor).thenReturn(taskbarInteractor)
        whenever(taskbarInteractor.launchFocusedTask().get())
            .thenReturn(REQUESTED_KEYBOARD_FOCUS_TASK_IDS)
        whenever(taskbarManager.getUIControllerForDisplay(anyInt())).thenReturn(taskbarUIController)

        sut =
            spy(
                OverviewCommandHelper(
                    touchInteractionService = touchInteractionService,
                    overviewComponentObserver = overviewComponentObserver,
                    dispatcherProvider = TestDispatcherProvider(dispatcher),
                    displayRepository = displayRepository,
                    taskbarManager = taskbarManager,
                    taskAnimationManagerRepository =
                        FakePerDisplayRepository<TaskAnimationManager> { _ ->
                            taskAnimationManager
                        },
                    elapsedRealtime = ::elapsedRealtime,
                    systemUiProxy = systemUiProxy,
                )
            )
    }

    private fun addCallbackDelay(delayInMillis: Long = 0) {
        pendingCallbacksWithDelays.add(delayInMillis)
    }

    private fun mockExecuteCommand() {
        doAnswer { invocation ->
                val pendingCallback = invocation.arguments[1] as () -> Unit

                val delayInMillis = pendingCallbacksWithDelays.removeFirstOrNull()
                if (delayInMillis != null) {
                    runBlocking {
                        testScope.backgroundScope.launch {
                            delay(delayInMillis)
                            pendingCallback.invoke()
                        }
                    }
                }
                val commandInfo = invocation.arguments[0] as CommandInfo
                executeCommandDisplayIds.add(commandInfo.displayId)
                delayInMillis == null // if no callback to execute, returns success
            }
            .`when`(sut)
            .executeCommand(any<CommandInfo>(), any())
    }

    @Test
    fun whenFirstCommandIsAdded_executeCommandImmediately() =
        testScope.runTest {
            mockExecuteCommand()
            // Add command to queue
            val commandInfo: CommandInfo = sut.addCommand(CommandType.HOME)!!
            assertThat(commandInfo.status).isEqualTo(CommandStatus.IDLE)
            runCurrent()
            assertThat(commandInfo.status).isEqualTo(CommandStatus.COMPLETED)
        }

    @Test
    fun whenFirstCommandIsAdded_executeCommandImmediately_WithCallbackDelay() =
        testScope.runTest {
            mockExecuteCommand()
            addCallbackDelay(100)

            // Add command to queue
            val commandType = CommandType.HOME
            val commandInfo: CommandInfo = sut.addCommand(commandType)!!
            assertThat(commandInfo.status).isEqualTo(CommandStatus.IDLE)

            runCurrent()
            assertThat(commandInfo.status).isEqualTo(CommandStatus.PROCESSING)

            advanceTimeBy(200L)
            assertThat(commandInfo.status).isEqualTo(CommandStatus.COMPLETED)
        }

    @Test
    fun whenFirstCommandIsPendingCallback_NextCommandWillWait() =
        testScope.runTest {
            mockExecuteCommand()
            // Add command to queue
            addCallbackDelay(100)
            val commandType1 = CommandType.HOME
            val commandInfo1: CommandInfo = sut.addCommand(commandType1)!!
            assertThat(commandInfo1.status).isEqualTo(CommandStatus.IDLE)

            addCallbackDelay(100)
            val commandType2 = CommandType.SHOW_ALT_TAB
            val commandInfo2: CommandInfo = sut.addCommand(commandType2)!!
            assertThat(commandInfo2.status).isEqualTo(CommandStatus.IDLE)

            runCurrent()
            assertThat(commandInfo1.status).isEqualTo(CommandStatus.PROCESSING)
            assertThat(commandInfo2.status).isEqualTo(CommandStatus.IDLE)

            advanceTimeBy(101L)
            assertThat(commandInfo1.status).isEqualTo(CommandStatus.COMPLETED)
            assertThat(commandInfo2.status).isEqualTo(CommandStatus.PROCESSING)

            advanceTimeBy(101L)
            assertThat(commandInfo2.status).isEqualTo(CommandStatus.COMPLETED)
        }

    @Test
    fun whenCommandTakesTooLong_TriggerTimeout_AndExecuteNextCommand() =
        testScope.runTest {
            mockExecuteCommand()
            // Add command to queue
            addCallbackDelay(QUEUE_TIMEOUT)
            val commandType1 = CommandType.HOME
            val commandInfo1: CommandInfo = sut.addCommand(commandType1)!!
            assertThat(commandInfo1.status).isEqualTo(CommandStatus.IDLE)

            addCallbackDelay(100)
            val commandType2 = CommandType.SHOW_ALT_TAB
            val commandInfo2: CommandInfo = sut.addCommand(commandType2)!!
            assertThat(commandInfo2.status).isEqualTo(CommandStatus.IDLE)

            runCurrent()
            assertThat(commandInfo1.status).isEqualTo(CommandStatus.PROCESSING)
            assertThat(commandInfo2.status).isEqualTo(CommandStatus.IDLE)

            advanceTimeBy(QUEUE_TIMEOUT)
            assertThat(commandInfo1.status).isEqualTo(CommandStatus.CANCELED)
            assertThat(commandInfo2.status).isEqualTo(CommandStatus.PROCESSING)

            advanceTimeBy(101)
            assertThat(commandInfo2.status).isEqualTo(CommandStatus.COMPLETED)
        }

    @Test
    fun whenAllDisplaysCommandIsAdded_singleCommandProcessedForDefaultDisplay() =
        testScope.runTest {
            mockExecuteCommand()
            executeCommandDisplayIds.clear()
            // Add command to queue
            val commandInfo: CommandInfo = sut.addCommandsForAllDisplays(CommandType.HOME)!!
            assertThat(commandInfo.status).isEqualTo(CommandStatus.IDLE)
            runCurrent()
            assertThat(commandInfo.status).isEqualTo(CommandStatus.COMPLETED)
            assertThat(executeCommandDisplayIds).containsExactly(DEFAULT_DISPLAY)
        }

    @Test
    fun whenAllDisplaysCommandIsAdded_multipleCommandsProcessedForMultipleDisplays() =
        testScope.runTest {
            mockExecuteCommand()
            setupMultipleDisplays()
            executeCommandDisplayIds.clear()
            // Add command to queue
            val commandInfo: CommandInfo = sut.addCommandsForAllDisplays(CommandType.HOME)!!
            assertThat(commandInfo.status).isEqualTo(CommandStatus.IDLE)
            runCurrent()
            assertThat(commandInfo.status).isEqualTo(CommandStatus.COMPLETED)
            assertThat(executeCommandDisplayIds)
                .containsExactly(DEFAULT_DISPLAY, EXTERNAL_DISPLAY_ID)
        }

    @Test
    fun whenAllExceptDisplayCommandIsAdded_otherDisplayProcessed() =
        testScope.runTest {
            mockExecuteCommand()
            setupMultipleDisplays()
            executeCommandDisplayIds.clear()
            // Add command to queue
            val commandInfo: CommandInfo =
                sut.addCommandsForDisplaysExcept(CommandType.HOME, DEFAULT_DISPLAY)!!
            assertThat(commandInfo.status).isEqualTo(CommandStatus.IDLE)
            runCurrent()
            assertThat(commandInfo.status).isEqualTo(CommandStatus.COMPLETED)
            assertThat(executeCommandDisplayIds).containsExactly(EXTERNAL_DISPLAY_ID)
        }

    @Test
    fun whenSingleDisplayCommandIsAdded_thatDisplayIsProcessed() =
        testScope.runTest {
            mockExecuteCommand()
            executeCommandDisplayIds.clear()
            val displayId = 5
            // Add command to queue
            val commandInfo: CommandInfo = sut.addCommand(CommandType.HOME, displayId)!!
            assertThat(commandInfo.status).isEqualTo(CommandStatus.IDLE)
            runCurrent()
            assertThat(commandInfo.status).isEqualTo(CommandStatus.COMPLETED)
            assertThat(executeCommandDisplayIds).containsExactly(displayId)
        }

    @Test
    fun recentViewNotVisible_toggleOverviewPrev_goToOverview() =
        testScope.runTest {
            whenever(containerInterface.getVisibleRecentsView<RecentsView<*, *>>()).thenReturn(null)
            sut.addCommand(CommandType.TOGGLE_OVERVIEW_PREVIOUS)!!
            runCurrent()
            verify(containerInterface).switchToRecentsIfVisible(any())
        }

    @Test
    fun recentViewVisible_toggleOverviewPrev_goToHome() =
        testScope.runTest {
            whenever(containerInterface.getVisibleRecentsView<RecentsView<*, *>>())
                .thenReturn(recentView)
            val commandInfo: CommandInfo = sut.addCommand(CommandType.TOGGLE_OVERVIEW_PREVIOUS)!!

            runCurrent()
            verify(recentView).startHome()

            assertThat(commandInfo.status).isEqualTo(CommandStatus.COMPLETED)
        }

    @Test
    fun recentViewVisible_hasRunningTask_toggleOverviewPrev_goToPrevTask() =
        testScope.runTest {
            whenever(containerInterface.getVisibleRecentsView<RecentsView<*, *>>())
                .thenReturn(recentView)
            val mockTask = mock<TaskView>()
            whenever(recentView.runningTaskView).thenReturn(mockTask)
            val callbackList = RunnableList()
            whenever(mockTask.launchWithAnimation()).thenReturn(callbackList)

            val commandInfo: CommandInfo = sut.addCommand(CommandType.TOGGLE_OVERVIEW_PREVIOUS)!!
            runCurrent()

            verify(mockTask).launchWithAnimation()
            verify(recentView, never()).startHome()
            assertThat(commandInfo.status).isEqualTo(CommandStatus.PROCESSING)

            callbackList.executeAllAndDestroy()
            runCurrent()

            assertThat(commandInfo.status).isEqualTo(CommandStatus.COMPLETED)
        }

    @Test
    fun recentViewVisible_hasRunningTask_toggle() =
        testScope.runTest {
            val callbackList = RunnableList()

            fun getMockTask(vararg elements: Int) =
                mock<TaskView>().apply {
                    whenever(taskIdSet).thenReturn(elements.toSet())
                    whenever(recentView.getTaskViewByTaskIds(elements)).thenReturn(this)
                    whenever(launchWithAnimation()).thenReturn(callbackList)
                }
            val mockTask1 = getMockTask(1, 2, 3)
            val mockTask2 = getMockTask(4, 5)
            val mockTask3 = getMockTask(6, 7)

            // TOGGLE with a runningTaskView should go to nextTaskView
            whenever(containerInterface.getVisibleRecentsView<RecentsView<*, *>>())
                .thenReturn(recentView)
            whenever(recentView.runningTaskView).thenReturn(mockTask1)
            whenever(recentView.nextTaskView).thenReturn(mockTask2)
            sut.addCommand(CommandType.TOGGLE)
            runCurrent()
            verify(mockTask2).launchWithAnimation()
            callbackList.executeAllAndDestroy()

            // Next TOGGLE with runningTaskView will return to previous runningTaskView
            whenever(recentView.runningTaskView).thenReturn(mockTask2)
            whenever(recentView.nextTaskView).thenReturn(mockTask3)
            sut.addCommand(CommandType.TOGGLE)
            runCurrent()
            verify(mockTask1).launchWithAnimation()
            callbackList.executeAllAndDestroy()

            // After TOGGLE_PREVIOUS_TIMEOUT_MS has passed, subsequent TOGGLE will go to
            // nextTaskView again.
            whenever(recentView.runningTaskView).thenReturn(mockTask1)
            whenever(recentView.nextTaskView).thenReturn(mockTask3)
            sut.addCommand(CommandType.TOGGLE)
            elapsedRealtime += TOGGLE_PREVIOUS_TIMEOUT_MS
            runCurrent()
            verify(mockTask3).launchWithAnimation()
        }

    @Test
    fun recentViewVisible_noRunningTask_toggle_goToFirstNonDesktopTaskView() =
        testScope.runTest {
            val firstNonDesktopTaskView = mock<TaskView>()
            val lastDesktopTaskView = mock<TaskView>()
            val previousTaskView = mock<TaskView>()
            val nextTaskView = mock<TaskView>()

            whenever(containerInterface.getVisibleRecentsView<RecentsView<*, *>>())
                .thenReturn(recentView)
            whenever(recentView.runningTaskView).thenReturn(null)
            whenever(recentView.firstNonDesktopTaskView).thenReturn(firstNonDesktopTaskView)
            whenever(recentView.lastDesktopTaskView).thenReturn(lastDesktopTaskView)
            whenever(recentView.previousTaskView).thenReturn(previousTaskView)
            whenever(recentView.nextTaskView).thenReturn(nextTaskView)
            sut.addCommand(CommandType.TOGGLE)
            runCurrent()
            verify(firstNonDesktopTaskView).launchWithAnimation()
        }

    @Test
    fun recentViewVisible_noRunningTask_toggle_goToLastDesktopTaskView() =
        testScope.runTest {
            val lastDesktopTaskView = mock<TaskView>()
            val firstTaskView = mock<TaskView>()
            val previousTaskView = mock<TaskView>()
            val nextTaskView = mock<TaskView>()

            whenever(containerInterface.getVisibleRecentsView<RecentsView<*, *>>())
                .thenReturn(recentView)
            whenever(recentView.runningTaskView).thenReturn(null)
            whenever(recentView.firstNonDesktopTaskView).thenReturn(null)
            whenever(recentView.lastDesktopTaskView).thenReturn(lastDesktopTaskView)
            whenever(recentView.firstTaskView).thenReturn(firstTaskView)
            whenever(recentView.previousTaskView).thenReturn(previousTaskView)
            whenever(recentView.nextTaskView).thenReturn(nextTaskView)
            sut.addCommand(CommandType.TOGGLE)
            runCurrent()
            verify(lastDesktopTaskView).launchWithAnimation()
        }

    @Test
    fun recentViewVisible_hasRunningTask_toggle_goToPreviousTaskView() =
        testScope.runTest {
            val runningTaskView = mock<TaskView>()
            val firstTaskView = mock<TaskView>()
            val previousTaskView = mock<TaskView>()

            whenever(containerInterface.getVisibleRecentsView<RecentsView<*, *>>())
                .thenReturn(recentView)
            whenever(recentView.runningTaskView).thenReturn(runningTaskView)
            whenever(recentView.firstTaskView).thenReturn(firstTaskView)
            whenever(recentView.previousTaskView).thenReturn(previousTaskView)
            whenever(recentView.nextTaskView).thenReturn(null)
            sut.addCommand(CommandType.TOGGLE)
            runCurrent()
            verify(previousTaskView).launchWithAnimation()
        }

    @Test
    fun toggleWithFocus_recentViewNotVisible_goToOverview() =
        testScope.runTest {
            whenever(containerInterface.getVisibleRecentsView<RecentsView<*, *>>()).thenReturn(null)
            sut.addCommand(CommandType.TOGGLE_WITH_FOCUS)!!
            runCurrent()
            verify(containerInterface).switchToRecentsIfVisible(any())
        }

    // TODO(b/385128447): add tests for when a TaskContentView is focused.
    @Test
    fun toggleWithFocus_recentViewVisible_windowTaskFocused_launchFocusedTask() =
        testScope.runTest {
            val mockFocusedTask = mock<TaskView>()
            val mockTaskViewsIterable = mock<RecentsViewUtils.TaskViewsIterable>()

            whenever(containerInterface.getVisibleRecentsView<RecentsView<*, *>>())
                .thenReturn(recentView)
            whenever(mockFocusedTask.isFocused).thenReturn(true)
            whenever(mockTaskViewsIterable.iterator())
                .thenReturn(listOf(mockFocusedTask).iterator())
            whenever(recentView.taskViews).thenReturn(mockTaskViewsIterable)
            sut.addCommand(CommandType.TOGGLE_WITH_FOCUS)!!
            runCurrent()
            verify(mockFocusedTask).launchWithAnimation()
        }

    @Test
    fun toggleWithFocus_recentViewVisible_windowTaskHovered_launchHoveredTask() =
        testScope.runTest {
            val mockFocusedTask = mock<TaskView>()
            val mockTaskViewsIterable = mock<RecentsViewUtils.TaskViewsIterable>()

            whenever(containerInterface.getVisibleRecentsView<RecentsView<*, *>>())
                .thenReturn(recentView)
            whenever(mockFocusedTask.isFocused).thenReturn(false)
            whenever(mockFocusedTask.isHovered).thenReturn(true)
            whenever(mockTaskViewsIterable.iterator())
                .thenReturn(listOf(mockFocusedTask).iterator())
            whenever(recentView.taskViews).thenReturn(mockTaskViewsIterable)
            sut.addCommand(CommandType.TOGGLE_WITH_FOCUS)!!
            runCurrent()
            verify(mockFocusedTask).launchWithAnimation()
        }

    @Test
    fun toggleWithFocus_recentViewVisible_noTaskFocused_launchCurrentPageTaskView() =
        testScope.runTest {
            val mockTask = mock<TaskView>()
            val mockTaskViewsIterable = mock<RecentsViewUtils.TaskViewsIterable>()

            whenever(containerInterface.getVisibleRecentsView<RecentsView<*, *>>())
                .thenReturn(recentView)
            whenever(mockTaskViewsIterable.iterator()).thenReturn(emptyList<TaskView>().iterator())
            whenever(recentView.taskViews).thenReturn(mockTaskViewsIterable)
            whenever(recentView.currentPageTaskView).thenReturn(mockTask)
            sut.addCommand(CommandType.TOGGLE_WITH_FOCUS)!!
            runCurrent()
            verify(mockTask).launchWithAnimation()
        }

    @Test
    @EnableFlags(WindowFlags.FLAG_ENABLE_REJECT_HOME_TRANSITION)
    fun whenHomeCommandIsAdded_executeRejectHomeActionOnExternalDisplay() =
        testScope.runTest {
            whenever(containerInterface.getVisibleRecentsView<RecentsView<*, *>>()).thenReturn(null)
            val (swipeUpHandler, newGestureState) = setupGestureDependencies()
            val command = sut.addCommand(CommandType.HOME, EXTERNAL_DISPLAY_ID)!!

            runCurrent()
            assertThat(command.status).isEqualTo(CommandStatus.PROCESSING)
            verify(touchInteractionService, never()).startActivity(any())
            verify(swipeUpHandler).onGestureStarted(any())
            verify(newGestureState)
                .setHandlingAtomicEvent(GestureState.GestureEndTarget.REJECT_HOME)

            // Make sure we can transition to completed state once we see an end callback.
            val gestureAnimationEndCallbackCaptor = argumentCaptor<Runnable>()
            verify(swipeUpHandler)
                .setGestureAnimationEndCallback(gestureAnimationEndCallbackCaptor.capture())
            gestureAnimationEndCallbackCaptor.firstValue.run()
            runCurrent()
            assertThat(command.status).isEqualTo(CommandStatus.COMPLETED)
        }

    @Test
    fun whenHomeCommandIsAddedAndRecentsIsVisible_executeHomeActionOnMainDisplay() =
        testScope.runTest {
            val onHomeAnimationCompleteRunnableCaptor = argumentCaptor<Runnable>()
            whenever(containerInterface.getVisibleRecentsView<RecentsView<*, *>>())
                .thenReturn(recentView)
            val command = sut.addCommand(CommandType.HOME)!!
            runCurrent()
            verify(recentView).startHome(onHomeAnimationCompleteRunnableCaptor.capture())

            assertThat(command.status).isEqualTo(CommandStatus.PROCESSING)
            onHomeAnimationCompleteRunnableCaptor.firstValue.run()
            runCurrent()
            assertThat(command.status).isEqualTo(CommandStatus.COMPLETED)
        }

    @Test
    @EnableFlags(WindowFlags.FLAG_ENABLE_REJECT_HOME_TRANSITION)
    fun whenHomeCommandIsAddedAndRecentsIsVisible_dontExecuteHomeActionOnExternalDisplay() =
        testScope.runTest {
            whenever(containerInterface.getVisibleRecentsView<RecentsView<*, *>>())
                .thenReturn(recentView)
            val command = sut.addCommand(CommandType.HOME, EXTERNAL_DISPLAY_ID)!!
            runCurrent()
            verify(recentView, never()).startHome()

            assertThat(command.status).isEqualTo(CommandStatus.COMPLETED)
        }

    @Test
    fun whenHomeCommandIsAdded_executeHomeAction_withKeycodeHomeEnabled() =
        testScope.runTest {
            sut.addCommand(CommandType.HOME)
            runCurrent()
            verify(systemUiProxy).onKeyEvent(anyInt(), anyInt())
        }

    @Test
    fun hideAltTabCommand_onSmallScreenDefaultDisplay_neverSetsKeyboardFocusTask() =
        testScope.runTest {
            whenever(containerInterface.getVisibleRecentsView<RecentsView<*, *>>()).thenReturn(null)
            val command = sut.addCommand(CommandType.HIDE_ALT_TAB)!!
            runCurrent()
            assertThat(command.status).isEqualTo(CommandStatus.COMPLETED)
            verify(taskbarUIController, never()).launchFocusedTask()
            verify(recentView, never()).setKeyboardFocusTask(any())
        }

    @Test
    fun hideAltTabCommand_onSmallScreenExternalDisplay_setsKeyboardFocusTaskToTaskViewWithIds() =
        testScope.runTest {
            whenever(containerInterface.getVisibleRecentsView<RecentsView<*, *>>()).thenReturn(null)
            val command = sut.addCommand(CommandType.HIDE_ALT_TAB, EXTERNAL_DISPLAY_ID)!!
            runCurrent()
            assertThat(command.status).isEqualTo(CommandStatus.PROCESSING)
            verify(taskbarUIController).launchFocusedTask()
            verify(recentView)
                .setKeyboardFocusTask(
                    KeyboardFocusTask.TaskViewWithIds(REQUESTED_KEYBOARD_FOCUS_TASK_IDS)
                )
        }

    @Test
    fun hideAltTabCommand_withoutRequestedKeyboardFocusTaskIds_neverSetsKeyboardFocusTask() =
        testScope.runTest {
            whenever(containerInterface.getVisibleRecentsView<RecentsView<*, *>>()).thenReturn(null)
            whenever(taskbarUIController.launchFocusedTask()).thenReturn(null)
            val command = sut.addCommand(CommandType.HIDE_ALT_TAB, EXTERNAL_DISPLAY_ID)!!
            runCurrent()
            assertThat(command.status).isEqualTo(CommandStatus.COMPLETED)
            verify(taskbarUIController).launchFocusedTask()
            verify(recentView, never()).setKeyboardFocusTask(any())
        }

    @Test
    fun showAltTabCommand_onSmallScreenDefaultDisplay_opensOverviewWithCurrentPageFocus() =
        testScope.runTest {
            whenever(containerInterface.getVisibleRecentsView<RecentsView<*, *>>()).thenReturn(null)
            val command = sut.addCommand(CommandType.SHOW_ALT_TAB)!!
            runCurrent()
            assertThat(command.status).isEqualTo(CommandStatus.PROCESSING)
            verify(taskbarUIController, never()).openQuickSwitchView()
            verify(recentView).setKeyboardFocusTask(KeyboardFocusTask.CurrentPageTaskView)
        }

    @Test
    fun showAltTabCommand_onSmallScreenExternalDisplay_opensQuickSwitchView() =
        testScope.runTest {
            whenever(containerInterface.getVisibleRecentsView<RecentsView<*, *>>()).thenReturn(null)
            val command = sut.addCommand(CommandType.SHOW_ALT_TAB, EXTERNAL_DISPLAY_ID)!!
            runCurrent()
            assertThat(command.status).isEqualTo(CommandStatus.COMPLETED)
            verify(taskbarUIController).openQuickSwitchView()
            verify(recentView, never()).setKeyboardFocusTask(any())
        }

    @Test
    fun showWithFocusCommand_setsKeyboardFocusTaskToCurrentTask() =
        testScope.runTest {
            whenever(containerInterface.getVisibleRecentsView<RecentsView<*, *>>()).thenReturn(null)
            whenever(containerInterface.switchToRecentsIfVisible(any())).thenReturn(false)
            val (swipeUpHandler, newGestureState) = setupGestureDependencies()
            val command = sut.addCommand(CommandType.SHOW_WITH_FOCUS)!!

            runCurrent()
            assertThat(command.status).isEqualTo(CommandStatus.PROCESSING)
            verify(swipeUpHandler).onGestureStarted(any())
            verify(newGestureState).setHandlingAtomicEvent(GestureState.GestureEndTarget.RECENTS)
            verify(recentView).setKeyboardFocusTask(KeyboardFocusTask.ExpectedCurrentTask)

            // Make sure we can transition to completed state once we see an end callback.
            val gestureAnimationEndCallbackCaptor = argumentCaptor<Runnable>()
            verify(swipeUpHandler)
                .setGestureAnimationEndCallback(gestureAnimationEndCallbackCaptor.capture())
            whenever(containerInterface.getVisibleRecentsView<RecentsView<*, *>>())
                .thenReturn(recentView)
            gestureAnimationEndCallbackCaptor.firstValue.run()

            runCurrent()
            assertThat(command.status).isEqualTo(CommandStatus.COMPLETED)
            verify(recentView).setKeyboardFocusTask(KeyboardFocusTask.Unfocused)
        }

    private fun setupGestureDependencies(): Pair<AbsSwipeUpHandler<*, *, *>, GestureState> {
        val swipeUpHandlerFactory = mock<AbsSwipeUpHandler.Factory>()
        val swipeUpHandler = mock<AbsSwipeUpHandler<*, *, *>>()
        val newGestureState = mock<GestureState>()
        whenever(touchInteractionService.getSwipeUpHandlerFactory(any()))
            .thenReturn(swipeUpHandlerFactory)
        whenever(swipeUpHandlerFactory.newHandler(any(), any())).thenReturn(swipeUpHandler)
        whenever(swipeUpHandler.getLaunchIntent()).thenReturn(Intent())
        whenever(touchInteractionService.createGestureState(any(), any(), any()))
            .thenReturn(newGestureState)
        whenever(taskAnimationManager.isRecentsAnimationRunning).thenReturn(false)
        whenever(taskAnimationManager.startRecentsAnimation(any(), any(), any())).thenReturn(mock())
        return Pair(swipeUpHandler, newGestureState)
    }

    private companion object {
        const val QUEUE_TIMEOUT = 5001L
        const val EXTERNAL_DISPLAY_ID = 1
        const val TASK_ID = 10
        val REQUESTED_KEYBOARD_FOCUS_TASK_IDS = setOf(TASK_ID)
    }
}
