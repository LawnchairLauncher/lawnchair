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

package com.android.quickstep;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.system.NavigationBarCompat;
import com.android.systemui.shared.system.WindowManagerWrapper;
import com.android.launcher3.R;

/**
 * Touch consumer for handling events to launch assistant from launcher
 */
public class AssistantTouchConsumer implements InputConsumer {
    private static final String TAG = "AssistantTouchConsumer";

    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();
    private int mActivePointerId = -1;

    private final int mDisplayRotation;
    private final Rect mStableInsets = new Rect();

    private final float mDragSlop;
    private final float mTouchSlop;
    private final float mThreshold;

    private float mStartDisplacement;
    private boolean mPassedDragSlop;
    private boolean mPassedTouchSlop;
    private long mPassedTouchSlopTime;
    private boolean mLaunchedAssistant;
    private float mLastProgress;

    private final ISystemUiProxy mSysUiProxy;

    public AssistantTouchConsumer(Context context, ISystemUiProxy systemUiProxy) {
        mSysUiProxy = systemUiProxy;

        mDragSlop = NavigationBarCompat.getQuickStepDragSlopPx();
        mTouchSlop = NavigationBarCompat.getQuickStepTouchSlopPx();
        mThreshold = context.getResources().getDimension(R.dimen.gestures_assistant_threshold);

        Display display = context.getSystemService(WindowManager.class).getDefaultDisplay();
        mDisplayRotation = display.getRotation();
        WindowManagerWrapper.getInstance().getStableInsets(mStableInsets);
    }

    @Override
    public int getType() {
        return TYPE_ASSISTANT;
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        // TODO add logging
        switch (ev.getActionMasked()) {
            case ACTION_DOWN: {
                mActivePointerId = ev.getPointerId(0);
                mDownPos.set(ev.getX(), ev.getY());
                mLastPos.set(mDownPos);
                mLastProgress = -1;
                break;
            }
            case ACTION_POINTER_UP: {
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
            }
            case ACTION_MOVE: {
                int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex == -1) {
                    break;
                }
                mLastPos.set(ev.getX(pointerIndex), ev.getY(pointerIndex));
                float displacement = getDisplacement(ev);

                if (!mPassedDragSlop) {
                    // Normal gesture, ensure we pass the drag slop before we start tracking
                    // the gesture
                    if (Math.abs(displacement) > mDragSlop) {
                        mPassedDragSlop = true;
                        mStartDisplacement = displacement;
                        mPassedTouchSlopTime = SystemClock.uptimeMillis();
                    }
                }

                if (!mPassedTouchSlop) {
                    if (Math.hypot(mLastPos.x - mDownPos.x, mLastPos.y - mDownPos.y) >=
                        mTouchSlop) {
                        mPassedTouchSlop = true;
                        if (!mPassedDragSlop) {
                            mPassedDragSlop = true;
                            mStartDisplacement = displacement;
                            mPassedTouchSlopTime = SystemClock.uptimeMillis();
                        }
                    }
                }

                if (mPassedDragSlop) {
                    // Move
                    float distance = mStartDisplacement - displacement;
                    if (distance >= 0) {
                        onAssistantProgress(distance / mThreshold);
                    }
                }
                break;
            }
            case ACTION_CANCEL:
                break;
            case ACTION_UP: {
                if (ev.getEventTime() - mPassedTouchSlopTime < ViewConfiguration.getTapTimeout()) {
                    onAssistantProgress(1);
                }

                break;
            }
        }
    }

    private void onAssistantProgress(float progress) {
        if (mLastProgress == progress) {
            return;
        }
        try {
            mSysUiProxy.onAssistantProgress(Math.max(0, Math.min(1, progress)));
            if (progress >= 1 && !mLaunchedAssistant) {
                mSysUiProxy.startAssistant(new Bundle());
                mLaunchedAssistant = true;
            }
            mLastProgress = progress;
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to notify SysUI to start/send assistant progress: " + progress, e);
        }
    }

    private boolean isNavBarOnRight() {
        return mDisplayRotation == Surface.ROTATION_90 && mStableInsets.right > 0;
    }

    private boolean isNavBarOnLeft() {
        return mDisplayRotation == Surface.ROTATION_270 && mStableInsets.left > 0;
    }

    private float getDisplacement(MotionEvent ev) {
        float eventX = ev.getX();
        float eventY = ev.getY();
        float displacement = eventY - mDownPos.y;
        if (isNavBarOnRight()) {
            displacement = eventX - mDownPos.x;
        } else if (isNavBarOnLeft()) {
            displacement = mDownPos.x - eventX;
        }
        return displacement;
    }

    static boolean withinTouchRegion(Context context, float x) {
        return x > context.getResources().getDisplayMetrics().widthPixels
                - context.getResources().getDimension(R.dimen.gestures_assistant_width);
    }
}
