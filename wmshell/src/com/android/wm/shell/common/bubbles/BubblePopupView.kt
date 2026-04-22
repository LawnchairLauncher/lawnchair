/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.wm.shell.common.bubbles

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.LinearLayout

/** A popup container view that uses [BubblePopupDrawable] as a background */
open class BubblePopupView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {
    var popupDrawable: BubblePopupDrawable? = null
        private set

    /**
     * Sets up the popup drawable with the config provided. Required to remove dependency on local
     * resources
     */
    fun setupBackground(config: BubblePopupDrawable.Config) {
        popupDrawable = BubblePopupDrawable(config)
        background = popupDrawable
        forceLayout()
    }

    /**
     * Sets the arrow direction for the background drawable and updates the padding to fit the
     * content inside of the popup drawable
     */
    fun setArrowDirection(direction: BubblePopupDrawable.ArrowDirection) {
        popupDrawable?.let {
            it.arrowDirection = direction
            val padding = Rect()
            if (it.getPadding(padding)) {
                setPadding(padding.left, padding.top, padding.right, padding.bottom)
            }
        }
    }

    /** Sets the arrow position for the background drawable and triggers redraw */
    fun setArrowPosition(position: BubblePopupDrawable.ArrowPosition) {
        popupDrawable?.let {
            it.arrowPosition = position
            invalidate()
        }
    }
}
