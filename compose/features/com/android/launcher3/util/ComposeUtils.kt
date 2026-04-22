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

import android.animation.TimeInterpolator
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.drawable.toBitmap

/**
 * Converts a [TimeInterpolator] to a compose [Easing].
 *
 * This function allows you to use Android's [TimeInterpolator] instances, such as those defined in
 * `android.R.interpolator`, directly with Compose animations that expect an [Easing].
 */
fun TimeInterpolator.toComposeEasing(): Easing = Easing { fraction -> getInterpolation(fraction) }

/**
 * A composable function that takes a [Drawable] and converts it into a [Painter] which can be used
 * to draw the drawable on a Compose canvas.
 *
 * This function uses [remember] to cache the conversion of the [Drawable] to an [ImageBitmap]. This
 * means that if the same [Drawable] instance is provided across recompositions, the conversion will
 * only happen once, improving performance.
 */
@Composable
fun painterResource(drawable: Drawable): Painter {
    val imageBitmap = remember(drawable) { drawable.toBitmap().asImageBitmap() }
    return BitmapPainter(imageBitmap)
}


/**
 * A multi-preview annotation that displays the same Composable with different font scales.
 */
@Preview(name = "normal font", group = "font scales", fontScale = 1f)
@Preview(name = "large font", group = "font scales", fontScale = 2f)
annotation class FontScalePreviews
