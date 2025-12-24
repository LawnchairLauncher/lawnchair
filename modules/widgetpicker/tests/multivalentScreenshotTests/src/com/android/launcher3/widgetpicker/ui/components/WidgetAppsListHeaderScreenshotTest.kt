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

import android.platform.test.rule.DisableAnimationsRule
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Face2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.goldenpathmanager.WidgetPickerGoldenPathManager
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
class WidgetAppsListHeaderScreenshotTest(emulationSpec: DeviceEmulationSpec) {
    @get:Rule(order = 0) val disableAnimationsRule = DisableAnimationsRule()

    @get:Rule(order = 1)
    val screenshotRule =
        ComposeScreenshotTestRule(
            emulationSpec,
            WidgetPickerGoldenPathManager(getEmulatedDevicePathConfig(emulationSpec)),
        )

    @Test
    fun expandableListHeader_focused() {
        screenshotRule.screenshotTest(
            goldenIdentifier = "expandableListHeader_focused",
            beforeScreenshot = {
                screenshotRule.composeRule
                    .onNodeWithText(ScreenshotData.HEADER_TITLE)
                    .assertExists()
                    .requestFocus()
            },
        ) {
            CompositionLocalProvider(LocalInputModeManager provides keyboardMockManager) {
                ExpandableListHeaderFocusedStateTest()
            }
        }
    }

    @Test
    fun selectableListHeader_focused() {
        screenshotRule.screenshotTest(
            goldenIdentifier = "selectableListHeader_focused",
            beforeScreenshot = {
                screenshotRule.composeRule
                    .onNodeWithText(ScreenshotData.HEADER_TITLE_2)
                    .assertExists()
                    .requestFocus()
            },
        ) {
            CompositionLocalProvider(LocalInputModeManager provides keyboardMockManager) {
                SelectableListHeaderFocusedStateTest()
            }
        }
    }

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getTestSpecs(): List<DeviceEmulationSpec> {
            return DeviceEmulationSpec.forDisplays(Displays.Phone)
        }

        private val keyboardMockManager =
            object : InputModeManager {
                override val inputMode = InputMode.Keyboard

                override fun requestInputMode(inputMode: InputMode) = true
            }
    }
}

@Preview
@Composable
private fun ExpandableListHeaderFocusedStateTest() {
    WidgetPickerTheme {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier =
                Modifier.wrapContentSize()
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surfaceDim),
        ) {
            ExpandableListHeader(
                modifier = Modifier.padding(horizontal = 16.dp).semantics { traversalIndex = 0f },
                expanded = true,
                leadingAppIcon = {
                    Icon(imageVector = Icons.Default.Face, contentDescription = null)
                },
                title = ScreenshotData.HEADER_TITLE,
                subTitle = ScreenshotData.HEADER_SUBTITLE,
                expandedContent = {
                    Row(
                        modifier =
                            Modifier.height(200.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(text = "widget text")
                    }
                },
                onClick = {},
                shape =
                    RoundedCornerShape(
                        topStart = ScreenshotData.largeRadius,
                        topEnd = ScreenshotData.largeRadius,
                        bottomStart = ScreenshotData.smallRadius,
                        bottomEnd = ScreenshotData.smallRadius,
                    ),
            )
            ExpandableListHeader(
                modifier = Modifier.padding(horizontal = 16.dp).semantics { traversalIndex = 0f },
                expanded = false,
                leadingAppIcon = {
                    Icon(imageVector = Icons.Default.Face2, contentDescription = null)
                },
                title = ScreenshotData.HEADER_TITLE_2,
                subTitle = ScreenshotData.HEADER_SUBTITLE,
                expandedContent = {},
                onClick = {},
                shape =
                    RoundedCornerShape(
                        topStart = ScreenshotData.smallRadius,
                        topEnd = ScreenshotData.smallRadius,
                        bottomStart = ScreenshotData.largeRadius,
                        bottomEnd = ScreenshotData.largeRadius,
                    ),
            )
        }
    }
}

@Preview
@Composable
private fun SelectableListHeaderFocusedStateTest() {
    WidgetPickerTheme {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.wrapContentSize().padding(16.dp),
        ) {
            SelectableListHeader(
                modifier = Modifier.semantics { traversalIndex = 0f },
                selected = false,
                leadingAppIcon = {
                    Icon(imageVector = Icons.Default.Face, contentDescription = null)
                },
                title = ScreenshotData.HEADER_TITLE,
                subTitle = ScreenshotData.HEADER_SUBTITLE,
                onSelect = {},
                shape = RoundedCornerShape(ScreenshotData.largeRadius),
            )
            SelectableListHeader(
                modifier = Modifier.semantics { traversalIndex = 0f },
                selected = true,
                leadingAppIcon = {
                    Icon(imageVector = Icons.Default.Face2, contentDescription = null)
                },
                title = ScreenshotData.HEADER_TITLE_2,
                subTitle = ScreenshotData.HEADER_SUBTITLE,
                onSelect = {},
                shape = RoundedCornerShape(ScreenshotData.largeRadius),
            )
        }
    }
}

private object ScreenshotData {
    val largeRadius = 24.dp
    val smallRadius = 4.dp

    const val HEADER_TITLE = "App header"
    const val HEADER_TITLE_2 = "Another App header"
    const val HEADER_SUBTITLE = "2 widgets"
}
