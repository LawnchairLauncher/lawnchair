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

import android.os.SystemClock
import android.platform.test.annotations.Presubmit
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import androidx.test.filters.FlakyTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test launching a new activity from bubble.
 *
 * To run this test: `atest WMShellFlickerTestsBubbles:ChangeActiveActivityFromBubbleTest`
 *
 * Actions:
 * ```
 *     Switch in different bubble notifications
 * ```
 */
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FlakyTest(bugId = 217777115)
class ChangeActiveActivityFromBubbleTest(flicker: LegacyFlickerTest) : BaseBubbleScreen(flicker) {
    /** {@inheritDoc} */
    override val transition: FlickerBuilder.() -> Unit
        get() = buildTransition {
            setup {
                for (i in 1..3) {
                    val addBubbleBtn = waitAndGetAddBubbleBtn() ?: error("Add Bubble not found")
                    addBubbleBtn.click()
                    SystemClock.sleep(1000)
                }
                val showBubble =
                    device.wait(
                        Until.findObject(By.res(SYSTEM_UI_PACKAGE, BUBBLE_RES_NAME)),
                        FIND_OBJECT_TIMEOUT
                    )
                        ?: error("Show bubble not found")
                showBubble.click()
                SystemClock.sleep(1000)
            }
            transitions {
                val bubbles: List<UiObject2> =
                    device.wait(
                        Until.findObjects(By.res(SYSTEM_UI_PACKAGE, BUBBLE_RES_NAME)),
                        FIND_OBJECT_TIMEOUT
                    )
                        ?: error("No bubbles found")
                for (entry in bubbles) {
                    entry.click()
                    SystemClock.sleep(1000)
                }
            }
        }

    @Presubmit
    @Test
    open fun testAppIsAlwaysVisible() {
        flicker.assertLayers { this.isVisible(testApp) }
    }
}
