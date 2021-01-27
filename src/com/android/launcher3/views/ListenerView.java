/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.launcher3.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.testing.TestProtocol;

/**
 * An invisible AbstractFloatingView that can run a callback when it is being closed.
 */
public class ListenerView extends AbstractFloatingView {

    private Runnable mCloseListener;

    public ListenerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setVisibility(View.GONE);
    }

    public void setListener(Runnable listener) {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.PAUSE_NOT_DETECTED, "ListenerView setListener lv=" + this
                    + ", listener=" + listener, new Exception());
        }
        mCloseListener = listener;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.PAUSE_NOT_DETECTED, "ListenerView onAttachedToWindow lv=" + this,
                    new Exception());
        }
        mIsOpen = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mIsOpen = false;
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.PAUSE_NOT_DETECTED, "ListenerView onDetachedFromView lv=" + this,
                    new Exception());
        }
    }

    @Override
    protected void handleClose(boolean animate) {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.PAUSE_NOT_DETECTED, "ListenerView handeClose lv=" + this
                    + ", mIsOpen=" + mIsOpen + ", mCloseListener=" + mCloseListener
                    + ", getParent()=" + getParent(), new Exception());
        }
        if (mIsOpen) {
            if (mCloseListener != null) {
                mCloseListener.run();
            } else {
                if (getParent() instanceof ViewGroup) {
                    ((ViewGroup) getParent()).removeView(this);
                }
            }
        }
        mIsOpen = false;
    }

    @Override
    public void logActionCommand(int command) {
        // Users do not interact with FloatingIconView, so there is nothing to log here.
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_LISTENER) != 0;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.PAUSE_NOT_DETECTED, "ListenerView touchEvent lv=" + this
                    + ", ev=" + ev, new Exception());
        }
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            handleClose(false);
        }
        // We want other views to be able to intercept the touch so we return false here.
        return false;
    }

    @Override
    public boolean canInterceptEventsInSystemGestureRegion() {
        return true;
    }
}
