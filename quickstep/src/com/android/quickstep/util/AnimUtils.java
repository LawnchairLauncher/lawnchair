/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.quickstep.util;

import static com.android.app.animation.Interpolators.clampToProgress;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.animation.AnimatorSet;
import android.os.BinderUtils;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IRemoteCallback;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.views.ActivityContext;
import com.android.quickstep.views.RecentsViewContainer;

/**
 * Utility class containing methods to help manage animations, interpolators, and timings.
 */
public class AnimUtils {
    private static final int DURATION_DEFAULT_SPLIT_DISMISS = 350;

    /**
     * Fetches device-specific timings for the Overview > Split animation
     * (splitscreen initiated from Overview).
     */
    public static SplitAnimationTimings getDeviceOverviewToSplitTimings(boolean isTablet) {
        return isTablet
                ? SplitAnimationTimings.TABLET_OVERVIEW_TO_SPLIT
                : SplitAnimationTimings.PHONE_OVERVIEW_TO_SPLIT;
    }

    /**
     * Fetches device-specific timings for the Split > Confirm animation
     * (splitscreen confirmed by selecting a second app).
     */
    public static SplitAnimationTimings getDeviceSplitToConfirmTimings(boolean isTablet) {
        return isTablet
                ? SplitAnimationTimings.TABLET_SPLIT_TO_CONFIRM
                : SplitAnimationTimings.PHONE_SPLIT_TO_CONFIRM;
    }

    /**
     * Fetches device-specific timings for the app pair launch animation.
     */
    public static SplitAnimationTimings getDeviceAppPairLaunchTimings(boolean isTablet) {
        return isTablet
                ? SplitAnimationTimings.TABLET_APP_PAIR_LAUNCH
                : SplitAnimationTimings.PHONE_APP_PAIR_LAUNCH;
    }

    /**
     * Synchronizes the timing for the split dismiss animation to the current transition to
     * NORMAL (launcher home/workspace)
     */
    public static void goToNormalStateWithSplitDismissal(@NonNull StateManager stateManager,
            @NonNull RecentsViewContainer container,
            @NonNull StatsLogManager.LauncherEvent exitReason,
            @NonNull SplitAnimationController animationController) {
        StateAnimationConfig config = new StateAnimationConfig();
        BaseState startState = stateManager.getState();
        long duration = startState.getTransitionDuration(container, false /*isToState*/);
        if (duration == 0) {
            // Case where we're in contextual on workspace (NORMAL), which by default has 0
            // transition duration
            duration = DURATION_DEFAULT_SPLIT_DISMISS;
        }
        config.duration = duration;
        AnimatorSet stateAnim = stateManager.createAtomicAnimation(
                startState, NORMAL, config);
        AnimatorSet dismissAnim = animationController
                .createPlaceholderDismissAnim(container, exitReason, duration);
        stateAnim.play(dismissAnim);
        stateManager.setCurrentAnimation(stateAnim, NORMAL);
        stateAnim.start();
    }

    /**
     * Returns a IRemoteCallback which completes the provided list as a result or when the owner
     * is destroyed
     */
    public static IRemoteCallback completeRunnableListCallback(
            RunnableList list, ActivityContext owner) {
        DefaultLifecycleObserver destroyObserver = new DefaultLifecycleObserver() {
            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                list.executeAllAndClear();
            }
        };
        MAIN_EXECUTOR.execute(() -> owner.getLifecycle().addObserver(destroyObserver));
        list.add(() -> owner.getLifecycle().removeObserver(destroyObserver));

        return new IRemoteCallback.Stub() {
            @Override
            public void sendResult(Bundle bundle) {
                MAIN_EXECUTOR.execute(list::executeAllAndDestroy);
            }

            @Override
            public IBinder asBinder() {
                return BinderUtils.wrapLifecycle(this, owner.getOwnerCleanupSet());
            }
        };
    }

    /**
     * Returns a function that runs the given interpolator such that the entire progress is set
     * between the given duration. That is, we set the interpolation to 0 until startDelay and reach
     * 1 by (startDelay + duration).
     */
    public static Interpolator clampToDuration(Interpolator interpolator, float startDelay,
            float duration, float totalDuration) {
        return clampToProgress(interpolator, startDelay / totalDuration,
                (startDelay + duration) / totalDuration);
    }
}
