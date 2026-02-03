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
package com.android.launcher3.util.launcheremulator.models

import com.android.launcher3.util.launcheremulator.DensityPicker.Density
import com.android.launcher3.util.launcheremulator.models.DeviceEmulationData.Companion.FIXED_LANDSCAPE_GRID
import com.android.launcher3.util.launcheremulator.models.EmulationParamsUtils.getDensityShort
import com.android.launcher3.util.launcheremulator.models.EmulationParamsUtils.getDeviceShort
import com.android.launcher3.util.launcheremulator.models.EmulationParamsUtils.getFontScaleShort
import com.android.launcher3.util.launcheremulator.models.EmulationParamsUtils.getGridShort
import com.android.launcher3.util.launcheremulator.models.EmulationParamsUtils.getOrientationShort
import com.android.launcher3.util.launcheremulator.models.EmulationParamsUtils.getRtlShort
import com.android.launcher3.util.launcheremulator.models.EmulationParamsUtils.isDarkThemeShort
import com.android.launcher3.util.launcheremulator.models.EmulationParamsUtils.isUsingThemeIconsShort
import com.android.launcher3.util.launcheremulator.models.LauncherOrientation.LANDSCAPE
import com.android.launcher3.util.launcheremulator.models.LauncherOrientation.PORTRAIT
import com.android.launcher3.util.launcheremulator.models.LauncherOrientation.SEASCAPE
import java.util.Locale

/** Class to hold all parameters used for device and launcher config emulation */
data class EmulationParams
@JvmOverloads
constructor(
    val device: DeviceEmulationData,
    val density: Density = Density.NORMAL,
    val grid: String = device.defaultGrid,
    val orientation: LauncherOrientation = PORTRAIT,
    val fontScale: FontScale = FontScale.DEFAULT,
    val isRtl: Boolean = false,
    val isDarkTheme: Boolean = false,
    val isUsingThemeIcons: Boolean = false,
) {

    init {
        validateParameters()
    }

    val deviceType: DeviceType =
        when {
            device.secondDisplay != null -> DeviceType.Foldable
            device.isTablet() -> DeviceType.Tablet
            else -> DeviceType.Phone
        }

    val isFixedLandscape = grid == FIXED_LANDSCAPE_GRID && orientation == LANDSCAPE

    override fun toString(): String =
        listOf(
                getDeviceShort(device),
                getGridShort(grid),
                getDensityShort(density),
                getOrientationShort(orientation),
                getFontScaleShort(fontScale),
                getRtlShort(isRtl),
                isDarkThemeShort(isDarkTheme),
                isUsingThemeIconsShort(isUsingThemeIcons),
            )
            .filter { it.isNotBlank() }
            .joinToString("_")

    enum class DeviceType {
        Phone,
        Tablet,
        Foldable,
    }

    private fun validateParameters() {
        if (isFixedLandscape && !device.supportsFixedLandscape) {
            throw Exception(
                "Device ${device.name} doesn't support fixed landscape, update " +
                    "EmulationParams or update the DeviceList.kt"
            )
        }
    }
}

object EmulationParamsUtils {

    fun getDeviceShort(device: DeviceEmulationData): String =
        when (device.name) {
            "pixel5" -> "p5"
            "pixel6" -> "p6"
            "pixel6pro" -> "p6pro"
            "pixel7pro" -> "p7pro"
            "pixelFoldable2023" -> "fold"
            "pixelFoldable2023_frontDisplay" -> "foldFront"
            "pixelTablet2023" -> "tab"
            else -> device.name
        }

    fun getGridShort(grid: String) =
        when (grid) {
            "normal" -> "5x5"
            "practical" -> "4x5"
            "reasonable" -> "4x4"
            "big" -> "3x3"
            "crazy_big" -> "2x2"
            "tablet_normal" -> "6x5"
            else -> grid
        }

    fun getDensityShort(density: Density) =
        "den" +
            when (density) {
                Density.SMALL -> "S"
                Density.NORMAL -> "N"
                Density.LARGE -> "L"
                Density.LARGER -> "L2"
                Density.LARGEST -> "L3"
            }

    fun getOrientationShort(orientation: LauncherOrientation) =
        when (orientation) {
            PORTRAIT -> "port"
            LANDSCAPE -> "land"
            SEASCAPE -> "seas"
        }

    fun getFontScaleShort(fontScale: FontScale) =
        if (fontScale != FontScale.DEFAULT) "font${fontScale.value}" else ""

    fun getRtlShort(isRtl: Boolean) = if (isRtl) "rtl" else ""

    fun isDarkThemeShort(isDarkTheme: Boolean) = if (isDarkTheme) "dark" else ""

    fun isUsingThemeIconsShort(isUsingThemeIcons: Boolean) =
        if (isUsingThemeIcons) "thIcons" else ""
}

enum class LauncherOrientation {
    PORTRAIT,
    LANDSCAPE,
    SEASCAPE,
}

enum class FontScale(val value: Float) {
    SMALL(0.85f),
    DEFAULT(1f),
    LARGEST(2f);

    override fun toString() = name.lowercase(Locale.getDefault())
}
