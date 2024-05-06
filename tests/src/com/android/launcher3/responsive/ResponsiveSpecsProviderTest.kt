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
import com.android.launcher3.tests.R
import com.android.launcher3.util.TestResourceHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ResponsiveSpecsProviderTest : AbstractDeviceProfileTest() {
    override val runningContext: Context = InstrumentationRegistry.getInstrumentation().context
    val deviceSpec = deviceSpecs["tablet"]!!
    var aspectRatio = deviceSpec.naturalSize.first.toFloat() / deviceSpec.naturalSize.second

    @Before
    fun setup() {
        initializeVarsForPhone(deviceSpec)
    }

    @Test
    fun parseValidFile() {
        val resourceHelper = TestResourceHelper(context, R.xml.valid_responsive_spec_unsorted)
        val provider = ResponsiveSpecsProvider.create(resourceHelper, ResponsiveSpecType.Workspace)

        // Validate Portrait
        val aspectRatioPortrait = 1.0f
        val portraitSpecs = provider.getSpecsByAspectRatio(aspectRatioPortrait)

        val (expectedPortWidthSpecs, expectedPortHeightSpecs) = expectedPortraitSpecs()
        validateSpecs(
            portraitSpecs,
            aspectRatioPortrait,
            expectedPortWidthSpecs,
            expectedPortHeightSpecs
        )

        // Validate Landscape
        val aspectRatioLandscape = 1.051f
        val landscapeSpecs = provider.getSpecsByAspectRatio(aspectRatioLandscape)

        val (expectedLandWidthSpecs, expectedLandHeightSpecs) = expectedLandscapeSpecs()
        validateSpecs(
            landscapeSpecs,
            aspectRatioLandscape,
            expectedLandWidthSpecs,
            expectedLandHeightSpecs
        )

        // Validate Extra Spec
        val aspectRatioExtra = 10.1f
        val extraSpecs = provider.getSpecsByAspectRatio(aspectRatioExtra)

        val expectedOtherWidthSpecs =
            listOf(
                ResponsiveSpec(
                    maxAvailableSize = 9999.dpToPx(),
                    dimensionType = DimensionType.WIDTH,
                    specType = ResponsiveSpecType.Workspace,
                    startPadding = SizeSpec(1f.dpToPx()),
                    endPadding = SizeSpec(1f.dpToPx()),
                    gutter = SizeSpec(8f.dpToPx()),
                    cellSize = SizeSpec(ofRemainderSpace = 1f)
                )
            )

        val expectedOtherHeightSpecs =
            listOf(
                ResponsiveSpec(
                    maxAvailableSize = 9999.dpToPx(),
                    dimensionType = DimensionType.HEIGHT,
                    specType = ResponsiveSpecType.Workspace,
                    startPadding = SizeSpec(2f.dpToPx()),
                    endPadding = SizeSpec(2f.dpToPx()),
                    gutter = SizeSpec(8f.dpToPx()),
                    cellSize = SizeSpec(ofRemainderSpace = 1f)
                ),
            )

        validateSpecs(
            extraSpecs,
            aspectRatioExtra,
            expectedOtherWidthSpecs,
            expectedOtherHeightSpecs
        )
    }

    @Test(expected = IllegalStateException::class)
    fun parseValidFile_invalidAspectRatio_throwsError() {
        val resourceHelper = TestResourceHelper(context, R.xml.valid_responsive_spec_unsorted)
        val provider = ResponsiveSpecsProvider.create(resourceHelper, ResponsiveSpecType.Workspace)
        provider.getSpecsByAspectRatio(0f)
    }

    @Test(expected = InvalidResponsiveGridSpec::class)
    fun parseInvalidFile_missingGroups_throwsError() {
        val resourceHelper = TestResourceHelper(context, R.xml.invalid_responsive_spec_1)
        ResponsiveSpecsProvider.create(resourceHelper, ResponsiveSpecType.Workspace)
    }

    @Test(expected = InvalidResponsiveGridSpec::class)
    fun parseInvalidFile_partialGroups_throwsError() {
        val resourceHelper = TestResourceHelper(context, R.xml.invalid_responsive_spec_2)
        ResponsiveSpecsProvider.create(resourceHelper, ResponsiveSpecType.Workspace)
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_invalidAspectRatio_throwsError() {
        val resourceHelper = TestResourceHelper(context, R.xml.invalid_responsive_spec_3)
        ResponsiveSpecsProvider.create(resourceHelper, ResponsiveSpecType.Workspace)
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_invalidRemainderSpace_throwsError() {
        val resourceHelper = TestResourceHelper(context, R.xml.invalid_responsive_spec_4)
        ResponsiveSpecsProvider.create(resourceHelper, ResponsiveSpecType.Workspace)
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_invalidAvailableSpace_throwsError() {
        val resourceHelper = TestResourceHelper(context, R.xml.invalid_responsive_spec_5)
        ResponsiveSpecsProvider.create(resourceHelper, ResponsiveSpecType.Workspace)
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_invalidFixedSize_throwsError() {
        val resourceHelper = TestResourceHelper(context, R.xml.invalid_responsive_spec_6)
        ResponsiveSpecsProvider.create(resourceHelper, ResponsiveSpecType.Workspace)
    }

    private fun validateSpecs(
        specs: ResponsiveSpecGroup<ResponsiveSpec>,
        expectedAspectRatio: Float,
        expectedWidthSpecs: List<ResponsiveSpec>,
        expectedHeightSpecs: List<ResponsiveSpec>
    ) {
        assertThat(specs.aspectRatio).isAtLeast(expectedAspectRatio)

        assertThat(specs.widthSpecs.size).isEqualTo(expectedWidthSpecs.size)
        specs.widthSpecs.forEachIndexed { index, responsiveSpec ->
            assertThat(responsiveSpec).isEqualTo(expectedWidthSpecs[index])
        }

        assertThat(specs.heightSpecs.size).isEqualTo(expectedHeightSpecs.size)
        specs.heightSpecs.forEachIndexed { index, responsiveSpec ->
            assertThat(responsiveSpec).isEqualTo(expectedHeightSpecs[index])
        }
    }

    private fun expectedPortraitSpecs(): Pair<List<ResponsiveSpec>, List<ResponsiveSpec>> {
        val sizeSpec16 = SizeSpec(16f.dpToPx())
        val expectedWidthSpecs =
            listOf(
                ResponsiveSpec(
                    maxAvailableSize = 9999.dpToPx(),
                    dimensionType = DimensionType.WIDTH,
                    specType = ResponsiveSpecType.Workspace,
                    startPadding = SizeSpec(22f.dpToPx()),
                    endPadding = SizeSpec(22f.dpToPx()),
                    gutter = sizeSpec16,
                    cellSize = SizeSpec(ofRemainderSpace = 1f)
                )
            )

        val expectedHeightSpecs =
            listOf(
                ResponsiveSpec(
                    maxAvailableSize = 584.dpToPx(),
                    dimensionType = DimensionType.HEIGHT,
                    specType = ResponsiveSpecType.Workspace,
                    startPadding = SizeSpec(0f),
                    endPadding = SizeSpec(32f.dpToPx()),
                    gutter = sizeSpec16,
                    cellSize = SizeSpec(ofAvailableSpace = .15808f)
                ),
                ResponsiveSpec(
                    maxAvailableSize = 612.dpToPx(),
                    dimensionType = DimensionType.HEIGHT,
                    specType = ResponsiveSpecType.Workspace,
                    startPadding = SizeSpec(0f),
                    endPadding = SizeSpec(ofRemainderSpace = 1f),
                    gutter = sizeSpec16,
                    cellSize = SizeSpec(104f.dpToPx())
                ),
                ResponsiveSpec(
                    maxAvailableSize = 9999.dpToPx(),
                    dimensionType = DimensionType.HEIGHT,
                    specType = ResponsiveSpecType.Workspace,
                    startPadding = SizeSpec(8f.dpToPx()),
                    endPadding = SizeSpec(ofRemainderSpace = 1f),
                    gutter = sizeSpec16,
                    cellSize = SizeSpec(104f.dpToPx())
                ),
            )

        return Pair(expectedWidthSpecs, expectedHeightSpecs)
    }

    private fun expectedLandscapeSpecs(): Pair<List<ResponsiveSpec>, List<ResponsiveSpec>> {
        val sizeSpec12 = SizeSpec(12f.dpToPx())
        val expectedWidthSpecs =
            listOf(
                ResponsiveSpec(
                    maxAvailableSize = 602.dpToPx(),
                    dimensionType = DimensionType.WIDTH,
                    specType = ResponsiveSpecType.Workspace,
                    startPadding = SizeSpec(0f.dpToPx()),
                    endPadding = SizeSpec(36f.dpToPx()),
                    gutter = sizeSpec12,
                    cellSize = SizeSpec(ofRemainderSpace = 1f)
                ),
                ResponsiveSpec(
                    maxAvailableSize = 716.dpToPx(),
                    dimensionType = DimensionType.WIDTH,
                    specType = ResponsiveSpecType.Workspace,
                    startPadding = SizeSpec(16f.dpToPx()),
                    endPadding = SizeSpec(64f.dpToPx()),
                    gutter = sizeSpec12,
                    cellSize = SizeSpec(ofRemainderSpace = 1f)
                ),
                ResponsiveSpec(
                    maxAvailableSize = 9999.dpToPx(),
                    dimensionType = DimensionType.WIDTH,
                    specType = ResponsiveSpecType.Workspace,
                    startPadding = SizeSpec(36f.dpToPx()),
                    endPadding = SizeSpec(80f.dpToPx()),
                    gutter = sizeSpec12,
                    cellSize = SizeSpec(ofRemainderSpace = 1f)
                )
            )

        val expectedHeightSpecs =
            listOf(
                ResponsiveSpec(
                    maxAvailableSize = 371.dpToPx(),
                    dimensionType = DimensionType.HEIGHT,
                    specType = ResponsiveSpecType.Workspace,
                    startPadding = SizeSpec(0f),
                    endPadding = SizeSpec(24f.dpToPx()),
                    gutter = sizeSpec12,
                    cellSize = SizeSpec(ofRemainderSpace = 1f)
                ),
                ResponsiveSpec(
                    maxAvailableSize = 9999.dpToPx(),
                    dimensionType = DimensionType.HEIGHT,
                    specType = ResponsiveSpecType.Workspace,
                    startPadding = SizeSpec(0f),
                    endPadding = SizeSpec(34f.dpToPx()),
                    gutter = sizeSpec12,
                    cellSize = SizeSpec(ofRemainderSpace = 1f)
                ),
            )

        return Pair(expectedWidthSpecs, expectedHeightSpecs)
    }
}
