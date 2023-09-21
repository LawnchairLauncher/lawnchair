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

package com.android.systemui.surfaceeffects.ripple

import androidx.annotation.VisibleForTesting

/** Controller that handles playing [RippleAnimation]. */
class MultiRippleController(private val multipleRippleView: MultiRippleView) {

    private val ripplesFinishedListeners = ArrayList<RipplesFinishedListener>()

    companion object {
        /** Max number of ripple animations at a time. */
        @VisibleForTesting const val MAX_RIPPLE_NUMBER = 10

        interface RipplesFinishedListener {
            /** Triggered when all the ripples finish running. */
            fun onRipplesFinish()
        }
    }

    fun addRipplesFinishedListener(listener: RipplesFinishedListener) {
        ripplesFinishedListeners.add(listener)
    }

    /** Updates all the ripple colors during the animation. */
    fun updateColor(color: Int) {
        multipleRippleView.ripples.forEach { anim -> anim.updateColor(color) }
    }

    fun play(rippleAnimation: RippleAnimation) {
        if (multipleRippleView.ripples.size >= MAX_RIPPLE_NUMBER) {
            return
        }

        multipleRippleView.ripples.add(rippleAnimation)

        rippleAnimation.play {
            // Remove ripple once the animation is done
            multipleRippleView.ripples.remove(rippleAnimation)
            if (multipleRippleView.ripples.isEmpty()) {
                ripplesFinishedListeners.forEach { listener -> listener.onRipplesFinish() }
            }
        }

        // Trigger drawing
        multipleRippleView.invalidate()
    }
}
