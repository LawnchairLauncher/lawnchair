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

package com.android.quickstep

import com.android.quickstep.util.DeviceConfigHelper
import com.android.quickstep.util.DeviceConfigHelper.PropReader
import java.io.PrintWriter

/** Various configurations specific to nav-bar functionalities */
class DeviceConfigWrapper private constructor(propReader: PropReader) {

    val customLpnhThresholds =
        propReader.get(
            "CUSTOM_LPNH_THRESHOLDS",
            true,
            "Add dev options and server side control to customize the LPNH trigger slop and milliseconds"
        )

    val customLphThresholds =
        propReader.get(
            "CUSTOM_LPH_THRESHOLDS",
            true,
            "Server side control to customize LPH timeout and touch slop"
        )

    val customLpaaThresholds =
        propReader.get(
            "CUSTOM_LPAA_THRESHOLDS",
            false,
            "Server side control to customize LPAA timeout and touch slop"
        )

    val overrideLpnhLphThresholds =
        propReader.get(
            "OVERRIDE_LPNH_LPH_THRESHOLDS",
            false,
            "Enable AGSA override for LPNH and LPH timeout and touch slop"
        )

    val lpnhTimeoutMs =
        propReader.get("LPNH_TIMEOUT_MS", 450, "Controls lpnh timeout in milliseconds")

    val lpnhSlopPercentage =
        propReader.get("LPNH_SLOP_PERCENTAGE", 100, "Controls touch slop percentage for lpnh")

    val enableLpnhTwoStages =
        propReader.get(
            "ENABLE_LPNH_TWO_STAGES",
            false,
            "Enable two stage for LPNH duration and touch slop"
        )

    val twoStageMultiplier =
        propReader.get(
            "TWO_STAGE_MULTIPLIER",
            2,
            "Extends the duration and touch slop if the initial slop is passed"
        )

    val animateLpnh = propReader.get("ANIMATE_LPNH", false, "Animates navbar when long pressing")

    val shrinkNavHandleOnPress =
        propReader.get(
            "SHRINK_NAV_HANDLE_ON_PRESS",
            false,
            "Shrinks navbar when long pressing if ANIMATE_LPNH is enabled"
        )

    val enableLongPressNavHandle =
        propReader.get(
            "ENABLE_LONG_PRESS_NAV_HANDLE",
            true,
            "Enables long pressing on the bottom bar nav handle to trigger events."
        )

    val enableSearchHapticHint =
        propReader.get(
            "ENABLE_SEARCH_HAPTIC_HINT",
            true,
            "Enables haptic hint while long pressing on the bottom bar nav handle."
        )

    val enableSearchHapticCommit =
        propReader.get(
            "ENABLE_SEARCH_HAPTIC_COMMIT",
            true,
            "Enables haptic hint at end of long pressing on the bottom bar nav handle."
        )

    val lpnhHapticHintStartScalePercent =
        propReader.get("LPNH_HAPTIC_HINT_START_SCALE_PERCENT", 0, "Haptic hint start scale.")

    val lpnhHapticHintEndScalePercent =
        propReader.get("LPNH_HAPTIC_HINT_END_SCALE_PERCENT", 100, "Haptic hint end scale.")

    val lpnhHapticHintScaleExponent =
        propReader.get("LPNH_HAPTIC_HINT_SCALE_EXPONENT", 1, "Haptic hint scale exponent.")

    val lpnhHapticHintIterations =
        propReader.get("LPNH_HAPTIC_HINT_ITERATIONS", 50, "Haptic hint number of iterations.")

    val enableLpnhDeepPress =
        propReader.get(
            "ENABLE_LPNH_DEEP_PRESS",
            true,
            "Long press of nav handle is instantly triggered if deep press is detected."
        )

    val lpnhHapticHintDelay =
        propReader.get("LPNH_HAPTIC_HINT_DELAY", 0, "Delay before haptic hint starts.")

    val lpnhExtraTouchWidthDp =
        propReader.get(
            "LPNH_EXTRA_TOUCH_WIDTH_DP",
            0,
            "Controls extra dp on the nav bar sides to trigger LPNH. Can be negative for a smaller touch region."
        )

    val allAppsOverviewThreshold =
        propReader.get(
            "ALL_APPS_OVERVIEW_THRESHOLD",
            180,
            "Threshold to open All Apps from Overview"
        )

    /** Dump config values. */
    fun dump(prefix: String, writer: PrintWriter) {
        writer.println("$prefix DeviceConfigWrapper:")
        writer.println("$prefix\tcustomLpnhThresholds=$customLpnhThresholds")
        writer.println("$prefix\tcustomLphThresholds=$customLphThresholds")
        writer.println("$prefix\toverrideLpnhLphThresholds=$overrideLpnhLphThresholds")
        writer.println("$prefix\tlpnhSlopPercentage=$lpnhSlopPercentage")
        writer.println("$prefix\tanimateLpnh=$animateLpnh")
        writer.println("$prefix\tshrinkNavHandleOnPress=$shrinkNavHandleOnPress")
        writer.println("$prefix\tlpnhTimeoutMs=$lpnhTimeoutMs")
        writer.println("$prefix\tenableLongPressNavHandle=$enableLongPressNavHandle")
        writer.println("$prefix\tenableSearchHapticHint=$enableSearchHapticHint")
        writer.println("$prefix\tenableSearchHapticCommit=$enableSearchHapticCommit")
        writer.println("$prefix\tlpnhHapticHintStartScalePercent=$lpnhHapticHintStartScalePercent")
        writer.println("$prefix\tlpnhHapticHintEndScalePercent=$lpnhHapticHintEndScalePercent")
        writer.println("$prefix\tlpnhHapticHintScaleExponent=$lpnhHapticHintScaleExponent")
        writer.println("$prefix\tlpnhHapticHintIterations=$lpnhHapticHintIterations")
        writer.println("$prefix\tenableLpnhDeepPress=$enableLpnhDeepPress")
        writer.println("$prefix\tlpnhHapticHintDelay=$lpnhHapticHintDelay")
        writer.println("$prefix\tlpnhExtraTouchWidthDp=$lpnhExtraTouchWidthDp")
        writer.println("$prefix\tallAppsOverviewThreshold=$allAppsOverviewThreshold")
    }

    companion object {
        @JvmStatic val configHelper by lazy { DeviceConfigHelper(::DeviceConfigWrapper) }

        @JvmStatic fun get() = configHelper.config
    }
}
