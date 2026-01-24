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
 *
 */

package com.android.systemui.biometrics.shared.model

import android.hardware.biometrics.BiometricConstants

/** Lockout mode. Represents [BiometricConstants.LockoutMode]. */
enum class LockoutMode {
    NONE,
    TIMED,
    PERMANENT,
}

/** Convert [this] to corresponding [LockoutMode] */
fun Int.toLockoutMode(): LockoutMode =
    when (this) {
        BiometricConstants.BIOMETRIC_LOCKOUT_PERMANENT -> LockoutMode.PERMANENT
        BiometricConstants.BIOMETRIC_LOCKOUT_TIMED -> LockoutMode.TIMED
        else -> LockoutMode.NONE
    }
