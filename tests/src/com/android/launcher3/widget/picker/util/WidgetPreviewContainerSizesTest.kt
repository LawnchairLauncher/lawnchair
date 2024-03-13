/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.widget.picker.util

import android.content.ComponentName
import android.content.Context
import android.graphics.Point
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.WidgetItem
import com.android.launcher3.util.ActivityContextWrapper
import com.android.launcher3.util.WidgetUtils.createAppWidgetProviderInfo
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
class WidgetPreviewContainerSizesTest {
    private lateinit var context: Context
    private lateinit var deviceProfile: DeviceProfile
    private lateinit var testInvariantProfile: InvariantDeviceProfile

    @Mock private lateinit var iconCache: IconCache

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        context = ActivityContextWrapper(ApplicationProvider.getApplicationContext())
        testInvariantProfile = LauncherAppState.getIDP(context)
        deviceProfile = testInvariantProfile.getDeviceProfile(context).copy(context)
    }

    @Test
    fun widgetPreviewContainerSize_forItem_returnsCorrectContainerSize() {
        val testSizes = getTestSizes(deviceProfile)
        val expectedPreviewContainers = testSizes.values.toList()

        for ((index, widgetSize) in testSizes.keys.withIndex()) {
            val widgetItem = createWidgetItem(widgetSize, context, testInvariantProfile, iconCache)

            assertWithMessage("size for $widgetSize should be: ${expectedPreviewContainers[index]}")
                .that(WidgetPreviewContainerSize.forItem(widgetItem, deviceProfile))
                .isEqualTo(expectedPreviewContainers[index])
        }
    }

    companion object {
        private const val TEST_PACKAGE = "com.google.test"

        private val HANDHELD_TEST_SIZES: Map<Point, WidgetPreviewContainerSize> =
            mapOf(
                // 1x1
                Point(1, 1) to WidgetPreviewContainerSize(1, 1),
                // 2x1
                Point(2, 1) to WidgetPreviewContainerSize(2, 1),
                Point(3, 1) to WidgetPreviewContainerSize(2, 1),
                // 4x1
                Point(4, 1) to WidgetPreviewContainerSize(4, 1),
                // 2x2
                Point(2, 2) to WidgetPreviewContainerSize(2, 2),
                Point(3, 3) to WidgetPreviewContainerSize(2, 2),
                Point(3, 2) to WidgetPreviewContainerSize(2, 2),
                // 2x3
                Point(2, 3) to WidgetPreviewContainerSize(2, 3),
                Point(3, 4) to WidgetPreviewContainerSize(2, 3),
                Point(3, 5) to WidgetPreviewContainerSize(2, 3),
                // 4x2
                Point(4, 2) to WidgetPreviewContainerSize(4, 2),
                // 4x3
                Point(4, 3) to WidgetPreviewContainerSize(4, 3),
                Point(4, 4) to WidgetPreviewContainerSize(4, 3),
            )

        private val TABLET_TEST_SIZES: Map<Point, WidgetPreviewContainerSize> =
            mapOf(
                // 1x1
                Point(1, 1) to WidgetPreviewContainerSize(1, 1),
                // 2x1
                Point(2, 1) to WidgetPreviewContainerSize(2, 1),
                // 3x1
                Point(3, 1) to WidgetPreviewContainerSize(3, 1),
                Point(4, 1) to WidgetPreviewContainerSize(3, 1),
                // 2x2
                Point(2, 2) to WidgetPreviewContainerSize(2, 2),
                // 2x3
                Point(2, 3) to WidgetPreviewContainerSize(2, 3),
                // 3x2
                Point(3, 2) to WidgetPreviewContainerSize(3, 2),
                Point(4, 2) to WidgetPreviewContainerSize(3, 2),
                Point(5, 2) to WidgetPreviewContainerSize(3, 2),
                // 3x3
                Point(3, 3) to WidgetPreviewContainerSize(3, 3),
                Point(4, 4) to WidgetPreviewContainerSize(3, 3),
                // 3x4
                Point(5, 4) to WidgetPreviewContainerSize(3, 4),
                Point(3, 4) to WidgetPreviewContainerSize(3, 4),
                Point(5, 5) to WidgetPreviewContainerSize(3, 4),
                Point(6, 4) to WidgetPreviewContainerSize(3, 4),
                Point(6, 5) to WidgetPreviewContainerSize(3, 4),
            )

        private fun getTestSizes(dp: DeviceProfile) =
            if (dp.isTablet && !dp.isTwoPanels) {
                TABLET_TEST_SIZES
            } else {
                HANDHELD_TEST_SIZES
            }

        private fun createWidgetItem(
            widgetSize: Point,
            context: Context,
            invariantDeviceProfile: InvariantDeviceProfile,
            iconCache: IconCache
        ): WidgetItem {
            val providerInfo =
                createAppWidgetProviderInfo(
                    ComponentName.createRelative(
                        TEST_PACKAGE,
                        /*cls=*/ ".WidgetProvider_" + widgetSize.x + "x" + widgetSize.y
                    )
                )
            val widgetInfo =
                LauncherAppWidgetProviderInfo.fromProviderInfo(context, providerInfo).apply {
                    spanX = widgetSize.x
                    spanY = widgetSize.y
                }
            return WidgetItem(widgetInfo, invariantDeviceProfile, iconCache, context)
        }
    }
}
