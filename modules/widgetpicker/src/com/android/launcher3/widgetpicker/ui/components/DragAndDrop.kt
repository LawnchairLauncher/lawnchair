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

import android.content.ClipData
import android.content.ClipDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.os.UserHandle
import android.view.View
import android.view.View.DragShadowBuilder
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.drawable.RoundedBitmapDrawable
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.android.launcher3.widgetpicker.shared.model.WidgetInfo
import java.util.UUID

/** Information about the image's dimensions post scaling. */
data class ImageScaledDimensions(
    val scale: Float,
    val scaledSizeDp: DpSize,
    val scaledSizePx: IntSize,
    val scaledRadiusDp: Dp,
    val scaledRadiusPx: Float,
)

/** A [DragShadowBuilder] that draws drag shadow using the provided bitmap and image dimensions. */
class ImageBitmapDragShadowBuilder(
    context: Context,
    bitmap: Bitmap,
    imageScaledDimensions: ImageScaledDimensions,
) : DragShadowBuilder() {
    private val shadowWidth = imageScaledDimensions.scaledSizePx.width
    private val shadowHeight = imageScaledDimensions.scaledSizePx.height

    private val shadowDrawable: RoundedBitmapDrawable =
        RoundedBitmapDrawableFactory.create(context.resources, bitmap).apply {
            cornerRadius = imageScaledDimensions.scaledRadiusPx
        }

    override fun onProvideShadowMetrics(outShadowSize: Point?, outShadowTouchPoint: Point?) {
        outShadowSize?.set(shadowWidth, shadowHeight)
        // Set the touch point's position to be in the middle of the drag shadow.
        outShadowTouchPoint?.set(shadowWidth / 2, shadowHeight / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        // The Drawable's native bounds may be different than the source ImageView. Change it to
        // to the needed size.
        val oldBounds: Rect = shadowDrawable.copyBounds()
        shadowDrawable.setBounds(0, 0, shadowWidth, shadowHeight)
        canvas.let { shadowDrawable.draw(it) }
        shadowDrawable.bounds = oldBounds
    }
}

/**
 * A [DragShadowBuilder] that draws a transparent drag shadow; useful for cases when the actual drag
 * shadow is displayed by the host.
 */
object TransparentDragShadowBuilder : DragShadowBuilder() {
    private const val SHADOW_SIZE = 10

    override fun onDrawShadow(canvas: Canvas) {}

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(SHADOW_SIZE, SHADOW_SIZE)
        outShadowTouchPoint.set(SHADOW_SIZE / 2, SHADOW_SIZE / 2)
    }
}

/** State containing information to start a drag for a widget. */
class DragState(
    private val widgetInfo: WidgetInfo,
    private val dragShadowBuilder: DragShadowBuilder,
) {
    private val uniqueId = UUID.randomUUID().toString()
    val pickerMimeType = "com.android.launcher3.widgetpicker.drag_and_drop/$uniqueId"

    fun startDrag(view: View) {
        val clipData =
            ClipData(
                ClipDescription(
                    // not displayed anywhere; so, set to empty.
                    /* label= */ "",
                    arrayOf(
                        // unique picker specific mime type.
                        pickerMimeType,
                        // indicates that the clip item contains an intent (with extras about widget
                        // info).
                        ClipDescription.MIMETYPE_TEXT_INTENT,
                    ),
                ),
                ClipData.Item(
                    when (widgetInfo) {
                        is WidgetInfo.AppWidgetInfo ->
                            buildIntentForClipData(
                                user = widgetInfo.appWidgetProviderInfo.profile,
                                componentName = widgetInfo.appWidgetProviderInfo.provider,
                            )

                        is WidgetInfo.ShortcutInfo ->
                            buildIntentForClipData(
                                user = widgetInfo.launcherActivityInfo.user,
                                componentName = widgetInfo.launcherActivityInfo.componentName,
                            )
                    }
                ),
            )

        view.startDragAndDrop(
            clipData,
            /*shadowBuilder=*/ dragShadowBuilder,
            /*myLocalState=*/ null,
            View.DRAG_FLAG_GLOBAL,
        )
    }

    private fun buildIntentForClipData(user: UserHandle, componentName: ComponentName): Intent =
        Intent()
            .putExtra(Intent.EXTRA_USER, user)
            .putExtra(Intent.EXTRA_COMPONENT_NAME, componentName)
}
