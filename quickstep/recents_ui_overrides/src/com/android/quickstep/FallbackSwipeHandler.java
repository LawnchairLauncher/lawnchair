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

import static com.android.launcher3.anim.Interpolators.ACCEL_1_5;
import static com.android.launcher3.anim.Interpolators.ACCEL_2;
import static com.android.quickstep.GestureState.GestureEndTarget.HOME;
import static com.android.quickstep.GestureState.GestureEndTarget.LAST_TASK;
import static com.android.quickstep.GestureState.GestureEndTarget.NEW_TASK;
import static com.android.quickstep.GestureState.GestureEndTarget.RECENTS;
import static com.android.quickstep.MultiStateCallback.DEBUG_STATES;
import static com.android.quickstep.RecentsActivity.EXTRA_TASK_ID;
import static com.android.quickstep.RecentsActivity.EXTRA_THUMBNAIL;
import static com.android.quickstep.views.RecentsView.UPDATE_SYSUI_FLAGS_THRESHOLD;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.ArrayMap;
import android.view.MotionEvent;

import com.android.launcher3.R;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.util.ObjectWrapper;
import com.android.quickstep.BaseActivityInterface.HomeAnimationFactory;
import com.android.quickstep.GestureState.GestureEndTarget;
import com.android.quickstep.fallback.FallbackRecentsView;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.ActivityOptionsCompat;
import com.android.systemui.shared.system.InputConsumerController;

/**
 * Handles the navigation gestures when a 3rd party launcher is the default home activity.
 */
public class FallbackSwipeHandler extends BaseSwipeUpHandler<RecentsActivity, FallbackRecentsView> {

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

    public static class EndTargetAnimationParams {
        private final float mEndProgress;
        private final long mDurationMultiplier;
        private final float mLauncherAlpha;

        EndTargetAnimationParams(float endProgress, long durationMultiplier, float launcherAlpha) {
            mEndProgress = endProgress;
            mDurationMultiplier = durationMultiplier;
            mLauncherAlpha = launcherAlpha;
        }
    }
    private final ArrayMap<GestureEndTarget, EndTargetAnimationParams>
            mEndTargetAnimationParams = new ArrayMap();

    private final AnimatedFloat mLauncherAlpha = new AnimatedFloat(this::onLauncherAlphaChanged);

    private boolean mOverviewThresholdPassed = false;

    private final boolean mInQuickSwitchMode;
    private final boolean mContinuingLastGesture;
    private final boolean mRunningOverHome;
    private final boolean mSwipeUpOverHome;
    private boolean mTouchedHomeDuringTransition;

    private final PointF mEndVelocityPxPerMs = new PointF(0, 0.5f);
    private RunningWindowAnim mFinishAnimation;

    public FallbackSwipeHandler(Context context, RecentsAnimationDeviceState deviceState,
            GestureState gestureState, InputConsumerController inputConsumer,
            boolean isLikelyToStartNewTask, boolean continuingLastGesture) {
        super(context, deviceState, gestureState, inputConsumer);

        mInQuickSwitchMode = isLikelyToStartNewTask || continuingLastGesture;
        mContinuingLastGesture = continuingLastGesture;
        mRunningOverHome = ActivityManagerWrapper.isHomeTask(mGestureState.getRunningTask());
        mSwipeUpOverHome = mRunningOverHome && !mInQuickSwitchMode;

        // Keep the home launcher invisible until we decide to land there.
        mLauncherAlpha.value = mRunningOverHome ? 1 : 0;
        if (mSwipeUpOverHome) {
            mAppWindowAnimationHelper.setBaseAlphaCallback((t, a) -> 1 - mLauncherAlpha.value);
        } else {
            mAppWindowAnimationHelper.setBaseAlphaCallback((t, a) -> mLauncherAlpha.value);
        }

        // Going home has an extra long progress to ensure that it animates into the screen
        mEndTargetAnimationParams.put(HOME, new EndTargetAnimationParams(3, 100, 1));
        mEndTargetAnimationParams.put(RECENTS, new EndTargetAnimationParams(1, 300, 0));
        mEndTargetAnimationParams.put(LAST_TASK, new EndTargetAnimationParams(0, 150, 1));
        mEndTargetAnimationParams.put(NEW_TASK, new EndTargetAnimationParams(0, 150, 1));

        initAfterSubclassConstructor();
        initStateCallbacks();
    }

    private void initStateCallbacks() {
        mStateCallback = new MultiStateCallback(STATE_NAMES);

        mStateCallback.runOnceAtState(STATE_HANDLER_INVALIDATED,
                this::onHandlerInvalidated);
        mStateCallback.runOnceAtState(STATE_RECENTS_PRESENT | STATE_HANDLER_INVALIDATED,
                this::onHandlerInvalidatedWithRecents);

        mStateCallback.runOnceAtState(STATE_GESTURE_CANCELLED | STATE_APP_CONTROLLER_RECEIVED,
                this::finishAnimationTargetSetAnimationComplete);

        if (mInQuickSwitchMode) {
            mStateCallback.runOnceAtState(STATE_GESTURE_COMPLETED | STATE_APP_CONTROLLER_RECEIVED
                            | STATE_RECENTS_PRESENT,
                    this::finishAnimationTargetSet);
        } else {
            mStateCallback.runOnceAtState(STATE_GESTURE_COMPLETED | STATE_APP_CONTROLLER_RECEIVED,
                    this::finishAnimationTargetSet);
        }
    }

    private void onLauncherAlphaChanged() {
        if (mRecentsAnimationTargets != null && mGestureState.getEndTarget() == null) {
            applyTransformUnchecked();
        }
    }

    @Override
    protected boolean onActivityInit(Boolean alreadyOnHome) {
        super.onActivityInit(alreadyOnHome);
        mActivity = mActivityInterface.getCreatedActivity();
        mRecentsView = mActivity.getOverviewPanel();
        mRecentsView.setOnPageTransitionEndCallback(null);
        linkRecentsViewScroll();
        mRecentsView.setDisallowScrollToClearAll(true);
        mRecentsView.getClearAllButton().setVisibilityAlpha(0);
        mRecentsView.setZoomProgress(1);

        if (!mContinuingLastGesture) {
            if (mRunningOverHome) {
                mRecentsView.onGestureAnimationStart(mGestureState.getRunningTask());
            } else {
                mRecentsView.onGestureAnimationStart(mGestureState.getRunningTaskId());
            }
        }
        mStateCallback.setStateOnUiThread(STATE_RECENTS_PRESENT);
        mDeviceState.enableMultipleRegions(false);
        return true;
    }

    @Override
    protected boolean moveWindowWithRecentsScroll() {
        return mInQuickSwitchMode;
    }

    @Override
    public void initWhenReady(Intent intent) {
        if (mInQuickSwitchMode) {
            // Only init if we are in quickswitch mode
            super.initWhenReady(intent);
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
        return new InputConsumer() {
            @Override
            public int getType() {
                return InputConsumer.TYPE_NO_OP;
            }

            @Override
            public void onMotionEvent(MotionEvent ev) {
                mTouchedHomeDuringTransition = true;
            }
        };
    }

    @Override
    public void onMotionPauseChanged(boolean isPaused) {
        if (!mInQuickSwitchMode && mDeviceState.isFullyGesturalNavMode()) {
            updateOverviewThresholdPassed(isPaused);
        }
    }

    private void updateOverviewThresholdPassed(boolean passed) {
        if (passed != mOverviewThresholdPassed) {
            mOverviewThresholdPassed = passed;
            if (mSwipeUpOverHome) {
                mLauncherAlpha.animateToValue(mLauncherAlpha.value, passed ? 0 : 1)
                        .setDuration(150).start();
            }
            performHapticFeedback();
        }
    }

    @Override
    public Intent getLaunchIntent() {
        if (mInQuickSwitchMode || mSwipeUpOverHome || !mDeviceState.isFullyGesturalNavMode()) {
            return mGestureState.getOverviewIntent();
        } else {
            return mGestureState.getHomeIntent();
        }
    }

    @Override
    public void updateFinalShift() {
        mTransformParams.setProgress(mCurrentShift.value);
        if (mRecentsAnimationController != null) {
            boolean swipeUpThresholdPassed = mCurrentShift.value > 1 - UPDATE_SYSUI_FLAGS_THRESHOLD;
            mRecentsAnimationController.setUseLauncherSystemBarFlags(mInQuickSwitchMode
                    || swipeUpThresholdPassed);
            mRecentsAnimationController.setSplitScreenMinimized(!mInQuickSwitchMode
                    && swipeUpThresholdPassed);
        }

        if (!mInQuickSwitchMode && !mDeviceState.isFullyGesturalNavMode()) {
            updateOverviewThresholdPassed(mCurrentShift.value >= MIN_PROGRESS_FOR_OVERVIEW);
        }

        if (mRecentsAnimationTargets != null) {
            applyTransformUnchecked();
        }
    }

    @Override
    public void onGestureCancelled() {
        updateDisplacement(0);
        mGestureState.setEndTarget(LAST_TASK);
        mStateCallback.setStateOnUiThread(STATE_GESTURE_CANCELLED);
    }

    @Override
    public void onGestureEnded(float endVelocity, PointF velocity, PointF downPos) {
        mEndVelocityPxPerMs.set(0, velocity.y / 1000);
        if (mInQuickSwitchMode) {
            // For now set it to non-null, it will be reset before starting the animation
            mGestureState.setEndTarget(LAST_TASK);
        } else {
            float flingThreshold = mContext.getResources()
                    .getDimension(R.dimen.quickstep_fling_threshold_velocity);
            boolean isFling = Math.abs(endVelocity) > flingThreshold;

            if (mDeviceState.isFullyGesturalNavMode()) {
                if (isFling) {
                    mGestureState.setEndTarget(endVelocity < 0 ? HOME : LAST_TASK);
                } else if (mOverviewThresholdPassed) {
                    mGestureState.setEndTarget(RECENTS);
                } else {
                    mGestureState.setEndTarget(mCurrentShift.value >= MIN_PROGRESS_FOR_OVERVIEW
                            ? HOME
                            : LAST_TASK);
                }
            } else {
                GestureEndTarget startState = mSwipeUpOverHome ? HOME : LAST_TASK;
                if (isFling) {
                    mGestureState.setEndTarget(endVelocity < 0 ? RECENTS : startState);
                } else {
                    mGestureState.setEndTarget(mCurrentShift.value >= MIN_PROGRESS_FOR_OVERVIEW
                            ? RECENTS
                            : startState);
                }
            }
        }
        mStateCallback.setStateOnUiThread(STATE_GESTURE_COMPLETED);
    }

    @Override
    public void onConsumerAboutToBeSwitched() {
        if (mInQuickSwitchMode && mGestureState.getEndTarget() != null) {
            mGestureState.setEndTarget(NEW_TASK);

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
                    mGestureState.setFinishingRecentsAnimationTaskId(newRunningTaskId);
                }
                mRecentsView.setOnScrollChangeListener(null);
            }
        } else {
            mStateCallback.setStateOnUiThread(STATE_HANDLER_INVALIDATED);
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
        switch (mGestureState.getEndTarget()) {
            case HOME: {
                if (mSwipeUpOverHome) {
                    mRecentsAnimationController.finish(false, null, false);
                    // Send a home intent to clear the task stack
                    mContext.startActivity(mGestureState.getHomeIntent());
                } else {
                    // Notify swipe-to-home (recents animation) is finished
                    SystemUiProxy.INSTANCE.get(mContext).notifySwipeToHomeFinished();
                    mRecentsAnimationController.finish(true, () -> {
                        if (!mTouchedHomeDuringTransition) {
                            // If the user hasn't interacted with the screen during the transition,
                            // send a home intent so launcher can go to the default home screen.
                            // (If they are trying to touch something, we don't want to interfere.)
                            mContext.startActivity(mGestureState.getHomeIntent());
                        }
                    }, true);
                }
                break;
            }
            case LAST_TASK:
                mRecentsAnimationController.finish(false, null, false);
                break;
            case RECENTS: {
                if (mSwipeUpOverHome || !mDeviceState.isFullyGesturalNavMode()) {
                    mRecentsAnimationController.finish(true, null, true);
                    break;
                }

                final int runningTaskId = mGestureState.getRunningTaskId();
                ThumbnailData thumbnail = mRecentsAnimationController.screenshotTask(runningTaskId);
                mRecentsAnimationController.setDeferCancelUntilNextTransition(true /* defer */,
                        false /* screenshot */);

                ActivityOptions options = ActivityOptions.makeCustomAnimation(mContext, 0, 0);
                ActivityOptionsCompat.setFreezeRecentTasksList(options);

                Bundle extras = new Bundle();
                extras.putBinder(EXTRA_THUMBNAIL, new ObjectWrapper<>(thumbnail));
                extras.putInt(EXTRA_TASK_ID, runningTaskId);

                Intent intent = new Intent(mGestureState.getOverviewIntent())
                        .putExtras(extras);
                mContext.startActivity(intent, options.toBundle());
                mRecentsAnimationController.cleanupScreenshot();
                break;
            }
            case NEW_TASK: {
                startNewTask(STATE_HANDLER_INVALIDATED, b -> {});
                break;
            }
        }

        mStateCallback.setStateOnUiThread(STATE_HANDLER_INVALIDATED);
    }

    private void finishAnimationTargetSet() {
        if (mInQuickSwitchMode) {
            // Recalculate the end target, some views might have been initialized after
            // gesture has ended.
            if (mRecentsView == null || !hasTargets()) {
                mGestureState.setEndTarget(LAST_TASK);
            } else {
                final int runningTaskIndex = mRecentsView.getRunningTaskIndex();
                final int taskToLaunch = mRecentsView.getNextPage();
                mGestureState.setEndTarget(
                        (runningTaskIndex >= 0 && taskToLaunch != runningTaskIndex)
                                ? NEW_TASK
                                : LAST_TASK);
            }
        }

        EndTargetAnimationParams params = mEndTargetAnimationParams.get(mGestureState.getEndTarget());
        float endProgress = params.mEndProgress;
        long duration = (long) (params.mDurationMultiplier *
                Math.abs(endProgress - mCurrentShift.value));
        if (mRecentsView != null) {
            duration = Math.max(duration, mRecentsView.getScroller().getDuration());
        }
        if (mCurrentShift.value != endProgress || mInQuickSwitchMode) {
            AnimationSuccessListener endListener = new AnimationSuccessListener() {

                @Override
                public void onAnimationSuccess(Animator animator) {
                    if (mRecentsView != null) {
                        mRecentsView.setOnPageTransitionEndCallback(FallbackSwipeHandler.this
                                ::finishAnimationTargetSetAnimationComplete);
                    } else {
                        finishAnimationTargetSetAnimationComplete();
                    }
                    mFinishAnimation = null;
                }
            };

            if (mGestureState.getEndTarget() == HOME && !mRunningOverHome) {
                mRecentsAnimationController.enableInputProxy(mInputConsumer,
                        this::createNewInputProxyHandler);
                RectFSpringAnim anim = createWindowAnimationToHome(mCurrentShift.value, duration);
                anim.addAnimatorListener(endListener);
                anim.start(mContext, mEndVelocityPxPerMs);
                mFinishAnimation = RunningWindowAnim.wrap(anim);
            } else {

                AnimatorSet anim = new AnimatorSet();
                anim.play(mLauncherAlpha.animateToValue(
                        mLauncherAlpha.value, params.mLauncherAlpha));
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
    public void onRecentsAnimationStart(RecentsAnimationController controller,
            RecentsAnimationTargets targets) {
        super.onRecentsAnimationStart(controller, targets);
        mRecentsAnimationController.enableInputConsumer();

        if (mRunningOverHome) {
            mAppWindowAnimationHelper.prepareAnimation(mDp);
        }
        applyTransformUnchecked();

        mStateCallback.setStateOnUiThread(STATE_APP_CONTROLLER_RECEIVED);
    }

    @Override
    public void onRecentsAnimationCanceled(ThumbnailData thumbnailData) {
        mStateCallback.setStateOnUiThread(STATE_HANDLER_INVALIDATED);

        // Defer clearing the controller and the targets until after we've updated the state
        super.onRecentsAnimationCanceled(thumbnailData);
    }

    /**
     * Creates an animation that transforms the current app window into the home app.
     * @param startProgress The progress of {@link #mCurrentShift} to start the window from.
     */
    private RectFSpringAnim createWindowAnimationToHome(float startProgress, long duration) {
        HomeAnimationFactory factory = new HomeAnimationFactory() {
            @Override
            public RectF getWindowTargetRect() {
                PagedOrientationHandler orientationHandler = mRecentsView != null
                        ? mRecentsView.getPagedOrientationHandler()
                        : (mDp.isLandscape
                                ? PagedOrientationHandler.LANDSCAPE
                                : PagedOrientationHandler.PORTRAIT);
                return HomeAnimationFactory
                    .getDefaultWindowTargetRect(orientationHandler, mDp);
            }

            @Override
            public AnimatorPlaybackController createActivityAnimationToHome() {
                AnimatorSet anim = new AnimatorSet();
                Animator fadeInLauncher = mLauncherAlpha.animateToValue(mLauncherAlpha.value, 1);
                fadeInLauncher.setInterpolator(ACCEL_2);
                anim.play(fadeInLauncher);
                anim.setDuration(duration);
                return AnimatorPlaybackController.wrap(anim, duration);
            }
        };
        return createWindowAnimationToHome(startProgress, factory);
    }

    @Override
    protected float getWindowAlpha(float progress) {
        return 1 - ACCEL_1_5.getInterpolation(progress);
    }
}
