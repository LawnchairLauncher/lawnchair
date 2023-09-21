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

import android.view.View
import androidx.annotation.VisibleForTesting
import java.util.Random

/** Plays [TurbulenceNoiseView] in ease-in, main (no easing), and ease-out order. */
class TurbulenceNoiseController(private val turbulenceNoiseView: TurbulenceNoiseView) {

    companion object {
        /**
         * States of the turbulence noise animation.
         *
         * <p>The state is designed to be follow the order below: [AnimationState.EASE_IN],
         * [AnimationState.MAIN], [AnimationState.EASE_OUT].
         */
        enum class AnimationState {
            EASE_IN,
            MAIN,
            EASE_OUT,
            NOT_PLAYING
        }
    }

    private val random = Random()

    /** Current state of the animation. */
    @VisibleForTesting
    var state: AnimationState = AnimationState.NOT_PLAYING
        set(value) {
            field = value
            if (state == AnimationState.NOT_PLAYING) {
                turbulenceNoiseView.visibility = View.INVISIBLE
                turbulenceNoiseView.clearConfig()
            } else {
                turbulenceNoiseView.visibility = View.VISIBLE
            }
        }

    init {
        turbulenceNoiseView.visibility = View.INVISIBLE
    }

    /** Updates the color of the noise. */
    fun updateNoiseColor(color: Int) {
        if (state == AnimationState.NOT_PLAYING) {
            return
        }
        turbulenceNoiseView.updateColor(color)
    }

    /**
     * Plays [TurbulenceNoiseView] with the given config.
     *
     * <p>It plays ease-in, main, and ease-out animations in sequence.
     */
    fun play(config: TurbulenceNoiseAnimationConfig) {
        if (state != AnimationState.NOT_PLAYING) {
            return // Ignore if any of the animation is playing.
        }

        turbulenceNoiseView.applyConfig(config)
        playEaseInAnimation()
    }

    // TODO(b/237282226): Support force finish.
    /** Finishes the main animation, which triggers the ease-out animation. */
    fun finish() {
        if (state == AnimationState.MAIN) {
            turbulenceNoiseView.finish(nextAnimation = this::playEaseOutAnimation)
        }
    }

    private fun playEaseInAnimation() {
        if (state != AnimationState.NOT_PLAYING) {
            return
        }
        state = AnimationState.EASE_IN

        // Add offset to avoid repetitive noise.
        turbulenceNoiseView.playEaseIn(
            offsetX = random.nextFloat(),
            offsetY = random.nextFloat(),
            this::playMainAnimation
        )
    }

    private fun playMainAnimation() {
        if (state != AnimationState.EASE_IN) {
            return
        }
        state = AnimationState.MAIN

        turbulenceNoiseView.play(this::playEaseOutAnimation)
    }

    private fun playEaseOutAnimation() {
        if (state != AnimationState.MAIN) {
            return
        }
        state = AnimationState.EASE_OUT

        turbulenceNoiseView.playEaseOut(onAnimationEnd = { state = AnimationState.NOT_PLAYING })
    }
}
