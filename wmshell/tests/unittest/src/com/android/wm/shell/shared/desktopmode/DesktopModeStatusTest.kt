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

package com.android.wm.shell.shared.desktopmode

import android.content.Context
import android.content.res.Resources
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.Presubmit
import android.platform.test.flag.junit.SetFlagsRule
import android.window.DesktopExperienceFlags
import android.window.DesktopModeFlags
import androidx.test.filters.SmallTest
import com.android.internal.R
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Test class for [DesktopModeStatus].
 */
@SmallTest
@Presubmit
@EnableFlags(Flags.FLAG_SHOW_DESKTOP_WINDOWING_DEV_OPTION)
class DesktopModeStatusTest : ShellTestCase() {
    @get:Rule
    val mSetFlagsRule = SetFlagsRule()

    private val mockContext = mock<Context>()
    private val mockResources = mock<Resources>()

    @Before
    fun setUp() {
        doReturn(mockResources).whenever(mockContext).resources
        setDeviceEligibleForDesktopMode(false)
        doReturn(false).whenever(mockResources).getBoolean(
            eq(R.bool.config_isDesktopModeDevOptionSupported)
        )
        setCanInternalDisplayHostDesktops(false)
        doReturn(context.contentResolver).whenever(mockContext).contentResolver
        resetDesktopModeFlagsCache()
        resetEnforceDeviceRestriction()
    }

    @After
    fun tearDown() {
        resetDesktopModeFlagsCache()
        resetEnforceDeviceRestriction()
    }

    @DisableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
        Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION
    )
    @Test
    fun canEnterDesktopMode_DWFlagDisabled_deviceNotEligible_returnsFalse() {
        assertThat(DesktopModeStatus.canEnterDesktopMode(mockContext)).isFalse()
    }

    @DisableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
        Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION
    )
    @Test
    fun canEnterDesktopMode_DWFlagDisabled_deviceEligible_configDevOptionOn_returnsFalse() {
        setDeviceEligibleForDesktopMode(true)
        setCanInternalDisplayHostDesktops(true)
        doReturn(true).whenever(mockResources).getBoolean(
            eq(R.bool.config_isDesktopModeDevOptionSupported)
        )
        disableEnforceDeviceRestriction()

        assertThat(DesktopModeStatus.canEnterDesktopMode(mockContext)).isFalse()
    }

    @DisableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
        Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION
    )
    @Test
    fun canEnterDesktopMode_DWFlagDisabled_deviceNotEligible_configDevOptionOn_returnsFalse() {
        doReturn(true).whenever(mockResources).getBoolean(
            eq(R.bool.config_isDesktopModeDevOptionSupported)
        )

        assertThat(DesktopModeStatus.canEnterDesktopMode(mockContext)).isFalse()
    }

    @DisableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
        Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION
    )
    @Test
    fun canEnterDesktopMode_DWFlagDisabled_deviceNotEligible_forceUsingDevOption_returnsTrue() {
        doReturn(true).whenever(mockResources).getBoolean(
            eq(R.bool.config_isDesktopModeDevOptionSupported)
        )
        setFlagOverride(DesktopModeFlags.ToggleOverride.OVERRIDE_ON)

        assertThat(DesktopModeStatus.canEnterDesktopMode(mockContext)).isTrue()
    }

    @DisableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
        Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION
    )
    @Test
    fun canEnterDesktopMode_DWFlagDisabled_deviceNotEligible_forceNotUsingDevOption_returnsFalse() {
        doReturn(true).whenever(mockResources).getBoolean(
            eq(R.bool.config_isDesktopModeDevOptionSupported)
        )
        setFlagOverride(DesktopModeFlags.ToggleOverride.OVERRIDE_OFF)

        assertThat(DesktopModeStatus.canEnterDesktopMode(mockContext)).isFalse()
    }

    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION)
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    @Test
    fun canEnterDesktopMode_DWFlagEnabled_deviceNotEligible_returnsFalse() {
        doReturn(true).whenever(mockResources).getBoolean(
            eq(R.bool.config_isDesktopModeDevOptionSupported)
        )

        assertThat(DesktopModeStatus.canEnterDesktopMode(mockContext)).isFalse()
    }

    @DisableFlags(
        Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION,
        Flags.FLAG_ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE,
    )
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    @Test
    fun canEnterDesktopMode_DWFlagEnabled_deviceEligible_returnsTrue() {
        setDeviceEligibleForDesktopMode(true)
        setCanInternalDisplayHostDesktops(true)

        assertThat(DesktopModeStatus.canEnterDesktopMode(mockContext)).isTrue()
    }

    @DisableFlags(
        Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION,
        Flags.FLAG_ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE,
    )
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    @Test
    fun canEnterDesktopMode_DWFlagEnabled_deviceEligibleWithoutInternalDisplay_returnsFalse() {
        setDeviceEligibleForDesktopMode(true)
        setCanInternalDisplayHostDesktops(false)

        assertThat(DesktopModeStatus.canEnterDesktopMode(mockContext)).isFalse()
    }

    @DisableFlags(
        Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION,
    )
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
        Flags.FLAG_ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE,
    )
    @Test
    fun canEnterDesktopMode_DWAndProjectedFlagEnabled_eligibleWithoutInternalDisplay_returnsTrue() {
        setDeviceEligibleForDesktopMode(true)
        setCanInternalDisplayHostDesktops(false)

        assertThat(DesktopModeStatus.canEnterDesktopMode(mockContext)).isTrue()
    }


    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
        Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION
    )
    @Test
    fun canEnterDesktopMode_DWFlagEnabled_deviceNotEligible_forceUsingDevOption_returnsTrue() {
        doReturn(true).whenever(mockResources).getBoolean(
            eq(R.bool.config_isDesktopModeDevOptionSupported)
        )
        setFlagOverride(DesktopModeFlags.ToggleOverride.OVERRIDE_ON)

        assertThat(DesktopModeStatus.canEnterDesktopMode(mockContext)).isTrue()
    }

    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
        Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION
    )
    @Test
    fun canEnterDesktopMode_DWFlagEnabled_deviceNotEligible_forceNotUsingDevOption_returnsFalse() {
        doReturn(true).whenever(mockResources).getBoolean(
            eq(R.bool.config_isDesktopModeDevOptionSupported)
        )
        setFlagOverride(DesktopModeFlags.ToggleOverride.OVERRIDE_OFF)

        assertThat(DesktopModeStatus.canEnterDesktopMode(mockContext)).isFalse()
    }

    @Test
    fun isDeviceEligibleForDesktopMode_configDEModeOnAndIntDispHostsDesktop_returnsTrue() {
        doReturn(true).whenever(mockResources).getBoolean(eq(R.bool.config_isDesktopModeSupported))
        doReturn(true).whenever(mockResources).getBoolean(eq(R.bool.config_canInternalDisplayHostDesktops))

        assertThat(DesktopModeStatus.isDeviceEligibleForDesktopMode(mockContext)).isTrue()
    }

    @Test
    fun isDeviceEligibleForDesktopMode_configDEModeOffAndIntDispHostsDesktop_returnsFalse() {
        doReturn(false).whenever(mockResources).getBoolean(eq(R.bool.config_isDesktopModeSupported))
        doReturn(true).whenever(mockResources).getBoolean(eq(R.bool.config_canInternalDisplayHostDesktops))

        assertThat(DesktopModeStatus.isDeviceEligibleForDesktopMode(mockContext)).isFalse()
    }

    @DisableFlags(Flags.FLAG_ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE)
    @Test
    fun isDeviceEligibleForDesktopMode_configDEModeOnAndIntDispHostsDesktopOff_returnsTrue() {
        doReturn(true).whenever(mockResources).getBoolean(eq(R.bool.config_isDesktopModeSupported))
        doReturn(false).whenever(mockResources)
            .getBoolean(eq(R.bool.config_canInternalDisplayHostDesktops))

        assertThat(DesktopModeStatus.isDeviceEligibleForDesktopMode(mockContext)).isTrue()
    }

    @EnableFlags(Flags.FLAG_ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE)
    @Test
    fun isPDDeviceEligibleForDesktopMode_configDEModeOnAndIntDispHostsDesktopOff_returnsTrue() {
        doReturn(true).whenever(mockResources).getBoolean(eq(R.bool.config_isDesktopModeSupported))
        doReturn(false).whenever(mockResources).getBoolean(eq(R.bool.config_canInternalDisplayHostDesktops))

        assertThat(DesktopModeStatus.isDeviceEligibleForDesktopMode(mockContext)).isTrue()
    }

    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION)
    @Test
    fun isInternalDisplayEligibleToHostDesktops_supportFlagOff_returnsFalse() {
        assertThat(DesktopModeStatus.isDeviceEligibleForDesktopMode(mockContext)).isFalse()
    }

    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION)
    @Test
    fun isInternalDisplayEligibleToHostDesktops_supportFlagOn_returnsFalse() {
        assertThat(DesktopModeStatus.isDeviceEligibleForDesktopMode(mockContext)).isFalse()
    }

    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION)
    @Test
    fun isInternalDisplayEligibleToHostDesktops_supportFlagOn_configDevOptModeOn_returnsTrue() {
        doReturn(true).whenever(mockResources).getBoolean(
            eq(R.bool.config_isDesktopModeDevOptionSupported)
        )

        assertThat(DesktopModeStatus.isDeviceEligibleForDesktopMode(mockContext)).isTrue()
    }

    @DisableFlags(Flags.FLAG_SHOW_DESKTOP_EXPERIENCE_DEV_OPTION)
    @Test
    fun canShowDesktopExperienceDevOption_flagDisabled_returnsFalse() {
        setDeviceEligibleForDesktopMode(true)

        assertThat(DesktopModeStatus.canShowDesktopExperienceDevOption(mockContext)).isFalse()
    }

    @EnableFlags(Flags.FLAG_SHOW_DESKTOP_EXPERIENCE_DEV_OPTION)
    @Test
    fun canShowDesktopExperienceDevOption_flagEnabled_deviceNotEligible_returnsFalse() {
        assertThat(DesktopModeStatus.canShowDesktopExperienceDevOption(mockContext)).isFalse()
    }

    @EnableFlags(Flags.FLAG_SHOW_DESKTOP_EXPERIENCE_DEV_OPTION)
    @Test
    fun canShowDesktopExperienceDevOption_flagEnabled_deviceNotEligible_forceDevOpt_returnsTrue() {
        doReturn(true).whenever(mockResources).getBoolean(
            eq(R.bool.config_isDesktopModeDevOptionSupported)
        )

        assertThat(DesktopModeStatus.canShowDesktopExperienceDevOption(mockContext)).isTrue()
    }

    @EnableFlags(Flags.FLAG_SHOW_DESKTOP_EXPERIENCE_DEV_OPTION)
    @Test
    fun canShowDesktopExperienceDevOption_flagEnabled_deviceEligible_returnsTrue() {
        setDeviceEligibleForDesktopMode(true)

        assertThat(DesktopModeStatus.canShowDesktopExperienceDevOption(mockContext)).isTrue()
    }

    private fun resetEnforceDeviceRestriction() {
        setEnforceDeviceRestriction(true)
    }

    private fun disableEnforceDeviceRestriction() {
        setEnforceDeviceRestriction(false)
    }

    private fun setEnforceDeviceRestriction(value: Boolean) {
        val field = DesktopModeStatus::class.java.getDeclaredField("ENFORCE_DEVICE_RESTRICTIONS")
        field.isAccessible = true
        field.setBoolean(null, value)
    }

    private fun resetDesktopModeFlagsCache() {
        // Toggle raw override cache for DesktopModeFlags
        val cachedRawToggleOverride1 =
            DesktopModeFlags::class.java.getDeclaredField("sCachedRawToggleOverride")
        cachedRawToggleOverride1.isAccessible = true
        cachedRawToggleOverride1.set(null, DesktopModeFlags.ToggleOverride.OVERRIDE_UNSET)

        // Toggle override cache for DesktopModeFlags
        val cachedToggleOverride1 =
            DesktopModeFlags::class.java.getDeclaredField("sCachedToggleOverride")
        cachedToggleOverride1.isAccessible = true
        cachedToggleOverride1.set(null, null)

        // Toggle override cache for DesktopExperienceFlags
        val cachedToggleOverride2 =
            DesktopExperienceFlags::class.java.getDeclaredField("sCachedToggleOverride")
        cachedToggleOverride2.isAccessible = true
        cachedToggleOverride2.set(null, false)
    }

    private fun setFlagOverride(override: DesktopModeFlags.ToggleOverride) {
        resetDesktopModeFlagsCache()

        // Toggle raw override cache for DesktopModeFlags can be on/off/unset
        val cachedToggleOverride1 =
            DesktopModeFlags::class.java.getDeclaredField("sCachedRawToggleOverride")
        cachedToggleOverride1.isAccessible = true
        cachedToggleOverride1.set(null, override)

        // Toggle override cache for DesktopExperienceFlags can be true or false
        val cachedToggleOverride2 =
            DesktopExperienceFlags::class.java.getDeclaredField("sCachedToggleOverride")
        cachedToggleOverride2.isAccessible = true
        cachedToggleOverride2.set(null, override == DesktopModeFlags.ToggleOverride.OVERRIDE_ON)
    }

    private fun setDeviceEligibleForDesktopMode(eligible: Boolean) {
        doReturn(eligible).whenever(mockResources)
            .getBoolean(eq(R.bool.config_isDesktopModeSupported))
    }

    private fun setCanInternalDisplayHostDesktops(eligible: Boolean) {
        doReturn(eligible).whenever(mockResources)
            .getBoolean(eq(R.bool.config_canInternalDisplayHostDesktops))
    }

    private fun setIsVeiledResizeEnabled(enabled: Boolean) {
        val deviceRestrictions =
            DesktopModeStatus::class.java.getDeclaredField("IS_VEILED_RESIZE_ENABLED")
        deviceRestrictions.isAccessible = true
        deviceRestrictions.setBoolean(/* obj= */ null, /* z= */ enabled)
    }
}
