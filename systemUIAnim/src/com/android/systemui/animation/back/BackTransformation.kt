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

package com.android.systemui.animation.back

import android.view.View

/**
 * This object that represents the transformation to apply to the target. The properties of this
 * object are mutable for performance reasons (avoid recreating this object)
 */
data class BackTransformation(
    var translateX: Float = Float.NaN,
    var translateY: Float = Float.NaN,
    var scale: Float = Float.NaN,
)

/** Apply the transformation to the [targetView] */
fun BackTransformation.applyTo(targetView: View) {
    if (translateX.isFinite()) targetView.translationX = translateX
    if (translateY.isFinite()) targetView.translationY = translateY
    if (scale.isFinite()) {
        targetView.scaleX = scale
        targetView.scaleY = scale
    }
}
