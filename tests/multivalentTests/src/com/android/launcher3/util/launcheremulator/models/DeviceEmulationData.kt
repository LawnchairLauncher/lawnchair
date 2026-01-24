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
package com.android.launcher3.util.launcheremulator.models

import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.os.Build
import android.util.ArrayMap
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.InvariantDeviceProfile.DeviceType
import com.android.launcher3.testing.shared.ResourceUtils
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.window.WindowManagerProxy.MIN_TABLET_WIDTH
import java.util.Locale
import kotlin.math.min

/** Model class that holds the data needed to emulate a device display. */
class DeviceEmulationData(
    val name: String,
    width: Int,
    height: Int,
    density: Int,
    @JvmField val densityMaxScale: Float,
    @JvmField val densityMinScale: Float,
    @JvmField val densityMinScaleInterval: Float,
    cutout: Rect,
    var defaultGrid: String,
    @JvmField val resourceOverrides: Map<String, Int>,
    @JvmField val secondDisplay: EmulatorDisplay?,
    val supportsFixedLandscape: Boolean,
) : EmulatorDisplay(width, height, density, cutout) {

    override fun toString(): String {
        val secondDisplayString =
            secondDisplay?.let { "EmulatorDisplay(${it.propString("\n\t\t")})" } ?: "null"
        val resourcesString =
            resourceOverrides.entries.joinToString { "\n\t\t\"${it.key}\" to ${it.value}" }
        return "DeviceEmulationData(" +
            "\n\tname = \"$name\"," +
            propString("\n\t") +
            "," +
            "\n\tdensityMaxScale = ${densityMaxScale}f," +
            "\n\tdensityMinScale = ${densityMinScale}f," +
            "\n\tdensityMinScaleInterval = ${densityMinScaleInterval}f," +
            "\n\tdefaultGrid = \"${defaultGrid}\"," +
            "\n\tsecondDisplay = $secondDisplayString," +
            "\n\tresourceOverrides = mapOf($resourcesString)" +
            "\n)"
    }

    /** Returns if the device is in tablet dimension */
    fun isTablet() = (min(width, height).toFloat() / (density / 160)) > MIN_TABLET_WIDTH

    companion object {
        private const val DENSITY_MIN_SCALE = 0.85f
        private const val DENSITY_MAX_SCALE = 1.5f
        private const val DENSITY_SCALE_INTERVAL = 0.09f
        private val EMULATED_SYSTEM_RESOURCES =
            arrayOf(
                ResourceUtils.NAVBAR_HEIGHT,
                ResourceUtils.NAVBAR_HEIGHT_LANDSCAPE,
                ResourceUtils.NAVBAR_LANDSCAPE_LEFT_RIGHT_SIZE,
                ResourceUtils.STATUS_BAR_HEIGHT,
                ResourceUtils.STATUS_BAR_HEIGHT_LANDSCAPE,
                ResourceUtils.STATUS_BAR_HEIGHT_PORTRAIT,
            )

        const val FIXED_LANDSCAPE_GRID = "fixed_landscape_mode"

        private fun getFraction(c: Context, resName: String, fallback: Float): Float {
            val resId = c.resources.getIdentifier(resName, "fraction", c.packageName)
            return if (resId != Resources.ID_NULL) c.resources.getFraction(resId, 1, 1)
            else fallback
        }

        /**
         * Returns a `DeviceEmulationData` representing the current device.
         *
         * @param context Launcher context.
         * @return `DeviceEmulationData`
         */
        @JvmStatic
        @JvmOverloads
        fun getCurrentDeviceData(
            context: Context,
            info: DisplayController.Info = DisplayController.INSTANCE[context].info,
        ): DeviceEmulationData {
            val code = Build.MODEL.replace("\\s".toRegex(), "").lowercase(Locale.getDefault())
            val resourceOverrides: MutableMap<String, Int> = ArrayMap()
            for (s in EMULATED_SYSTEM_RESOURCES) {
                resourceOverrides[s] = ResourceUtils.getDimenByName(s, context.resources, 0)
            }

            // Print overridden resources
            val settingsCtx =
                try {
                    context.createPackageContext("com.android.settings", 0)
                } catch (e: Exception) {
                    context
                }
            val displayDensityMaxScale =
                getFraction(settingsCtx, "display_density_max_scale", DENSITY_MAX_SCALE)
            val displayDensityMinScale =
                getFraction(settingsCtx, "display_density_min_scale", DENSITY_MIN_SCALE)
            val displayDensityMinScaleInterval =
                getFraction(
                    settingsCtx,
                    "display_density_min_scale_interval",
                    DENSITY_SCALE_INTERVAL,
                )

            @DeviceType val deviceType = info.deviceType
            val defaultGrid =
                InvariantDeviceProfile.parseAllDefinedGridOptions(context, info)
                    .stream()
                    .filter { it.isEnabled(deviceType) }
                    .map { it.name }
                    .findFirst()
                    .get()

            return DeviceEmulationData(
                name = code,
                width = info.currentSize.x,
                height = info.currentSize.y,
                density = info.densityDpi,
                densityMaxScale = displayDensityMaxScale,
                densityMinScale = displayDensityMinScale,
                densityMinScaleInterval = displayDensityMinScaleInterval,
                cutout = info.cutout,
                defaultGrid = defaultGrid,
                resourceOverrides = resourceOverrides,
                secondDisplay = null,
                supportsFixedLandscape = deviceType == InvariantDeviceProfile.TYPE_PHONE,
            )
        }

        /** Returns a stored `DeviceEmulationData` */
        @JvmStatic
        fun getDevice(deviceCode: String) =
            LauncherDeviceList.ALL_DEVICES.find { it.name == deviceCode }!!
    }
}
