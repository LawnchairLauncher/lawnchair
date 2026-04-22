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
import android.annotation.DimenRes
import android.content.res.Resources;
import android.content.Context
import android.util.AttributeSet
import android.widget.ImageButton
import com.android.wm.shell.R

/**
 * [ImageButton] for the handle at the top of fullscreen apps. Has custom hover
 * and press handling to grow the handle on hover enter and shrink the handle on
 * hover exit and press.
 */
class HandleImageButton (context: Context?, attrs: AttributeSet?) :
    ImageButton(context, attrs) {
    private val handleAnimator = ValueAnimator()

    /** Final horizontal padding for hover enter. **/
    private val HANDLE_HOVER_ENTER_PADDING = loadDimensionPixelSize(
        R.dimen.desktop_mode_fullscreen_decor_caption_horizontal_padding_hovered)
    /** Final horizontal padding for press down. **/
    private val HANDLE_PRESS_DOWN_PADDING = loadDimensionPixelSize(
        R.dimen.desktop_mode_fullscreen_decor_caption_horizontal_padding_touched)
    /** Default horizontal padding. **/
    private val HANDLE_DEFAULT_PADDING = loadDimensionPixelSize(
        R.dimen.desktop_mode_fullscreen_decor_caption_horizontal_padding_default)

    override fun onHoverChanged(hovered: Boolean) {
        super.onHoverChanged(hovered)
        if (hovered) {
            animateHandle(HANDLE_HOVER_ANIM_DURATION, HANDLE_HOVER_ENTER_PADDING)
        } else {
            if (!isPressed) {
                animateHandle(HANDLE_HOVER_ANIM_DURATION, HANDLE_DEFAULT_PADDING)
            }
        }
    }

    override fun setPressed(pressed: Boolean) {
        if (isPressed != pressed) {
            super.setPressed(pressed)
            if (pressed) {
                animateHandle(HANDLE_PRESS_ANIM_DURATION, HANDLE_PRESS_DOWN_PADDING)
            } else {
                animateHandle(HANDLE_PRESS_ANIM_DURATION, HANDLE_DEFAULT_PADDING)
            }
        }
    }

    private fun animateHandle(duration: Long, endPadding: Int) {
        if (handleAnimator.isRunning) {
            handleAnimator.cancel()
        }
        handleAnimator.duration = duration
        handleAnimator.setIntValues(paddingLeft, endPadding)
        handleAnimator.addUpdateListener { animator ->
            val padding = animator.animatedValue as Int
            setPadding(padding, paddingTop, padding, paddingBottom)
        }
        handleAnimator.start()
    }

    private fun loadDimensionPixelSize(@DimenRes resourceId: Int): Int {
        if (resourceId == Resources.ID_NULL) {
            return 0
        }
        return context.resources.getDimensionPixelSize(resourceId)
    }

    companion object {
        /** The duration of animations related to hover state. **/
        private const val HANDLE_HOVER_ANIM_DURATION = 300L
        /** The duration of animations related to pressed state. **/
        private const val HANDLE_PRESS_ANIM_DURATION = 200L
    }
}
