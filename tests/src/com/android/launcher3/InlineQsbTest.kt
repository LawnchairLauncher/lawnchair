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
package com.android.launcher3

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test for [DeviceProfile]
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class InlineQsbTest : DeviceProfileBaseTest() {

    @Test
    fun qsbWidth_is_match_parent_for_phones() {
        initializeVarsForPhone()

        val dp = newDP()

        assertThat(dp.isQsbInline).isFalse()
        assertThat(dp.qsbWidth).isEqualTo(0)
    }

    @Test
    fun qsbWidth_is_match_parent_for_tablet_portrait() {
        initializeVarsForTablet()
        inv = newScalableInvariantDeviceProfile().apply {
            inlineQsb = booleanArrayOf(
                false,
                true, // landscape
                false,
                false
            )
        }

        val dp = DeviceProfile(
            context,
            inv,
            info,
            windowBounds,
            isMultiWindowMode,
            transposeLayoutWithOrientation,
            useTwoPanels,
            isGestureMode
        )

        assertThat(dp.isQsbInline).isFalse()
        assertThat(dp.qsbWidth).isEqualTo(0)
    }

    @Test
    fun qsbWidth_has_size_for_tablet_landscape() {
        initializeVarsForTablet(isLandscape = true)
        inv = newScalableInvariantDeviceProfile().apply {
            inlineQsb = booleanArrayOf(
                false,
                true, // landscape
                false,
                false
            )
        }

        val dp = newDP()

        if (dp.hotseatQsbHeight > 0) {
            assertThat(dp.isQsbInline).isTrue()
            assertThat(dp.qsbWidth).isGreaterThan(0)
        } else { // Launcher3 doesn't have QSB height
            assertThat(dp.isQsbInline).isFalse()
            assertThat(dp.qsbWidth).isEqualTo(0)
        }
    }

    /**
     * This test is to make sure that a tablet doesn't inline the QSB if the layout doesn't support
     */
    @Test
    fun qsbWidth_is_match_parent_for_tablet_landscape_without_inline() {
        initializeVarsForTablet(isLandscape = true)
        useTwoPanels = true

        val dp = newDP()

        assertThat(dp.isQsbInline).isFalse()
        assertThat(dp.qsbWidth).isEqualTo(0)
    }

}