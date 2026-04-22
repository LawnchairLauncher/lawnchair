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
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.View
import android.view.View.DragShadowBuilder
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.launcher3.widgetpicker.shared.model.WidgetId
import com.android.launcher3.widgetpicker.shared.model.WidgetInfo
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview
import com.android.launcher3.widgetpicker.shared.model.WidgetSizeInfo
import com.android.launcher3.widgetpicker.shared.model.isAppWidget
import com.android.launcher3.widgetpicker.ui.WidgetInteractionInfo
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import kotlin.math.roundToInt

/** Renders a different types of preview for an appwidget. */
@Composable
fun WidgetPreview(
    id: WidgetId,
    sizeInfo: WidgetSizeInfo,
    preview: WidgetPreview,
    widgetInfo: WidgetInfo,
    modifier: Modifier = Modifier,
    showDragShadow: Boolean,
    onWidgetInteraction: (WidgetInteractionInfo) -> Unit,
    onAddButtonToggle: (WidgetId) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current

    val widgetRadius = dimensionResource(android.R.dimen.system_app_widget_background_radius)

    val density = LocalDensity.current
    val containerSize =
        with(density) {
            DpSize(sizeInfo.containerWidthPx.toDp(), sizeInfo.containerHeightPx.toDp())
        }

    Box(
        modifier =
            modifier.wrapContentSize().clickable(
                interactionSource = interactionSource,
                // no ripples for preview taps that toggle the add button.
                indication = null,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onAddButtonToggle(id)
            }
    ) {
        when (preview) {
            is WidgetPreview.PlaceholderWidgetPreview ->
                PlaceholderWidgetPreview(size = containerSize, widgetRadius = widgetRadius)

            is WidgetPreview.BitmapWidgetPreview ->
                BitmapWidgetPreview(
                    bitmap = preview.bitmap,
                    size = containerSize,
                    widgetRadius = widgetRadius,
                    widgetInfo = widgetInfo,
                    showDragShadow = showDragShadow,
                    onWidgetInteraction = onWidgetInteraction,
                )

            is WidgetPreview.RemoteViewsWidgetPreview -> {
                check(widgetInfo.isAppWidget())
                RemoteViewsWidgetPreview(
                    remoteViews = preview.remoteViews,
                    widgetInfo = widgetInfo,
                    sizeInfo = sizeInfo,
                    widgetRadius = widgetRadius,
                    showDragShadow = showDragShadow,
                    onWidgetInteraction = onWidgetInteraction,
                )
            }

            is WidgetPreview.ProviderInfoWidgetPreview -> {
                check(widgetInfo.isAppWidget())
                RemoteViewsWidgetPreview(
                    previewLayoutProviderInfo = preview.providerInfo,
                    widgetInfo = widgetInfo,
                    sizeInfo = sizeInfo,
                    widgetRadius = widgetRadius,
                    showDragShadow = showDragShadow,
                    onWidgetInteraction = onWidgetInteraction,
                )
            }
        }
    }
}

@Composable
private fun PlaceholderWidgetPreview(size: DpSize, widgetRadius: Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.width(size.width)
                .height(size.height)
                .background(
                    color = WidgetPickerTheme.colors.widgetPlaceholderBackground,
                    shape = RoundedCornerShape(widgetRadius),
                ),
    ) {
        CircularProgressIndicator(color = WidgetPickerTheme.colors.widgetPlaceholderContent)
    }
}

@Composable
private fun BitmapWidgetPreview(
    bitmap: Bitmap,
    size: DpSize,
    widgetInfo: WidgetInfo,
    widgetRadius: Dp,
    showDragShadow: Boolean,
    onWidgetInteraction: (WidgetInteractionInfo) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val scaledBitmapDimensions by
        remember(bitmap, density, size) {
            derivedStateOf { bitmap.calculateScaledDimensions(density, size, widgetRadius) }
        }

    val dragState by
        remember(widgetInfo, showDragShadow) {
            derivedStateOf {
                DragState(
                    widgetInfo,
                    if (showDragShadow) {
                        ImageBitmapDragShadowBuilder(context, bitmap, scaledBitmapDimensions)
                    } else {
                        TransparentDragShadowBuilder
                    },
                )
            }
        }

    var imagePositionInParent by remember { mutableStateOf(Offset.Zero) }

    // A view to start drag and drop; the compose drag and drop doesn't provide pre-drag hooks.
    // So, we simulate a drag and drop with a backing view.
    val dragView: View = remember(widgetInfo) { FrameLayout(context) }
    AndroidView(factory = { dragView })

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null, // only visual (widget details provides the readable info)
        contentScale = ContentScale.FillBounds,
        modifier =
            Modifier.onGloballyPositioned { coordinates ->
                    imagePositionInParent = coordinates.positionInParent()
                }
                .pointerInput(bitmap) {
                    detectDragGesturesAfterLongPress(
                        onDrag = { change, _ -> change.consume() },
                        onDragStart = { offset ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                            dragState.startDrag(dragView)

                            val bounds =
                                calculateImageDragBounds(
                                    scaledBitmapDimensions = scaledBitmapDimensions,
                                    imagePositionInParent = imagePositionInParent,
                                    offset = offset,
                                )
                            onWidgetInteraction(
                                WidgetInteractionInfo.WidgetDragInfo(
                                    mimeType = dragState.pickerMimeType,
                                    widgetInfo = widgetInfo,
                                    bounds = bounds,
                                    widthPx = scaledBitmapDimensions.scaledSizePx.width,
                                    heightPx = scaledBitmapDimensions.scaledSizePx.height,
                                    previewInfo = WidgetPreview.BitmapWidgetPreview(bitmap = bitmap),
                                )
                            )
                        },
                    )
                }
                .width(scaledBitmapDimensions.scaledSizeDp.width)
                .height(scaledBitmapDimensions.scaledSizeDp.height)
                .clip(shape = RoundedCornerShape(scaledBitmapDimensions.scaledRadiusDp)),
    )
}

/** Returns the visual bounds of image offset by the touch point represented by [offset]. */
private fun calculateImageDragBounds(
    scaledBitmapDimensions: ImageScaledDimensions,
    imagePositionInParent: Offset,
    offset: Offset,
): Rect {
    val bounds = Rect()
    bounds.left = 0
    bounds.top = 0
    bounds.right = scaledBitmapDimensions.scaledSizePx.width
    bounds.bottom = scaledBitmapDimensions.scaledSizePx.height
    val xOffset: Int = (imagePositionInParent.x - offset.x).roundToInt()
    val yOffset: Int = (imagePositionInParent.y - offset.y).roundToInt()
    bounds.offset(xOffset, yOffset)
    return bounds
}

private fun Bitmap.calculateScaledDimensions(density: Density, size: DpSize, widgetRadius: Dp) =
    with(density) {
        val bitmapSize = DpSize(width = width.toDp(), height = height.toDp())
        val bitmapAspectRatio = bitmapSize.width / bitmapSize.height
        val containerAspectRatio: Float = size.width / size.height

        // Scale by width if image has larger aspect ratio than the container else by
        // height; and avoid cropping the previews.
        val scale =
            if (bitmapAspectRatio > containerAspectRatio) {
                size.width / bitmapSize.width
            } else {
                size.height / bitmapSize.height
            }

        val scaledDpSize =
            DpSize(width = bitmapSize.width * scale, height = bitmapSize.height * scale)
        val scaledPxSize =
            IntSize(
                width = scaledDpSize.width.roundToPx(),
                height = scaledDpSize.height.roundToPx(),
            )
        val scaledRadius = (widgetRadius * scale).coerceAtMost(widgetRadius).value.roundToInt().dp

        ImageScaledDimensions(
            scale = scale,
            scaledSizePx = scaledPxSize,
            scaledSizeDp = scaledDpSize,
            scaledRadiusDp = scaledRadius,
            scaledRadiusPx = scaledRadius.toPx(),
        )
    }

@Composable
private fun RemoteViewsWidgetPreview(
    remoteViews: RemoteViews? = null,
    previewLayoutProviderInfo: AppWidgetProviderInfo? = null,
    widgetInfo: WidgetInfo.AppWidgetInfo,
    sizeInfo: WidgetSizeInfo,
    widgetRadius: Dp,
    onWidgetInteraction: (WidgetInteractionInfo) -> Unit,
    showDragShadow: Boolean,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val appWidgetHostView by
        remember(sizeInfo, widgetInfo) {
            derivedStateOf {
                WidgetPreviewHostView(context).apply {
                    setContainerSizePx(
                        IntSize(sizeInfo.containerWidthPx, sizeInfo.containerHeightPx)
                    )
                }
            }
        }

    val dragState by remember {
        derivedStateOf {
            DragState(
                widgetInfo = widgetInfo,
                dragShadowBuilder =
                    if (showDragShadow) {
                        DragShadowBuilder(appWidgetHostView)
                    } else {
                        TransparentDragShadowBuilder
                    },
            )
        }
    }

    key(appWidgetHostView) {
        AndroidView(
            modifier =
                Modifier.pointerInput(appWidgetHostView) {
                        detectDragGesturesAfterLongPress(
                            onDrag = { change, _ -> change.consume() },
                            onDragStart = { offset ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragState.startDrag(appWidgetHostView)

                                onWidgetInteraction(
                                    WidgetInteractionInfo.WidgetDragInfo(
                                        mimeType = dragState.pickerMimeType,
                                        widgetInfo = widgetInfo,
                                        bounds = appWidgetHostView.getDragBoundsForOffset(offset),
                                        widthPx = appWidgetHostView.measuredWidth,
                                        heightPx = appWidgetHostView.measuredHeight,
                                        previewInfo =
                                            when {
                                                remoteViews != null ->
                                                    WidgetPreview.RemoteViewsWidgetPreview(
                                                        remoteViews = remoteViews
                                                    )

                                                previewLayoutProviderInfo != null ->
                                                    WidgetPreview.ProviderInfoWidgetPreview(
                                                        providerInfo = previewLayoutProviderInfo
                                                    )

                                                else ->
                                                    throw IllegalStateException(
                                                        "No preview during drag"
                                                    )
                                            },
                                    )
                                )
                            },
                        )
                    }
                    .wrapContentSize()
                    .clip(RoundedCornerShape(widgetRadius)),
            factory = { appWidgetHostView },
            update = { view ->
                // if preview.remoteViews is null, initial layout will render.
                // the databasePreviewLoader overwrites the initial layout in "preview.providerInfo"
                // to be the previewLayout.
                view.setAppWidget(
                    /*appWidgetId=*/ NO_OP_APP_WIDGET_ID,
                    /*info=*/ previewLayoutProviderInfo ?: widgetInfo.appWidgetProviderInfo,
                )
                view.updateAppWidget(remoteViews)
            },
            onReset = {}, // enable reuse ("update" sets the and preview info)
        )
    }
}

// We don't care about appWidgetId since this is a preview.
private const val NO_OP_APP_WIDGET_ID = -1
