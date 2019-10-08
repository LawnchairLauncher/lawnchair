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

import android.content.Context;
import android.os.RemoteException;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.R;
import com.android.quickstep.ActivityControlHelper;
import com.android.quickstep.util.MotionPauseDetector;
import com.android.systemui.shared.recents.ISystemUiProxy;

/**
 * An input consumer that detects swipe up and hold to exit screen pinning mode.
 */
public class ScreenPinnedInputConsumer implements InputConsumer {

    private static final String TAG = "ScreenPinnedConsumer";

    private final float mMotionPauseMinDisplacement;
    private final MotionPauseDetector mMotionPauseDetector;

    private float mTouchDownY;

    public ScreenPinnedInputConsumer(Context context, ISystemUiProxy sysuiProxy,
            ActivityControlHelper activityControl) {
        mMotionPauseMinDisplacement = context.getResources().getDimension(
                R.dimen.motion_pause_detector_min_displacement_from_app);
        mMotionPauseDetector = new MotionPauseDetector(context, true /* makePauseHarderToTrigger*/);
        mMotionPauseDetector.setOnMotionPauseListener(isPaused -> {
            if (isPaused) {
                try {
                    sysuiProxy.stopScreenPinning();
                    BaseDraggingActivity launcherActivity = activityControl.getCreatedActivity();
                    if (launcherActivity != null) {
                        launcherActivity.getRootView().performHapticFeedback(
                                HapticFeedbackConstants.LONG_PRESS,
                                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                    }
                    mMotionPauseDetector.clear();
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to stop screen pinning ", e);
                }
            }
        });
    }

    @Override
    public int getType() {
        return TYPE_SCREEN_PINNED;
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        float y = ev.getY();
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mTouchDownY = y;
                break;
            case MotionEvent.ACTION_MOVE:
                float displacement = mTouchDownY - y;
                mMotionPauseDetector.setDisallowPause(displacement < mMotionPauseMinDisplacement);
                mMotionPauseDetector.addPosition(y, ev.getEventTime());
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mMotionPauseDetector.clear();
                break;
        }
    }
}
