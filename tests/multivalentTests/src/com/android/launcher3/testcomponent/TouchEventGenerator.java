/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.testcomponent;

import android.graphics.Point;
import android.util.Pair;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to generate MotionEvent event sequences for testing touch gesture detectors.
 */
public class TouchEventGenerator {

    /**
     * Amount of time between two generated events.
     */
    private static final long TIME_INCREMENT_MS = 20L;

    /**
     * Id of the fake device generating the events.
     */
    private static final int DEVICE_ID = 2104;

    /**
     * The fingers currently present on the emulated touch screen.
     */
    private Map<Integer, Point> mFingers;

    /**
     * Initial event time for the current sequence.
     */
    private long mInitialTime;

    /**
     * Time of the last generated event.
     */
    private long mLastEventTime;

    /**
     * Time of the next event.
     */
    private long mTime;

    /**
     * Receives the generated events.
     */
    public interface Listener {

        /**
         * Called when an event was generated.
         */
        void onTouchEvent(MotionEvent event);
    }
    private final Listener mListener;

    public TouchEventGenerator(Listener listener) {
        mListener = listener;
        mFingers = new HashMap<Integer, Point>();
    }

    /**
     * Adds a finger on the touchscreen.
     */
    public TouchEventGenerator put(int id, int x, int y, long ms) {
        checkFingerExistence(id, false);
        boolean isInitialDown = mFingers.isEmpty();
        mFingers.put(id, new Point(x, y));
        int action;
        if (isInitialDown) {
            action = MotionEvent.ACTION_DOWN;
        } else {
            action = MotionEvent.ACTION_POINTER_DOWN;
            // Set the id of the changed pointer.
            action |= id << MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        }
        generateEvent(action, ms);
        return this;
    }

    /**
     * Adds a finger on the touchscreen after advancing default time interval.
     */
    public TouchEventGenerator put(int id, int x, int y) {
        return put(id, x, y, TIME_INCREMENT_MS);
    }

    /**
     * Adjusts the position of a finger for an upcoming move event.
     *
     * @see #move(long ms)
     */
    public TouchEventGenerator position(int id, int x, int y) {
        checkFingerExistence(id, true);
        mFingers.get(id).set(x, y);
        return this;
    }

    /**
     * Commits the finger position changes of {@link #position(int, int, int)} by generating a move
     * event.
     *
     * @see #position(int, int, int)
     */
    public TouchEventGenerator move(long ms) {
        generateEvent(MotionEvent.ACTION_MOVE, ms);
        return this;
    }

    /**
     * Commits the finger position changes of {@link #position(int, int, int)} by generating a move
     * event after advancing the default time interval.
     *
     * @see #position(int, int, int)
     */
    public TouchEventGenerator move() {
        return move(TIME_INCREMENT_MS);
    }

    /**
     * Moves a single finger on the touchscreen.
     */
    public TouchEventGenerator move(int id, int x, int y, long ms) {
        return position(id, x, y).move(ms);
    }

    /**
     * Moves a single finger on the touchscreen after advancing default time interval.
     */
    public TouchEventGenerator move(int id, int x, int y) {
        return move(id, x, y, TIME_INCREMENT_MS);
    }

    /**
     * Removes an existing finger from the touchscreen.
     */
    public TouchEventGenerator lift(int id, long ms) {
        checkFingerExistence(id, true);
        boolean isFinalUp = mFingers.size() == 1;
        int action;
        if (isFinalUp) {
            action = MotionEvent.ACTION_UP;
        } else {
            action = MotionEvent.ACTION_POINTER_UP;
            // Set the id of the changed pointer.
            action |= id << MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        }
        generateEvent(action, ms);
        mFingers.remove(id);
        return this;
    }

    /**
     * Removes a finger from the touchscreen.
     */
    public TouchEventGenerator lift(int id, int x, int y, long ms) {
        checkFingerExistence(id, true);
        mFingers.get(id).set(x, y);
        return lift(id, ms);
    }

    /**
     * Removes an existing finger from the touchscreen after advancing default time interval.
     */
    public TouchEventGenerator lift(int id) {
        return lift(id, TIME_INCREMENT_MS);
    }

    /**
     * Cancels an ongoing sequence.
     */
    public TouchEventGenerator cancel(long ms) {
        generateEvent(MotionEvent.ACTION_CANCEL, ms);
        mFingers.clear();
        return this;
    }

    /**
     * Cancels an ongoing sequence.
     */
    public TouchEventGenerator cancel() {
        return cancel(TIME_INCREMENT_MS);
    }

    private void checkFingerExistence(int id, boolean shouldExist) {
        if (shouldExist != mFingers.containsKey(id)) {
            throw new IllegalArgumentException(
                    shouldExist ? "Finger does not exist" : "Finger already exists");
        }
    }

    private void generateEvent(int action, long ms) {
        mTime = mLastEventTime + ms;
        Pair<PointerProperties[], PointerCoords[]> state = getFingerState();
        MotionEvent event = MotionEvent.obtain(
                mInitialTime,
                mTime,
                action,
                state.first.length,
                state.first,
                state.second,
                0 /* metaState */,
                0 /* buttonState */,
                1.0f /* xPrecision */,
                1.0f /* yPrecision */,
                DEVICE_ID,
                0 /* edgeFlags */,
                InputDevice.SOURCE_TOUCHSCREEN,
                0 /* flags */);
        mListener.onTouchEvent(event);
        if (action == MotionEvent.ACTION_UP) {
            resetTime();
        }
        event.recycle();
        mLastEventTime = mTime;
    }

    /**
     * Returns the description of the fingers' state expected by MotionEvent.
     */
    private Pair<PointerProperties[], PointerCoords[]> getFingerState() {
        int nFingers = mFingers.size();
        PointerProperties[] properties = new PointerProperties[nFingers];
        PointerCoords[] coordinates = new PointerCoords[nFingers];

        int index = 0;
        for (Map.Entry<Integer, Point> entry : mFingers.entrySet()) {
            int id = entry.getKey();
            Point location = entry.getValue();

            PointerProperties property = new PointerProperties();
            property.id = id;
            property.toolType = MotionEvent.TOOL_TYPE_FINGER;
            properties[index] = property;

            PointerCoords coordinate = new PointerCoords();
            coordinate.x = location.x;
            coordinate.y = location.y;
            coordinate.pressure = 1.0f;
            coordinates[index] = coordinate;

            index++;
        }

        return new Pair<MotionEvent.PointerProperties[], MotionEvent.PointerCoords[]>(
                properties, coordinates);
    }

    /**
     * Resets the time references for a new sequence.
     */
    private void resetTime() {
        mInitialTime = 0L;
        mLastEventTime = -1L;
        mTime = 0L;
    }
}
