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

import static com.android.launcher3.LauncherPrefs.LONG_PRESS_NAV_HANDLE_TIMEOUT_MS;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_DEEP_PRESS_NAVBAR;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_LONG_PRESS_NAVBAR;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.util.DisplayController;
import com.android.quickstep.InputConsumer;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.TopTaskTracker;
import com.android.systemui.shared.system.InputMonitorCompat;

/**
 * Listens for a long press
 */
public class NavHandleLongPressInputConsumer extends DelegateInputConsumer {

    private final NavHandleLongPressHandler mNavHandleLongPressHandler;
    private final float mNavHandleWidth;
    private final float mScreenWidth;

    private final Runnable mTriggerLongPress = this::triggerLongPress;
    private final float mTouchSlopSquared;
    private final int mLongPressTimeout;
    private final boolean mDeepPressEnabled;
    private final StatsLogManager mStatsLogManager;
    private final TopTaskTracker mTopTaskTracker;

    private MotionEvent mCurrentDownEvent;
    private boolean mDeepPressLogged;  // Whether deep press has been logged for the current touch.

    public NavHandleLongPressInputConsumer(Context context, InputConsumer delegate,
            InputMonitorCompat inputMonitor, RecentsAnimationDeviceState deviceState) {
        super(delegate, inputMonitor);
        mNavHandleWidth = context.getResources().getDimensionPixelSize(
                R.dimen.navigation_home_handle_width);
        mScreenWidth = DisplayController.INSTANCE.get(context).getInfo().currentSize.x;
        mDeepPressEnabled = FeatureFlags.ENABLE_LPNH_DEEP_PRESS.get();
        if (FeatureFlags.CUSTOM_LPNH_THRESHOLDS.get()) {
            mLongPressTimeout = LauncherPrefs.get(context).get(LONG_PRESS_NAV_HANDLE_TIMEOUT_MS);
        } else {
            mLongPressTimeout = ViewConfiguration.getLongPressTimeout();
        }
        mTouchSlopSquared = deviceState.getSquaredTouchSlop();
        mNavHandleLongPressHandler = NavHandleLongPressHandler.newInstance(context);
        mStatsLogManager = StatsLogManager.newInstance(context);
        mTopTaskTracker = TopTaskTracker.INSTANCE.get(context);
    }

    @Override
    public int getType() {
        return TYPE_NAV_HANDLE_LONG_PRESS | mDelegate.getType();
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        if (mDelegate.allowInterceptByParent()) {
            handleMotionEvent(ev);
        } else if (MAIN_EXECUTOR.getHandler().hasCallbacks(mTriggerLongPress)) {
            cancelLongPress("intercept disallowed by child input consumer");
        }

        if (mState != STATE_ACTIVE) {
            mDelegate.onMotionEvent(ev);
        }
    }

    private void handleMotionEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN -> {
                if (mCurrentDownEvent != null) {
                    mCurrentDownEvent.recycle();
                }
                mCurrentDownEvent = MotionEvent.obtain(ev);
                mDeepPressLogged = false;
                if (isInNavBarHorizontalArea(ev.getRawX())) {
                    mNavHandleLongPressHandler.onTouchStarted();
                    MAIN_EXECUTOR.getHandler().postDelayed(mTriggerLongPress,
                            mLongPressTimeout);
                }
            }
            case MotionEvent.ACTION_MOVE -> {
                if (!MAIN_EXECUTOR.getHandler().hasCallbacks(mTriggerLongPress)) {
                    break;
                }

                float touchSlopSquared = mTouchSlopSquared;
                float dx = ev.getX() - mCurrentDownEvent.getX();
                float dy = ev.getY() - mCurrentDownEvent.getY();
                double distanceSquared = (dx * dx) + (dy * dy);
                if (distanceSquared > touchSlopSquared) {
                    cancelLongPress("touch slop passed");
                }
            }
            case MotionEvent.ACTION_UP -> cancelLongPress("touch action up");
            case MotionEvent.ACTION_CANCEL -> cancelLongPress("touch action cancel");
        }

        // If the gesture is deep press then trigger long press asap
        if (MAIN_EXECUTOR.getHandler().hasCallbacks(mTriggerLongPress)
                && ev.getClassification() == MotionEvent.CLASSIFICATION_DEEP_PRESS
                && !mDeepPressLogged) {
            // Log deep press even if feature is disabled.
            String runningPackage = mTopTaskTracker.getCachedTopTask(
                    /* filterOnlyVisibleRecents */ true).getPackageName();
            mStatsLogManager.logger().withPackageName(runningPackage)
                    .log(LAUNCHER_DEEP_PRESS_NAVBAR);
            mDeepPressLogged = true;

            // But only trigger if the feature is enabled.
            if (mDeepPressEnabled) {
                MAIN_EXECUTOR.getHandler().removeCallbacks(mTriggerLongPress);
                MAIN_EXECUTOR.getHandler().post(mTriggerLongPress);
            }
        }
    }

    private void triggerLongPress() {
        String runningPackage = mTopTaskTracker.getCachedTopTask(
                /* filterOnlyVisibleRecents */ true).getPackageName();
        mStatsLogManager.logger().withPackageName(runningPackage).log(LAUNCHER_LONG_PRESS_NAVBAR);

        Runnable longPressRunnable = mNavHandleLongPressHandler.getLongPressRunnable();
        if (longPressRunnable == null) {
            return;
        }

        OtherActivityInputConsumer oaic = getInputConsumerOfClass(OtherActivityInputConsumer.class);
        if (oaic != null && oaic.hasStartedTouchTracking()) {
            oaic.setForceFinishRecentsTransitionCallback(longPressRunnable);
            setActive(mCurrentDownEvent);
        } else {
            setActive(mCurrentDownEvent);
            MAIN_EXECUTOR.post(longPressRunnable);
        }
    }

    private void cancelLongPress(String reason) {
        MAIN_EXECUTOR.getHandler().removeCallbacks(mTriggerLongPress);
        mNavHandleLongPressHandler.onTouchFinished(reason);
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
