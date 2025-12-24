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

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * A [NestedScrollConnection] that resolves gesture conflicts for bottom with the nested scrolling
 * content within.
 */
class BottomSheetNestedScrollConnection(
    private val sheetState: BottomSheetDismissState,
    private val flingBehavior: FlingBehavior,
    private val enabled: Boolean,
) : NestedScrollConnection {
    private var sheetConsumedPreScrollDelta = 0f
    private var childConsumedAnyScroll = false
    private var acceptFling = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!enabled) return Offset.Zero

        val offset =
            when {
                // If sheet was already moved beyond its resting state, take over the scroll
                isSheetMoving() -> Offset(x = 0f, y = dispatchRawDelta(available.y))
                // If not, let child evaluate the scroll first and then we will figure out it in
                // post scroll.
                else -> Offset.Zero
            }

        sheetConsumedPreScrollDelta = offset.y
        return offset
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (!enabled) return Offset.Zero

        childConsumedAnyScroll = sheetConsumedPreScrollDelta != consumed.y

        return when {
            // If child didn't accept scroll, assume we can scroll.
            !childConsumedAnyScroll -> Offset(x = 0f, y = dispatchRawDelta(available.y))
            else -> Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val availableVelocity = available.y

        // Accept fling if we were already scrolling OR if child wasn't scrolling.
        acceptFling =
            enabled &&
                (sheetConsumedPreScrollDelta != 0f ||
                    (availableVelocity != 0f && !childConsumedAnyScroll))

        return when {
            acceptFling -> {
                performFling(availableVelocity)
                Velocity(0f, availableVelocity)
            }

            else -> Velocity.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        resetScrollState()

        if (acceptFling) {
            acceptFling = false
            performFling(available.y)

            return Velocity(0f, available.y)
        }
        return Velocity.Zero
    }

    private fun resetScrollState() {
        childConsumedAnyScroll = false
        sheetConsumedPreScrollDelta = 0f
    }

    private suspend fun performFling(initialVelocity: Float) {
        sheetState.anchoredDraggableState.anchoredDrag {
            val scrollFlingScope =
                object : ScrollScope {
                    override fun scrollBy(pixels: Float): Float {
                        dragTo(newOffset = sheetState.anchoredDraggableState.offset + pixels)
                        return pixels
                    }
                }
            with(flingBehavior) { scrollFlingScope.performFling(initialVelocity) }
        }
    }

    private fun dispatchRawDelta(delta: Float) =
        sheetState.anchoredDraggableState.dispatchRawDelta(delta = delta)

    private fun isSheetMoving() = sheetState.anchoredDraggableState.requireOffset() != 0f
}
