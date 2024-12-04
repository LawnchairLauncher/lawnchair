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

import android.view.InputDevice

/**
 * Returns true if [InputDevice] is electronic components to allow a user to use an active stylus in
 * the host device or a passive stylus is detected by the host device.
 */
val InputDevice.isInternalStylusSource: Boolean
    get() = isAnyStylusSource && !isExternal

/** Returns true if [InputDevice] is an active stylus. */
val InputDevice.isExternalStylusSource: Boolean
    get() = isAnyStylusSource && isExternal

/**
 * Returns true if [InputDevice] supports any stylus source.
 *
 * @see InputDevice.isInternalStylusSource
 * @see InputDevice.isExternalStylusSource
 */
val InputDevice.isAnyStylusSource: Boolean
    get() = supportsSource(InputDevice.SOURCE_STYLUS)
