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

import static android.app.contextualsearch.ContextualSearchManager.ENTRYPOINT_LONG_PRESS_NAV_HANDLE;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_LAUNCH_ASSISTANT_SUCCESSFUL_NAV_HANDLE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_OMNI_GET_LONG_PRESS_RUNNABLE;
import static com.android.launcher3.logging.StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_OMNI_RUNNABLE;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.InstanceIdSequence;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.util.VibratorWrapper;
import com.android.quickstep.DeviceConfigWrapper;
import com.android.quickstep.NavHandle;
import com.android.quickstep.TopTaskTracker;
import com.android.quickstep.util.ContextualSearchHapticManager;
import com.android.quickstep.util.ContextualSearchInvoker;
import com.android.quickstep.util.ContextualSearchStateManager;

import javax.inject.Inject;

/**
 * Class for extending nav handle long press behavior
 */
public class NavHandleLongPressHandler {

    private static final String TAG = "NavHandleLongPressHandler";
    protected final VibratorWrapper mVibratorWrapper;
    protected final ContextualSearchHapticManager mContextualSearchHapticManager;
    protected final ContextualSearchInvoker mContextualSearchInvoker;
    protected final StatsLogManager mStatsLogManager;
    private boolean mPendingInvocation;
    protected final TopTaskTracker mTopTaskTracker;
    private final ContextualSearchStateManager mContextualSearchStateManager;

    @Inject
    public NavHandleLongPressHandler(@ApplicationContext Context context,
            VibratorWrapper vibratorWrapper,
            ContextualSearchHapticManager hapticManager,
            TopTaskTracker topTaskTracker,
            StatsLogManager.StatsLogManagerFactory logManagerFactory,
            ContextualSearchStateManager contextualSearchStateManager,
            ContextualSearchInvoker contextualSearchInvoker) {
        mStatsLogManager = logManagerFactory.create(context);
        mVibratorWrapper = vibratorWrapper;
        mContextualSearchHapticManager = hapticManager;
        mContextualSearchInvoker = contextualSearchInvoker;
        mTopTaskTracker = topTaskTracker;
        mContextualSearchStateManager = contextualSearchStateManager;
    }

    /** Creates NavHandleLongPressHandler as specified by overrides */
    public static NavHandleLongPressHandler newInstance(Context context) {
        return LauncherComponentProvider.get(context).getNavHandleLongPressHandler();
    }

    protected boolean isContextualSearchEntrypointEnabled(NavHandle navHandle) {
        return DeviceConfigWrapper.get().getEnableLongPressNavHandle();
    }

    /**
     * Called when nav handle is long pressed to get the Runnable that should be executed by the
     * caller to invoke long press behavior. If null is returned that means long press couldn't be
     * handled.
     * <p>
     * A Runnable is returned here to ensure the InputConsumer can call
     * {@link android.view.InputMonitor#pilferPointers()} before invoking the long press behavior
     * since pilfering can break the long press behavior.
     *
     * @param navHandle to handle this long press
     */
    @Nullable
    @VisibleForTesting
    final Runnable getLongPressRunnable(NavHandle navHandle, int displayId) {
        if (!isContextualSearchEntrypointEnabled(navHandle)) {
            Log.i(TAG, "Contextual Search invocation failed: entry point disabled");
            mVibratorWrapper.cancelVibrate();
            return null;
        }

        if (!mContextualSearchInvoker.runContextualSearchInvocationChecksAndLogFailures()) {
            Log.i(TAG, "Contextual Search invocation failed: precondition not satisfied");
            mVibratorWrapper.cancelVibrate();
            return null;
        }

        mPendingInvocation = true;
        Log.i(TAG, "Contextual Search invocation: invocation runnable created");
        InstanceId instanceId = new InstanceIdSequence().newInstanceId();
        mStatsLogManager.logger().withInstanceId(instanceId).log(
                LAUNCHER_OMNI_GET_LONG_PRESS_RUNNABLE);
        long startTimeMillis = SystemClock.elapsedRealtime();
        return () -> {
            mStatsLogManager.latencyLogger().withInstanceId(instanceId).withLatency(
                    SystemClock.elapsedRealtime() - startTimeMillis).log(
                    LAUNCHER_LATENCY_OMNI_RUNNABLE);
            if (mContextualSearchInvoker.invokeContextualSearchUncheckedWithHaptic(
                    ENTRYPOINT_LONG_PRESS_NAV_HANDLE)) {
                Log.i(TAG, "Contextual Search invocation successful");

                String runningPackage = mTopTaskTracker.getCachedTopTask(
                        /* filterOnlyVisibleRecents */ true, displayId).getPackageName();
                mStatsLogManager.logger().withPackageName(runningPackage)
                        .log(LAUNCHER_LAUNCH_ASSISTANT_SUCCESSFUL_NAV_HANDLE);
            } else {
                mVibratorWrapper.cancelVibrate();
                if (DeviceConfigWrapper.get().getAnimateLpnh()
                        && !DeviceConfigWrapper.get().getShrinkNavHandleOnPress()) {
                    navHandle.animateNavBarLongPress(
                            /*isTouchDown*/false, /*shrink*/ false, /*durationMs*/160);
                }
            }
        };
    }

    /**
     * Called when nav handle gesture starts.
     *
     * @param navHandle to handle the animation for this touch
     */
    @VisibleForTesting
    final void onTouchStarted(NavHandle navHandle) {
        mPendingInvocation = false;
        if (isContextualSearchEntrypointEnabled(navHandle)
                && mContextualSearchInvoker.runContextualSearchInvocationChecksAndLogFailures()) {
            Log.i(TAG, "Contextual Search invocation: touch started");
            startNavBarAnimation(navHandle);
        }
    }

    /**
     * Called when nav handle gesture is finished by the user lifting their finger or the system
     * cancelling the touch for some other reason.
     *
     * @param navHandle to handle the animation for this touch
     * @param reason why the touch ended
     */
    @VisibleForTesting
    final void onTouchFinished(NavHandle navHandle, String reason) {
        Log.i(TAG, "Contextual Search invocation: touch finished with reason: " + reason);

        if (!DeviceConfigWrapper.get().getShrinkNavHandleOnPress() || !mPendingInvocation) {
            mVibratorWrapper.cancelVibrate();
        }

        if (DeviceConfigWrapper.get().getAnimateLpnh()) {
            if (DeviceConfigWrapper.get().getShrinkNavHandleOnPress()) {
                navHandle.animateNavBarLongPress(
                        /*isTouchDown*/false, /*shrink*/ true, /*durationMs*/200);
            } else {
                navHandle.animateNavBarLongPress(
                        /*isTouchDown*/false, /*shrink*/ false, /*durationMs*/ 160);
            }
        }
    }

    @VisibleForTesting
    final void startNavBarAnimation(NavHandle navHandle) {
        mContextualSearchHapticManager.vibrateForSearchHint();

        if (DeviceConfigWrapper.get().getAnimateLpnh()) {
            if (DeviceConfigWrapper.get().getShrinkNavHandleOnPress()) {
                navHandle.animateNavBarLongPress(
                        /*isTouchDown*/ true, /*shrink*/true, /*durationMs*/200);
            } else {
                long longPressTimeout;
                if (mContextualSearchStateManager.getLPNHDurationMillis().isPresent()) {
                    longPressTimeout =
                            mContextualSearchStateManager.getLPNHDurationMillis().get().intValue();
                } else {
                    longPressTimeout = ViewConfiguration.getLongPressTimeout();
                }
                navHandle.animateNavBarLongPress(
                        /*isTouchDown*/ true, /*shrink*/ false, /*durationMs*/ longPressTimeout);
            }
        }
    }
}
