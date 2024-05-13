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
package com.android.launcher3.widget.util

import android.content.Context
import android.graphics.Point
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.DeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.ActivityContextWrapper
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class WidgetDragScaleUtilsTest {
    private lateinit var context: Context
    private lateinit var itemInfo: ItemInfo
    private lateinit var deviceProfile: DeviceProfile

    @Before
    fun setup() {
        context = ActivityContextWrapper(ApplicationProvider.getApplicationContext())

        itemInfo = ItemInfo()

        deviceProfile =
            Mockito.spy(LauncherAppState.getIDP(context).getDeviceProfile(context).copy(context))

        doAnswer {
                return@doAnswer 0.8f
            }
            .whenever(deviceProfile)
            .getWorkspaceSpringLoadScale(any(Context::class.java))
        whenever(deviceProfile.cellSize).thenReturn(Point(CELL_SIZE, CELL_SIZE))
        deviceProfile.cellLayoutBorderSpacePx = Point(CELL_SPACING, CELL_SPACING)
        deviceProfile.widgetPadding.setEmpty()
    }

    @Test
    fun getWidgetDragScalePx_largeDraggedView_downScaled() {
        itemInfo.spanX = 2
        itemInfo.spanY = 2
        val widgetSize = WidgetSizes.getWidgetSizePx(deviceProfile, itemInfo.spanX, itemInfo.spanY)
        // Assume dragged view was a drawable which was larger than widget's size.
        val draggedViewWidthPx = widgetSize.width + 0.5f * widgetSize.width
        val draggedViewHeightPx = widgetSize.height + 0.5f * widgetSize.height
        // Returns negative scale pixels - i.e. downscaled
        assertThat(
                WidgetDragScaleUtils.getWidgetDragScalePx(
                    context,
                    deviceProfile,
                    draggedViewWidthPx,
                    draggedViewHeightPx,
                    itemInfo
                )
            )
            .isLessThan(0)
    }

    @Test
    fun getWidgetDragScalePx_draggedViewSameAsWidgetSize_downScaled() {
        itemInfo.spanX = 4
        itemInfo.spanY = 2

        val widgetSize = WidgetSizes.getWidgetSizePx(deviceProfile, itemInfo.spanX, itemInfo.spanY)
        // Assume dragged view was a drawable which was larger than widget's size.
        val draggedViewWidthPx = widgetSize.width.toFloat()
        val draggedViewHeightPx = widgetSize.height.toFloat()
        // Returns negative scale pixels - i.e. downscaled
        // Even if dragged view was of same size as widget's drop target, to accommodate the spring
        // load scaling of workspace and additionally getting the view inside of drop target frame,
        // widget would be downscaled.
        assertThat(
                WidgetDragScaleUtils.getWidgetDragScalePx(
                    context,
                    deviceProfile,
                    draggedViewWidthPx,
                    draggedViewHeightPx,
                    itemInfo
                )
            )
            .isLessThan(0)
    }

    @Test
    fun getWidgetDragScalePx_draggedViewSmallerThanMinSize_scaledSizeIsAtLeastMinSize() {
        itemInfo.spanX = 1
        itemInfo.spanY = 1
        val minSizePx =
            context.resources.getDimensionPixelSize(R.dimen.widget_drag_view_min_scale_down_size)

        // Assume min size is greater than cell size, so that, we know the upscale of dragged view
        // is due to min size enforcement.
        assumeTrue(minSizePx > CELL_SIZE)

        val draggedViewWidthPx = minSizePx - 15f
        val draggedViewHeightPx = minSizePx - 15f

        // Returns positive scale pixels - i.e. up-scaled
        val finalScalePx =
            WidgetDragScaleUtils.getWidgetDragScalePx(
                context,
                deviceProfile,
                draggedViewWidthPx,
                draggedViewHeightPx,
                itemInfo
            )

        val effectiveWidthPx = draggedViewWidthPx + finalScalePx
        val scaleFactor = (draggedViewWidthPx + finalScalePx) / draggedViewWidthPx
        val effectiveHeightPx = scaleFactor * draggedViewHeightPx
        // Both original height and width were smaller than min size, scaling them down below min
        // size would have made them not visible under the finger. Here, as expected, widget is
        // at least as large as min size.
        assertThat(effectiveWidthPx).isAtLeast(minSizePx)
        assertThat(effectiveHeightPx).isAtLeast(minSizePx)
    }

    companion object {
        const val CELL_SIZE = 60
        const val CELL_SPACING = 10
    }
}
