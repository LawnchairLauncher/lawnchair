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

package com.android.launcher3.pageindicators

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.android.launcher3.R

/**
 * Handles logic for the pagination arrow. The foreground and background images and the pressed /
 * hovered state UX.
 */
@SuppressLint("AppCompatCustomView")
class PaginationArrow(context: Context, attrs: AttributeSet) : ImageView(context, attrs) {
    private val bgCircle = ContextCompat.getDrawable(context, R.drawable.ic_circle)

    init {
        foreground = ContextCompat.getDrawable(context, R.drawable.ic_chevron_left_rounded_700)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                background = bgCircle
                background.alpha = BACKGROUND_PRESSED_OPACITY
            }
            MotionEvent.ACTION_UP -> background = null
        }
        return super.onTouchEvent(event)
    }

    override fun onHoverEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_HOVER_ENTER -> {
                background = bgCircle
                background.alpha = BACKGROUND_HOVERED_OPACITY
            }
            MotionEvent.ACTION_HOVER_EXIT -> background = null
        }
        return super.onHoverEvent(event)
    }

    companion object {
        const val FULLY_OPAQUE = 1f
        const val DISABLED_ARROW_OPACITY = .38f

        // alpha ints are 0 - 255; the former being transparent and the latter being fully opaque
        private const val BACKGROUND_HOVERED_OPACITY = 28 // 11%
        private const val BACKGROUND_PRESSED_OPACITY = 38 // 15%
    }
}
