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

package com.android.wm.shell.windowdecor

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.widget.ImageButton

/**
 * [ImageButton] for the handle at the top of fullscreen apps. Has custom hover
 * and press handling to grow the handle on hover enter and shrink the handle on
 * hover exit and press.
 */
class HandleImageButton (context: Context?, attrs: AttributeSet?) :
    ImageButton(context, attrs) {
    private val handleAnimator = ValueAnimator()

    override fun onHoverChanged(hovered: Boolean) {
        super.onHoverChanged(hovered)
        if (hovered) {
            animateHandle(HANDLE_HOVER_ANIM_DURATION, HANDLE_HOVER_ENTER_SCALE)
        } else {
            if (!isPressed) {
                animateHandle(HANDLE_HOVER_ANIM_DURATION, HANDLE_DEFAULT_SCALE)
            }
        }
    }

    override fun setPressed(pressed: Boolean) {
        if (isPressed != pressed) {
            super.setPressed(pressed)
            if (pressed) {
                animateHandle(HANDLE_PRESS_ANIM_DURATION, HANDLE_PRESS_DOWN_SCALE)
            } else {
                animateHandle(HANDLE_PRESS_ANIM_DURATION, HANDLE_DEFAULT_SCALE)
            }
        }
    }

    private fun animateHandle(duration: Long, endScale: Float) {
        if (handleAnimator.isRunning) {
            handleAnimator.cancel()
        }
        handleAnimator.duration = duration
        handleAnimator.setFloatValues(scaleX, endScale)
        handleAnimator.addUpdateListener { animator ->
            scaleX = animator.animatedValue as Float
        }
        handleAnimator.start()
    }

    companion object {
        /** The duration of animations related to hover state. **/
        private const val HANDLE_HOVER_ANIM_DURATION = 300L
        /** The duration of animations related to pressed state. **/
        private const val HANDLE_PRESS_ANIM_DURATION = 200L
        /** Ending scale for hover enter. **/
        private const val HANDLE_HOVER_ENTER_SCALE = 1.2f
        /** Ending scale for press down. **/
        private const val HANDLE_PRESS_DOWN_SCALE = 0.85f
        /** Default scale for handle. **/
        private const val HANDLE_DEFAULT_SCALE = 1f
    }
}
