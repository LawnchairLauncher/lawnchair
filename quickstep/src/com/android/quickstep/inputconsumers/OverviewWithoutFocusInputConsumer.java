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

import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_BACKGROUND;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_HOME;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOME_GESTURE;
import static com.android.quickstep.OverviewComponentObserver.startHomeIntentSafely;

import android.content.Context;
import android.graphics.PointF;
import android.view.MotionEvent;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.quickstep.GestureState;
import com.android.quickstep.InputConsumer;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.util.TriggerSwipeUpTouchTracker;
import com.android.systemui.shared.system.InputMonitorCompat;

public class OverviewWithoutFocusInputConsumer implements InputConsumer,
        TriggerSwipeUpTouchTracker.OnSwipeUpListener {
    private static final String TAG = "OverviewWithoutFocusInputConsumer";

    private final Context mContext;
    private final InputMonitorCompat mInputMonitor;
    private final TriggerSwipeUpTouchTracker mTriggerSwipeUpTracker;
    private final GestureState mGestureState;

    public OverviewWithoutFocusInputConsumer(Context context,
            RecentsAnimationDeviceState deviceState, GestureState gestureState,
            InputMonitorCompat inputMonitor, boolean disableHorizontalSwipe) {
        mContext = context;
        mGestureState = gestureState;
        mInputMonitor = inputMonitor;
        mTriggerSwipeUpTracker = new TriggerSwipeUpTouchTracker(context, disableHorizontalSwipe,
                deviceState.getNavBarPosition(), this::onInterceptTouch, this);
    }

    @Override
    public int getType() {
        return TYPE_OVERVIEW_WITHOUT_FOCUS;
    }

    @Override
    public boolean allowInterceptByParent() {
        return !mTriggerSwipeUpTracker.interceptedTouch();
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        mTriggerSwipeUpTracker.onMotionEvent(ev);
    }

    private void onInterceptTouch() {
        if (mInputMonitor != null) {
            TestLogging.recordEvent(TestProtocol.SEQUENCE_PILFER, "pilferPointers");
            mInputMonitor.pilferPointers();
        }
    }

    @Override
    public void onSwipeUp(boolean wasFling, PointF finalVelocity) {
        startHomeIntentSafely(mContext, mGestureState.getHomeIntent(), null, TAG);
        BaseActivity activity = BaseDraggingActivity.fromContext(mContext);
        int state = (mGestureState != null && mGestureState.getEndTarget() != null)
                ? mGestureState.getEndTarget().containerType
                : LAUNCHER_STATE_HOME;
        activity.getStatsLogManager().logger()
                .withSrcState(LAUNCHER_STATE_BACKGROUND)
                .withDstState(state)
                .withContainerInfo(LauncherAtom.ContainerInfo.newBuilder()
                        .setWorkspace(
                                LauncherAtom.WorkspaceContainer.newBuilder()
                                        .setPageIndex(-1))
                        .build())
                .log(LAUNCHER_HOME_GESTURE);
    }

    @Override
    public void onSwipeUpCancelled() {}
}
