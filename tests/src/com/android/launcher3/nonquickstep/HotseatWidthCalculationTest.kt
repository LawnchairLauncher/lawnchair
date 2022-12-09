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
 * limitations under the License.
 */
package com.android.launcher3.nonquickstep

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.DeviceProfileBaseTest
import com.android.launcher3.util.WindowBounds
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class HotseatWidthCalculationTest : DeviceProfileBaseTest() {

    /**
     * This is a case when after setting the hotseat, the space needs to be recalculated
     * but it doesn't need to change QSB width or remove icons
     */
    @Test
    fun distribute_border_space_when_space_is_enough_portrait() {
        initializeVarsForTablet(isGestureMode = false)
        windowBounds = WindowBounds(Rect(0, 0, 1800, 2560), Rect(0, 104, 0, 0))
        val dp = newDP()
        dp.isTaskbarPresentInApps = true

        assertThat(dp.hotseatBarEndOffset).isEqualTo(0)
        assertThat(dp.numShownHotseatIcons).isEqualTo(6)
        assertThat(dp.hotseatBorderSpace).isEqualTo(145)

        assertThat(dp.getHotseatLayoutPadding(context).left).isEqualTo(177)
        assertThat(dp.getHotseatLayoutPadding(context).right).isEqualTo(177)

        assertThat(dp.isQsbInline).isFalse()
        assertThat(dp.hotseatQsbWidth).isEqualTo(1445)
    }

    /**
     * This is a case when after setting the hotseat, and recalculating spaces
     * it still needs to remove icons for everything to fit
     */
    @Test
    fun decrease_num_of_icons_when_not_enough_space_portrait() {
        initializeVarsForTablet(isGestureMode = false)
        windowBounds = WindowBounds(Rect(0, 0, 1300, 2560), Rect(0, 104, 0, 0))
        val dp = newDP()
        dp.isTaskbarPresentInApps = true

        assertThat(dp.hotseatBarEndOffset).isEqualTo(0)
        assertThat(dp.numShownHotseatIcons).isEqualTo(6)
        assertThat(dp.hotseatBorderSpace).isEqualTo(72)

        assertThat(dp.getHotseatLayoutPadding(context).left).isEqualTo(110)
        assertThat(dp.getHotseatLayoutPadding(context).right).isEqualTo(110)

        assertThat(dp.isQsbInline).isFalse()
        assertThat(dp.hotseatQsbWidth).isEqualTo(1080)
    }

    /**
     * This is a case when after setting the hotseat, the space needs to be recalculated
     * but it doesn't need to change QSB width or remove icons
     */
    @Test
    fun distribute_border_space_when_space_is_enough_landscape() {
        initializeVarsForTwoPanel(isGestureMode = false, isLandscape = true)
        val dp = newDP()
        dp.isTaskbarPresentInApps = true

        assertThat(dp.hotseatBarEndOffset).isEqualTo(0)
        assertThat(dp.numShownHotseatIcons).isEqualTo(6)
        assertThat(dp.hotseatBorderSpace).isEqualTo(104)

        assertThat(dp.getHotseatLayoutPadding(context).left).isEqualTo(370)
        assertThat(dp.getHotseatLayoutPadding(context).right).isEqualTo(370)

        assertThat(dp.isQsbInline).isFalse()
        assertThat(dp.hotseatQsbWidth).isEqualTo(1468)
    }

    /**
     * This is a case when the hotseat spans a certain amount of columns
     * and the nav buttons push the hotseat to the side, but not enough to change the border space.
     */
    @Test
    fun nav_buttons_dont_interfere_with_required_hotseat_width() {
        initializeVarsForTablet(isGestureMode = false, isLandscape = true)
        inv?.apply {
            hotseatColumnSpan = IntArray(4) { 4 }
            inlineQsb = BooleanArray(4) { false }
        }
        val dp = newDP()
        dp.isTaskbarPresentInApps = true

        assertThat(dp.hotseatBarEndOffset).isEqualTo(0)
        assertThat(dp.numShownHotseatIcons).isEqualTo(6)
        assertThat(dp.hotseatBorderSpace).isEqualTo(100)

        assertThat(dp.getHotseatLayoutPadding(context).left).isEqualTo(668)
        assertThat(dp.getHotseatLayoutPadding(context).right).isEqualTo(668)

        assertThat(dp.isQsbInline).isFalse()
        assertThat(dp.hotseatQsbWidth).isEqualTo(1224)
    }

    /**
     * This is a case when after setting the hotseat, the QSB width needs to be changed to fit
     */
    @Test
    fun decrease_qsb_when_not_enough_space_landscape() {
        initializeVarsForTablet(isGestureMode = false, isLandscape = true)
        windowBounds = WindowBounds(Rect(0, 0, 2460, 1600), Rect(0, 104, 0, 0))
        val dp = newDP()
        dp.isTaskbarPresentInApps = true

        assertThat(dp.hotseatBarEndOffset).isEqualTo(0)
        assertThat(dp.numShownHotseatIcons).isEqualTo(6)
        assertThat(dp.hotseatBorderSpace).isEqualTo(91)

        assertThat(dp.getHotseatLayoutPadding(context).left).isEqualTo(640)
        assertThat(dp.getHotseatLayoutPadding(context).right).isEqualTo(640)

        assertThat(dp.isQsbInline).isFalse()
        assertThat(dp.hotseatQsbWidth).isEqualTo(1179)
    }

    /**
     * This is a case when after setting the hotseat, changing QSB width, and recalculating spaces
     * it still needs to remove icons for everything to fit
     */
    @Test
    fun decrease_num_of_icons_when_not_enough_space_landscape() {
        initializeVarsForTablet(isGestureMode = false, isLandscape = true)
        windowBounds = WindowBounds(Rect(0, 0, 2260, 1600), Rect(0, 104, 0, 0))
        val dp = newDP()
        dp.isTaskbarPresentInApps = true

        assertThat(dp.hotseatBarEndOffset).isEqualTo(0)
        assertThat(dp.numShownHotseatIcons).isEqualTo(6)
        assertThat(dp.hotseatBorderSpace).isEqualTo(75)

        assertThat(dp.getHotseatLayoutPadding(context).left).isEqualTo(582)
        assertThat(dp.getHotseatLayoutPadding(context).right).isEqualTo(582)

        assertThat(dp.isQsbInline).isFalse()
        assertThat(dp.hotseatQsbWidth).isEqualTo(1095)
    }
}
