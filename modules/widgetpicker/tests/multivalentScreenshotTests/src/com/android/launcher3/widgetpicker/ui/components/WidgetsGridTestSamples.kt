/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.widgetpicker.ui.components

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Process
import android.os.Process.myUserHandle
import android.widget.RemoteViews
import androidx.compose.ui.unit.IntSize
import com.android.launcher3.widgetpicker.shared.model.PickableWidget
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import com.android.launcher3.widgetpicker.shared.model.WidgetId
import com.android.launcher3.widgetpicker.shared.model.WidgetInfo
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview
import com.android.launcher3.widgetpicker.shared.model.WidgetSizeInfo
import com.android.launcher3.widgetpicker.tests.R
import com.android.launcher3.widgetpicker.ui.model.WidgetSizeGroup

/**
 * Different combinations of test widget groupings to verify behavior of arranging them in a grid.
 */
object WidgetsGridTestSamples {
    /**
     * A test sample comprising of different sizes of widgets using layout and generated previews.
     */
    fun remoteViewWidgets(packageName: String, screenWidth: Int): WidgetsSample {
        val (cellWidth, cellHeight) = testCellSize(screenWidth)

        val noFillBoundsWidgetId = newWidgetId("noFillBounds")
        val bigSizeWidgetId = newWidgetId("tooBig")
        val fillsBoundsWidgetId = newWidgetId("matchSize")

        return WidgetsSample(
            widgetSizeGroups =
                listOf(
                    WidgetSizeGroup(
                        previewContainerWidthPx = 2 * cellWidth,
                        previewContainerHeightPx = 2 * cellHeight,
                        widgets =
                            listOf(
                                twoByTwo(cellWidth, cellHeight)
                                    .copy(
                                        id = noFillBoundsWidgetId,
                                        label = "NoFillBounds",
                                        description =
                                            "The preview's layout wraps content with size that doesn't fill" +
                                                " container",
                                    ),
                                threeByTwo(cellWidth, cellHeight)
                                    .copy(
                                        id = bigSizeWidgetId,
                                        label = "BigSize",
                                        description =
                                            "The preview's layout hardcodes very large size " +
                                                "that should be scaled down",
                                    ),
                            ),
                    ),
                    WidgetSizeGroup(
                        previewContainerWidthPx = 4 * cellWidth,
                        previewContainerHeightPx = 2 * cellHeight,
                        widgets =
                            listOf(
                                fourByTwo(cellWidth, cellHeight)
                                    .copy(
                                        id = fillsBoundsWidgetId,
                                        label = "FillBounds",
                                        description =
                                            "The preview uses match_parent and renders at correct size. " +
                                                "This preview doesn't need scaling",
                                    )
                            ),
                    ),
                ),
            previews =
                mapOf(
                    noFillBoundsWidgetId to
                        WidgetPreview.RemoteViewsWidgetPreview(
                            remoteViews =
                                RemoteViews(packageName, R.layout.widget_preview_wrap_content)
                        ),
                    bigSizeWidgetId to
                        WidgetPreview.RemoteViewsWidgetPreview(
                            remoteViews =
                                RemoteViews(
                                    packageName,
                                    R.layout.widget_preview_hardcoded_large_size,
                                )
                        ),
                    fillsBoundsWidgetId to
                        WidgetPreview.RemoteViewsWidgetPreview(
                            remoteViews =
                                RemoteViews(
                                    packageName,
                                    R.layout.widget_preview_matching_parent_size,
                                )
                        ),
                ),
        )
    }

    /** A test sample with 3 size groups: 2x2s, 4x2s and 1x1s */
    fun varyingSizedWidgets(screenWidth: Int): WidgetsSample {
        val (cellWidth, cellHeight) = testCellSize(screenWidth)

        val fillsBoundsWidgetId = newWidgetId("FillsBounds")
        val threeByTwoWidgetId = newWidgetId("ThreeByTwo")
        val noFillBoundsWidgetId = newWidgetId("NoFillBounds")
        val tinyImageWidgetId = newWidgetId("TinyImage")
        val fourByTwoWidgetId = newWidgetId("FourByTwo")

        return WidgetsSample(
            previews =
                mapOf(
                    fillsBoundsWidgetId to
                        createBitmapPreview(cellWidth * 2, cellHeight * 2, Color.RED),
                    threeByTwoWidgetId to
                        createBitmapPreview(cellWidth * 3, cellHeight * 2, Color.GREEN),
                    noFillBoundsWidgetId to
                        createBitmapPreview(cellWidth * 3, cellHeight * 3, Color.BLUE),
                    tinyImageWidgetId to
                        createBitmapPreview(cellWidth, cellHeight / 2, Color.YELLOW),
                    fourByTwoWidgetId to
                        createBitmapPreview(cellWidth * 3, cellHeight * 2, Color.LTGRAY),
                ),
            widgetSizeGroups =
                listOf(
                    // widgets with 2x2 container size
                    WidgetSizeGroup(
                        previewContainerWidthPx = cellWidth * 2,
                        previewContainerHeightPx = cellHeight * 2,
                        widgets =
                            listOf(
                                twoByTwo(cellWidth, cellHeight)
                                    .copy(id = fillsBoundsWidgetId, label = "Fills bounds"),
                                threeByTwo(cellWidth, cellHeight)
                                    .copy(
                                        id = threeByTwoWidgetId,
                                        label = "Three by two",
                                        description = "Scaled to 2x2 container",
                                    ),
                                twoByTwo(cellWidth, cellHeight)
                                    .copy(
                                        id = noFillBoundsWidgetId,
                                        label = "Doesn't Fill bounds",
                                        description =
                                            "Actual image size is more like 3x3, it will be scaled.",
                                    ),
                                twoByTwo(cellWidth, cellHeight)
                                    .copy(
                                        id = tinyImageWidgetId,
                                        label = "Tiny image",
                                        description = "Should scale up",
                                    ),
                            ),
                    ),
                    // widgets with 4x2 size
                    WidgetSizeGroup(
                        previewContainerWidthPx = cellWidth * 4,
                        previewContainerHeightPx = cellHeight * 2,
                        widgets =
                            listOf(
                                fourByTwo(cellWidth, cellHeight)
                                    .copy(id = fourByTwoWidgetId, label = "FourByTwo 3x2 image")
                            ),
                    ),
                ),
        )
    }

    /** A group of 1x1 sized widgets. */
    fun oneByOneWidgets(screenWidth: Int): WidgetsSample {
        val (cellWidth, cellHeight) = testCellSize(screenWidth)

        val largeWidthWidgetId = newWidgetId("LargerWidth")
        val largeHeightWidgetId = newWidgetId("LargerHeight")
        val tinySizeWidgetId = newWidgetId("TinySize")
        val correctSizeWidthId = newWidgetId("CorrectSize")

        return WidgetsSample(
            widgetSizeGroups =
                listOf(
                    WidgetSizeGroup(
                        previewContainerWidthPx = cellWidth,
                        previewContainerHeightPx = cellHeight,
                        widgets =
                            listOf(
                                oneByOne(cellWidth, cellHeight)
                                    .copy(id = largeWidthWidgetId, label = "Larger width"),
                                oneByOne(cellWidth, cellHeight)
                                    .copy(
                                        id = largeHeightWidgetId,
                                        label = "Larger height",
                                        description = "Has a description",
                                    ),
                                oneByOne(cellWidth, cellHeight)
                                    .copy(
                                        id = newWidgetId("TinySize"),
                                        label = "Tiny size",
                                        description = "Slightly longer description",
                                    ),
                                oneByOne(cellWidth, cellHeight)
                                    .copy(id = newWidgetId("CorrectSize"), label = "Correct size"),
                            ),
                    )
                ),
            previews =
                mapOf(
                    largeWidthWidgetId to
                        createBitmapPreview(cellWidth * 2, cellHeight, Color.YELLOW),
                    largeHeightWidgetId to
                        createBitmapPreview(cellWidth, cellHeight * 3, Color.BLUE),
                    tinySizeWidgetId to
                        createBitmapPreview(cellWidth / 2, cellHeight / 3, Color.RED),
                    correctSizeWidthId to createBitmapPreview(cellWidth, cellHeight, Color.GREEN),
                ),
        )
    }

    private fun oneByOne(cellWidth: Int, cellHeight: Int) =
        PickableWidget(
            id = newWidgetId("OneByOne"),
            appId = TEST_WIDGET_APP_ID,
            label = "One by One",
            description = null,
            sizeInfo =
                WidgetSizeInfo(
                    spanX = 1,
                    spanY = 1,
                    widthPx = cellWidth,
                    heightPx = cellHeight,
                    containerSpanX = 1,
                    containerSpanY = 1,
                    containerWidthPx = cellWidth,
                    containerHeightPx = cellHeight,
                ),
            widgetInfo = WidgetInfo.AppWidgetInfo(newAppWidgetInfo("OneByOneProvider")),
        )

    private fun twoByTwo(cellWidth: Int, cellHeight: Int) =
        PickableWidget(
            id = newWidgetId("TwoByTwo"),
            appId = TEST_WIDGET_APP_ID,
            label = "Two by Two",
            description = null,
            widgetInfo = WidgetInfo.AppWidgetInfo(newAppWidgetInfo("TwoByTwoProvider")),
            sizeInfo =
                WidgetSizeInfo(
                    spanX = 2,
                    spanY = 2,
                    widthPx = cellWidth * 2,
                    heightPx = cellHeight * 2,
                    containerSpanX = 2,
                    containerSpanY = 2,
                    // container same as size
                    containerWidthPx = cellWidth * 2,
                    containerHeightPx = cellHeight * 2,
                ),
        )

    private fun threeByTwo(cellWidth: Int, cellHeight: Int) =
        PickableWidget(
            id = newWidgetId("ThreeByTwo"),
            appId = TEST_WIDGET_APP_ID,
            label = "Three by two",
            description = null,
            widgetInfo = WidgetInfo.AppWidgetInfo(newAppWidgetInfo("threeByTwoProvider")),
            sizeInfo =
                WidgetSizeInfo(
                    spanX = 3,
                    spanY = 2,
                    widthPx = cellWidth * 3,
                    heightPx = cellHeight * 2,
                    containerSpanX = 2,
                    containerSpanY = 2,
                    // 3x2s are bucketed to 2x2 container
                    containerWidthPx = cellWidth * 2,
                    containerHeightPx = cellHeight * 2,
                ),
        )

    private fun fourByTwo(cellWidth: Int, cellHeight: Int) =
        PickableWidget(
            id = newWidgetId("FourByTwo"),
            appId = TEST_WIDGET_APP_ID,
            label = "Four by two",
            description = null,
            widgetInfo = WidgetInfo.AppWidgetInfo(newAppWidgetInfo("FourByTwoProvider")),
            sizeInfo =
                WidgetSizeInfo(
                    spanX = 4,
                    spanY = 2,
                    widthPx = cellWidth * 4,
                    heightPx = cellHeight * 2,
                    containerSpanX = 4,
                    containerSpanY = 2,
                    // container same as size
                    containerWidthPx = cellWidth * 4,
                    containerHeightPx = cellHeight * 2,
                ),
        )

    private fun newWidgetId(suffix: String) =
        WidgetId(
            ComponentName.createRelative(PACKAGE_NAME, "WidgetReceiver$suffix"),
            myUserHandle(),
        )

    private fun createBitmapPreview(
        width: Int,
        height: Int,
        color: Int,
    ): WidgetPreview.BitmapWidgetPreview {
        val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(color)

        return WidgetPreview.BitmapWidgetPreview(bitmap = bitmap)
    }

    private fun testCellSize(screenWidthPx: Int): IntSize {
        val gridCols = 4
        val totalHorizontalGridCellPadding = 40 * 2
        val cellWidth = ((screenWidthPx / gridCols) - totalHorizontalGridCellPadding)
        val cellHeight = cellWidth + 20 // assume cell height slightly larger than width

        return IntSize(cellWidth, cellHeight)
    }

    private fun newAppWidgetInfo(providerClassName: String): AppWidgetProviderInfo {
        val activityInfo = ActivityInfo()
        activityInfo.applicationInfo = ApplicationInfo()
        activityInfo.applicationInfo.uid = Process.myUid()
        return AppWidgetProviderInfo().apply {
            providerInfo = activityInfo
            provider = ComponentName.createRelative(PACKAGE_NAME, providerClassName)
        }
    }

    private const val PACKAGE_NAME = "com.android.widgetpicker.tests"
    private val TEST_WIDGET_APP_ID = WidgetAppId(PACKAGE_NAME, myUserHandle(), category = null)
}

data class WidgetsSample(
    val widgetSizeGroups: List<WidgetSizeGroup>,
    val previews: Map<WidgetId, WidgetPreview>,
)
