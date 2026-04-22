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
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AbstractFloatingView.TYPE_TASKBAR_PINNING_POPUP
import com.android.launcher3.R
import com.android.launcher3.popup.ArrowPopup.OPEN_DURATION_U
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnMainSync
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createHotseatItems
import com.android.launcher3.taskbar.customization.TaskbarDividerContainer
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelFoldable2023", "pixelTablet2023"])
class TaskbarPinningControllerTest {
    @get:Rule(order = 0) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 1) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)
    @get:Rule(order = 2) val animatorTestRule = AnimatorTestRule(this)

    @InjectController lateinit var pinningController: TaskbarPinningController

    private val taskbarContext: TaskbarActivityContext
        get() = taskbarUnitTestRule.activityContext

    private lateinit var taskbarView: TaskbarView
    private lateinit var dividerIcon: TaskbarDividerContainer

    @Before
    fun setup() {
        taskbarContext.controllers.uiController.init(taskbarContext.controllers)
        runOnMainSync { taskbarView = taskbarContext.dragLayer.findViewById(R.id.taskbar_view) }

        runOnMainSync {
            taskbarView.updateItems(createHotseatItems(1), emptyList())
            dividerIcon = requireNotNull(taskbarView.taskbarDividerViewContainer)
        }
    }

    @Test
    fun showPinningView() {
        assertThat(hasPinningPopUp).isFalse()
        runOnMainSync { pinningController.showPinningView(dividerIcon) }
        runOnMainSync {
            // Animation has started. Advance to end of animation.
            animatorTestRule.advanceTimeBy(OPEN_DURATION_U.toLong())
        }
        assertThat(hasPinningPopUp).isTrue()
    }

    private val hasPinningPopUp: Boolean
        get() {
            return AbstractFloatingView.hasOpenView(taskbarContext, TYPE_TASKBAR_PINNING_POPUP)
        }
}
