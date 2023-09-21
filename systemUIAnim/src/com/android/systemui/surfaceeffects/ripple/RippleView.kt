/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.surfaceeffects.ripple

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.android.systemui.surfaceeffects.ripple.RippleShader.RippleShape

/**
 * A generic expanding ripple effect.
 *
 * Set up the shader with a desired [RippleShape] using [setupShader], [setMaxSize] and [setCenter],
 * then call [startRipple] to trigger the ripple expansion.
 */
open class RippleView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    protected lateinit var rippleShader: RippleShader
    lateinit var rippleShape: RippleShape
        private set

    private val ripplePaint = Paint()
    protected val animator: ValueAnimator = ValueAnimator.ofFloat(0f, 1f)

    var duration: Long = 1750

    fun setMaxSize(maxWidth: Float, maxHeight: Float) {
        rippleShader.rippleSize.setMaxSize(maxWidth, maxHeight)
    }

    private var centerX: Float = 0.0f
    private var centerY: Float = 0.0f
    fun setCenter(x: Float, y: Float) {
        this.centerX = x
        this.centerY = y
        rippleShader.setCenter(x, y)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        rippleShader.pixelDensity = resources.displayMetrics.density
        super.onConfigurationChanged(newConfig)
    }

    override fun onAttachedToWindow() {
        rippleShader.pixelDensity = resources.displayMetrics.density
        super.onAttachedToWindow()
    }

    /** Initializes the shader. Must be called before [startRipple]. */
    fun setupShader(rippleShape: RippleShape = RippleShape.CIRCLE) {
        this.rippleShape = rippleShape
        rippleShader = RippleShader(rippleShape)

        rippleShader.color = RippleAnimationConfig.RIPPLE_DEFAULT_COLOR
        rippleShader.rawProgress = 0f
        rippleShader.sparkleStrength = RippleAnimationConfig.RIPPLE_SPARKLE_STRENGTH
        rippleShader.pixelDensity = resources.displayMetrics.density

        ripplePaint.shader = rippleShader
    }

    /**
     * Sets the fade parameters for the base ring.
     *
     * <p>Base ring indicates a blurred ring below the sparkle ring. See
     * [RippleShader.baseRingFadeParams].
     */
    @JvmOverloads
    fun setBaseRingFadeParams(
        fadeInStart: Float = rippleShader.baseRingFadeParams.fadeInStart,
        fadeInEnd: Float = rippleShader.baseRingFadeParams.fadeInEnd,
        fadeOutStart: Float = rippleShader.baseRingFadeParams.fadeOutStart,
        fadeOutEnd: Float = rippleShader.baseRingFadeParams.fadeOutEnd
    ) {
        setFadeParams(
            rippleShader.baseRingFadeParams,
            fadeInStart,
            fadeInEnd,
            fadeOutStart,
            fadeOutEnd
        )
    }

    /**
     * Sets the fade parameters for the sparkle ring.
     *
     * <p>Sparkle ring refers to the ring that's drawn on top of the base ring. See
     * [RippleShader.sparkleRingFadeParams].
     */
    @JvmOverloads
    fun setSparkleRingFadeParams(
        fadeInStart: Float = rippleShader.sparkleRingFadeParams.fadeInStart,
        fadeInEnd: Float = rippleShader.sparkleRingFadeParams.fadeInEnd,
        fadeOutStart: Float = rippleShader.sparkleRingFadeParams.fadeOutStart,
        fadeOutEnd: Float = rippleShader.sparkleRingFadeParams.fadeOutEnd
    ) {
        setFadeParams(
            rippleShader.sparkleRingFadeParams,
            fadeInStart,
            fadeInEnd,
            fadeOutStart,
            fadeOutEnd
        )
    }

    /**
     * Sets the fade parameters for the center fill.
     *
     * <p>One common use case is set all the params to 1, which completely removes the center fill.
     * See [RippleShader.centerFillFadeParams].
     */
    @JvmOverloads
    fun setCenterFillFadeParams(
        fadeInStart: Float = rippleShader.centerFillFadeParams.fadeInStart,
        fadeInEnd: Float = rippleShader.centerFillFadeParams.fadeInEnd,
        fadeOutStart: Float = rippleShader.centerFillFadeParams.fadeOutStart,
        fadeOutEnd: Float = rippleShader.centerFillFadeParams.fadeOutEnd
    ) {
        setFadeParams(
            rippleShader.centerFillFadeParams,
            fadeInStart,
            fadeInEnd,
            fadeOutStart,
            fadeOutEnd
        )
    }

    private fun setFadeParams(
        fadeParams: RippleShader.FadeParams,
        fadeInStart: Float,
        fadeInEnd: Float,
        fadeOutStart: Float,
        fadeOutEnd: Float
    ) {
        with(fadeParams) {
            this.fadeInStart = fadeInStart
            this.fadeInEnd = fadeInEnd
            this.fadeOutStart = fadeOutStart
            this.fadeOutEnd = fadeOutEnd
        }
    }

    /**
     * Sets blur multiplier at start and end of the progress.
     *
     * <p>It interpolates between [start] and [end]. No need to set it if using default blur.
     */
    fun setBlur(start: Float, end: Float) {
        rippleShader.blurStart = start
        rippleShader.blurEnd = end
    }

    /**
     * Sets the list of [RippleShader.SizeAtProgress].
     *
     * <p>Note that this clears the list before it sets with the new data.
     */
    fun setSizeAtProgresses(vararg targetSizes: RippleShader.SizeAtProgress) {
        rippleShader.rippleSize.setSizeAtProgresses(*targetSizes)
    }

    @JvmOverloads
    fun startRipple(onAnimationEnd: Runnable? = null) {
        if (animator.isRunning) {
            return // Ignore if ripple effect is already playing
        }
        animator.duration = duration
        animator.addUpdateListener { updateListener ->
            val now = updateListener.currentPlayTime
            val progress = updateListener.animatedValue as Float
            rippleShader.rawProgress = progress
            rippleShader.distortionStrength = 1 - progress
            rippleShader.time = now.toFloat()
            invalidate()
        }
        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    onAnimationEnd?.run()
                }
            }
        )
        animator.start()
    }

    /**
     * Set the color to be used for the ripple.
     *
     * The alpha value of the color will be applied to the ripple. The alpha range is [0-255].
     */
    fun setColor(color: Int, alpha: Int = RippleAnimationConfig.RIPPLE_DEFAULT_ALPHA) {
        rippleShader.color = ColorUtils.setAlphaComponent(color, alpha)
    }

    /** Set the intensity of the sparkles. */
    fun setSparkleStrength(strength: Float) {
        rippleShader.sparkleStrength = strength
    }

    /** Indicates whether the ripple animation is playing. */
    fun rippleInProgress(): Boolean = animator.isRunning

    override fun onDraw(canvas: Canvas?) {
        if (canvas == null || !canvas.isHardwareAccelerated) {
            // Drawing with the ripple shader requires hardware acceleration, so skip
            // if it's unsupported.
            return
        }
        // To reduce overdraw, we mask the effect to a circle or a rectangle that's bigger than the
        // active effect area. Values here should be kept in sync with the animation implementation
        // in the ripple shader.
        if (rippleShape == RippleShape.CIRCLE) {
            val maskRadius = rippleShader.rippleSize.currentWidth
            canvas.drawCircle(centerX, centerY, maskRadius, ripplePaint)
        } else if (rippleShape == RippleShape.ELLIPSE) {
            val maskWidth = rippleShader.rippleSize.currentWidth * 2
            val maskHeight = rippleShader.rippleSize.currentHeight * 2
            canvas.drawRect(
                /* left= */ centerX - maskWidth,
                /* top= */ centerY - maskHeight,
                /* right= */ centerX + maskWidth,
                /* bottom= */ centerY + maskHeight,
                ripplePaint
            )
        } else { // RippleShape.RoundedBox
            // No masking for the rounded box, as it has more blur which requires larger bounds.
            // Masking creates sharp bounds even when the masking is 4 times bigger.
            canvas.drawPaint(ripplePaint)
        }
    }
}
