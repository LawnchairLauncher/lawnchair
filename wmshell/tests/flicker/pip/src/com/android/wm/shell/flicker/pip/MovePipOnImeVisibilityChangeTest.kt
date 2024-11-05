/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.flicker.pip

import android.platform.test.annotations.Presubmit
import android.tools.Rotation
import android.tools.flicker.assertions.FlickerTest
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.legacy.LegacyFlickerTestFactory
import android.tools.helpers.WindowUtils
import android.tools.traces.component.ComponentNameMatcher
import com.android.server.wm.flicker.helpers.ImeAppHelper
import com.android.server.wm.flicker.helpers.setRotation
import com.android.wm.shell.flicker.pip.common.PipTransition
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test Pip launch. To run this test:
 * `atest WMShellFlickerTestsPip3:MovePipOnImeVisibilityChangeTest`
 */
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MovePipOnImeVisibilityChangeTest(flicker: LegacyFlickerTest) : PipTransition(flicker) {
    private val imeApp = ImeAppHelper(instrumentation)

    override val thisTransition: FlickerBuilder.() -> Unit = {
        setup {
            imeApp.launchViaIntent(wmHelper)
            setRotation(flicker.scenario.startRotation)
        }
        teardown { imeApp.exit(wmHelper) }
        transitions {
            // open the soft keyboard
            imeApp.openIME(wmHelper)
            createTag(TAG_IME_VISIBLE)

            // then close it again
            imeApp.closeIME(wmHelper)
        }
    }

    /** Ensure the pip window remains visible throughout any keyboard interactions */
    @Presubmit
    @Test
    open fun pipInVisibleBounds() {
        flicker.assertWmVisibleRegion(pipApp) {
            val displayBounds = WindowUtils.getDisplayBounds(flicker.scenario.startRotation)
            coversAtMost(displayBounds)
        }
    }

    /** Ensure that the pip window does not obscure the keyboard */
    @Presubmit
    @Test
    open fun pipIsAboveAppWindow() {
        flicker.assertWmTag(TAG_IME_VISIBLE) { isAboveWindow(ComponentNameMatcher.IME, pipApp) }
    }

    companion object {
        private const val TAG_IME_VISIBLE = "imeIsVisible"

        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams(): Collection<FlickerTest> {
            return LegacyFlickerTestFactory.nonRotationTests(
                supportedRotations = listOf(Rotation.ROTATION_0)
            )
        }
    }
}
