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

import static com.android.launcher3.Utilities.postAsyncCallback;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.quickstep.views.IconRecentsView.REMOTE_APP_TO_OVERVIEW_DURATION;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.ACTIVITY_TYPE_HOME;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_CLOSING;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_OPENING;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.app.ActivityOptions;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.LauncherAnimationRunner;
import com.android.quickstep.util.RemoteAnimationProvider;
import com.android.quickstep.util.RemoteAnimationTargetSet;
import com.android.quickstep.views.IconRecentsView;
import com.android.systemui.shared.system.ActivityOptionsCompat;
import com.android.systemui.shared.system.RemoteAnimationAdapterCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

/**
 * Provider for the atomic remote window animation from the app to the overview.
 *
 * @param <T> activity that contains the overview
 */
final class AppToOverviewAnimationProvider<T extends BaseDraggingActivity> implements
        RemoteAnimationProvider {
    private static final String TAG = "AppToOverviewAnimationProvider";

    private final ActivityControlHelper<T> mHelper;
    private final int mTargetTaskId;
    private IconRecentsView mRecentsView;
    private AppToOverviewAnimationListener mAnimationReadyListener;

    AppToOverviewAnimationProvider(ActivityControlHelper<T> helper, int targetTaskId) {
        mHelper = helper;
        mTargetTaskId = targetTaskId;
    }

    /**
     * Set listener to various points in the animation preparing to animate.
     *
     * @param listener listener
     */
    void setAnimationListener(AppToOverviewAnimationListener listener) {
        mAnimationReadyListener = listener;
    }

    /**
     * Callback for when the activity is ready/initialized.
     *
     * @param activity the activity that is ready
     * @param wasVisible true if it was visible before
     */
    boolean onActivityReady(T activity, Boolean wasVisible) {
        if (mAnimationReadyListener != null) {
            mAnimationReadyListener.onActivityReady(activity);
        }
        ActivityControlHelper.AnimationFactory factory =
                mHelper.prepareRecentsUI(activity, wasVisible,
                        false /* animate activity */, (controller) -> {
                            controller.dispatchOnStart();
                            ValueAnimator anim = controller.getAnimationPlayer()
                                    .setDuration(getRecentsLaunchDuration());
                            anim.setInterpolator(FAST_OUT_SLOW_IN);
                            anim.start();
                        });
        factory.onRemoteAnimationReceived(null);
        factory.createActivityController(getRecentsLaunchDuration());
        mRecentsView = activity.getOverviewPanel();
        return false;
    }

    /**
     * Create remote window animation from the currently running app to the overview panel. Should
     * be called after {@link #onActivityReady}.
     *
     * @param targetCompats the target apps
     * @return animation from app to overview
     */
    @Override
    public AnimatorSet createWindowAnimation(RemoteAnimationTargetCompat[] targetCompats) {
        if (mAnimationReadyListener != null) {
            mAnimationReadyListener.onWindowAnimationCreated();
        }
        AnimatorSet anim = new AnimatorSet();
        if (mRecentsView == null) {
            if (Log.isLoggable(TAG, Log.WARN)) {
                Log.w(TAG, "No recents view. Using stub animation.");
            }
            anim.play(ValueAnimator.ofInt(0, 1).setDuration(getRecentsLaunchDuration()));
            return anim;
        }

        RemoteAnimationTargetSet targetSet =
                new RemoteAnimationTargetSet(targetCompats, MODE_CLOSING);
        mRecentsView.setTransitionedFromApp(!targetSet.isAnimatingHome());

        RemoteAnimationTargetCompat recentsTarget = null;
        RemoteAnimationTargetCompat closingAppTarget = null;

        for (RemoteAnimationTargetCompat target : targetCompats) {
            if (target.mode == MODE_OPENING) {
                recentsTarget = target;
            } else if (target.mode == MODE_CLOSING && target.taskId == mTargetTaskId) {
                closingAppTarget = target;
            }
        }

        if (closingAppTarget == null) {
            if (Log.isLoggable(TAG, Log.WARN)) {
                Log.w(TAG, "No closing app target. Using stub animation.");
            }
            anim.play(ValueAnimator.ofInt(0, 1).setDuration(getRecentsLaunchDuration()));
            return anim;
        }
        if (recentsTarget == null) {
            if (Log.isLoggable(TAG, Log.WARN)) {
                Log.w(TAG, "No recents target. Using stub animation.");
            }
            anim.play(ValueAnimator.ofInt(0, 1).setDuration(getRecentsLaunchDuration()));
            return anim;
        }

        if (closingAppTarget.activityType == ACTIVITY_TYPE_HOME) {
            mRecentsView.playRemoteHomeToRecentsAnimation(anim, closingAppTarget, recentsTarget);
        } else {
            mRecentsView.playRemoteAppToRecentsAnimation(anim, closingAppTarget, recentsTarget);
        }

        return anim;
    }

    @Override
    public ActivityOptions toActivityOptions(Handler handler, long duration, Context context) {
        LauncherAnimationRunner runner = new LauncherAnimationRunner(handler,
                false /* startAtFrontOfQueue */) {

            @Override
            public void onCreateAnimation(RemoteAnimationTargetCompat[] targetCompats,
                    AnimationResult result) {
                IconRecentsView recentsView = mRecentsView;
                if (!recentsView.isReadyForRemoteAnim()) {
                    recentsView.setOnReadyForRemoteAnimCallback(() -> postAsyncCallback(handler,
                            () -> onCreateAnimation(targetCompats, result))
                    );
                    return;
                }
                result.setAnimation(createWindowAnimation(targetCompats), context);
            }
        };
        return ActivityOptionsCompat.makeRemoteAnimation(
                new RemoteAnimationAdapterCompat(runner, duration,
                        0 /* statusBarTransitionDelay */));
    }

    /**
     * Get duration of animation from app to overview.
     *
     * @return duration of animation
     */
    long getRecentsLaunchDuration() {
        return REMOTE_APP_TO_OVERVIEW_DURATION;
    }

    /**
     * Listener for various points in the app to overview animation preparing to animate.
     */
    interface AppToOverviewAnimationListener {
        /**
         * Logic for when activity we're animating to is ready
         *
         * @param activity activity to animate to
         */
        void onActivityReady(BaseDraggingActivity activity);

        /**
         * Logic for when we've created the app to recents animation.
         */
        void onWindowAnimationCreated();
    }
}
