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
import com.android.launcher3.tests.R as TestR
import com.android.launcher3.util.TestResourceHelper
import com.android.systemui.util.dpToPx
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class HotseatSpecsTest : AbstractDeviceProfileTest() {
    override val runningContext: Context = InstrumentationRegistry.getInstrumentation().context

    @Before
    fun setup() {
        initializeVarsForPhone(deviceSpecs["phone"]!!)
    }

    @Test
    fun parseValidFile() {
        val hotseatSpecs =
            HotseatSpecs.create(TestResourceHelper(context!!, TestR.xml.valid_hotseat_file))
        assertThat(hotseatSpecs.specs.size).isEqualTo(2)

        val expectedSpecs =
            listOf(
                HotseatSpec(
                    maxAvailableSize = 847.dpToPx(),
                    specType = ResponsiveSpec.SpecType.HEIGHT,
                    hotseatQsbSpace = SizeSpec(24f.dpToPx())
                ),
                HotseatSpec(
                    maxAvailableSize = 9999.dpToPx(),
                    specType = ResponsiveSpec.SpecType.HEIGHT,
                    hotseatQsbSpace = SizeSpec(36f.dpToPx())
                ),
            )

        assertThat(hotseatSpecs.specs.size).isEqualTo(expectedSpecs.size)
        assertThat(hotseatSpecs.specs[0]).isEqualTo(expectedSpecs[0])
        assertThat(hotseatSpecs.specs[1]).isEqualTo(expectedSpecs[1])
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_spaceIsNotFixedSize_throwsError() {
        HotseatSpecs.create(TestResourceHelper(context!!, TestR.xml.invalid_hotseat_file_case_1))
    }
}
