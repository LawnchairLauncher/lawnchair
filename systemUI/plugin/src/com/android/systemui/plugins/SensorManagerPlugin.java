/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.systemui.plugins;

import android.hardware.SensorListener;

import com.android.systemui.plugins.annotations.ProvidesInterface;

/**
 * Allows for additional sensors to be retrieved from
 * {@link com.android.systemui.util.sensors.AsyncSensorManager}.
 */
@ProvidesInterface(action = SensorManagerPlugin.ACTION, version = SensorManagerPlugin.VERSION)
public interface SensorManagerPlugin extends Plugin {
    String ACTION = "com.android.systemui.action.PLUGIN_SENSOR_MANAGER";
    int VERSION = 1;

    /**
     * Registers for sensor events. Events will be sent until the listener is unregistered.
     * @param sensor
     * @param listener
     * @see android.hardware.SensorManager#registerListener(SensorListener, int)
     */
    void registerListener(Sensor sensor, SensorEventListener listener);

    /**
     * Unregisters events from the sensor.
     * @param sensor
     * @param listener
     */
    void unregisterListener(Sensor sensor, SensorEventListener listener);

    /**
     * Listener triggered whenever the Sensor has new data.
     */
    interface SensorEventListener {
        void onSensorChanged(SensorEvent event);
    }

    /**
     * Sensor that can be defined in a plugin.
     */
    class Sensor {
        public static final int TYPE_WAKE_LOCK_SCREEN = 1;
        public static final int TYPE_WAKE_DISPLAY = 2;
        public static final int TYPE_SWIPE = 3;
        public static final int TYPE_SKIP_STATUS = 4;

        private int mType;

        public Sensor(int type) {
            mType = type;
        }
        public int getType() {
            return mType;
        }
        public String toString() {
            return "{PluginSensor type=\"" + mType + "\"}";
        }
    }

    /**
     * Event sent by a {@link Sensor}.
     */
    class SensorEvent {
        Sensor mSensor;
        int mVendorType;
        float[] mValues;

        /**
         * Creates a sensor event.
         * @param sensor The type of sensor, e.g. TYPE_WAKE_LOCK_SCREEN
         * @param vendorType The vendor type, which should be unique for each type of sensor,
         *                   e.g. SINGLE_TAP = 1, DOUBLE_TAP = 2, etc.
         */
        public SensorEvent(Sensor sensor, int vendorType) {
            this(sensor, vendorType, null);
        }

        /**
         * Creates a sensor event.
         * @param sensor The type of sensor, e.g. TYPE_WAKE_LOCK_SCREEN
         * @param vendorType The vendor type, which should be unique for each type of sensor,
         *                   e.g. SINGLE_TAP = 1, DOUBLE_TAP = 2, etc.
         * @param values Values captured by the sensor.
         */
        public SensorEvent(Sensor sensor, int vendorType, float[] values) {
            mSensor = sensor;
            mVendorType = vendorType;
            mValues = values;
        }

        public Sensor getSensor() {
            return mSensor;
        }

        public float[] getValues() {
            return mValues;
        }

        public int getVendorType() {
            return mVendorType;
        }
    }
}
