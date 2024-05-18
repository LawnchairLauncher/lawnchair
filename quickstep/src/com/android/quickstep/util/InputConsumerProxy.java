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

import android.content.Context;
import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import com.android.quickstep.InputConsumer;
import com.android.quickstep.SimpleOrientationTouchTransformer;
import com.android.systemui.shared.system.InputConsumerController;

import java.util.function.Supplier;

/**
 * Utility class which manages proxying input events from {@link InputConsumerController}
 * to an {@link InputConsumer}
 */
public class InputConsumerProxy {

    private static final String TAG = "InputConsumerProxy";

    private final Context mContext;
    private final Supplier<Integer> mRotationSupplier;
    private final InputConsumerController mInputConsumerController;

    /** Called if a new InputConsumer is created via touch down event. */
    private @Nullable Runnable mOnTouchDownCallback;

    private Supplier<InputConsumer> mConsumerSupplier;

    // The consumer is created lazily on demand.
    private InputConsumer mInputConsumer;

    private boolean mDestroyed = false;
    private boolean mTouchInProgress = false;
    private boolean mDestroyPending = false;

    public InputConsumerProxy(Context context, Supplier<Integer> rotationSupplier,
            InputConsumerController inputConsumerController,
            Runnable onTouchDownCallback, Supplier<InputConsumer> consumerSupplier) {
        mContext = context;
        mRotationSupplier = rotationSupplier;
        mInputConsumerController = inputConsumerController;
        mOnTouchDownCallback = onTouchDownCallback;
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
            MotionEvent event = (MotionEvent) ev;
            int action = event.getActionMasked();
            boolean isHoverEvent = action == MotionEvent.ACTION_HOVER_ENTER
                    || action == MotionEvent.ACTION_HOVER_MOVE
                    || action == MotionEvent.ACTION_HOVER_EXIT;
            if (isHoverEvent) {
                onInputConsumerHoverEvent(event);
            } else {
                onInputConsumerMotionEvent(event);
            }
        } else if (ev instanceof KeyEvent) {
            initInputConsumerIfNeeded(/* isFromTouchDown= */ false);
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
            initInputConsumerIfNeeded(/* isFromTouchDown= */ true);
        } else if (action == ACTION_CANCEL || action == ACTION_UP) {
            // Finish any pending actions
            mTouchInProgress = false;
            if (mDestroyPending) {
                destroy();
            }
        }
        if (mInputConsumer != null) {
            SimpleOrientationTouchTransformer.INSTANCE.get(mContext).transform(ev,
                    mRotationSupplier.get());
            mInputConsumer.onMotionEvent(ev);
        }

        return true;
    }

    private void onInputConsumerHoverEvent(MotionEvent ev) {
        initInputConsumerIfNeeded(/* isFromTouchDown= */ false);
        if (mInputConsumer != null) {
            SimpleOrientationTouchTransformer.INSTANCE.get(mContext).transform(ev,
                    mRotationSupplier.get());
            mInputConsumer.onHoverEvent(ev);
        }
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

    /** Sets mOnToudhCownCallback = null. */
    public void unregisterOnTouchDownCallback() {
        mOnTouchDownCallback = null;
    }

    private void initInputConsumerIfNeeded(boolean isFromTouchDown) {
        if (mInputConsumer == null) {
            if (isFromTouchDown && mOnTouchDownCallback != null) {
                mOnTouchDownCallback.run();
            }
            mInputConsumer = mConsumerSupplier.get();
            mConsumerSupplier = null;
        }
    }
}
