/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.unfold.updates.hinge

import androidx.core.util.Consumer
import com.android.systemui.unfold.util.CallbackController

/**
 * Emits device hinge angle values (angle between two integral parts of the device).
 *
 * The hinge angle could be from 0 to 360 degrees inclusive. For foldable devices usually 0
 * corresponds to fully closed (folded) state and 180 degrees corresponds to fully open (flat)
 * state.
 */
interface HingeAngleProvider : CallbackController<Consumer<Float>> {
    fun start()
    fun stop()
}

const val FULLY_OPEN_DEGREES = 180f
const val FULLY_CLOSED_DEGREES = 0f
