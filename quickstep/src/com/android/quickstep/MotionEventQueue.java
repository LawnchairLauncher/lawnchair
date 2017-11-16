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
package com.android.quickstep;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_MOVE;

import android.view.Choreographer;
import android.view.MotionEvent;

import com.android.systemui.shared.system.ChoreographerCompat;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Helper class for batching input events
 */
public class MotionEventQueue implements Runnable {

    // We use two arrays and swap the current index when one array is being consumed
    private final EventArray[] mArrays = new EventArray[] {new EventArray(), new EventArray()};
    private int mCurrentIndex = 0;

    private final Choreographer mChoreographer;
    private final Consumer<MotionEvent> mConsumer;

    public MotionEventQueue(Choreographer choreographer, Consumer<MotionEvent> consumer) {
        mChoreographer = choreographer;
        mConsumer = consumer;
    }

    public void queue(MotionEvent event) {
        synchronized (mArrays) {
            EventArray array = mArrays[mCurrentIndex];
            if (array.isEmpty()) {
                ChoreographerCompat.postInputFrame(mChoreographer, this);
            }

            int eventAction = event.getAction();
            if (eventAction == ACTION_MOVE && array.lastEventAction == ACTION_MOVE) {
                // Replace and recycle the last event
                array.set(array.size() - 1, event).recycle();
            } else {
                array.add(event);
                array.lastEventAction = eventAction;
            }
        }
    }

    @Override
    public void run() {
        EventArray array = swapAndGetCurrentArray();
        int size = array.size();
        for (int i = 0; i < size; i++) {
            MotionEvent event = array.get(i);
            mConsumer.accept(event);
            event.recycle();
        }
        array.clear();
        array.lastEventAction = ACTION_CANCEL;
    }

    private EventArray swapAndGetCurrentArray() {
        synchronized (mArrays) {
            EventArray current = mArrays[mCurrentIndex];
            mCurrentIndex = mCurrentIndex ^ 1;
            return current;
        }
    }

    private static class EventArray extends ArrayList<MotionEvent> {

        public int lastEventAction = ACTION_CANCEL;

        public EventArray() {
            super(4);
        }
    }
}
