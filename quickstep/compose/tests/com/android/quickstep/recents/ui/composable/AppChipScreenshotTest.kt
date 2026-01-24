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
 *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.quickstep.recents.ui.composable

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.helper.NavigateDownTabEvent
import com.google.android.apps.nexuslauncher.imagecomparison.goldenpathmanager.ViewScreenshotGoldenPathManager
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays
import platform.test.screenshot.getEmulatedDevicePathConfig
import platform.test.screenshot.utils.compose.ComposeScreenshotTestRule

@RunWith(ParameterizedAndroidJunit4::class)
class AppChipScreenshotTest(emulationSpec: DeviceEmulationSpec) {

    @get:Rule val screenshotRule = composeScreenshotTestRule(emulationSpec)

    @Composable
    private fun AppChipForTest(expanded: Boolean, onClick: () -> Unit = {}) {
        Column(
            modifier = Modifier.widthIn(min = COLLAPSED_WIDTH_OFFSET),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppChip(
                title = SHORT_APP_NAME,
                icon = getGoogleDrawable()!!,
                modifier = Modifier.testTag("appChip_short"),
                expanded = expanded,
                onClick = onClick,
            )
            AppChip(
                title = LONG_APP_NAME,
                icon = getGoogleDrawable()!!,
                modifier = Modifier.testTag("appChip_long"),
                expanded = expanded,
                onClick = onClick,
            )
        }
    }

    @Test
    fun collapsed() =
        screenshotRule.screenshotTest("appchip_collapsed") { AppChipForTest(expanded = false) }

    @Test
    fun expanded() =
        screenshotRule.screenshotTest("appchip_expanded") { AppChipForTest(expanded = true) }

    @Test
    fun focusableState() {
        screenshotRule.screenshotTest(
            "appchip_focusable",
            beforeScreenshot = {
                val composeTestRule = screenshotRule.composeRule
                composeTestRule.onNodeWithTag("appChip").assertIsNotFocused()

                // Press down to focus first element
                composeTestRule.onRoot().performKeyPress(keyEvent = NavigateDownTabEvent)

                composeTestRule.onNodeWithTag("appChip").assertIsFocused()
            },
        ) {
            Box(modifier = Modifier.widthIn(min = COLLAPSED_WIDTH_OFFSET)) {
                AppChip(
                    title = SHORT_APP_NAME,
                    icon = getGoogleDrawable()!!,
                    modifier = Modifier.testTag("appChip"),
                    expanded = false,
                    onClick = {},
                )
            }
        }
    }

    private fun getGoogleDrawable(): Drawable? {
        val context = InstrumentationRegistry.getInstrumentation().context
        val identifier =
            context.resources.getIdentifier(
                APP_ICON,
                "drawable",
                InstrumentationRegistry.getInstrumentation().context.packageName,
            )
        return context.getDrawable(identifier)
    }

    private companion object {
        const val SHORT_APP_NAME = "App name"
        const val LONG_APP_NAME = "Very long name for the app"
        const val APP_ICON = "ic_super_g_color"
        val COLLAPSED_WIDTH_OFFSET = 156.dp + 6.dp

        @Parameters(name = "{0}")
        @JvmStatic
        fun getTestSpecs(): List<DeviceEmulationSpec> {
            return DeviceEmulationSpec.forDisplays(Displays.Phone, isLandscape = false)
        }
    }
}

/**
 * Create a [ComposeScreenshotTestRule] for screenshot tests of Compose-based UI. If
 * [enforcePerfectPixelMatch] is true, enables a strict pixel-by-pixel comparison to detect any
 * visual differences between images.
 */
fun composeScreenshotTestRule(
    emulationSpec: DeviceEmulationSpec,
    enforcePerfectPixelMatch: Boolean = false,
): ComposeScreenshotTestRule {
    return ComposeScreenshotTestRule(
        emulationSpec = emulationSpec,
        pathManager = ViewScreenshotGoldenPathManager(getEmulatedDevicePathConfig(emulationSpec)),
        enforcePerfectPixelMatch = enforcePerfectPixelMatch,
    )
}
