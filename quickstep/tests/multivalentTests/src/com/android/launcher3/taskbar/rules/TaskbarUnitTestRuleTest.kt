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

package com.android.launcher3.taskbar.rules

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import com.android.launcher3.Utilities
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarKeyguardController
import com.android.launcher3.taskbar.TaskbarManager
import com.android.launcher3.taskbar.TaskbarStashController
import com.android.launcher3.taskbar.bubbles.BubbleBarController
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.ForceRtl
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.NavBarKidsMode
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.UserSetupMode
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.android.wm.shell.Flags
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelFoldable2023", "pixelTablet2023"])
class TaskbarUnitTestRuleTest {

    @get:Rule(order = 0) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 1) val setFlagsRule = SetFlagsRule()

    @Test
    fun testSetup_taskbarInitialized() {
        onSetup { assertThat(activityContext).isInstanceOf(TaskbarActivityContext::class.java) }
    }

    @Test
    fun testRecreateTaskbar_activityContextChanged() {
        onSetup {
            val context1 = activityContext
            recreateTaskbar()
            val context2 = activityContext
            assertThat(context1).isNotSameInstanceAs(context2)
        }
    }

    @Test
    fun testTeardown_taskbarDestroyed() {
        val testRule = TaskbarUnitTestRule(this, context)
        testRule.apply(EMPTY_STATEMENT, DESCRIPTION).evaluate()
        assertThrows(RuntimeException::class.java) { testRule.activityContext }
    }

    @Test
    fun testInjectController_validControllerType_isInjected() {
        val testClass =
            object {
                @InjectController lateinit var controller: TaskbarStashController
                val isInjected: Boolean
                    get() = ::controller.isInitialized
            }

        TaskbarUnitTestRule(testClass, context).apply(EMPTY_STATEMENT, DESCRIPTION).evaluate()

        onSetup(TaskbarUnitTestRule(testClass, context)) {
            assertThat(testClass.isInjected).isTrue()
        }
    }

    @Test
    fun testInjectController_multipleControllers_areInjected() {
        val testClass =
            object {
                @InjectController lateinit var controller1: TaskbarStashController
                @InjectController lateinit var controller2: TaskbarKeyguardController
                val areInjected: Boolean
                    get() = ::controller1.isInitialized && ::controller2.isInitialized
            }

        onSetup(TaskbarUnitTestRule(testClass, context)) {
            assertThat(testClass.areInjected).isTrue()
        }
    }

    @Test
    fun testInjectController_invalidControllerType_exceptionThrown() {
        val testClass =
            object {
                @InjectController lateinit var manager: TaskbarManager // Not a controller.
            }

        // We cannot use #assertThrows because we also catch an assumption violated exception when
        // running #evaluate on devices that do not support Taskbar.
        val result =
            try {
                TaskbarUnitTestRule(testClass, context)
                    .apply(EMPTY_STATEMENT, DESCRIPTION)
                    .evaluate()
            } catch (e: NoSuchElementException) {
                e
            }
        assertThat(result).isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun testInjectController_recreateTaskbar_controllerChanged() {
        val testClass =
            object {
                @InjectController lateinit var controller: TaskbarStashController
            }

        onSetup(TaskbarUnitTestRule(testClass, context)) {
            val controller1 = testClass.controller
            recreateTaskbar()
            val controller2 = testClass.controller
            assertThat(controller1).isNotSameInstanceAs(controller2)
        }
    }

    @EnableFlags(Flags.FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun testInjectBubbleController_bubbleFlagOn_isInjected() {
        val testClass =
            object {
                @InjectController lateinit var controller: BubbleBarController
                val isInjected: Boolean
                    get() = ::controller.isInitialized
            }

        TaskbarUnitTestRule(testClass, context).apply(EMPTY_STATEMENT, DESCRIPTION).evaluate()

        onSetup(TaskbarUnitTestRule(testClass, context)) {
            assertThat(testClass.isInjected).isTrue()
        }
    }

    @DisableFlags(Flags.FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun testInjectBubbleController_bubbleFlagOff_exceptionThrown() {
        val testClass =
            object {
                @InjectController lateinit var controller: BubbleBarController
            }

        // We cannot use #assertThrows because we also catch an assumption violated exception when
        // running #evaluate on devices that do not support Taskbar.
        val result =
            try {
                TaskbarUnitTestRule(testClass, context)
                    .apply(EMPTY_STATEMENT, DESCRIPTION)
                    .evaluate()
            } catch (e: NoSuchElementException) {
                e
            }
        assertThat(result).isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun testUserSetupMode_default_isComplete() {
        onSetup { assertThat(activityContext.isUserSetupComplete).isTrue() }
    }

    @Test
    fun testUserSetupMode_withAnnotation_isIncomplete() {
        @UserSetupMode class Mode
        onSetup(description = Description.createSuiteDescription(Mode::class.java)) {
            assertThat(activityContext.isUserSetupComplete).isFalse()
        }
    }

    @Test
    fun testNavBarKidsMode_default_navBarNotForcedVisible() {
        onSetup { assertThat(activityContext.isNavBarForceVisible).isFalse() }
    }

    @Test
    fun testNavBarKidsMode_withAnnotation_navBarForcedVisible() {
        @NavBarKidsMode class Mode
        onSetup(description = Description.createSuiteDescription(Mode::class.java)) {
            assertThat(activityContext.isNavBarForceVisible).isTrue()
        }
    }

    @Test
    fun testForceRtlAnnotation_setsActivityContextLayoutDirection() {
        @ForceRtl class Rtl
        onSetup(description = Description.createSuiteDescription(Rtl::class.java)) {
            assertThat(Utilities.isRtl(activityContext.resources)).isTrue()
        }
    }

    /**
     * Executes [runTest] after the [testRule] setup phase completes.
     *
     * A [description] can also be provided to mimic annotating a test or test class.
     */
    private fun onSetup(
        testRule: TaskbarUnitTestRule = TaskbarUnitTestRule(this, context),
        description: Description = DESCRIPTION,
        runTest: TaskbarUnitTestRule.() -> Unit,
    ) {
        testRule
            .apply(
                object : Statement() {
                    override fun evaluate() = runTest(testRule)
                },
                description,
            )
            .evaluate()
    }

    private companion object {
        private val EMPTY_STATEMENT =
            object : Statement() {
                override fun evaluate() = Unit
            }
        private val DESCRIPTION =
            Description.createSuiteDescription(TaskbarUnitTestRuleTest::class.java)
    }
}
