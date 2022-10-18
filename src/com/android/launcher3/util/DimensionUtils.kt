/*
 * Copyright (C) 2022 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.launcher3.util

import android.content.res.Resources
import android.graphics.Point
import android.view.ViewGroup
import com.android.launcher3.DeviceProfile
import com.android.launcher3.R

object DimensionUtils {
    /**
     * Point where x is width, and y is height of taskbar based on provided [deviceProfile]
     * x or y could also be -1 to indicate there is no dimension specified
     */
    @JvmStatic
    fun getTaskbarPhoneDimensions(deviceProfile: DeviceProfile, res: Resources,
                                  isPhoneMode: Boolean): Point {
        val p = Point()
        // Taskbar for large screen
        if (!isPhoneMode) {
            p.x = ViewGroup.LayoutParams.MATCH_PARENT
            p.y = deviceProfile.taskbarSize
            return p
        }

        // Taskbar on phone using gesture nav, it will always be stashed
        if (deviceProfile.isGestureMode) {
            p.x = ViewGroup.LayoutParams.MATCH_PARENT
            p.y = res.getDimensionPixelSize(R.dimen.taskbar_stashed_size)
            return p
        }

        // Taskbar on phone, portrait
        if (!deviceProfile.isLandscape) {
            p.x = ViewGroup.LayoutParams.MATCH_PARENT
            p.y = res.getDimensionPixelSize(R.dimen.taskbar_size)
            return p
        }

        // Taskbar on phone, landscape
        p.x = res.getDimensionPixelSize(R.dimen.taskbar_size)
        p.y = ViewGroup.LayoutParams.MATCH_PARENT
        return p
    }
}