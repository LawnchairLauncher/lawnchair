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
package com.android.launcher3.touch;

import android.graphics.PointF;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;

import com.android.launcher3.Utilities.Consumer;

/**
 * To minimize the size of the MotionEvent, historic events are not copied and passed via the
 * listener.
 */
public class TouchEventTranslator {

    private static final String TAG = "TouchEventTranslator";
    private static final boolean DEBUG = false;

    private class DownState {
        long timeStamp;
        float downX;
        float downY;
        public DownState(long timeStamp, float downX, float downY) {
            this.timeStamp = timeStamp;
            this.downX = downX;
            this.downY = downY;
        }
    };
    private final DownState ZERO = new DownState(0, 0f, 0f);

    private final Consumer<MotionEvent> mListener;

    private final SparseArray<DownState> mDownEvents;
    private final SparseArray<PointF> mFingers;

    private final SparseArray<Pair<PointerProperties[], PointerCoords[]>> mCache;

    public TouchEventTranslator(Consumer<MotionEvent> listener) {
        mDownEvents = new SparseArray<>();
        mFingers = new SparseArray<>();
        mCache = new SparseArray<>();

        mListener = listener;
    }

    public void reset() {
        mDownEvents.clear();
        mFingers.clear();
    }

    public float getDownX() {
        return mDownEvents.get(0).downX;
    }

    public float getDownY() {
        return mDownEvents.get(0).downY;
    }

    public void setDownParameters(int idx, MotionEvent e) {
        DownState ev = new DownState(e.getEventTime(), e.getX(idx), e.getY(idx));
        mDownEvents.append(idx, ev);
    }

    public void dispatchDownEvents(MotionEvent ev) {
        for(int i = 0; i < ev.getPointerCount() && i < mDownEvents.size(); i++) {
            int pid = ev.getPointerId(i);
            put(pid, i, ev.getX(i), 0, mDownEvents.get(i).timeStamp, ev);
        }
    }

    public void processMotionEvent(MotionEvent ev) {
        if (DEBUG) {
            printSamples(TAG + " processMotionEvent", ev);
        }
        int index = ev.getActionIndex();
        float x = ev.getX(index);
        float y = ev.getY(index) - mDownEvents.get(index, ZERO).downY;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_POINTER_DOWN:
                int pid = ev.getPointerId(index);
                if(mFingers.get(pid, null) != null) {
                    for(int i=0; i < ev.getPointerCount(); i++) {
                        pid = ev.getPointerId(i);
                        position(pid, x, y);
                    }
                    generateEvent(ev.getAction(), ev);
                } else {
                    put(pid, index, x, y, ev);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                for(int i=0; i < ev.getPointerCount(); i++) {
                    pid = ev.getPointerId(i);
                    position(pid, x, y);
                }
                generateEvent(ev.getAction(), ev);
                break;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
                pid = ev.getPointerId(index);
                lift(pid, index, x, y, ev);
                break;
            case MotionEvent.ACTION_CANCEL:
                cancel(ev);
                break;
            default:
                Log.v(TAG, "Didn't process ");
                printSamples(TAG, ev);

        }
    }

    private TouchEventTranslator put(int id, int index, float x, float y, MotionEvent ev) {
        return put(id, index, x, y, ev.getEventTime(), ev);
    }

    private TouchEventTranslator put(int id, int index, float x, float y, long ms, MotionEvent ev) {
        checkFingerExistence(id, false);
        boolean isInitialDown = (mFingers.size() == 0);

        mFingers.put(id, new PointF(x, y));
        int n = mFingers.size();

        if (mCache.get(n) == null) {
            PointerProperties[] properties = new PointerProperties[n];
            PointerCoords[] coords = new PointerCoords[n];
            for (int i = 0; i < n; i++) {
                properties[i] = new PointerProperties();
                coords[i] = new PointerCoords();
            }
            mCache.put(n, new Pair(properties, coords));
        }

        int action;
        if (isInitialDown) {
            action = MotionEvent.ACTION_DOWN;
        } else {
            action = MotionEvent.ACTION_POINTER_DOWN;
            // Set the id of the changed pointer.
            action |= index << MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        }
        generateEvent(action, ms, ev);
        return this;
    }

    public TouchEventTranslator position(int id, float x, float y) {
        checkFingerExistence(id, true);
        mFingers.get(id).set(x, y);
        return this;
    }

    private TouchEventTranslator lift(int id, int index, MotionEvent ev) {
        checkFingerExistence(id, true);
        boolean isFinalUp = (mFingers.size() == 1);
        int action;
        if (isFinalUp) {
            action = MotionEvent.ACTION_UP;
        } else {
            action = MotionEvent.ACTION_POINTER_UP;
            // Set the id of the changed pointer.
            action |= index << MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        }
        generateEvent(action, ev);
        mFingers.remove(id);
        return this;
    }

    private TouchEventTranslator lift(int id, int index, float x, float y, MotionEvent ev) {
        checkFingerExistence(id, true);
        mFingers.get(id).set(x, y);
        return lift(id, index, ev);
    }

    public TouchEventTranslator cancel(MotionEvent ev) {
        generateEvent(MotionEvent.ACTION_CANCEL, ev);
        mFingers.clear();
        return this;
    }

    private void checkFingerExistence(int id, boolean shouldExist) {
        if (shouldExist != (mFingers.get(id, null) != null)) {
            throw new IllegalArgumentException(
                    shouldExist ? "Finger does not exist" : "Finger already exists");
        }
    }


    /**
     * Used to debug MotionEvents being sent/received.
     */
    public void printSamples(String msg, MotionEvent ev) {
        System.out.printf("%s %s", msg, MotionEvent.actionToString(ev.getActionMasked()));
        final int pointerCount = ev.getPointerCount();
        System.out.printf("#%d/%d", ev.getActionIndex(), pointerCount);
        System.out.printf(" t=%d:", ev.getEventTime());
        for (int p = 0; p < pointerCount; p++) {
            System.out.printf("  id=%d: (%f,%f)",
                    ev.getPointerId(p), ev.getX(p), ev.getY(p));
        }
        System.out.println();
    }

    private void generateEvent(int action, MotionEvent ev) {
        generateEvent(action, ev.getEventTime(), ev);
    }

    private void generateEvent(int action, long ms, MotionEvent ev) {
        Pair<PointerProperties[], PointerCoords[]> state = getFingerState();
        MotionEvent event = MotionEvent.obtain(
                mDownEvents.get(0).timeStamp,
                ms,
                action,
                state.first.length,
                state.first,
                state.second,
                ev.getMetaState(),
                ev.getButtonState() /* buttonState */,
                ev.getXPrecision() /* xPrecision */,
                ev.getYPrecision() /* yPrecision */,
                ev.getDeviceId(),
                ev.getEdgeFlags(),
                ev.getSource(),
                ev.getFlags() /* flags */);
        if (DEBUG) {
            printSamples(TAG + " generateEvent", event);
        }
        if (event.getPointerId(event.getActionIndex()) < 0) {
            printSamples(TAG + "generateEvent", event);
            throw new IllegalStateException(event.getActionIndex() + " not found in MotionEvent");
        }
        mListener.accept(event);
        event.recycle();
    }

    /**
     * Returns the description of the fingers' state expected by MotionEvent.
     */
    private Pair<PointerProperties[], PointerCoords[]> getFingerState() {
        int nFingers = mFingers.size();

        Pair<PointerProperties[], PointerCoords[]> result = mCache.get(nFingers);
        PointerProperties[] properties = result.first;
        PointerCoords[] coordinates = result.second;

        int index = 0;
        for (int i = 0; i < mFingers.size(); i++) {
            int id = mFingers.keyAt(i);
            PointF location = mFingers.get(id);

            PointerProperties property = properties[i];
            property.id = id;
            property.toolType = MotionEvent.TOOL_TYPE_FINGER;
            properties[index] = property;

            PointerCoords coordinate = coordinates[i];
            coordinate.x = location.x;
            coordinate.y = location.y;
            coordinate.pressure = 1.0f;
            coordinates[index] = coordinate;

            index++;
        }
        return mCache.get(nFingers);
    }
}
