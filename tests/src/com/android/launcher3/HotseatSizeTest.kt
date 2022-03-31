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
import com.android.launcher3.InvariantDeviceProfile.TYPE_MULTI_DISPLAY
import com.android.launcher3.InvariantDeviceProfile.TYPE_PHONE
import com.android.launcher3.InvariantDeviceProfile.TYPE_TABLET
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when` as whenever

/**
 * Test for [DeviceProfile]
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class HotseatSizeTest : DeviceProfileBaseTest() {

    @Test
    fun hotseat_size_is_normal_for_handhelds() {
        initializeVarsForPhone()
        inv = newScalableInvariantDeviceProfile().apply {
            deviceType = TYPE_PHONE
        }

        val dp = newDP()

        assertThat(dp.isQsbInline).isFalse()
        assertThat(dp.numShownHotseatIcons).isEqualTo(5)
    }

    @Test
    fun hotseat_size_is_max_for_foldables() {
        initializeVarsForTablet(isLandscape = true)
        inv = newScalableInvariantDeviceProfile().apply {
            deviceType = TYPE_MULTI_DISPLAY
        }
        useTwoPanels = true

        val dp = newDP()

        assertThat(dp.isQsbInline).isFalse()
        assertThat(dp.numShownHotseatIcons).isEqualTo(6)
    }

    @Test
    fun hotseat_size_is_shrunk_if_needed() {
        initializeVarsForTablet(isLandscape = true)
        inv = newScalableInvariantDeviceProfile().apply {
            deviceType = TYPE_MULTI_DISPLAY
            inlineQsb = booleanArrayOf(
                false,
                false,
                false,
                true // two panels landscape
            )
        }
        useTwoPanels = true

        isGestureMode = false
        val dp = newDP()

        if (dp.hotseatQsbHeight > 0) {
            assertThat(dp.isQsbInline).isTrue()
            assertThat(dp.numShownHotseatIcons).isEqualTo(4)
        } else { // Launcher3 doesn't have QSB height
            assertThat(dp.isQsbInline).isFalse()
            assertThat(dp.numShownHotseatIcons).isEqualTo(6)
        }
    }

    /**
     * For consistency, the hotseat should shrink if any orientation on the device type has an
     * inline qsb
     */
    @Test
    fun hotseat_size_is_shrunk_even_in_portrait() {
        initializeVarsForTablet()
        inv = newScalableInvariantDeviceProfile().apply {
            deviceType = TYPE_MULTI_DISPLAY
            inlineQsb = booleanArrayOf(
                false,
                false,
                false,
                true // two panels landscape
            )
        }
        useTwoPanels = true

        isGestureMode = false
        val dp = newDP()

        if (dp.hotseatQsbHeight > 0) {
            assertThat(dp.isQsbInline).isFalse()
            assertThat(dp.numShownHotseatIcons).isEqualTo(4)
        } else { // Launcher3 doesn't have QSB height
            assertThat(dp.isQsbInline).isFalse()
            assertThat(dp.numShownHotseatIcons).isEqualTo(6)
        }
    }

    @Test
    fun hotseat_size_is_default_when_folded() {
        initializeVarsForPhone()
        inv = newScalableInvariantDeviceProfile().apply {
            deviceType = TYPE_MULTI_DISPLAY
        }
        useTwoPanels = true

        val dp = newDP()

        assertThat(dp.numShownHotseatIcons).isEqualTo(5)
    }

    @Test
    fun hotseat_size_is_shrunk_if_needed_on_tablet() {
        initializeVarsForTablet(isLandscape = true)
        inv = newScalableInvariantDeviceProfile().apply {
            deviceType = TYPE_TABLET
            inlineQsb = booleanArrayOf(
                false,
                true, // landscape
                false,
                false
            )
        }

        isGestureMode = false
        val dp = newDP()

        if (dp.hotseatQsbHeight > 0) {
            assertThat(dp.isQsbInline).isTrue()
            assertThat(dp.numShownHotseatIcons).isEqualTo(4)
        } else { // Launcher3 doesn't have QSB height
            assertThat(dp.isQsbInline).isFalse()
            assertThat(dp.numShownHotseatIcons).isEqualTo(5)
        }
    }

    /**
     * For consistency, the hotseat should shrink if any orientation on the device type has an
     * inline qsb
     */
    @Test
    fun hotseat_size_is_shrunk_even_in_portrait_on_tablet() {
        initializeVarsForTablet()
        inv = newScalableInvariantDeviceProfile().apply {
            deviceType = TYPE_TABLET
            inlineQsb = booleanArrayOf(
                false,
                true, // landscape
                false,
                false
            )
        }

        isGestureMode = false
        val dp = newDP()

        if (dp.hotseatQsbHeight > 0) {
            assertThat(dp.isQsbInline).isFalse()
            assertThat(dp.numShownHotseatIcons).isEqualTo(4)
        } else { // Launcher3 doesn't have QSB height
            assertThat(dp.isQsbInline).isFalse()
            assertThat(dp.numShownHotseatIcons).isEqualTo(5)
        }
    }

}