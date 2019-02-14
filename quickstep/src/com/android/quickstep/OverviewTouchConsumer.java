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
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT;
import static android.view.MotionEvent.ACTION_UP;

import static com.android.quickstep.TouchInteractionService.TOUCH_INTERACTION_LOG;
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_RECENTS;

import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.views.BaseDragLayer;
import com.android.systemui.shared.system.ActivityManagerWrapper;

/**
 * Touch consumer for handling touch on the recents/Launcher activity.
 */
public class OverviewTouchConsumer<T extends BaseDraggingActivity>
        implements TouchConsumer {

    private final T mActivity;
    private final BaseDragLayer mTarget;
    private final int[] mLocationOnScreen = new int[2];
    private final PointF mDownPos = new PointF();
    private final int mTouchSlop;

    private final boolean mStartingInActivityBounds;

    private boolean mTrackingStarted = false;
    private boolean mInvalidated = false;

    OverviewTouchConsumer(T activity, boolean startingInActivityBounds) {
        mActivity = activity;
        mTarget = activity.getDragLayer();
        mTouchSlop = ViewConfiguration.get(mActivity).getScaledTouchSlop();
        mStartingInActivityBounds = startingInActivityBounds;
    }

    @Override
    public void accept(MotionEvent ev) {
        if (mInvalidated) {
            return;
        }
        int action = ev.getActionMasked();
        if (action == ACTION_DOWN) {
            if (mStartingInActivityBounds) {
                startTouchTracking(ev, false /* updateLocationOffset */,
                        false /* closeActiveWindows */);
                return;
            }
            mTrackingStarted = false;
            mDownPos.set(ev.getX(), ev.getY());
        } else if (!mTrackingStarted) {
            switch (action) {
                case ACTION_CANCEL:
                case ACTION_UP:
                    startTouchTracking(ev, true /* updateLocationOffset */,
                            false /* closeActiveWindows */);
                    break;
                case ACTION_MOVE: {
                    float displacement = mActivity.getDeviceProfile().isLandscape ?
                            ev.getX() - mDownPos.x : ev.getY() - mDownPos.y;
                    if (Math.abs(displacement) >= mTouchSlop) {
                        // Start tracking only when mTouchSlop is crossed.
                        startTouchTracking(ev, true /* updateLocationOffset */,
                                true /* closeActiveWindows */);
                    }
                }
            }
        }

        if (mTrackingStarted) {
            sendEvent(ev);
        }

        if (action == ACTION_UP || action == ACTION_CANCEL) {
            mInvalidated = true;
        }
    }

    private void startTouchTracking(MotionEvent ev, boolean updateLocationOffset,
            boolean closeActiveWindows) {
        if (updateLocationOffset) {
            mTarget.getLocationOnScreen(mLocationOnScreen);
        }

        // Send down touch event
        MotionEvent down = MotionEvent.obtainNoHistory(ev);
        down.setAction(ACTION_DOWN);
        sendEvent(down);

        mTrackingStarted = true;
        // Send pointer down for remaining pointers.
        int pointerCount = ev.getPointerCount();
        for (int i = 1; i < pointerCount; i++) {
            down.setAction(ACTION_POINTER_DOWN | (i << ACTION_POINTER_INDEX_SHIFT));
            sendEvent(down);
        }

        down.recycle();

        if (closeActiveWindows) {
            OverviewCallbacks.get(mActivity).closeAllWindows();
            ActivityManagerWrapper.getInstance()
                    .closeSystemWindows(CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);
            TOUCH_INTERACTION_LOG.startQuickStep();
        }
    }

    private void sendEvent(MotionEvent ev) {
        if (!mTarget.verifyTouchDispatch(this, ev)) {
            mInvalidated = true;
            return;
        }
        int flags = ev.getEdgeFlags();
        ev.setEdgeFlags(flags | TouchInteractionService.EDGE_NAV_BAR);
        ev.offsetLocation(-mLocationOnScreen[0], -mLocationOnScreen[1]);
        if (!mTrackingStarted) {
            mTarget.onInterceptTouchEvent(ev);
        }
        mTarget.onTouchEvent(ev);
        ev.offsetLocation(mLocationOnScreen[0], mLocationOnScreen[1]);
        ev.setEdgeFlags(flags);
    }

    public static TouchConsumer newInstance(ActivityControlHelper activityHelper,
            boolean startingInActivityBounds) {
        BaseDraggingActivity activity = activityHelper.getCreatedActivity();
        if (activity == null) {
            return TouchConsumer.NO_OP;
        }
        return new OverviewTouchConsumer(activity, startingInActivityBounds);
    }
}