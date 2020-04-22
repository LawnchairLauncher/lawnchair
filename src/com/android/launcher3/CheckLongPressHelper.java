/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.launcher3;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Utility class to handle tripper long press on a view with custom timeout and stylus event
 */
public class CheckLongPressHelper {

    public static final float DEFAULT_LONG_PRESS_TIMEOUT_FACTOR = 0.75f;

    private final View mView;
    private final View.OnLongClickListener mListener;
    private final float mSlop;

    private float mLongPressTimeoutFactor = DEFAULT_LONG_PRESS_TIMEOUT_FACTOR;

    private boolean mHasPerformedLongPress;

    private Runnable mPendingCheckForLongPress;

    public CheckLongPressHelper(View v) {
        this(v, null);
    }

    public CheckLongPressHelper(View v, View.OnLongClickListener listener) {
        mView = v;
        mListener = listener;
        mSlop = ViewConfiguration.get(mView.getContext()).getScaledTouchSlop();
    }

    /**
     * Handles the touch event on a view
     *
     * @see View#onTouchEvent(MotionEvent)
     */
    public void onTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                // Just in case the previous long press hasn't been cleared, we make sure to
                // start fresh on touch down.
                cancelLongPress();

                postCheckForLongPress();
                if (isStylusButtonPressed(ev)) {
                    triggerLongPress();
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                cancelLongPress();
                break;
            case MotionEvent.ACTION_MOVE:
                if (!Utilities.pointInView(mView, ev.getX(), ev.getY(), mSlop)) {
                    cancelLongPress();
                } else if (mPendingCheckForLongPress != null && isStylusButtonPressed(ev)) {
                    // Only trigger long press if it has not been cancelled before
                    triggerLongPress();
                }
                break;
        }
    }

    /**
     * Overrides the default long press timeout.
     */
    public void setLongPressTimeoutFactor(float longPressTimeoutFactor) {
        mLongPressTimeoutFactor = longPressTimeoutFactor;
    }

    private void postCheckForLongPress() {
        mHasPerformedLongPress = false;

        if (mPendingCheckForLongPress == null) {
            mPendingCheckForLongPress = this::triggerLongPress;
        }
        mView.postDelayed(mPendingCheckForLongPress,
                (long) (ViewConfiguration.getLongPressTimeout() * mLongPressTimeoutFactor));
    }

    /**
     * Cancels any pending long press
     */
    public void cancelLongPress() {
        mHasPerformedLongPress = false;
        clearCallbacks();
    }

    /**
     * Returns true if long press has been performed in the current touch gesture
     */
    public boolean hasPerformedLongPress() {
        return mHasPerformedLongPress;
    }

    private void triggerLongPress() {
        if ((mView.getParent() != null)
                && mView.hasWindowFocus()
                && (!mView.isPressed() || mListener == null)
                && !mHasPerformedLongPress) {
            boolean handled;
            if (mListener != null) {
                handled = mListener.onLongClick(mView);
            } else {
                handled = mView.performLongClick();
            }
            if (handled) {
                mView.setPressed(false);
                mHasPerformedLongPress = true;
            }
            clearCallbacks();
        }
    }

    private void clearCallbacks() {
        if (mPendingCheckForLongPress != null) {
            mView.removeCallbacks(mPendingCheckForLongPress);
            mPendingCheckForLongPress = null;
        }
    }


    /**
     * Identifies if the provided {@link MotionEvent} is a stylus with the primary stylus button
     * pressed.
     *
     * @param event The event to check.
     * @return Whether a stylus button press occurred.
     */
    private static boolean isStylusButtonPressed(MotionEvent event) {
        return event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
                && event.isButtonPressed(MotionEvent.BUTTON_SECONDARY);
    }
}
