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

package com.android.launcher3.widgetpicker.listeners

import android.graphics.Point
import android.graphics.Rect
import android.view.View
import com.android.launcher3.PendingAddItemInfo
import com.android.launcher3.dragndrop.BaseItemDragListener
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.pm.ShortcutConfigActivityInfo.ShortcutConfigActivityInfoVO
import com.android.launcher3.widget.DatabaseWidgetPreviewLoader.WidgetPreviewInfo
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.PendingAddShortcutInfo
import com.android.launcher3.widget.PendingAddWidgetInfo
import com.android.launcher3.widget.PendingItemDragHelper
import com.android.launcher3.widgetpicker.shared.model.WidgetInfo
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview
import com.android.launcher3.widgetpicker.shared.model.isAppWidget

/**
 * A callback listener of type [BaseItemDragListener] that handles widget drag and drop from widget
 * picker hosted in a separate activity than home screen.
 *
 * Responsible for initializing the [PendingItemDragHelper] that then handles the rest of the drag
 * and drop (including showing a drag shadow for the widget).
 *
 * @param container the source container in which widget was interacted with; enables the stats logs
 *   to capture the source container.
 * @param mimeType a mime type used by widget picker when attaching this listener for a specific
 *   widget's drag and drop session.
 * @param widgetInfo metadata of the widget being dragged
 * @param widgetPreview provides the preview information for widgets
 * @param previewRect the bounds of widget's preview offset by the point of long press
 * @param previewWidth width of the preview as it appears in the widget picker.
 */
class WidgetPickerDragItemListener(
    private val container: Int,
    private val mimeType: String,
    private val widgetInfo: WidgetInfo,
    private val widgetPreview: WidgetPreview,
    previewRect: Rect,
    previewWidth: Int,
) : BaseItemDragListener(previewRect, previewWidth, previewWidth) {
    override fun getMimeType(): String = mimeType

    override fun startDrag(
        previewRect: Rect,
        previewBitmapWidth: Int,
        previewViewWidth: Int,
        screenPos: Point,
        options: DragOptions,
    ) {
        val pendingAddItemInfo: PendingAddItemInfo =
            when (widgetInfo) {
                is WidgetInfo.AppWidgetInfo -> {
                    val launcherProviderInfo =
                        LauncherAppWidgetProviderInfo.fromProviderInfo(
                            mLauncher,
                            widgetInfo.appWidgetProviderInfo,
                        )
                    PendingAddWidgetInfo(launcherProviderInfo, container)
                }

                is WidgetInfo.ShortcutInfo ->
                    PendingAddShortcutInfo(
                        ShortcutConfigActivityInfoVO(widgetInfo.launcherActivityInfo)
                    )
            }

        val view = View(mLauncher)
        view.tag = pendingAddItemInfo

        val dragHelper = PendingItemDragHelper(view)

        if (widgetInfo.isAppWidget()) {
            setAppWidgetPreviewInfo(widgetPreview, widgetInfo, dragHelper)
        } // shortcut preview is fetched by home screen.

        dragHelper.startDrag(
            previewRect,
            previewBitmapWidth,
            previewViewWidth,
            screenPos,
            /*source=*/ this,
            options,
        )
    }

    private fun setAppWidgetPreviewInfo(
        appWidgetPreview: WidgetPreview,
        widgetInfo: WidgetInfo.AppWidgetInfo,
        dragHelper: PendingItemDragHelper,
    ) {
        val info = WidgetPreviewInfo()
        when (appWidgetPreview) {
            is WidgetPreview.BitmapWidgetPreview -> {
                info.previewBitmap = appWidgetPreview.bitmap
                info.providerInfo = widgetInfo.appWidgetProviderInfo
            }

            is WidgetPreview.ProviderInfoWidgetPreview -> {
                info.providerInfo = appWidgetPreview.providerInfo
            }

            is WidgetPreview.RemoteViewsWidgetPreview -> {
                info.remoteViews = appWidgetPreview.remoteViews
                info.providerInfo = widgetInfo.appWidgetProviderInfo
            }

            else ->
                throw IllegalStateException(
                    "Unsupported preview type when dropping widget to launcher"
                )
        }
        dragHelper.setWidgetPreviewInfo(info)
    }
}
