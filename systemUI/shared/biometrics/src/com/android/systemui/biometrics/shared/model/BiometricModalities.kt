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
import android.hardware.face.FaceSensorPropertiesInternal
import android.hardware.fingerprint.FingerprintSensorPropertiesInternal

/** The available modalities for an operation. */
data class BiometricModalities(
    val fingerprintProperties: FingerprintSensorPropertiesInternal? = null,
    val faceProperties: FaceSensorPropertiesInternal? = null,
) {
    /** If there are no available modalities. */
    val isEmpty: Boolean
        get() = !hasFingerprint && !hasFace

    /** If fingerprint authentication is available (and [fingerprintProperties] is non-null). */
    val hasFingerprint: Boolean
        get() = fingerprintProperties != null

    /** If SFPS authentication is available. */
    val hasSfps: Boolean
        get() = hasFingerprint && fingerprintProperties!!.isAnySidefpsType

    /** If UDFPS authentication is available. */
    val hasUdfps: Boolean
        get() = hasFingerprint && fingerprintProperties!!.isAnyUdfpsType

    /** If fingerprint authentication is available (and [faceProperties] is non-null). */
    val hasFace: Boolean
        get() = faceProperties != null

    /** If only face authentication is enabled. */
    val hasFaceOnly: Boolean
        get() = hasFace && !hasFingerprint

    /** If only fingerprint authentication is enabled. */
    val hasFingerprintOnly: Boolean
        get() = hasFingerprint && !hasFace

    /** If face & fingerprint authentication is enabled (coex). */
    val hasFaceAndFingerprint: Boolean
        get() = hasFingerprint && hasFace

    /** If [hasFace] and it is configured as a STRONG class 3 biometric. */
    val isFaceStrong: Boolean
        get() = faceProperties?.sensorStrength == SensorProperties.STRENGTH_STRONG
}
