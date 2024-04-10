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

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_DEEP_PRESS_NAVBAR;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_DEEP_PRESS_STASHED_TASKBAR;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_LONG_PRESS_NAVBAR;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_LONG_PRESS_STASHED_TASKBAR;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.LogConfig.NAV_HANDLE_LONG_PRESS;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.launcher3.Utilities;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.util.DisplayController;
import com.android.quickstep.DeviceConfigWrapper;
import com.android.quickstep.InputConsumer;
import com.android.quickstep.NavHandle;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.TopTaskTracker;
import com.android.quickstep.util.AssistStateManager;
import com.android.systemui.shared.system.InputMonitorCompat;

/**
 * Listens for a long press
 */
public class NavHandleLongPressInputConsumer extends DelegateInputConsumer {

    private static final String TAG = "NavHandleLongPressIC";
    private static final boolean DEBUG_NAV_HANDLE = Utilities.isPropertyEnabled(
            NAV_HANDLE_LONG_PRESS);

    private final NavHandleLongPressHandler mNavHandleLongPressHandler;
    private final float mNavHandleWidth;
    private final float mScreenWidth;

    private final Runnable mTriggerLongPress = this::triggerLongPress;
    private final float mTouchSlopSquaredOriginal;
    private float mTouchSlopSquared;
    private final float mOuterTouchSlopSquared;
    private final int mLongPressTimeout;
    private final int mOuterLongPressTimeout;
    private final boolean mDeepPressEnabled;
    private final NavHandle mNavHandle;
    private final StatsLogManager mStatsLogManager;
    private final TopTaskTracker mTopTaskTracker;

    private MotionEvent mCurrentDownEvent;
    private boolean mDeepPressLogged;  // Whether deep press has been logged for the current touch.

    public NavHandleLongPressInputConsumer(Context context, InputConsumer delegate,
            InputMonitorCompat inputMonitor, RecentsAnimationDeviceState deviceState,
            NavHandle navHandle) {
        super(delegate, inputMonitor);
        mScreenWidth = DisplayController.INSTANCE.get(context).getInfo().currentSize.x;
        mDeepPressEnabled = DeviceConfigWrapper.get().getEnableLpnhDeepPress();
        int twoStageMultiplier = DeviceConfigWrapper.get().getTwoStageMultiplier();
        AssistStateManager assistStateManager = AssistStateManager.INSTANCE.get(context);
        if (assistStateManager.getLPNHDurationMillis().isPresent()) {
            mLongPressTimeout = assistStateManager.getLPNHDurationMillis().get().intValue();
        } else {
            mLongPressTimeout = ViewConfiguration.getLongPressTimeout();
        }
        mOuterLongPressTimeout = mLongPressTimeout * twoStageMultiplier;
        mTouchSlopSquaredOriginal = deviceState.getSquaredTouchSlop();
        mTouchSlopSquared = mTouchSlopSquaredOriginal;
        mOuterTouchSlopSquared = mTouchSlopSquared * (twoStageMultiplier * twoStageMultiplier);
        if (DEBUG_NAV_HANDLE) {
            Log.d(TAG, "mLongPressTimeout=" + mLongPressTimeout);
            Log.d(TAG, "mOuterLongPressTimeout=" + mOuterLongPressTimeout);
            Log.d(TAG, "mTouchSlopSquared=" + mTouchSlopSquared);
            Log.d(TAG, "mOuterTouchSlopSquared=" + mOuterTouchSlopSquared);
        }
        mNavHandle = navHandle;
        mNavHandleWidth = navHandle.getNavHandleWidth(context);
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

    @Override
    public void onHoverEvent(MotionEvent ev) {
        mDelegate.onHoverEvent(ev);
    }

    private void handleMotionEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN -> {
                if (mCurrentDownEvent != null) {
                    mCurrentDownEvent.recycle();
                }
                mCurrentDownEvent = MotionEvent.obtain(ev);
                mTouchSlopSquared = mTouchSlopSquaredOriginal;
                mDeepPressLogged = false;
                if (isInNavBarHorizontalArea(ev.getRawX())) {
                    mNavHandleLongPressHandler.onTouchStarted(mNavHandle);
                    MAIN_EXECUTOR.getHandler().postDelayed(mTriggerLongPress, mLongPressTimeout);
                }
                if (DEBUG_NAV_HANDLE) {
                    Log.d(TAG, "ACTION_DOWN");
                }
            }
            case MotionEvent.ACTION_MOVE -> {
                if (!MAIN_EXECUTOR.getHandler().hasCallbacks(mTriggerLongPress)) {
                    break;
                }

                float dx = ev.getX() - mCurrentDownEvent.getX();
                float dy = ev.getY() - mCurrentDownEvent.getY();
                double distanceSquared = (dx * dx) + (dy * dy);
                if (DEBUG_NAV_HANDLE) {
                    Log.d(TAG, "ACTION_MOVE distanceSquared=" + distanceSquared);
                }
                if (DeviceConfigWrapper.get().getEnableLpnhTwoStages()) {
                    if (mTouchSlopSquared < distanceSquared
                            && distanceSquared <= mOuterTouchSlopSquared) {
                        MAIN_EXECUTOR.getHandler().removeCallbacks(mTriggerLongPress);
                        int delay = mOuterLongPressTimeout
                                - (int) (ev.getEventTime() - ev.getDownTime());
                        MAIN_EXECUTOR.getHandler().postDelayed(mTriggerLongPress, delay);
                        mTouchSlopSquared = mOuterTouchSlopSquared;
                        if (DEBUG_NAV_HANDLE) {
                            Log.d(TAG, "Touch in middle region!");
                        }
                    }
                }
                if (distanceSquared > mTouchSlopSquared) {
                    if (DEBUG_NAV_HANDLE) {
                        Log.d(TAG, "Touch slop out. mTouchSlopSquared=" + mTouchSlopSquared);
                    }
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
            mStatsLogManager.logger().withPackageName(runningPackage).log(
                    mNavHandle.isNavHandleStashedTaskbar() ? LAUNCHER_DEEP_PRESS_STASHED_TASKBAR
                            : LAUNCHER_DEEP_PRESS_NAVBAR);
            mDeepPressLogged = true;

            // But only trigger if the feature is enabled.
            if (mDeepPressEnabled) {
                MAIN_EXECUTOR.getHandler().removeCallbacks(mTriggerLongPress);
                MAIN_EXECUTOR.getHandler().post(mTriggerLongPress);
            }
        }
    }

    private void triggerLongPress() {
        if (DEBUG_NAV_HANDLE) {
            Log.d(TAG, "triggerLongPress");
        }
        String runningPackage = mTopTaskTracker.getCachedTopTask(
                /* filterOnlyVisibleRecents */ true).getPackageName();
        mStatsLogManager.logger().withPackageName(runningPackage).log(
                mNavHandle.isNavHandleStashedTaskbar() ? LAUNCHER_LONG_PRESS_STASHED_TASKBAR
                        : LAUNCHER_LONG_PRESS_NAVBAR);

        Runnable longPressRunnable = mNavHandleLongPressHandler.getLongPressRunnable(mNavHandle);
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
        if (DEBUG_NAV_HANDLE) {
            Log.d(TAG, "cancelLongPress");
        }
        MAIN_EXECUTOR.getHandler().removeCallbacks(mTriggerLongPress);
        mNavHandleLongPressHandler.onTouchFinished(mNavHandle, reason);
    }

    private boolean isInNavBarHorizontalArea(float x) {
        float areaFromMiddle = mNavHandleWidth / 2.0f;
        if (DeviceConfigWrapper.get().getCustomLpnhThresholds()) {
            areaFromMiddle += Utilities.dpToPx(
                    DeviceConfigWrapper.get().getLpnhExtraTouchWidthDp());
        }
        int minAccessibleSize = Utilities.dpToPx(24);  // Half of 48dp because this is per side.
        if (areaFromMiddle < minAccessibleSize) {
            Log.w(TAG, "Custom nav handle region is too small - resetting to 48dp");
            areaFromMiddle = minAccessibleSize;
        }
        float distFromMiddle = Math.abs(mScreenWidth / 2.0f - x);

        return distFromMiddle < areaFromMiddle;
    }

    @Override
    protected String getDelegatorName() {
        return "NavHandleLongPressInputConsumer";
    }
}
