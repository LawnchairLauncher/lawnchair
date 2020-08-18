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

import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.anim.Interpolators.TOUCH_RESPONSE_INTERPOLATOR;
import static com.android.launcher3.anim.Interpolators.clampToProgress;
import static com.android.launcher3.statehandlers.DepthController.DEPTH;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_CLOSING;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.app.ActivityManager.RunningTaskInfo;
import android.util.Log;
import android.view.animation.Interpolator;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.quickstep.util.RemoteAnimationProvider;
import com.android.quickstep.util.SurfaceTransactionApplier;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

/**
 * Provider for the atomic (for 3-button mode) remote window animation from the app to the overview.
 *
 * @param <T> activity that contains the overview
 */
final class AppToOverviewAnimationProvider<T extends StatefulActivity<?>> extends
        RemoteAnimationProvider {

    private static final long RECENTS_LAUNCH_DURATION = 250;
    private static final String TAG = "AppToOverviewAnimationProvider";

    private final BaseActivityInterface<?, T> mActivityInterface;
    // The id of the currently running task that is transitioning to overview.
    private final RunningTaskInfo mTargetTask;
    private final RecentsAnimationDeviceState mDeviceState;

    private T mActivity;
    private RecentsView mRecentsView;

    AppToOverviewAnimationProvider(
            BaseActivityInterface<?, T> activityInterface, RunningTaskInfo targetTask,
            RecentsAnimationDeviceState deviceState) {
        mActivityInterface = activityInterface;
        mTargetTask = targetTask;
        mDeviceState = deviceState;
    }

    /**
     * Callback for when the activity is ready/initialized.
     *
     * @param activity the activity that is ready
     * @param wasVisible true if it was visible before
     */
    boolean onActivityReady(T activity, Boolean wasVisible) {
        activity.<RecentsView>getOverviewPanel().showCurrentTask(mTargetTask);
        AbstractFloatingView.closeAllOpenViews(activity, wasVisible);
        BaseActivityInterface.AnimationFactory factory = mActivityInterface.prepareRecentsUI(
                mDeviceState,
                wasVisible, (controller) -> {
                    controller.getNormalController().dispatchOnStart();
                    controller.getNormalController().getAnimationPlayer().end();
                });
        factory.createActivityInterface(RECENTS_LAUNCH_DURATION);
        factory.setRecentsAttachedToAppWindow(true, false);
        mActivity = activity;
        mRecentsView = mActivity.getOverviewPanel();
        return false;
    }

    /**
     * Create remote window animation from the currently running app to the overview panel.
     *
     * @param appTargets the target apps
     * @return animation from app to overview
     */
    @Override
    public AnimatorSet createWindowAnimation(RemoteAnimationTargetCompat[] appTargets,
            RemoteAnimationTargetCompat[] wallpaperTargets) {
        PendingAnimation pa = new PendingAnimation(RECENTS_LAUNCH_DURATION);
        if (mActivity == null) {
            Log.e(TAG, "Animation created, before activity");
            return pa.buildAnim();
        }

        mRecentsView.setRunningTaskIconScaledDown(true);
        pa.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                mActivityInterface.onSwipeUpToRecentsComplete();
                mRecentsView.animateUpRunningTaskIconScale();
            }
        });

        DepthController depthController = mActivityInterface.getDepthController();
        if (depthController != null) {
            pa.addFloat(depthController, DEPTH, BACKGROUND_APP.getDepth(mActivity),
                    OVERVIEW.getDepth(mActivity), TOUCH_RESPONSE_INTERPOLATOR);
        }

        RemoteAnimationTargets targets = new RemoteAnimationTargets(appTargets,
                wallpaperTargets, MODE_CLOSING);

        // Use the top closing app to determine the insets for the animation
        RemoteAnimationTargetCompat runningTaskTarget = mTargetTask == null ? null
                : targets.findTask(mTargetTask.taskId);
        if (runningTaskTarget == null) {
            Log.e(TAG, "No closing app");
            return pa.buildAnim();
        }

        TaskViewSimulator tsv = new TaskViewSimulator(mActivity, mRecentsView.getSizeStrategy());
        tsv.setDp(mActivity.getDeviceProfile());
        tsv.setPreview(runningTaskTarget);
        tsv.setLayoutRotation(mRecentsView.getPagedViewOrientedState().getTouchRotation(),
                mRecentsView.getPagedViewOrientedState().getDisplayRotation());

        TransformParams params = new TransformParams()
                .setTargetSet(targets)
                .setSyncTransactionApplier(new SurfaceTransactionApplier(mActivity.getRootView()));

        AnimatedFloat recentsAlpha = new AnimatedFloat(() -> { });
        params.setBaseBuilderProxy((builder, app, p)
                -> builder.withAlpha(recentsAlpha.value));

        Interpolator taskInterpolator;
        if (targets.isAnimatingHome()) {
            params.setHomeBuilderProxy((builder, app, p) -> builder.withAlpha(1 - p.getProgress()));

            taskInterpolator = TOUCH_RESPONSE_INTERPOLATOR;
            pa.addFloat(recentsAlpha, AnimatedFloat.VALUE, 0, 1, TOUCH_RESPONSE_INTERPOLATOR);
        } else {
            // When animation from app to recents, the recents layer is drawn on top of the app. To
            // prevent the overlap, we animate the task first and then quickly fade in the recents.
            taskInterpolator = clampToProgress(TOUCH_RESPONSE_INTERPOLATOR, 0, 0.8f);
            pa.addFloat(recentsAlpha, AnimatedFloat.VALUE, 0, 1,
                    clampToProgress(TOUCH_RESPONSE_INTERPOLATOR, 0.8f, 1));
        }

        pa.addFloat(params, TransformParams.PROGRESS, 0, 1, taskInterpolator);
        tsv.addAppToOverviewAnim(pa, taskInterpolator);
        pa.addOnFrameCallback(() -> tsv.apply(params));
        return pa.buildAnim();
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
