/*
 * Copyright 2026, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package app.lawnchair.gestures

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.android.launcher3.PagedView
import app.lawnchair.preferences.PreferenceManager
import kotlin.math.abs

/**
 * Pages the workspace once for each deliberate left/right gyroscope flick while dragging.
 */
class PanToMoveController(
    context: Context,
    private val pager: PagedView<*>,
) : SensorEventListener {

    private val preferences = PreferenceManager.getInstance(context)
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val sensor = gyroscope ?: accelerometer

    private var filteredRate = 0f
    private var baselineTilt: Float? = null
    private var lastPageChangeMs = 0L

    val isSupported: Boolean
        get() = sensor != null && sensorManager != null

    fun onDragStarted() {
        if (!preferences.panToMoveEnabled.get()) return
        val manager = sensorManager ?: return
        val sensor = sensor ?: return

        filteredRate = 0f
        baselineTilt = null
        lastPageChangeMs = 0L
        manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun onDragEnded() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!preferences.panToMoveEnabled.get()) {
            onDragEnded()
            return
        }

        val isGyroscope = event.sensor.type == Sensor.TYPE_GYROSCOPE
        val sensitivity = preferences.panToMoveSensitivity.get().coerceIn(0f, 1f)
        if (isGyroscope) {
            // Gyroscope values are angular velocity. A flick is brief, so use a responsive
            // filter rather than smoothing it over several sensor frames.
            filteredRate = FILTER_WEIGHT * filteredRate + (1f - FILTER_WEIGHT) * event.values[1]
        } else {
            // Accelerometer fallback for devices without a gyroscope. Calibrate the neutral
            // position at drag start, then detect a deliberate change in tilt.
            val tilt = event.values[0] / SensorManager.GRAVITY_EARTH
            if (baselineTilt == null) {
                baselineTilt = tilt
                filteredRate = 0f
                return
            }
            filteredRate = FILTER_WEIGHT * filteredRate + (1f - FILTER_WEIGHT) * (tilt - baselineTilt!!)
        }
        val threshold = lerp(
            if (isGyroscope) MAX_RATE_THRESHOLD else MAX_TILT_THRESHOLD,
            if (isGyroscope) MIN_RATE_THRESHOLD else MIN_TILT_THRESHOLD,
            sensitivity,
        )
        if (abs(filteredRate) < threshold) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastPageChangeMs < PAGE_CHANGE_COOLDOWN_MS) return

        val moved = if (filteredRate > 0f) {
            pager.scrollLeft()
        } else {
            pager.scrollRight()
        }
        if (moved) {
            lastPageChangeMs = now
            filteredRate = 0f
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }

    private companion object {
        const val FILTER_WEIGHT = 0.35f
        const val MAX_RATE_THRESHOLD = 1.5f
        const val MIN_RATE_THRESHOLD = 0.35f
        const val MAX_TILT_THRESHOLD = 0.5f
        const val MIN_TILT_THRESHOLD = 0.2f
        const val PAGE_CHANGE_COOLDOWN_MS = 700L
    }
}
