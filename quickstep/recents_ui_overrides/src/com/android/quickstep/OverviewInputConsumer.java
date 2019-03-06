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
import static android.view.MotionEvent.ACTION_UP;

import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.quickstep.TouchInteractionService.TOUCH_INTERACTION_LOG;
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_RECENTS;

import android.graphics.PointF;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.util.CachedEventDispatcher;
import com.android.systemui.shared.system.ActivityManagerWrapper;

/**
 * Input consumer for handling touch on the recents/Launcher activity.
 */
public class OverviewInputConsumer<T extends BaseDraggingActivity>
        implements InputConsumer {

    private final CachedEventDispatcher mCachedEventDispatcher = new CachedEventDispatcher();
    private final T mActivity;
    private final BaseDragLayer mTarget;
    private final int[] mLocationOnScreen = new int[2];
    private final PointF mDownPos = new PointF();
    private final int mTouchSlop;

    private final boolean mStartingInActivityBounds;

    private boolean mTrackingStarted = false;
    private boolean mInvalidated = false;

    OverviewInputConsumer(T activity, boolean startingInActivityBounds) {
        mActivity = activity;
        mTarget = activity.getDragLayer();
        mTouchSlop = ViewConfiguration.get(mActivity).getScaledTouchSlop();
        mStartingInActivityBounds = startingInActivityBounds;
    }

    @Override
    public int getType() {
        return TYPE_OVERVIEW;
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        if (mInvalidated) {
            return;
        }
        mCachedEventDispatcher.dispatchEvent(ev);
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

        if (action == ACTION_UP || action == ACTION_CANCEL) {
            mInvalidated = true;

            // Set an empty consumer to that all the cached events are cleared
            if (!mCachedEventDispatcher.hasConsumer()) {
                mCachedEventDispatcher.setConsumer(motionEvent -> { });
            }
        }
    }

    @Override
    public void onKeyEvent(KeyEvent ev) {
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            mActivity.dispatchKeyEvent(ev);
        }
    }

    private void startTouchTracking(MotionEvent ev, boolean updateLocationOffset,
            boolean closeActiveWindows) {
        if (updateLocationOffset) {
            mTarget.getLocationOnScreen(mLocationOnScreen);
        }

        if (closeActiveWindows) {
            OverviewCallbacks.get(mActivity).closeAllWindows();
            ActivityManagerWrapper.getInstance()
                    .closeSystemWindows(CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);
            TOUCH_INTERACTION_LOG.addLog("startQuickstep");
        }

        mTrackingStarted = true;
        mCachedEventDispatcher.setConsumer(this::sendEvent);

    }

    private void sendEvent(MotionEvent ev) {
        if (mInvalidated || !mTarget.verifyTouchDispatch(this, ev)) {
            mInvalidated = true;
            return;
        }
        int flags = ev.getEdgeFlags();
        ev.setEdgeFlags(flags | TouchInteractionService.EDGE_NAV_BAR);
        ev.offsetLocation(-mLocationOnScreen[0], -mLocationOnScreen[1]);
        if (ev.getAction() == ACTION_DOWN) {
            mTarget.onInterceptTouchEvent(ev);
        }
        mTarget.onTouchEvent(ev);
        ev.offsetLocation(mLocationOnScreen[0], mLocationOnScreen[1]);
        ev.setEdgeFlags(flags);
    }

    public static InputConsumer newInstance(ActivityControlHelper activityHelper,
            boolean startingInActivityBounds) {
        BaseDraggingActivity activity = activityHelper.getCreatedActivity();
        if (activity == null) {
            return InputConsumer.NO_OP;
        }
        return new OverviewInputConsumer(activity, startingInActivityBounds);
    }
}
