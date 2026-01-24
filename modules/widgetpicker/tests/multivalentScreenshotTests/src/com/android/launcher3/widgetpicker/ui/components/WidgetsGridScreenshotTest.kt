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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.goldenpathmanager.WidgetPickerGoldenPathManager
import com.android.launcher3.widgetpicker.shared.model.WidgetId
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview
import com.android.launcher3.widgetpicker.ui.model.WidgetSizeGroup
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
class WidgetsGridScreenshotTest(emulationSpec: DeviceEmulationSpec) {
    @get:Rule(order = 0) val disableAnimationsRule = DisableAnimationsRule()

    @get:Rule(order = 1)
    val screenshotRule =
        ComposeScreenshotTestRule(
            emulationSpec,
            WidgetPickerGoldenPathManager(getEmulatedDevicePathConfig(emulationSpec)),
        )

    @Test
    fun widgetsGrid_multipleSizeGroups() {
        screenshotRule.screenshotTest("widgetsGrid_multipleSizeGroups") {
            MultipleSizeGroupsPreview()
        }
    }

    @Test
    fun widgetsGrid_oneByOneWidgets() {
        screenshotRule.screenshotTest("widgetsGrid_oneByOneWidgets") { OneByOneWidgetsPreview() }
    }

    @Test
    fun widgetsGrid_remoteViews() {
        screenshotRule.screenshotTest("widgetsGrid_remoteViews") { RemoteViewWidgetsPreview() }
    }

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getTestSpecs(): List<DeviceEmulationSpec> {
            return DeviceEmulationSpec.forDisplays(
                Displays.Phone,
                isDarkTheme = false,
                isLandscape = false,
            )
        }
    }
}

@Preview(widthDp = 420)
@Composable
private fun MultipleSizeGroupsPreview() {
    val testWidth = 420.dp
    val testWidthPx = with(LocalDensity.current) { testWidth.roundToPx() }
    val sample = WidgetsGridTestSamples.varyingSizedWidgets(testWidthPx)

    GridPreview(groups = sample.widgetSizeGroups, testWidth = testWidth, previews = sample.previews)
}

@Preview(widthDp = 420)
@Composable
private fun OneByOneWidgetsPreview() {
    val testWidth = 420.dp
    val testWidthPx = with(LocalDensity.current) { testWidth.roundToPx() }
    val sample = WidgetsGridTestSamples.oneByOneWidgets(testWidthPx)

    GridPreview(groups = sample.widgetSizeGroups, testWidth = testWidth, previews = sample.previews)
}

@Preview(widthDp = 420)
@Composable
private fun RemoteViewWidgetsPreview() {
    val testWidth = 420.dp
    val testWidthPx = with(LocalDensity.current) { testWidth.roundToPx() }
    val sample =
        WidgetsGridTestSamples.remoteViewWidgets(
            packageName = LocalContext.current.packageName,
            screenWidth = testWidthPx,
        )

    GridPreview(groups = sample.widgetSizeGroups, testWidth = testWidth, previews = sample.previews)
}

@Composable
private fun GridPreview(
    groups: List<WidgetSizeGroup>,
    previews: Map<WidgetId, WidgetPreview>,
    testWidth: Dp,
) {
    WidgetPickerTheme {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier.width(testWidth)
                    .wrapContentHeight()
                    .background(MaterialTheme.colorScheme.surfaceBright),
        ) {
            WidgetsGrid(
                widgetSizeGroups = groups,
                showAllWidgetDetails = true,
                showDragShadow = false,
                previews = previews,
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                onWidgetInteraction = {}
            )
        }
    }
}
