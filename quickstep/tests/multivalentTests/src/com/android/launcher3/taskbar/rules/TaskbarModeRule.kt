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

import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode
import com.android.launcher3.taskbar.rules.TaskbarModeRule.TaskbarMode
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.NavigationMode
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.spy

/**
 * Allows tests to specify which Taskbar [Mode] to run under.
 *
 * [context] should match the test's target context, so that Dagger singleton instances are properly
 * sandboxed.
 *
 * Annotate tests with [TaskbarMode] to set a mode. If the annotation is omitted for any tests, this
 * rule is a no-op.
 *
 * Make sure this rule precedes any rules that depend on [DisplayController], or else the instance
 * might be inconsistent across the test lifecycle.
 */
class TaskbarModeRule(private val context: TaskbarWindowSandboxContext) : TestRule {
    /** The selected Taskbar mode. */
    enum class Mode {
        TRANSIENT,
        PINNED,
        THREE_BUTTONS,
    }

    /** Overrides Taskbar [mode] for a test. */
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FUNCTION)
    annotation class TaskbarMode(val mode: Mode)

    override fun apply(base: Statement, description: Description): Statement {
        val taskbarMode = description.getAnnotation(TaskbarMode::class.java) ?: return base

        return object : Statement() {
            override fun evaluate() {
                val mode = taskbarMode.mode

                getInstrumentation().runOnMainSync {
                    DisplayController.INSTANCE[context].let {
                        if (it is DisplayControllerSpy) {
                            it.infoModifier = { info ->
                                spy(info) {
                                    on { isTransientTaskbar } doReturn (mode == Mode.TRANSIENT)
                                    on { isPinnedTaskbar } doReturn (mode == Mode.PINNED)
                                    on { navigationMode } doReturn
                                        when (mode) {
                                            Mode.TRANSIENT,
                                            Mode.PINNED -> NavigationMode.NO_BUTTON
                                            Mode.THREE_BUTTONS -> NavigationMode.THREE_BUTTONS
                                        }
                                }
                            }
                        }
                    }
                }

                base.evaluate()
            }
        }
    }
}
