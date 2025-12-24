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
package com.android.wm.shell.windowdecor

import android.annotation.ColorInt
import android.annotation.IdRes
import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.withStyledAttributes
import com.android.wm.shell.R

/**
 * Button-like component used to display the "Additional options" elements of the Handle menu window
 * decoration.
 *
 * The possible options for which this button is used for are "Screenshot", "New Window", "Manage
 * Windows" and "Change Aspect Ratio".
 */
class HandleMenuActionButton
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    LinearLayout(context, attrs, defStyleAttr) {

    val iconView: ImageView
    val textView: MarqueedTextView

    init {
        LayoutInflater.from(context)
            .inflate(R.layout.desktop_mode_window_decor_handle_menu_action_button, this, true)
        iconView = findViewById(R.id.image)
        textView = findViewById(R.id.label)

        context.withStyledAttributes(attrs, R.styleable.HandleMenuActionButton) {
            contentDescription = getString(R.styleable.HandleMenuActionButton_android_text)
            textView.text = getString(R.styleable.HandleMenuActionButton_android_text)
            textView.setTextColor(getColor(R.styleable.HandleMenuActionButton_android_textColor, 0))
            iconView.setImageResource(
                getResourceId(R.styleable.HandleMenuActionButton_android_src, 0)
            )
            iconView.imageTintList =
                getColorStateList(R.styleable.HandleMenuActionButton_android_drawableTint)
        }
    }

    /**
     * Sets the text color for the text inside the button.
     *
     * @param color the color to set for the text, as a color integer.
     */
    fun setTextColor(@ColorInt color: Int) {
        textView.setTextColor(color)
    }

    /**
     * Sets the icon for the button using a resource ID.
     *
     * @param resourceId the resource ID of the drawable to set as the icon.
     */
    fun setIconResource(@IdRes resourceId: Int) {
        iconView.setImageResource(resourceId)
    }

    /**
     * Sets the text to display inside the button.
     *
     * @param text the text to display.
     */
    fun setText(text: CharSequence?) {
        textView.text = text
    }

    /**
     * Sets the tint color for the icon.
     *
     * @param color the color to use for the tint, as a color integer.
     */
    fun setDrawableTint(@ColorInt color: Int) {
        iconView.imageTintList = ColorStateList.valueOf(color)
    }

    /**
     * Gets or sets the tint applied to the icon.
     *
     * @return The [ColorStateList] representing the tint, or null if no tint is applied.
     */
    var compoundDrawableTintList: ColorStateList?
        get() = iconView.imageTintList
        set(value) {
            iconView.imageTintList = value
        }
}
