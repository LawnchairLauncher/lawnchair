/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.quickstep.util;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_UP;

import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.android.quickstep.InputConsumer;
import com.android.systemui.shared.system.InputConsumerController;

import java.util.function.Supplier;

/**
 * Utility class which manages proxying input events from {@link InputConsumerController}
 * to an {@link InputConsumer}
 */
public class InputConsumerProxy {

    private static final String TAG = "InputConsumerProxy";

    private final InputConsumerController mInputConsumerController;
    private Runnable mCallback;
    private Supplier<InputConsumer> mConsumerSupplier;

    // The consumer is created lazily on demand.
    private InputConsumer mInputConsumer;

    private boolean mDestroyed = false;
    private boolean mTouchInProgress = false;
    private boolean mDestroyPending = false;

    public InputConsumerProxy(InputConsumerController inputConsumerController,
            Runnable callback, Supplier<InputConsumer> consumerSupplier) {
        mInputConsumerController = inputConsumerController;
        mCallback = callback;
        mConsumerSupplier = consumerSupplier;
    }

    public void enable() {
        if (mDestroyed) {
            return;
        }
        mInputConsumerController.setInputListener(this::onInputConsumerEvent);
    }

    private boolean onInputConsumerEvent(InputEvent ev) {
        if (ev instanceof MotionEvent) {
            onInputConsumerMotionEvent((MotionEvent) ev);
        } else if (ev instanceof KeyEvent) {
            initInputConsumerIfNeeded();
            mInputConsumer.onKeyEvent((KeyEvent) ev);
            return true;
        }
        return false;
    }

    private boolean onInputConsumerMotionEvent(MotionEvent ev) {
        int action = ev.getAction();

        // Just to be safe, verify that ACTION_DOWN comes before any other action,
        // and ignore any ACTION_DOWN after the first one (though that should not happen).
        if (!mTouchInProgress && action != ACTION_DOWN) {
            Log.w(TAG, "Received non-down motion before down motion: " + action);
            return false;
        }
        if (mTouchInProgress && action == ACTION_DOWN) {
            Log.w(TAG, "Received down motion while touch was already in progress");
            return false;
        }

        if (action == ACTION_DOWN) {
            mTouchInProgress = true;
            initInputConsumerIfNeeded();
        } else if (action == ACTION_CANCEL || action == ACTION_UP) {
            // Finish any pending actions
            mTouchInProgress = false;
            if (mDestroyPending) {
                destroy();
            }
        }
        if (mInputConsumer != null) {
            mInputConsumer.onMotionEvent(ev);
        }

        return true;
    }

    public void destroy() {
        if (mTouchInProgress) {
            mDestroyPending = true;
            return;
        }
        mDestroyPending = false;
        mDestroyed = true;
        mInputConsumerController.setInputListener(null);
    }

    public void unregisterCallback() {
        mCallback = null;
    }

    private void initInputConsumerIfNeeded() {
        if (mInputConsumer == null) {
            if (mCallback != null) {
                mCallback.run();
            }
            mInputConsumer = mConsumerSupplier.get();
            mConsumerSupplier = null;
        }
    }
}
