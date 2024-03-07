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

package com.android.launcher3

import android.graphics.Rect

/**
 * Fit [this] into [targetRect] with letter boxing. After calling this method, [this] will be
 * modified to be letter boxed.
 *
 * @param targetRect target [Rect] that [this] should be fitted into
 */
fun Rect.letterBox(targetRect: Rect) {
    letterBox(targetRect, this)
}

/**
 * Fit [this] into [targetRect] with letter boxing. After calling this method, [resultRect] will be
 * modified to be letter boxed.
 *
 * @param targetRect target [Rect] that [this] should be fitted into
 * @param resultRect the letter boxed [Rect]
 */
fun Rect.letterBox(targetRect: Rect, resultRect: Rect) {
    val widthRatio: Float = 1f * targetRect.width() / width()
    val heightRatio: Float = 1f * targetRect.height() / height()
    if (widthRatio < heightRatio) {
        val scaledHeight: Int = (widthRatio * height()).toInt()
        val verticalPadding: Int = (targetRect.height() - scaledHeight) / 2
        resultRect.set(
            targetRect.left,
            targetRect.top + verticalPadding,
            targetRect.right,
            targetRect.bottom - verticalPadding
        )
    } else {
        val scaledWidth: Int = (heightRatio * width()).toInt()
        val horizontalPadding: Int = (targetRect.width() - scaledWidth) / 2
        resultRect.set(
            targetRect.left + horizontalPadding,
            targetRect.top,
            targetRect.right - horizontalPadding,
            targetRect.bottom
        )
    }
}
