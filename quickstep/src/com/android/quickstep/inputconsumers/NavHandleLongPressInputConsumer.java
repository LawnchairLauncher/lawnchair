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

    private final NavHandleLongPressHandler mNavHandleLongPressHandler;
    private final float mNavHandleWidth;
    private final float mScreenWidth;
    private final ViewConfiguration mViewConfiguration;

    private final Runnable mTriggerLongPress = this::triggerLongPress;
    private final float mTouchSlopSquared;
    private final int mLongPressTimeout;

    private MotionEvent mCurrentDownEvent;

    public NavHandleLongPressInputConsumer(Context context, InputConsumer delegate,
            InputMonitorCompat inputMonitor) {
        super(delegate, inputMonitor);
        mViewConfiguration = ViewConfiguration.get(context);
        mNavHandleWidth = context.getResources().getDimensionPixelSize(
                R.dimen.navigation_home_handle_width);
        mScreenWidth = DisplayController.INSTANCE.get(context).getInfo().currentSize.x;
        float touchSlop;
        if (FeatureFlags.CUSTOM_LPNH_THRESHOLDS.get()) {
            float customSlopMultiplier =
                    LauncherPrefs.get(context).get(LONG_PRESS_NAV_HANDLE_SLOP_PERCENTAGE) / 100f;
            touchSlop = mViewConfiguration.getScaledEdgeSlop() * customSlopMultiplier;
            mLongPressTimeout = LauncherPrefs.get(context).get(LONG_PRESS_NAV_HANDLE_TIMEOUT_MS);
        } else {
            touchSlop = mViewConfiguration.getScaledTouchSlop();
            mLongPressTimeout = ViewConfiguration.getLongPressTimeout();
        }
        mTouchSlopSquared = touchSlop * touchSlop;
        mNavHandleLongPressHandler = NavHandleLongPressHandler.newInstance(context);
    }

    @Override
    public int getType() {
        return TYPE_NAV_HANDLE_LONG_PRESS | mDelegate.getType();
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN -> {
                if (mCurrentDownEvent != null) {
                    mCurrentDownEvent.recycle();
                }
                mCurrentDownEvent = MotionEvent.obtain(ev);
                if (isInNavBarHorizontalArea(ev.getRawX())
                        && mNavHandleLongPressHandler.canStartTouch()) {
                    MAIN_EXECUTOR.getHandler().postDelayed(mTriggerLongPress,
                            mLongPressTimeout);
                }
            }
            case MotionEvent.ACTION_MOVE -> {
                float touchSlopSquared = mTouchSlopSquared;
                float dx = ev.getX() - mCurrentDownEvent.getX();
                float dy = ev.getY() - mCurrentDownEvent.getY();
                double distanceSquared = (dx * dx) + (dy * dy);
                // If the gesture is ambiguous then require more movement before classifying this
                // as a NON long press gesture.
                if (ev.getClassification() == MotionEvent.CLASSIFICATION_AMBIGUOUS_GESTURE) {
                    float ambiguousGestureMultiplier =
                            mViewConfiguration.getScaledAmbiguousGestureMultiplier();
                    touchSlopSquared *= ambiguousGestureMultiplier * ambiguousGestureMultiplier;
                }
                if (distanceSquared > touchSlopSquared) {
                    cancelLongPress();
                }
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelLongPress();
        }

        // If the gesture is deep press then trigger long press asap
        if (MAIN_EXECUTOR.getHandler().hasCallbacks(mTriggerLongPress)
                && ev.getClassification() == MotionEvent.CLASSIFICATION_DEEP_PRESS) {
            MAIN_EXECUTOR.getHandler().removeCallbacks(mTriggerLongPress);
            MAIN_EXECUTOR.getHandler().post(mTriggerLongPress);
        }

        if (mState != STATE_ACTIVE) {
            mDelegate.onMotionEvent(ev);
        }
    }

    private void triggerLongPress() {
        Runnable longPressRunnable = mNavHandleLongPressHandler.getLongPressRunnable();
        if (longPressRunnable != null) {
            OtherActivityInputConsumer oaic = getInputConsumerOfClass(
                    OtherActivityInputConsumer.class);
            if (oaic != null) {
                oaic.setForceFinishRecentsTransitionCallback(longPressRunnable);
                setActive(mCurrentDownEvent);
            } else {
                setActive(mCurrentDownEvent);
                MAIN_EXECUTOR.post(longPressRunnable);
            }
        }
    }

    private void cancelLongPress() {
        MAIN_EXECUTOR.getHandler().removeCallbacks(mTriggerLongPress);
        mNavHandleLongPressHandler.onTouchFinished();
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
