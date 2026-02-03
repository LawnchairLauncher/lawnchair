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

package com.android.launcher3.widgetpicker.ui

import android.graphics.Rect
import com.android.launcher3.widgetpicker.shared.model.WidgetInfo
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview

/**
 * General interface that clients can implement to listen to events from different types of widget
 * picker.
 */
interface WidgetPickerEventListeners {
    /** Called when the widget picker is dismissed. */
    fun onClose()

    /** Called when a widget is being dragged or added from picker. */
    fun onWidgetInteraction(widgetInteractionInfo: WidgetInteractionInfo)
}

/** Information passed in event listener when a widget is dragged or added from picker. */
sealed class WidgetInteractionInfo {
    /**
     * Information passed in event listener when a widget is dragged.
     *
     * @param widgetInfo metadata for the provider of the widget being dragged.
     * @param bounds current bounds of the widget's preview considering the drag offset and scale.
     * @param widthPx measured width of the preview.
     * @param heightPx measured height of the preview.
     * @param previewInfo information necessary to render a preview within host
     * @param mimeType a unique mime type set on clip data for the drag session
     */
    data class WidgetDragInfo(
        val widgetInfo: WidgetInfo,
        val bounds: Rect,
        val widthPx: Int,
        val heightPx: Int,
        val previewInfo: WidgetPreview,
        val mimeType: String,
    ) : WidgetInteractionInfo()

    /**
     * Information passed in event listener when a widget is added using tap to add.
     *
     * @param widgetInfo metadata for the provider of the widget being added.
     */
    data class WidgetAddInfo(val widgetInfo: WidgetInfo) : WidgetInteractionInfo()
}
