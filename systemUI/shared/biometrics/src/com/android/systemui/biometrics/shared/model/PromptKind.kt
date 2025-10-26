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

package com.android.systemui.biometrics.shared.model

sealed interface PromptKind {
    object None : PromptKind

    data class Biometric(
        /** The available modalities for the authentication on the prompt. */
        val activeModalities: BiometricModalities = BiometricModalities(),
        val paneType: PaneType = PaneType.ONE_PANE_PORTRAIT,
    ) : PromptKind {
        enum class PaneType {
            TWO_PANE_LANDSCAPE,
            ONE_PANE_PORTRAIT,
            ONE_PANE_NO_SENSOR_LANDSCAPE,
            ONE_PANE_LARGE_SCREEN_LANDSCAPE
        }
    }

    data object Pin : PromptKind
    data object Pattern : PromptKind
    data object Password : PromptKind

    fun isBiometric() = this is Biometric
    fun isTwoPaneLandscapeBiometric(): Boolean =
        (this as? Biometric)?.paneType == Biometric.PaneType.TWO_PANE_LANDSCAPE
    fun isOnePanePortraitBiometric() =
        (this as? Biometric)?.paneType == Biometric.PaneType.ONE_PANE_PORTRAIT
    fun isOnePaneNoSensorLandscapeBiometric() =
        (this as? Biometric)?.paneType == Biometric.PaneType.ONE_PANE_NO_SENSOR_LANDSCAPE
    fun isOnePaneLargeScreenLandscapeBiometric() =
        (this as? Biometric)?.paneType == Biometric.PaneType.ONE_PANE_LARGE_SCREEN_LANDSCAPE
    fun isCredential() = (this is Pin) || (this is Pattern) || (this is Password)
}
