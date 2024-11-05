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

package com.android.wm.shell.flicker.splitscreen.benchmark

import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.legacy.LegacyFlickerTestFactory
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.helpers.ImeAppHelper
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import org.junit.FixMethodOrder
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
abstract class MultipleShowImeRequestsInSplitScreenBenchmark(
        override val flicker: LegacyFlickerTest
) : SplitScreenBase(flicker) {
    override val primaryApp = ImeAppHelper(instrumentation)
    override val defaultTeardown: FlickerBuilder.() -> Unit
        get() = {
            teardown {
                primaryApp.closeIME(wmHelper)
                super.defaultTeardown
            }
        }

    protected val thisTransition: FlickerBuilder.() -> Unit
        get() = {
            setup {
                SplitScreenUtils.enterSplit(
                        wmHelper,
                        tapl,
                        device,
                        primaryApp,
                        secondaryApp,
                        flicker.scenario.startRotation
                )
                // initially open the IME
                primaryApp.openIME(wmHelper)
            }
            transitions {
                for (i in 1..OPEN_IME_COUNT) {
                    primaryApp.openIME(wmHelper)
                }
            }
        }

    companion object {
        const val OPEN_IME_COUNT = 30

        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams() = LegacyFlickerTestFactory.nonRotationTests()
    }
}
