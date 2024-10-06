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
package com.android.launcher3.util

import android.content.res.Resources
import androidx.core.content.res.getDimensionOrThrow
import androidx.core.content.res.use
import com.android.launcher3.R
import kotlin.math.max

class IconSizeSteps(res: Resources) {
    private val steps: List<Int>
    val minimumIconLabelSize: Int

    init {
        steps =
            res.obtainTypedArray(R.array.icon_size_steps).use {
                (0 until it.length()).map { step -> it.getDimensionOrThrow(step).toInt() }.sorted()
            }
        minimumIconLabelSize = res.getDimensionPixelSize(R.dimen.minimum_icon_label_size)
    }

    fun minimumIconSize(): Int = steps[0]

    fun getNextLowerIconSize(iconSizePx: Int): Int {
        return steps[max(0, getIndexForIconSize(iconSizePx) - 1)]
    }

    fun getIconSmallerThan(cellSize: Int): Int {
        return steps.lastOrNull { it <= cellSize } ?: steps[0]
    }

    private fun getIndexForIconSize(iconSizePx: Int): Int {
        return max(0, steps.indexOfFirst { iconSizePx <= it })
    }

    companion object {
        internal const val TEXT_STEP = 1

        // This icon extra step is used for stepping down logic in extreme cases when it's
        // necessary to reduce the icon size below minimum size available in [icon_size_steps].
        internal const val ICON_SIZE_STEP_EXTRA = 2
    }
}
