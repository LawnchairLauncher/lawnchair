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

import static com.android.launcher3.anim.Interpolators.ACCEL_2;
import static com.android.launcher3.anim.Interpolators.ACCEL_DEACCEL;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.quickstep.util.RemoteAnimationProvider.getLayer;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_CLOSING;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_OPENING;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;

import com.android.launcher3.BaseDraggingActivity;
import com.android.quickstep.util.MultiValueUpdateListener;
import com.android.quickstep.util.RemoteAnimationProvider;
import com.android.quickstep.util.RemoteAnimationTargetSet;
import com.android.quickstep.views.IconRecentsView;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat.SurfaceParams;

/**
 * Provider for the atomic remote window animation from the app to the overview.
 *
 * @param <T> activity that contains the overview
 */
final class AppToOverviewAnimationProvider<T extends BaseDraggingActivity> implements
        RemoteAnimationProvider {

    private static final long APP_TO_THUMBNAIL_FADE_DURATION = 50;
    private static final long APP_SCALE_DOWN_DURATION = 400;
    private static final String TAG = "AppToOverviewAnimationProvider";

    private final ActivityControlHelper<T> mHelper;
    private final int mTargetTaskId;
    private IconRecentsView mRecentsView;

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

        View thumbnailView = mRecentsView.getBottomThumbnailView();
        if (thumbnailView == null) {
            // This can be null if there were previously 0 tasks and the recycler view has not had
            // enough time to take in the data change, bind a new view, and lay out the new view.
            // TODO: Have a fallback to animate to
            if (Log.isLoggable(TAG, Log.WARN)) {
                Log.w(TAG, "No thumbnail view for running task. Using stub animation.");
            }
            anim.play(ValueAnimator.ofInt(0, 1).setDuration(getRecentsLaunchDuration()));
            return anim;
        }

        playAppScaleDownAnim(anim, closingAppTarget, recentsTarget, thumbnailView);

        return anim;
    }

    /**
     * Animate a closing app to scale down to the location of the thumbnail view in recents.
     *
     * @param anim animator set
     * @param appTarget the app surface thats closing
     * @param recentsTarget the surface containing recents
     * @param thumbnailView the thumbnail view to animate to
     */
    private void playAppScaleDownAnim(@NonNull AnimatorSet anim,
            @NonNull RemoteAnimationTargetCompat appTarget,
            @NonNull RemoteAnimationTargetCompat recentsTarget, @NonNull View thumbnailView) {

        // Identify where the entering remote app should animate to.
        Rect endRect = new Rect();
        thumbnailView.getGlobalVisibleRect(endRect);

        Rect appBounds = appTarget.sourceContainerBounds;

        ValueAnimator valueAnimator = ValueAnimator.ofInt(0, 1);
        valueAnimator.setDuration(APP_SCALE_DOWN_DURATION);

        SyncRtSurfaceTransactionApplierCompat surfaceApplier =
                new SyncRtSurfaceTransactionApplierCompat(thumbnailView);

        // Keep recents visible throughout the animation.
        SurfaceParams[] params = new SurfaceParams[2];
        // Closing app should stay on top.
        int boostedMode = MODE_CLOSING;
        params[0] = new SurfaceParams(recentsTarget.leash, 1f, null /* matrix */,
                null /* windowCrop */, getLayer(recentsTarget, boostedMode), 0 /* cornerRadius */);

        valueAnimator.addUpdateListener(new MultiValueUpdateListener() {
            private final FloatProp mScaleX;
            private final FloatProp mScaleY;
            private final FloatProp mTranslationX;
            private final FloatProp mTranslationY;
            private final FloatProp mAlpha;

            {
                // Scale down and move to view location.
                float endScaleX = ((float) endRect.width()) / appBounds.width();
                mScaleX = new FloatProp(1f, endScaleX, 0, APP_SCALE_DOWN_DURATION,
                        ACCEL_DEACCEL);
                float endScaleY = ((float) endRect.height()) / appBounds.height();
                mScaleY = new FloatProp(1f, endScaleY, 0, APP_SCALE_DOWN_DURATION,
                        ACCEL_DEACCEL);
                float endTranslationX = endRect.left -
                        (appBounds.width() - thumbnailView.getWidth()) / 2.0f;
                mTranslationX = new FloatProp(0, endTranslationX, 0, APP_SCALE_DOWN_DURATION,
                        ACCEL_DEACCEL);
                float endTranslationY = endRect.top -
                        (appBounds.height() - thumbnailView.getHeight()) / 2.0f;
                mTranslationY = new FloatProp(0, endTranslationY, 0, APP_SCALE_DOWN_DURATION,
                        ACCEL_DEACCEL);

                // Fade out quietly near the end to be replaced by the real view.
                mAlpha = new FloatProp(1.0f, 0,
                        APP_SCALE_DOWN_DURATION - APP_TO_THUMBNAIL_FADE_DURATION,
                        APP_TO_THUMBNAIL_FADE_DURATION, ACCEL_2);
            }

            @Override
            public void onUpdate(float percent) {
                Matrix m = new Matrix();
                m.setScale(mScaleX.value, mScaleY.value,
                        appBounds.width() / 2.0f, appBounds.height() / 2.0f);
                m.postTranslate(mTranslationX.value, mTranslationY.value);

                params[1] = new SurfaceParams(appTarget.leash, mAlpha.value, m,
                        null /* windowCrop */, getLayer(appTarget, boostedMode),
                        0 /* cornerRadius */);
                surfaceApplier.scheduleApply(params);
            }
        });
        anim.play(valueAnimator);
    }

    /**
     * Get duration of animation from app to overview.
     *
     * @return duration of animation
     */
    long getRecentsLaunchDuration() {
        return APP_SCALE_DOWN_DURATION;
    }
}
