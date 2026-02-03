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

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView

/** A custom [TextView] that allows better control over marquee animation used to ellipsize text. */
class MarqueedTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : TextView(context, attrs, defStyleAttr) {

    /**
     * Starts marquee animation if the layout attributes for this object include
     * `android:ellipsize=marquee`, `android:singleLine=true`, and
     * `android:scrollHorizontally=true`.
     */
    override public fun startMarquee() {
        super.startMarquee()
    }

    /**
     * Must always return [true] since [TextView.startMarquee()] requires view to be selected or
     * focused in order to start the marquee animation.
     *
     * We are not using [TextView.setSelected()] as this would dispatch undesired accessibility
     * events.
     */
    override fun isSelected() : Boolean {
        return true
    }
}
