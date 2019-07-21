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

import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.quickstep.TouchInteractionService.TOUCH_INTERACTION_LOG;
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_RECENTS;

import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.Utilities;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.OverviewCallbacks;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.InputMonitorCompat;

import java.util.function.Predicate;

/**
 * Input consumer for handling touch on the recents/Launcher activity.
 */
public class OverviewInputConsumer<T extends BaseDraggingActivity>
        implements InputConsumer {

    private final T mActivity;
    private final BaseDragLayer mTarget;
    private final InputMonitorCompat mInputMonitor;

    private final int[] mLocationOnScreen = new int[2];
    private final boolean mProxyTouch;
    private final Predicate<MotionEvent> mEventReceiver;

    private final boolean mStartingInActivityBounds;
    private boolean mTargetHandledTouch;

    public OverviewInputConsumer(T activity, @Nullable InputMonitorCompat inputMonitor,
            boolean startingInActivityBounds) {
        mActivity = activity;
        mInputMonitor = inputMonitor;
        mStartingInActivityBounds = startingInActivityBounds;

        mTarget = activity.getDragLayer();
        if (startingInActivityBounds) {
            mEventReceiver = mTarget::dispatchTouchEvent;
            mProxyTouch = true;
        } else {
            // Only proxy touches to controllers if we are starting touch from nav bar.
            mEventReceiver = mTarget::proxyTouchEvent;
            mTarget.getLocationOnScreen(mLocationOnScreen);
            mProxyTouch = mTarget.prepareProxyEventStarting();
        }
    }

    @Override
    public int getType() {
        return TYPE_OVERVIEW;
    }

    @Override
    public boolean allowInterceptByParent() {
        return !mTargetHandledTouch;
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        if (!mProxyTouch) {
            return;
        }

        int flags = ev.getEdgeFlags();
        if (!mStartingInActivityBounds) {
            ev.setEdgeFlags(flags | Utilities.EDGE_NAV_BAR);
        }
        ev.offsetLocation(-mLocationOnScreen[0], -mLocationOnScreen[1]);
        boolean handled = mEventReceiver.test(ev);
        ev.offsetLocation(mLocationOnScreen[0], mLocationOnScreen[1]);
        ev.setEdgeFlags(flags);

        if (!mTargetHandledTouch && handled) {
            mTargetHandledTouch = true;
            if (!mStartingInActivityBounds) {
                OverviewCallbacks.get(mActivity).closeAllWindows();
                ActivityManagerWrapper.getInstance()
                        .closeSystemWindows(CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);
                TOUCH_INTERACTION_LOG.addLog("startQuickstep");
            }
            if (mInputMonitor != null) {
                mInputMonitor.pilferPointers();
            }
        }
    }

    @Override
    public void onKeyEvent(KeyEvent ev) {
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            mActivity.dispatchKeyEvent(ev);
        }
    }
}

