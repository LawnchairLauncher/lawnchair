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
import static com.android.launcher3.logging.StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_CONTEXTUAL_SEARCH_LPNH_ABANDON;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.LogConfig.NAV_HANDLE_LONG_PRESS;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.Utilities;
import com.android.launcher3.logging.InstanceIdSequence;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.util.DisplayController;
import com.android.quickstep.DeviceConfigWrapper;
import com.android.quickstep.GestureState;
import com.android.quickstep.InputConsumer;
import com.android.quickstep.NavHandle;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.TopTaskTracker;
import com.android.quickstep.util.ContextualSearchStateManager;
import com.android.systemui.shared.system.InputMonitorCompat;

/**
 * Listens for a long press
 */
public class NavHandleLongPressInputConsumer extends DelegateInputConsumer {

    private static final String TAG = "NavHandleLongPressIC";
    private static final boolean DEBUG_NAV_HANDLE = Utilities.isPropertyEnabled(
            NAV_HANDLE_LONG_PRESS);
    // Minimum time between touch down and abandon to log.
    @VisibleForTesting static final long MIN_TIME_TO_LOG_ABANDON_MS = 200;

    private NavHandleLongPressHandler mNavHandleLongPressHandler;
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
    private StatsLogManager mStatsLogManager;
    private final TopTaskTracker mTopTaskTracker;
    private final GestureState mGestureState;

    private MotionEvent mCurrentDownEvent;  // Down event that started the current gesture.
    private MotionEvent mCurrentMotionEvent;  // Most recent motion event.
    private boolean mDeepPressLogged;  // Whether deep press has been logged for the current touch.

    public NavHandleLongPressInputConsumer(
            Context context,
            InputConsumer delegate,
            InputMonitorCompat inputMonitor,
            RecentsAnimationDeviceState deviceState,
            NavHandle navHandle,
            GestureState gestureState) {
        super(gestureState.getDisplayId(), delegate, inputMonitor);
        mScreenWidth = DisplayController.INSTANCE.get(context).getInfo().currentSize.x;
        mDeepPressEnabled = DeviceConfigWrapper.get().getEnableLpnhDeepPress();
        ContextualSearchStateManager contextualSearchStateManager =
                ContextualSearchStateManager.INSTANCE.get(context);
        if (contextualSearchStateManager.getLPNHDurationMillis().isPresent()) {
            mLongPressTimeout =
                    contextualSearchStateManager.getLPNHDurationMillis().get().intValue();
        } else {
            mLongPressTimeout = ViewConfiguration.getLongPressTimeout();
        }
        float twoStageDurationMultiplier =
                (DeviceConfigWrapper.get().getTwoStageDurationPercentage() / 100f);
        mOuterLongPressTimeout = (int) (mLongPressTimeout * twoStageDurationMultiplier);

        float gestureNavTouchSlopSquared = deviceState.getSquaredTouchSlop();
        float twoStageSlopMultiplier =
                (DeviceConfigWrapper.get().getTwoStageSlopPercentage() / 100f);
        float twoStageSlopMultiplierSquared = twoStageSlopMultiplier * twoStageSlopMultiplier;
        if (DeviceConfigWrapper.get().getEnableLpnhTwoStages()) {
            // For 2 stages, the outer touch slop should match gesture nav.
            mTouchSlopSquared = gestureNavTouchSlopSquared * twoStageSlopMultiplierSquared;
            mOuterTouchSlopSquared = gestureNavTouchSlopSquared;
        } else {
            // For single stage, the touch slop should match gesture nav.
            mTouchSlopSquared = gestureNavTouchSlopSquared;
            // Note: This outer slop is not actually used for single-stage (flag disabled).
            mOuterTouchSlopSquared = gestureNavTouchSlopSquared;
        }
        mTouchSlopSquaredOriginal = mTouchSlopSquared;

        mGestureState = gestureState;
        mGestureState.setIsInExtendedSlopRegion(false);
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
        if (mCurrentMotionEvent != null) {
            mCurrentMotionEvent.recycle();
        }
        mCurrentMotionEvent = MotionEvent.obtain(ev);
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
                mGestureState.setIsInExtendedSlopRegion(false);
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
                        mGestureState.setIsInExtendedSlopRegion(true);
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
                    /* filterOnlyVisibleRecents */ true, getDisplayId()).getPackageName();
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
                /* filterOnlyVisibleRecents */ true, getDisplayId()).getPackageName();
        mStatsLogManager.logger().withPackageName(runningPackage).log(
                mNavHandle.isNavHandleStashedTaskbar() ? LAUNCHER_LONG_PRESS_STASHED_TASKBAR
                        : LAUNCHER_LONG_PRESS_NAVBAR);

        Runnable longPressRunnable = mNavHandleLongPressHandler.getLongPressRunnable(mNavHandle,
                getDisplayId());
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
            Log.d(TAG, "cancelLongPress: " + reason);
        }
        // Log LPNH abandon latency if we didn't trigger but were still prepared to.
        if (mCurrentMotionEvent != null && mCurrentDownEvent != null) {
            long latencyMs = mCurrentMotionEvent.getEventTime() - mCurrentDownEvent.getEventTime();
            if (mState != STATE_ACTIVE && MAIN_EXECUTOR.getHandler().hasCallbacks(mTriggerLongPress)
                    && latencyMs >= MIN_TIME_TO_LOG_ABANDON_MS) {
                mStatsLogManager.latencyLogger()
                        .withInstanceId(new InstanceIdSequence().newInstanceId())
                        .withLatency(latencyMs)
                        .log(LAUNCHER_LATENCY_CONTEXTUAL_SEARCH_LPNH_ABANDON);
            }
        }
        mGestureState.setIsInExtendedSlopRegion(false);
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

    @VisibleForTesting
    void setNavHandleLongPressHandler(NavHandleLongPressHandler navHandleLongPressHandler) {
        mNavHandleLongPressHandler = navHandleLongPressHandler;
    }

    @VisibleForTesting
    void setStatsLogManager(StatsLogManager statsLogManager) {
        mStatsLogManager = statsLogManager;
    }
}
