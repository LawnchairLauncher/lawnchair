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
package com.android.launcher3.nonquickstep

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.AbstractDeviceProfileTest
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for DeviceProfile. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceProfileDumpTest : AbstractDeviceProfileTest() {
    private val testContext: Context = InstrumentationRegistry.getInstrumentation().context
    private val folderName: String = "DeviceProfileDumpTest"
    @Test
    fun phonePortrait3Button() {
        initializeVarsForPhone(deviceSpecs["phone"]!!, isGestureMode = false)
        val dp = getDeviceProfileForGrid("5_by_5")

        assertDump(dp, "phonePortrait3Button")
    }

    @Test
    fun phonePortrait() {
        initializeVarsForPhone(deviceSpecs["phone"]!!)
        val dp = getDeviceProfileForGrid("5_by_5")

        assertDump(dp, "phonePortrait")
    }

    @Test
    fun phoneVerticalBar3Button() {
        initializeVarsForPhone(deviceSpecs["phone"]!!, isVerticalBar = true, isGestureMode = false)
        val dp = getDeviceProfileForGrid("5_by_5")

        assertDump(dp, "phoneVerticalBar3Button")
    }

    @Test
    fun phoneVerticalBar() {
        initializeVarsForPhone(deviceSpecs["phone"]!!, isVerticalBar = true)
        val dp = getDeviceProfileForGrid("5_by_5")

        assertDump(dp, "phoneVerticalBar")
    }

    @Test
    fun tabletLandscape3Button() {
        initializeVarsForTablet(deviceSpecs["tablet"]!!, isLandscape = true, isGestureMode = false)
        val dp = getDeviceProfileForGrid("6_by_5")
        dp.isTaskbarPresentInApps = true

        assertDump(dp, "tabletLandscape3Button")
    }

    @Test
    fun tabletLandscape() {
        initializeVarsForTablet(deviceSpecs["tablet"]!!, isLandscape = true)
        val dp = getDeviceProfileForGrid("6_by_5")
        dp.isTaskbarPresentInApps = true

        assertDump(dp, "tabletLandscape")
    }

    @Test
    fun tabletPortrait3Button() {
        initializeVarsForTablet(deviceSpecs["tablet"]!!, isGestureMode = false)
        val dp = getDeviceProfileForGrid("6_by_5")
        dp.isTaskbarPresentInApps = true

        assertDump(dp, "tabletPortrait3Button")
    }

    @Test
    fun tabletPortrait() {
        initializeVarsForTablet(deviceSpecs["tablet"]!!)
        val dp = getDeviceProfileForGrid("6_by_5")
        dp.isTaskbarPresentInApps = true

        assertDump(dp, "tabletPortrait")
    }

    @Test
    fun twoPanelLandscape3Button() {
        initializeVarsForTwoPanel(
            deviceSpecs["twopanel-tablet"]!!,
            deviceSpecs["twopanel-phone"]!!,
            isLandscape = true,
            isGestureMode = false
        )
        val dp = getDeviceProfileForGrid("4_by_4")
        dp.isTaskbarPresentInApps = true

        assertDump(dp, "twoPanelLandscape3Button")
    }

    @Test
    fun twoPanelLandscape() {
        initializeVarsForTwoPanel(
            deviceSpecs["twopanel-tablet"]!!,
            deviceSpecs["twopanel-phone"]!!,
            isLandscape = true
        )
        val dp = getDeviceProfileForGrid("4_by_4")
        dp.isTaskbarPresentInApps = true

        assertDump(dp, "twoPanelLandscape")
    }

    @Test
    fun twoPanelPortrait3Button() {
        initializeVarsForTwoPanel(
            deviceSpecs["twopanel-tablet"]!!,
            deviceSpecs["twopanel-phone"]!!,
            isGestureMode = false
        )
        val dp = getDeviceProfileForGrid("4_by_4")
        dp.isTaskbarPresentInApps = true

        assertDump(dp, "twoPanelPortrait3Button")
    }

    @Test
    fun twoPanelPortrait() {
        initializeVarsForTwoPanel(deviceSpecs["twopanel-tablet"]!!, deviceSpecs["twopanel-phone"]!!)
        val dp = getDeviceProfileForGrid("4_by_4")
        dp.isTaskbarPresentInApps = true

        assertDump(dp, "twoPanelPortrait")
    }

    private fun getDeviceProfileForGrid(gridName: String): DeviceProfile {
        return InvariantDeviceProfile(context, gridName).getDeviceProfile(context)
    }

    private fun assertDump(dp: DeviceProfile, filename: String) {
        val dump = dump(context!!, dp, "${folderName}_$filename.txt")
        val expected = readDumpFromAssets(testContext, "$folderName/$filename.txt")

        assertThat(dump).isEqualTo(expected)
    }
}
