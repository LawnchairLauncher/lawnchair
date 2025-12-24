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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.runtime.Stable
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.BottomSheetDismissDimensions.SETTLE_ANIMATION_SPEC

/**
 * A state holding information necessary to perform drag / predictive back gestures on sheet to
 * dismiss it.
 *
 * @see [dismissibleBottomSheet] modifier.
 */
@Stable
class BottomSheetDismissState(private val expandCollapseAnimationSpec: AnimationSpec<Float>) {
    val backProgress = Animatable(0f)
    val anchoredDraggableState = AnchoredDraggableState(initialValue = SheetPositionValue.COLLAPSED)

    fun updateAnchors(sheetHeightPx: Float) {
        anchoredDraggableState.updateAnchors(
            DraggableAnchors {
                SheetPositionValue.EXPANDED at 0f
                SheetPositionValue.COLLAPSED at sheetHeightPx
            }
        )
    }

    /** Animates the sheet to the fully expanded position. */
    suspend fun expand() {
        anchoredDraggableState.animateTo(
            targetValue = SheetPositionValue.EXPANDED,
            animationSpec = expandCollapseAnimationSpec,
        )
    }

    /** Animates the sheet to the closed position. */
    suspend fun collapse() {
        anchoredDraggableState.animateTo(
            targetValue = SheetPositionValue.COLLAPSED,
            animationSpec = expandCollapseAnimationSpec,
        )
    }

    /** Animates and settles the back progress to open position. */
    suspend fun settleProgress() {
        backProgress.animateTo(targetValue = 0f, animationSpec = SETTLE_ANIMATION_SPEC)
    }
}

/** Holds the anchor position of the sheet. */
enum class SheetPositionValue {
    EXPANDED,
    COLLAPSED,
}
