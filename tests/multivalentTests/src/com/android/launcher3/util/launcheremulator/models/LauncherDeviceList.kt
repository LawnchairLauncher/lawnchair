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

import android.graphics.Rect

object LauncherDeviceList {

    val pixel9pro =
        DeviceEmulationData(
            name = "pixel9pro",
            width = 960,
            height = 2142,
            density = 360,
            cutout = Rect(0, 153, 0, 0),
            densityMaxScale = 1.5f,
            densityMinScale = 0.85f,
            densityMinScaleInterval = 0.09000003f,
            defaultGrid = "medium",
            secondDisplay = null,
            resourceOverrides =
                mapOf(
                    "status_bar_height_portrait" to 153,
                    "status_bar_height" to 153,
                    "navigation_bar_height_landscape" to 54,
                    "navigation_bar_height" to 54,
                    "status_bar_height_landscape" to 54,
                    "navigation_bar_width" to 54,
                ),
            supportsFixedLandscape = false,
        )

    val pixel9proFold =
        DeviceEmulationData(
            name = "pixel9profold",
            width = 2076,
            height = 2152,
            density = 390,
            cutout = Rect(0, 136, 0, 0),
            densityMaxScale = 1.3849792f,
            densityMinScale = 0.85f,
            densityMinScaleInterval = 0.09000003f,
            defaultGrid = "medium",
            secondDisplay =
                EmulatorDisplay(
                    width = 1080,
                    height = 2424,
                    density = 390,
                    cutout = Rect(0, 152, 0, 0),
                ),
            resourceOverrides =
                mapOf(
                    "status_bar_height_portrait" to 88,
                    "status_bar_height" to 88,
                    "navigation_bar_height_landscape" to 59,
                    "navigation_bar_height" to 59,
                    "status_bar_height_landscape" to 88,
                    "navigation_bar_width" to 59,
                ),
            supportsFixedLandscape = false,
        )

    val pixel9proFold_frontDisplay =
        DeviceEmulationData(
            name = "pixel9profold_front",
            width = 1080,
            height = 2424,
            density = 390,
            cutout = Rect(0, 152, 0, 0),
            densityMaxScale = 1.3849792f,
            densityMinScale = 0.85f,
            densityMinScaleInterval = 0.09000003f,
            defaultGrid = "medium",
            secondDisplay =
                EmulatorDisplay(
                    width = 2076,
                    height = 2152,
                    density = 390,
                    cutout = Rect(0, 136, 0, 0),
                ),
            resourceOverrides =
                mapOf(
                    "status_bar_height_portrait" to 161,
                    "status_bar_height" to 161,
                    "navigation_bar_height_landscape" to 59,
                    "navigation_bar_height" to 59,
                    "status_bar_height_landscape" to 59,
                    "navigation_bar_width" to 59,
                ),
            supportsFixedLandscape = false,
        )

    val pixel8proHD =
        DeviceEmulationData(
            name = "p8pro_qhd",
            width = 1344,
            height = 2992,
            density = 480,
            cutout = Rect(0, 151, 0, 0),
            densityMaxScale = 1.5f,
            densityMinScale = 0.85f,
            densityMinScaleInterval = 0.09f,
            defaultGrid = "medium",
            secondDisplay = null,
            resourceOverrides =
                mapOf(
                    "status_bar_height_portrait" to 151,
                    "status_bar_height" to 151,
                    "navigation_bar_height_landscape" to 72,
                    "navigation_bar_height" to 72,
                    "status_bar_height_landscape" to 84,
                    "navigation_bar_width" to 72,
                ),
            supportsFixedLandscape = true,
        )

    val pixel8proFHD =
        DeviceEmulationData(
            name = "p8pro_fhd",
            width = 1008,
            height = 2244,
            density = 360,
            cutout = Rect(0, 113, 0, 0),
            densityMaxScale = 1.5f,
            densityMinScale = 0.85f,
            densityMinScaleInterval = 0.09f,
            defaultGrid = "medium",
            secondDisplay = null,
            resourceOverrides =
                mapOf(
                    "status_bar_height_portrait" to 151,
                    "status_bar_height" to 151,
                    "navigation_bar_height_landscape" to 54,
                    "navigation_bar_height" to 54,
                    "status_bar_height_landscape" to 63,
                    "navigation_bar_width" to 54,
                ),
            supportsFixedLandscape = true,
        )

    val pixel8 =
        DeviceEmulationData(
            name = "p8",
            width = 1080,
            height = 2400,
            density = 420,
            cutout = Rect(0, 132, 0, 0),
            densityMaxScale = 1.5f,
            densityMinScale = 0.85f,
            densityMinScaleInterval = 0.09f,
            defaultGrid = "medium",
            secondDisplay = null,
            resourceOverrides =
                mapOf(
                    "status_bar_height_portrait" to 132,
                    "status_bar_height" to 132,
                    "navigation_bar_height_landscape" to 63,
                    "navigation_bar_height" to 63,
                    "status_bar_height_landscape" to 74,
                    "navigation_bar_width" to 63,
                ),
            supportsFixedLandscape = true,
        )

    val pixel7pro =
        DeviceEmulationData(
            name = "pixel7pro",
            width = 1080,
            height = 2340,
            density = 420,
            cutout = Rect(0, 98, 0, 0),
            densityMaxScale = 1.5f,
            densityMinScale = 0.85f,
            densityMinScaleInterval = 0.09f,
            defaultGrid = "medium",
            secondDisplay = null,
            resourceOverrides =
                mapOf(
                    "status_bar_height_portrait" to 130,
                    "status_bar_height" to 130,
                    "navigation_bar_height_landscape" to 63,
                    "navigation_bar_height" to 63,
                    "status_bar_height_landscape" to 74,
                    "navigation_bar_width" to 63,
                ),
            supportsFixedLandscape = true,
        )

    val pixel6pro =
        DeviceEmulationData(
            name = "pixel6pro",
            width = 1440,
            height = 3120,
            density = 560,
            cutout = Rect(0, 130, 0, 0),
            densityMaxScale = 1.5f,
            densityMinScale = 0.85f,
            densityMinScaleInterval = 0.09f,
            defaultGrid = "medium",
            secondDisplay = null,
            resourceOverrides =
                mapOf(
                    "status_bar_height_portrait" to 130,
                    "status_bar_height" to 130,
                    "navigation_bar_height_landscape" to 84,
                    "navigation_bar_height" to 84,
                    "status_bar_height_landscape" to 98,
                    "navigation_bar_width" to 84,
                ),
            supportsFixedLandscape = true,
        )

    val pixel6 =
        DeviceEmulationData(
            name = "pixel6",
            width = 1080,
            height = 2400,
            density = 420,
            cutout = Rect(0, 118, 0, 0),
            densityMaxScale = 1.5f,
            densityMinScale = 0.85f,
            densityMinScaleInterval = 0.09f,
            defaultGrid = "medium",
            secondDisplay = null,
            resourceOverrides =
                mapOf(
                    "status_bar_height_portrait" to 118,
                    "status_bar_height" to 118,
                    "navigation_bar_height_landscape" to 63,
                    "navigation_bar_height" to 63,
                    "status_bar_height_landscape" to 74,
                    "navigation_bar_width" to 63,
                ),
            supportsFixedLandscape = true,
        )

    val pixelTablet2023 =
        DeviceEmulationData(
            name = "pixelTablet2023",
            width = 2560,
            height = 1600,
            density = 320,
            cutout = Rect(0, 0, 0, 0),
            densityMaxScale = 1.3312378f,
            densityMinScale = 0.85f,
            densityMinScaleInterval = 0.09f,
            defaultGrid = "tablet_normal",
            secondDisplay = null,
            resourceOverrides =
                mapOf(
                    "status_bar_height_portrait" to 104,
                    "status_bar_height" to 104,
                    "navigation_bar_height_landscape" to 48,
                    "navigation_bar_height" to 48,
                    "status_bar_height_landscape" to 104,
                    "navigation_bar_width" to 48,
                ),
            supportsFixedLandscape = false,
        )

    val pixelFold2023 =
        DeviceEmulationData(
            name = "pixelFoldable2023",
            width = 2208,
            height = 1840,
            density = 420,
            cutout = Rect(0, 0, 0, 0),
            densityMaxScale = 1.1679993f,
            densityMinScale = 0.85f,
            densityMinScaleInterval = 0.09f,
            defaultGrid = "medium",
            secondDisplay =
                EmulatorDisplay(
                    width = 1080,
                    height = 2092,
                    density = 420,
                    cutout = Rect(0, 133, 0, 0),
                ),
            resourceOverrides =
                mapOf(
                    "status_bar_height_portrait" to 110,
                    "status_bar_height" to 110,
                    "navigation_bar_height_landscape" to 63,
                    "navigation_bar_height" to 63,
                    "status_bar_height_landscape" to 110,
                    "navigation_bar_width" to 63,
                ),
            supportsFixedLandscape = false,
        )

    val pixelFold2023_frontDisplay =
        DeviceEmulationData(
            name = "pixelFoldable2023_frontDisplay",
            width = 1080,
            height = 2092,
            density = 420,
            cutout = Rect(0, 133, 0, 0),
            densityMaxScale = 1.1679993f,
            densityMinScale = 0.85f,
            densityMinScaleInterval = 0.09f,
            defaultGrid = "medium",
            secondDisplay =
                EmulatorDisplay(
                    width = 2208,
                    height = 1840,
                    density = 420,
                    cutout = Rect(0, 0, 0, 0),
                ),
            resourceOverrides =
                mapOf(
                    "status_bar_height_portrait" to 133,
                    "status_bar_height" to 133,
                    "navigation_bar_height_landscape" to 63,
                    "navigation_bar_height" to 63,
                    "status_bar_height_landscape" to 110,
                    "navigation_bar_width" to 63,
                ),
            supportsFixedLandscape = false,
        )

    val pixel5 =
        DeviceEmulationData(
            name = "pixel5",
            width = 1080,
            height = 2340,
            density = 440,
            cutout = Rect(0, 136, 0, 0),
            densityMaxScale = 1.5f,
            densityMinScale = 0.85f,
            densityMinScaleInterval = 0.09f,
            defaultGrid = "medium",
            secondDisplay = null,
            resourceOverrides =
                mapOf(
                    "status_bar_height_portrait" to 136,
                    "status_bar_height" to 136,
                    "navigation_bar_height_landscape" to 66,
                    "navigation_bar_height" to 66,
                    "status_bar_height_landscape" to 77,
                    "navigation_bar_width" to 66,
                ),
            supportsFixedLandscape = true,
        )

    // This is the list of devices that are currently under development, we keep the other list in
    // case special tests are needed for other devices
    val CURRENT_DEVICES =
        listOf(
            pixel9pro,
            pixel9proFold,
            pixel9proFold_frontDisplay,
            pixel8proHD,
            pixel8proFHD,
            pixel8,
            pixel7pro,
            pixelTablet2023,
            pixelFold2023,
            pixelFold2023_frontDisplay,
        )

    val ALL_DEVICES =
        listOf(
            pixel9pro,
            pixel9proFold,
            pixel9proFold_frontDisplay,
            pixel8proHD,
            pixel8proFHD,
            pixel8,
            pixel7pro,
            pixel6pro,
            pixelTablet2023,
            pixelFold2023,
            pixelFold2023_frontDisplay,
            pixel6,
            pixel5,
        )
}
