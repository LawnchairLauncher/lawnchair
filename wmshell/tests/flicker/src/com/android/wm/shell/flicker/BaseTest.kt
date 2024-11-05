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
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.traces.component.ComponentNameMatcher
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.wm.shell.flicker.utils.ICommonAssertions

/**
 * Base test class containing common assertions for [ComponentNameMatcher.NAV_BAR],
 * [ComponentNameMatcher.TASK_BAR], [ComponentNameMatcher.STATUS_BAR], and general assertions
 * (layers visible in consecutive states, entire screen covered, etc.)
 */
abstract class BaseTest
@JvmOverloads
constructor(
    override val flicker: LegacyFlickerTest,
    instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation(),
    tapl: LauncherInstrumentation = LauncherInstrumentation()
) : BaseBenchmarkTest(flicker, instrumentation, tapl), ICommonAssertions
