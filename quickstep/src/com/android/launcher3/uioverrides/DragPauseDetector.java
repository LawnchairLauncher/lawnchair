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
package com.android.launcher3.uioverrides;

import com.android.launcher3.Alarm;
import com.android.launcher3.OnAlarmListener;

/**
 * Utility class to detect a pause during a drag.
 */
public class DragPauseDetector implements OnAlarmListener {

    private static final float MAX_VELOCITY_TO_PAUSE = 0.2f;
    private static final long PAUSE_DURATION = 100;

    private final Alarm mAlarm;
    private final Runnable mOnPauseCallback;

    private boolean mTriggered = false;
    private int mDisabledFlags = 0;

    public DragPauseDetector(Runnable onPauseCallback) {
        mOnPauseCallback = onPauseCallback;

        mAlarm = new Alarm();
        mAlarm.setOnAlarmListener(this);
        mAlarm.setAlarm(PAUSE_DURATION);
    }

    public void onDrag(float velocity) {
        if (mTriggered || !isEnabled()) {
            return;
        }

        if (Math.abs(velocity) > MAX_VELOCITY_TO_PAUSE) {
            // Cancel any previous alarm and set a new alarm
            mAlarm.setAlarm(PAUSE_DURATION);
        }
    }

    @Override
    public void onAlarm(Alarm alarm) {
        if (!mTriggered && isEnabled()) {
            mTriggered = true;
            mOnPauseCallback.run();
        }
    }

    public boolean isTriggered () {
        return mTriggered;
    }

    public boolean isEnabled() {
        return mDisabledFlags == 0;
    }

    public void addDisabledFlags(int flags) {
        boolean wasEnabled = isEnabled();
        mDisabledFlags |= flags;
        resetAlarm(wasEnabled);
    }

    public void clearDisabledFlags(int flags) {
        boolean wasEnabled = isEnabled();
        mDisabledFlags  &= ~flags;
        resetAlarm(wasEnabled);
    }

    private void resetAlarm(boolean wasEnabled) {
        boolean isEnabled = isEnabled();
        if (wasEnabled == isEnabled) {
          // Nothing has changed
        } if (isEnabled && !mTriggered) {
            mAlarm.setAlarm(PAUSE_DURATION);
        } else if (!isEnabled) {
            mAlarm.cancelAlarm();
        }
    }
}
