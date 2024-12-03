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

package com.android.systemui.shared.hardware

import android.hardware.input.InputManager
import android.view.InputDevice

/**
 * Gets information about all input devices in the system and returns as a lazy [Sequence].
 *
 * For performance reasons, it is preferred to operate atop the returned [Sequence] to ensure each
 * operation is executed on an element-per-element basis yet customizable.
 *
 * For example:
 * ```kotlin
 * val stylusDevices = inputManager.getInputDeviceSequence().filter {
 *   it.supportsSource(InputDevice.SOURCE_STYLUS)
 * }
 *
 * val hasInternalStylus = stylusDevices.any { it.isInternal }
 * val hasExternalStylus = stylusDevices.any { !it.isInternal }
 * ```
 *
 * @return a [Sequence] of [InputDevice].
 */
fun InputManager.getInputDeviceSequence(): Sequence<InputDevice> =
    inputDeviceIds.asSequence().mapNotNull { getInputDevice(it) }

/**
 * Returns the first [InputDevice] matching the given predicate, or null if no such [InputDevice]
 * was found.
 */
fun InputManager.findInputDevice(predicate: (InputDevice) -> Boolean): InputDevice? =
    getInputDeviceSequence().find { predicate(it) }

/**
 * Returns true if [any] [InputDevice] matches with [predicate].
 *
 * For example:
 * ```kotlin
 * val hasStylusSupport = inputManager.hasInputDevice { it.isStylusSupport() }
 * val hasStylusPen = inputManager.hasInputDevice { it.isStylusPen() }
 * ```
 */
fun InputManager.hasInputDevice(predicate: (InputDevice) -> Boolean): Boolean =
    getInputDeviceSequence().any { predicate(it) }

/** Returns true if host device has any [InputDevice] where [InputDevice.isInternalStylusSource]. */
fun InputManager.hasInternalStylusSource(): Boolean = hasInputDevice { it.isInternalStylusSource }

/** Returns true if host device has any [InputDevice] where [InputDevice.isExternalStylusSource]. */
fun InputManager.hasExternalStylusSource(): Boolean = hasInputDevice { it.isExternalStylusSource }

/** Returns true if host device has any [InputDevice] where [InputDevice.isAnyStylusSource]. */
fun InputManager.hasAnyStylusSource(): Boolean = hasInputDevice { it.isAnyStylusSource }
