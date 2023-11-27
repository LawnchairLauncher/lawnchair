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
class HotseatSpecsProviderTest : AbstractDeviceProfileTest() {
    override val runningContext: Context = InstrumentationRegistry.getInstrumentation().context
    val deviceSpec = deviceSpecs["phone"]!!
    val aspectRatio = deviceSpec.naturalSize.first.toFloat() / deviceSpec.naturalSize.second

    @Before
    fun setup() {
        initializeVarsForPhone(deviceSpec)
    }

    @Test
    fun parseValidFile() {
        val hotseatSpecsProvider =
            HotseatSpecsProvider.create(TestResourceHelper(context, TestR.xml.valid_hotseat_file))
        val specs = hotseatSpecsProvider.getSpecsByAspectRatio(aspectRatio)

        val expectedHeightSpecs =
            listOf(
                HotseatSpec(
                    maxAvailableSize = 847.dpToPx(),
                    dimensionType = ResponsiveSpec.DimensionType.HEIGHT,
                    specType = ResponsiveSpecType.Hotseat,
                    hotseatQsbSpace = SizeSpec(24f.dpToPx()),
                    edgePadding = SizeSpec(48f.dpToPx())
                ),
                HotseatSpec(
                    maxAvailableSize = 9999.dpToPx(),
                    dimensionType = ResponsiveSpec.DimensionType.HEIGHT,
                    specType = ResponsiveSpecType.Hotseat,
                    hotseatQsbSpace = SizeSpec(36f.dpToPx()),
                    edgePadding = SizeSpec(48f.dpToPx())
                ),
            )

        assertThat(specs.heightSpecs.size).isEqualTo(expectedHeightSpecs.size)
        assertThat(specs.heightSpecs[0]).isEqualTo(expectedHeightSpecs[0])
        assertThat(specs.heightSpecs[1]).isEqualTo(expectedHeightSpecs[1])

        assertThat(specs.widthSpecs.size).isEqualTo(0)
    }

    @Test
    fun parseValidLandscapeFile() {
        val hotseatSpecsProvider =
            HotseatSpecsProvider.create(
                TestResourceHelper(context, TestR.xml.valid_hotseat_land_file)
            )
        val specs = hotseatSpecsProvider.getSpecsByAspectRatio(aspectRatio)
        assertThat(specs.heightSpecs.size).isEqualTo(0)

        val expectedWidthSpecs =
            listOf(
                HotseatSpec(
                    maxAvailableSize = 743.dpToPx(),
                    dimensionType = ResponsiveSpec.DimensionType.WIDTH,
                    specType = ResponsiveSpecType.Hotseat,
                    hotseatQsbSpace = SizeSpec(0f),
                    edgePadding = SizeSpec(48f.dpToPx())
                ),
                HotseatSpec(
                    maxAvailableSize = 9999.dpToPx(),
                    dimensionType = ResponsiveSpec.DimensionType.WIDTH,
                    specType = ResponsiveSpecType.Hotseat,
                    hotseatQsbSpace = SizeSpec(0f),
                    edgePadding = SizeSpec(64f.dpToPx())
                ),
            )

        assertThat(specs.widthSpecs.size).isEqualTo(expectedWidthSpecs.size)
        assertThat(specs.widthSpecs[0]).isEqualTo(expectedWidthSpecs[0])
        assertThat(specs.widthSpecs[1]).isEqualTo(expectedWidthSpecs[1])
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_spaceIsNotFixedSize_throwsError() {
        HotseatSpecsProvider.create(
            TestResourceHelper(context, TestR.xml.invalid_hotseat_file_case_1)
        )
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_invalidFixedSize_throwsError() {
        HotseatSpecsProvider.create(
            TestResourceHelper(context, TestR.xml.invalid_hotseat_file_case_2)
        )
    }
}
