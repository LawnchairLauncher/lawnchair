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

import static com.android.launcher3.LauncherPrefs.LONG_PRESS_NAV_HANDLE_SLOP_PERCENTAGE;
import static com.android.launcher3.LauncherPrefs.LONG_PRESS_NAV_HANDLE_TIMEOUT_MS;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
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

    // Below are only used if CUSTOM_LPNH_THRESHOLDS is enabled.
    private final float mCustomTouchSlopSquared;
    private final int mCustomLongPressTimeout;
    private final Runnable mTriggerCustomLongPress = this::triggerCustomLongPress;
    private MotionEvent mCurrentCustomDownEvent;

    public NavHandleLongPressInputConsumer(Context context, InputConsumer delegate,
            InputMonitorCompat inputMonitor) {
        super(delegate, inputMonitor);
        mNavHandleWidth = context.getResources().getDimensionPixelSize(
                R.dimen.navigation_home_handle_width);
        mScreenWidth = DisplayController.INSTANCE.get(context).getInfo().currentSize.x;
        float customSlopMultiplier =
                LauncherPrefs.get(context).get(LONG_PRESS_NAV_HANDLE_SLOP_PERCENTAGE) / 100f;
        float customTouchSlop =
                ViewConfiguration.get(context).getScaledEdgeSlop() * customSlopMultiplier;
        mCustomTouchSlopSquared = customTouchSlop * customTouchSlop;
        mCustomLongPressTimeout = LauncherPrefs.get(context).get(LONG_PRESS_NAV_HANDLE_TIMEOUT_MS);

        mNavHandleLongPressHandler = NavHandleLongPressHandler.newInstance(context);

        mLongPressDetector = new GestureDetector(context, new SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent motionEvent) {
                if (isInNavBarHorizontalArea(motionEvent.getRawX())) {
                    Runnable longPressRunnable = mNavHandleLongPressHandler.getLongPressRunnable();
                    if (longPressRunnable != null) {
                        OtherActivityInputConsumer oaic = getInputConsumerOfClass(
                                OtherActivityInputConsumer.class);
                        if (oaic != null) {
                            oaic.setForceFinishRecentsTransitionCallback(longPressRunnable);
                            setActive(motionEvent);
                        } else {
                            setActive(motionEvent);
                            MAIN_EXECUTOR.post(longPressRunnable);
                        }
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
        if (!FeatureFlags.CUSTOM_LPNH_THRESHOLDS.get()) {
            mLongPressDetector.onTouchEvent(ev);
        } else {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN -> {
                    if (mCurrentCustomDownEvent != null) {
                        mCurrentCustomDownEvent.recycle();
                    }
                    mCurrentCustomDownEvent = MotionEvent.obtain(ev);
                    if (isInNavBarHorizontalArea(ev.getRawX())) {
                        MAIN_EXECUTOR.getHandler().postDelayed(mTriggerCustomLongPress,
                                mCustomLongPressTimeout);
                    }
                }
                case MotionEvent.ACTION_MOVE -> {
                    double touchDeltaSquared =
                            Math.pow(ev.getX() - mCurrentCustomDownEvent.getX(), 2)
                            + Math.pow(ev.getY() - mCurrentCustomDownEvent.getY(), 2);
                    if (touchDeltaSquared > mCustomTouchSlopSquared) {
                        cancelCustomLongPress();
                    }
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelCustomLongPress();
            }
        }

        if (mState != STATE_ACTIVE) {
            mDelegate.onMotionEvent(ev);
        }
    }

    private void triggerCustomLongPress() {
        Runnable longPressRunnable = mNavHandleLongPressHandler.getLongPressRunnable();
        if (longPressRunnable != null) {
            setActive(mCurrentCustomDownEvent);

            MAIN_EXECUTOR.post(longPressRunnable);
        }
    }

    private void cancelCustomLongPress() {
        MAIN_EXECUTOR.getHandler().removeCallbacks(mTriggerCustomLongPress);
    }

    private boolean isInNavBarHorizontalArea(float x) {
        float areaFromMiddle = mNavHandleWidth / 2.0f;
        float distFromMiddle = Math.abs(mScreenWidth / 2.0f - x);

        return distFromMiddle < areaFromMiddle;
    }

    @Override
    protected String getDelegatorName() {
        return "NavHandleLongPressInputConsumer";
    }
}
