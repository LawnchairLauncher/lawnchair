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

import android.hardware.fingerprint.FingerprintSensorProperties

/** Fingerprint sensor types. Represents [FingerprintSensorProperties.SensorType]. */
enum class FingerprintSensorType {
    UNKNOWN,
    REAR,
    UDFPS_ULTRASONIC,
    UDFPS_OPTICAL,
    POWER_BUTTON,
    HOME_BUTTON;

    fun isUdfps(): Boolean {
        return (this == UDFPS_OPTICAL) || (this == UDFPS_ULTRASONIC)
    }

    fun isPowerButton(): Boolean {
        return this == POWER_BUTTON
    }
}

/** Convert [this] to corresponding [FingerprintSensorType] */
fun Int.toSensorType(): FingerprintSensorType =
    when (this) {
        FingerprintSensorProperties.TYPE_UNKNOWN -> FingerprintSensorType.UNKNOWN
        FingerprintSensorProperties.TYPE_REAR -> FingerprintSensorType.REAR
        FingerprintSensorProperties.TYPE_UDFPS_ULTRASONIC -> FingerprintSensorType.UDFPS_ULTRASONIC
        FingerprintSensorProperties.TYPE_UDFPS_OPTICAL -> FingerprintSensorType.UDFPS_OPTICAL
        FingerprintSensorProperties.TYPE_POWER_BUTTON -> FingerprintSensorType.POWER_BUTTON
        FingerprintSensorProperties.TYPE_HOME_BUTTON -> FingerprintSensorType.HOME_BUTTON
        else -> throw IllegalArgumentException("Invalid SensorType value: $this")
    }

/** Convert [this] to corresponding [Int] */
fun FingerprintSensorType.toInt(): Int =
    when (this) {
        FingerprintSensorType.UNKNOWN -> FingerprintSensorProperties.TYPE_UNKNOWN
        FingerprintSensorType.REAR -> FingerprintSensorProperties.TYPE_REAR
        FingerprintSensorType.UDFPS_ULTRASONIC -> FingerprintSensorProperties.TYPE_UDFPS_ULTRASONIC
        FingerprintSensorType.UDFPS_OPTICAL -> FingerprintSensorProperties.TYPE_UDFPS_OPTICAL
        FingerprintSensorType.POWER_BUTTON -> FingerprintSensorProperties.TYPE_POWER_BUTTON
        FingerprintSensorType.HOME_BUTTON -> FingerprintSensorProperties.TYPE_HOME_BUTTON
    }
