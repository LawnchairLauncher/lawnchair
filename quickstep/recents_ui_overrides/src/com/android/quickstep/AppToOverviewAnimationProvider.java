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
import static com.android.launcher3.anim.Interpolators.TOUCH_RESPONSE_INTERPOLATOR;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_CLOSING;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_OPENING;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.quickstep.util.ClipAnimationHelper;
import com.android.quickstep.util.RemoteAnimationProvider;
import com.android.quickstep.util.RemoteAnimationTargetSet;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat;
import com.android.systemui.shared.system.TransactionCompat;

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
    // The id of the currently running task that is transitioning to overview.
    private final int mTargetTaskId;

    private T mActivity;
    private RecentsView mRecentsView;

    AppToOverviewAnimationProvider(ActivityControlHelper<T> helper, int targetTaskId) {
        mHelper = helper;
        mTargetTaskId = targetTaskId;
    }

    /**
     * Callback for when the activity is ready/initialized.
     *
     * @param activity the activity that is ready
     * @param wasVisible true if it was visible before
     */
    boolean onActivityReady(T activity, Boolean wasVisible) {
        activity.<RecentsView>getOverviewPanel().showCurrentTask(mTargetTaskId);
        AbstractFloatingView.closeAllOpenViews(activity, wasVisible);
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
        factory.setRecentsAttachedToAppWindow(true, false);
        mActivity = activity;
        mRecentsView = mActivity.getOverviewPanel();
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
        if (mRecentsView != null) {
            mRecentsView.setRunningTaskIconScaledDown(true);
        }
        AnimatorSet anim = new AnimatorSet();
        anim.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                mHelper.onSwipeUpToRecentsComplete(mActivity);
                if (mRecentsView != null) {
                    mRecentsView.animateUpRunningTaskIconScale();
                }
            }
        });
        if (mActivity == null) {
            Log.e(TAG, "Animation created, before activity");
            anim.play(ValueAnimator.ofInt(0, 1).setDuration(RECENTS_LAUNCH_DURATION));
            return anim;
        }

        RemoteAnimationTargetSet targetSet =
                new RemoteAnimationTargetSet(targetCompats, MODE_CLOSING);

        // Use the top closing app to determine the insets for the animation
        RemoteAnimationTargetCompat runningTaskTarget = targetSet.findTask(mTargetTaskId);
        if (runningTaskTarget == null) {
            Log.e(TAG, "No closing app");
            anim.play(ValueAnimator.ofInt(0, 1).setDuration(RECENTS_LAUNCH_DURATION));
            return anim;
        }

        final ClipAnimationHelper clipHelper = new ClipAnimationHelper(mActivity);

        // At this point, the activity is already started and laid-out. Get the home-bounds
        // relative to the screen using the rootView of the activity.
        int loc[] = new int[2];
        View rootView = mActivity.getRootView();
        rootView.getLocationOnScreen(loc);
        Rect homeBounds = new Rect(loc[0], loc[1],
                loc[0] + rootView.getWidth(), loc[1] + rootView.getHeight());
        clipHelper.updateSource(homeBounds, runningTaskTarget);

        Rect targetRect = new Rect();
        mHelper.getSwipeUpDestinationAndLength(mActivity.getDeviceProfile(), mActivity, targetRect);
        clipHelper.updateTargetRect(targetRect);
        clipHelper.prepareAnimation(mActivity.getDeviceProfile(), false /* isOpening */);

        ClipAnimationHelper.TransformParams params = new ClipAnimationHelper.TransformParams()
                .setSyncTransactionApplier(new SyncRtSurfaceTransactionApplierCompat(rootView));
        ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1);
        valueAnimator.setDuration(RECENTS_LAUNCH_DURATION);
        valueAnimator.setInterpolator(TOUCH_RESPONSE_INTERPOLATOR);
        valueAnimator.addUpdateListener((v) -> {
            params.setProgress((float) v.getAnimatedValue());
            clipHelper.applyTransform(targetSet, params);
        });

        if (targetSet.isAnimatingHome()) {
            // If we are animating home, fade in the opening targets
            RemoteAnimationTargetSet openingSet =
                    new RemoteAnimationTargetSet(targetCompats, MODE_OPENING);

            TransactionCompat transaction = new TransactionCompat();
            valueAnimator.addUpdateListener((v) -> {
                for (RemoteAnimationTargetCompat app : openingSet.apps) {
                    transaction.setAlpha(app.leash, (float) v.getAnimatedValue());
                }
                transaction.apply();
            });
        }
        anim.play(valueAnimator);
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
