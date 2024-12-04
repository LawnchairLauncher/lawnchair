/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.flicker

import android.app.Instrumentation
import android.tools.flicker.junit.FlickerBuilderProvider
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.tapl.LauncherInstrumentation

abstract class BaseBenchmarkTest
@JvmOverloads
constructor(
    protected open val flicker: LegacyFlickerTest,
    protected val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation(),
    protected val tapl: LauncherInstrumentation = LauncherInstrumentation()
) {
    /** Specification of the test transition to execute */
    abstract val transition: FlickerBuilder.() -> Unit

    /**
     * Entry point for the test runner. It will use this method to initialize and cache flicker
     * executions
     */
    @FlickerBuilderProvider
    open fun buildFlicker(): FlickerBuilder {
        return FlickerBuilder(instrumentation).apply {
            setup { flicker.scenario.setIsTablet(tapl.isTablet) }
            transition()
        }
    }
}
