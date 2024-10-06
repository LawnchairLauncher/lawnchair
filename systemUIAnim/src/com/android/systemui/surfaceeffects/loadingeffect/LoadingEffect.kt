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

package com.android.systemui.surfaceeffects.loadingeffect

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Paint
import android.graphics.RenderEffect
import android.view.View
import com.android.systemui.surfaceeffects.PaintDrawCallback
import com.android.systemui.surfaceeffects.RenderEffectDrawCallback
import com.android.systemui.surfaceeffects.turbulencenoise.TurbulenceNoiseAnimationConfig
import com.android.systemui.surfaceeffects.turbulencenoise.TurbulenceNoiseShader

/**
 * Plays loading effect with the given configuration.
 *
 * @param baseType immutable base shader type. This is used for constructing the shader. Reconstruct
 *   the [LoadingEffect] if the base type needs to be changed.
 * @param config immutable parameters that are used for drawing the effect.
 * @param paintCallback triggered every frame when animation is playing. Use this to draw the effect
 *   with [Canvas.drawPaint].
 * @param renderEffectCallback triggered every frame when animation is playing. Use this to draw the
 *   effect with [RenderEffect].
 * @param animationStateChangedCallback triggered when the [AnimationState] changes. Optional.
 *
 * The client is responsible to actually draw the [Paint] or [RenderEffect] returned in the
 * callback. Note that [View.invalidate] must be called on each callback. There are a few ways to
 * render the effect:
 * 1) Use [Canvas.drawPaint]. (Preferred. Significantly cheaper!)
 * 2) Set [RenderEffect] to the [View]. (Good for chaining effects.)
 * 3) Use [RenderNode.setRenderEffect]. (This may be least preferred, as 2 should do what you want.)
 *
 * <p>First approach is more performant than other ones because [RenderEffect] forces an
 * intermediate render pass of the View to a texture to feed into it.
 *
 * <p>If going with the first approach, your custom [View] would look like as follow:
 * <pre>{@code
 *     private var paint: Paint? = null
 *     // Override [View.onDraw].
 *     override fun onDraw(canvas: Canvas) {
 *         // RuntimeShader requires hardwareAcceleration.
 *         if (!canvas.isHardwareAccelerated) return
 *
 *         paint?.let { canvas.drawPaint(it) }
 *     }
 *
 *     // This is called [Callback.onDraw]
 *     fun draw(paint: Paint) {
 *         this.paint = paint
 *
 *         // Must call invalidate to trigger View#onDraw
 *         invalidate()
 *     }
 * }</pre>
 *
 * <p>If going with the second approach, it doesn't require an extra custom [View], and it is as
 * simple as calling [View.setRenderEffect] followed by [View.invalidate]. You can also chain the
 * effect with other [RenderEffect].
 *
 * <p>Third approach is an option, but it's more of a boilerplate so you would like to stick with
 * the second option. If you want to go with this option for some reason, below is the example:
 * <pre>{@code
 *     // Initialize the shader and paint to use to pass into the [Canvas].
 *     private val renderNode = RenderNode("LoadingEffect")
 *
 *     // Override [View.onDraw].
 *     override fun onDraw(canvas: Canvas) {
 *         // RuntimeShader requires hardwareAcceleration.
 *         if (!canvas.isHardwareAccelerated) return
 *
 *         if (renderNode.hasDisplayList()) {
 *             canvas.drawRenderNode(renderNode)
 *         }
 *     }
 *
 *     // This is called [Callback.onDraw]
 *     fun draw(renderEffect: RenderEffect) {
 *         renderNode.setPosition(0, 0, width, height)
 *         renderNode.setRenderEffect(renderEffect)
 *
 *         val recordingCanvas = renderNode.beginRecording()
 *         // We need at least 1 drawing instruction.
 *         recordingCanvas.drawColor(Color.TRANSPARENT)
 *         renderNode.endRecording()
 *
 *         // Must call invalidate to trigger View#onDraw
 *         invalidate()
 *     }
 * }</pre>
 */
class LoadingEffect
private constructor(
    baseType: TurbulenceNoiseShader.Companion.Type,
    private val config: TurbulenceNoiseAnimationConfig,
    private val paintCallback: PaintDrawCallback?,
    private val renderEffectCallback: RenderEffectDrawCallback?,
    private val animationStateChangedCallback: AnimationStateChangedCallback? = null
) {
    constructor(
        baseType: TurbulenceNoiseShader.Companion.Type,
        config: TurbulenceNoiseAnimationConfig,
        paintCallback: PaintDrawCallback,
        animationStateChangedCallback: AnimationStateChangedCallback? = null
    ) : this(
        baseType,
        config,
        paintCallback,
        renderEffectCallback = null,
        animationStateChangedCallback
    )
    constructor(
        baseType: TurbulenceNoiseShader.Companion.Type,
        config: TurbulenceNoiseAnimationConfig,
        renderEffectCallback: RenderEffectDrawCallback,
        animationStateChangedCallback: AnimationStateChangedCallback? = null
    ) : this(
        baseType,
        config,
        paintCallback = null,
        renderEffectCallback,
        animationStateChangedCallback
    )

    private val turbulenceNoiseShader: TurbulenceNoiseShader =
        TurbulenceNoiseShader(baseType).apply { applyConfig(config) }
    private var currentAnimator: ValueAnimator? = null
    private var state: AnimationState = AnimationState.NOT_PLAYING
        set(value) {
            if (field != value) {
                animationStateChangedCallback?.onStateChanged(field, value)
                field = value
            }
        }

    // We create a paint instance only if the client renders it with Paint.
    private val paint =
        if (paintCallback != null) {
            Paint().apply { this.shader = turbulenceNoiseShader }
        } else {
            null
        }

    /** Plays LoadingEffect. */
    fun play() {
        if (state != AnimationState.NOT_PLAYING) {
            return // Ignore if any of the animation is playing.
        }

        playEaseIn()
    }

    // TODO(b/237282226): Support force finish.
    /** Finishes the main animation, which triggers the ease-out animation. */
    fun finish() {
        if (state == AnimationState.MAIN) {
            // Calling Animator#end sets the animation state back to the initial state. Using pause
            // to avoid visual artifacts.
            currentAnimator?.pause()
            currentAnimator = null

            playEaseOut()
        }
    }

    /** Updates the noise color dynamically. */
    fun updateColor(newColor: Int) {
        turbulenceNoiseShader.setColor(newColor)
    }

    /** Updates the noise color that's screen blended on top. */
    fun updateScreenColor(newColor: Int) {
        turbulenceNoiseShader.setScreenColor(newColor)
    }

    /**
     * Retrieves the noise offset x, y, z values. This is useful for replaying the animation
     * smoothly from the last animation, by passing in the last values to the next animation.
     */
    fun getNoiseOffset(): Array<Float> {
        return arrayOf(
            turbulenceNoiseShader.noiseOffsetX,
            turbulenceNoiseShader.noiseOffsetY,
            turbulenceNoiseShader.noiseOffsetZ
        )
    }

    private fun playEaseIn() {
        if (state != AnimationState.NOT_PLAYING) {
            return
        }
        state = AnimationState.EASE_IN

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
                initialX + timeInSec * config.noiseMoveSpeedX,
                initialY + timeInSec * config.noiseMoveSpeedY,
                initialZ + timeInSec * config.noiseMoveSpeedZ
            )

            // TODO: Replace it with a better curve.
            turbulenceNoiseShader.setOpacity(progress * config.luminosityMultiplier)

            draw()
        }

        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentAnimator = null
                    playMain()
                }
            }
        )

        animator.start()
        this.currentAnimator = animator
    }

    private fun playMain() {
        if (state != AnimationState.EASE_IN) {
            return
        }
        state = AnimationState.MAIN

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = config.maxDuration.toLong()

        // Animation should start from the initial position to avoid abrupt transition.
        val initialX = turbulenceNoiseShader.noiseOffsetX
        val initialY = turbulenceNoiseShader.noiseOffsetY
        val initialZ = turbulenceNoiseShader.noiseOffsetZ

        turbulenceNoiseShader.setOpacity(config.luminosityMultiplier)

        animator.addUpdateListener { updateListener ->
            val timeInSec = updateListener.currentPlayTime * MS_TO_SEC
            turbulenceNoiseShader.setNoiseMove(
                initialX + timeInSec * config.noiseMoveSpeedX,
                initialY + timeInSec * config.noiseMoveSpeedY,
                initialZ + timeInSec * config.noiseMoveSpeedZ
            )

            draw()
        }

        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentAnimator = null
                    playEaseOut()
                }
            }
        )

        animator.start()
        this.currentAnimator = animator
    }

    private fun playEaseOut() {
        if (state != AnimationState.MAIN) {
            return
        }
        state = AnimationState.EASE_OUT

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

            draw()
        }

        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentAnimator = null
                    state = AnimationState.NOT_PLAYING
                }
            }
        )

        animator.start()
        this.currentAnimator = animator
    }

    private fun draw() {
        paintCallback?.onDraw(paint!!)
        renderEffectCallback?.onDraw(
            RenderEffect.createRuntimeShaderEffect(
                turbulenceNoiseShader,
                TurbulenceNoiseShader.BACKGROUND_UNIFORM
            )
        )
    }

    /**
     * States of the loading effect animation.
     *
     * <p>The state is designed to be follow the order below: [AnimationState.EASE_IN],
     * [AnimationState.MAIN], [AnimationState.EASE_OUT]. Note that ease in and out don't necessarily
     * mean the acceleration and deceleration in the animation curve. They simply mean each stage of
     * the animation. (i.e. Intro, core, and rest)
     */
    enum class AnimationState {
        EASE_IN,
        MAIN,
        EASE_OUT,
        NOT_PLAYING
    }

    /** Optional callback that is triggered when the animation state changes. */
    interface AnimationStateChangedCallback {
        /**
         * A callback that's triggered when the [AnimationState] changes. Example usage is
         * performing a cleanup when [AnimationState] becomes [NOT_PLAYING].
         */
        fun onStateChanged(oldState: AnimationState, newState: AnimationState) {}
    }

    private companion object {
        private const val MS_TO_SEC = 0.001f
    }
}
