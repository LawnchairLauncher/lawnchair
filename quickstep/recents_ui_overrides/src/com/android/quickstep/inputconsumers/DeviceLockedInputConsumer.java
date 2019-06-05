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

import static com.android.launcher3.Utilities.squaredHypot;
import static com.android.launcher3.Utilities.squaredTouchSlop;

import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.view.MotionEvent;

/**
 * A dummy input consumer used when the device is still locked, e.g. from secure camera.
 */
public class DeviceLockedInputConsumer implements InputConsumer {

    private final Context mContext;
    private final float mTouchSlopSquared;
    private final PointF mTouchDown = new PointF();

    public DeviceLockedInputConsumer(Context context) {
        mContext = context;
        mTouchSlopSquared = squaredTouchSlop(context);
    }

    @Override
    public int getType() {
        return TYPE_DEVICE_LOCKED;
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        float x = ev.getX();
        float y = ev.getY();
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mTouchDown.set(x, y);
        } else if (ev.getAction() == MotionEvent.ACTION_MOVE) {
            if (squaredHypot(x - mTouchDown.x, y - mTouchDown.y) > mTouchSlopSquared) {
                // For now, just start the home intent so user is prompted to unlock the device.
                mContext.startActivity(new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }
    }
}
