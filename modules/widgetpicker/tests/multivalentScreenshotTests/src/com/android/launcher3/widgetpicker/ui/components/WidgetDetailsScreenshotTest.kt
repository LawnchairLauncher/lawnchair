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
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.goldenpathmanager.WidgetPickerGoldenPathManager
import com.android.launcher3.widgetpicker.ui.WidgetInteractionSource
import com.android.launcher3.widgetpicker.ui.components.WidgetsDetailsScreenshotTest.Companion.TAG
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
class WidgetsDetailsScreenshotTest(emulationSpec: DeviceEmulationSpec) {
    @get:Rule(order = 0) val disableAnimationsRule = DisableAnimationsRule()

    @get:Rule(order = 1)
    val screenshotRule =
        ComposeScreenshotTestRule(
            emulationSpec,
            WidgetPickerGoldenPathManager(getEmulatedDevicePathConfig(emulationSpec)),
        )

    @Test
    fun focusedState_detailsFocused() {
        screenshotRule.screenshotTest(
            goldenIdentifier = "focusedState_detailsFocused",
            beforeScreenshot = {
                screenshotRule.composeRule.onRoot().performKeyPress(keyEvent = TAB_EVENT)
            },
        ) {
            TestWithKeyboardInputMode { DetailsFocusedStateTest() }
        }
    }

    @Test
    fun focusedState_addButtonFocused() {
        screenshotRule.screenshotTest("focusedStates_addButtonFocused") {
            TestWithKeyboardInputMode { AddButtonFocusedTest() }
        }
    }

    @Test
    fun addButton_sizes() {
        screenshotRule.screenshotTest("addButton_sizes") { AddButtonSizesTestContent() }
    }

    companion object {
        const val TAG = "WidgetDetailsScreenshotTest"

        @Parameters(name = "{0}")
        @JvmStatic
        fun getTestSpecs(): List<DeviceEmulationSpec> {
            return DeviceEmulationSpec.forDisplays(
                // Use high density to mimic larger display size
                Displays.Phone.copy(densityDpi = 700),
                isDarkTheme = false,
                isLandscape = false,
            )
        }

        private val TAB_EVENT =
            KeyEvent(NativeKeyEvent(NativeKeyEvent.ACTION_DOWN, NativeKeyEvent.KEYCODE_TAB))
    }
}

@Preview
@Composable
private fun AddButtonFocusedTest() {
    FocusedStatesTestContent(showAddButton = true)
}

@Preview
@Composable
private fun DetailsFocusedStateTest() {
    FocusedStatesTestContent(showAddButton = false)
}

@Composable
private fun FocusedStatesTestContent(showAddButton: Boolean) {
    WidgetPickerTheme {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier =
                Modifier.wrapContentSize()
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surfaceBright),
        ) {
            WidgetDetailsTestContent(cellWidth = 500, showAddButton = showAddButton)
        }
    }
}

@Preview(fontScale = 1.5f)
@Composable
private fun AddButtonSizesTestContent() {
    WidgetPickerTheme {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier =
                Modifier.wrapContentSize().background(MaterialTheme.colorScheme.surfaceBright),
        ) {
            WidgetDetailsTestContent(cellWidth = 600, showAddButton = true)
            Spacer(modifier = Modifier.height(8.dp))
            WidgetDetailsTestContent(cellWidth = 250, showAddButton = true)
        }
    }
}

@Composable
private fun WidgetDetailsTestContent(showAddButton: Boolean, cellWidth: Int) {
    val density = LocalDensity.current
    val cellWidthDp = with(density) { cellWidth.toDp() }

    WidgetDetails(
        widget = WidgetsGridTestSamples.twoByTwo(cellWidth = cellWidth, 200),
        appIcon = null,
        showAllDetails = true,
        showAddButton = showAddButton,
        widgetInteractionSource = WidgetInteractionSource.FEATURED,
        onWidgetAddClick = { Log.i(TAG, "Add button click") },
        onClick = {},
        onHoverChange = {},
        traversalIndex = 0,
        modifier = Modifier.width(cellWidthDp),
    )
}

@Composable
private fun TestWithKeyboardInputMode(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalInputModeManager provides keyboardMockManager, content)
}

private val keyboardMockManager =
    object : InputModeManager {
        override val inputMode = InputMode.Keyboard

        override fun requestInputMode(inputMode: InputMode) = true
    }
