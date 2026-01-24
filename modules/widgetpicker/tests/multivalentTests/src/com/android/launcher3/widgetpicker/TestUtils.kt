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

package com.android.launcher3.widgetpicker

import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.UserHandle
import com.android.launcher3.widgetpicker.shared.model.PickableWidget
import com.android.launcher3.widgetpicker.shared.model.WidgetApp
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import com.android.launcher3.widgetpicker.shared.model.WidgetId
import com.android.launcher3.widgetpicker.shared.model.WidgetInfo
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview
import com.android.launcher3.widgetpicker.shared.model.WidgetSizeInfo
import com.android.launcher3.widgetpicker.shared.model.WidgetUserProfile
import com.android.launcher3.widgetpicker.shared.model.WidgetUserProfileType

/** Utilities and constants to build test data. */
object TestUtils {
    val personalUser: UserHandle = UserHandle.of(1)
    val workUser: UserHandle = UserHandle.of(10)

    val PERSONAL_LABEL = "personal"
    val WORK_LABEL = "work"

    val widgetUserProfilePersonal =
        WidgetUserProfile(type = WidgetUserProfileType.PERSONAL, label = PERSONAL_LABEL)
    val widgetUserProfileWork =
        WidgetUserProfile(type = WidgetUserProfileType.WORK, label = WORK_LABEL)

    private const val PACKAGE_NAME = "com.example.test"
    private val PERSONAL_WIDGET_APP_1 = buildWidgetAppId("AppOne", personalUser)
    private val PERSONAL_WIDGET_APP_2 = buildWidgetAppId("AppTwo", personalUser)
    private val WORK_WIDGET_APP_1 = buildWidgetAppId("WAppOne", workUser)

    val PERSONAL_TEST_APPS =
        listOf(
            WidgetApp(
                id = PERSONAL_WIDGET_APP_1,
                title = "Personal App 1",
                widgets =
                    listOf(
                        buildTestWidget(
                            providerClassName = "PersonalWidget1A",
                            widgetAppId = PERSONAL_WIDGET_APP_1,
                            userHandle = personalUser,
                        )
                    ),
            ),
            WidgetApp(
                id = PERSONAL_WIDGET_APP_2,
                title = "Personal App 2",
                widgets =
                    listOf(
                        buildTestWidget(
                            providerClassName = "PersonalWidget2A",
                            widgetAppId = PERSONAL_WIDGET_APP_2,
                            userHandle = personalUser,
                        )
                    ),
            ),
        )

    val WORK_TEST_APPS =
        listOf(
            WidgetApp(
                id = WORK_WIDGET_APP_1,
                title = "Work App 1",
                widgets =
                    listOf(
                        buildTestWidget(
                            providerClassName = "WorkWidget1A",
                            widgetAppId = WORK_WIDGET_APP_1,
                            userHandle = workUser,
                        ),
                        buildTestWidget(
                            providerClassName = "WorkWidget1B",
                            widgetAppId = WORK_WIDGET_APP_1,
                            userHandle = workUser,
                        ),
                    ),
            )
        )

    private fun buildWidgetAppId(suffix: String, userHandle: UserHandle) =
        WidgetAppId(packageName = "$PACKAGE_NAME.$suffix", category = null, userHandle = userHandle)

    fun buildTestWidget(
        providerClassName: String,
        category: Int = WIDGET_CATEGORY_HOME_SCREEN,
        widgetAppId: WidgetAppId? = null,
        userHandle: UserHandle = UserHandle.of(0),
    ): PickableWidget {
        val finalWidgetAppId =
            widgetAppId
                ?: WidgetAppId(packageName = PACKAGE_NAME, userHandle = userHandle, category = null)

        return PickableWidget(
            id =
                WidgetId(ComponentName.createRelative(PACKAGE_NAME, providerClassName), userHandle),
            appId = finalWidgetAppId,
            label = providerClassName,
            description = null,
            widgetInfo =
                WidgetInfo.AppWidgetInfo(
                    AppWidgetProviderInfo().apply {
                        widgetCategory = category
                        provider = ComponentName.createRelative(PACKAGE_NAME, providerClassName)
                    }
                ),
            sizeInfo =
                WidgetSizeInfo(
                    spanX = 2,
                    spanY = 2,
                    widthPx = 200,
                    heightPx = 200,
                    containerSpanX = 2,
                    containerSpanY = 2,
                    containerWidthPx = 200,
                    containerHeightPx = 200,
                ),
        )
    }

    fun createBitmapPreview(
        width: Int = 200,
        height: Int = 200,
        color: Int = Color.RED,
    ): WidgetPreview.BitmapWidgetPreview {
        val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(color)

        return WidgetPreview.BitmapWidgetPreview(bitmap = bitmap)
    }
}
