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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.goldenpathmanager.WidgetPickerGoldenPathManager
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import org.junit.Ignore
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
@Ignore("b/418064758: Re-enable when flaky test is fixed.")
class FloatingToolbarScreenshotTest(emulationSpec: DeviceEmulationSpec) {
    @get:Rule(order = 0) val disableAnimationsRule = DisableAnimationsRule()

    @get:Rule(order = 1)
    val screenshotRule =
        ComposeScreenshotTestRule(
            emulationSpec,
            WidgetPickerGoldenPathManager(getEmulatedDevicePathConfig(emulationSpec)),
        )

    @Test
    fun scrollableFloatingToolbar_twoTabs() {
        screenshotRule.screenshotTest("scrollableFloatingToolbar_twoTabs") { TwoTabsPreview() }
    }

    @Test
    fun scrollableFloatingToolbar_threeTabs() {
        screenshotRule.screenshotTest("scrollableFloatingToolbar_threeTabs") { ThreeTabsPreview() }
    }

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getTestSpecs(): List<DeviceEmulationSpec> {
            return DeviceEmulationSpec.forDisplays(Displays.Phone, isLandscape = false)
        }
    }
}

@Preview
@Composable
private fun TwoTabsPreview() {
    var selectedIndexOne: Int by remember { mutableStateOf(0) }
    var selectedIndexTwo: Int by remember { mutableStateOf(1) }

    WidgetPickerTheme {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier.background(
                        MaterialTheme.colorScheme.surfaceContainer
                    ) // uses bg color of picker
                    .padding(8.dp),
        ) {
            TestComposable(
                tabs = TWO_TABS,
                selectedIndex = selectedIndexOne,
                onClick = { selectedIndexOne = it },
            )
            TestSpacer()
            TestComposable(
                tabs = TWO_TABS,
                selectedIndex = selectedIndexTwo,
                onClick = { selectedIndexTwo = it },
            )
            TestSpacer()
            TestComposable(
                tabs = TWO_LONG_TABS,
                selectedIndex = selectedIndexOne,
                onClick = { selectedIndexOne = it },
            )
            TestSpacer()
            TestComposable(
                tabs = TWO_LONG_TABS,
                selectedIndex = selectedIndexTwo,
                onClick = { selectedIndexTwo = it },
            )
        }
    }
}

@Preview
@Composable
private fun ThreeTabsPreview() {
    var selectedIndexOne: Int by remember { mutableStateOf(0) }
    var selectedIndexTwo: Int by remember { mutableStateOf(1) }
    var selectedIndexThree: Int by remember { mutableStateOf(2) }

    WidgetPickerTheme {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier.background(
                        MaterialTheme.colorScheme.surfaceContainer
                    ) // uses bg color of picker
                    .padding(8.dp),
        ) {
            TestComposable(
                tabs = THREE_TABS,
                selectedIndex = selectedIndexOne,
                onClick = { selectedIndexOne = it },
            )
            TestSpacer()
            TestComposable(
                tabs = THREE_TABS,
                selectedIndex = selectedIndexTwo,
                onClick = { selectedIndexTwo = it },
            )
            TestSpacer()
            TestComposable(
                tabs = THREE_TABS,
                selectedIndex = selectedIndexThree,
                onClick = { selectedIndexThree = it },
            )
            TestSpacer()
            TestComposable(
                tabs = THREE_LONG_TABS,
                selectedIndex = selectedIndexOne,
                onClick = { selectedIndexOne = it },
            )
            TestSpacer()
            TestComposable(
                tabs = THREE_LONG_TABS,
                selectedIndex = selectedIndexTwo,
                onClick = { selectedIndexTwo = it },
            )
            TestSpacer()
            TestComposable(
                tabs = THREE_LONG_TABS,
                selectedIndex = selectedIndexThree,
                onClick = { selectedIndexThree = it },
            )
        }
    }
}

@Composable
private fun TestComposable(
    tabs: List<FloatingToolbarTestItems>,
    selectedIndex: Int,
    onClick: (id: Int) -> Unit = {},
) {
    ScrollableFloatingToolbar(
        modifier = Modifier.wrapContentSize(align = Alignment.Center),
        selectedTabIndex = selectedIndex,
        shadowElevation = 0.dp,
        tabs =
            tabs.map {
                {
                    LeadingIconToolbarTab(
                        label = it.label,
                        leadingIcon = it.leadingIcon,
                        selected = it.id == selectedIndex,
                        onClick = { onClick(it.id) },
                    )
                }
            },
    )
}

@Composable
private fun TestSpacer() {
    Spacer(modifier = Modifier.height(8.dp).fillMaxWidth())
}

private data class FloatingToolbarTestItems(
    val id: Int,
    val label: String,
    val leadingIcon: ImageVector,
)

private val THREE_TABS =
    listOf(
        FloatingToolbarTestItems(id = 0, label = "Featured", leadingIcon = Icons.Filled.Star),
        FloatingToolbarTestItems(id = 1, label = "Personal", leadingIcon = Icons.Filled.Person),
        FloatingToolbarTestItems(id = 2, label = "Work", leadingIcon = Icons.Filled.Work),
    )

private val THREE_LONG_TABS =
    listOf(
        FloatingToolbarTestItems(id = 0, label = "A long tab A", leadingIcon = Icons.Filled.Star),
        FloatingToolbarTestItems(
            id = 1,
            label = "Super longer tab B",
            leadingIcon = Icons.Filled.Person,
        ),
        FloatingToolbarTestItems(
            id = 2,
            label = "Much much longer tab C",
            leadingIcon = Icons.Filled.Work,
        ),
    )

private val TWO_TABS =
    listOf(
        FloatingToolbarTestItems(id = 0, label = "Featured", leadingIcon = Icons.Filled.Star),
        FloatingToolbarTestItems(id = 1, label = "Browse", leadingIcon = Icons.Filled.Person),
    )

private val TWO_LONG_TABS =
    listOf(
        FloatingToolbarTestItems(id = 0, label = "A long tab", leadingIcon = Icons.Filled.Star),
        FloatingToolbarTestItems(
            id = 1,
            label = "Another long tab",
            leadingIcon = Icons.Filled.Person,
        ),
    )
