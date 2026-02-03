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

import android.graphics.Rect
import android.hardware.fingerprint.FingerprintSensorProperties.SensorType
import android.hardware.fingerprint.FingerprintSensorProperties.TYPE_UDFPS_OPTICAL
import android.view.Surface
import android.view.Surface.Rotation

/**
 * Collection of parameters that define an under-display fingerprint sensor (UDFPS) overlay.
 *
 * [sensorBounds] coordinates of the bounding box around the sensor in natural orientation, in
 * pixels, for the current resolution.
 *
 * [overlayBounds] coordinates of the UI overlay in natural orientation, in pixels, for the current
 * resolution.
 *
 * [naturalDisplayWidth] width of the physical display in natural orientation, in pixels, for the
 * current resolution.
 *
 * [naturalDisplayHeight] height of the physical display in natural orientation, in pixels, for the
 * current resolution.
 *
 * [scaleFactor] ratio of a dimension in the current resolution to the corresponding dimension in
 * the native resolution.
 *
 * [rotation] current rotation of the display.
 *
 * [sensorType] fingerprint sensor type
 */
data class UdfpsOverlayParams(
    val sensorBounds: Rect = Rect(),
    val overlayBounds: Rect = Rect(),
    val naturalDisplayWidth: Int = 0,
    val naturalDisplayHeight: Int = 0,
    val scaleFactor: Float = 1f,
    @Rotation val rotation: Int = Surface.ROTATION_0,
    @SensorType val sensorType: Int = TYPE_UDFPS_OPTICAL
) {

    /** Same as [sensorBounds], but in native resolution. */
    val nativeSensorBounds = Rect(sensorBounds).apply { scale(1f / scaleFactor) }

    /** Same as [overlayBounds], but in native resolution. */
    val nativeOverlayBounds = Rect(overlayBounds).apply { scale(1f / scaleFactor) }

    /** See [android.view.DisplayInfo.logicalWidth] */
    val logicalDisplayWidth =
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            naturalDisplayHeight
        } else {
            naturalDisplayWidth
        }

    /** See [android.view.DisplayInfo.logicalHeight] */
    val logicalDisplayHeight =
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            naturalDisplayWidth
        } else {
            naturalDisplayHeight
        }
}
