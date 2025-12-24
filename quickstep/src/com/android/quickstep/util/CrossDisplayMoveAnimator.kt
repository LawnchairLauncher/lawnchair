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
package com.android.quickstep.util

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.graphics.Rect
import android.os.RemoteException
import android.util.Log
import android.view.SurfaceControl
import android.window.IRemoteTransitionFinishedCallback
import androidx.core.animation.doOnEnd
import com.android.app.animation.Interpolators
import com.android.launcher3.Utilities.scaleRectAboutCenter
import kotlin.math.roundToInt

/**
 * Helper class to create and run the cross-display move animation.
 *
 * Defines the actual effect used on the task moving between displays. Any other effects are
 * expected to be put in mExtraAnimators, which is played in parallel.
 *
 * @property srcTaskLeash The leash for the moving task screenshot on the source display.
 * @property dstTaskLeash The leash for the moving task on the destination display.
 * @property srcFinalBounds The final bounds of the moving task on the source display.
 * @property dstFinalBounds The final bounds of the moving task on the destination display.
 * @property launchingTransitionDurationMs The duration of the launching animation.
 * @property unlaunchingTransitionDurationMs The duration of the unlaunching animation.
 * @property extraAnimators Any extra animators to play in parallel.
 * @property finishCallback The callback to invoke when the full transition is finished.
 */
class CrossDisplayMoveAnimator(
    private val srcTaskLeash: SurfaceControl?,
    private val dstTaskLeash: SurfaceControl,
    private val srcFinalBounds: Rect,
    private val dstFinalBounds: Rect,
    private val launchingTransitionDurationMs: Long,
    private val unlaunchingTransitionDurationMs: Long,
    private val extraAnimators: AnimatorSet?,
    private val finishCallback: IRemoteTransitionFinishedCallback
) {
    companion object {
        private const val TAG = "CrossDisplayMoveAnimator"
        // Scale from 90% --> 100% (and vice versa)
        private const val ANIMATION_SCALE_START = 0.9f
        private const val ANIMATION_SCALE_END = 1.0f
        private val ANIMATION_SCALE_RANGE = ANIMATION_SCALE_END - ANIMATION_SCALE_START

        /** Applies the unlaunch state (scale-down and fade-out) to a leash. */
        @JvmStatic
        fun applyUnlaunchState(
            t: SurfaceControl.Transaction,
            leash: SurfaceControl,
            finalBounds: Rect,
            progress: Float
        ) {
            val scale = ANIMATION_SCALE_END - ANIMATION_SCALE_RANGE * progress
            val scaledRect = Rect(finalBounds)
            scaleRectAboutCenter(scaledRect, scale)
            t.setAlpha(leash, 1f - progress)
            t.setPosition(leash, scaledRect.left.toFloat(), scaledRect.top.toFloat())
            t.setScale(leash, scale, scale)
        }

        /** Applies the launch state (scale-up and fade-in) to a leash. */
        @JvmStatic
        fun applyLaunchState(
            t: SurfaceControl.Transaction,
            leash: SurfaceControl,
            finalBounds: Rect,
            progress: Float
        ) {
            val scale = ANIMATION_SCALE_START + ANIMATION_SCALE_RANGE * progress
            val scaledRect = Rect(finalBounds)
            scaleRectAboutCenter(scaledRect, scale)
            t.setAlpha(leash, progress)
            t.setPosition(leash, scaledRect.left.toFloat(), scaledRect.top.toFloat())
            t.setScale(leash, scale, scale)
        }
    }

    private fun createUnlaunchAnimation(
        leash: SurfaceControl,
        finalBounds: Rect
    ): Animator {
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = unlaunchingTransitionDurationMs
            interpolator = Interpolators.EMPHASIZED_DECELERATE
            val t = SurfaceControl.Transaction()
            addUpdateListener {
                applyUnlaunchState(t, leash, finalBounds, it.animatedValue as Float)
                t.apply()
            }
            doOnEnd {
                val t = SurfaceControl.Transaction()
                t.setAlpha(leash, 0f)
                t.setVisibility(leash, false)
                t.reparent(leash, null)
                t.apply()
            }
        }
    }

    private fun createLaunchAnimation(leash: SurfaceControl, finalBounds: Rect): Animator {
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = launchingTransitionDurationMs
            interpolator = Interpolators.EMPHASIZED
            val t = SurfaceControl.Transaction()
            addUpdateListener {
                applyLaunchState(t, leash, finalBounds, it.animatedValue as Float)
                t.apply()
            }
            doOnEnd {
                val t = SurfaceControl.Transaction()
                t.setAlpha(leash, 1f)
                t.setScale(leash, 1f, 1f)
                t.setPosition(
                    leash,
                    finalBounds.left.toFloat(),
                    finalBounds.top.toFloat()
                )
                t.apply()
            }
        }
    }

    fun start() {
        val animators = mutableListOf<Animator>()
        srcTaskLeash?.let { animators.add(createUnlaunchAnimation(it, srcFinalBounds)) }
        animators.add(createLaunchAnimation(dstTaskLeash, dstFinalBounds))
        extraAnimators?.let { animators.add(it) }

        val animSet = AnimatorSet()
        animSet.playTogether(animators)
        animSet.doOnEnd {
            try {
                finishCallback.onTransitionFinished(null /* wct */, null /* sct */)
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to finish transition", e)
            }
        }
        animSet.start()
    }
}