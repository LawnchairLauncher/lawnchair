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

import static android.os.VibrationEffect.EFFECT_CLICK;
import static android.os.VibrationEffect.createPredefined;

import static com.android.launcher3.Utilities.postAsyncCallback;
import static com.android.launcher3.anim.Interpolators.DEACCEL;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.quickstep.TouchInteractionService.BACKGROUND_EXECUTOR;
import static com.android.quickstep.TouchInteractionService.MAIN_THREAD_EXECUTOR;
import static com.android.quickstep.TouchInteractionService.TOUCH_INTERACTION_LOG;

import android.annotation.TargetApi;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.animation.Interpolator;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.graphics.RotationMode;
import com.android.quickstep.ActivityControlHelper.ActivityInitListener;
import com.android.quickstep.inputconsumers.InputConsumer;
import com.android.quickstep.util.ClipAnimationHelper;
import com.android.quickstep.util.ClipAnimationHelper.TransformParams;
import com.android.quickstep.util.SwipeAnimationTargetSet.SwipeAnimationListener;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat;

import java.util.function.Consumer;

import androidx.annotation.UiThread;

/**
 * Base class for swipe up handler with some utility methods
 */
@TargetApi(Build.VERSION_CODES.Q)
public abstract class BaseSwipeUpHandler<T extends BaseDraggingActivity>
        implements SwipeAnimationListener {

    private static final String TAG = "BaseSwipeUpHandler";

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

    protected final ClipAnimationHelper mClipAnimationHelper;
    protected final TransformParams mTransformParams = new TransformParams();

    private final Vibrator mVibrator;

    // Shift in the range of [0, 1].
    // 0 => preview snapShot is completely visible, and hotseat is completely translated down
    // 1 => preview snapShot is completely aligned with the recents view and hotseat is completely
    // visible.
    protected final AnimatedFloat mCurrentShift = new AnimatedFloat(this::updateFinalShift);

    protected final ActivityInitListener mActivityInitListener;
    protected final RecentsAnimationWrapper mRecentsAnimationWrapper;

    protected T mActivity;
    protected RecentsView mRecentsView;
    protected DeviceProfile mDp;
    private final int mPageSpacing;

    protected Runnable mGestureEndCallback;

    protected final Handler mMainThreadHandler = MAIN_THREAD_EXECUTOR.getHandler();
    protected MultiStateCallback mStateCallback;

    protected boolean mCanceled;
    protected int mFinishingRecentsAnimationForNewTaskId = -1;

    protected BaseSwipeUpHandler(Context context,
            OverviewComponentObserver overviewComponentObserver,
            RecentsModel recentsModel, InputConsumerController inputConsumer) {
        mContext = context;
        mOverviewComponentObserver = overviewComponentObserver;
        mActivityControlHelper = overviewComponentObserver.getActivityControlHelper();
        mRecentsModel = recentsModel;
        mActivityInitListener =
                mActivityControlHelper.createActivityInitListener(this::onActivityInit);
        mRecentsAnimationWrapper = new RecentsAnimationWrapper(inputConsumer,
                this::createNewInputProxyHandler);

        mClipAnimationHelper = new ClipAnimationHelper(context);
        mPageSpacing = context.getResources().getDimensionPixelSize(R.dimen.recents_page_spacing);
        mVibrator = context.getSystemService(Vibrator.class);
    }

    protected void setStateOnUiThread(int stateFlag) {
        if (Looper.myLooper() == mMainThreadHandler.getLooper()) {
            mStateCallback.setState(stateFlag);
        } else {
            postAsyncCallback(mMainThreadHandler, () -> mStateCallback.setState(stateFlag));
        }
    }

    protected void performHapticFeedback() {
        if (!mVibrator.hasVibrator()) {
            return;
        }
        if (Settings.System.getInt(
                mContext.getContentResolver(), Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) == 0) {
            return;
        }

        VibrationEffect effect = createPredefined(EFFECT_CLICK);
        if (effect == null) {
            return;
        }
        BACKGROUND_EXECUTOR.execute(() -> mVibrator.vibrate(effect));
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
            mRecentsView.getTaskViewAt(mRecentsView.getNextPage()).launchTask(false /* animate */,
                    true /* freezeTaskList */);
        } else {
            int taskId = mRecentsView.getTaskViewAt(mRecentsView.getNextPage()).getTask().key.id;
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

    public interface Factory {

        BaseSwipeUpHandler newHandler(RunningTaskInfo runningTask,
                long touchTimeMs, boolean continuingLastGesture, boolean isLikelyToStartNewTask);
    }
}
