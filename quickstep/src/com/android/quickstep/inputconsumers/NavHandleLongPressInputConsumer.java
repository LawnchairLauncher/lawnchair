/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;

import com.android.launcher3.R;
import com.android.launcher3.util.DisplayController;
import com.android.quickstep.InputConsumer;
import com.android.systemui.shared.system.InputMonitorCompat;

/**
 * Listens for a long press
 */
public class NavHandleLongPressInputConsumer extends DelegateInputConsumer {

    private final GestureDetector mLongPressDetector;
    private final NavHandleLongPressHandler mNavHandleLongPressHandler;
    private final float mNavHandleWidth;
    private final float mScreenWidth;

    public NavHandleLongPressInputConsumer(Context context, InputConsumer delegate,
            InputMonitorCompat inputMonitor) {
        super(delegate, inputMonitor);
        mNavHandleWidth = context.getResources().getDimensionPixelSize(
                R.dimen.navigation_home_handle_width);
        mScreenWidth = DisplayController.INSTANCE.get(context).getInfo().currentSize.x;

        mNavHandleLongPressHandler = NavHandleLongPressHandler.newInstance(context);

        mLongPressDetector = new GestureDetector(context, new SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent motionEvent) {
                if (isInArea(motionEvent.getRawX())) {
                    Runnable longPressRunnable = mNavHandleLongPressHandler.getLongPressRunnable();
                    if (longPressRunnable != null) {
                        setActive(motionEvent);

                        MAIN_EXECUTOR.getHandler().postDelayed(longPressRunnable, 50);
                    }
                }
            }
        });
    }

    @Override
    public int getType() {
        return TYPE_NAV_HANDLE_LONG_PRESS | mDelegate.getType();
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        mLongPressDetector.onTouchEvent(ev);
        if (mState != STATE_ACTIVE) {
            mDelegate.onMotionEvent(ev);
        }
    }

    protected boolean isInArea(float x) {
        float areaFromMiddle = mNavHandleWidth / 2.0f;
        float distFromMiddle = Math.abs(mScreenWidth / 2.0f - x);

        return distFromMiddle < areaFromMiddle;
    }

    @Override
    protected String getDelegatorName() {
        return "NavHandleLongPressInputConsumer";
    }
}
