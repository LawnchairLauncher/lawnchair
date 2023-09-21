/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.systemui.surfaceeffects.turbulencenoise

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.ColorUtils

/**
 * View that renders turbulence noise effect.
 *
 * <p>Use [TurbulenceNoiseController] to control the turbulence animation. If you want to make some
 * other turbulence noise effects, either add functionality to [TurbulenceNoiseController] or create
 * another controller instead of extend or modify the [TurbulenceNoiseView].
 *
 * <p>Please keep the [TurbulenceNoiseView] (or View in general) not aware of the state.
 *
 * <p>Please avoid inheriting the View if possible. Instead, reconsider adding a controller for a
 * new case.
 */
class TurbulenceNoiseView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    companion object {
        private const val MS_TO_SEC = 0.001f
    }

    private val turbulenceNoiseShader = TurbulenceNoiseShader()
    private val paint = Paint().apply { this.shader = turbulenceNoiseShader }
    @VisibleForTesting var noiseConfig: TurbulenceNoiseAnimationConfig? = null
    @VisibleForTesting var currentAnimator: ValueAnimator? = null

    override fun onDraw(canvas: Canvas?) {
        if (canvas == null || !canvas.isHardwareAccelerated) {
            // Drawing with the turbulence noise shader requires hardware acceleration, so skip
            // if it's unsupported.
            return
        }

        canvas.drawPaint(paint)
    }

    /** Updates the color during the animation. No-op if there's no animation playing. */
    internal fun updateColor(color: Int) {
        noiseConfig?.let {
            turbulenceNoiseShader.setColor(ColorUtils.setAlphaComponent(color, it.opacity))
        }
    }

    /** Plays the turbulence noise with no easing. */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    fun play(onAnimationEnd: Runnable? = null) {
        if (noiseConfig == null) {
            return
        }
        val config = noiseConfig!!

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = config.maxDuration.toLong()

        // Animation should start from the initial position to avoid abrupt transition.
        val initialX = turbulenceNoiseShader.noiseOffsetX
        val initialY = turbulenceNoiseShader.noiseOffsetY
        val initialZ = turbulenceNoiseShader.noiseOffsetZ

        animator.addUpdateListener { updateListener ->
            val timeInSec = updateListener.currentPlayTime * MS_TO_SEC
            turbulenceNoiseShader.setNoiseMove(
                initialX + timeInSec * config.noiseMoveSpeedX,
                initialY + timeInSec * config.noiseMoveSpeedY,
                initialZ + timeInSec * config.noiseMoveSpeedZ
            )

            turbulenceNoiseShader.setOpacity(config.luminosityMultiplier)

            invalidate()
        }

        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentAnimator = null
                    onAnimationEnd?.run()
                }
            }
        )

        animator.start()
        currentAnimator = animator
    }

    /** Plays the turbulence noise with linear ease-in. */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    fun playEaseIn(offsetX: Float = 0f, offsetY: Float = 0f, onAnimationEnd: Runnable? = null) {
        if (noiseConfig == null) {
            return
        }
        val config = noiseConfig!!

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = config.easeInDuration.toLong()

        // Animation should start from the initial position to avoid abrupt transition.
        val initialX = turbulenceNoiseShader.noiseOffsetX
        val initialY = turbulenceNoiseShader.noiseOffsetY
        val initialZ = turbulenceNoiseShader.noiseOffsetZ

        animator.addUpdateListener { updateListener ->
            val timeInSec = updateListener.currentPlayTime * MS_TO_SEC
            val progress = updateListener.animatedValue as Float

            turbulenceNoiseShader.setNoiseMove(
                offsetX + initialX + timeInSec * config.noiseMoveSpeedX,
                offsetY + initialY + timeInSec * config.noiseMoveSpeedY,
                initialZ + timeInSec * config.noiseMoveSpeedZ
            )

            // TODO: Replace it with a better curve.
            turbulenceNoiseShader.setOpacity(progress * config.luminosityMultiplier)

            invalidate()
        }

        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentAnimator = null
                    onAnimationEnd?.run()
                }
            }
        )

        animator.start()
        currentAnimator = animator
    }

    /** Plays the turbulence noise with linear ease-out. */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    fun playEaseOut(onAnimationEnd: Runnable? = null) {
        if (noiseConfig == null) {
            return
        }
        val config = noiseConfig!!

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = config.easeOutDuration.toLong()

        // Animation should start from the initial position to avoid abrupt transition.
        val initialX = turbulenceNoiseShader.noiseOffsetX
        val initialY = turbulenceNoiseShader.noiseOffsetY
        val initialZ = turbulenceNoiseShader.noiseOffsetZ

        animator.addUpdateListener { updateListener ->
            val timeInSec = updateListener.currentPlayTime * MS_TO_SEC
            val progress = updateListener.animatedValue as Float

            turbulenceNoiseShader.setNoiseMove(
                initialX + timeInSec * config.noiseMoveSpeedX,
                initialY + timeInSec * config.noiseMoveSpeedY,
                initialZ + timeInSec * config.noiseMoveSpeedZ
            )

            // TODO: Replace it with a better curve.
            turbulenceNoiseShader.setOpacity((1f - progress) * config.luminosityMultiplier)

            invalidate()
        }

        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentAnimator = null
                    onAnimationEnd?.run()
                }
            }
        )

        animator.start()
        currentAnimator = animator
    }

    /** Finishes the current animation if playing and plays the next animation if given. */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    fun finish(nextAnimation: Runnable? = null) {
        // Calling Animator#end sets the animation state back to the initial state. Using pause to
        // avoid visual artifacts.
        currentAnimator?.pause()
        currentAnimator = null

        nextAnimation?.run()
    }

    /** Applies shader uniforms. Must be called before playing animation. */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    fun applyConfig(config: TurbulenceNoiseAnimationConfig) {
        noiseConfig = config
        with(turbulenceNoiseShader) {
            setGridCount(config.gridCount)
            setColor(ColorUtils.setAlphaComponent(config.color, config.opacity))
            setBackgroundColor(config.backgroundColor)
            setSize(config.width, config.height)
            setPixelDensity(config.pixelDensity)
        }
        paint.blendMode = config.blendMode
    }

    internal fun clearConfig() {
        noiseConfig = null
    }
}
