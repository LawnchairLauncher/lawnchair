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

package com.android.launcher3.util.window

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.window.WindowManagerProxy.areBottomDisplayCutoutsSmallAndAtCorners
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Unit test for [WindowManagerProxy] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class WindowManagerProxyTest {

    private val windowWidthPx = 2000

    private val bottomLeftCutout = Rect(0, 2364, 136, 2500)
    private val bottomRightCutout = Rect(1864, 2364, 2000, 2500)

    private val bottomLeftCutoutWithOffset = Rect(10, 2364, 136, 2500)
    private val bottomRightCutoutWithOffset = Rect(1864, 2364, 1990, 2500)

    private val maxWidthAndHeightOfSmallCutoutPx = 136

    @Test
    fun cutout_at_bottom_right_corner() {
        assertTrue(
            areBottomDisplayCutoutsSmallAndAtCorners(
                bottomRightCutout,
                windowWidthPx,
                maxWidthAndHeightOfSmallCutoutPx
            )
        )
    }

    @Test
    fun cutout_at_bottom_left_corner_with_offset() {
        assertTrue(
            areBottomDisplayCutoutsSmallAndAtCorners(
                bottomLeftCutoutWithOffset,
                windowWidthPx,
                maxWidthAndHeightOfSmallCutoutPx
            )
        )
    }

    @Test
    fun cutout_at_bottom_right_corner_with_offset() {
        assertTrue(
            areBottomDisplayCutoutsSmallAndAtCorners(
                bottomRightCutoutWithOffset,
                windowWidthPx,
                maxWidthAndHeightOfSmallCutoutPx
            )
        )
    }

    @Test
    fun cutout_at_bottom_left_corner() {
        assertTrue(
            areBottomDisplayCutoutsSmallAndAtCorners(
                bottomLeftCutout,
                windowWidthPx,
                maxWidthAndHeightOfSmallCutoutPx
            )
        )
    }

    @Test
    fun cutout_at_bottom_edge_at_bottom_corners() {
        assertTrue(
            areBottomDisplayCutoutsSmallAndAtCorners(
                bottomLeftCutout,
                windowWidthPx,
                maxWidthAndHeightOfSmallCutoutPx
            )
        )
    }

    @Test
    fun cutout_too_big_not_at_bottom_corners() {
        // Rect in size of 200px
        val bigBottomLeftCutout = Rect(0, 2300, 200, 2500)

        assertFalse(
            areBottomDisplayCutoutsSmallAndAtCorners(
                bigBottomLeftCutout,
                windowWidthPx,
                maxWidthAndHeightOfSmallCutoutPx
            )
        )
    }

    @Test
    fun cutout_too_small_at_bottom_corners() {
        // Rect in size of 100px
        val smallBottomLeft = Rect(0, 2400, 100, 2500)

        assertTrue(
            areBottomDisplayCutoutsSmallAndAtCorners(
                smallBottomLeft,
                windowWidthPx,
                maxWidthAndHeightOfSmallCutoutPx
            )
        )
    }

    @Test
    fun cutout_empty_not_at_bottom_corners() {
        val emptyRect = Rect(0, 0, 0, 0)

        assertFalse(
            areBottomDisplayCutoutsSmallAndAtCorners(
                emptyRect,
                windowWidthPx,
                maxWidthAndHeightOfSmallCutoutPx
            )
        )
    }
}
