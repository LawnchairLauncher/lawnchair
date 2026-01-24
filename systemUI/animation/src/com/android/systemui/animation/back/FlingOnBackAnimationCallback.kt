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

package com.android.systemui.animation.back

import android.os.Build
import android.util.TimeUtils
import android.view.Choreographer
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_MOVE
import android.view.VelocityTracker
import android.view.animation.Interpolator
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import com.android.app.animation.Interpolators
import com.android.internal.dynamicanimation.animation.DynamicAnimation
import com.android.internal.dynamicanimation.animation.FlingAnimation
import com.android.internal.dynamicanimation.animation.FloatValueHolder
import com.android.window.flags2.Flags.predictiveBackTimestampApi

private const val FLING_FRICTION = 6f
private const val SCALE_FACTOR = 100f

/**
 * Enhanced [OnBackAnimationCallback] with automatic fling animation and interpolated progress.
 *
 * Simplifies back gesture handling by animating flings and emitting processed events through
 * `compat` functions. Customize progress interpolation with an optional [Interpolator].
 *
 * @param progressInterpolator [Interpolator] for progress, defaults to
 *   [Interpolators.BACK_GESTURE].
 */
abstract class FlingOnBackAnimationCallback(
    val progressInterpolator: Interpolator = Interpolators.BACK_GESTURE
) : OnBackAnimationCallback {

    private val velocityTracker = VelocityTracker.obtain()
    private var lastBackEvent: BackEvent? = null
    private var downTime: Long? = null

    private var backInvokedFlingAnim: FlingAnimation? = null
    private val backInvokedFlingUpdateListener =
        DynamicAnimation.OnAnimationUpdateListener { _, progress: Float, _ ->
            lastBackEvent?.let {
                val backEvent =
                    BackEvent(
                        it.touchX,
                        it.touchY,
                        progress / SCALE_FACTOR,
                        it.swipeEdge,
                        it.frameTimeMillis,
                    )
                onBackProgressedCompat(backEvent)
            }
        }
    private val backInvokedFlingEndListener =
        DynamicAnimation.OnAnimationEndListener { _, _, _, _ ->
            onBackInvokedCompat()
            reset()
        }

    abstract fun onBackStartedCompat(backEvent: BackEvent)

    abstract fun onBackProgressedCompat(backEvent: BackEvent)

    abstract fun onBackInvokedCompat()

    abstract fun onBackCancelledCompat()

    final override fun onBackStarted(backEvent: BackEvent) {
        if (backInvokedFlingAnim != null) {
            // This should never happen but let's call onBackInvokedCompat() just in case there is
            // still a fling animation in progress
            onBackInvokedCompat()
        }
        reset()
        if (predictiveBackTimestampApi() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            downTime = backEvent.frameTimeMillis
        }
        onBackStartedCompat(backEvent)
    }

    final override fun onBackProgressed(backEvent: BackEvent) {
        val interpolatedProgress = progressInterpolator.getInterpolation(backEvent.progress)
        if (predictiveBackTimestampApi() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            downTime?.let { downTime ->
                velocityTracker.addMovement(
                    MotionEvent.obtain(
                        /* downTime */ downTime,
                        /* eventTime */ backEvent.frameTimeMillis,
                        /* action */ ACTION_MOVE,
                        /* x */ interpolatedProgress * SCALE_FACTOR,
                        /* y */ 0f,
                        /* metaState */ 0,
                    )
                )
            }
            lastBackEvent =
                BackEvent(
                    backEvent.touchX,
                    backEvent.touchY,
                    interpolatedProgress,
                    backEvent.swipeEdge,
                    backEvent.frameTimeMillis,
                )
        } else {
            lastBackEvent =
                BackEvent(
                    backEvent.touchX,
                    backEvent.touchY,
                    interpolatedProgress,
                    backEvent.swipeEdge,
                )
        }
        lastBackEvent?.let { onBackProgressedCompat(it) }
    }

    final override fun onBackInvoked() {
        if (predictiveBackTimestampApi() && lastBackEvent != null) {
            velocityTracker.computeCurrentVelocity(1000)
            backInvokedFlingAnim =
                FlingAnimation(FloatValueHolder())
                    .setStartValue((lastBackEvent?.progress ?: 0f) * SCALE_FACTOR)
                    .setFriction(FLING_FRICTION)
                    .setStartVelocity(velocityTracker.xVelocity)
                    .setMinValue(0f)
                    .setMaxValue(SCALE_FACTOR)
                    .also {
                        it.addUpdateListener(backInvokedFlingUpdateListener)
                        it.addEndListener(backInvokedFlingEndListener)
                        it.start()
                        // do an animation-frame immediately to prevent idle frame
                        it.doAnimationFrame(
                            Choreographer.getInstance().lastFrameTimeNanos / TimeUtils.NANOS_PER_MS
                        )
                    }
        } else {
            onBackInvokedCompat()
            reset()
        }
    }

    final override fun onBackCancelled() {
        onBackCancelledCompat()
        reset()
    }

    private fun reset() {
        velocityTracker.clear()
        backInvokedFlingAnim?.removeEndListener(backInvokedFlingEndListener)
        backInvokedFlingAnim?.removeUpdateListener(backInvokedFlingUpdateListener)
        lastBackEvent = null
        backInvokedFlingAnim = null
        downTime = null
    }
}
