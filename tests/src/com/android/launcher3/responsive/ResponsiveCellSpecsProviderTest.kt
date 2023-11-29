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
import com.android.launcher3.tests.R as TestR
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
        val testResourceHelper = TestResourceHelper(context, TestR.xml.valid_cell_specs_file)
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
                    iconDrawablePadding = SizeSpec(8f.dpToPx())
                ),
                CellSpec(
                    maxAvailableSize = 9999.dpToPx(),
                    dimensionType = ResponsiveSpec.DimensionType.HEIGHT,
                    specType = ResponsiveSpecType.Cell,
                    iconSize = SizeSpec(52f.dpToPx()),
                    iconTextSize = SizeSpec(12f.dpToPx()),
                    iconDrawablePadding = SizeSpec(11f.dpToPx())
                )
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
                iconDrawablePadding = SizeSpec(0f)
            )
        val landscapeSpecs = provider.getSpecsByAspectRatio(aspectRatioLandscape)

        assertThat(landscapeSpecs.aspectRatio).isAtLeast(aspectRatioLandscape)
        assertThat(landscapeSpecs.widthSpecs.size).isEqualTo(0)
        assertThat(landscapeSpecs.heightSpecs.size).isEqualTo(1)
        assertThat(landscapeSpecs.heightSpecs[0]).isEqualTo(expectedLandscapeSpec)
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_IsNotFixedSizeOrMatchWorkspace_throwsError() {
        ResponsiveCellSpecsProvider.create(
            TestResourceHelper(context, TestR.xml.invalid_cell_specs_1)
        )
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_dimensionTypeIsNotHeight_throwsError() {
        ResponsiveCellSpecsProvider.create(
            TestResourceHelper(context, TestR.xml.invalid_cell_specs_2)
        )
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_invalidFixedSize_throwsError() {
        ResponsiveCellSpecsProvider.create(
            TestResourceHelper(context, TestR.xml.invalid_cell_specs_3)
        )
    }
}
