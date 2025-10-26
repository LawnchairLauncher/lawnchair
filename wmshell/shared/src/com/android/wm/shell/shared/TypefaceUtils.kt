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

package com.android.wm.shell.shared

import android.graphics.Typeface
import android.widget.TextView
import com.android.wm.shell.Flags

/**
 * Utility class to apply a specified typeface to a [TextView].
 *
 * This class provides a method, [setTypeface],
 * to easily set a pre-defined font family and style to a given [TextView].
 */
class TypefaceUtils {

    enum class FontFamily(val value: String) {
        GSF_DISPLAY_LARGE("variable-display-large"),
        GSF_DISPLAY_MEDIUM("variable-display-medium"),
        GSF_DISPLAY_SMALL("variable-display-small"),
        GSF_HEADLINE_LARGE("variable-headline-large"),
        GSF_HEADLINE_MEDIUM("variable-headline-medium"),
        GSF_HEADLINE_SMALL("variable-headline-small"),
        GSF_TITLE_LARGE("variable-title-large"),
        GSF_TITLE_MEDIUM("variable-title-medium"),
        GSF_TITLE_SMALL("variable-title-small"),
        GSF_LABEL_LARGE("variable-label-large"),
        GSF_LABEL_MEDIUM("variable-label-medium"),
        GSF_LABEL_SMALL("variable-label-small"),
        GSF_BODY_LARGE("variable-body-large"),
        GSF_BODY_MEDIUM("variable-body-medium"),
        GSF_BODY_SMALL("variable-body-small"),
        GSF_DISPLAY_LARGE_EMPHASIZED("variable-display-large-emphasized"),
        GSF_DISPLAY_MEDIUM_EMPHASIZED("variable-display-medium-emphasized"),
        GSF_DISPLAY_SMALL_EMPHASIZED("variable-display-small-emphasized"),
        GSF_HEADLINE_LARGE_EMPHASIZED("variable-headline-large-emphasized"),
        GSF_HEADLINE_MEDIUM_EMPHASIZED("variable-headline-medium-emphasized"),
        GSF_HEADLINE_SMALL_EMPHASIZED("variable-headline-small-emphasized"),
        GSF_TITLE_LARGE_EMPHASIZED("variable-title-large-emphasized"),
        GSF_TITLE_MEDIUM_EMPHASIZED("variable-title-medium-emphasized"),
        GSF_TITLE_SMALL_EMPHASIZED("variable-title-small-emphasized"),
        GSF_LABEL_LARGE_EMPHASIZED("variable-label-large-emphasized"),
        GSF_LABEL_MEDIUM_EMPHASIZED("variable-label-medium-emphasized"),
        GSF_LABEL_SMALL_EMPHASIZED("variable-label-small-emphasized"),
        GSF_BODY_LARGE_EMPHASIZED("variable-body-large-emphasized"),
        GSF_BODY_MEDIUM_EMPHASIZED("variable-body-medium-emphasized"),
        GSF_BODY_SMALL_EMPHASIZED("variable-body-small-emphasized"),
    }

    companion object {
        /**
         * Sets the typeface of the provided [textView] to the specified [fontFamily] and [fontStyle].
         *
         * The typeface is only applied to the [TextView] when [Flags.enableGsf] is `true`.
         * If [Flags.enableGsf] is `false`, this method has no effect.
         *
         * @param textView The [TextView] to which the typeface should be applied. If `null`, this method does nothing.
         * @param fontFamily The desired [FontFamily] for the [TextView].
         * @param fontStyle The desired font style (e.g., [Typeface.NORMAL], [Typeface.BOLD], [Typeface.ITALIC]). Defaults to [Typeface.NORMAL].
         */
        @JvmStatic
        @JvmOverloads
        fun setTypeface(
            textView: TextView?,
            fontFamily: FontFamily,
            fontStyle: Int = Typeface.NORMAL,
        ) {
            if (!Flags.enableGsf()) return
            textView?.typeface = Typeface.create(fontFamily.value, fontStyle)
        }
    }
}
