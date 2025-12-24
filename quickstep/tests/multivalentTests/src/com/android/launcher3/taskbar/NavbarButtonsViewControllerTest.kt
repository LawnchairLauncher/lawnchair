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
import com.android.launcher3.Flags.FLAG_ENABLE_TASKBAR_FOR_DIRECT_BOOT
import com.android.launcher3.taskbar.NavbarButtonsViewController.ALPHA_INDEX_KEYGUARD_OR_DISABLE
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnMainSync
import com.android.launcher3.taskbar.rules.TaskbarModeRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.THREE_BUTTONS
import com.android.launcher3.taskbar.rules.TaskbarModeRule.TaskbarMode
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.UserLocked
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val ANIMATION_DURATION = 300L // Default from ValueAnimator.

@RunWith(LauncherMultivalentJUnit::class)
@EnableFlags(FLAG_ENABLE_TASKBAR_FOR_DIRECT_BOOT)
class NavbarButtonsViewControllerTest {

    @get:Rule(order = 0) val animatorTestRule = AnimatorTestRule(this)
    @get:Rule(order = 1) val setFlagsRule = SetFlagsRule()
    @get:Rule(order = 2) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 3) val taskbarModeRule = TaskbarModeRule(context)
    @get:Rule(order = 4) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    @InjectController lateinit var navbarButtonsViewController: NavbarButtonsViewController

    @Test
    @TaskbarMode(THREE_BUTTONS)
    @UserLocked
    fun userLocked_keyguardOccluded_homeButtonHidden() {
        runOnMainSync {
            navbarButtonsViewController.setKeyguardVisible(
                /* isKeyguardVisible = */ true,
                /* isKeyguardOccluded = */ true,
            )
            animatorTestRule.advanceTimeBy(ANIMATION_DURATION)
        }
        val alpha =
            navbarButtonsViewController.homeButtonAlpha[ALPHA_INDEX_KEYGUARD_OR_DISABLE].value
        assertThat(alpha).isZero()
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    fun userUnlocked_keyguardOccluded_homeButtonShown() {
        runOnMainSync {
            navbarButtonsViewController.setKeyguardVisible(
                /* isKeyguardVisible = */ true,
                /* isKeyguardOccluded = */ true,
            )
            animatorTestRule.advanceTimeBy(ANIMATION_DURATION)
        }
        val alpha =
            navbarButtonsViewController.homeButtonAlpha[ALPHA_INDEX_KEYGUARD_OR_DISABLE].value
        assertThat(alpha).isEqualTo(1)
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    @UserLocked
    fun userLocked_keyguardVisible_backButtonHidden() {
        runOnMainSync {
            navbarButtonsViewController.setKeyguardVisible(
                /* isKeyguardVisible = */ true,
                /* isKeyguardOccluded = */ false,
            )
            animatorTestRule.advanceTimeBy(ANIMATION_DURATION)
        }
        val alpha =
            navbarButtonsViewController.backButtonAlpha[ALPHA_INDEX_KEYGUARD_OR_DISABLE].value
        assertThat(alpha).isZero()
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    @UserLocked
    fun userLocked_keyguardBouncerVisible_backButtonShown() {
        runOnMainSync {
            navbarButtonsViewController.setKeyguardVisible(
                /* isKeyguardVisible = */ true,
                /* isKeyguardOccluded = */ false,
            )
            navbarButtonsViewController.setBackForBouncer(true)
            animatorTestRule.advanceTimeBy(ANIMATION_DURATION)
        }
        val alpha =
            navbarButtonsViewController.backButtonAlpha[ALPHA_INDEX_KEYGUARD_OR_DISABLE].value
        assertThat(alpha).isEqualTo(1)
    }
}
