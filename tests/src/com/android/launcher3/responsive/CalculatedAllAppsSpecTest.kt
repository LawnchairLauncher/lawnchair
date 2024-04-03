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
class CalculatedAllAppsSpecTest : AbstractDeviceProfileTest() {
    override val runningContext: Context = InstrumentationRegistry.getInstrumentation().context

    /**
     * This test tests:
     * - (height spec) copy values from workspace
     * - (width spec) copy values from workspace
     */
    @Test
    fun normalPhone_copiesFromWorkspace() {
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

        val allAppsSpecs =
            ResponsiveSpecsProvider.create(
                TestResourceHelper(context, TestR.xml.valid_all_apps_file),
                ResponsiveSpecType.AllApps
            )

        with(
            allAppsSpecs.getCalculatedSpec(
                aspectRatio,
                DimensionType.WIDTH,
                4,
                availableWidth,
                widthSpec
            )
        ) {
            assertThat(availableSpace).isEqualTo(availableWidth)
            assertThat(cells).isEqualTo(4)
            assertThat(startPaddingPx).isEqualTo(widthSpec.startPaddingPx)
            assertThat(endPaddingPx).isEqualTo(widthSpec.endPaddingPx)
            assertThat(gutterPx).isEqualTo(widthSpec.gutterPx)
            assertThat(cellSizePx).isEqualTo(widthSpec.cellSizePx)
        }

        with(
            allAppsSpecs.getCalculatedSpec(
                aspectRatio,
                DimensionType.HEIGHT,
                5,
                availableHeight,
                heightSpec
            )
        ) {
            assertThat(availableSpace).isEqualTo(availableHeight)
            assertThat(cells).isEqualTo(5)
            assertThat(startPaddingPx).isEqualTo(0)
            assertThat(endPaddingPx).isEqualTo(0)
            assertThat(gutterPx).isEqualTo(heightSpec.gutterPx)
            assertThat(cellSizePx).isEqualTo(heightSpec.cellSizePx)
        }
    }
}
