/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.quickstep.inputconsumers;

import static android.view.MotionEvent.AXIS_GESTURE_X_OFFSET;
import static android.view.MotionEvent.AXIS_GESTURE_Y_OFFSET;
import static android.view.MotionEvent.INVALID_POINTER_ID;

import static com.android.launcher3.Utilities.getTrackpadMotionEventScale;
import static com.android.launcher3.Utilities.isTrackpadMotionEvent;

import android.content.Context;
import android.graphics.PointF;
import android.view.MotionEvent;

import com.android.quickstep.util.NavBarPosition;

/**
 * A motion event handler that can tracks the states of a gesture, whether it's from on-screen
 * touch or trackpad gesture.
 */
public class MotionEventsHandler {

    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();
    private final int mScale;

    private int mActivePointerId = INVALID_POINTER_ID;

    private float mCurrentTrackpadOffsetX = 0;
    private float mCurrentTrackpadOffsetY = 0;

    public MotionEventsHandler(Context context) {
        mScale = getTrackpadMotionEventScale(context);
    }

    public int getActivePointerId() {
        return mActivePointerId;
    }

    public void onActionDown(MotionEvent ev) {
        mActivePointerId = ev.getPointerId(0);
        if (isTrackpadMotionEvent(ev)) {
            mCurrentTrackpadOffsetX = ev.getAxisValue(AXIS_GESTURE_X_OFFSET);
            mCurrentTrackpadOffsetY = ev.getAxisValue(AXIS_GESTURE_Y_OFFSET);
        } else {
            mDownPos.set(ev.getX(), ev.getY());
            mLastPos.set(mDownPos);
        }
    }

    public void onActionMove(MotionEvent ev) {
        int pointerIndex = ev.findPointerIndex(mActivePointerId);
        if (isTrackpadMotionEvent(ev)) {
            mCurrentTrackpadOffsetX += ev.getAxisValue(AXIS_GESTURE_X_OFFSET, pointerIndex)
                    * mScale;
            mCurrentTrackpadOffsetY += ev.getAxisValue(AXIS_GESTURE_Y_OFFSET, pointerIndex)
                    * mScale;
        } else {
            mLastPos.set(ev.getX(pointerIndex), ev.getY(pointerIndex));
        }
    }

    public void onActionPointerUp(MotionEvent ev) {
        int ptrIdx = ev.getActionIndex();
        int ptrId = ev.getPointerId(ptrIdx);
        if (ptrId == mActivePointerId && !isTrackpadMotionEvent(ev)) {
            final int newPointerIdx = ptrIdx == 0 ? 1 : 0;
            mDownPos.set(
                    ev.getX(newPointerIdx) - (mLastPos.x - mDownPos.x),
                    ev.getY(newPointerIdx) - (mLastPos.y - mDownPos.y));
            mLastPos.set(ev.getX(newPointerIdx), ev.getY(newPointerIdx));
            mActivePointerId = ev.getPointerId(newPointerIdx);
        }
    }

    public float getDisplacement(MotionEvent ev, NavBarPosition mNavBarPosition) {
        if (mNavBarPosition.isRightEdge()) {
            if (isTrackpadMotionEvent(ev)) {
                return mCurrentTrackpadOffsetX;
            }
            return ev.getX() - mDownPos.x;
        } else if (mNavBarPosition.isLeftEdge()) {
            if (isTrackpadMotionEvent(ev)) {
                return -mCurrentTrackpadOffsetX;
            }
            return mDownPos.x - ev.getX();
        } else {
            if (isTrackpadMotionEvent(ev)) {
                return mCurrentTrackpadOffsetY;
            }
            return ev.getY() - mDownPos.y;
        }
    }

    public float getDisplacementX(MotionEvent ev) {
        return isTrackpadMotionEvent(ev) ? mCurrentTrackpadOffsetX : mLastPos.x - mDownPos.x;
    }

    public float getDisplacementY(MotionEvent ev) {
        return isTrackpadMotionEvent(ev) ? mCurrentTrackpadOffsetY : mLastPos.y - mDownPos.y;
    }
}
