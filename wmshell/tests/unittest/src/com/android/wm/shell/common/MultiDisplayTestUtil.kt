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

package com.android.wm.shell.common

import android.content.res.Resources
import android.graphics.RectF
import android.util.DisplayMetrics
import android.view.DisplayInfo
import org.mockito.Mockito.spy

/** Utility class for tests of [DesktopModeWindowDecorViewModel] */
object MultiDisplayTestUtil {
    // We have four displays, display#1 is placed on the center top of display#0, display#2 is
    // adjacent to display#1 and display#3 is on the right of display#0.
    //   +---+-------+
    //   | 1 |   2   |
    // +-+---+-+---+-+
    // |   0   | 3 |
    // +-------+   |
    //         +---+

    enum class TestDisplay(val id: Int, val bounds: RectF, val dpi: Int) {
        DISPLAY_0(0, RectF(0f, 0f, 1200f, 800f), DisplayMetrics.DENSITY_DEFAULT),
        DISPLAY_1(1, RectF(100f, -1000f, 1100f, 0f), DisplayMetrics.DENSITY_DEFAULT * 2),
        DISPLAY_2(2, RectF(1100f, -1000f, 2100f, 0f), DisplayMetrics.DENSITY_DEFAULT),
        DISPLAY_3(3, RectF(1200f, 0f, 1600f, 1200f), DisplayMetrics.DENSITY_DEFAULT / 4);

        fun getSpyDisplayLayout(resources: Resources): DisplayLayout {
            val displayInfo = DisplayInfo()
            displayInfo.logicalDensityDpi = dpi
            val displayLayout = spy(DisplayLayout(displayInfo, resources, true, true))
            displayLayout.setGlobalBoundsDp(bounds)
            return displayLayout
        }
    }
}
