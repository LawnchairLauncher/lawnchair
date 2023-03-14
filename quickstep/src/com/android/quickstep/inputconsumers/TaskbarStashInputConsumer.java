/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.view.MotionEvent.INVALID_POINTER_ID;

import static com.android.launcher3.Utilities.squaredHypot;
import static com.android.launcher3.config.FeatureFlags.ENABLE_TASKBAR_REVISED_THRESHOLDS;
import static com.android.launcher3.taskbar.TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_TOUCHING;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.PointF;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarTranslationController.TransitionCallback;
import com.android.launcher3.touch.OverScroll;
import com.android.launcher3.util.DisplayController;
import com.android.quickstep.InputConsumer;
import com.android.systemui.shared.system.InputMonitorCompat;

/**
 * Listens for a long press, and cancels the current gesture if that causes Taskbar to be unstashed.
 */
public class TaskbarStashInputConsumer extends DelegateInputConsumer {

    private final TaskbarActivityContext mTaskbarActivityContext;
    private final GestureDetector mLongPressDetector;
    private final float mSquaredTouchSlop;

    private float mLongPressDownX, mLongPressDownY;
    private boolean mCanceledUnstashHint;
    private final float mUnstashArea;
    private final float mScreenWidth;

    private final int mTaskbarNavThresholdY;
    private final boolean mIsTaskbarAllAppsOpen;
    private boolean mHasPassedTaskbarNavThreshold;

    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();
    private int mActivePointerId = INVALID_POINTER_ID;

    private final boolean mIsTransientTaskbar;

    private final @Nullable TransitionCallback mTransitionCallback;

    public TaskbarStashInputConsumer(Context context, InputConsumer delegate,
            InputMonitorCompat inputMonitor, TaskbarActivityContext taskbarActivityContext) {
        super(delegate, inputMonitor);
        mTaskbarActivityContext = taskbarActivityContext;
        mSquaredTouchSlop = Utilities.squaredTouchSlop(context);
        mScreenWidth = taskbarActivityContext.getDeviceProfile().widthPx;

        Resources res = context.getResources();
        mUnstashArea = res.getDimensionPixelSize(R.dimen.taskbar_unstash_input_area);
        int taskbarNavThreshold = res.getDimensionPixelSize(ENABLE_TASKBAR_REVISED_THRESHOLDS.get()
                ? R.dimen.taskbar_nav_threshold_v2
                : R.dimen.taskbar_nav_threshold);
        int screenHeight = taskbarActivityContext.getDeviceProfile().heightPx;
        mTaskbarNavThresholdY = screenHeight - taskbarNavThreshold;
        mIsTaskbarAllAppsOpen =
                mTaskbarActivityContext != null && mTaskbarActivityContext.isTaskbarAllAppsOpen();

        mIsTransientTaskbar = DisplayController.isTransientTaskbar(context);

        mLongPressDetector = new GestureDetector(context, new SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent motionEvent) {
                onLongPressDetected(motionEvent);
            }
        });

        mTransitionCallback = mIsTransientTaskbar
                ? taskbarActivityContext.getTranslationCallbacks()
                : null;
    }

    @Override
    public int getType() {
        return TYPE_TASKBAR_STASH | mDelegate.getType();
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        mLongPressDetector.onTouchEvent(ev);
        if (mState != STATE_ACTIVE) {
            mDelegate.onMotionEvent(ev);

            if (mTaskbarActivityContext != null) {
                final float x = ev.getRawX();
                final float y = ev.getRawY();
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mActivePointerId = ev.getPointerId(0);
                        mDownPos.set(ev.getX(), ev.getY());
                        mLastPos.set(mDownPos);

                        mHasPassedTaskbarNavThreshold = false;
                        mTaskbarActivityContext.setAutohideSuspendFlag(
                                FLAG_AUTOHIDE_SUSPEND_TOUCHING, true);
                        if (isInArea(x)) {
                            if (!mIsTransientTaskbar) {
                                mLongPressDownX = x;
                                mLongPressDownY = y;
                                mTaskbarActivityContext.startTaskbarUnstashHint(
                                        /* animateForward = */ true);
                                mCanceledUnstashHint = false;
                            }
                        }
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
                        if (!mIsTransientTaskbar
                                && !mCanceledUnstashHint
                                && squaredHypot(mLongPressDownX - x, mLongPressDownY - y)
                                > mSquaredTouchSlop) {
                            mTaskbarActivityContext.startTaskbarUnstashHint(
                                    /* animateForward = */ false);
                            mCanceledUnstashHint = true;
                        }

                        int pointerIndex = ev.findPointerIndex(mActivePointerId);
                        if (pointerIndex == INVALID_POINTER_ID) {
                            break;
                        }
                        mLastPos.set(ev.getX(pointerIndex), ev.getY(pointerIndex));

                        if (mIsTransientTaskbar) {
                            float dY = mLastPos.y - mDownPos.y;
                            boolean passedTaskbarNavThreshold = dY < 0
                                    && mLastPos.y < mTaskbarNavThresholdY;

                            if (!mHasPassedTaskbarNavThreshold && passedTaskbarNavThreshold) {
                                mHasPassedTaskbarNavThreshold = true;
                                mTaskbarActivityContext.onSwipeToUnstashTaskbar();
                            }

                            if (dY < 0) {
                                dY = -OverScroll.dampedScroll(-dY, mTaskbarNavThresholdY);
                                if (mTransitionCallback != null && !mIsTaskbarAllAppsOpen) {
                                    mTransitionCallback.onActionMove(dY);
                                }
                            }
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!mIsTransientTaskbar && !mCanceledUnstashHint) {
                            mTaskbarActivityContext.startTaskbarUnstashHint(
                                    /* animateForward = */ false);
                        }
                        mTaskbarActivityContext.setAutohideSuspendFlag(
                                FLAG_AUTOHIDE_SUSPEND_TOUCHING, false);
                        if (mTransitionCallback != null) {
                            mTransitionCallback.onActionEnd();
                        }
                        mHasPassedTaskbarNavThreshold = false;
                        break;
                }
            }
        }
    }

    private boolean isInArea(float x) {
        float areaFromMiddle = mUnstashArea / 2.0f;
        float distFromMiddle = Math.abs(mScreenWidth / 2.0f - x);
        return distFromMiddle < areaFromMiddle;
    }

    private void onLongPressDetected(MotionEvent motionEvent) {
        if (mTaskbarActivityContext != null
                && isInArea(motionEvent.getRawX())
                && !mIsTransientTaskbar) {
            boolean taskBarPressed = mTaskbarActivityContext.onLongPressToUnstashTaskbar();
            if (taskBarPressed) {
                setActive(motionEvent);
            }
        }
    }
}
