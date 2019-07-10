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
package com.android.quickstep.inputconsumers;

import static com.android.quickstep.RecentsActivity.EXTRA_TASK_ID;
import static com.android.quickstep.RecentsActivity.EXTRA_THUMBNAIL;
import static com.android.quickstep.WindowTransformSwipeHandler.MIN_PROGRESS_FOR_OVERVIEW;
import static com.android.quickstep.inputconsumers.FallbackNoButtonInputConsumer.GestureEndTarget.HOME;
import static com.android.quickstep.inputconsumers.FallbackNoButtonInputConsumer.GestureEndTarget.LAST_TASK;
import static com.android.quickstep.inputconsumers.FallbackNoButtonInputConsumer.GestureEndTarget.RECENTS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.WindowManager;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.R;
import com.android.quickstep.ActivityControlHelper;
import com.android.quickstep.AnimatedFloat;
import com.android.quickstep.BaseSwipeUpHandler;
import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.util.ObjectWrapper;
import com.android.quickstep.util.SwipeAnimationTargetSet;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

public class FallbackNoButtonInputConsumer extends BaseSwipeUpHandler {

    public enum GestureEndTarget {
        HOME(3, 100, 1),
        RECENTS(1, 300, 0),
        LAST_TASK(0, 150, 1);

        private final float mEndProgress;
        private final long mDurationMultiplier;
        private final float mLauncherAlpha;

        GestureEndTarget(float endProgress, long durationMultiplier, float launcherAlpha) {
            mEndProgress = endProgress;
            mDurationMultiplier = durationMultiplier;
            mLauncherAlpha = launcherAlpha;
        }
    }

    private final ActivityControlHelper mActivityControlHelper;
    private final OverviewComponentObserver mOverviewComponentObserver;
    private final int mRunningTaskId;

    private final DeviceProfile mDP;
    private final Rect mTargetRect = new Rect();

    private final AnimatedFloat mLauncherAlpha = new AnimatedFloat(this::onLauncherAlphaChanged);

    private SwipeAnimationTargetSet mSwipeAnimationTargetSet;

    private boolean mIsMotionPaused = false;
    private GestureEndTarget mEndTarget;

    public FallbackNoButtonInputConsumer(Context context,
            OverviewComponentObserver overviewComponentObserver,
            RunningTaskInfo runningTaskInfo) {
        super(context);
        mOverviewComponentObserver = overviewComponentObserver;
        mActivityControlHelper = overviewComponentObserver.getActivityControlHelper();
        mRunningTaskId = runningTaskInfo.id;
        mDP = InvariantDeviceProfile.INSTANCE.get(context).getDeviceProfile(context).copy(context);
        mLauncherAlpha.value = 1;

        mClipAnimationHelper.setBaseAlphaCallback((t, a) -> mLauncherAlpha.value);
        initTransitionTarget();
    }

    private void onLauncherAlphaChanged() {
        if (mSwipeAnimationTargetSet != null && mEndTarget == null) {
            mClipAnimationHelper.applyTransform(mSwipeAnimationTargetSet, mTransformParams);
        }
    }

    @Override
    public void onMotionPauseChanged(boolean isPaused) {
        mIsMotionPaused = isPaused;
        mLauncherAlpha.animateToValue(mLauncherAlpha.value, isPaused ? 0 : 1)
                .setDuration(150).start();
        performHapticFeedback();
    }

    @Override
    public void updateFinalShift() {
        mTransformParams.setProgress(mCurrentShift.value);
        if (mSwipeAnimationTargetSet != null) {
            mClipAnimationHelper.applyTransform(mSwipeAnimationTargetSet, mTransformParams);
        }
    }

    @Override
    public void onGestureCancelled() {
        updateDisplacement(0);
        mEndTarget = LAST_TASK;
        finishAnimationTargetSetAnimationComplete();
    }

    @Override
    public void onGestureEnded(float endVelocity, PointF velocity, PointF downPos) {
        float flingThreshold = mContext.getResources()
                .getDimension(R.dimen.quickstep_fling_threshold_velocity);
        boolean isFling = Math.abs(endVelocity) > flingThreshold;

        if (isFling) {
            mEndTarget = endVelocity < 0 ? HOME : LAST_TASK;
        } else if (mIsMotionPaused) {
            mEndTarget = RECENTS;
        } else {
            mEndTarget = mCurrentShift.value >= MIN_PROGRESS_FOR_OVERVIEW ? HOME : LAST_TASK;
        }
        if (mSwipeAnimationTargetSet != null) {
            finishAnimationTargetSet();
        }
    }

    private void finishAnimationTargetSetAnimationComplete() {
        switch (mEndTarget) {
            case HOME:
                mSwipeAnimationTargetSet.finishController(true, null, true);
                break;
            case LAST_TASK:
                mSwipeAnimationTargetSet.finishController(false, null, false);
                break;
            case RECENTS: {
                ThumbnailData thumbnail =
                        mSwipeAnimationTargetSet.controller.screenshotTask(mRunningTaskId);
                mSwipeAnimationTargetSet.controller.setCancelWithDeferredScreenshot(true);

                ActivityOptions options = ActivityOptions.makeCustomAnimation(mContext, 0, 0);
                Bundle extras = new Bundle();
                extras.putBinder(EXTRA_THUMBNAIL, new ObjectWrapper<>(thumbnail));
                extras.putInt(EXTRA_TASK_ID, mRunningTaskId);

                Intent intent = new Intent(mOverviewComponentObserver.getOverviewIntent())
                        .putExtras(extras);
                mContext.startActivity(intent, options.toBundle());
                mSwipeAnimationTargetSet.controller.cleanupScreenshot();
                break;
            }
        }
        if (mGestureEndCallback != null) {
            mGestureEndCallback.run();
        }
    }

    private void finishAnimationTargetSet() {
        float endProgress = mEndTarget.mEndProgress;

        if (mCurrentShift.value != endProgress) {
            AnimatorSet anim = new AnimatorSet();
            anim.play(mLauncherAlpha.animateToValue(
                    mLauncherAlpha.value, mEndTarget.mLauncherAlpha));
            anim.play(mCurrentShift.animateToValue(mCurrentShift.value, endProgress));

            anim.setDuration((long) (mEndTarget.mDurationMultiplier *
                    Math.abs(endProgress - mCurrentShift.value)));
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    finishAnimationTargetSetAnimationComplete();
                }
            });
            anim.start();
        } else {
            finishAnimationTargetSetAnimationComplete();
        }
    }

    @Override
    public void onRecentsAnimationStart(SwipeAnimationTargetSet targetSet) {
        mSwipeAnimationTargetSet = targetSet;
        Rect overviewStackBounds = new Rect(0, 0, mDP.widthPx, mDP.heightPx);
        RemoteAnimationTargetCompat runningTaskTarget = targetSet.findTask(mRunningTaskId);

        mDP.updateIsSeascape(mContext.getSystemService(WindowManager.class));
        if (targetSet.homeContentInsets != null) {
            mDP.updateInsets(targetSet.homeContentInsets);
        }

        if (runningTaskTarget != null) {
            mClipAnimationHelper.updateSource(overviewStackBounds, runningTaskTarget);
        }
        mClipAnimationHelper.prepareAnimation(mDP, false /* isOpening */);
        initTransitionTarget();
        mClipAnimationHelper.applyTransform(mSwipeAnimationTargetSet, mTransformParams);

        if (mEndTarget != null) {
            finishAnimationTargetSet();
        }
    }

    private void initTransitionTarget() {
        mTransitionDragLength = mActivityControlHelper.getSwipeUpDestinationAndLength(
                mDP, mContext, mTargetRect);
        mDragLengthFactor = (float) mDP.heightPx / mTransitionDragLength;
        mClipAnimationHelper.updateTargetRect(mTargetRect);
    }

    @Override
    public void onRecentsAnimationCanceled() { }
}
