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
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.AbstractDeviceProfileTest
import com.android.launcher3.responsive.ResponsiveSpec.Companion.ResponsiveSpecType
import com.android.launcher3.util.TestResourceHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ResponsiveCellSpecsProviderTest : AbstractDeviceProfileTest() {
    override val runningContext: Context = InstrumentationRegistry.getInstrumentation().context
    val deviceSpec = deviceSpecs["phone"]!!
    val aspectRatio = deviceSpec.naturalSize.first.toFloat() / deviceSpec.naturalSize.second

    @Before
    fun setup() {
        initializeVarsForPhone(deviceSpec)
    }

    @Test
    fun parseValidFile() {
        val testResourceHelper = TestResourceHelper(context, "valid_cell_specs_file".xmlToId())
        val provider = ResponsiveCellSpecsProvider.create(testResourceHelper)

        // Validate Portrait
        val aspectRatioPortrait = 1.0f
        val expectedPortraitSpecs =
            listOf(
                CellSpec(
                    maxAvailableSize = 606.dpToPx(),
                    dimensionType = ResponsiveSpec.DimensionType.HEIGHT,
                    specType = ResponsiveSpecType.Cell,
                    iconSize = SizeSpec(48f.dpToPx()),
                    iconTextSize = SizeSpec(12f.dpToPx()),
                    iconDrawablePadding = SizeSpec(8f.dpToPx()),
                    iconTextMaxLineCount = 1,
                    iconTextMaxLineCountMatchesWorkspace = false,
                ),
                CellSpec(
                    maxAvailableSize = 9999.dpToPx(),
                    dimensionType = ResponsiveSpec.DimensionType.HEIGHT,
                    specType = ResponsiveSpecType.Cell,
                    iconSize = SizeSpec(52f.dpToPx()),
                    iconTextSize = SizeSpec(12f.dpToPx()),
                    iconDrawablePadding = SizeSpec(11f.dpToPx()),
                    iconTextMaxLineCount = 1,
                    iconTextMaxLineCountMatchesWorkspace = false,
                ),
            )

        val portraitSpecs = provider.getSpecsByAspectRatio(aspectRatioPortrait)

        assertThat(portraitSpecs.aspectRatio).isAtLeast(aspectRatioPortrait)
        assertThat(portraitSpecs.widthSpecs.size).isEqualTo(0)
        assertThat(portraitSpecs.heightSpecs.size).isEqualTo(2)
        assertThat(portraitSpecs.heightSpecs[0]).isEqualTo(expectedPortraitSpecs[0])
        assertThat(portraitSpecs.heightSpecs[1]).isEqualTo(expectedPortraitSpecs[1])

        // Validate Landscape
        val aspectRatioLandscape = 1.051f
        val expectedLandscapeSpec =
            CellSpec(
                maxAvailableSize = 9999.dpToPx(),
                dimensionType = ResponsiveSpec.DimensionType.HEIGHT,
                specType = ResponsiveSpecType.Cell,
                iconSize = SizeSpec(52f.dpToPx()),
                iconTextSize = SizeSpec(0f),
                iconDrawablePadding = SizeSpec(0f),
                iconTextMaxLineCount = 1,
                iconTextMaxLineCountMatchesWorkspace = false,
            )
        val landscapeSpecs = provider.getSpecsByAspectRatio(aspectRatioLandscape)

        assertThat(landscapeSpecs.aspectRatio).isAtLeast(aspectRatioLandscape)
        assertThat(landscapeSpecs.widthSpecs.size).isEqualTo(0)
        assertThat(landscapeSpecs.heightSpecs.size).isEqualTo(1)
        assertThat(landscapeSpecs.heightSpecs[0]).isEqualTo(expectedLandscapeSpec)
    }

    @Test
    @EnableFlags(com.android.launcher3.Flags.FLAG_ENABLE_SCALABILITY_FOR_DESKTOP_EXPERIENCE)
    fun parseValidFileWithMaxLineCount() {
        val testResourceHelper =
            TestResourceHelper(context, "valid_cell_specs_file_with_max_line_count".xmlToId())
        val provider = ResponsiveCellSpecsProvider.create(testResourceHelper)

        // Validate Portrait
        val aspectRatioPortrait = 1.0f
        val expectedPortraitSpecs =
            listOf(
                CellSpec(
                    maxAvailableSize = 9999.dpToPx(),
                    dimensionType = ResponsiveSpec.DimensionType.HEIGHT,
                    specType = ResponsiveSpecType.Cell,
                    iconSize = SizeSpec(52f.dpToPx()),
                    iconTextSize = SizeSpec(12f.dpToPx()),
                    iconDrawablePadding = SizeSpec(11f.dpToPx()),
                    iconTextMaxLineCount = 2,
                    iconTextMaxLineCountMatchesWorkspace = false,
                )
            )

        val portraitSpecs = provider.getSpecsByAspectRatio(aspectRatioPortrait)

        assertThat(portraitSpecs.aspectRatio).isAtLeast(aspectRatioPortrait)
        assertThat(portraitSpecs.widthSpecs.size).isEqualTo(0)
        assertThat(portraitSpecs.heightSpecs.size).isEqualTo(1)
        assertThat(portraitSpecs.heightSpecs[0]).isEqualTo(expectedPortraitSpecs[0])

        // Validate Landscape
        val aspectRatioLandscape = 1.051f
        val expectedLandscapeSpec =
            CellSpec(
                maxAvailableSize = 9999.dpToPx(),
                dimensionType = ResponsiveSpec.DimensionType.HEIGHT,
                specType = ResponsiveSpecType.Cell,
                iconSize = SizeSpec(52f.dpToPx()),
                iconTextSize = SizeSpec(12f.dpToPx()),
                iconDrawablePadding = SizeSpec(4f.dpToPx()),
                iconTextMaxLineCount = 2,
                iconTextMaxLineCountMatchesWorkspace = false,
            )
        val landscapeSpecs = provider.getSpecsByAspectRatio(aspectRatioLandscape)

        assertThat(landscapeSpecs.aspectRatio).isAtLeast(aspectRatioLandscape)
        assertThat(landscapeSpecs.widthSpecs.size).isEqualTo(0)
        assertThat(landscapeSpecs.heightSpecs.size).isEqualTo(1)
        assertThat(landscapeSpecs.heightSpecs[0]).isEqualTo(expectedLandscapeSpec)
    }

    @Test
    @DisableFlags(com.android.launcher3.Flags.FLAG_ENABLE_SCALABILITY_FOR_DESKTOP_EXPERIENCE)
    fun parseValidFileWithMaxLineCount_flagDisabled() {
        val testResourceHelper =
            TestResourceHelper(context, "valid_cell_specs_file_with_max_line_count".xmlToId())
        val provider = ResponsiveCellSpecsProvider.create(testResourceHelper)

        // Validate Portrait
        val aspectRatioPortrait = 1.0f
        val expectedPortraitSpecs =
            listOf(
                CellSpec(
                    maxAvailableSize = 9999.dpToPx(),
                    dimensionType = ResponsiveSpec.DimensionType.HEIGHT,
                    specType = ResponsiveSpecType.Cell,
                    iconSize = SizeSpec(52f.dpToPx()),
                    iconTextSize = SizeSpec(12f.dpToPx()),
                    iconDrawablePadding = SizeSpec(11f.dpToPx()),
                    iconTextMaxLineCount = 1,
                    iconTextMaxLineCountMatchesWorkspace = false,
                )
            )

        val portraitSpecs = provider.getSpecsByAspectRatio(aspectRatioPortrait)

        assertThat(portraitSpecs.aspectRatio).isAtLeast(aspectRatioPortrait)
        assertThat(portraitSpecs.widthSpecs.size).isEqualTo(0)
        assertThat(portraitSpecs.heightSpecs.size).isEqualTo(1)
        assertThat(portraitSpecs.heightSpecs[0]).isEqualTo(expectedPortraitSpecs[0])

        // Validate Landscape
        val aspectRatioLandscape = 1.051f
        val expectedLandscapeSpec =
            CellSpec(
                maxAvailableSize = 9999.dpToPx(),
                dimensionType = ResponsiveSpec.DimensionType.HEIGHT,
                specType = ResponsiveSpecType.Cell,
                iconSize = SizeSpec(52f.dpToPx()),
                iconTextSize = SizeSpec(12f.dpToPx()),
                iconDrawablePadding = SizeSpec(4f.dpToPx()),
                iconTextMaxLineCount = 1,
                iconTextMaxLineCountMatchesWorkspace = false,
            )
        val landscapeSpecs = provider.getSpecsByAspectRatio(aspectRatioLandscape)

        assertThat(landscapeSpecs.aspectRatio).isAtLeast(aspectRatioLandscape)
        assertThat(landscapeSpecs.widthSpecs.size).isEqualTo(0)
        assertThat(landscapeSpecs.heightSpecs.size).isEqualTo(1)
        assertThat(landscapeSpecs.heightSpecs[0]).isEqualTo(expectedLandscapeSpec)
    }

    @Test
    fun parseValidFile_matchesWorkspace() {
        val testWorkspaceResourceHelper =
            TestResourceHelper(context, "valid_cell_specs_file_with_max_line_count".xmlToId())
        val workspaceProvider = ResponsiveCellSpecsProvider.create(testWorkspaceResourceHelper)

        val testResourceHelper =
            TestResourceHelper(context, "valid_cell_specs_matches_workspace".xmlToId())
        val provider = ResponsiveCellSpecsProvider.create(testResourceHelper)

        // Validate Landscape
        val aspectRatioLandscape = 1.051f
        val landscapeWorkspaceSpecs = workspaceProvider.getSpecsByAspectRatio(aspectRatioLandscape)
        assertThat(landscapeWorkspaceSpecs.heightSpecs.size).isEqualTo(1)
        val calculatedWorkspaceSpec =
            CalculatedCellSpec(800.dpToPx(), landscapeWorkspaceSpecs.heightSpecs[0])

        val landscapeSpecs = provider.getSpecsByAspectRatio(aspectRatioLandscape)
        assertThat(landscapeSpecs.heightSpecs.size).isEqualTo(1)

        val calculatedCellSpec =
            CalculatedCellSpec(800.dpToPx(), landscapeSpecs.heightSpecs[0], calculatedWorkspaceSpec)

        assertThat(calculatedCellSpec.availableSpace).isEqualTo(800.dpToPx())
        assertThat(calculatedCellSpec.iconSize).isEqualTo(52.dpToPx())
        assertThat(calculatedCellSpec.iconTextSize).isEqualTo(12.dpToPx())
        assertThat(calculatedCellSpec.iconDrawablePadding).isEqualTo(4.dpToPx())
        assertThat(calculatedCellSpec.iconTextMaxLineCount).isEqualTo(1)
        assertThat(calculatedCellSpec.iconTextMaxLineCountMatchesWorkspace).isFalse()
    }

    @Test
    @EnableFlags(com.android.launcher3.Flags.FLAG_ENABLE_SCALABILITY_FOR_DESKTOP_EXPERIENCE)
    fun parseValidFile_matchesWorkspaceIncludingMaxLines() {
        val testWorkspaceResourceHelper =
            TestResourceHelper(context, "valid_cell_specs_file_with_max_line_count".xmlToId())
        val workspaceProvider = ResponsiveCellSpecsProvider.create(testWorkspaceResourceHelper)

        val testResourceHelper =
            TestResourceHelper(
                context,
                "valid_cell_specs_matches_workspace_with_max_lines".xmlToId(),
            )
        val provider = ResponsiveCellSpecsProvider.create(testResourceHelper)

        // Validate Landscape
        val aspectRatioLandscape = 1.051f
        val landscapeWorkspaceSpecs = workspaceProvider.getSpecsByAspectRatio(aspectRatioLandscape)
        assertThat(landscapeWorkspaceSpecs.heightSpecs.size).isEqualTo(1)
        val calculatedWorkspaceSpec =
            CalculatedCellSpec(800.dpToPx(), landscapeWorkspaceSpecs.heightSpecs[0])

        val landscapeSpecs = provider.getSpecsByAspectRatio(aspectRatioLandscape)
        assertThat(landscapeSpecs.heightSpecs.size).isEqualTo(1)

        val calculatedCellSpec =
            CalculatedCellSpec(800.dpToPx(), landscapeSpecs.heightSpecs[0], calculatedWorkspaceSpec)

        assertThat(calculatedCellSpec.availableSpace).isEqualTo(800.dpToPx())
        assertThat(calculatedCellSpec.iconSize).isEqualTo(52.dpToPx())
        assertThat(calculatedCellSpec.iconTextSize).isEqualTo(12.dpToPx())
        assertThat(calculatedCellSpec.iconDrawablePadding).isEqualTo(4.dpToPx())
        assertThat(calculatedCellSpec.iconTextMaxLineCount).isEqualTo(2)
        assertThat(calculatedCellSpec.iconTextMaxLineCountMatchesWorkspace).isTrue()
    }

    @Test
    @DisableFlags(com.android.launcher3.Flags.FLAG_ENABLE_SCALABILITY_FOR_DESKTOP_EXPERIENCE)
    fun parseValidFile_matchesWorkspaceIncludingMaxLines_flagDisabled() {
        val testWorkspaceResourceHelper =
            TestResourceHelper(context, "valid_cell_specs_file_with_max_line_count".xmlToId())
        val workspaceProvider = ResponsiveCellSpecsProvider.create(testWorkspaceResourceHelper)

        val testResourceHelper =
            TestResourceHelper(
                context,
                "valid_cell_specs_matches_workspace_with_max_lines".xmlToId(),
            )
        val provider = ResponsiveCellSpecsProvider.create(testResourceHelper)

        // Validate Landscape
        val aspectRatioLandscape = 1.051f
        val landscapeWorkspaceSpecs = workspaceProvider.getSpecsByAspectRatio(aspectRatioLandscape)
        assertThat(landscapeWorkspaceSpecs.heightSpecs.size).isEqualTo(1)
        val calculatedWorkspaceSpec =
            CalculatedCellSpec(800.dpToPx(), landscapeWorkspaceSpecs.heightSpecs[0])

        val landscapeSpecs = provider.getSpecsByAspectRatio(aspectRatioLandscape)
        assertThat(landscapeSpecs.heightSpecs.size).isEqualTo(1)

        val calculatedCellSpec =
            CalculatedCellSpec(800.dpToPx(), landscapeSpecs.heightSpecs[0], calculatedWorkspaceSpec)

        assertThat(calculatedCellSpec.availableSpace).isEqualTo(800.dpToPx())
        assertThat(calculatedCellSpec.iconSize).isEqualTo(52.dpToPx())
        assertThat(calculatedCellSpec.iconTextSize).isEqualTo(12.dpToPx())
        assertThat(calculatedCellSpec.iconDrawablePadding).isEqualTo(4.dpToPx())
        assertThat(calculatedCellSpec.iconTextMaxLineCount).isEqualTo(1)
        assertThat(calculatedCellSpec.iconTextMaxLineCountMatchesWorkspace).isFalse()
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_IsNotFixedSizeOrMatchWorkspace_throwsError() {
        ResponsiveCellSpecsProvider.create(
            TestResourceHelper(context, "invalid_cell_specs_1".xmlToId())
        )
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_dimensionTypeIsNotHeight_throwsError() {
        ResponsiveCellSpecsProvider.create(
            TestResourceHelper(context, "invalid_cell_specs_2".xmlToId())
        )
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_invalidFixedSize_throwsError() {
        ResponsiveCellSpecsProvider.create(
            TestResourceHelper(context, "invalid_cell_specs_3".xmlToId())
        )
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_invalidFixedSizeWithMultiline_throwsError() {
        ResponsiveCellSpecsProvider.create(
            TestResourceHelper(context, "invalid_cell_specs_4".xmlToId())
        )
    }
}
