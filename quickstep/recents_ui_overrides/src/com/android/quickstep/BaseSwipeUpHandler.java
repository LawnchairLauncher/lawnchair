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
import static com.android.launcher3.anim.Interpolators.ACCEL_1_5;
import static com.android.launcher3.anim.Interpolators.DEACCEL;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.VibratorWrapper.OVERVIEW_HAPTIC;
import static com.android.launcher3.views.FloatingIconView.SHAPE_PROGRESS_DURATION;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.view.MotionEvent;
import android.view.animation.Interpolator;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.util.VibratorWrapper;
import com.android.launcher3.views.FloatingIconView;
import com.android.quickstep.RecentsAnimationCallbacks.RecentsAnimationListener;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.util.ActivityInitListener;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.util.TransformParams.BuilderProxy;
import com.android.quickstep.util.WindowSizeStrategy;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat.SurfaceParams.Builder;

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

    protected final TaskViewSimulator mTaskViewSimulator;
    private AnimatorPlaybackController mWindowTransitionController;

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

    protected Runnable mGestureEndCallback;

    protected MultiStateCallback mStateCallback;

    protected boolean mCanceled;

    private boolean mRecentsViewScrollLinked = false;

    protected BaseSwipeUpHandler(Context context, RecentsAnimationDeviceState deviceState,
            GestureState gestureState, InputConsumerController inputConsumer,
            WindowSizeStrategy windowSizeStrategy) {
        mContext = context;
        mDeviceState = deviceState;
        mGestureState = gestureState;
        mActivityInterface = gestureState.getActivityInterface();
        mActivityInitListener = mActivityInterface.createActivityInitListener(this::onActivityInit);
        mInputConsumer = inputConsumer;
        mTaskViewSimulator = new TaskViewSimulator(context, windowSizeStrategy);
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
        runOnRecentsAnimationStart(() ->
                mRecentsView.setRecentsAnimationTargets(mRecentsAnimationController,
                        mRecentsAnimationTargets));
        mRecentsViewScrollLinked = true;
    }

    protected void startNewTask(Consumer<Boolean> resultCallback) {
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
                    mGestureState.updateLastStartedTaskId(taskId);
                    nextTask.launchTask(false /* animate */, true /* freezeTaskList */,
                            success -> {
                                resultCallback.accept(success);
                                if (success) {
                                    if (mRecentsView.indexOfChild(nextTask)
                                            == getLastAppearedTaskIndex()) {
                                        onRestartLastAppearedTask();
                                    }
                                } else {
                                    mActivityInterface.onLaunchTaskFailed();
                                    nextTask.notifyTaskLaunchFailed(TAG);
                                    mRecentsAnimationController.finish(true /* toRecents */, null);
                                }
                            }, MAIN_EXECUTOR.getHandler());
                }
            }
            mCanceled = false;
        }
        ActiveGestureLog.INSTANCE.addLog("finishRecentsAnimation", true);
    }

    /**
     * Called when we successfully startNewTask() on the task that was previously running. Normally
     * we call resumeLastTask() when returning to the previously running task, but this handles a
     * specific edge case: if we switch from A to B, and back to A before B appears, we need to
     * start A again to ensure it stays on top.
     */
    @CallSuper
    protected void onRestartLastAppearedTask() {
        // Finish the controller here, since we won't get onTaskAppeared() for a task that already
        // appeared.
        mRecentsAnimationController.finish(false, null);
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
     * TODO can we remove this now that we don't finish the controller until onTaskAppeared()?
     * @return whether the recents animation has started and there are valid app targets.
     */
    protected boolean hasTargets() {
        return mRecentsAnimationTargets != null && mRecentsAnimationTargets.hasTargets();
    }

    @Override
    public void onRecentsAnimationStart(RecentsAnimationController recentsAnimationController,
            RecentsAnimationTargets targets) {
        mRecentsAnimationController = recentsAnimationController;
        mRecentsAnimationTargets = targets;
        DeviceProfile dp = InvariantDeviceProfile.INSTANCE.get(mContext).getDeviceProfile(mContext);
        RemoteAnimationTargetCompat runningTaskTarget = targets.findTask(
                mGestureState.getRunningTaskId());

        if (targets.minimizedHomeBounds != null && runningTaskTarget != null) {
            Rect overviewStackBounds = mActivityInterface
                    .getOverviewWindowBounds(targets.minimizedHomeBounds, runningTaskTarget);
            dp = dp.getMultiWindowProfile(mContext, overviewStackBounds);
        } else {
            // If we are not in multi-window mode, home insets should be same as system insets.
            dp = dp.copy(mContext);
        }
        dp.updateInsets(targets.homeContentInsets);
        dp.updateIsSeascape(mContext);
        if (runningTaskTarget != null) {
            mTaskViewSimulator.setPreview(runningTaskTarget);
        }

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

    @Override
    public void onTaskAppeared(RemoteAnimationTargetCompat appearedTaskTarget) {
        if (mRecentsAnimationController != null) {
            if (handleTaskAppeared(appearedTaskTarget)) {
                mRecentsAnimationController.finish(false /* toRecents */,
                        null /* onFinishComplete */);
                mActivityInterface.onLaunchTaskSuccess();
            }
        }
    }

    /** @return Whether this was the task we were waiting to appear, and thus handled it. */
    protected abstract boolean handleTaskAppeared(RemoteAnimationTargetCompat appearedTaskTarget);

    /**
     * @return The index of the TaskView in RecentsView whose taskId matches the task that will
     * resume if we finish the controller.
     */
    protected int getLastAppearedTaskIndex() {
        return mGestureState.getLastAppearedTaskId() != -1
                ? mRecentsView.getTaskIndexForId(mGestureState.getLastAppearedTaskId())
                : mRecentsView.getRunningTaskIndex();
    }

    /**
     * @return Whether we are continuing a gesture that already landed on a new task,
     * but before that task appeared.
     */
    protected boolean hasStartedNewTask() {
        return mGestureState.getLastStartedTaskId() != -1;
    }

    protected void initTransitionEndpoints(DeviceProfile dp) {
        mDp = dp;

        mTransitionDragLength = mActivityInterface.getSwipeUpDestinationAndLength(
                dp, mContext, TEMP_RECT);
        mTaskViewSimulator.setDp(dp);
        mTaskViewSimulator.setLayoutRotation(
                mDeviceState.getCurrentActiveRotation(),
                mDeviceState.getDisplayRotation());

        if (mDeviceState.isFullyGesturalNavMode()) {
            // We can drag all the way to the top of the screen.
            mDragLengthFactor = (float) dp.heightPx / mTransitionDragLength;

            float startScale = mTaskViewSimulator.getFullScreenScale();
            // Start pulling back when RecentsView scale is 0.75f, and let it go down to 0.5f.
            mDragLengthFactorStartPullback = (0.75f - startScale) / (1 - startScale);
            mDragLengthFactorMaxPullback = (0.5f - startScale) / (1 - startScale);
        } else {
            mDragLengthFactor = 1;
            mDragLengthFactorStartPullback = mDragLengthFactorMaxPullback = 1;
        }

        AnimatorSet anim = new AnimatorSet();
        anim.setDuration(mTransitionDragLength * 2);
        anim.setInterpolator(t -> t * mDragLengthFactor);
        anim.play(ObjectAnimator.ofFloat(mTaskViewSimulator.recentsViewScale,
                AnimatedFloat.VALUE,
                mTaskViewSimulator.getFullScreenScale(), 1));
        anim.play(ObjectAnimator.ofFloat(mTaskViewSimulator.fullScreenProgress,
                AnimatedFloat.VALUE,
                BACKGROUND_APP.getOverviewFullscreenProgress(),
                OVERVIEW.getOverviewFullscreenProgress()));
        mWindowTransitionController =
                AnimatorPlaybackController.wrap(anim, mTransitionDragLength * 2);
    }

    /**
     * Return true if the window should be translated horizontally if the recents view scrolls
     */
    protected abstract boolean moveWindowWithRecentsScroll();

    protected boolean onActivityInit(Boolean alreadyOnHome) {
        T createdActivity = mActivityInterface.getCreatedActivity();
        if (createdActivity != null) {
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
     * Applies the transform on the recents animation
     */
    protected void applyWindowTransform() {
        if (mWindowTransitionController != null) {
            float progress = mCurrentShift.value / mDragLengthFactor;
            mWindowTransitionController.setPlayFraction(progress);
            mTransformParams.setTargetSet(mRecentsAnimationTargets);

            if (mRecentsViewScrollLinked) {
                mTaskViewSimulator.setScroll(mRecentsView.getScrollOffset());
            }
            mTaskViewSimulator.apply(mTransformParams);
        }
    }

    protected PagedOrientationHandler getOrientationHandler() {
        return mTaskViewSimulator.getOrientationState().getOrientationHandler();
    }

    /**
     * Creates an animation that transforms the current app window into the home app.
     * @param startProgress The progress of {@link #mCurrentShift} to start the window from.
     * @param homeAnimationFactory The home animation factory.
     */
    protected RectFSpringAnim createWindowAnimationToHome(float startProgress,
            HomeAnimationFactory homeAnimationFactory) {
        final RectF targetRect = homeAnimationFactory.getWindowTargetRect();
        final FloatingIconView fiv = homeAnimationFactory.mIconView;
        final boolean isFloatingIconView = fiv != null;

        mWindowTransitionController.setPlayFraction(startProgress / mDragLengthFactor);
        mTaskViewSimulator.apply(mTransformParams
                .setProgress(startProgress)
                .setTargetSet(mRecentsAnimationTargets));
        RectF cropRectF = new RectF(mTaskViewSimulator.getCurrentCropRect());

        // Matrix to map a rect in Launcher space to window space
        Matrix homeToWindowPositionMap = new Matrix();
        mTaskViewSimulator.applyWindowToHomeRotation(homeToWindowPositionMap);

        final RectF startRect = new RectF(cropRectF);
        mTaskViewSimulator.getCurrentMatrix().mapRect(startRect);
        // Move the startRect to Launcher space as floatingIconView runs in Launcher
        Matrix windowToHomePositionMap = new Matrix();
        homeToWindowPositionMap.invert(windowToHomePositionMap);
        windowToHomePositionMap.mapRect(startRect);

        RectFSpringAnim anim = new RectFSpringAnim(startRect, targetRect, mContext);
        if (isFloatingIconView) {
            anim.addAnimatorListener(fiv);
            fiv.setOnTargetChangeListener(anim::onTargetPositionChanged);
            fiv.setFastFinishRunnable(anim::end);
        }

        SpringAnimationRunner runner = new SpringAnimationRunner(
                homeAnimationFactory, cropRectF, homeToWindowPositionMap);
        anim.addOnUpdateListener(runner);
        anim.addAnimatorListener(runner);
        return anim;
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

    protected abstract class HomeAnimationFactory {

        private FloatingIconView mIconView;

        public HomeAnimationFactory(@Nullable FloatingIconView iconView) {
            mIconView = iconView;
        }

        public @NonNull RectF getWindowTargetRect() {
            PagedOrientationHandler orientationHandler = getOrientationHandler();
            DeviceProfile dp = mDp;
            final int halfIconSize = dp.iconSizePx / 2;
            float primaryDimension = orientationHandler
                    .getPrimaryValue(dp.availableWidthPx, dp.availableHeightPx);
            float secondaryDimension = orientationHandler
                    .getSecondaryValue(dp.availableWidthPx, dp.availableHeightPx);
            final float targetX =  primaryDimension / 2f;
            final float targetY = secondaryDimension - dp.hotseatBarSizePx;
            // Fallback to animate to center of screen.
            return new RectF(targetX - halfIconSize, targetY - halfIconSize,
                    targetX + halfIconSize, targetY + halfIconSize);
        }

        public abstract @NonNull AnimatorPlaybackController createActivityAnimationToHome();

        public void playAtomicAnimation(float velocity) {
            // No-op
        }
    }

    private class SpringAnimationRunner extends AnimationSuccessListener
            implements RectFSpringAnim.OnUpdateListener, BuilderProxy {

        final Rect mCropRect = new Rect();
        final Matrix mMatrix = new Matrix();

        final RectF mWindowCurrentRect = new RectF();
        final Matrix mHomeToWindowPositionMap;

        final FloatingIconView mFIV;
        final AnimatorPlaybackController mHomeAnim;
        final RectF mCropRectF;

        final float mStartRadius;
        final float mEndRadius;
        final float mWindowAlphaThreshold;

        SpringAnimationRunner(HomeAnimationFactory factory, RectF cropRectF,
                Matrix homeToWindowPositionMap) {
            mHomeAnim = factory.createActivityAnimationToHome();
            mCropRectF = cropRectF;
            mHomeToWindowPositionMap = homeToWindowPositionMap;

            cropRectF.roundOut(mCropRect);
            mFIV = factory.mIconView;

            // End on a "round-enough" radius so that the shape reveal doesn't have to do too much
            // rounding at the end of the animation.
            mStartRadius = mTaskViewSimulator.getCurrentCornerRadius();
            mEndRadius = cropRectF.width() / 2f;

            // We want the window alpha to be 0 once this threshold is met, so that the
            // FolderIconView can be seen morphing into the icon shape.
            mWindowAlphaThreshold = mFIV != null ? 1f - SHAPE_PROGRESS_DURATION : 1f;
        }

        @Override
        public void onUpdate(RectF currentRect, float progress) {
            mHomeAnim.setPlayFraction(progress);
            mHomeToWindowPositionMap.mapRect(mWindowCurrentRect, currentRect);

            mMatrix.setRectToRect(mCropRectF, mWindowCurrentRect, ScaleToFit.FILL);
            float cornerRadius = Utilities.mapRange(progress, mStartRadius, mEndRadius);
            mTransformParams
                    .setTargetAlpha(getWindowAlpha(progress))
                    .setCornerRadius(cornerRadius);

            mTransformParams.applySurfaceParams(mTransformParams.createSurfaceParams(this));
            if (mFIV != null) {
                mFIV.update(currentRect, 1f, progress,
                        mWindowAlphaThreshold, mMatrix.mapRadius(cornerRadius), false);
            }
        }

        @Override
        public void onBuildParams(Builder builder, RemoteAnimationTargetCompat app, int targetMode,
                TransformParams params) {
            if (app.mode == targetMode
                    && app.activityType != RemoteAnimationTargetCompat.ACTIVITY_TYPE_HOME) {
                builder.withMatrix(mMatrix)
                        .withWindowCrop(mCropRect)
                        .withCornerRadius(params.getCornerRadius());
            }
        }

        @Override
        public void onCancel() {
            if (mFIV != null) {
                mFIV.fastFinish();
            }
        }

        @Override
        public void onAnimationStart(Animator animation) {
            mHomeAnim.dispatchOnStart();
        }

        @Override
        public void onAnimationSuccess(Animator animator) {
            mHomeAnim.getAnimationPlayer().end();
        }
    }
}
