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

import android.animation.AnimatorTestRule
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.Flags.enableTaskbarUiThread
import com.android.launcher3.Hotseat
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherInteractor
import com.android.launcher3.LauncherState
import com.android.launcher3.LauncherUiState
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.taskbar.TaskbarManagerImpl.TASKBAR_UI_THREAD
import com.android.launcher3.taskbar.bubbles.BubbleControllers
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.android.launcher3.util.MutableListenableRef
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_AWAKE
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_WAKEFULNESS_TRANSITION
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags
import com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import java.util.concurrent.Executor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixel9profold", "pixelTablet2023"])
class TaskbarLauncherStateControllerTest {
    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()
    @get:Rule(order = 1) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 2) val animatorTestRule = AnimatorTestRule(this)
    @get:Rule(order = 3) val taskbarUnitTestRule = TaskbarUnitTestRule(testInstance = this, context)

    @InjectController lateinit var bubbleControllers: Optional<BubbleControllers>
    @InjectController lateinit var taskbarStashController: TaskbarStashController

    private val bubbleBarViewController by lazy {
        bubbleControllers.orElseThrow().bubbleBarViewController
    }
    private val bubbleBarStashController by lazy {
        bubbleControllers.orElseThrow().bubbleStashController
    }
    private val taskbarLauncherStateController = TaskbarLauncherStateController()

    @Test
    fun updateStateForSysuiFlags_singleTapPowerButton_stashTaskAndBubbleBarOnAnimationEnd() {
        initForWakeTransitionWithBubbles(SYSUI_STATE_AWAKE)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleBarStashController.showBubbleBar(expandBubbles = true)
            animatorTestRule.advanceTimeBy(BubbleStashController.BAR_STASH_DURATION)
        }

        assertThat(bubbleBarStashController.isStashed).isFalse()
        assertThat(bubbleBarViewController.isExpanded).isTrue()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // simulate the device going to sleep
            taskbarLauncherStateController.updateStateForSysuiFlags(
                SYSUI_STATE_WAKEFULNESS_TRANSITION and SYSUI_STATE_AWAKE.inv()
            )
            // Stash the taskbar.
            animatorTestRule.advanceTimeBy(taskbarStashController.stashDuration)
            // Stash the bubble bar.
            animatorTestRule.advanceTimeBy(BubbleStashController.BAR_STASH_DURATION)
        }

        assertThat(bubbleBarStashController.isStashed).isTrue()
        assertThat(bubbleBarViewController.isExpanded).isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun updateStateForSysuiFlags_doubleTapPowerButton_doesNotStashTaskAndBubbleBarOnAnimationEnd() {
        initForWakeTransitionWithBubbles(SYSUI_STATE_AWAKE)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleBarStashController.showBubbleBar(expandBubbles = true)
            animatorTestRule.advanceTimeBy(BubbleStashController.BAR_STASH_DURATION)
        }

        assertThat(bubbleBarStashController.isStashed).isFalse()
        assertThat(bubbleBarViewController.isExpanded).isTrue()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // simulate double tap
            taskbarLauncherStateController.updateStateForSysuiFlags(
                SYSUI_STATE_WAKEFULNESS_TRANSITION and SYSUI_STATE_AWAKE.inv()
            )
            taskbarLauncherStateController.updateStateForSysuiFlags(
                SYSUI_STATE_WAKEFULNESS_TRANSITION or SYSUI_STATE_AWAKE
            )
            animatorTestRule.advanceTimeBy(taskbarStashController.stashDuration)
        }

        assertThat(bubbleBarStashController.isStashed).isFalse()
        assertThat(bubbleBarViewController.isExpanded).isTrue()
    }

    /** Initializes the controller for a wake transition with a transit taskbar and bubbles. */
    private fun initForWakeTransitionWithBubbles(@SystemUiStateFlags sysUiStateFlags: Long) {
        val launcherStateManager =
            mock<StateManager<LauncherState, Launcher>> {
                on { state } doReturn mock<LauncherState>()
            }
        val dp = taskbarUnitTestRule.activityContext.deviceProfile
        val mockedLauncherUiState =
            mock<LauncherUiState> {
                on { deviceProfileRef } doReturn MutableListenableRef(dp)
                on { isSplitSelectActiveRef } doReturn MutableListenableRef(false)
                on { launcherStateRef } doReturn MutableListenableRef(LauncherState.NORMAL)
                on { taskbarAlignmentChannelAlpha } doReturn MutableListenableRef(0f)
            }
        val quickstepLauncher =
            mock<QuickstepLauncher> {
                on { deviceProfile } doReturn dp
                on { hotseat } doReturn mock<Hotseat>()
                on { stateManager } doReturn launcherStateManager
                on { launcherUiState } doReturn mockedLauncherUiState
            }
        val controllers = taskbarUnitTestRule.activityContext.controllers
        val immediateExecutor = Executor { r -> r.run() }
        val launcherExecutor: Executor =
            if (enableTaskbarUiThread()) MAIN_EXECUTOR else immediateExecutor
        val taskbarExecutor: Executor =
            if (enableTaskbarUiThread()) TASKBAR_UI_THREAD else immediateExecutor
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            taskbarLauncherStateController.init(
                controllers,
                LauncherInteractor(quickstepLauncher, launcherExecutor),
                mockedLauncherUiState,
                sysUiStateFlags,
                taskbarExecutor,
            )
            taskbarStashController.toggleTaskbarStash() // Un-stashing the taskbar.
            bubbleBarViewController.setHiddenForBubbles(false) // Show the bubble bar.
        }
    }
}
