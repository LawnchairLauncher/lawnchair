/*
 * Copyright (C) 2021 The Android Open Source Project
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
@file:JvmName("UnfoldTransitionFactory")

package com.android.systemui.unfold

import android.content.Context
import android.hardware.SensorManager
import android.hardware.devicestate.DeviceStateManager
import android.os.Handler
import com.android.systemui.unfold.updates.screen.ScreenStatusProvider
import com.android.systemui.unfold.config.ResourceUnfoldTransitionConfig
import com.android.systemui.unfold.config.UnfoldTransitionConfig
import com.android.systemui.unfold.progress.FixedTimingTransitionProgressProvider
import com.android.systemui.unfold.progress.PhysicsBasedUnfoldTransitionProgressProvider
import com.android.systemui.unfold.util.ScaleAwareTransitionProgressProvider
import com.android.systemui.unfold.updates.DeviceFoldStateProvider
import com.android.systemui.unfold.updates.hinge.EmptyHingeAngleProvider
import com.android.systemui.unfold.updates.hinge.HingeSensorAngleProvider
import java.lang.IllegalStateException
import java.util.concurrent.Executor

fun createUnfoldTransitionProgressProvider(
    context: Context,
    config: UnfoldTransitionConfig,
    screenStatusProvider: ScreenStatusProvider,
    deviceStateManager: DeviceStateManager,
    sensorManager: SensorManager,
    mainHandler: Handler,
    mainExecutor: Executor
): UnfoldTransitionProgressProvider {

    if (!config.isEnabled) {
        throw IllegalStateException("Trying to create " +
            "UnfoldTransitionProgressProvider when the transition is disabled")
    }

    val hingeAngleProvider =
        if (config.isHingeAngleEnabled) {
            HingeSensorAngleProvider(sensorManager)
        } else {
            EmptyHingeAngleProvider()
        }

    val foldStateProvider = DeviceFoldStateProvider(
        context,
        hingeAngleProvider,
        screenStatusProvider,
        deviceStateManager,
        mainExecutor,
        mainHandler
    )

    val unfoldTransitionProgressProvider = if (config.isHingeAngleEnabled) {
        PhysicsBasedUnfoldTransitionProgressProvider(foldStateProvider)
    } else {
        FixedTimingTransitionProgressProvider(foldStateProvider)
    }
    return ScaleAwareTransitionProgressProvider(
            unfoldTransitionProgressProvider,
            context.contentResolver
    )
}

fun createConfig(context: Context): UnfoldTransitionConfig =
    ResourceUnfoldTransitionConfig(context)
