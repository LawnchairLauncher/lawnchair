/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.launcher3.util;

import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;

/**
 * This class differs from the framework {@link TouchDelegate} in that it transforms the
 * coordinates of the motion event to the provided bounds.
 *
 * You can also modify the bounds post construction. Since the bounds are available during layout,
 * this avoids new object creation during every layout.
 */
public class TransformingTouchDelegate extends TouchDelegate {
    private static final Rect sTempRect = new Rect();

    private final RectF mBounds;

    private final RectF mTouchCheckBounds;
    private float mTouchExtension;
    private boolean mWasTouchOutsideBounds;

    private View mDelegateView;
    private boolean mDelegateTargeted;

    public TransformingTouchDelegate(View delegateView) {
        super(sTempRect, delegateView);

        mDelegateView = delegateView;
        mBounds = new RectF();
        mTouchCheckBounds = new RectF();
    }

    public void setBounds(int left, int top, int right, int bottom) {
        mBounds.set(left, top, right, bottom);
        updateTouchBounds();
    }

    public void extendTouchBounds(float extension) {
        mTouchExtension = extension;
        updateTouchBounds();
    }

    private void updateTouchBounds() {
        mTouchCheckBounds.set(mBounds);
        mTouchCheckBounds.inset(-mTouchExtension, -mTouchExtension);
    }

    public void setDelegateView(View view) {
        mDelegateView = view;
    }

    /**
     * Will forward touch events to the delegate view if the event is within the bounds
     * specified in the constructor.
     *
     * @param event The touch event to forward
     * @return True if the event was forwarded to the delegate, false otherwise.
     */
    public boolean onTouchEvent(MotionEvent event) {
        boolean sendToDelegate = false;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDelegateTargeted = mTouchCheckBounds.contains(event.getX(), event.getY());
                if (mDelegateTargeted) {
                    mWasTouchOutsideBounds = !mBounds.contains(event.getX(), event.getY());
                    sendToDelegate = true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                sendToDelegate = mDelegateTargeted;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                sendToDelegate = mDelegateTargeted;
                mDelegateTargeted = false;
                break;
        }
        boolean handled = false;
        if (sendToDelegate) {
            float x = event.getX();
            float y = event.getY();
            if (mWasTouchOutsideBounds) {
                event.setLocation(mBounds.centerX(), mBounds.centerY());
            } else {
                event.offsetLocation(-mBounds.left, -mBounds.top);
            }
            handled = mDelegateView.dispatchTouchEvent(event);
            event.setLocation(x, y);
        }
        return handled;
    }
}
