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

import android.animation.AnimatorTestRule
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.LauncherPrefs.Companion.TASKBAR_PINNING
import com.android.launcher3.QuickstepTransitionManager.PINNED_TASKBAR_TRANSITION_DURATION
import com.android.launcher3.R
import com.android.launcher3.taskbar.StashedHandleViewController.ALPHA_INDEX_STASHED
import com.android.launcher3.taskbar.TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_EDU_OPEN
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.asProperty
import com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_APP
import com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_OVERVIEW
import com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_STASHED_LAUNCHER_STATE
import com.android.launcher3.taskbar.TaskbarStashController.FLAG_STASHED_DEVICE_LOCKED
import com.android.launcher3.taskbar.TaskbarStashController.FLAG_STASHED_IME
import com.android.launcher3.taskbar.TaskbarStashController.FLAG_STASHED_IN_APP_AUTO
import com.android.launcher3.taskbar.TaskbarStashController.FLAG_STASHED_SMALL_SCREEN
import com.android.launcher3.taskbar.TaskbarStashController.FLAG_STASHED_SYSUI
import com.android.launcher3.taskbar.TaskbarStashController.TASKBAR_STASH_DURATION
import com.android.launcher3.taskbar.TaskbarStashController.TASKBAR_STASH_DURATION_FOR_IME
import com.android.launcher3.taskbar.TaskbarStashController.TRANSIENT_TASKBAR_STASH_ALPHA_DURATION
import com.android.launcher3.taskbar.TaskbarStashController.TRANSIENT_TASKBAR_STASH_DURATION
import com.android.launcher3.taskbar.TaskbarViewController.ALPHA_INDEX_STASH
import com.android.launcher3.taskbar.bubbles.BubbleBarViewController
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController
import com.android.launcher3.taskbar.rules.TaskbarModeRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.PINNED
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.THREE_BUTTONS
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.TRANSIENT
import com.android.launcher3.taskbar.rules.TaskbarModeRule.TaskbarMode
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.UserSetupMode
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BUBBLES_EXPANDED
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_IME_VISIBLE
import com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_BAR
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
@EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
@EmulatedDevices(["pixelTablet2023"])
class TaskbarStashControllerTest {
    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()
    @get:Rule(order = 1) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 2) val taskbarModeRule = TaskbarModeRule(context)
    @get:Rule(order = 4) val animatorTestRule = AnimatorTestRule(this)
    @get:Rule(order = 5) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    @InjectController lateinit var stashController: TaskbarStashController
    @InjectController lateinit var viewController: TaskbarViewController
    @InjectController lateinit var stashedHandleViewController: StashedHandleViewController
    @InjectController lateinit var dragLayerController: TaskbarDragLayerController
    @InjectController lateinit var autohideSuspendController: TaskbarAutohideSuspendController
    @InjectController lateinit var bubbleBarViewController: BubbleBarViewController
    @InjectController lateinit var bubbleStashController: BubbleStashController

    private val activityContext by taskbarUnitTestRule::activityContext

    @After fun cancelTimeoutIfExists() = stashController.cancelTimeoutIfExists()

    @Test
    @TaskbarMode(TRANSIENT)
    fun testInit_transientMode_stashedInApp() {
        assertThat(stashController.isStashedInApp).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testInit_pinnedMode_unstashedInApp() {
        assertThat(stashController.isStashedInApp).isFalse()
    }

    @Test
    @UserSetupMode
    @TaskbarMode(PINNED)
    fun testInit_userSetupWithPinnedMode_stashedInApp() {
        assertThat(stashController.isStashedInApp).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testSetSetupUiVisible_true_stashedInApp() {
        getInstrumentation().runOnMainSync { stashController.setSetupUIVisible(true) }
        assertThat(stashController.isStashedInApp).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testSetSetupUiVisible_false_unstashedInApp() {
        getInstrumentation().runOnMainSync { stashController.setSetupUIVisible(false) }
        assertThat(stashController.isStashedInApp).isFalse()
    }

    @Test
    fun testRecreateAsTransient_timeoutStarted() {
        var isPinned by TASKBAR_PINNING.asProperty(context)
        isPinned = true
        activityContext.controllers.sharedState?.taskbarWasPinned = true

        isPinned = false
        assertThat(stashController.timeoutAlarm.alarmPending()).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testSupportsVisualStashing_transientMode_supported() {
        assertThat(stashController.supportsVisualStashing()).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testSupportsVisualStashing_pinnedMode_supported() {
        assertThat(stashController.supportsVisualStashing()).isTrue()
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    fun testSupportsVisualStashing_threeButtonsMode_unsupported() {
        assertThat(stashController.supportsVisualStashing()).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testGetStashDuration_transientMode() {
        assertThat(stashController.stashDuration).isEqualTo(TRANSIENT_TASKBAR_STASH_DURATION)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testGetStashDuration_pinnedMode() {
        assertThat(stashController.stashDuration).isEqualTo(PINNED_TASKBAR_TRANSITION_DURATION)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testIsStashed_pinnedInApp_isUnstashed() {
        getInstrumentation().runOnMainSync {
            stashController.updateStateForFlag(FLAG_IN_APP, true)
            stashController.applyState(0)
        }
        assertThat(stashController.isStashed).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testIsStashed_transientInApp_isStashed() {
        getInstrumentation().runOnMainSync {
            stashController.updateStateForFlag(FLAG_IN_APP, true)
            stashController.applyState(0)
        }
        assertThat(stashController.isStashed).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testIsStashed_transientNotInApp_isUnstashed() {
        getInstrumentation().runOnMainSync {
            stashController.updateStateForFlag(FLAG_IN_APP, false)
            stashController.applyState(0)
        }
        assertThat(stashController.isStashed).isFalse()
    }

    @Test
    fun testIsStashed_stashedInLauncherState_isStashed() {
        getInstrumentation().runOnMainSync {
            stashController.updateStateForFlag(FLAG_IN_APP, false)
            stashController.updateStateForFlag(FLAG_IN_STASHED_LAUNCHER_STATE, true)
            stashController.applyState(0)
        }
        assertThat(stashController.isStashed).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testIsStashed_transientInOverview_isUnstashed() {
        getInstrumentation().runOnMainSync {
            stashController.updateStateForFlag(FLAG_IN_APP, false)
            stashController.updateStateForFlag(FLAG_IN_OVERVIEW, true)
            stashController.applyState(0)
        }
        assertThat(stashController.isStashed).isFalse()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testIsStashed_pinnedInOverviewWithIme_isStashed() {
        getInstrumentation().runOnMainSync {
            stashController.updateStateForFlag(FLAG_IN_APP, false)
            stashController.updateStateForFlag(FLAG_IN_OVERVIEW, true)
            stashController.updateStateForFlag(FLAG_STASHED_IME, true)
            stashController.applyState(0)
        }
        assertThat(stashController.isStashed).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testIsStashed_pinnedTaskbarWithPinnedApp_isStashed() {
        getInstrumentation().runOnMainSync {
            stashController.updateStateForFlag(FLAG_IN_APP, true)
            stashController.updateStateForFlag(FLAG_STASHED_SYSUI, true) // App pinned.
            stashController.applyState(0)
        }
        assertThat(stashController.isStashed).isTrue()
    }

    @Test
    fun testIsInStashedLauncherState_flagUnset_false() {
        stashController.updateStateForFlag(FLAG_IN_STASHED_LAUNCHER_STATE, false)
        assertThat(stashController.isInStashedLauncherState).isFalse()
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    fun testIsInStashedLauncherState_flagSetInThreeButtonsMode_false() {
        stashController.updateStateForFlag(FLAG_IN_STASHED_LAUNCHER_STATE, true)
        assertThat(stashController.isInStashedLauncherState).isFalse()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testIsInStashedLauncherState_flagSetInPinnedMode_true() {
        stashController.updateStateForFlag(FLAG_IN_STASHED_LAUNCHER_STATE, true)
        assertThat(stashController.isInStashedLauncherState).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testIsTaskbarVisibleAndNotStashing_pinnedButNotVisible_false() {
        getInstrumentation().runOnMainSync {
            viewController.taskbarIconAlpha.get(ALPHA_INDEX_STASH).value = 0f
        }
        assertThat(stashController.isTaskbarVisibleAndNotStashing).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testIsTaskbarVisibleAndNotStashing_visibleButStashed_false() {
        getInstrumentation().runOnMainSync {
            viewController.taskbarIconAlpha.get(ALPHA_INDEX_STASH).value = 1f
        }
        assertThat(stashController.isTaskbarVisibleAndNotStashing).isFalse()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testIsTaskbarVisibleAndNotStashing_pinnedAndVisible_true() {
        getInstrumentation().runOnMainSync {
            viewController.taskbarIconAlpha.get(ALPHA_INDEX_STASH).value = 1f
        }
        assertThat(stashController.isTaskbarVisibleAndNotStashing).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testGetTouchableHeight_isStashed_stashedHeight() {
        assertThat(stashController.touchableHeight).isEqualTo(stashController.stashedHeight)
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testGetTouchableHeight_unstashedTransientMode_heightAndBottomMargin() {
        getInstrumentation().runOnMainSync {
            stashController.updateStateForFlag(FLAG_STASHED_IN_APP_AUTO, false)
            stashController.applyState(0)
        }

        val expectedHeight =
            activityContext.deviceProfile.run { taskbarHeight + taskbarBottomMargin }
        assertThat(stashController.touchableHeight).isEqualTo(expectedHeight)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testGetTouchableHeight_pinnedMode_taskbarHeight() {
        assertThat(stashController.touchableHeight)
            .isEqualTo(activityContext.deviceProfile.taskbarHeight)
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testGetContentHeightToReportToApps_transientMode_stashedHeight() {
        assertThat(stashController.contentHeightToReportToApps)
            .isEqualTo(stashController.stashedHeight)
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    fun testGetContentHeightToReportToApps_threeButtonsMode_taskbarHeight() {
        assertThat(stashController.contentHeightToReportToApps)
            .isEqualTo(activityContext.deviceProfile.taskbarHeight)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testGetContentHeightToReportToApps_pinnedMode_taskbarHeight() {
        assertThat(stashController.contentHeightToReportToApps)
            .isEqualTo(activityContext.deviceProfile.taskbarHeight)
    }

    @Test
    @TaskbarMode(PINNED)
    @UserSetupMode
    fun testGetContentHeightToReportToApps_pinnedInSetupMode_setupWizardInsets() {
        assertThat(stashController.contentHeightToReportToApps)
            .isEqualTo(context.resources.getDimensionPixelSize(R.dimen.taskbar_suw_insets))
    }

    @Test
    @TaskbarMode(PINNED)
    fun testGetContentHeightToReportToApps_pinnedModeButFolded_stashedHeight() {
        getInstrumentation().runOnMainSync {
            stashedHandleViewController.stashedHandleAlpha.get(ALPHA_INDEX_STASHED).value = 1f
            stashController.updateStateForFlag(FLAG_STASHED_SMALL_SCREEN, true)
        }
        assertThat(stashController.contentHeightToReportToApps)
            .isEqualTo(stashController.stashedHeight)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testGetContentHeightToReportToApps_homeDisabledWhenFolded_zeroHeight() {
        getInstrumentation().runOnMainSync {
            stashedHandleViewController.stashedHandleAlpha.get(ALPHA_INDEX_STASHED).value = 1f
            stashedHandleViewController.setIsHomeButtonDisabled(true)
            stashController.updateStateForFlag(FLAG_STASHED_SMALL_SCREEN, true)
        }
        assertThat(stashController.contentHeightToReportToApps).isEqualTo(0)
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testGetTappableHeightToReportToApps_transientMode_zeroHeight() {
        assertThat(stashController.tappableHeightToReportToApps).isEqualTo(0)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testGetTappableHeightToReportToApps_pinnedMode_taskbarHeight() {
        assertThat(stashController.tappableHeightToReportToApps)
            .isEqualTo(activityContext.deviceProfile.taskbarHeight)
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testUpdateAndAnimateTransientTaskbar_unstashTaskbar_updatesState() {
        getInstrumentation().runOnMainSync {
            stashController.updateAndAnimateTransientTaskbar(false)
        }
        assertThat(stashController.isStashed).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testUpdateAndAnimateTransientTaskbar_runUnstashAnimation_startsTaskbarTimeout() {
        getInstrumentation().runOnMainSync {
            stashController.updateAndAnimateTransientTaskbar(false)
            animatorTestRule.advanceTimeBy(stashController.stashDuration)
        }
        assertThat(stashController.timeoutAlarm.alarmPending()).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testUpdateAndAnimateTransientTaskbar_finishTaskbarTimeout_taskbarStashes() {
        getInstrumentation().runOnMainSync {
            stashController.updateAndAnimateTransientTaskbar(false)
            animatorTestRule.advanceTimeBy(stashController.stashDuration)
        }
        assertThat(stashController.timeoutAlarm.alarmPending()).isTrue()

        getInstrumentation().runOnMainSync {
            stashController.timeoutAlarm.finishAlarm()
            animatorTestRule.advanceTimeBy(stashController.stashDuration)
        }
        assertThat(stashController.isStashed).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testUpdateAndAnimateTransientTaskbar_autoHideSuspendedForEdu_remainsUnstashed() {
        getInstrumentation().runOnMainSync {
            stashController.updateAndAnimateTransientTaskbar(false)
            animatorTestRule.advanceTimeBy(stashController.stashDuration)
        }

        getInstrumentation().runOnMainSync {
            autohideSuspendController.updateFlag(FLAG_AUTOHIDE_SUSPEND_EDU_OPEN, true)
            stashController.updateAndAnimateTransientTaskbar(true)
            animatorTestRule.advanceTimeBy(stashController.stashDuration)
        }
        assertThat(stashController.isStashed).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testUpdateAndAnimateTransientTaskbar_unstashTaskbarWithBubbles_bubbleBarUnstashes() {
        getInstrumentation().runOnMainSync {
            bubbleBarViewController.setHiddenForBubbles(false)
            bubbleStashController.stashBubbleBarImmediate()
            stashController.updateAndAnimateTransientTaskbar(false, true)
        }
        assertThat(bubbleStashController.isStashed).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testUpdateAndAnimateTransientTaskbar_unstashTaskbarWithoutBubbles_bubbleBarStashed() {
        getInstrumentation().runOnMainSync {
            bubbleBarViewController.setHiddenForBubbles(false)
            bubbleStashController.stashBubbleBarImmediate()
            stashController.updateAndAnimateTransientTaskbar(false, false)
        }
        assertThat(bubbleStashController.isStashed).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testUpdateAndAnimateTransientTaskbar_stashTaskbarWithBubbles_bubbleBarStashes() {
        getInstrumentation().runOnMainSync {
            bubbleBarViewController.setHiddenForBubbles(false)
            bubbleStashController.showBubbleBarImmediate()
            stashController.updateAndAnimateTransientTaskbar(true, true)
        }
        assertThat(bubbleStashController.isStashed).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testUpdateAndAnimateTransientTaskbar_stashTaskbarWithoutBubbles_bubbleBarUnstashed() {
        getInstrumentation().runOnMainSync {
            bubbleBarViewController.setHiddenForBubbles(false)
            bubbleStashController.showBubbleBarImmediate()
            stashController.updateAndAnimateTransientTaskbar(true, false)
        }
        assertThat(bubbleStashController.isStashed).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testUpdateAndAnimateTransientTaskbar_bubbleBarExpandedBeforeTimeout_expandedAfterwards() {
        getInstrumentation().runOnMainSync {
            bubbleBarViewController.setHiddenForBubbles(false)
            bubbleBarViewController.isExpanded = true
            stashController.updateAndAnimateTransientTaskbar(false)
            animatorTestRule.advanceTimeBy(stashController.stashDuration)
        }
        assertThat(stashController.timeoutAlarm.alarmPending()).isTrue()

        getInstrumentation().runOnMainSync {
            stashController.timeoutAlarm.finishAlarm()
            animatorTestRule.advanceTimeBy(stashController.stashDuration)
        }
        assertThat(bubbleBarViewController.isExpanded).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testToggleTaskbarStash_pinnedMode_doesNothing() {
        getInstrumentation().runOnMainSync { stashController.toggleTaskbarStash() }
        assertThat(stashController.isStashed).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testToggleTaskbarStash_transientMode_unstashesTaskbar() {
        getInstrumentation().runOnMainSync { stashController.toggleTaskbarStash() }
        assertThat(stashController.isStashed).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testToggleTaskbarStash_twiceInTransientMode_stashesTaskbar() {
        getInstrumentation().runOnMainSync {
            stashController.toggleTaskbarStash()
            stashController.toggleTaskbarStash()
        }
        assertThat(stashController.isStashed).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testToggleTaskbarStash_notInAppWithTransientMode_doesNothing() {
        getInstrumentation().runOnMainSync {
            stashController.updateStateForFlag(FLAG_IN_APP, false)
            stashController.applyState(0)
            stashController.toggleTaskbarStash()
        }
        assertThat(stashController.isStashed).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testAnimateTransientTaskbar_bubblesShownInOverview_stashesTaskbar() {
        // Start in Overview. Should unstash Taskbar.
        getInstrumentation().runOnMainSync {
            stashController.updateStateForFlag(FLAG_STASHED_IN_APP_AUTO, false)
            stashController.updateStateForFlag(FLAG_IN_APP, false)
            stashController.updateStateForFlag(FLAG_IN_OVERVIEW, true)
            stashController.applyState(0)
        }
        assertThat(stashController.isStashed).isFalse()

        // Expand bubbles. Should stash Taskbar.
        getInstrumentation().runOnMainSync {
            stashController.updateStateForSysuiFlags(SYSUI_STATE_BUBBLES_EXPANDED, false)
            animatorTestRule.advanceTimeBy(TASKBAR_STASH_DURATION)
        }
        assertThat(stashController.isStashed).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testAnimatePinnedTaskbar_imeShown_replacesIconsWithHandle() {
        assume().that(activityContext.isHardwareKeyboard).isFalse()

        getInstrumentation().runOnMainSync {
            stashController.updateStateForSysuiFlags(SYSUI_STATE_IME_VISIBLE, false)
            animatorTestRule.advanceTimeBy(TASKBAR_STASH_DURATION_FOR_IME)
        }
        assertThat(viewController.areIconsVisible()).isFalse()
        assertThat(stashedHandleViewController.isStashedHandleVisible).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testAnimatePinnedTaskbar_imeHidden_replacesHandleWithIcons() {
        assume().that(activityContext.isHardwareKeyboard).isFalse()

        getInstrumentation().runOnMainSync {
            stashController.updateStateForSysuiFlags(SYSUI_STATE_IME_VISIBLE, true)
            animatorTestRule.advanceTimeBy(0)
        }

        getInstrumentation().runOnMainSync {
            stashController.updateStateForSysuiFlags(0, true)
            animatorTestRule.advanceTimeBy(0)
        }
        assertThat(stashedHandleViewController.isStashedHandleVisible).isFalse()
        assertThat(viewController.areIconsVisible()).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testAnimatePinnedTaskbar_imeHidden_verifyAnimationDuration() {
        assume().that(activityContext.isHardwareKeyboard).isFalse()

        // Start with IME shown.
        getInstrumentation().runOnMainSync {
            stashController.updateStateForSysuiFlags(SYSUI_STATE_IME_VISIBLE, true)
            animatorTestRule.advanceTimeBy(0)
        }

        // Hide IME with animation.
        getInstrumentation().runOnMainSync {
            stashController.updateStateForSysuiFlags(0, false)
            // Fast forward without start delay.
            animatorTestRule.advanceTimeBy(TASKBAR_STASH_DURATION_FOR_IME)
        }
        // Icons should not be visible yet due to start delay.
        assertThat(viewController.areIconsVisible()).isFalse()

        // Advance by start delay retroactively. Animation should complete.
        getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(stashController.taskbarStashStartDelayForIme)
        }
        assertThat(viewController.areIconsVisible()).isTrue()
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    fun testAnimateThreeButtonsTaskbar_imeShown_hidesIconsAndBg() {
        assume().that(activityContext.isHardwareKeyboard).isFalse()

        getInstrumentation().runOnMainSync {
            stashController.updateStateForSysuiFlags(SYSUI_STATE_IME_VISIBLE, false)
            animatorTestRule.advanceTimeBy(TASKBAR_STASH_DURATION_FOR_IME)
        }
        assertThat(viewController.areIconsVisible()).isFalse()
        assertThat(dragLayerController.imeBgTaskbar.value).isEqualTo(0)
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    fun testAnimateThreeButtonsTaskbar_imeHidden_showsIconsAndBg() {
        assume().that(activityContext.isHardwareKeyboard).isFalse()

        getInstrumentation().runOnMainSync {
            stashController.updateStateForSysuiFlags(SYSUI_STATE_IME_VISIBLE, false)
            animatorTestRule.advanceTimeBy(TASKBAR_STASH_DURATION_FOR_IME)
        }

        getInstrumentation().runOnMainSync {
            stashController.updateStateForSysuiFlags(0, false)
            animatorTestRule.advanceTimeBy(
                TASKBAR_STASH_DURATION_FOR_IME + stashController.taskbarStashStartDelayForIme
            )
        }
        assertThat(viewController.areIconsVisible()).isTrue()
        assertThat(dragLayerController.imeBgTaskbar.value).isEqualTo(1)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testSetSystemGestureInProgress_whileImeShown_unstashesTaskbar() {
        assume().that(activityContext.isHardwareKeyboard).isFalse()

        getInstrumentation().runOnMainSync {
            stashController.updateStateForSysuiFlags(SYSUI_STATE_IME_VISIBLE, true)
            animatorTestRule.advanceTimeBy(0)
        }

        getInstrumentation().runOnMainSync {
            stashController.setSystemGestureInProgress(true)
            animatorTestRule.advanceTimeBy(
                TASKBAR_STASH_DURATION_FOR_IME + stashController.taskbarStashStartDelayForIme
            )
        }
        assertThat(stashController.isStashed).isFalse()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testSysuiStateImeShowingInApp_hardwareKeyboardWithPinnedMode_notStashedForIme() {
        assume().that(activityContext.isHardwareKeyboard).isTrue()

        getInstrumentation().runOnMainSync {
            stashController.updateStateForFlag(FLAG_IN_APP, true)
            stashController.updateStateForSysuiFlags(SYSUI_STATE_IME_VISIBLE, true)
        }

        assertThat(stashController.isStashed).isFalse()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testUnlockTransition_pinnedMode_fadesOutHandle() {
        getInstrumentation().runOnMainSync {
            stashController.updateStateForFlag(FLAG_STASHED_DEVICE_LOCKED, true)
            stashController.applyState(0)
        }
        assertThat(stashedHandleViewController.isStashedHandleVisible).isTrue()

        getInstrumentation().runOnMainSync {
            stashController.updateStateForFlag(FLAG_STASHED_DEVICE_LOCKED, false)
            stashController.applyState()
            animatorTestRule.advanceTimeBy(stashController.stashDuration)
        }
        assertThat(stashedHandleViewController.isStashedHandleVisible).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testUnlockTransition_transientMode_fadesOutHandleEarly() {
        getInstrumentation().runOnMainSync {
            stashController.updateStateForFlag(FLAG_IN_APP, false)
            stashController.updateStateForFlag(FLAG_STASHED_DEVICE_LOCKED, true)
            stashController.applyState(0)
        }
        assertThat(stashedHandleViewController.isStashedHandleVisible).isTrue()

        getInstrumentation().runOnMainSync {
            stashController.updateStateForFlag(FLAG_STASHED_DEVICE_LOCKED, false)
            stashController.applyState()
            // Time it takes for just the handle to hide (full stash animation is longer).
            animatorTestRule.advanceTimeBy(TRANSIENT_TASKBAR_STASH_ALPHA_DURATION)
        }
        assertThat(stashedHandleViewController.isStashedHandleVisible).isFalse()
    }
}

private fun TaskbarStashController.updateStateForFlag(flag: Int, value: Boolean) {
    updateStateForFlag(flag.toLong(), value)
}
