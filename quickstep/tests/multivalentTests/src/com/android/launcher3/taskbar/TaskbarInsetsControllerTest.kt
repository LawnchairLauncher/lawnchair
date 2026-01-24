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
import android.view.ViewTreeObserver
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnMainSync
import com.android.launcher3.taskbar.TaskbarInsetsController.DebugTouchableRegion.Companion.DEFAULT_TOUCH_REGION
import com.android.launcher3.taskbar.TaskbarInsetsController.DebugTouchableRegion.Companion.FULLSCREEN_TASKBAR_WINDOW
import com.android.launcher3.taskbar.TaskbarInsetsController.DebugTouchableRegion.Companion.ICONS_INVISIBLE
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.TRANSIENT
import com.android.launcher3.taskbar.rules.TaskbarModeRule.TaskbarMode
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_IME_VISIBLE
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelFoldable2023", "pixelTablet2023"])
class TaskbarInsetsControllerTest {

    @get:Rule(order = 0) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 1) val animatorTestRule = AnimatorTestRule(this)
    @get:Rule(order = 2) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    @InjectController lateinit var taskbarInsetsController: TaskbarInsetsController
    @InjectController lateinit var taskbarStashController: TaskbarStashController

    private val taskbarContext by taskbarUnitTestRule::activityContext

    @Test
    fun imeShowing_taskbarWindowUntouchable() {
        runOnMainSync { taskbarContext.updateSysuiStateFlags(SYSUI_STATE_IME_VISIBLE, false) }
        runOnMainSync {
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableReason)
                .isEqualTo(ICONS_INVISIBLE)
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableInsets)
                .isEqualTo(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION)
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableBounds.isEmpty)
                .isTrue()
        }
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun imeShowing_transientTaskbarUnstashed_taskbarWindowTouchable() {
        runOnMainSync {
            taskbarContext.updateSysuiStateFlags(SYSUI_STATE_IME_VISIBLE, false)
            taskbarStashController.updateAndAnimateTransientTaskbar(false)
            animatorTestRule.advanceTimeBy(taskbarStashController.stashDuration)
        }
        runOnMainSync {
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableReason)
                .isEqualTo(DEFAULT_TOUCH_REGION)
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableInsets)
                .isEqualTo(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION)
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableBounds.isEmpty)
                .isFalse()
        }
    }

    @Test
    fun windowFullscreen_entireTaskbarWindowTouchable() {
        runOnMainSync { taskbarContext.setTaskbarWindowFullscreen(true) }
        runOnMainSync {
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableReason)
                .isEqualTo(FULLSCREEN_TASKBAR_WINDOW)
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableInsets)
                .isEqualTo(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_FRAME)
        }
    }

    @Test
    fun windowFullscreen_imeShowing_entireTaskbarWindowTouchable() {
        runOnMainSync {
            taskbarContext.setTaskbarWindowFullscreen(true)
            taskbarContext.updateSysuiStateFlags(SYSUI_STATE_IME_VISIBLE, false)
        }
        runOnMainSync {
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableReason)
                .isEqualTo(FULLSCREEN_TASKBAR_WINDOW)
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableInsets)
                .isEqualTo(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_FRAME)
        }
    }
}
