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

package com.android.wm.shell.shared.animation

import android.animation.PointFEvaluator
import android.animation.ValueAnimator
import android.graphics.PointF
import android.graphics.Rect
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Choreographer
import android.view.SurfaceControl
import android.view.animation.Interpolator
import android.window.TransitionInfo

/** Creates animations that can be applied to windows/surfaces. */
object WindowAnimator {

    /** Parameters defining a window bounds animation. */
    data class BoundsAnimationParams(
        val durationMs: Long,
        val startOffsetYDp: Float = 0f,
        val endOffsetYDp: Float = 0f,
        val startScale: Float = 1f,
        val endScale: Float = 1f,
        val interpolator: Interpolator,
    )

    /**
     * Creates an animator to reposition and scale the bounds of the leash of the given change.
     *
     * @param displayMetrics the metrics of the display where the animation plays in
     * @param boundsAnimDef the parameters for the animation itself (duration, scale, position)
     * @param change the change to which the animation should be applied
     * @param transaction the transaction to apply the animation to
     */
    fun createBoundsAnimator(
        displayMetrics: DisplayMetrics,
        boundsAnimDef: BoundsAnimationParams,
        change: TransitionInfo.Change,
        transaction: SurfaceControl.Transaction,
    ): ValueAnimator {
        val startPos =
            getPosition(
                displayMetrics,
                change.endAbsBounds,
                boundsAnimDef.startScale,
                boundsAnimDef.startOffsetYDp,
            )
        val leash = change.leash
        val endPos =
            getPosition(
                displayMetrics,
                change.endAbsBounds,
                boundsAnimDef.endScale,
                boundsAnimDef.endOffsetYDp,
            )
        return ValueAnimator.ofObject(PointFEvaluator(), startPos, endPos).apply {
            duration = boundsAnimDef.durationMs
            interpolator = boundsAnimDef.interpolator
            addUpdateListener { animation ->
                val animPos = animation.animatedValue as PointF
                val animScale =
                    interpolate(
                        boundsAnimDef.startScale,
                        boundsAnimDef.endScale,
                        animation.animatedFraction
                    )
                transaction
                    .setPosition(leash, animPos.x, animPos.y)
                    .setScale(leash, animScale, animScale)
                    .setFrameTimeline(Choreographer.getInstance().vsyncId)
                    .apply()
            }
        }
    }

    private fun interpolate(startVal: Float, endVal: Float, fraction: Float): Float {
        require(fraction in 0.0f..1.0f)
        return startVal + (endVal - startVal) * fraction
    }

    private fun getPosition(
        displayMetrics: DisplayMetrics,
        bounds: Rect,
        scale: Float,
        offsetYDp: Float
    ) = PointF(bounds.left.toFloat(), bounds.top.toFloat()).apply {
            check(scale in 0.0f..1.0f)
            // Scale the bounds down with an anchor in the center
            offset(
                (bounds.width().toFloat() * (1 - scale) / 2),
                (bounds.height().toFloat() * (1 - scale) / 2),
            )
            val offsetYPx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        offsetYDp,
                        displayMetrics,
                    )
                    .toInt()
            offset(/* dx= */ 0f, offsetYPx.toFloat())
        }
}
