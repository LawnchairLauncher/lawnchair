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

package com.android.launcher3.widgetpicker.shared.model

import android.appwidget.AppWidgetProviderInfo
import android.graphics.Bitmap
import android.widget.RemoteViews

/** Represents different types & states of a widget preview. */
sealed class WidgetPreview {
    /**
     * Holds widget's preview as a [Bitmap].
     *
     * This is the case when developer only provides image previews via the `android:previewImage`
     * configuration on their app widget info xml.
     */
    data class BitmapWidgetPreview(val bitmap: Bitmap) : WidgetPreview()

    /**
     * Holds widget preview as [RemoteViews].
     *
     * This is the case when developer provides previews using the generated previews api
     * (`AppWidgetManager.setWidgetPreview`).
     */
    data class RemoteViewsWidgetPreview(val remoteViews: RemoteViews) : WidgetPreview()

    /**
     * Holds the widget previews as [AppWidgetProviderInfo] containing the layout xml widget
     * preview.
     *
     * @property providerInfo [AppWidgetProviderInfo] where initial layout is set to the widget's
     *   `android:previewLayout`.
     */
    data class ProviderInfoWidgetPreview(val providerInfo: AppWidgetProviderInfo) : WidgetPreview()

    /** Represents a state where widget preview is not available. */
    data object PlaceholderWidgetPreview : WidgetPreview()
}
