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

package com.android.quickstep.util

import com.android.app.animation.Interpolators

/** Timings for the app pair launch animation. */
abstract class AppPairLaunchTimings : SplitAnimationTimings {
    protected abstract val STAGED_RECT_SLIDE_DURATION: Int

    // Common timings that apply to app pair launches on any type of device
    override fun getStagedRectSlideStart() = 0
    override fun getStagedRectSlideEnd() = stagedRectSlideStart + STAGED_RECT_SLIDE_DURATION
    override fun getPlaceholderFadeInStart() = 0
    override fun getPlaceholderFadeInEnd() = 0
    override fun getPlaceholderIconFadeInStart() = 0
    override fun getPlaceholderIconFadeInEnd() = 0

    private val iconFadeStart: Int
        get() = getStagedRectSlideEnd()
    private val iconFadeEnd: Int
        get() = iconFadeStart + 83
    private val appRevealStart: Int
        get() = getStagedRectSlideEnd() + 67
    private val appRevealEnd: Int
        get() = appRevealStart + 217
    private val cellSplitStart: Int
        get() = (getStagedRectSlideEnd() * 0.83f).toInt()
    private val cellSplitEnd: Int
        get() = cellSplitStart + 500

    override fun getStagedRectXInterpolator() = Interpolators.EMPHASIZED_COMPLEMENT
    override fun getStagedRectYInterpolator() = Interpolators.EMPHASIZED
    override fun getStagedRectScaleXInterpolator() = Interpolators.EMPHASIZED
    override fun getStagedRectScaleYInterpolator() = Interpolators.EMPHASIZED
    override fun getCellSplitInterpolator() = Interpolators.EMPHASIZED
    override fun getIconFadeInterpolator() = Interpolators.LINEAR

    override fun getCellSplitStartOffset(): Float {
        return cellSplitStart.toFloat() / getDuration()
    }
    override fun getCellSplitEndOffset(): Float {
        return cellSplitEnd.toFloat() / getDuration()
    }
    override fun getIconFadeStartOffset(): Float {
        return iconFadeStart.toFloat() / getDuration()
    }
    override fun getIconFadeEndOffset(): Float {
        return iconFadeEnd.toFloat() / getDuration()
    }
    override fun getAppRevealStartOffset(): Float {
        return appRevealStart.toFloat() / getDuration()
    }
    override fun getAppRevealEndOffset(): Float {
        return appRevealEnd.toFloat() / getDuration()
    }
    abstract override fun getDuration(): Int
}
