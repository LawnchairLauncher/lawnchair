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

import static android.view.MotionEvent.ACTION_MASK;
import static android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.view.Choreographer;
import android.view.InputEvent;
import android.view.MotionEvent;

import com.android.systemui.shared.system.InputChannelCompat;
import com.android.systemui.shared.system.InputChannelCompat.InputEventDispatcher;
import com.android.systemui.shared.system.InputChannelCompat.InputEventReceiver;

import java.util.function.Supplier;

/**
 * Helper class for batching input events
 */
@TargetApi(Build.VERSION_CODES.O)
public class MotionEventQueue {

    private static final String TAG = "MotionEventQueue";

    private static final int ACTION_VIRTUAL = ACTION_MASK - 1;

    private static final int ACTION_QUICK_SCRUB_START =
            ACTION_VIRTUAL | (1 << ACTION_POINTER_INDEX_SHIFT);
    private static final int ACTION_QUICK_SCRUB_PROGRESS =
            ACTION_VIRTUAL | (2 << ACTION_POINTER_INDEX_SHIFT);
    private static final int ACTION_QUICK_SCRUB_END =
            ACTION_VIRTUAL | (3 << ACTION_POINTER_INDEX_SHIFT);
    private static final int ACTION_RESET =
            ACTION_VIRTUAL | (4 << ACTION_POINTER_INDEX_SHIFT);
    private static final int ACTION_SHOW_OVERVIEW_FROM_ALT_TAB =
            ACTION_VIRTUAL | (5 << ACTION_POINTER_INDEX_SHIFT);
    private static final int ACTION_QUICK_STEP =
            ACTION_VIRTUAL | (6 << ACTION_POINTER_INDEX_SHIFT);
    private static final int ACTION_COMMAND =
            ACTION_VIRTUAL | (7 << ACTION_POINTER_INDEX_SHIFT);
    private static final int ACTION_SWITCH_CONSUMER =
            ACTION_VIRTUAL | (8 << ACTION_POINTER_INDEX_SHIFT);

    private final InputEventDispatcher mDispatcher;
    private final InputEventReceiver mReceiver;

    private final Object mConsumerParamsLock = new Object();
    private Supplier[] mConsumerParams = new Supplier[2];

    private TouchConsumer mConsumer;

    public MotionEventQueue(Looper looper, Choreographer choreographer) {
        Pair<InputEventDispatcher, InputEventReceiver> pair = InputChannelCompat.createPair(
                "sysui-callbacks", looper, choreographer, this::onInputEvent);

        mConsumer = TouchConsumer.NO_OP;
        mDispatcher = pair.first;
        mReceiver = pair.second;
    }

    private void onInputEvent(InputEvent ev) {
        if (!(ev instanceof MotionEvent)) {
            throw new IllegalStateException("Unknown event " + ev);
        }
        MotionEvent event = (MotionEvent) ev;
        if (event.getActionMasked() == ACTION_VIRTUAL) {
            switch (event.getAction()) {
                case ACTION_QUICK_SCRUB_START:
                    mConsumer.onQuickScrubStart();
                    break;
                case ACTION_QUICK_SCRUB_PROGRESS:
                    mConsumer.onQuickScrubProgress(event.getX());
                    break;
                case ACTION_QUICK_SCRUB_END:
                    mConsumer.onQuickScrubEnd();
                    break;
                case ACTION_RESET:
                    mConsumer.reset();
                    break;
                case ACTION_SHOW_OVERVIEW_FROM_ALT_TAB:
                    mConsumer.onShowOverviewFromAltTab();
                    mConsumer.onQuickScrubStart();
                    break;
                case ACTION_QUICK_STEP:
                    mConsumer.onQuickStep(event);
                    break;
                case ACTION_COMMAND:
                    mConsumer.onCommand(event.getSource());
                    break;
                case ACTION_SWITCH_CONSUMER:
                    synchronized (mConsumerParamsLock) {
                        int index = event.getSource();
                        mConsumer = (TouchConsumer) mConsumerParams[index].get();
                        mConsumerParams[index] = null;
                    }
                    break;
                default:
                    Log.e(TAG, "Invalid virtual event: " + event.getAction());
            }
        } else {
            mConsumer.accept(event);
        }
    }

    public void queue(MotionEvent event) {
        mDispatcher.dispatch(event);
    }

    private void queueVirtualAction(int action, float param) {
        queue(MotionEvent.obtain(0, 0, action, param, 0, 0));
    }

    private void queueVirtualAction(int action, int param) {
        MotionEvent ev = MotionEvent.obtain(0, 0, action, 0, 0, 0);
        ev.setSource(param);
        queue(ev);
    }

    public void onQuickScrubStart() {
        queueVirtualAction(ACTION_QUICK_SCRUB_START, 0);
    }

    public void onOverviewShownFromAltTab() {
        queueVirtualAction(ACTION_SHOW_OVERVIEW_FROM_ALT_TAB, 0);
    }

    public void onQuickScrubProgress(float progress) {
        queueVirtualAction(ACTION_QUICK_SCRUB_PROGRESS, progress);
    }

    public void onQuickScrubEnd() {
        queueVirtualAction(ACTION_QUICK_SCRUB_END, 0);
    }

    public void onQuickStep(MotionEvent event) {
        event.setAction(ACTION_QUICK_STEP);
        queue(event);
    }

    public void reset() {
        queueVirtualAction(ACTION_RESET, 0);
    }

    public void onCommand(int command) {
        queueVirtualAction(ACTION_COMMAND, command);
    }

    public void switchConsumer(Supplier<TouchConsumer> consumer) {
        int index = -1;
        synchronized (mConsumerParamsLock) {
            // Find a null index
            for (int i = 0; i < mConsumerParams.length; i++) {
                if (mConsumerParams[i] == null) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                index = mConsumerParams.length;
                final Supplier[] newValues = new Supplier[index + 1];
                System.arraycopy(mConsumerParams, 0, newValues, 0, index);
                mConsumerParams = newValues;
            }
            mConsumerParams[index] = consumer;
        }
        queueVirtualAction(ACTION_SWITCH_CONSUMER, index);
    }

    public TouchConsumer getConsumer() {
        return mConsumer;
    }

    public void dispose() {
        mDispatcher.dispose();
        mReceiver.dispose();
    }
}
