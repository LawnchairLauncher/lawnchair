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

package com.android.quickstep.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.util.FloatProperty
import android.widget.ImageButton
import com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X
import com.android.launcher3.R
import com.android.launcher3.util.KFloatProperty
import com.android.launcher3.util.MultiPropertyDelegate
import com.android.launcher3.util.MultiPropertyFactory
import com.android.launcher3.util.MultiValueAlpha
import com.android.quickstep.util.BorderAnimator
import com.android.quickstep.util.BorderAnimator.Companion.createSimpleBorderAnimator

/**
 * Button for supporting multiple desktop sessions. The button will be next to the first TaskView
 * inside overview, while clicking this button will create a new desktop session.
 */
class AddDesktopButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ImageButton(context, attrs) {

    private val addDeskButtonAlpha = MultiValueAlpha(this, Alpha.entries.size)
    var contentAlpha by MultiPropertyDelegate(addDeskButtonAlpha, Alpha.CONTENT)
    var visibilityAlpha by MultiPropertyDelegate(addDeskButtonAlpha, Alpha.VISIBILITY)

    private val multiTranslationX =
        MultiPropertyFactory(this, VIEW_TRANSLATE_X, TranslationX.entries.size) { a: Float, b: Float
            ->
            a + b
        }
    var gridTranslationX by MultiPropertyDelegate(multiTranslationX, TranslationX.GRID)
    var offsetTranslationX by MultiPropertyDelegate(multiTranslationX, TranslationX.OFFSET)

    private val focusBorderAnimator: BorderAnimator =
        createSimpleBorderAnimator(
            context.resources.getDimensionPixelSize(R.dimen.add_desktop_button_size),
            context.resources.getDimensionPixelSize(R.dimen.keyboard_quick_switch_border_width),
            this::getBorderBounds,
            this,
            context
                .obtainStyledAttributes(attrs, R.styleable.AddDesktopButton)
                .getColor(
                    R.styleable.AddDesktopButton_focusBorderColor,
                    BorderAnimator.DEFAULT_BORDER_COLOR,
                ),
        )

    var borderEnabled = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            focusBorderAnimator.setBorderVisibility(visible = field && isFocused, animated = true)
        }

    public override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?,
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (borderEnabled) {
            focusBorderAnimator.setBorderVisibility(gainFocus, /* animated= */ true)
        }
    }

    private fun getBorderBounds(bounds: Rect) {
        bounds.set(0, 0, width, height)
        val outlinePadding =
            context.resources.getDimensionPixelSize(R.dimen.add_desktop_button_outline_padding)
        bounds.inset(-outlinePadding, -outlinePadding)
    }

    override fun draw(canvas: Canvas) {
        focusBorderAnimator.drawBorder(canvas)
        super.draw(canvas)
    }

    companion object {
        private enum class Alpha {
            CONTENT,
            VISIBILITY,
        }

        private enum class TranslationX {
            GRID,
            OFFSET,
        }

        @JvmField
        val VISIBILITY_ALPHA: FloatProperty<AddDesktopButton> =
            KFloatProperty(AddDesktopButton::visibilityAlpha)
    }
}
