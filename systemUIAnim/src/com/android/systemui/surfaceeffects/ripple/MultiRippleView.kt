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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.annotation.VisibleForTesting

/**
 * A view that allows multiple ripples to play.
 *
 * Use [MultiRippleController] to play ripple animations.
 */
class MultiRippleView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    val ripples = ArrayList<RippleAnimation>()
    private val ripplePaint = Paint()
    private var isWarningLogged = false

    companion object {
        private const val TAG = "MultiRippleView"
    }

    override fun onDraw(canvas: Canvas?) {
        if (canvas == null || !canvas.isHardwareAccelerated) {
            // Drawing with the ripple shader requires hardware acceleration, so skip
            // if it's unsupported.
            if (!isWarningLogged) {
                // Only log once to not spam.
                Log.w(
                    TAG,
                    "Can't draw ripple shader. $canvas does not support hardware acceleration."
                )
                isWarningLogged = true
            }
            return
        }

        var shouldInvalidate = false

        ripples.forEach { anim ->
            ripplePaint.shader = anim.rippleShader
            canvas.drawPaint(ripplePaint)

            shouldInvalidate = shouldInvalidate || anim.isPlaying()
        }

        if (shouldInvalidate) {
            invalidate()
        }
    }
}
