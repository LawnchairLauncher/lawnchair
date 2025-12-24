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

package com.android.launcher3.widgetpicker.ui.components

import android.graphics.Color
import android.platform.test.rule.DisableAnimationsRule
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.goldenpathmanager.WidgetPickerGoldenPathManager
import com.android.launcher3.widgetpicker.shared.model.AppIconBadge
import com.android.launcher3.widgetpicker.tests.R
import com.android.launcher3.widgetpicker.ui.components.WidgetAppIconTest.Companion.TestBadge
import com.android.launcher3.widgetpicker.ui.testdata.ScreenshotTestData.Companion.createBitmapIcon
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
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
class WidgetAppIconTest(emulationSpec: DeviceEmulationSpec) {
    @get:Rule(order = 0)
    val disableAnimationsRule = DisableAnimationsRule()

    @get:Rule(order = 1)
    val screenshotRule =
        ComposeScreenshotTestRule(
            emulationSpec,
            WidgetPickerGoldenPathManager(getEmulatedDevicePathConfig(emulationSpec)),
        )

    @Test
    fun widgetPickerAppIcons() {
        screenshotRule.screenshotTest("widgetPickerAppIcons") {
            WidgetAppIconsPreview()
        }
    }

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getTestSpecs(): List<DeviceEmulationSpec> {
            return DeviceEmulationSpec.forDisplays(
                Displays.Phone,
                isLandscape = false,
                isDarkTheme = false
            )
        }

        const val BITMAP_SIZE = 48
        const val CIRCLE_CENTER = BITMAP_SIZE / 2f
        const val ROUND_RECT_RADIUS = 10f

        val TestBadge = AppIconBadge.DrawableBadge(
            R.drawable.test_badge,
            tintColor = android.R.color.holo_blue_dark
        )
    }
}

@Preview
@Composable
private fun WidgetAppIconsPreview() {
    WidgetPickerTheme {
        FlowRow(
            modifier = Modifier
                .wrapContentSize()
                .background(WidgetPickerTheme.colors.expandableListItemsBackground)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (size in listOf(AppIconSize.MEDIUM, AppIconSize.SMALL)) {
                // Full bleed; no badge
                WidgetAppIcon(
                    size = size,
                    widgetAppIcon = com.android.launcher3.widgetpicker.shared.model.WidgetAppIcon(
                        icon = createBitmapIcon(color = Color.GREEN, fullBleed = true),
                        badge = AppIconBadge.NoBadge
                    )
                )
                // Full bleed and a badge
                WidgetAppIcon(
                    size = size,
                    widgetAppIcon = com.android.launcher3.widgetpicker.shared.model.WidgetAppIcon(
                        icon = createBitmapIcon(color = Color.YELLOW, fullBleed = true),
                        badge = TestBadge
                    )
                )
                // Full bleed with circle shape and a badge
                WidgetAppIcon(
                    iconShape = CircleShape,
                    size = size,
                    widgetAppIcon = com.android.launcher3.widgetpicker.shared.model.WidgetAppIcon(
                        icon = createBitmapIcon(color = Color.RED, fullBleed = true),
                        badge = TestBadge
                    )
                )
                // non full bleed i.e. pre-shaped; and a badge
                WidgetAppIcon(
                    size = size,
                    widgetAppIcon = com.android.launcher3.widgetpicker.shared.model.WidgetAppIcon(
                        icon = createBitmapIcon(color = Color.BLUE, fullBleed = false),
                        badge = TestBadge
                    )
                )
            }
        }
    }
}
