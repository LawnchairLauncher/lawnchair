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
import static com.android.launcher3.anim.Interpolators.ACCEL_1_5;
import static com.android.launcher3.anim.Interpolators.DEACCEL;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.VibratorWrapper.OVERVIEW_HAPTIC;
import static com.android.launcher3.views.FloatingIconView.SHAPE_PROGRESS_DURATION;
import static com.android.quickstep.TouchInteractionService.TOUCH_INTERACTION_LOG;

import android.animation.Animator;
import android.annotation.TargetApi;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.UiThread;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.graphics.RotationMode;
import com.android.launcher3.util.VibratorWrapper;
import com.android.launcher3.views.FloatingIconView;
import com.android.quickstep.ActivityControlHelper.ActivityInitListener;
import com.android.quickstep.ActivityControlHelper.HomeAnimationFactory;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.inputconsumers.InputConsumer;
import com.android.quickstep.util.ClipAnimationHelper;
import com.android.quickstep.util.ClipAnimationHelper.TransformParams;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.RemoteAnimationTargetSet;
import com.android.quickstep.util.SwipeAnimationTargetSet;
import com.android.quickstep.util.SwipeAnimationTargetSet.SwipeAnimationListener;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat;

import java.util.function.Consumer;

/**
 * Base class for swipe up handler with some utility methods
 */
@TargetApi(Build.VERSION_CODES.Q)
public abstract class BaseSwipeUpHandler<T extends BaseDraggingActivity, Q extends RecentsView>
        implements SwipeAnimationListener {

    private static final String TAG = "BaseSwipeUpHandler";
    protected static final Rect TEMP_RECT = new Rect();

    // Start resisting when swiping past this factor of mTransitionDragLength.
    private static final float DRAG_LENGTH_FACTOR_START_PULLBACK = 1.4f;
    // This is how far down we can scale down, where 0f is full screen and 1f is recents.
    private static final float DRAG_LENGTH_FACTOR_MAX_PULLBACK = 1.8f;
    private static final Interpolator PULLBACK_INTERPOLATOR = DEACCEL;

    // The distance needed to drag to reach the task size in recents.
    protected int mTransitionDragLength;
    // How much further we can drag past recents, as a factor of mTransitionDragLength.
    protected float mDragLengthFactor = 1;

    protected final Context mContext;
    protected final OverviewComponentObserver mOverviewComponentObserver;
    protected final ActivityControlHelper<T> mActivityControlHelper;
    protected final RecentsModel mRecentsModel;
    protected final int mRunningTaskId;

    protected final ClipAnimationHelper mClipAnimationHelper;
    protected final TransformParams mTransformParams = new TransformParams();

    protected final Mode mMode;

    // Shift in the range of [0, 1].
    // 0 => preview snapShot is completely visible, and hotseat is completely translated down
    // 1 => preview snapShot is completely aligned with the recents view and hotseat is completely
    // visible.
    protected final AnimatedFloat mCurrentShift = new AnimatedFloat(this::updateFinalShift);

    protected final ActivityInitListener mActivityInitListener;
    protected final RecentsAnimationWrapper mRecentsAnimationWrapper;

    protected T mActivity;
    protected Q mRecentsView;
    protected DeviceProfile mDp;
    private final int mPageSpacing;

    protected Runnable mGestureEndCallback;

    protected final Handler mMainThreadHandler = MAIN_EXECUTOR.getHandler();
    protected MultiStateCallback mStateCallback;

    protected boolean mCanceled;
    protected int mFinishingRecentsAnimationForNewTaskId = -1;

    protected BaseSwipeUpHandler(Context context,
            OverviewComponentObserver overviewComponentObserver,
            RecentsModel recentsModel, InputConsumerController inputConsumer, int runningTaskId) {
        mContext = context;
        mOverviewComponentObserver = overviewComponentObserver;
        mActivityControlHelper = overviewComponentObserver.getActivityControlHelper();
        mRecentsModel = recentsModel;
        mActivityInitListener =
                mActivityControlHelper.createActivityInitListener(this::onActivityInit);
        mRunningTaskId = runningTaskId;
        mRecentsAnimationWrapper = new RecentsAnimationWrapper(inputConsumer,
                this::createNewInputProxyHandler);
        mMode = SysUINavigationMode.getMode(context);

        mClipAnimationHelper = new ClipAnimationHelper(context);
        mPageSpacing = context.getResources().getDimensionPixelSize(R.dimen.recents_page_spacing);
        initTransitionEndpoints(InvariantDeviceProfile.INSTANCE.get(mContext)
                .getDeviceProfile(mContext));
    }

    protected void setStateOnUiThread(int stateFlag) {
        if (Looper.myLooper() == mMainThreadHandler.getLooper()) {
            mStateCallback.setState(stateFlag);
        } else {
            postAsyncCallback(mMainThreadHandler, () -> mStateCallback.setState(stateFlag));
        }
    }

    protected void performHapticFeedback() {
        VibratorWrapper.INSTANCE.get(mContext).vibrate(OVERVIEW_HAPTIC);
    }

    public Consumer<MotionEvent> getRecentsViewDispatcher(RotationMode rotationMode) {
        return mRecentsView != null ? mRecentsView.getEventDispatcher(rotationMode) : null;
    }

    @UiThread
    public void updateDisplacement(float displacement) {
        // We are moving in the negative x/y direction
        displacement = -displacement;
        float shift;
        if (displacement > mTransitionDragLength * mDragLengthFactor && mTransitionDragLength > 0) {
            shift = mDragLengthFactor;
        } else {
            float translation = Math.max(displacement, 0);
            shift = mTransitionDragLength == 0 ? 0 : translation / mTransitionDragLength;
            if (shift > DRAG_LENGTH_FACTOR_START_PULLBACK) {
                float pullbackProgress = Utilities.getProgress(shift,
                        DRAG_LENGTH_FACTOR_START_PULLBACK, mDragLengthFactor);
                pullbackProgress = PULLBACK_INTERPOLATOR.getInterpolation(pullbackProgress);
                shift = DRAG_LENGTH_FACTOR_START_PULLBACK + pullbackProgress
                        * (DRAG_LENGTH_FACTOR_MAX_PULLBACK - DRAG_LENGTH_FACTOR_START_PULLBACK);
            }
        }

        mCurrentShift.updateValue(shift);
    }

    public void setGestureEndCallback(Runnable gestureEndCallback) {
        mGestureEndCallback = gestureEndCallback;
    }

    public abstract Intent getLaunchIntent();

    protected void linkRecentsViewScroll() {
        SyncRtSurfaceTransactionApplierCompat.create(mRecentsView, applier -> {
            mTransformParams.setSyncTransactionApplier(applier);
            mRecentsAnimationWrapper.runOnInit(() ->
                    mRecentsAnimationWrapper.targetSet.addDependentTransactionApplier(applier));
        });

        mRecentsView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (moveWindowWithRecentsScroll()) {
                updateFinalShift();
            }
        });
        mRecentsView.setRecentsAnimationWrapper(mRecentsAnimationWrapper);
        mRecentsView.setClipAnimationHelper(mClipAnimationHelper);
    }

    protected void startNewTask(int successStateFlag, Consumer<Boolean> resultCallback) {
        // Launch the task user scrolled to (mRecentsView.getNextPage()).
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            // We finish recents animation inside launchTask() when live tile is enabled.
            mRecentsView.getNextPageTaskView().launchTask(false /* animate */,
                    true /* freezeTaskList */);
        } else {
            int taskId = mRecentsView.getNextPageTaskView().getTask().key.id;
            mFinishingRecentsAnimationForNewTaskId = taskId;
            mRecentsAnimationWrapper.finish(true /* toRecents */, () -> {
                if (!mCanceled) {
                    TaskView nextTask = mRecentsView.getTaskView(taskId);
                    if (nextTask != null) {
                        nextTask.launchTask(false /* animate */, true /* freezeTaskList */,
                                success -> {
                                    resultCallback.accept(success);
                                    if (!success) {
                                        mActivityControlHelper.onLaunchTaskFailed(mActivity);
                                        nextTask.notifyTaskLaunchFailed(TAG);
                                    } else {
                                        mActivityControlHelper.onLaunchTaskSuccess(mActivity);
                                    }
                                }, mMainThreadHandler);
                    }
                    setStateOnUiThread(successStateFlag);
                }
                mCanceled = false;
                mFinishingRecentsAnimationForNewTaskId = -1;
            });
        }
        TOUCH_INTERACTION_LOG.addLog("finishRecentsAnimation", true);
    }

    @Override
    public void onRecentsAnimationStart(SwipeAnimationTargetSet targetSet) {
        DeviceProfile dp = InvariantDeviceProfile.INSTANCE.get(mContext).getDeviceProfile(mContext);
        final Rect overviewStackBounds;
        RemoteAnimationTargetCompat runningTaskTarget = targetSet.findTask(mRunningTaskId);

        if (targetSet.minimizedHomeBounds != null && runningTaskTarget != null) {
            overviewStackBounds = mActivityControlHelper
                    .getOverviewWindowBounds(targetSet.minimizedHomeBounds, runningTaskTarget);
            dp = dp.getMultiWindowProfile(mContext, new Point(
                    overviewStackBounds.width(), overviewStackBounds.height()));
        } else {
            // If we are not in multi-window mode, home insets should be same as system insets.
            dp = dp.copy(mContext);
            overviewStackBounds = getStackBounds(dp);
        }
        dp.updateInsets(targetSet.homeContentInsets);
        dp.updateIsSeascape(mContext);
        if (runningTaskTarget != null) {
            mClipAnimationHelper.updateSource(overviewStackBounds, runningTaskTarget);
        }

        mClipAnimationHelper.prepareAnimation(dp, false /* isOpening */);
        initTransitionEndpoints(dp);

        mRecentsAnimationWrapper.setController(targetSet);
    }

    private Rect getStackBounds(DeviceProfile dp) {
        if (mActivity != null) {
            int loc[] = new int[2];
            View rootView = mActivity.getRootView();
            rootView.getLocationOnScreen(loc);
            return new Rect(loc[0], loc[1], loc[0] + rootView.getWidth(),
                    loc[1] + rootView.getHeight());
        } else {
            return new Rect(0, 0, dp.widthPx, dp.heightPx);
        }
    }

    protected void initTransitionEndpoints(DeviceProfile dp) {
        mDp = dp;

        mTransitionDragLength = mActivityControlHelper.getSwipeUpDestinationAndLength(
                dp, mContext, TEMP_RECT);
        if (!dp.isMultiWindowMode) {
            // When updating the target rect, also update the home bounds since the location on
            // screen of the launcher window may be stale (position is not updated until first
            // traversal after the window is resized).  We only do this for non-multiwindow because
            // we otherwise use the minimized home bounds provided by the system.
            mClipAnimationHelper.updateHomeBounds(getStackBounds(dp));
        }
        mClipAnimationHelper.updateTargetRect(TEMP_RECT);
        if (mMode == Mode.NO_BUTTON) {
            // We can drag all the way to the top of the screen.
            mDragLengthFactor = (float) dp.heightPx / mTransitionDragLength;
        }
    }

    /**
     * Return true if the window should be translated horizontally if the recents view scrolls
     */
    protected abstract boolean moveWindowWithRecentsScroll();

    protected abstract boolean onActivityInit(final T activity, Boolean alreadyOnHome);

    /**
     * Called to create a input proxy for the running task
     */
    @UiThread
    protected abstract InputConsumer createNewInputProxyHandler();

    /**
     * Called when the value of {@link #mCurrentShift} changes
     */
    @UiThread
    public abstract void updateFinalShift();

    /**
     * Called when motion pause is detected
     */
    public abstract void onMotionPauseChanged(boolean isPaused);

    @UiThread
    public void onGestureStarted() { }

    @UiThread
    public abstract void onGestureCancelled();

    @UiThread
    public abstract void onGestureEnded(float endVelocity, PointF velocity, PointF downPos);

    public abstract void onConsumerAboutToBeSwitched(SwipeSharedState sharedState);

    public void setIsLikelyToStartNewTask(boolean isLikelyToStartNewTask) { }

    public void initWhenReady() {
        // Preload the plan
        mRecentsModel.getTasks(null);

        mActivityInitListener.register();
    }

    /**
     * Applies the transform on the recents animation without any additional null checks
     */
    protected void applyTransformUnchecked() {
        float shift = mCurrentShift.value;
        float offsetX = mRecentsView == null ? 0 : mRecentsView.getScrollOffset();
        float offsetScale = getTaskCurveScaleForOffsetX(offsetX,
                mClipAnimationHelper.getTargetRect().width());
        mTransformParams.setProgress(shift).setOffsetX(offsetX).setOffsetScale(offsetScale);
        mClipAnimationHelper.applyTransform(mRecentsAnimationWrapper.targetSet,
                mTransformParams);
    }

    private float getTaskCurveScaleForOffsetX(float offsetX, float taskWidth) {
        float distanceToReachEdge = mDp.widthPx / 2 + taskWidth / 2 + mPageSpacing;
        float interpolation = Math.min(1, offsetX / distanceToReachEdge);
        return TaskView.getCurveScaleForInterpolation(interpolation);
    }

    /**
     * Creates an animation that transforms the current app window into the home app.
     * @param startProgress The progress of {@link #mCurrentShift} to start the window from.
     * @param homeAnimationFactory The home animation factory.
     */
    protected RectFSpringAnim createWindowAnimationToHome(float startProgress,
            HomeAnimationFactory homeAnimationFactory) {
        final RemoteAnimationTargetSet targetSet = mRecentsAnimationWrapper.targetSet;
        final RectF startRect = new RectF(mClipAnimationHelper.applyTransform(targetSet,
                mTransformParams.setProgress(startProgress), false /* launcherOnTop */));
        final RectF targetRect = homeAnimationFactory.getWindowTargetRect();

        final View floatingView = homeAnimationFactory.getFloatingView();
        final boolean isFloatingIconView = floatingView instanceof FloatingIconView;
        RectFSpringAnim anim = new RectFSpringAnim(startRect, targetRect, mContext.getResources());
        if (isFloatingIconView) {
            FloatingIconView fiv = (FloatingIconView) floatingView;
            anim.addAnimatorListener(fiv);
            fiv.setOnTargetChangeListener(anim::onTargetPositionChanged);
        }

        AnimatorPlaybackController homeAnim = homeAnimationFactory.createActivityAnimationToHome();

        // End on a "round-enough" radius so that the shape reveal doesn't have to do too much
        // rounding at the end of the animation.
        float startRadius = mClipAnimationHelper.getCurrentCornerRadius();
        float endRadius = startRect.width() / 6f;
        // We want the window alpha to be 0 once this threshold is met, so that the
        // FolderIconView can be seen morphing into the icon shape.
        final float windowAlphaThreshold = isFloatingIconView ? 1f - SHAPE_PROGRESS_DURATION : 1f;
        anim.addOnUpdateListener(new RectFSpringAnim.OnUpdateListener() {

            // Alpha interpolates between [1, 0] between progress values [start, end]
            final float start = 0f;
            final float end = 0.85f;

            private float getWindowAlpha(float progress) {
                if (progress <= start) {
                    return 1f;
                }
                if (progress >= end) {
                    return 0f;
                }
                return Utilities.mapToRange(progress, start, end, 1, 0, ACCEL_1_5);
            }

            @Override
            public void onUpdate(RectF currentRect, float progress) {
                homeAnim.setPlayFraction(progress);

                mTransformParams.setProgress(progress)
                        .setCurrentRectAndTargetAlpha(currentRect, getWindowAlpha(progress));
                if (isFloatingIconView) {
                    mTransformParams.setCornerRadius(endRadius * progress + startRadius
                            * (1f - progress));
                }
                mClipAnimationHelper.applyTransform(targetSet, mTransformParams,
                        false /* launcherOnTop */);

                if (isFloatingIconView) {
                    ((FloatingIconView) floatingView).update(currentRect, 1f, progress,
                            windowAlphaThreshold, mClipAnimationHelper.getCurrentCornerRadius(),
                            false);
                }
            }

            @Override
            public void onCancel() {
                if (isFloatingIconView) {
                    ((FloatingIconView) floatingView).fastFinish();
                }
            }
        });
        anim.addAnimatorListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                homeAnim.dispatchOnStart();
            }

            @Override
            public void onAnimationSuccess(Animator animator) {
                homeAnim.getAnimationPlayer().end();
            }
        });
        return anim;
    }

    public interface Factory {

        BaseSwipeUpHandler newHandler(RunningTaskInfo runningTask,
                long touchTimeMs, boolean continuingLastGesture, boolean isLikelyToStartNewTask);
    }

    protected interface RunningWindowAnim {
        void end();

        void cancel();

        static RunningWindowAnim wrap(Animator animator) {
            return new RunningWindowAnim() {
                @Override
                public void end() {
                    animator.end();
                }

                @Override
                public void cancel() {
                    animator.cancel();
                }
            };
        }

        static RunningWindowAnim wrap(RectFSpringAnim rectFSpringAnim) {
            return new RunningWindowAnim() {
                @Override
                public void end() {
                    rectFSpringAnim.end();
                }

                @Override
                public void cancel() {
                    rectFSpringAnim.cancel();
                }
            };
        }
    }
}
