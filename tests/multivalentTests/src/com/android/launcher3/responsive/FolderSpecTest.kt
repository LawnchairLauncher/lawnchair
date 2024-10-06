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
import com.android.launcher3.util.TestResourceHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class FolderSpecTest : AbstractDeviceProfileTest() {
    override val runningContext: Context = InstrumentationRegistry.getInstrumentation().context
    val deviceSpec = deviceSpecs["tablet"]!!
    val aspectRatio = deviceSpec.naturalSize.first.toFloat() / deviceSpec.naturalSize.second

    @Before
    fun setup() {
        initializeVarsForPhone(deviceSpec)
    }

    @Test
    fun parseValidFile() {
        val resourceHelper = TestResourceHelper(context, "valid_folders_specs".xmlToId())
        val folderSpecs = ResponsiveSpecsProvider.create(resourceHelper, ResponsiveSpecType.Folder)
        val specs = folderSpecs.getSpecsByAspectRatio(aspectRatio)

        val sizeSpec16 = SizeSpec(16f.dpToPx())
        val widthSpecsExpected =
            listOf(
                ResponsiveSpec(
                    maxAvailableSize = 800.dpToPx(),
                    dimensionType = DimensionType.WIDTH,
                    specType = ResponsiveSpecType.Folder,
                    startPadding = sizeSpec16,
                    endPadding = sizeSpec16,
                    gutter = sizeSpec16,
                    cellSize = SizeSpec(matchWorkspace = true)
                ),
                ResponsiveSpec(
                    maxAvailableSize = 9999.dpToPx(),
                    dimensionType = DimensionType.WIDTH,
                    specType = ResponsiveSpecType.Folder,
                    startPadding = sizeSpec16,
                    endPadding = sizeSpec16,
                    gutter = sizeSpec16,
                    cellSize = SizeSpec(102f.dpToPx())
                )
            )

        val heightSpecsExpected =
            ResponsiveSpec(
                maxAvailableSize = 9999.dpToPx(),
                dimensionType = DimensionType.HEIGHT,
                specType = ResponsiveSpecType.Folder,
                startPadding = SizeSpec(24f.dpToPx()),
                endPadding = SizeSpec(64f.dpToPx()),
                gutter = sizeSpec16,
                cellSize = SizeSpec(matchWorkspace = true)
            )

        assertThat(specs.widthSpecs.size).isEqualTo(widthSpecsExpected.size)
        assertThat(specs.widthSpecs[0]).isEqualTo(widthSpecsExpected[0])
        assertThat(specs.widthSpecs[1]).isEqualTo(widthSpecsExpected[1])

        assertThat(specs.heightSpecs.size).isEqualTo(1)
        assertThat(specs.heightSpecs[0]).isEqualTo(heightSpecsExpected)
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_missingTag_throwsError() {
        val resourceHelper = TestResourceHelper(context, "invalid_folders_specs_1".xmlToId())
        ResponsiveSpecsProvider.create(resourceHelper, ResponsiveSpecType.Folder)
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_moreThanOneValuePerTag_throwsError() {
        val resourceHelper = TestResourceHelper(context, "invalid_folders_specs_2".xmlToId())
        ResponsiveSpecsProvider.create(resourceHelper, ResponsiveSpecType.Folder)
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_valueBiggerThan1_throwsError() {
        val resourceHelper = TestResourceHelper(context, "invalid_folders_specs_3".xmlToId())
        ResponsiveSpecsProvider.create(resourceHelper, ResponsiveSpecType.Folder)
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_missingSpecs_throwsError() {
        val resourceHelper = TestResourceHelper(context, "invalid_folders_specs_4".xmlToId())
        ResponsiveSpecsProvider.create(resourceHelper, ResponsiveSpecType.Folder)
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_missingWidthBreakpoint_throwsError() {
        val availableSpace = 900.dpToPx()
        val cells = 3

        val workspaceSpec =
            ResponsiveSpec(
                maxAvailableSize = availableSpace,
                dimensionType = DimensionType.WIDTH,
                specType = ResponsiveSpecType.Folder,
                startPadding = SizeSpec(fixedSize = 10f),
                endPadding = SizeSpec(fixedSize = 10f),
                gutter = SizeSpec(fixedSize = 10f),
                cellSize = SizeSpec(fixedSize = 10f)
            )
        val calculatedWorkspaceSpec =
            CalculatedResponsiveSpec(aspectRatio, availableSpace, cells, workspaceSpec)

        val resourceHelper = TestResourceHelper(context, "invalid_folders_specs_5".xmlToId())
        val folderSpecs = ResponsiveSpecsProvider.create(resourceHelper, ResponsiveSpecType.Folder)
        folderSpecs.getCalculatedSpec(
            aspectRatio,
            DimensionType.WIDTH,
            cells,
            availableSpace,
            calculatedWorkspaceSpec
        )
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_missingHeightBreakpoint_throwsError() {
        val availableSpace = 900.dpToPx()
        val cells = 3

        val workspaceSpec =
            ResponsiveSpec(
                maxAvailableSize = availableSpace,
                dimensionType = DimensionType.HEIGHT,
                specType = ResponsiveSpecType.Folder,
                startPadding = SizeSpec(fixedSize = 10f),
                endPadding = SizeSpec(fixedSize = 10f),
                gutter = SizeSpec(fixedSize = 10f),
                cellSize = SizeSpec(fixedSize = 10f)
            )
        val calculatedWorkspaceSpec =
            CalculatedResponsiveSpec(aspectRatio, availableSpace, cells, workspaceSpec)

        val resourceHelper = TestResourceHelper(context, "invalid_folders_specs_5".xmlToId())
        val folderSpecs = ResponsiveSpecsProvider.create(resourceHelper, ResponsiveSpecType.Folder)
        folderSpecs.getCalculatedSpec(
            aspectRatio,
            DimensionType.HEIGHT,
            cells,
            availableSpace,
            calculatedWorkspaceSpec
        )
    }

    @Test
    fun retrievesCalculatedWidthSpec() {
        val availableSpace = 800.dpToPx()
        val cells = 3

        val workspaceSpec =
            ResponsiveSpec(
                maxAvailableSize = availableSpace,
                dimensionType = DimensionType.WIDTH,
                specType = ResponsiveSpecType.Workspace,
                startPadding = SizeSpec(fixedSize = 10f),
                endPadding = SizeSpec(fixedSize = 10f),
                gutter = SizeSpec(fixedSize = 10f),
                cellSize = SizeSpec(fixedSize = 10f)
            )
        val calculatedWorkspaceSpec =
            CalculatedResponsiveSpec(aspectRatio, availableSpace, cells, workspaceSpec)

        val resourceHelper = TestResourceHelper(context, "valid_folders_specs".xmlToId())
        val folderSpecs = ResponsiveSpecsProvider.create(resourceHelper, ResponsiveSpecType.Folder)
        val calculatedWidthSpec =
            folderSpecs.getCalculatedSpec(
                aspectRatio,
                DimensionType.WIDTH,
                cells,
                availableSpace,
                calculatedWorkspaceSpec
            )

        assertThat(calculatedWidthSpec.cells).isEqualTo(cells)
        assertThat(calculatedWidthSpec.availableSpace).isEqualTo(availableSpace)
        assertThat(calculatedWidthSpec.startPaddingPx).isEqualTo(16.dpToPx())
        assertThat(calculatedWidthSpec.endPaddingPx).isEqualTo(16.dpToPx())
        assertThat(calculatedWidthSpec.gutterPx).isEqualTo(16.dpToPx())
        assertThat(calculatedWidthSpec.cellSizePx).isEqualTo(calculatedWorkspaceSpec.cellSizePx)
    }

    @Test(expected = IllegalStateException::class)
    fun retrievesCalculatedWidthSpec_invalidCalculatedResponsiveSpecType_throwsError() {
        val availableSpace = 10.dpToPx()
        val cells = 3

        val workspaceSpec =
            ResponsiveSpec(
                maxAvailableSize = availableSpace,
                dimensionType = DimensionType.HEIGHT,
                specType = ResponsiveSpecType.Folder,
                startPadding = SizeSpec(fixedSize = 10f),
                endPadding = SizeSpec(fixedSize = 10f),
                gutter = SizeSpec(fixedSize = 10f),
                cellSize = SizeSpec(fixedSize = 10f)
            )
        val calculatedWorkspaceSpec =
            CalculatedResponsiveSpec(aspectRatio, availableSpace, cells, workspaceSpec)

        val resourceHelper = TestResourceHelper(context, "valid_folders_specs".xmlToId())
        val folderSpecs = ResponsiveSpecsProvider.create(resourceHelper, ResponsiveSpecType.Folder)
        folderSpecs.getCalculatedSpec(
            aspectRatio,
            DimensionType.WIDTH,
            cells,
            availableSpace,
            calculatedWorkspaceSpec
        )
    }

    @Test
    fun retrievesCalculatedHeightSpec() {
        val availableSpace = 700.dpToPx()
        val cells = 3

        val workspaceSpec =
            ResponsiveSpec(
                maxAvailableSize = availableSpace,
                dimensionType = DimensionType.HEIGHT,
                specType = ResponsiveSpecType.Workspace,
                startPadding = SizeSpec(fixedSize = 10f),
                endPadding = SizeSpec(fixedSize = 10f),
                gutter = SizeSpec(fixedSize = 10f),
                cellSize = SizeSpec(fixedSize = 10f)
            )
        val calculatedWorkspaceSpec =
            CalculatedResponsiveSpec(aspectRatio, availableSpace, cells, workspaceSpec)

        val resourceHelper = TestResourceHelper(context, "valid_folders_specs".xmlToId())
        val folderSpecs = ResponsiveSpecsProvider.create(resourceHelper, ResponsiveSpecType.Folder)
        val calculatedHeightSpec =
            folderSpecs.getCalculatedSpec(
                aspectRatio,
                DimensionType.HEIGHT,
                cells,
                availableSpace,
                calculatedWorkspaceSpec
            )

        assertThat(calculatedHeightSpec.cells).isEqualTo(cells)
        assertThat(calculatedHeightSpec.availableSpace).isEqualTo(availableSpace)
        assertThat(calculatedHeightSpec.startPaddingPx).isEqualTo(24.dpToPx())
        assertThat(calculatedHeightSpec.endPaddingPx).isEqualTo(64.dpToPx())
        assertThat(calculatedHeightSpec.gutterPx).isEqualTo(16.dpToPx())
        assertThat(calculatedHeightSpec.cellSizePx).isEqualTo(calculatedWorkspaceSpec.cellSizePx)
    }

    @Test(expected = IllegalStateException::class)
    fun retrievesCalculatedHeightSpec_invalidCalculatedResponsiveSpecType_throwsError() {
        val availableSpace = 10.dpToPx()
        val cells = 3

        val workspaceSpec =
            ResponsiveSpec(
                maxAvailableSize = availableSpace,
                dimensionType = DimensionType.WIDTH,
                specType = ResponsiveSpecType.Folder,
                startPadding = SizeSpec(fixedSize = 10f),
                endPadding = SizeSpec(fixedSize = 10f),
                gutter = SizeSpec(fixedSize = 10f),
                cellSize = SizeSpec(fixedSize = 10f)
            )
        val calculatedWorkspaceSpec =
            CalculatedResponsiveSpec(aspectRatio, availableSpace, cells, workspaceSpec)

        val resourceHelper = TestResourceHelper(context, "valid_folders_specs".xmlToId())
        val folderSpecs = ResponsiveSpecsProvider.create(resourceHelper, ResponsiveSpecType.Folder)
        folderSpecs.getCalculatedSpec(
            aspectRatio,
            DimensionType.HEIGHT,
            cells,
            availableSpace,
            calculatedWorkspaceSpec
        )
    }
}
