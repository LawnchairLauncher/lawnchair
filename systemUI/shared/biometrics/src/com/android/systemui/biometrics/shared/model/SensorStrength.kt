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

package com.android.systemui.biometrics.shared.model

import android.hardware.biometrics.SensorProperties

/** Sensor security strength. Represents [SensorProperties.Strength]. */
enum class SensorStrength {
    CONVENIENCE,
    WEAK,
    STRONG,
}

/** Convert [this] to corresponding [SensorStrength] */
fun Int.toSensorStrength(): SensorStrength =
    when (this) {
        SensorProperties.STRENGTH_CONVENIENCE -> SensorStrength.CONVENIENCE
        SensorProperties.STRENGTH_WEAK -> SensorStrength.WEAK
        SensorProperties.STRENGTH_STRONG -> SensorStrength.STRONG
        else -> throw IllegalArgumentException("Invalid SensorStrength value: $this")
    }
/** Convert [SensorStrength] to corresponding [Int] */
fun SensorStrength.toInt(): Int =
    when (this) {
        SensorStrength.CONVENIENCE -> SensorProperties.STRENGTH_CONVENIENCE
        SensorStrength.WEAK -> SensorProperties.STRENGTH_WEAK
        SensorStrength.STRONG -> SensorProperties.STRENGTH_STRONG
    }
