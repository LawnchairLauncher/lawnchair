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
package com.android.wm.shell.windowdecor.common

import android.annotation.ColorInt
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape

/**
 * Represents drawable insets, specifying the number of pixels to inset a drawable from its bounds.
 */
data class DrawableInsets(val l: Int, val t: Int, val r: Int, val b: Int) {
    constructor(insets: Int) : this(insets, insets, insets, insets)

    constructor(
        vertical: Int = 0,
        horizontal: Int = 0,
    ) : this(horizontal, vertical, horizontal, vertical)

    constructor(
        vertical: Int = 0,
        horizontalLeft: Int = 0,
        horizontalRight: Int = 0,
    ) : this(horizontalLeft, vertical, horizontalRight, vertical)
}

/** Replaces the alpha component of a color with the given alpha value. */
@ColorInt
fun replaceColorAlpha(@ColorInt color: Int, alpha: Int): Int {
    return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}

/** Creates a background drawable with specified color, corner radius, and insets. */
fun createBackgroundDrawable(
    @ColorInt color: Int,
    cornerRadius: Int,
    drawableInsets: DrawableInsets,
): Drawable =
    createBackgroundDrawable(color, FloatArray(8) { cornerRadius.toFloat() }, drawableInsets)

/** Creates a background drawable with specified color, corner radius, and insets. */
fun createBackgroundDrawable(
    @ColorInt color: Int,
    cornerRadius: FloatArray,
    drawableInsets: DrawableInsets,
): Drawable =
    LayerDrawable(
            arrayOf(
                ShapeDrawable().apply {
                    shape = RoundRectShape(cornerRadius, /* inset= */ null, /* innerRadii= */ null)
                    setTintList(
                        ColorStateList(
                            arrayOf(
                                intArrayOf(android.R.attr.state_hovered),
                                intArrayOf(android.R.attr.state_pressed),
                            ),
                            intArrayOf(
                                replaceColorAlpha(color, OPACITY_11),
                                replaceColorAlpha(color, OPACITY_15),
                            ),
                        )
                    )
                }
            )
        )
        .apply {
            require(numberOfLayers == 1) { "Must only contain one layer" }
            setLayerInset(
                /* index= */ 0,
                drawableInsets.l,
                drawableInsets.t,
                drawableInsets.r,
                drawableInsets.b,
            )
        }
