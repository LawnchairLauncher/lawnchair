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

package com.android.launcher3.workspace

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
class WorkspaceSpecsTest : AbstractDeviceProfileTest() {
    override val runningContext: Context = InstrumentationRegistry.getInstrumentation().context

    @Before
    fun setup() {
        initializeVarsForPhone(deviceSpecs["phone"]!!)
    }

    @Test
    fun parseValidFile() {
        val workspaceSpecs =
            WorkspaceSpecs(TestResourceHelper(context!!, TestR.xml.valid_workspace_file))
        assertThat(workspaceSpecs.workspaceHeightSpecList.size).isEqualTo(2)
        assertThat(workspaceSpecs.workspaceHeightSpecList[0].toString())
            .isEqualTo(
                "WorkspaceSpec(" +
                    "maxAvailableSize=1701, " +
                    "specType=HEIGHT, " +
                    "startPadding=SizeSpec(fixedSize=0.0, " +
                    "ofAvailableSpace=0.0125, " +
                    "ofRemainderSpace=0.0), " +
                    "endPadding=SizeSpec(fixedSize=0.0, " +
                    "ofAvailableSpace=0.05, " +
                    "ofRemainderSpace=0.0), " +
                    "gutter=SizeSpec(fixedSize=42.0, " +
                    "ofAvailableSpace=0.0, " +
                    "ofRemainderSpace=0.0), " +
                    "cellSize=SizeSpec(fixedSize=0.0, " +
                    "ofAvailableSpace=0.0, " +
                    "ofRemainderSpace=0.2)" +
                    ")"
            )
        assertThat(workspaceSpecs.workspaceHeightSpecList[1].toString())
            .isEqualTo(
                "WorkspaceSpec(" +
                    "maxAvailableSize=26247, " +
                    "specType=HEIGHT, " +
                    "startPadding=SizeSpec(fixedSize=0.0, " +
                    "ofAvailableSpace=0.0306, " +
                    "ofRemainderSpace=0.0), " +
                    "endPadding=SizeSpec(fixedSize=0.0, " +
                    "ofAvailableSpace=0.068, " +
                    "ofRemainderSpace=0.0), " +
                    "gutter=SizeSpec(fixedSize=42.0, " +
                    "ofAvailableSpace=0.0, " +
                    "ofRemainderSpace=0.0), " +
                    "cellSize=SizeSpec(fixedSize=0.0, " +
                    "ofAvailableSpace=0.0, " +
                    "ofRemainderSpace=0.2)" +
                    ")"
            )
        assertThat(workspaceSpecs.workspaceWidthSpecList.size).isEqualTo(1)
        assertThat(workspaceSpecs.workspaceWidthSpecList[0].toString())
            .isEqualTo(
                "WorkspaceSpec(" +
                    "maxAvailableSize=26247, " +
                    "specType=WIDTH, " +
                    "startPadding=SizeSpec(fixedSize=0.0, " +
                    "ofAvailableSpace=0.0, " +
                    "ofRemainderSpace=0.21436226), " +
                    "endPadding=SizeSpec(fixedSize=0.0, " +
                    "ofAvailableSpace=0.0, " +
                    "ofRemainderSpace=0.21436226), " +
                    "gutter=SizeSpec(fixedSize=0.0, " +
                    "ofAvailableSpace=0.0, " +
                    "ofRemainderSpace=0.11425509), " +
                    "cellSize=SizeSpec(fixedSize=315.0, " +
                    "ofAvailableSpace=0.0, " +
                    "ofRemainderSpace=0.0)" +
                    ")"
            )
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_missingTag_throwsError() {
        WorkspaceSpecs(TestResourceHelper(context!!, TestR.xml.invalid_workspace_file_case_1))
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_moreThanOneValuePerTag_throwsError() {
        WorkspaceSpecs(TestResourceHelper(context!!, TestR.xml.invalid_workspace_file_case_2))
    }

    @Test(expected = IllegalStateException::class)
    fun parseInvalidFile_valueBiggerThan1_throwsError() {
        WorkspaceSpecs(TestResourceHelper(context!!, TestR.xml.invalid_workspace_file_case_3))
    }
}
