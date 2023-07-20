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
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AllAppsSpecsTest : AbstractDeviceProfileTest() {
    override val runningContext: Context = InstrumentationRegistry.getInstrumentation().context

    @Before
    fun setup() {
        initializeVarsForPhone(deviceSpecs["phone"]!!)
    }

    @Test
    fun parseValidFile() {
        val allAppsSpecs =
            AllAppsSpecs.create(TestResourceHelper(context!!, TestR.xml.valid_all_apps_file))
        assertThat(allAppsSpecs.heightSpecs.size).isEqualTo(1)
        assertThat(allAppsSpecs.heightSpecs[0].toString())
            .isEqualTo(
                "AllAppsSpec(" +
                    "maxAvailableSize=26247, " +
                    "specType=HEIGHT, " +
                    "startPadding=SizeSpec(fixedSize=0.0, " +
                    "ofAvailableSpace=0.0, " +
                    "ofRemainderSpace=0.0, " +
                    "matchWorkspace=false, " +
                    "maxSize=2147483647), " +
                    "endPadding=SizeSpec(fixedSize=0.0, " +
                    "ofAvailableSpace=0.0, " +
                    "ofRemainderSpace=0.0, " +
                    "matchWorkspace=false, " +
                    "maxSize=2147483647), " +
                    "gutter=SizeSpec(fixedSize=0.0, " +
                    "ofAvailableSpace=0.0, " +
                    "ofRemainderSpace=0.0, " +
                    "matchWorkspace=true, " +
                    "maxSize=2147483647), " +
                    "cellSize=SizeSpec(fixedSize=0.0, " +
                    "ofAvailableSpace=0.0, " +
                    "ofRemainderSpace=0.0, " +
                    "matchWorkspace=true, " +
                    "maxSize=2147483647)" +
                    ")"
            )

        assertThat(allAppsSpecs.widthSpecs.size).isEqualTo(1)
        assertThat(allAppsSpecs.widthSpecs[0].toString())
            .isEqualTo(
                "AllAppsSpec(" +
                    "maxAvailableSize=26247, " +
                    "specType=WIDTH, " +
                    "startPadding=SizeSpec(fixedSize=0.0, " +
                    "ofAvailableSpace=0.0, " +
                    "ofRemainderSpace=0.0, " +
                    "matchWorkspace=true, " +
                    "maxSize=2147483647), " +
                    "endPadding=SizeSpec(fixedSize=0.0, " +
                    "ofAvailableSpace=0.0, " +
                    "ofRemainderSpace=0.0, " +
                    "matchWorkspace=true, " +
                    "maxSize=2147483647), " +
                    "gutter=SizeSpec(fixedSize=0.0, " +
                    "ofAvailableSpace=0.0, " +
                    "ofRemainderSpace=0.0, " +
                    "matchWorkspace=true, " +
                    "maxSize=2147483647), " +
                    "cellSize=SizeSpec(fixedSize=0.0, " +
                    "ofAvailableSpace=0.0, " +
                    "ofRemainderSpace=0.0, " +
                    "matchWorkspace=true, " +
                    "maxSize=2147483647)" +
                    ")"
            )
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_missingTag_throwsError() {
        AllAppsSpecs.create(TestResourceHelper(context!!, TestR.xml.invalid_all_apps_file_case_1))
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_moreThanOneValuePerTag_throwsError() {
        AllAppsSpecs.create(TestResourceHelper(context!!, TestR.xml.invalid_all_apps_file_case_2))
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_valueBiggerThan1_throwsError() {
        AllAppsSpecs.create(TestResourceHelper(context!!, TestR.xml.invalid_all_apps_file_case_3))
    }
}
