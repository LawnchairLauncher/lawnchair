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

import androidx.test.filters.SmallTest
import com.android.launcher3.AbstractDeviceProfileTest
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Flags
import com.android.launcher3.InvariantDeviceProfile
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Tests for DeviceProfile. */
@SmallTest
@RunWith(Parameterized::class)
class DeviceProfileDumpTest : AbstractDeviceProfileTest() {
    private val folderName: String = "DeviceProfileDumpTest"

    @Parameterized.Parameter lateinit var instance: TestCase

    @Before
    fun setUp() {
        if (instance.decoupleDepth) {
            setFlagsRule.enableFlags(Flags.FLAG_ENABLE_SCALING_REVEAL_HOME_ANIMATION)
        } else {
            setFlagsRule.disableFlags(Flags.FLAG_ENABLE_SCALING_REVEAL_HOME_ANIMATION)
        }
    }

    @Test
    fun dumpPortraitGesture() {
        initializeDevice(instance.deviceName, isGestureMode = true, isLandscape = false)
        val dp = getDeviceProfileForGrid(instance.gridName)
        dp.isTaskbarPresentInApps = instance.isTaskbarPresentInApps

        assertDump(dp, instance.filename("Portrait"))
    }

    @Test
    fun dumpPortrait3Button() {
        initializeDevice(instance.deviceName, isGestureMode = false, isLandscape = false)
        val dp = getDeviceProfileForGrid(instance.gridName)
        dp.isTaskbarPresentInApps = instance.isTaskbarPresentInApps

        assertDump(dp, instance.filename("Portrait3Button"))
    }

    @Test
    fun dumpLandscapeGesture() {
        initializeDevice(instance.deviceName, isGestureMode = true, isLandscape = true)
        val dp = getDeviceProfileForGrid(instance.gridName)
        dp.isTaskbarPresentInApps = instance.isTaskbarPresentInApps

        val testName =
            if (instance.deviceName == "phone") {
                "VerticalBar"
            } else {
                "Landscape"
            }
        assertDump(dp, instance.filename(testName))
    }

    @Test
    fun dumpLandscape3Button() {
        initializeDevice(instance.deviceName, isGestureMode = false, isLandscape = true)
        val dp = getDeviceProfileForGrid(instance.gridName)
        dp.isTaskbarPresentInApps = instance.isTaskbarPresentInApps

        val testName =
            if (instance.deviceName == "phone") {
                "VerticalBar3Button"
            } else {
                "Landscape3Button"
            }
        assertDump(dp, instance.filename(testName))
    }

    private fun initializeDevice(deviceName: String, isGestureMode: Boolean, isLandscape: Boolean) {
        val deviceSpec = deviceSpecs[instance.deviceName]!!
        when (deviceName) {
            "twopanel-phone",
            "twopanel-tablet" ->
                initializeVarsForTwoPanel(
                    deviceSpecUnfolded = deviceSpecs["twopanel-tablet"]!!,
                    deviceSpecFolded = deviceSpecs["twopanel-phone"]!!,
                    isLandscape = isLandscape,
                    isGestureMode = isGestureMode,
                )
            "tablet" ->
                initializeVarsForTablet(
                    deviceSpec = deviceSpec,
                    isLandscape = isLandscape,
                    isGestureMode = isGestureMode
                )
            else ->
                initializeVarsForPhone(
                    deviceSpec = deviceSpec,
                    isVerticalBar = isLandscape,
                    isGestureMode = isGestureMode
                )
        }
    }

    private fun getDeviceProfileForGrid(gridName: String): DeviceProfile {
        return InvariantDeviceProfile(context, gridName).getDeviceProfile(context)
    }

    private fun assertDump(dp: DeviceProfile, filename: String) {
        assertDump(dp, folderName, filename)
    }

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getInstances(): List<TestCase> {
            return listOf(
                TestCase("phone", gridName = "5_by_5"),
                TestCase("tablet", gridName = "6_by_5", isTaskbarPresentInApps = true),
                TestCase("twopanel-tablet", gridName = "4_by_4", isTaskbarPresentInApps = true),
                TestCase(
                    "twopanel-tablet",
                    gridName = "4_by_4",
                    isTaskbarPresentInApps = true,
                    decoupleDepth = true
                ),
            )
        }

        data class TestCase(
            val deviceName: String,
            val gridName: String,
            val isTaskbarPresentInApps: Boolean = false,
            val decoupleDepth: Boolean = false
        ) {
            fun filename(testName: String = ""): String {
                val device =
                    when (deviceName) {
                        "tablet" -> "tablet"
                        "twopanel-tablet" -> "twoPanel"
                        "twopanel-phone" -> "twoPanelFolded"
                        else -> "phone"
                    }
                val depth =
                    if (decoupleDepth) {
                        "_decoupleDepth"
                    } else {
                        ""
                    }
                return "$device$testName$depth"
            }
        }
    }
}
