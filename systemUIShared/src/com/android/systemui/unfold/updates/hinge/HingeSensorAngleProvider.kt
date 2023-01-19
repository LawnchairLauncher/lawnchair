package com.android.systemui.unfold.updates.hinge

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.util.Consumer

internal class HingeSensorAngleProvider(
    private val sensorManager: SensorManager
) : HingeAngleProvider {

    private val sensorListener = HingeAngleSensorListener()
    private val listeners: MutableList<Consumer<Float>> = arrayListOf()

    override fun start() {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
    }

    override fun stop() {
        sensorManager.unregisterListener(sensorListener)
    }

    override fun removeCallback(listener: Consumer<Float>) {
        listeners.remove(listener)
    }

    override fun addCallback(listener: Consumer<Float>) {
        listeners.add(listener)
    }

    private inner class HingeAngleSensorListener : SensorEventListener {

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        }

        override fun onSensorChanged(event: SensorEvent) {
            listeners.forEach { it.accept(event.values[0]) }
        }
    }
}
