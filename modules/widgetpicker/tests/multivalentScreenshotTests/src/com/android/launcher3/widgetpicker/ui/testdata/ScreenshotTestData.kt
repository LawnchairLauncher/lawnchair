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

package com.android.launcher3.widgetpicker.ui.testdata

import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.UserHandle
import androidx.compose.ui.unit.IntSize
import com.android.launcher3.widgetpicker.shared.model.AppIcon
import com.android.launcher3.widgetpicker.shared.model.AppIconBadge
import com.android.launcher3.widgetpicker.shared.model.PickableWidget
import com.android.launcher3.widgetpicker.shared.model.WidgetApp
import com.android.launcher3.widgetpicker.shared.model.WidgetAppIcon
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import com.android.launcher3.widgetpicker.shared.model.WidgetId
import com.android.launcher3.widgetpicker.shared.model.WidgetInfo
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview
import com.android.launcher3.widgetpicker.shared.model.WidgetSizeInfo
import com.android.launcher3.widgetpicker.shared.model.WidgetUserProfile
import com.android.launcher3.widgetpicker.shared.model.WidgetUserProfileType
import com.android.launcher3.widgetpicker.ui.components.WidgetAppIconTest.Companion.BITMAP_SIZE
import com.android.launcher3.widgetpicker.ui.components.WidgetAppIconTest.Companion.CIRCLE_CENTER
import com.android.launcher3.widgetpicker.ui.components.WidgetAppIconTest.Companion.ROUND_RECT_RADIUS

/** Builds test data for provided [screenWidth] and [screenHeight]. */
class ScreenshotTestData(private val screenWidth: Int, private val screenHeight: Int) {
    private val testCellSize = testCellSize(screenWidth)

    private val widgetApp1 = buildWidgetAppId("AppOne")
    private val widget1A =
        buildTestWidget(suffix = "1A", widgetAppId = widgetApp1, spanX = 2, spanY = 2)
    private val widget1B =
        buildTestWidget(suffix = "1A", widgetAppId = widgetApp1, spanX = 4, spanY = 2)

    private val widgetApp2 = buildWidgetAppId("AppTwo")
    private val widget2A =
        buildTestWidget(suffix = "2A", widgetAppId = widgetApp2, spanX = 1, spanY = 1)

    private val widgetApp3 = buildWidgetAppId("AppThree")
    private val widget3A =
        buildTestWidget(suffix = "3A", widgetAppId = widgetApp3, spanX = 2, spanY = 2)

    private val widgetApp4 = buildWidgetAppId("AppFour")
    private val widget4A =
        buildTestWidget(suffix = "4A", widgetAppId = widgetApp4, spanX = 1, spanY = 1)

    private val widgetApp5 = buildWidgetAppId("AppFive")
    private val widget5A =
        buildTestWidget(suffix = "5A", widgetAppId = widgetApp5, spanX = 4, spanY = 2)

    private val widgetApp6 = buildWidgetAppId("AppSix")
    private val widget6A =
        buildTestWidget(suffix = "6A", widgetAppId = widgetApp6, spanX = 4, spanY = 3)

    private val widgetApp7 = buildWidgetAppId("AppSeven")
    private val widget7A =
        buildTestWidget(suffix = "7A", widgetAppId = widgetApp7, spanX = 2, spanY = 1)

    fun widgetApps(): List<WidgetApp> {
        return listOf(
            WidgetApp(id = widgetApp1, title = "App 1", widgets = listOf(widget1A, widget1B)),
            WidgetApp(id = widgetApp2, title = "App 2", widgets = listOf(widget2A)),
            WidgetApp(id = widgetApp3, title = "App 3", widgets = listOf(widget3A)),
            WidgetApp(id = widgetApp4, title = "App 4", widgets = listOf(widget4A)),
            WidgetApp(id = widgetApp5, title = "Long App 5", widgets = listOf(widget5A)),
            WidgetApp(id = widgetApp6, title = "Super long App 6", widgets = listOf(widget6A)),
            WidgetApp(id = widgetApp7, title = "Much much longer App 7", widgets = listOf(widget7A)),
        )
    }

    fun featuredWidgets(): List<PickableWidget> =
        listOf(widget1A, widget2A, widget3A, widget4A, widget5A)

    fun widgetPreviews(): Map<WidgetId, WidgetPreview> =
        mapOf(
            widget1A.id to createBitmapPreview(widget1A.sizeInfo, Color.RED),
            widget1B.id to createBitmapPreview(widget1B.sizeInfo, Color.BLUE),
            widget2A.id to createBitmapPreview(widget2A.sizeInfo, Color.YELLOW),
            widget3A.id to createBitmapPreview(widget3A.sizeInfo, Color.GREEN),
            widget4A.id to createBitmapPreview(widget4A.sizeInfo, Color.RED),
            widget5A.id to createBitmapPreview(widget5A.sizeInfo, Color.GREEN),
        )

    fun widgetAppIcons(): Map<WidgetAppId, WidgetAppIcon> =
        mapOf(
            widgetApp1 to createWidgetAppIcon(Color.DKGRAY),
            widgetApp2 to createWidgetAppIcon(Color.RED),
            widgetApp3 to createWidgetAppIcon(Color.BLUE),
            widgetApp4 to createWidgetAppIcon(Color.GREEN),
            widgetApp5 to createWidgetAppIcon(Color.LTGRAY),
            widgetApp6 to createWidgetAppIcon(Color.YELLOW),
            widgetApp7 to createWidgetAppIcon(Color.RED),
        )

    private fun buildTestWidget(
        suffix: String,
        category: Int = WIDGET_CATEGORY_HOME_SCREEN,
        widgetAppId: WidgetAppId,
        spanX: Int,
        spanY: Int,
    ): PickableWidget {
        return PickableWidget(
            id =
                WidgetId(
                    ComponentName.createRelative(PACKAGE_NAME, "WidgetProvider$suffix"),
                    UserHandle.of(0),
                ),
            appId = widgetAppId,
            label = "Widget $suffix",
            description = null,
            widgetInfo =
                WidgetInfo.AppWidgetInfo(
                    AppWidgetProviderInfo().apply {
                        widgetCategory = category
                        provider = ComponentName.createRelative(PACKAGE_NAME, suffix)
                    }
                ),
            sizeInfo =
                WidgetSizeInfo(
                    spanX = spanX,
                    spanY = spanY,
                    widthPx = testCellSize.width * spanX,
                    heightPx = testCellSize.height * spanY,
                    containerSpanX = spanX,
                    containerSpanY = spanY,
                    containerWidthPx = testCellSize.width * spanX,
                    containerHeightPx = testCellSize.height * spanY,
                ),
        )
    }

    companion object {
        private const val PACKAGE_NAME = "com.example.test"

        private val personalUser: UserHandle = UserHandle.of(1)
        private const val PERSONAL_LABEL = "personal"
        val widgetUserProfilePersonal =
            WidgetUserProfile(type = WidgetUserProfileType.PERSONAL, label = PERSONAL_LABEL)

        private fun buildWidgetAppId(suffix: String) =
            WidgetAppId(
                packageName = "$PACKAGE_NAME.$suffix",
                category = null,
                userHandle = personalUser,
            )

        fun createBitmapPreview(
            widgetSizeInfo: WidgetSizeInfo,
            color: Int = Color.RED,
        ): WidgetPreview.BitmapWidgetPreview {
            val bitmap: Bitmap =
                Bitmap.createBitmap(
                    widgetSizeInfo.widthPx,
                    widgetSizeInfo.heightPx,
                    Bitmap.Config.ARGB_8888,
                )
            Canvas(bitmap).drawColor(color)

            return WidgetPreview.BitmapWidgetPreview(bitmap = bitmap)
        }

        private fun testCellSize(screenWidthPx: Int): IntSize {
            val gridCols = 4
            val totalHorizontalGridCellPadding = 40 * 2
            val availableWidth =
                if (screenWidthPx > 2000) {
                    screenWidthPx / 2
                } else {
                    screenWidthPx
                }
            val cellWidth = ((availableWidth / gridCols) - totalHorizontalGridCellPadding)
            val cellHeight = cellWidth + 20 // assume cell height slightly larger than width

            return IntSize(cellWidth, cellHeight)
        }

        fun createWidgetAppIcon(color: Int, fullBleed: Boolean = true): WidgetAppIcon =
            WidgetAppIcon(icon = createBitmapIcon(color, fullBleed), badge = AppIconBadge.NoBadge)

        fun createBitmapIcon(color: Int, fullBleed: Boolean = true): AppIcon {
            val bitmap: Bitmap =
                Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            if (!fullBleed) {
                val paint =
                    Paint().apply {
                        isAntiAlias = true
                        setColor(color)
                        setShadowLayer(CIRCLE_CENTER, CIRCLE_CENTER, CIRCLE_CENTER, Color.WHITE)
                    }
                canvas.drawRoundRect(
                    /*left=*/ 0f,
                    /*top=*/ 0f,
                    /*right=*/ BITMAP_SIZE.toFloat(),
                    /*bottom=*/ BITMAP_SIZE.toFloat(),
                    /*rx=*/ ROUND_RECT_RADIUS,
                    /*ry=*/ ROUND_RECT_RADIUS,
                    paint,
                )
            } else {
                canvas.drawColor(color)
            }

            return AppIcon.HighResBitmapIcon(bitmap = bitmap, isFullBleed = fullBleed)
        }
    }
}
