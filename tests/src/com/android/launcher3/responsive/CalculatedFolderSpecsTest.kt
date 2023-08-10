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
import com.android.launcher3.tests.R
import com.android.launcher3.util.TestResourceHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class CalculatedFolderSpecsTest : AbstractDeviceProfileTest() {
    override val runningContext: Context = InstrumentationRegistry.getInstrumentation().context

    private val deviceSpec = deviceSpecs["phone"]!!

    @Before
    fun setup() {
        initializeVarsForPhone(deviceSpec)
    }

    @Test
    fun validate_matchWidthWorkspace() {
        val columns = 6

        // Loading workspace specs
        val resourceHelperWorkspace = TestResourceHelper(context!!, R.xml.valid_workspace_file)
        val workspaceSpecs = WorkspaceSpecs.create(resourceHelperWorkspace)

        // Loading folders specs
        val resourceHelperFolder = TestResourceHelper(context!!, R.xml.valid_folders_specs)
        val folderSpecs = FolderSpecs.create(resourceHelperFolder)

        assertThat(folderSpecs.widthSpecs.size).isEqualTo(2)
        assertThat(folderSpecs.widthSpecs[0].cellSize.matchWorkspace).isEqualTo(true)
        assertThat(folderSpecs.widthSpecs[1].cellSize.matchWorkspace).isEqualTo(false)

        // Validate width spec <= 800
        var availableWidth = deviceSpec.naturalSize.first
        var calculatedWorkspace = workspaceSpecs.getCalculatedWidthSpec(columns, availableWidth)
        var calculatedWidthFolderSpec =
            folderSpecs.getCalculatedWidthSpec(columns, availableWidth, calculatedWorkspace)
        with(calculatedWidthFolderSpec) {
            assertThat(availableSpace).isEqualTo(availableWidth)
            assertThat(cells).isEqualTo(columns)
            assertThat(startPaddingPx).isEqualTo(16.dpToPx())
            assertThat(endPaddingPx).isEqualTo(16.dpToPx())
            assertThat(gutterPx).isEqualTo(16.dpToPx())
            assertThat(cellSizePx).isEqualTo(calculatedWorkspace.cellSizePx)
        }

        // Validate width spec > 800
        availableWidth = 2000.dpToPx()
        calculatedWorkspace = workspaceSpecs.getCalculatedWidthSpec(columns, availableWidth)
        calculatedWidthFolderSpec =
            folderSpecs.getCalculatedWidthSpec(columns, availableWidth, calculatedWorkspace)
        with(calculatedWidthFolderSpec) {
            assertThat(availableSpace).isEqualTo(availableWidth)
            assertThat(cells).isEqualTo(columns)
            assertThat(startPaddingPx).isEqualTo(16.dpToPx())
            assertThat(endPaddingPx).isEqualTo(16.dpToPx())
            assertThat(gutterPx).isEqualTo(16.dpToPx())
            assertThat(cellSizePx).isEqualTo(102.dpToPx())
        }
    }

    @Test
    fun validate_matchHeightWorkspace() {
        // Hotseat is roughly 495px on a real device, it doesn't need to be precise on unit tests
        val hotseatSize = 495
        val statusBarHeight = deviceSpec.statusBarNaturalPx
        val availableHeight = deviceSpec.naturalSize.second - statusBarHeight - hotseatSize
        val rows = 5

        // Loading workspace specs
        val resourceHelperWorkspace = TestResourceHelper(context!!, R.xml.valid_workspace_file)
        val workspaceSpecs = WorkspaceSpecs.create(resourceHelperWorkspace)

        // Loading folders specs
        val resourceHelperFolder = TestResourceHelper(context!!, R.xml.valid_folders_specs)
        val folderSpecs = FolderSpecs.create(resourceHelperFolder)

        assertThat(folderSpecs.heightSpecs.size).isEqualTo(1)
        assertThat(folderSpecs.heightSpecs[0].cellSize.matchWorkspace).isEqualTo(true)

        // Validate height spec
        val calculatedWorkspace = workspaceSpecs.getCalculatedHeightSpec(rows, availableHeight)
        val calculatedFolderSpec =
            folderSpecs.getCalculatedHeightSpec(rows, availableHeight, calculatedWorkspace)
        with(calculatedFolderSpec) {
            assertThat(availableSpace).isEqualTo(availableHeight)
            assertThat(cells).isEqualTo(rows)
            assertThat(startPaddingPx).isEqualTo(24.dpToPx())
            assertThat(endPaddingPx).isEqualTo(64.dpToPx())
            assertThat(gutterPx).isEqualTo(16.dpToPx())
            assertThat(cellSizePx).isEqualTo(calculatedWorkspace.cellSizePx)
        }
    }
}
