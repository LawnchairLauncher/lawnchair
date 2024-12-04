/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.flicker.bubble

import android.platform.test.annotations.Presubmit
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test launching a new activity from bubble.
 *
 * To run this test: `atest WMShellFlickerTestsBubbles:OpenActivityFromBubbleTest`
 *
 * Actions:
 * ```
 *     Launch an app and enable app's bubble notification
 *     Send a bubble notification
 *     The activity for the bubble is launched
 * ```
 */
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
class OpenActivityFromBubbleTest(flicker: LegacyFlickerTest) : BaseBubbleScreen(flicker) {

    /** {@inheritDoc} */
    override val transition: FlickerBuilder.() -> Unit
        get() = buildTransition {
            setup {
                val addBubbleBtn = waitAndGetAddBubbleBtn()
                addBubbleBtn?.click() ?: error("Add Bubble not found")
            }
            transitions {
                val showBubble =
                    device.wait(
                        Until.findObject(By.res("com.android.systemui", "bubble_view")),
                        FIND_OBJECT_TIMEOUT
                    )
                showBubble?.run { showBubble.click() } ?: error("Bubble notify not found")
            }
        }

    @Presubmit
    @Test
    open fun testAppIsAlwaysVisible() {
        flicker.assertLayers { this.isVisible(testApp) }
    }
}
