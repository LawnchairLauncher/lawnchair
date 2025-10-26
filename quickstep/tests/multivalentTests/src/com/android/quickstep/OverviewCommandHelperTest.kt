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

import android.platform.test.flag.junit.SetFlagsRule
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.TestDispatcherProvider
import com.android.launcher3.util.rule.setFlags
import com.android.quickstep.OverviewCommandHelper.CommandInfo
import com.android.quickstep.OverviewCommandHelper.CommandInfo.CommandStatus
import com.android.quickstep.OverviewCommandHelper.CommandType
import com.android.quickstep.fallback.window.RecentsDisplayModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.spy
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(LauncherMultivalentJUnit::class)
@OptIn(ExperimentalCoroutinesApi::class)
class OverviewCommandHelperTest {
    @get:Rule val setFlagsRule: SetFlagsRule = SetFlagsRule()

    private lateinit var sut: OverviewCommandHelper
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)

    private var pendingCallbacksWithDelays = mutableListOf<Long>()

    private val recentsDisplayModel: RecentsDisplayModel = mock()
    private val defaultDisplayResource: RecentsDisplayModel.RecentsDisplayResource = mock()
    private val secondaryDisplayResource: RecentsDisplayModel.RecentsDisplayResource = mock()
    private val executeCommandDisplayIds = mutableListOf<Int>()

    private fun setupDefaultDisplay() {
        whenever(defaultDisplayResource.displayId).thenReturn(DEFAULT_DISPLAY)
        whenever(recentsDisplayModel.activeDisplayResources)
            .thenReturn(listOf(defaultDisplayResource))
    }

    private fun setupMultipleDisplays() {
        whenever(defaultDisplayResource.displayId).thenReturn(DEFAULT_DISPLAY)
        whenever(secondaryDisplayResource.displayId).thenReturn(1)
        whenever(recentsDisplayModel.activeDisplayResources)
            .thenReturn(listOf(defaultDisplayResource, secondaryDisplayResource))
    }

    @Suppress("UNCHECKED_CAST")
    @Before
    fun setup() {
        setFlagsRule.setFlags(true, Flags.FLAG_ENABLE_OVERVIEW_COMMAND_HELPER_TIMEOUT)

        setupDefaultDisplay()

        sut =
            spy(
                OverviewCommandHelper(
                    touchInteractionService = mock(),
                    overviewComponentObserver = mock(),
                    dispatcherProvider = TestDispatcherProvider(dispatcher),
                    recentsDisplayModel = recentsDisplayModel,
                    focusState = mock(),
                    taskbarManager = mock(),
                )
            )

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

    private fun addCallbackDelay(delayInMillis: Long = 0) {
        pendingCallbacksWithDelays.add(delayInMillis)
    }

    @Test
    fun whenFirstCommandIsAdded_executeCommandImmediately() =
        testScope.runTest {
            // Add command to queue
            val commandInfo: CommandInfo = sut.addCommand(CommandType.HOME)!!
            assertThat(commandInfo.status).isEqualTo(CommandStatus.IDLE)
            runCurrent()
            assertThat(commandInfo.status).isEqualTo(CommandStatus.COMPLETED)
        }

    @Test
    fun whenFirstCommandIsAdded_executeCommandImmediately_WithCallbackDelay() =
        testScope.runTest {
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
            // Add command to queue
            addCallbackDelay(100)
            val commandType1 = CommandType.HOME
            val commandInfo1: CommandInfo = sut.addCommand(commandType1)!!
            assertThat(commandInfo1.status).isEqualTo(CommandStatus.IDLE)

            addCallbackDelay(100)
            val commandType2 = CommandType.SHOW
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
            // Add command to queue
            addCallbackDelay(QUEUE_TIMEOUT)
            val commandType1 = CommandType.HOME
            val commandInfo1: CommandInfo = sut.addCommand(commandType1)!!
            assertThat(commandInfo1.status).isEqualTo(CommandStatus.IDLE)

            addCallbackDelay(100)
            val commandType2 = CommandType.SHOW
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
            executeCommandDisplayIds.clear()
            val displayId = 5
            // Add command to queue
            val commandInfo: CommandInfo = sut.addCommand(CommandType.HOME, displayId)!!
            assertThat(commandInfo.status).isEqualTo(CommandStatus.IDLE)
            runCurrent()
            assertThat(commandInfo.status).isEqualTo(CommandStatus.COMPLETED)
            assertThat(executeCommandDisplayIds).containsExactly(displayId)
        }

    private companion object {
        const val QUEUE_TIMEOUT = 5001L
        const val EXTERNAL_DISPLAY_ID = 1
    }
}
