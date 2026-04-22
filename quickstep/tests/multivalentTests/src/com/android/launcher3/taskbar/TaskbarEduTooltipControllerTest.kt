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

package com.android.launcher3.taskbar

import com.android.launcher3.Utilities
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.asProperty
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnMainSync
import com.android.launcher3.taskbar.rules.TaskbarModeRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.PINNED
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.THREE_BUTTONS
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.TRANSIENT
import com.android.launcher3.taskbar.rules.TaskbarModeRule.TaskbarMode
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.android.launcher3.util.OnboardingPrefs
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelFoldable2023", "pixelTablet2023"])
class TaskbarEduTooltipControllerTest {

    @get:Rule(order = 0) val context = TaskbarWindowSandboxContext.create()

    @get:Rule(order = 1) val taskbarModeRule = TaskbarModeRule(context)

    @get:Rule(order = 2) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    @InjectController lateinit var taskbarEduTooltipController: TaskbarEduTooltipController

    private val taskbarContext: TaskbarActivityContext
        get() = taskbarUnitTestRule.activityContext

    private val wasInTestHarness = Utilities.isRunningInTestHarness()

    private var tooltipStep by OnboardingPrefs.TASKBAR_EDU_TOOLTIP_STEP.prefItem.asProperty(context)
    private var searchEduSeen by OnboardingPrefs.TASKBAR_SEARCH_EDU_SEEN.asProperty(context)

    @Before
    fun setUp() {
        Utilities.disableRunningInTestHarnessForTests()
    }

    @After
    fun tearDown() {
        if (wasInTestHarness) {
            Utilities.enableRunningInTestHarnessForTests()
        }
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    fun testMaybeShowSwipeEdu_whenTaskbarIsInThreeButtonMode_doesNotShowSwipeEdu() {
        tooltipStep = TOOLTIP_STEP_SWIPE
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_SWIPE)
        runOnMainSync { taskbarEduTooltipController.maybeShowSwipeEdu() }
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_SWIPE)
        assertThat(taskbarEduTooltipController.isTooltipOpen).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testMaybeShowSwipeEdu_whenSwipeEduAlreadyShown_doesNotShowSwipeEdu() {
        tooltipStep = TOOLTIP_STEP_FEATURES
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_FEATURES)
        runOnMainSync { taskbarEduTooltipController.maybeShowSwipeEdu() }
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_FEATURES)
        assertThat(taskbarEduTooltipController.isTooltipOpen).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testMaybeShowSwipeEdu_whenUserHasNotSeen_doesShowSwipeEdu() {
        tooltipStep = TOOLTIP_STEP_SWIPE
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_SWIPE)
        runOnMainSync { taskbarEduTooltipController.maybeShowSwipeEdu() }
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_FEATURES)
        assertThat(taskbarEduTooltipController.isTooltipOpen).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testMaybeShowFeaturesEdu_whenFeatureEduAlreadyShown_doesNotShowFeatureEdu() {
        tooltipStep = TOOLTIP_STEP_NONE
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_NONE)
        runOnMainSync { taskbarEduTooltipController.maybeShowFeaturesEdu() }
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_NONE)
        assertThat(taskbarEduTooltipController.isTooltipOpen).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testMaybeShowFeaturesEdu_whenUserHasNotSeen_doesShowFeatureEdu() {
        tooltipStep = TOOLTIP_STEP_FEATURES
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_FEATURES)
        runOnMainSync { taskbarEduTooltipController.maybeShowFeaturesEdu() }
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_NONE)
        assertThat(taskbarEduTooltipController.isTooltipOpen).isTrue()
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    fun testMaybeShowPinningEdu_whenTaskbarIsInThreeButtonMode_doesNotShowPinningEdu() {
        tooltipStep = TOOLTIP_STEP_PINNING
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_PINNING)
        runOnMainSync { taskbarEduTooltipController.maybeShowFeaturesEdu() }
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_PINNING)
        assertThat(taskbarEduTooltipController.isTooltipOpen).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testMaybeShowPinningEdu_whenUserHasNotSeen_doesShowPinningEdu() {
        // Test standalone pinning edu, where user has seen taskbar edu before, but not pinning edu.
        tooltipStep = TOOLTIP_STEP_PINNING
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_PINNING)
        runOnMainSync { taskbarEduTooltipController.maybeShowFeaturesEdu() }
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_NONE)
        assertThat(taskbarEduTooltipController.isTooltipOpen).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testIsBeforeTooltipFeaturesStep_whenUserHasNotSeenFeatureEdu_shouldReturnTrue() {
        tooltipStep = TOOLTIP_STEP_SWIPE
        assertThat(taskbarEduTooltipController.isBeforeTooltipFeaturesStep).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testIsBeforeTooltipFeaturesStep_whenUserHasSeenFeatureEdu_shouldReturnFalse() {
        tooltipStep = TOOLTIP_STEP_NONE
        assertThat(taskbarEduTooltipController.isBeforeTooltipFeaturesStep).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testHide_whenTooltipIsOpen_shouldCloseTooltip() {
        tooltipStep = TOOLTIP_STEP_SWIPE
        assertThat(taskbarEduTooltipController.tooltipStep).isEqualTo(TOOLTIP_STEP_SWIPE)
        assertThat(taskbarEduTooltipController.isTooltipOpen).isFalse()
        runOnMainSync { taskbarEduTooltipController.maybeShowSwipeEdu() }
        assertThat(taskbarEduTooltipController.isTooltipOpen).isTrue()
        runOnMainSync { taskbarEduTooltipController.hide() }
        assertThat(taskbarEduTooltipController.isTooltipOpen).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testMaybeShowSearchEdu_whenTaskbarIsTransient_shouldNotShowSearchEdu() {
        assertThat(taskbarEduTooltipController.isTooltipOpen).isFalse()
        runOnMainSync { taskbarEduTooltipController.init(taskbarContext.controllers) }
        assertThat(taskbarEduTooltipController.isTooltipOpen).isFalse()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testMaybeShowSearchEdu_whenTaskbarIsPinnedAndUserHasSeenSearchEdu_shouldNotShowSearchEdu() {
        searchEduSeen = true
        assertThat(taskbarEduTooltipController.userHasSeenSearchEdu).isTrue()
        runOnMainSync { taskbarEduTooltipController.hide() }
        assertThat(taskbarEduTooltipController.isTooltipOpen).isFalse()
        runOnMainSync { taskbarEduTooltipController.init(taskbarContext.controllers) }
        assertThat(taskbarEduTooltipController.isTooltipOpen).isFalse()
    }
}
