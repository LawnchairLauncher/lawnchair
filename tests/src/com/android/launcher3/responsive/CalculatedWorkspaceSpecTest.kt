/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.launcher3.responsive

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.AbstractDeviceProfileTest
import com.android.launcher3.responsive.ResponsiveSpec.Companion.ResponsiveSpecType
import com.android.launcher3.responsive.ResponsiveSpec.DimensionType
import com.android.launcher3.tests.R as TestR
import com.android.launcher3.util.TestResourceHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class CalculatedWorkspaceSpecTest : AbstractDeviceProfileTest() {
    override val runningContext: Context = InstrumentationRegistry.getInstrumentation().context

    /**
     * This test tests:
     * - (height spec) gets the correct breakpoint from the XML - skips the first 2 breakpoints
     * - (height spec) do the correct calculations for available space and fixed size
     * - (width spec) do the correct calculations for remainder space and fixed size
     */
    @Test
    fun normalPhone_returnsThirdBreakpointSpec() {
        val deviceSpec = deviceSpecs["phone"]!!
        val aspectRatio = deviceSpec.naturalSize.first.toFloat() / deviceSpec.naturalSize.second
        initializeVarsForPhone(deviceSpec)

        val availableWidth = deviceSpec.naturalSize.first
        // Hotseat size is roughly 495px on a real device,
        // it doesn't need to be precise on unit tests
        val availableHeight = deviceSpec.naturalSize.second - deviceSpec.statusBarNaturalPx - 495

        val workspaceSpecs =
            ResponsiveSpecsProvider.create(
                TestResourceHelper(context, TestR.xml.valid_workspace_file),
                ResponsiveSpecType.Workspace
            )
        val widthSpec =
            workspaceSpecs.getCalculatedSpec(aspectRatio, DimensionType.WIDTH, 4, availableWidth)
        val heightSpec =
            workspaceSpecs.getCalculatedSpec(aspectRatio, DimensionType.HEIGHT, 5, availableHeight)

        assertThat(widthSpec.availableSpace).isEqualTo(availableWidth)
        assertThat(widthSpec.cells).isEqualTo(4)
        assertThat(widthSpec.startPaddingPx).isEqualTo(58)
        assertThat(widthSpec.endPaddingPx).isEqualTo(58)
        assertThat(widthSpec.gutterPx).isEqualTo(42)
        assertThat(widthSpec.cellSizePx).isEqualTo(210)

        assertThat(heightSpec.availableSpace).isEqualTo(availableHeight)
        assertThat(heightSpec.cells).isEqualTo(5)
        assertThat(heightSpec.startPaddingPx).isEqualTo(21)
        assertThat(heightSpec.endPaddingPx).isEqualTo(233)
        assertThat(heightSpec.gutterPx).isEqualTo(42)
        assertThat(heightSpec.cellSizePx).isEqualTo(273)
    }

    /**
     * This test tests:
     * - (height spec) gets the correct breakpoint from the XML - use the first breakpoint
     * - (height spec) do the correct calculations for remainder space and fixed size
     * - (width spec) do the correct calculations for remainder space and fixed size
     */
    @Test
    fun smallPhone_returnsFirstBreakpointSpec() {
        val deviceSpec = deviceSpecs["phone"]!!
        val aspectRatio = deviceSpec.naturalSize.first.toFloat() / deviceSpec.naturalSize.second
        deviceSpec.densityDpi = 540 // larger display size
        initializeVarsForPhone(deviceSpec)

        val availableWidth = deviceSpec.naturalSize.first
        // Hotseat size is roughly 640px on a real device,
        // it doesn't need to be precise on unit tests
        val availableHeight = deviceSpec.naturalSize.second - deviceSpec.statusBarNaturalPx - 640

        val workspaceSpecs =
            ResponsiveSpecsProvider.create(
                TestResourceHelper(context, TestR.xml.valid_workspace_file),
                ResponsiveSpecType.Workspace
            )
        val widthSpec =
            workspaceSpecs.getCalculatedSpec(aspectRatio, DimensionType.WIDTH, 4, availableWidth)
        val heightSpec =
            workspaceSpecs.getCalculatedSpec(aspectRatio, DimensionType.HEIGHT, 5, availableHeight)

        assertThat(widthSpec.availableSpace).isEqualTo(availableWidth)
        assertThat(widthSpec.cells).isEqualTo(4)
        assertThat(widthSpec.startPaddingPx).isEqualTo(74)
        assertThat(widthSpec.endPaddingPx).isEqualTo(74)
        assertThat(widthSpec.gutterPx).isEqualTo(54)
        assertThat(widthSpec.cellSizePx).isEqualTo(193)

        assertThat(heightSpec.availableSpace).isEqualTo(availableHeight)
        assertThat(heightSpec.cells).isEqualTo(5)
        assertThat(heightSpec.startPaddingPx).isEqualTo(0)
        assertThat(heightSpec.endPaddingPx).isEqualTo(108)
        assertThat(heightSpec.gutterPx).isEqualTo(54)
        assertThat(heightSpec.cellSizePx).isEqualTo(260)
    }

    /**
     * This test tests:
     * - (height spec) gets the correct breakpoint from the XML - use the first breakpoint
     * - (height spec) do the correct calculations for remainder space and fixed size
     * - (width spec) do the correct calculations for remainder space and fixed size
     */
    @Test
    fun smallPhone_returnsFirstBreakpointSpec_unsortedFile() {
        val deviceSpec = deviceSpecs["phone"]!!
        val aspectRatio = deviceSpec.naturalSize.first.toFloat() / deviceSpec.naturalSize.second
        deviceSpec.densityDpi = 540 // larger display size
        initializeVarsForPhone(deviceSpec)

        val availableWidth = deviceSpec.naturalSize.first
        // Hotseat size is roughly 640px on a real device,
        // it doesn't need to be precise on unit tests
        val availableHeight = deviceSpec.naturalSize.second - deviceSpec.statusBarNaturalPx - 640
        val workspaceSpecs =
            ResponsiveSpecsProvider.create(
                TestResourceHelper(context, TestR.xml.valid_workspace_unsorted_file),
                ResponsiveSpecType.Workspace
            )
        val widthSpec =
            workspaceSpecs.getCalculatedSpec(aspectRatio, DimensionType.WIDTH, 4, availableWidth)
        val heightSpec =
            workspaceSpecs.getCalculatedSpec(aspectRatio, DimensionType.HEIGHT, 5, availableHeight)

        assertThat(widthSpec.availableSpace).isEqualTo(availableWidth)
        assertThat(widthSpec.cells).isEqualTo(4)
        assertThat(widthSpec.startPaddingPx).isEqualTo(74)
        assertThat(widthSpec.endPaddingPx).isEqualTo(74)
        assertThat(widthSpec.gutterPx).isEqualTo(54)
        assertThat(widthSpec.cellSizePx).isEqualTo(193)

        assertThat(heightSpec.availableSpace).isEqualTo(availableHeight)
        assertThat(heightSpec.cells).isEqualTo(5)
        assertThat(heightSpec.startPaddingPx).isEqualTo(0)
        assertThat(heightSpec.endPaddingPx).isEqualTo(108)
        assertThat(heightSpec.gutterPx).isEqualTo(54)
        assertThat(heightSpec.cellSizePx).isEqualTo(260)
    }
}
