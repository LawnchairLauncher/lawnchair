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

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Trace
import androidx.core.util.Consumer
import com.android.systemui.unfold.dagger.UnfoldSingleThreadBg
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

internal class HingeSensorAngleProvider
@AssistedInject
constructor(
    private val sensorManager: SensorManager,
    @UnfoldSingleThreadBg private val singleThreadBgExecutor: Executor,
    @Assisted private val listenerHandler: Handler,
) : HingeAngleProvider {

    private val sensorListener = HingeAngleSensorListener()
    private val listeners: MutableList<Consumer<Float>> = CopyOnWriteArrayList()
    var started = false

    override fun start() {
        singleThreadBgExecutor.execute {
            if (started) return@execute
            Trace.beginSection("HingeSensorAngleProvider#start")
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
            sensorManager.registerListener(
                sensorListener,
                sensor,
                SensorManager.SENSOR_DELAY_FASTEST,
                listenerHandler
            )
            Trace.endSection()

            started = true
        }
    }

    override fun stop() {
        singleThreadBgExecutor.execute {
            if (!started) return@execute
            sensorManager.unregisterListener(sensorListener)
            started = false
        }
    }

    override fun removeCallback(listener: Consumer<Float>) {
        listeners.remove(listener)
    }

    override fun addCallback(listener: Consumer<Float>) {
        listeners.add(listener)
    }

    private inner class HingeAngleSensorListener : SensorEventListener {

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        override fun onSensorChanged(event: SensorEvent) {
            listeners.forEach { it.accept(event.values[0]) }
        }
    }

    @AssistedFactory
    interface Factory {
        /** Creates an [HingeSensorAngleProvider] that sends updates using [handler]. */
        fun create(handler: Handler): HingeSensorAngleProvider
    }
}
