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
package com.android.launcher3.allapps;

import android.content.Context;
import android.graphics.PointF;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.launcher3.touch.SwipeDetector;

import static android.view.MotionEvent.INVALID_POINTER_ID;


public class InterceptingViewPager extends ViewPager {


    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();
    private final int mSlop;
    private int mActivePointerId = INVALID_POINTER_ID;

    public InterceptingViewPager(@NonNull Context context) {
        super(context);
        mSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    public InterceptingViewPager(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean result = super.onInterceptTouchEvent(ev);
        if (!result) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mActivePointerId = ev.getPointerId(0);
                    mDownPos.set(ev.getX(), ev.getY());
                    mLastPos.set(mDownPos);
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    int ptrIdx = ev.getActionIndex();
                    int ptrId = ev.getPointerId(ptrIdx);
                    if (ptrId == mActivePointerId) {
                        final int newPointerIdx = ptrIdx == 0 ? 1 : 0;
                        mDownPos.set(
                                ev.getX(newPointerIdx) - (mLastPos.x - mDownPos.x),
                                ev.getY(newPointerIdx) - (mLastPos.y - mDownPos.y));
                        mLastPos.set(ev.getX(newPointerIdx), ev.getY(newPointerIdx));
                        mActivePointerId = ev.getPointerId(newPointerIdx);
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    int pointerIndex = ev.findPointerIndex(mActivePointerId);
                    if (pointerIndex == INVALID_POINTER_ID) {
                        break;
                    }
                    float deltaX = ev.getX() - mDownPos.x;
                    float deltaY = ev.getY() - mDownPos.y;
                    if (Math.abs(deltaX) > mSlop && Math.abs(deltaX) > Math.abs(deltaY)) {
                        return true;
                    }
                    mLastPos.set(ev.getX(pointerIndex), ev.getY(pointerIndex));
                    break;
                default:
                    break;
            }
        }
        return result;
    }

}