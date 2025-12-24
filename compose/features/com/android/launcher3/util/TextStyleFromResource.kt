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

package com.android.launcher3.util

import androidx.annotation.StyleRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.android.launcher3.R

/**
 * Creates a Jetpack Compose [TextStyle] from an Android [R.style] resource ID.
 *
 * Extracts basic text-related attributes from the provided style resource and maps them to the
 * corresponding [TextStyle] properties.
 *
 * @param styleResId The resource ID of the Android style (e.g., `R.style.MyTextStyle`).
 * @return A [TextStyle] object populated with attributes from the R.style.
 */
@Composable
fun textStyleFromResource(@StyleRes styleResId: Int): TextStyle {
    val context = LocalContext.current
    val density = LocalDensity.current
    val theme = context.theme

    var textSize: TextUnit = TextUnit.Unspecified
    var fontFamily: FontFamily? = null
    var fontWeight: FontWeight? = null
    var lineHeight: TextUnit = TextUnit.Unspecified

    // -- Separate groups of similar typed attributes. --

    theme.obtainStyledAttributes(styleResId, intArrayOf(android.R.attr.fontFamily))
        .use { typedArray ->
            if (typedArray.hasValue(0)) {
                typedArray.getString(0)?.let { fontName ->
                    fontFamily = FontFamily(
                        Font(DeviceFontFamilyName(fontName), weight = FontWeight.Medium),
                        Font(DeviceFontFamilyName(fontName), weight = FontWeight.Normal),
                    )
                }
            }
        }

    theme.obtainStyledAttributes(
        styleResId,
        intArrayOf(android.R.attr.textSize, android.R.attr.lineHeight)
    )
        .use { typedArray ->
            if (typedArray.hasValue(0)) {
                val textSizePx = typedArray.getDimensionPixelSize(0, 0)
                if (textSizePx != 0) {
                    textSize = with(density) { textSizePx.toSp() }
                }
            }
            if (typedArray.hasValue(1)) {
                val lineHeightPx = typedArray.getDimensionPixelSize(1, 0)
                if (lineHeightPx != 0) {
                    lineHeight = with(density) { lineHeightPx.toSp() }
                }
            }
        }

    theme.obtainStyledAttributes(
        styleResId,
        intArrayOf(android.R.attr.fontWeight)
    ).use { typedArray ->
        if (typedArray.hasValue(0)) {
            val fontWeightInt = typedArray.getInt(0, -1)
            if (fontWeightInt != -1) {
                fontWeight = FontWeight(fontWeightInt)
            }
        }
    }

    return TextStyle(
        fontSize = textSize,
        lineHeight = lineHeight,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
    )
}
