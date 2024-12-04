/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.plugins.clocks

import com.android.internal.annotations.Keep
import org.json.JSONObject

/** Identifies a clock design */
typealias ClockId = String

data class AodClockBurnInModel(
    val scale: Float,
    val translationX: Float,
    val translationY: Float,
)

/** Tick rates for clocks */
enum class ClockTickRate(val value: Int) {
    PER_MINUTE(2), // Update the clock once per minute.
    PER_SECOND(1), // Update the clock once per second.
    PER_FRAME(0), // Update the clock every second.
}

/** Some data about a clock design */
data class ClockMetadata(
    val clockId: ClockId,
)

/** Render configuration for the full clock. Modifies the way systemUI behaves with this clock. */
data class ClockConfig(
    val id: String,

    /** Localized name of the clock */
    val name: String,

    /** Localized accessibility description for the clock */
    val description: String,

    /** Transition to AOD should move smartspace like large clock instead of small clock */
    val useAlternateSmartspaceAODTransition: Boolean = false,

    /** True if the clock will react to tone changes in the seed color. */
    val isReactiveToTone: Boolean = true,

    /** True if the clock is large frame clock, which will use weather in compose. */
    val useCustomClockScene: Boolean = false,
)

/** Render configuration options for a clock face. Modifies the way SystemUI behaves. */
data class ClockFaceConfig(
    /** Expected interval between calls to onTimeTick. Can always reduce to PER_MINUTE in AOD. */
    val tickRate: ClockTickRate = ClockTickRate.PER_MINUTE,

    /** Call to check whether the clock consumes weather data */
    val hasCustomWeatherDataDisplay: Boolean = false,

    /**
     * Whether this clock has a custom position update animation. If true, the keyguard will call
     * `onPositionUpdated` to notify the clock of a position update animation. If false, a default
     * animation will be used (e.g. a simple translation).
     */
    val hasCustomPositionUpdatedAnimation: Boolean = false,

    /** True if the clock is large frame clock, which will use weatherBlueprint in compose. */
    val useCustomClockScene: Boolean = false,
)

/** Structure for keeping clock-specific settings */
@Keep
data class ClockSettings(
    val clockId: ClockId? = null,
    val seedColor: Int? = null,
) {
    // Exclude metadata from equality checks
    var metadata: JSONObject = JSONObject()

    companion object {
        private val KEY_CLOCK_ID = "clockId"
        private val KEY_SEED_COLOR = "seedColor"
        private val KEY_METADATA = "metadata"

        fun serialize(setting: ClockSettings?): String {
            if (setting == null) {
                return ""
            }

            return JSONObject()
                .put(KEY_CLOCK_ID, setting.clockId)
                .put(KEY_SEED_COLOR, setting.seedColor)
                .put(KEY_METADATA, setting.metadata)
                .toString()
        }

        fun deserialize(jsonStr: String?): ClockSettings? {
            if (jsonStr.isNullOrEmpty()) {
                return null
            }

            val json = JSONObject(jsonStr)
            val result =
                ClockSettings(
                    if (!json.isNull(KEY_CLOCK_ID)) json.getString(KEY_CLOCK_ID) else null,
                    if (!json.isNull(KEY_SEED_COLOR)) json.getInt(KEY_SEED_COLOR) else null
                )
            if (!json.isNull(KEY_METADATA)) {
                result.metadata = json.getJSONObject(KEY_METADATA)
            }
            return result
        }
    }
}
