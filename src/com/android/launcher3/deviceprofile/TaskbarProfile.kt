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

package com.android.launcher3.deviceprofile

import android.content.res.Resources
import android.util.DisplayMetrics
import androidx.core.content.res.ResourcesCompat
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R
import com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR
import com.android.launcher3.testing.shared.ResourceUtils.pxFromDp

data class TaskbarProfile(
    val height: Int,
    val stashedTaskbarHeight: Int,
    val bottomMargin: Int,
    val iconSize: Int,
    val transientTaskbarClaimedSpace: Int,
    // If true, used to layout taskbar in 3 button navigation mode.
    val isStartAlignTaskbar: Boolean,
    val isTransientTaskbar: Boolean,
) {
    companion object Factory {
        fun createTaskbarProfile(
            res: Resources,
            isTransientTaskbar: Boolean,
            isTaskbarPresent: Boolean,
            metrics: DisplayMetrics,
            displayOptionSpec: InvariantDeviceProfile.DisplayOptionSpec,
            typeIndex: Int,
            inv: InvariantDeviceProfile,
        ): TaskbarProfile {
            val transientTaskbarIconSize =
                pxFromDp(inv.transientTaskbarIconSize.get(typeIndex), metrics)
            val transientTaskbarBottomMargin: Int =
                res.getDimensionPixelSize(R.dimen.transient_taskbar_bottom_margin)
            val transientTaskbarHeight =
                Math.round(
                    (transientTaskbarIconSize * ICON_VISIBLE_AREA_FACTOR) +
                        (2 * res.getDimensionPixelSize(R.dimen.transient_taskbar_padding))
                )
            val transientTaskbarClaimedSpace =
                transientTaskbarHeight + 2 * transientTaskbarBottomMargin

            return when {
                !isTaskbarPresent -> TaskbarProfile(
                    bottomMargin = 0,
                    stashedTaskbarHeight = 0,
                    height = 0,
                    iconSize = 0,
                    isStartAlignTaskbar = false,
                    transientTaskbarClaimedSpace = transientTaskbarClaimedSpace,
                    isTransientTaskbar = isTransientTaskbar,
                )
                isTransientTaskbar -> TaskbarProfile(
                    iconSize = transientTaskbarIconSize,
                    height = transientTaskbarHeight,
                    stashedTaskbarHeight =
                        res.getDimensionPixelSize(R.dimen.transient_taskbar_stashed_height),
                    bottomMargin = transientTaskbarBottomMargin,
                    isStartAlignTaskbar = false,
                    transientTaskbarClaimedSpace = transientTaskbarClaimedSpace,
                    isTransientTaskbar = isTransientTaskbar,
                )
                else -> TaskbarProfile(
                    iconSize =
                        pxFromDp(ResourcesCompat.getFloat(res, R.dimen.taskbar_icon_size), metrics),
                    height = res.getDimensionPixelSize(R.dimen.taskbar_size),
                    stashedTaskbarHeight = res.getDimensionPixelSize(R.dimen.taskbar_stashed_size),
                    bottomMargin = 0,
                    isStartAlignTaskbar = displayOptionSpec.startAlignTaskbar,
                    isTransientTaskbar = isTransientTaskbar,
                    transientTaskbarClaimedSpace = transientTaskbarClaimedSpace,
                )
            }

        }
    }
}
