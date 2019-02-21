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
package com.android.quickstep;

import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.util.Log;

import com.android.launcher3.BaseDraggingActivity;
import com.android.quickstep.util.RemoteAnimationProvider;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

/**
 * Provider for the atomic remote window animation from the app to the overview.
 *
 * @param <T> activity that contains the overview
 */
final class AppToOverviewAnimationProvider<T extends BaseDraggingActivity> implements
        RemoteAnimationProvider {

    private static final long RECENTS_LAUNCH_DURATION = 250;
    private static final String TAG = "AppToOverviewAnimationProvider";

    private final ActivityControlHelper<T> mHelper;

    AppToOverviewAnimationProvider(ActivityControlHelper<T> helper, int targetTaskId) {
        mHelper = helper;
    }

    /**
     * Callback for when the activity is ready/initialized.
     *
     * @param activity the activity that is ready
     * @param wasVisible true if it was visible before
     */
    boolean onActivityReady(T activity, Boolean wasVisible) {
        ActivityControlHelper.AnimationFactory factory =
                mHelper.prepareRecentsUI(activity, wasVisible,
                        false /* animate activity */, (controller) -> {
                            controller.dispatchOnStart();
                            ValueAnimator anim = controller.getAnimationPlayer()
                                    .setDuration(RECENTS_LAUNCH_DURATION);
                            anim.setInterpolator(FAST_OUT_SLOW_IN);
                            anim.start();
                        });
        factory.onRemoteAnimationReceived(null);
        factory.createActivityController(RECENTS_LAUNCH_DURATION);
        return false;
    }

    /**
     * Create remote window animation from the currently running app to the overview panel.
     *
     * @param targetCompats the target apps
     * @return animation from app to overview
     */
    @Override
    public AnimatorSet createWindowAnimation(RemoteAnimationTargetCompat[] targetCompats) {
        //TODO: Implement the overview to app window animation for Go.
        AnimatorSet anim = new AnimatorSet();
        anim.play(ValueAnimator.ofInt(0, 1).setDuration(RECENTS_LAUNCH_DURATION));
        return anim;
    }

    /**
     * Get duration of animation from app to overview.
     *
     * @return duration of animation
     */
    long getRecentsLaunchDuration() {
        return RECENTS_LAUNCH_DURATION;
    }
}
