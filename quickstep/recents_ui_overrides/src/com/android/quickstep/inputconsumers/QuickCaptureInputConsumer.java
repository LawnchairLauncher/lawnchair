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
package com.android.quickstep.inputconsumers;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;

import static com.android.launcher3.Utilities.squaredHypot;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.R;
import com.android.quickstep.ActivityControlHelper;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.system.InputMonitorCompat;

/**
 * Input consumer for handling events to launch quick capture from launcher
 * @param <T> Draggable activity subclass used by RecentsView
 */
public class QuickCaptureInputConsumer<T extends BaseDraggingActivity>
        extends DelegateInputConsumer {

    private static final String TAG = "QuickCaptureInputConsumer";

    private static final String QUICK_CAPTURE_PACKAGE = "com.google.auxe.compose";
    private static final String QUICK_CAPTURE_PACKAGE_DEV = "com.google.auxe.compose.debug";

    private static final String EXTRA_DEVICE_STATE = "deviceState";
    private static final String DEVICE_STATE_LOCKED = "Locked";
    private static final String DEVICE_STATE_LAUNCHER = "Launcher";
    private static final String DEVICE_STATE_APP = "App";
    private static final String DEVICE_STATE_UNKNOWN = "Unknown";

    private static final int ANGLE_THRESHOLD = 35; // Degrees

    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();
    private final PointF mStartDragPos = new PointF();

    private int mActivePointerId = -1;
    private boolean mPassedSlop = false;

    private final float mSquaredSlop;

    private Context mContext;

    private RecentsView mRecentsView;

    public QuickCaptureInputConsumer(Context context, InputConsumer delegate,
            InputMonitorCompat inputMonitor, ActivityControlHelper<T> activityControlHelper) {
        super(delegate, inputMonitor);
        mContext = context;

        float slop = ViewConfiguration.get(context).getScaledTouchSlop();
        mSquaredSlop = slop * slop;

        activityControlHelper.createActivityInitListener(this::onActivityInit).register();
    }

    @Override
    public int getType() {
        return TYPE_QUICK_CAPTURE | mDelegate.getType();
    }

    private boolean onActivityInit(final T activity, Boolean alreadyOnHome) {
        mRecentsView = activity.getOverviewPanel();

        return true;
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case ACTION_DOWN: {
                mActivePointerId = ev.getPointerId(0);
                mDownPos.set(ev.getX(), ev.getY());
                mLastPos.set(mDownPos);

                break;
            }
            case ACTION_POINTER_DOWN: {
                if (mState != STATE_ACTIVE) {
                    mState = STATE_DELEGATE_ACTIVE;
                }
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
                if (mState == STATE_DELEGATE_ACTIVE) {
                    break;
                }
                if (!mDelegate.allowInterceptByParent()) {
                    mState = STATE_DELEGATE_ACTIVE;
                    break;
                }
                int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex == -1) {
                    break;
                }
                mLastPos.set(ev.getX(pointerIndex), ev.getY(pointerIndex));

                if (!mPassedSlop) {
                    // Normal gesture, ensure we pass the slop before we start tracking the gesture
                    if (squaredHypot(mLastPos.x - mDownPos.x, mLastPos.y - mDownPos.y)
                            > mSquaredSlop) {

                        mPassedSlop = true;
                        mStartDragPos.set(mLastPos.x, mLastPos.y);

                        if (isValidQuickCaptureGesture()) {
                            setActive(ev);
                        } else {
                            mState = STATE_DELEGATE_ACTIVE;
                        }
                    }
                }

                break;
            }
            case ACTION_CANCEL:
            case ACTION_UP:
                if (mState != STATE_DELEGATE_ACTIVE && mPassedSlop) {
                    startQuickCapture();
                }

                mPassedSlop = false;
                mState = STATE_INACTIVE;
                break;
        }

        if (mState != STATE_ACTIVE) {
            mDelegate.onMotionEvent(ev);
        }
    }

    private boolean isValidQuickCaptureGesture() {
        // Make sure there isn't an app to quick switch to on our right
        boolean atRightMostApp = (mRecentsView == null || mRecentsView.getRunningTaskIndex() <= 0);

        // Check if the gesture is within our angle threshold of horizontal
        float deltaY = Math.abs(mLastPos.y - mDownPos.y);
        float deltaX = mDownPos.x - mLastPos.x; // Positive if this is a gesture to the left
        boolean angleInBounds = Math.toDegrees(Math.atan2(deltaY, deltaX)) < ANGLE_THRESHOLD;

        return atRightMostApp && angleInBounds;
    }

    private void startQuickCapture() {
        // Inspect our delegate's type to figure out where the user invoked Compose
        String deviceState = DEVICE_STATE_UNKNOWN;
        int consumerType = mDelegate.getType();
        if (((consumerType & InputConsumer.TYPE_OVERVIEW) > 0)
                || ((consumerType & InputConsumer.TYPE_OVERVIEW_WITHOUT_FOCUS)) > 0) {
            deviceState = DEVICE_STATE_LAUNCHER;
        } else if ((consumerType & InputConsumer.TYPE_OTHER_ACTIVITY) > 0) {
            deviceState = DEVICE_STATE_APP;
        } else if (((consumerType & InputConsumer.TYPE_RESET_GESTURE) > 0)
                || ((consumerType & InputConsumer.TYPE_DEVICE_LOCKED) > 0)) {
            deviceState = DEVICE_STATE_LOCKED;
        }

        // Then launch the app
        PackageManager pm = mContext.getPackageManager();

        Intent qcIntent = pm.getLaunchIntentForPackage(QUICK_CAPTURE_PACKAGE);

        if (qcIntent == null) {
            // If we couldn't find the regular app, try the dev version
            qcIntent = pm.getLaunchIntentForPackage(QUICK_CAPTURE_PACKAGE_DEV);
        }

        if (qcIntent != null) {
            qcIntent.putExtra(EXTRA_DEVICE_STATE, deviceState);

            Bundle options = ActivityOptions.makeCustomAnimation(mContext, R.anim.slide_in_right,
                    0).toBundle();

            mContext.startActivity(qcIntent, options);
        }
    }
}
