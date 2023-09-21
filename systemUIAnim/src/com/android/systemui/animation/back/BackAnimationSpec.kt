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

import android.util.DisplayMetrics
import android.view.animation.Interpolator
import android.window.BackEvent
import com.android.systemui.animation.Interpolators
import com.android.systemui.util.dpToPx

/** Used to convert [BackEvent] into a [BackTransformation]. */
fun interface BackAnimationSpec {

    /** Computes transformation based on a [backEvent] and sets it to [result]. */
    fun getBackTransformation(
        backEvent: BackEvent,
        progressY: Float, // TODO(b/265060720): Remove progressY. Could be retrieved from backEvent
        result: BackTransformation,
    )

    companion object
}

/** Create a [BackAnimationSpec] from [displayMetrics] and design specs. */
fun BackAnimationSpec.Companion.createFloatingSurfaceAnimationSpec(
    displayMetrics: DisplayMetrics,
    maxMarginXdp: Float,
    maxMarginYdp: Float,
    minScale: Float,
    translateXEasing: Interpolator = Interpolators.STANDARD_DECELERATE,
    translateYEasing: Interpolator = Interpolators.LINEAR,
    scaleEasing: Interpolator = Interpolators.STANDARD_DECELERATE,
): BackAnimationSpec {
    val screenWidthPx = displayMetrics.widthPixels
    val screenHeightPx = displayMetrics.heightPixels

    val maxMarginXPx = maxMarginXdp.dpToPx(displayMetrics)
    val maxMarginYPx = maxMarginYdp.dpToPx(displayMetrics)
    val maxTranslationXByScale = (screenWidthPx - screenWidthPx * minScale) / 2
    val maxTranslationX = maxTranslationXByScale - maxMarginXPx
    val maxTranslationYByScale = (screenHeightPx - screenHeightPx * minScale) / 2
    val maxTranslationY = maxTranslationYByScale - maxMarginYPx
    val minScaleReversed = 1f - minScale

    return BackAnimationSpec { backEvent, progressY, result ->
        val direction = if (backEvent.swipeEdge == BackEvent.EDGE_LEFT) 1 else -1
        val progressX = backEvent.progress

        val ratioTranslateX = translateXEasing.getInterpolation(progressX)
        val ratioTranslateY = translateYEasing.getInterpolation(progressY)
        val ratioScale = scaleEasing.getInterpolation(progressX)

        result.apply {
            translateX = ratioTranslateX * direction * maxTranslationX
            translateY = ratioTranslateY * maxTranslationY
            scale = 1f - (ratioScale * minScaleReversed)
        }
    }
}
