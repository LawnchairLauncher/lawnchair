/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.PointF;
import android.os.RemoteException;
import android.util.Log;
import android.view.MotionEvent;

import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.quickstep.InputConsumer;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.util.TriggerSwipeUpTouchTracker;
import com.android.systemui.shared.system.InputMonitorCompat;

/**
 * Input consumer used when a fullscreen System UI overlay is showing (such as the expanded Bubbles
 * UI).
 *
 * This responds to swipes up by sending a closeSystemDialogs broadcast (causing overlays to close)
 * rather than closing the app behind the overlay and sending the user all the way home.
 */
public class SysUiOverlayInputConsumer implements InputConsumer,
        TriggerSwipeUpTouchTracker.OnSwipeUpListener {
    private static final String TAG = "SysUiOverlayInputConsumer";

    // Should match the values in PhoneWindowManager
    private static final String SYSTEM_DIALOG_REASON_GESTURE_NAV = "gestureNav";

    private final Context mContext;
    private final InputMonitorCompat mInputMonitor;
    private final TriggerSwipeUpTouchTracker mTriggerSwipeUpTracker;

    public SysUiOverlayInputConsumer(
            Context context,
            RecentsAnimationDeviceState deviceState,
            InputMonitorCompat inputMonitor) {
        mContext = context;
        mInputMonitor = inputMonitor;
        mTriggerSwipeUpTracker = new TriggerSwipeUpTouchTracker(context, true,
                deviceState.getNavBarPosition(), this);
    }

    @Override
    public int getType() {
        return TYPE_SYSUI_OVERLAY;
    }

    @Override
    public boolean allowInterceptByParent() {
        return !mTriggerSwipeUpTracker.interceptedTouch();
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        mTriggerSwipeUpTracker.onMotionEvent(ev);
    }

    @Override
    public void onSwipeUpTouchIntercepted() {
        if (mInputMonitor != null) {
            TestLogging.recordEvent(TestProtocol.SEQUENCE_PILFER, "pilferPointers");
            mInputMonitor.pilferPointers();
        }
    }

    @Override
    public void onSwipeUp(boolean wasFling, PointF finalVelocity) {
        // Close system dialogs when a swipe up is detected.
        try {
            ActivityManager.getService().closeSystemDialogs(SYSTEM_DIALOG_REASON_GESTURE_NAV);
        } catch (RemoteException e) {
            Log.e(TAG, "Exception calling closeSystemDialogs " + e.getMessage());
        }
    }
}
