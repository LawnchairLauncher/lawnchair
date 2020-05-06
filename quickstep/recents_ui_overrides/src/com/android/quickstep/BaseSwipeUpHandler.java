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
import static com.android.launcher3.anim.Interpolators.DEACCEL;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.VibratorWrapper.OVERVIEW_HAPTIC;
import static com.android.launcher3.views.FloatingIconView.SHAPE_PROGRESS_DURATION;

import android.animation.Animator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.Pair;
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
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.util.VibratorWrapper;
import com.android.launcher3.views.FloatingIconView;
import com.android.quickstep.BaseActivityInterface.HomeAnimationFactory;
import com.android.quickstep.RecentsAnimationCallbacks.RecentsAnimationListener;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.util.ActivityInitListener;
import com.android.quickstep.util.AppWindowAnimationHelper;
import com.android.quickstep.util.AppWindowAnimationHelper.TransformParams;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Base class for swipe up handler with some utility methods
 */
@TargetApi(Build.VERSION_CODES.Q)
public abstract class BaseSwipeUpHandler<T extends BaseDraggingActivity, Q extends RecentsView>
        implements RecentsAnimationListener {

    private static final String TAG = "BaseSwipeUpHandler";
    protected static final Rect TEMP_RECT = new Rect();

    public static final float MIN_PROGRESS_FOR_OVERVIEW = 0.7f;
    private static final Interpolator PULLBACK_INTERPOLATOR = DEACCEL;

    // The distance needed to drag to reach the task size in recents.
    protected int mTransitionDragLength;
    // How much further we can drag past recents, as a factor of mTransitionDragLength.
    protected float mDragLengthFactor = 1;
    // Start resisting when swiping past this factor of mTransitionDragLength.
    private float mDragLengthFactorStartPullback = 1f;
    // This is how far down we can scale down, where 0f is full screen and 1f is recents.
    private float mDragLengthFactorMaxPullback = 1f;

    protected final Context mContext;
    protected final RecentsAnimationDeviceState mDeviceState;
    protected final GestureState mGestureState;
    protected final BaseActivityInterface<T> mActivityInterface;
    protected final InputConsumerController mInputConsumer;

    protected AppWindowAnimationHelper mAppWindowAnimationHelper;
    protected final TransformParams mTransformParams = new TransformParams();

    // Shift in the range of [0, 1].
    // 0 => preview snapShot is completely visible, and hotseat is completely translated down
    // 1 => preview snapShot is completely aligned with the recents view and hotseat is completely
    // visible.
    protected final AnimatedFloat mCurrentShift = new AnimatedFloat(this::updateFinalShift);

    protected final ActivityInitListener mActivityInitListener;

    protected RecentsAnimationController mRecentsAnimationController;
    protected RecentsAnimationTargets mRecentsAnimationTargets;

    // Callbacks to be made once the recents animation starts
    private final ArrayList<Runnable> mRecentsAnimationStartCallbacks = new ArrayList<>();

    protected T mActivity;
    protected Q mRecentsView;
    protected DeviceProfile mDp;
    private final int mPageSpacing;

    protected Runnable mGestureEndCallback;

    protected MultiStateCallback mStateCallback;

    protected boolean mCanceled;
    protected int mFinishingRecentsAnimationForNewTaskId = -1;

    private RecentsOrientedState mOrientedState;

    protected BaseSwipeUpHandler(Context context, RecentsAnimationDeviceState deviceState,
            GestureState gestureState, InputConsumerController inputConsumer) {
        mContext = context;
        mDeviceState = deviceState;
        mGestureState = gestureState;
        mActivityInterface = gestureState.getActivityInterface();
        mActivityInitListener = mActivityInterface.createActivityInitListener(this::onActivityInit);
        mInputConsumer = inputConsumer;
        mAppWindowAnimationHelper = new AppWindowAnimationHelper(context);
        mPageSpacing = context.getResources().getDimensionPixelSize(R.dimen.recents_page_spacing);
    }

    /**
     * To be called at the end of constructor of subclasses. This calls various methods which can
     * depend on proper class initialization.
     */
    protected void initAfterSubclassConstructor() {
        initTransitionEndpoints(InvariantDeviceProfile.INSTANCE.get(mContext)
                .getDeviceProfile(mContext));
    }

    protected void performHapticFeedback() {
        VibratorWrapper.INSTANCE.get(mContext).vibrate(OVERVIEW_HAPTIC);
    }

    public Consumer<MotionEvent> getRecentsViewDispatcher(float navbarRotation) {
        return mRecentsView != null ? mRecentsView.getEventDispatcher(navbarRotation) : null;
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
            if (shift > mDragLengthFactorStartPullback) {
                float pullbackProgress = Utilities.getProgress(shift,
                        mDragLengthFactorStartPullback, mDragLengthFactor);
                pullbackProgress = PULLBACK_INTERPOLATOR.getInterpolation(pullbackProgress);
                shift = mDragLengthFactorStartPullback + pullbackProgress
                        * (mDragLengthFactorMaxPullback - mDragLengthFactorStartPullback);
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
            runOnRecentsAnimationStart(() ->
                    mRecentsAnimationTargets.addDependentTransactionApplier(applier));
        });

        mRecentsView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (moveWindowWithRecentsScroll()) {
                updateFinalShift();
            }
        });
        mRecentsView.setAppWindowAnimationHelper(mAppWindowAnimationHelper);
        runOnRecentsAnimationStart(() ->
                mRecentsView.setRecentsAnimationTargets(mRecentsAnimationController,
                        mRecentsAnimationTargets));
    }

    protected void startNewTask(int successStateFlag, Consumer<Boolean> resultCallback) {
        // Launch the task user scrolled to (mRecentsView.getNextPage()).
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            // We finish recents animation inside launchTask() when live tile is enabled.
            mRecentsView.getNextPageTaskView().launchTask(false /* animate */,
                    true /* freezeTaskList */);
        } else {
            int taskId = mRecentsView.getNextPageTaskView().getTask().key.id;
            if (!mCanceled) {
                TaskView nextTask = mRecentsView.getTaskView(taskId);
                if (nextTask != null) {
                    nextTask.launchTask(false /* animate */, true /* freezeTaskList */,
                            success -> {
                                resultCallback.accept(success);
                                if (!success) {
                                    mActivityInterface.onLaunchTaskFailed();
                                    nextTask.notifyTaskLaunchFailed(TAG);
                                } else {
                                    mActivityInterface.onLaunchTaskSuccess();
                                }
                            }, MAIN_EXECUTOR.getHandler());
                }
                mStateCallback.setStateOnUiThread(successStateFlag);
            }
            mCanceled = false;
        }
        ActiveGestureLog.INSTANCE.addLog("finishRecentsAnimation", true);
    }

    /**
     * Runs the given {@param action} if the recents animation has already started, or queues it to
     * be run when it is next started.
     */
    protected void runOnRecentsAnimationStart(Runnable action) {
        if (mRecentsAnimationTargets == null) {
            mRecentsAnimationStartCallbacks.add(action);
        } else {
            action.run();
        }
    }

    /**
     * @return whether the recents animation has started and there are valid app targets.
     */
    protected boolean hasTargets() {
        return mRecentsAnimationTargets != null && mRecentsAnimationTargets.hasTargets();
    }

    protected void updateSource(Rect stackBounds, RemoteAnimationTargetCompat runningTarget) {
        mAppWindowAnimationHelper.updateSource(stackBounds, runningTarget);
    }

    @Override
    public void onRecentsAnimationStart(RecentsAnimationController recentsAnimationController,
            RecentsAnimationTargets targets) {
        mRecentsAnimationController = recentsAnimationController;
        mRecentsAnimationTargets = targets;
        DeviceProfile dp = InvariantDeviceProfile.INSTANCE.get(mContext).getDeviceProfile(mContext);
        final Rect overviewStackBounds;
        RemoteAnimationTargetCompat runningTaskTarget = targets.findTask(
                mGestureState.getRunningTaskId());

        if (targets.minimizedHomeBounds != null && runningTaskTarget != null) {
            overviewStackBounds = mActivityInterface
                    .getOverviewWindowBounds(targets.minimizedHomeBounds, runningTaskTarget);
            dp = dp.getMultiWindowProfile(mContext, overviewStackBounds);
        } else {
            // If we are not in multi-window mode, home insets should be same as system insets.
            dp = dp.copy(mContext);
            overviewStackBounds = getStackBounds(dp);
        }
        dp.updateInsets(targets.homeContentInsets);
        dp.updateIsSeascape(mContext);
        if (runningTaskTarget != null) {
            updateSource(overviewStackBounds, runningTaskTarget);
        }

        mAppWindowAnimationHelper.prepareAnimation(dp);
        initTransitionEndpoints(dp);

        // Notify when the animation starts
        if (!mRecentsAnimationStartCallbacks.isEmpty()) {
            for (Runnable action : new ArrayList<>(mRecentsAnimationStartCallbacks)) {
                action.run();
            }
            mRecentsAnimationStartCallbacks.clear();
        }
    }

    @Override
    public void onRecentsAnimationCanceled(ThumbnailData thumbnailData) {
        mRecentsAnimationController = null;
        mRecentsAnimationTargets = null;
        if (mRecentsView != null) {
            mRecentsView.setRecentsAnimationTargets(null, null);
        }
    }

    @Override
    public void onRecentsAnimationFinished(RecentsAnimationController controller) {
        mRecentsAnimationController = null;
        mRecentsAnimationTargets = null;
        if (mRecentsView != null) {
            mRecentsView.setRecentsAnimationTargets(null, null);
        }
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

        mTransitionDragLength = mActivityInterface.getSwipeUpDestinationAndLength(
                dp, mContext, TEMP_RECT);

        if (!dp.isMultiWindowMode) {
            // When updating the target rect, also update the home bounds since the location on
            // screen of the launcher window may be stale (position is not updated until first
            // traversal after the window is resized).  We only do this for non-multiwindow because
            // we otherwise use the minimized home bounds provided by the system.
            mAppWindowAnimationHelper.updateHomeBounds(getStackBounds(dp));
        }
        int displayRotation = 0;
        if (mOrientedState != null && mOrientedState.isMultipleOrientationSupportedByDevice()) {
            // TODO(b/150300347): The first recents animation after launcher is started with the
            //  foreground app not in landscape will look funky until that bug is fixed
            displayRotation = mOrientedState.getDisplayRotation();

            RectF tempRectF = new RectF(TEMP_RECT);
            mOrientedState.mapRectFromRotation(displayRotation,
                    tempRectF, dp.widthPx, dp.heightPx);
            tempRectF.roundOut(TEMP_RECT);
        }
        mAppWindowAnimationHelper.updateTargetRect(TEMP_RECT);
        if (mDeviceState.isFullyGesturalNavMode()) {
            // We can drag all the way to the top of the screen.
            mDragLengthFactor = (float) dp.heightPx / mTransitionDragLength;
            Pair<Float, Float> dragFactorStartAndMaxProgress =
                    mActivityInterface.getSwipeUpPullbackStartAndMaxProgress();
            mDragLengthFactorStartPullback = dragFactorStartAndMaxProgress.first;
            mDragLengthFactorMaxPullback = dragFactorStartAndMaxProgress.second;
        }
    }

    /**
     * Return true if the window should be translated horizontally if the recents view scrolls
     */
    protected abstract boolean moveWindowWithRecentsScroll();

    protected boolean onActivityInit(Boolean alreadyOnHome) {
        T createdActivity = mActivityInterface.getCreatedActivity();
        if (createdActivity != null) {
            mOrientedState = ((RecentsView) createdActivity.getOverviewPanel())
                .getPagedViewOrientedState();
            mAppWindowAnimationHelper = new AppWindowAnimationHelper(mOrientedState, mContext);
            initTransitionEndpoints(InvariantDeviceProfile.INSTANCE.get(mContext)
                .getDeviceProfile(mContext));
        }
        return true;
    }

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

    public abstract void onConsumerAboutToBeSwitched();

    public void setIsLikelyToStartNewTask(boolean isLikelyToStartNewTask) { }

    /**
     * Registers a callback to run when the activity is ready.
     * @param intent The intent that will be used to start the activity if it doesn't exist already.
     */
    public void initWhenReady(Intent intent) {
        // Preload the plan
        RecentsModel.INSTANCE.get(mContext).getTasks(null);

        mActivityInitListener.register(intent);
    }

    /**
     * Applies the transform on the recents animation without any additional null checks
     */
    protected void applyTransformUnchecked() {
        float shift = mCurrentShift.value;
        float offset = mRecentsView == null ? 0 : mRecentsView.getScrollOffsetScaled();
        float taskSize = getOrientationHandler()
            .getPrimarySize(mAppWindowAnimationHelper.getTargetRect());
        float offsetScale = getTaskCurveScaleForOffset(offset, taskSize);
        mTransformParams
                .setProgress(shift)
                .setOffset(offset)
                .setOffsetScale(offsetScale)
                .setTargetSet(mRecentsAnimationTargets)
                .setLauncherOnTop(true);
        mAppWindowAnimationHelper.applyTransform(mTransformParams);
    }

    private float getTaskCurveScaleForOffset(float offset, float taskSize) {
        int dpPixel = getOrientationHandler().getShortEdgeLength(mDp);
        float distanceToReachEdge = dpPixel / 2 + taskSize / 2 + mPageSpacing;
        float interpolation = Math.min(1, offset / distanceToReachEdge);
        return TaskView.getCurveScaleForInterpolation(interpolation);
    }

    protected PagedOrientationHandler getOrientationHandler() {
        if (mOrientedState == null) {
            return PagedOrientationHandler.PORTRAIT;
        }
        return mOrientedState.getOrientationHandler();
    }

    /**
     * Creates an animation that transforms the current app window into the home app.
     * @param startProgress The progress of {@link #mCurrentShift} to start the window from.
     * @param homeAnimationFactory The home animation factory.
     */
    protected RectFSpringAnim createWindowAnimationToHome(float startProgress,
            HomeAnimationFactory homeAnimationFactory) {
        final RectF targetRect = homeAnimationFactory.getWindowTargetRect();
        final View floatingView = homeAnimationFactory.getFloatingView();
        final boolean isFloatingIconView = floatingView instanceof FloatingIconView;
        final RectF startRect = new RectF(
            mAppWindowAnimationHelper.applyTransform(
                mTransformParams.setProgress(startProgress)
                    .setTargetSet(mRecentsAnimationTargets)
                    .setLauncherOnTop(false)));
        if (isFloatingIconView) {
            mOrientedState.mapInverseRectFromNormalOrientation(
                    startRect, mDp.widthPx, mDp.heightPx);
        }
        RectFSpringAnim anim = new RectFSpringAnim(startRect, targetRect, mContext);
        if (isFloatingIconView) {
            FloatingIconView fiv = (FloatingIconView) floatingView;
            anim.addAnimatorListener(fiv);
            fiv.setOnTargetChangeListener(anim::onTargetPositionChanged);
            fiv.setFastFinishRunnable(anim::end);
        }

        AnimatorPlaybackController homeAnim = homeAnimationFactory.createActivityAnimationToHome();

        // End on a "round-enough" radius so that the shape reveal doesn't have to do too much
        // rounding at the end of the animation.
        float startRadius = mAppWindowAnimationHelper.getCurrentCornerRadius();
        float endRadius = startRect.width() / 6f;

        float startTransformProgress = mTransformParams.getProgress();
        float endTransformProgress = 1;

        // We want the window alpha to be 0 once this threshold is met, so that the
        // FolderIconView can be seen morphing into the icon shape.
        final float windowAlphaThreshold = isFloatingIconView ? 1f - SHAPE_PROGRESS_DURATION : 1f;
        final RectF rotatedRect = new RectF();
        anim.addOnUpdateListener(new RectFSpringAnim.OnUpdateListener() {

            @Override
            public void onUpdate(RectF currentRect, float progress) {
                homeAnim.setPlayFraction(progress);

                rotatedRect.set(currentRect);
                if (isFloatingIconView) {
                    mOrientedState.mapRectFromNormalOrientation(
                            rotatedRect, mDp.widthPx, mDp.heightPx);
                    mTransformParams.setCornerRadius(endRadius * progress + startRadius
                        * (1f - progress));
                }
                mTransformParams.setProgress(
                    Utilities.mapRange(progress, startTransformProgress, endTransformProgress))
                    .setCurrentRect(rotatedRect)
                    .setTargetAlpha(getWindowAlpha(progress));
                mAppWindowAnimationHelper.applyTransform(mTransformParams);

                if (isFloatingIconView) {
                    ((FloatingIconView) floatingView).update(currentRect, 1f, progress,
                            windowAlphaThreshold, mAppWindowAnimationHelper.getCurrentCornerRadius(),
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

    /**
     * @param progress The progress of the animation to the home screen.
     * @return The current alpha to set on the animating app window.
     */
    protected float getWindowAlpha(float progress) {
        // Alpha interpolates between [1, 0] between progress values [start, end]
        final float start = 0f;
        final float end = 0.85f;

        if (progress <= start) {
            return 1f;
        }
        if (progress >= end) {
            return 0f;
        }
        return Utilities.mapToRange(progress, start, end, 1, 0, ACCEL_1_5);
    }

    public interface Factory {

        BaseSwipeUpHandler newHandler(GestureState gestureState, long touchTimeMs,
                boolean continuingLastGesture, boolean isLikelyToStartNewTask);
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
