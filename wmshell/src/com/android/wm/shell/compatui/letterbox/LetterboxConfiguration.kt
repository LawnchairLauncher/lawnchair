/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox

import android.annotation.ColorRes
import android.content.Context
import android.graphics.Color
import com.android.internal.R
import com.android.wm.shell.dagger.WMSingleton
import javax.inject.Inject

/**
 * Contains configuration properties for the letterbox implementation in Shell.
 */
@WMSingleton
class LetterboxConfiguration @Inject constructor(
    private val context: Context
) {
    // Color to use for the solid color letterbox background type.
    private var letterboxBackgroundColorOverride: Color? = null

    // Color resource id for the solid color letterbox background type.
    private var letterboxBackgroundColorResourceIdOverride: Int? = null

    // Default value for corners radius for activities presented in the letterbox mode.
    // Values < 0 will be ignored.
    private val letterboxActivityDefaultCornersRadius: Int

    // Current corners radius for activities presented in the letterbox mode.
    // Values can be modified at runtime and values < 0 will be ignored.
    private var letterboxActivityCornersRadius = 0

    init {
        letterboxActivityDefaultCornersRadius = context.resources.getInteger(
            R.integer.config_letterboxActivityCornersRadius
        )
        letterboxActivityCornersRadius = letterboxActivityDefaultCornersRadius
    }

    /**
     * Sets color of letterbox background which is used when using the solid background mode.
     */
    fun setLetterboxBackgroundColor(color: Color) {
        letterboxBackgroundColorOverride = color
    }

    /**
     * Sets color ID of letterbox background which is used when using the solid background mode.
     */
    fun setLetterboxBackgroundColorResourceId(@ColorRes colorId: Int) {
        letterboxBackgroundColorResourceIdOverride = colorId
    }

    /**
     * Gets color of letterbox background which is used when the solid color mode is active.
     */
    fun getLetterboxBackgroundColor(): Color {
        if (letterboxBackgroundColorOverride != null) {
            return letterboxBackgroundColorOverride!!
        }
        val colorId = if (letterboxBackgroundColorResourceIdOverride != null) {
            letterboxBackgroundColorResourceIdOverride
        } else {
            R.color.config_letterboxBackgroundColor
        }
        // Query color dynamically because material colors extracted from wallpaper are updated
        // when wallpaper is changed.
        return Color.valueOf(context.getResources().getColor(colorId!!, null))
    }

    /**
     * Resets color of letterbox background to the default.
     */
    fun resetLetterboxBackgroundColor() {
        letterboxBackgroundColorOverride = null
        letterboxBackgroundColorResourceIdOverride = null
    }

    /**
     * The background color for the Letterbox.
     */
    fun getBackgroundColorRgbArray(): FloatArray = getLetterboxBackgroundColor().components

    /**
     * Overrides corners radius for activities presented in the letterbox mode. Values < 0,
     * will be ignored and corners of the activity won't be rounded.
     */
    fun setLetterboxActivityCornersRadius(cornersRadius: Int) {
        letterboxActivityCornersRadius = cornersRadius
    }

    /**
     * Resets corners radius for activities presented in the letterbox mode.
     */
    fun resetLetterboxActivityCornersRadius() {
        letterboxActivityCornersRadius = letterboxActivityDefaultCornersRadius
    }

    /**
     * Whether corners of letterboxed activities are rounded.
     */
    fun isLetterboxActivityCornersRounded(): Boolean {
        return getLetterboxActivityCornersRadius() > 0
    }

    /**
     * Gets corners radius for activities presented in the letterbox mode.
     */
    fun getLetterboxActivityCornersRadius(): Int {
        return maxOf(letterboxActivityCornersRadius, 0)
    }
}
