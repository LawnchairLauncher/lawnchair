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

import static com.android.quickstep.MultiStateCallback.DEBUG_STATES;
import static com.android.quickstep.RecentsActivity.EXTRA_TASK_ID;
import static com.android.quickstep.RecentsActivity.EXTRA_THUMBNAIL;
import static com.android.quickstep.inputconsumers.FallbackNoButtonInputConsumer.GestureEndTarget.NEW_TASK;
import static com.android.quickstep.WindowTransformSwipeHandler.MIN_PROGRESS_FOR_OVERVIEW;
import static com.android.quickstep.inputconsumers.FallbackNoButtonInputConsumer.GestureEndTarget.HOME;
import static com.android.quickstep.inputconsumers.FallbackNoButtonInputConsumer.GestureEndTarget.LAST_TASK;
import static com.android.quickstep.inputconsumers.FallbackNoButtonInputConsumer.GestureEndTarget.RECENTS;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.WindowManager;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.quickstep.AnimatedFloat;
import com.android.quickstep.BaseSwipeUpHandler;
import com.android.quickstep.MultiStateCallback;
import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.RecentsActivity;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.SwipeSharedState;
import com.android.quickstep.fallback.FallbackRecentsView;
import com.android.quickstep.util.ObjectWrapper;
import com.android.quickstep.util.SwipeAnimationTargetSet;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityOptionsCompat;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

public class FallbackNoButtonInputConsumer extends BaseSwipeUpHandler<RecentsActivity> {

    private static final String[] STATE_NAMES = DEBUG_STATES ? new String[5] : null;

    private static int getFlagForIndex(int index, String name) {
        if (DEBUG_STATES) {
            STATE_NAMES[index] = name;
        }
        return 1 << index;
    }

    private static final int STATE_RECENTS_PRESENT =
            getFlagForIndex(0, "STATE_RECENTS_PRESENT");
    private static final int STATE_HANDLER_INVALIDATED =
            getFlagForIndex(1, "STATE_HANDLER_INVALIDATED");

    private static final int STATE_GESTURE_CANCELLED =
            getFlagForIndex(2, "STATE_GESTURE_CANCELLED");
    private static final int STATE_GESTURE_COMPLETED =
            getFlagForIndex(3, "STATE_GESTURE_COMPLETED");
    private static final int STATE_APP_CONTROLLER_RECEIVED =
            getFlagForIndex(4, "STATE_APP_CONTROLLER_RECEIVED");

    public enum GestureEndTarget {
        HOME(3, 100, 1),
        RECENTS(1, 300, 0),
        LAST_TASK(0, 150, 1),
        NEW_TASK(0, 150, 1);

        private final float mEndProgress;
        private final long mDurationMultiplier;
        private final float mLauncherAlpha;

        GestureEndTarget(float endProgress, long durationMultiplier, float launcherAlpha) {
            mEndProgress = endProgress;
            mDurationMultiplier = durationMultiplier;
            mLauncherAlpha = launcherAlpha;
        }
    }

    private final int mRunningTaskId;

    private final Rect mTargetRect = new Rect();

    private final AnimatedFloat mLauncherAlpha = new AnimatedFloat(this::onLauncherAlphaChanged);

    private boolean mIsMotionPaused = false;
    private GestureEndTarget mEndTarget;

    private final boolean mInQuickSwitchMode;
    private final boolean mContinuingLastGesture;

    private Animator mFinishAnimation;

    public FallbackNoButtonInputConsumer(Context context,
            OverviewComponentObserver overviewComponentObserver,
            RunningTaskInfo runningTaskInfo, RecentsModel recentsModel,
            InputConsumerController inputConsumer,
            boolean isLikelyToStartNewTask, boolean continuingLastGesture) {
        super(context, overviewComponentObserver, recentsModel, inputConsumer);
        mRunningTaskId = runningTaskInfo.id;
        mDp = InvariantDeviceProfile.INSTANCE.get(context).getDeviceProfile(context).copy(context);
        mLauncherAlpha.value = 1;

        mInQuickSwitchMode = isLikelyToStartNewTask || continuingLastGesture;
        mContinuingLastGesture = continuingLastGesture;
        mClipAnimationHelper.setBaseAlphaCallback((t, a) -> mLauncherAlpha.value);
        initStateCallbacks();
        initTransitionTarget();
    }

    private void initStateCallbacks() {
        mStateCallback = new MultiStateCallback(STATE_NAMES);

        mStateCallback.addCallback(STATE_HANDLER_INVALIDATED,
                this::onHandlerInvalidated);
        mStateCallback.addCallback(STATE_RECENTS_PRESENT | STATE_HANDLER_INVALIDATED,
                this::onHandlerInvalidatedWithRecents);

        mStateCallback.addCallback(STATE_GESTURE_CANCELLED | STATE_APP_CONTROLLER_RECEIVED,
                this::finishAnimationTargetSetAnimationComplete);

        if (mInQuickSwitchMode) {
            mStateCallback.addCallback(STATE_GESTURE_COMPLETED | STATE_APP_CONTROLLER_RECEIVED
                            | STATE_RECENTS_PRESENT,
                    this::finishAnimationTargetSet);
        } else {
            mStateCallback.addCallback(STATE_GESTURE_COMPLETED | STATE_APP_CONTROLLER_RECEIVED,
                    this::finishAnimationTargetSet);
        }
    }

    private void onLauncherAlphaChanged() {
        if (mRecentsAnimationWrapper.targetSet != null && mEndTarget == null) {
            applyTransformUnchecked();
        }
    }

    @Override
    protected boolean onActivityInit(final RecentsActivity activity, Boolean alreadyOnHome) {
        mActivity = activity;
        mRecentsView = activity.getOverviewPanel();
        linkRecentsViewScroll();
        mRecentsView.setDisallowScrollToClearAll(true);
        mRecentsView.getClearAllButton().setVisibilityAlpha(0);

        ((FallbackRecentsView) mRecentsView).setZoomProgress(1);

        if (!mContinuingLastGesture) {
            mRecentsView.onGestureAnimationStart(mRunningTaskId);
        }
        setStateOnUiThread(STATE_RECENTS_PRESENT);
        return true;
    }

    @Override
    protected boolean moveWindowWithRecentsScroll() {
        return mInQuickSwitchMode;
    }

    @Override
    public void initWhenReady() {
        if (mInQuickSwitchMode) {
            // Only init if we are in quickswitch mode
            super.initWhenReady();
        }
    }

    @Override
    public void updateDisplacement(float displacement) {
        if (!mInQuickSwitchMode) {
            super.updateDisplacement(displacement);
        }
    }

    @Override
    protected InputConsumer createNewInputProxyHandler() {
        // Just consume all input on the active task
        return InputConsumer.NO_OP;
    }

    @Override
    public void onMotionPauseChanged(boolean isPaused) {
        if (!mInQuickSwitchMode) {
            mIsMotionPaused = isPaused;
            mLauncherAlpha.animateToValue(mLauncherAlpha.value, isPaused ? 0 : 1)
                    .setDuration(150).start();
            performHapticFeedback();
        }
    }

    @Override
    public Intent getLaunchIntent() {
        if (mInQuickSwitchMode) {
            return mOverviewComponentObserver.getOverviewIntent();
        } else {
            return mOverviewComponentObserver.getHomeIntent();
        }
    }

    @Override
    public void updateFinalShift() {
        mTransformParams.setProgress(mCurrentShift.value);
        if (mRecentsAnimationWrapper.targetSet != null) {
            applyTransformUnchecked();
        }
    }

    @Override
    public void onGestureCancelled() {
        updateDisplacement(0);
        mEndTarget = LAST_TASK;
        setStateOnUiThread(STATE_GESTURE_CANCELLED);
    }

    @Override
    public void onGestureEnded(float endVelocity, PointF velocity, PointF downPos) {
        if (mInQuickSwitchMode) {
            // For now set it to non-null, it will be reset before starting the animation
            mEndTarget = LAST_TASK;
        } else {
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
        }
        setStateOnUiThread(STATE_GESTURE_COMPLETED);
    }

    @Override
    public void onConsumerAboutToBeSwitched(SwipeSharedState sharedState) {
        if (mInQuickSwitchMode && mEndTarget != null) {
            sharedState.canGestureBeContinued = true;
            sharedState.goingToLauncher = false;

            mCanceled = true;
            mCurrentShift.cancelAnimation();
            if (mFinishAnimation != null) {
                mFinishAnimation.cancel();
            }

            if (mRecentsView != null) {
                if (mFinishingRecentsAnimationForNewTaskId != -1) {
                    TaskView newRunningTaskView = mRecentsView.getTaskView(
                            mFinishingRecentsAnimationForNewTaskId);
                    int newRunningTaskId = newRunningTaskView != null
                            ? newRunningTaskView.getTask().key.id
                            : -1;
                    mRecentsView.setCurrentTask(newRunningTaskId);
                    sharedState.setRecentsAnimationFinishInterrupted(newRunningTaskId);
                }
                mRecentsView.setOnScrollChangeListener(null);
            }
        } else {
            setStateOnUiThread(STATE_HANDLER_INVALIDATED);
        }
    }


    private void onHandlerInvalidated() {
        mActivityInitListener.unregister();
        if (mGestureEndCallback != null) {
            mGestureEndCallback.run();
        }
    }

    private void onHandlerInvalidatedWithRecents() {
        mRecentsView.onGestureAnimationEnd();
        mRecentsView.setDisallowScrollToClearAll(false);
        mRecentsView.getClearAllButton().setVisibilityAlpha(1);
    }


    private void finishAnimationTargetSetAnimationComplete() {
        switch (mEndTarget) {
            case HOME:
                mRecentsAnimationWrapper.finish(true, null, true);
                break;
            case LAST_TASK:
                mRecentsAnimationWrapper.finish(false, null, false);
                break;
            case RECENTS: {
                ThumbnailData thumbnail =
                        mRecentsAnimationWrapper.targetSet.controller.screenshotTask(mRunningTaskId);
                mRecentsAnimationWrapper.setCancelWithDeferredScreenshot(true);

                ActivityOptions options = ActivityOptions.makeCustomAnimation(mContext, 0, 0);
                ActivityOptionsCompat.setFreezeRecentTasksList(options);

                Bundle extras = new Bundle();
                extras.putBinder(EXTRA_THUMBNAIL, new ObjectWrapper<>(thumbnail));
                extras.putInt(EXTRA_TASK_ID, mRunningTaskId);

                Intent intent = new Intent(mOverviewComponentObserver.getOverviewIntent())
                        .putExtras(extras);
                mContext.startActivity(intent, options.toBundle());
                mRecentsAnimationWrapper.targetSet.controller.cleanupScreenshot();
                break;
            }
            case NEW_TASK: {
                startNewTask(STATE_HANDLER_INVALIDATED, b -> {});
                break;
            }
        }

        setStateOnUiThread(STATE_HANDLER_INVALIDATED);
    }

    private void finishAnimationTargetSet() {
        if (mInQuickSwitchMode) {
            // Recalculate the end target, some views might have been initialized after
            // gesture has ended.
            if (mRecentsView == null || !mRecentsAnimationWrapper.hasTargets()) {
                mEndTarget = LAST_TASK;
            } else {
                final int runningTaskIndex = mRecentsView.getRunningTaskIndex();
                final int taskToLaunch = mRecentsView.getNextPage();
                mEndTarget = (runningTaskIndex >= 0 && taskToLaunch != runningTaskIndex)
                        ? NEW_TASK : LAST_TASK;
            }
        }

        float endProgress = mEndTarget.mEndProgress;

        if (mCurrentShift.value != endProgress || mInQuickSwitchMode) {
            AnimatorSet anim = new AnimatorSet();
            anim.play(mLauncherAlpha.animateToValue(
                    mLauncherAlpha.value, mEndTarget.mLauncherAlpha));
            anim.play(mCurrentShift.animateToValue(mCurrentShift.value, endProgress));


            long duration = (long) (mEndTarget.mDurationMultiplier *
                    Math.abs(endProgress - mCurrentShift.value));
            if (mRecentsView != null) {
                duration = Math.max(duration, mRecentsView.getScroller().getDuration());
            }

            anim.setDuration(duration);
            anim.addListener(new AnimationSuccessListener() {

                @Override
                public void onAnimationSuccess(Animator animator) {
                    finishAnimationTargetSetAnimationComplete();
                    mFinishAnimation = null;
                }
            });
            anim.start();
            mFinishAnimation = anim;
        } else {
            finishAnimationTargetSetAnimationComplete();
        }
    }

    @Override
    public void onRecentsAnimationStart(SwipeAnimationTargetSet targetSet) {
        mRecentsAnimationWrapper.setController(targetSet);
        mRecentsAnimationWrapper.enableInputConsumer();
        Rect overviewStackBounds = new Rect(0, 0, mDp.widthPx, mDp.heightPx);
        RemoteAnimationTargetCompat runningTaskTarget = targetSet.findTask(mRunningTaskId);

        mDp.updateIsSeascape(mContext.getSystemService(WindowManager.class));
        if (targetSet.homeContentInsets != null) {
            mDp.updateInsets(targetSet.homeContentInsets);
        }

        if (runningTaskTarget != null) {
            mClipAnimationHelper.updateSource(overviewStackBounds, runningTaskTarget);
        }
        mClipAnimationHelper.prepareAnimation(mDp, false /* isOpening */);
        initTransitionTarget();
        applyTransformUnchecked();

        setStateOnUiThread(STATE_APP_CONTROLLER_RECEIVED);
    }

    private void initTransitionTarget() {
        mTransitionDragLength = mActivityControlHelper.getSwipeUpDestinationAndLength(
                mDp, mContext, mTargetRect);
        mDragLengthFactor = (float) mDp.heightPx / mTransitionDragLength;
        mClipAnimationHelper.updateTargetRect(mTargetRect);
    }

    @Override
    public void onRecentsAnimationCanceled() {
        mRecentsAnimationWrapper.setController(null);
        setStateOnUiThread(STATE_HANDLER_INVALIDATED);
    }
}
