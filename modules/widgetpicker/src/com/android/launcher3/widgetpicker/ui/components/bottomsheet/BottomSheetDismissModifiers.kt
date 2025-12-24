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

package com.android.launcher3.widgetpicker.ui.components.bottomsheet

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.BottomSheetDismissDimensions.NOT_VISIBLE_PREDICTIVE_BACK_SCALE
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.BottomSheetDismissDimensions.OFFSET_DIFF_TO_TRIGGER_DISMISS_CALLBACK
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.BottomSheetDismissDimensions.PredictiveBackContentTransformOrigin
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.BottomSheetDismissDimensions.SETTLE_ANIMATION_SPEC
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.BottomSheetDismissDimensions.VISIBLE_PREDICTIVE_BACK_SCALE
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.BottomSheetDismissDimensions.isInvalidSize
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.BottomSheetDismissDimensions.predictiveBackMaxScaleXDistance
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.BottomSheetDismissDimensions.predictiveBackMaxScaleYDistance
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.BottomSheetDismissDimensions.sheetPositionalThreshold
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * A modifier that handles the dismiss gestures (e.g. pull down to dismiss, predictive back) for
 * dismissing the bottom sheet.
 *
 * Use in combination with [dismissableBottomSheetContent] that's applied on the content.
 *
 * @param onSheetOpen callback invoked when sheet is fully opened first time.
 * @param onDismissSheet final callback invoked when the sheet has settled animating after a gesture
 *   that led to dismissing the sheet.
 * @param maxHeight max height available for the sheet
 * @param enableNestedScrolling whether to support nested scrolling; can be set to false when using
 *   accessibility services.
 */
fun Modifier.dismissibleBottomSheet(
    sheetState: BottomSheetDismissState,
    onSheetOpen: () -> Unit,
    onDismissSheet: () -> Unit,
    maxHeight: Float,
    enableNestedScrolling: Boolean = true,
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val flingBehavior =
        AnchoredDraggableDefaults.flingBehavior(
            state = sheetState.anchoredDraggableState,
            positionalThreshold = { with(density) { sheetPositionalThreshold.toPx() } },
            animationSpec = SETTLE_ANIMATION_SPEC,
        )

    val draggableModifier =
        this.onSizeChanged { size -> sheetState.updateAnchors(size.height.toFloat()) }
            .offset {
                IntOffset(x = 0, y = sheetState.anchoredDraggableState.requireOffset().roundToInt())
            }
            .anchoredDraggable(
                state = sheetState.anchoredDraggableState,
                orientation = Orientation.Vertical,
                flingBehavior = flingBehavior,
            )
            .nestedScroll(
                BottomSheetNestedScrollConnection(
                    sheetState = sheetState,
                    flingBehavior = flingBehavior,
                    enabled = enableNestedScrolling,
                )
            )

    LaunchedEffect(Unit) { sheetState.expand() }

    LaunchedEffect(sheetState, maxHeight) {
        var previous = SheetPositionValue.COLLAPSED
        snapshotFlow { sheetState.anchoredDraggableState.currentValue }
            .collect {
                if (previous == SheetPositionValue.EXPANDED && it == SheetPositionValue.COLLAPSED) {
                    // We are about to close, monitor close offset and dismiss sheet once almost
                    // down.
                    snapshotFlow { sheetState.anchoredDraggableState.offset }
                        .collect { offset ->
                            val offsetRemaining = abs(offset - maxHeight)
                            if (offsetRemaining < OFFSET_DIFF_TO_TRIGGER_DISMISS_CALLBACK) {
                                onDismissSheet()
                                cancel()
                            }
                        }
                }
                previous = it
            }
    }

    LaunchedEffect(sheetState) {
        var previous = SheetPositionValue.COLLAPSED
        snapshotFlow { sheetState.anchoredDraggableState.currentValue }
            .collect {
                if (previous == SheetPositionValue.COLLAPSED && it == SheetPositionValue.EXPANDED) {
                    // We are about to open, monitor close offset and invoke sheet open callback
                    // once fully open
                    snapshotFlow { sheetState.anchoredDraggableState.offset }
                        .collect { offset ->
                            if (offset <= 5f) {
                                onSheetOpen()
                                cancel()
                            }
                        }
                }
                previous = it
            }
    }

    PredictiveBackHandler { progress: Flow<BackEventCompat> ->
        try {
            // Gesture start
            progress.collect { backEvent ->
                val currentProgress = backEvent.progress
                scope.launch { sheetState.backProgress.snapTo(currentProgress) }
            }

            // Gesture completed, let's settle
            scope
                .launch {
                    sheetState.anchoredDraggableState.animateTo(SheetPositionValue.COLLAPSED)
                }
                .invokeOnCompletion { onDismissSheet() }
        } catch (e: CancellationException) {
            // Cancel gesture
            scope.launch { sheetState.settleProgress() }
        }
    }

    return@composed draggableModifier.graphicsLayer {
        val sheetOffset = sheetState.anchoredDraggableState.requireOffset()

        if (maxHeight != 0f) {
            scaleX = calculatePredictiveBackScaleX(sheetState.backProgress.value)
            scaleY = calculatePredictiveBackScaleY(sheetState.backProgress.value)
            transformOrigin =
                TransformOrigin(
                    pivotFractionX = 0.5f,
                    pivotFractionY = (sheetOffset + maxHeight) / maxHeight,
                )
        }
    }
}

/**
 * Modifier to be applied on the content of the bottom sheet that handles the scale of content
 * during gestures such as predictive back.
 */
fun Modifier.dismissableBottomSheetContent(sheetState: BottomSheetDismissState): Modifier {
    return this.graphicsLayer {
        val progress = sheetState.backProgress.value
        val predictiveBackScaleX = calculatePredictiveBackScaleX(progress)
        val predictiveBackScaleY = calculatePredictiveBackScaleY(progress)

        // Preserve the original aspect ratio and alignment of the child content.
        scaleY = calculateContentPredictiveBackScaleY(predictiveBackScaleX, predictiveBackScaleY)
        transformOrigin = PredictiveBackContentTransformOrigin
    }
}

private fun GraphicsLayerScope.calculatePredictiveBackScaleX(progress: Float): Float {
    val width = size.width

    return when {
        width.isInvalidSize() -> VISIBLE_PREDICTIVE_BACK_SCALE
        else ->
            VISIBLE_PREDICTIVE_BACK_SCALE -
                lerp(
                    start = NOT_VISIBLE_PREDICTIVE_BACK_SCALE,
                    stop = min(predictiveBackMaxScaleXDistance.toPx(), width),
                    fraction = progress,
                ) / width
    }
}

private fun GraphicsLayerScope.calculatePredictiveBackScaleY(progress: Float): Float {
    val height = size.height
    return when {
        height.isInvalidSize() -> VISIBLE_PREDICTIVE_BACK_SCALE
        else ->
            VISIBLE_PREDICTIVE_BACK_SCALE -
                lerp(
                    start = NOT_VISIBLE_PREDICTIVE_BACK_SCALE,
                    stop = min(predictiveBackMaxScaleYDistance.toPx(), height),
                    fraction = progress,
                ) / height
    }
}

private fun calculateContentPredictiveBackScaleY(sheetScaleX: Float, sheetScaleY: Float): Float =
    if (sheetScaleX != NOT_VISIBLE_PREDICTIVE_BACK_SCALE) {
        sheetScaleX / sheetScaleY
    } else {
        VISIBLE_PREDICTIVE_BACK_SCALE
    }

internal object BottomSheetDismissDimensions {
    val sheetPositionalThreshold = 56.dp

    val predictiveBackMaxScaleXDistance = 48.dp
    val predictiveBackMaxScaleYDistance = 24.dp
    val PredictiveBackContentTransformOrigin =
        TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0f)

    // Scale that essentially makes content disappear
    const val NOT_VISIBLE_PREDICTIVE_BACK_SCALE = 0f
    private const val DEFAULT_PREDICTIVE_BACK_SCALE = 1f
    const val VISIBLE_PREDICTIVE_BACK_SCALE = DEFAULT_PREDICTIVE_BACK_SCALE

    fun Float.isInvalidSize() = this.isNaN() || this == 0f

    /** Animation spec to use for settling the sheet after a gesture / fling. */
    val SETTLE_ANIMATION_SPEC: AnimationSpec<Float> =
        tween(durationMillis = 267, easing = LinearOutSlowInEasing)

    /** Offset closer to dismissal when we should invoke the dismiss callback. */
    const val OFFSET_DIFF_TO_TRIGGER_DISMISS_CALLBACK = 5
}
