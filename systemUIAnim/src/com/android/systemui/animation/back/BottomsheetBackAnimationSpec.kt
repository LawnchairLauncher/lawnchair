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

package com.android.systemui.animation.back

import android.util.DisplayMetrics
import android.view.animation.Interpolator
import com.android.app.animation.Interpolators
import com.android.systemui.util.dpToPx

private const val MAX_SCALE_DELTA_DP = 48

/** Create a [BackAnimationSpec] from [displayMetrics] and design specs. */
fun BackAnimationSpec.Companion.createBottomsheetAnimationSpec(
    displayMetricsProvider: () -> DisplayMetrics,
    scaleEasing: Interpolator = Interpolators.BACK_GESTURE,
): BackAnimationSpec {
    return BackAnimationSpec { backEvent, _, result ->
        val displayMetrics = displayMetricsProvider()
        val screenWidthPx = displayMetrics.widthPixels
        val minScale = 1 - MAX_SCALE_DELTA_DP.dpToPx(displayMetrics) / screenWidthPx
        val progressX = backEvent.progress
        val ratioScale = scaleEasing.getInterpolation(progressX)
        result.apply {
            scale = 1f - ratioScale * (1f - minScale)
            scalePivotPosition = ScalePivotPosition.BOTTOM_CENTER
        }
    }
}
