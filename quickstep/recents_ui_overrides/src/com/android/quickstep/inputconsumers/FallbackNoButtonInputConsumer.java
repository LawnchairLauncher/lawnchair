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
import static com.android.quickstep.WindowTransformSwipeHandler.MIN_PROGRESS_FOR_OVERVIEW;
import static com.android.quickstep.inputconsumers.FallbackNoButtonInputConsumer.GestureEndTarget.HOME;
import static com.android.quickstep.inputconsumers.FallbackNoButtonInputConsumer.GestureEndTarget.LAST_TASK;
import static com.android.quickstep.inputconsumers.FallbackNoButtonInputConsumer.GestureEndTarget.NEW_TASK;
import static com.android.quickstep.inputconsumers.FallbackNoButtonInputConsumer.GestureEndTarget.RECENTS;
import static com.android.quickstep.views.RecentsView.UPDATE_SYSUI_FLAGS_THRESHOLD;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;

import com.android.launcher3.R;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.quickstep.ActivityControlHelper.HomeAnimationFactory;
import com.android.quickstep.AnimatedFloat;
import com.android.quickstep.BaseSwipeUpHandler;
import com.android.quickstep.MultiStateCallback;
import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.RecentsActivity;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.SwipeSharedState;
import com.android.quickstep.fallback.FallbackRecentsView;
import com.android.quickstep.util.ObjectWrapper;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.SwipeAnimationTargetSet;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.ActivityOptionsCompat;
import com.android.systemui.shared.system.InputConsumerController;

public class FallbackNoButtonInputConsumer extends
        BaseSwipeUpHandler<RecentsActivity, FallbackRecentsView> {

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

    private final AnimatedFloat mLauncherAlpha = new AnimatedFloat(this::onLauncherAlphaChanged);

    private boolean mIsMotionPaused = false;
    private GestureEndTarget mEndTarget;

    private final boolean mInQuickSwitchMode;
    private final boolean mContinuingLastGesture;
    private final boolean mRunningOverHome;
    private final boolean mSwipeUpOverHome;

    private final RunningTaskInfo mRunningTaskInfo;

    private final PointF mEndVelocityPxPerMs = new PointF(0, 0.5f);
    private RunningWindowAnim mFinishAnimation;

    public FallbackNoButtonInputConsumer(Context context,
            OverviewComponentObserver overviewComponentObserver,
            RunningTaskInfo runningTaskInfo, RecentsModel recentsModel,
            InputConsumerController inputConsumer,
            boolean isLikelyToStartNewTask, boolean continuingLastGesture) {
        super(context, overviewComponentObserver, recentsModel, inputConsumer, runningTaskInfo.id);
        mLauncherAlpha.value = 1;

        mRunningTaskInfo = runningTaskInfo;
        mInQuickSwitchMode = isLikelyToStartNewTask || continuingLastGesture;
        mContinuingLastGesture = continuingLastGesture;
        mRunningOverHome = ActivityManagerWrapper.isHomeTask(runningTaskInfo);
        mSwipeUpOverHome = mRunningOverHome && !mInQuickSwitchMode;

        if (mSwipeUpOverHome) {
            mClipAnimationHelper.setBaseAlphaCallback((t, a) -> 1 - mLauncherAlpha.value);
        } else {
            mClipAnimationHelper.setBaseAlphaCallback((t, a) -> mLauncherAlpha.value);
        }

        initStateCallbacks();
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

        mRecentsView.setZoomProgress(1);

        if (!mContinuingLastGesture) {
            if (mRunningOverHome) {
                mRecentsView.onGestureAnimationStart(mRunningTaskInfo);
            } else {
                mRecentsView.onGestureAnimationStart(mRunningTaskId);
            }
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
        if (mInQuickSwitchMode || mSwipeUpOverHome) {
            return mOverviewComponentObserver.getOverviewIntent();
        } else {
            return mOverviewComponentObserver.getHomeIntent();
        }
    }

    @Override
    public void updateFinalShift() {
        mTransformParams.setProgress(mCurrentShift.value);
        mRecentsAnimationWrapper.setWindowThresholdCrossed(!mInQuickSwitchMode
                && (mCurrentShift.value > 1 - UPDATE_SYSUI_FLAGS_THRESHOLD));
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
        mEndVelocityPxPerMs.set(0, velocity.y / 1000);
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
        if (mFinishAnimation != null) {
            mFinishAnimation.end();
        }
    }

    private void onHandlerInvalidatedWithRecents() {
        mRecentsView.onGestureAnimationEnd();
        mRecentsView.setDisallowScrollToClearAll(false);
        mRecentsView.getClearAllButton().setVisibilityAlpha(1);
    }

    private void finishAnimationTargetSetAnimationComplete() {
        switch (mEndTarget) {
            case HOME: {
                if (mSwipeUpOverHome) {
                    mRecentsAnimationWrapper.finish(false, null, false);
                    // Send a home intent to clear the task stack
                    mContext.startActivity(mOverviewComponentObserver.getHomeIntent());
                } else {
                    mRecentsAnimationWrapper.finish(true, null, true);
                }
                break;
            }
            case LAST_TASK:
                mRecentsAnimationWrapper.finish(false, null, false);
                break;
            case RECENTS: {
                if (mSwipeUpOverHome) {
                    mRecentsAnimationWrapper.finish(true, null, true);
                    break;
                }

                ThumbnailData thumbnail =
                        mRecentsAnimationWrapper.targetSet.controller.screenshotTask(mRunningTaskId);
                mRecentsAnimationWrapper.setDeferCancelUntilNextTransition(true /* defer */,
                        false /* screenshot */);

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
        long duration = (long) (mEndTarget.mDurationMultiplier *
                Math.abs(endProgress - mCurrentShift.value));
        if (mRecentsView != null) {
            duration = Math.max(duration, mRecentsView.getScroller().getDuration());
        }
        if (mCurrentShift.value != endProgress || mInQuickSwitchMode) {
            AnimationSuccessListener endListener = new AnimationSuccessListener() {

                @Override
                public void onAnimationSuccess(Animator animator) {
                    finishAnimationTargetSetAnimationComplete();
                    mFinishAnimation = null;
                }
            };

            if (mEndTarget == HOME && !mRunningOverHome) {
                RectFSpringAnim anim = createWindowAnimationToHome(mCurrentShift.value, duration);
                anim.addAnimatorListener(endListener);
                anim.start(mEndVelocityPxPerMs);
                mFinishAnimation = RunningWindowAnim.wrap(anim);
            } else {

                AnimatorSet anim = new AnimatorSet();
                anim.play(mLauncherAlpha.animateToValue(
                        mLauncherAlpha.value, mEndTarget.mLauncherAlpha));
                anim.play(mCurrentShift.animateToValue(mCurrentShift.value, endProgress));

                anim.setDuration(duration);
                anim.addListener(endListener);
                anim.start();
                mFinishAnimation = RunningWindowAnim.wrap(anim);
            }

        } else {
            finishAnimationTargetSetAnimationComplete();
        }
    }

    @Override
    public void onRecentsAnimationStart(SwipeAnimationTargetSet targetSet) {
        super.onRecentsAnimationStart(targetSet);
        mRecentsAnimationWrapper.enableInputConsumer();

        if (mRunningOverHome) {
            mClipAnimationHelper.prepareAnimation(mDp, true);
        }
        applyTransformUnchecked();

        setStateOnUiThread(STATE_APP_CONTROLLER_RECEIVED);
    }

    @Override
    public void onRecentsAnimationCanceled() {
        mRecentsAnimationWrapper.setController(null);
        setStateOnUiThread(STATE_HANDLER_INVALIDATED);
    }

    /**
     * Creates an animation that transforms the current app window into the home app.
     * @param startProgress The progress of {@link #mCurrentShift} to start the window from.
     */
    private RectFSpringAnim createWindowAnimationToHome(float startProgress, long duration) {
        HomeAnimationFactory factory = new HomeAnimationFactory() {
            @Override
            public RectF getWindowTargetRect() {
                return HomeAnimationFactory.getDefaultWindowTargetRect(mDp);
            }

            @Override
            public AnimatorPlaybackController createActivityAnimationToHome() {
                AnimatorSet anim = new AnimatorSet();
                anim.play(mLauncherAlpha.animateToValue(mLauncherAlpha.value, 1));
                anim.setDuration(duration);
                return AnimatorPlaybackController.wrap(anim, duration);
            }
        };
        return createWindowAnimationToHome(startProgress, factory);
    }
}
