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

package com.android.systemui.animation

import kotlin.text.buildString

class FontVariationUtils {
    private var mWeight = -1
    private var mWidth = -1
    private var mOpticalSize = -1
    private var mRoundness = -1
    private var mCurrentFVar = ""

    /*
     * generate fontVariationSettings string, used for key in typefaceCache in TextAnimator
     * the order of axes should align to the order of parameters
     */
    fun updateFontVariation(
        weight: Int = -1,
        width: Int = -1,
        opticalSize: Int = -1,
        roundness: Int = -1,
    ): String {
        var isUpdated = false
        if (weight >= 0 && mWeight != weight) {
            isUpdated = true
            mWeight = weight
        }

        if (width >= 0 && mWidth != width) {
            isUpdated = true
            mWidth = width
        }

        if (opticalSize >= 0 && mOpticalSize != opticalSize) {
            isUpdated = true
            mOpticalSize = opticalSize
        }

        if (roundness >= 0 && mRoundness != roundness) {
            isUpdated = true
            mRoundness = roundness
        }

        if (!isUpdated) {
            return mCurrentFVar
        }

        return buildString {
                if (mWeight >= 0) {
                    if (!isBlank()) append(", ")
                    append("'${GSFAxes.WEIGHT.tag}' $mWeight")
                }

                if (mWidth >= 0) {
                    if (!isBlank()) append(", ")
                    append("'${GSFAxes.WIDTH.tag}' $mWidth")
                }

                if (mOpticalSize >= 0) {
                    if (!isBlank()) append(", ")
                    append("'${GSFAxes.OPTICAL_SIZE.tag}' $mOpticalSize")
                }

                if (mRoundness >= 0) {
                    if (!isBlank()) append(", ")
                    append("'${GSFAxes.ROUND.tag}' $mRoundness")
                }
            }
            .also { mCurrentFVar = it }
    }
}
