/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.celllayout

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.areAnimatorsEnabled
import android.util.ArrayMap
import android.view.View
import com.android.app.animation.Interpolators.DECELERATE_1_5
import com.android.launcher3.CellLayout
import com.android.launcher3.CellLayout.REORDER_ANIMATION_DURATION
import com.android.launcher3.Reorderable
import com.android.launcher3.Workspace
import com.android.launcher3.util.MultiTranslateDelegate.INDEX_REORDER_BOUNCE_OFFSET
import com.android.launcher3.util.Thunk
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin

/**
 * Class which represents the reorder preview animations. These animations show that an item is in a
 * temporary state, and hint at where the item will return to.
 */
class ReorderPreviewAnimation<T>(
    val child: T,
    // If the mode is MODE_HINT it will only move one period and stop, it then will be going
    // backwards to the initial position, otherwise it will oscillate.
    val mode: Int,
    cellX0: Int,
    cellY0: Int,
    cellX1: Int,
    cellY1: Int,
    spanX: Int,
    spanY: Int,
    reorderMagnitude: Float,
    cellLayout: CellLayout,
    private val shakeAnimators: ArrayMap<Reorderable, ReorderPreviewAnimation<T>>
) : ValueAnimator.AnimatorUpdateListener where T : View, T : Reorderable {

    private var finalDeltaX = 0f
    private var finalDeltaY = 0f
    private var initDeltaX =
        child.getTranslateDelegate().getTranslationX(INDEX_REORDER_BOUNCE_OFFSET).value
    private var initDeltaY =
        child.getTranslateDelegate().getTranslationY(INDEX_REORDER_BOUNCE_OFFSET).value
    private var initScale = child.getReorderBounceScale()
    private val finalScale = CellLayout.DEFAULT_SCALE - CHILD_DIVIDEND / child.width * initScale

    private val dir = if (mode == MODE_HINT) -1 else 1
    var animator: ValueAnimator =
        ObjectAnimator.ofFloat(0f, 1f).also {
            it.addUpdateListener(this)
            it.setDuration((if (mode == MODE_HINT) HINT_DURATION else PREVIEW_DURATION).toLong())
            it.startDelay = (Math.random() * 60).toLong()
            // Animations are disabled in power save mode, causing the repeated animation to jump
            // spastically between beginning and end states. Since this looks bad, we don't repeat
            // the animation in power save mode.
            if (areAnimatorsEnabled() && mode == MODE_PREVIEW) {
                it.repeatCount = ValueAnimator.INFINITE
                it.repeatMode = ValueAnimator.REVERSE
            }
        }

    init {
        val tmpRes = intArrayOf(0, 0)
        cellLayout.regionToCenterPoint(cellX0, cellY0, spanX, spanY, tmpRes)
        val (x0, y0) = tmpRes
        cellLayout.regionToCenterPoint(cellX1, cellY1, spanX, spanY, tmpRes)
        val (x1, y1) = tmpRes
        val dX = x1 - x0
        val dY = y1 - y0

        if (dX != 0 || dY != 0) {
            if (dY == 0) {
                finalDeltaX = -dir * sign(dX.toFloat()) * reorderMagnitude
            } else if (dX == 0) {
                finalDeltaY = -dir * sign(dY.toFloat()) * reorderMagnitude
            } else {
                val angle = atan((dY.toFloat() / dX))
                finalDeltaX = (-dir * sign(dX.toFloat()) * abs(cos(angle) * reorderMagnitude))
                finalDeltaY = (-dir * sign(dY.toFloat()) * abs(sin(angle) * reorderMagnitude))
            }
        }
    }

    private fun setInitialAnimationValuesToBaseline() {
        initScale = CellLayout.DEFAULT_SCALE
        initDeltaX = 0f
        initDeltaY = 0f
    }

    fun animate() {
        val noMovement = finalDeltaX == 0f && finalDeltaY == 0f
        if (shakeAnimators.containsKey(child)) {
            val oldAnimation: ReorderPreviewAnimation<T>? = shakeAnimators.remove(child)
            if (noMovement) {
                // A previous animation for this item exists, and no new animation will exist.
                // Finish the old animation smoothly.
                oldAnimation!!.finishAnimation()
                return
            } else {
                // A previous animation for this item exists, and a new one will exist. Stop
                // the old animation in its tracks, and proceed with the new one.
                oldAnimation!!.cancel()
            }
        }
        if (noMovement) {
            return
        }
        shakeAnimators[child] = this
        animator.start()
    }

    override fun onAnimationUpdate(updatedAnimation: ValueAnimator) {
        val progress = updatedAnimation.animatedValue as Float
        child
            .getTranslateDelegate()
            .setTranslation(
                INDEX_REORDER_BOUNCE_OFFSET,
                /* x = */ progress * finalDeltaX + (1 - progress) * initDeltaX,
                /* y = */ progress * finalDeltaY + (1 - progress) * initDeltaY
            )
        child.setReorderBounceScale(progress * finalScale + (1 - progress) * initScale)
    }

    private fun cancel() {
        animator.cancel()
    }

    /** Smoothly returns the item to its baseline position / scale */
    @Thunk
    fun finishAnimation() {
        animator.cancel()
        setInitialAnimationValuesToBaseline()
        animator = ObjectAnimator.ofFloat((animator.animatedValue as Float), 0f)
        animator.addUpdateListener(this)
        animator.interpolator = DECELERATE_1_5
        animator.setDuration(REORDER_ANIMATION_DURATION.toLong())
        animator.start()
    }

    companion object {
        const val PREVIEW_DURATION = 300
        const val HINT_DURATION = Workspace.REORDER_TIMEOUT
        private const val CHILD_DIVIDEND = 4.0f
        const val MODE_HINT = 0
        const val MODE_PREVIEW = 1
    }
}
