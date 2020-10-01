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

import static android.widget.Toast.LENGTH_SHORT;

import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.VibratorWrapper.OVERVIEW_HAPTIC;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Toast;

import androidx.annotation.CallSuper;
import androidx.annotation.UiThread;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.util.VibratorWrapper;
import com.android.launcher3.util.WindowBounds;
import com.android.quickstep.RecentsAnimationCallbacks.RecentsAnimationListener;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.util.ActivityInitListener;
import com.android.quickstep.util.InputConsumerProxy;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.SurfaceTransactionApplier;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Base class for swipe up handler with some utility methods
 */
@TargetApi(Build.VERSION_CODES.Q)
public abstract class BaseSwipeUpHandler<T extends StatefulActivity<?>, Q extends RecentsView>
        extends SwipeUpAnimationLogic implements RecentsAnimationListener {

    private static final String TAG = "BaseSwipeUpHandler";

    protected final BaseActivityInterface<?, T> mActivityInterface;
    protected final InputConsumerProxy mInputConsumerProxy;

    protected final ActivityInitListener mActivityInitListener;

    protected RecentsAnimationController mRecentsAnimationController;
    protected RecentsAnimationTargets mRecentsAnimationTargets;

    // Callbacks to be made once the recents animation starts
    private final ArrayList<Runnable> mRecentsAnimationStartCallbacks = new ArrayList<>();

    protected T mActivity;
    protected Q mRecentsView;

    protected Runnable mGestureEndCallback;

    protected MultiStateCallback mStateCallback;

    protected boolean mCanceled;

    private boolean mRecentsViewScrollLinked = false;

    protected BaseSwipeUpHandler(Context context, RecentsAnimationDeviceState deviceState,
            GestureState gestureState, InputConsumerController inputConsumer) {
        super(context, deviceState, gestureState, new TransformParams());
        mActivityInterface = gestureState.getActivityInterface();
        mActivityInitListener = mActivityInterface.createActivityInitListener(this::onActivityInit);
        mInputConsumerProxy =
                new InputConsumerProxy(inputConsumer, this::createNewInputProxyHandler);
    }

    /**
     * To be called at the end of constructor of subclasses. This calls various methods which can
     * depend on proper class initialization.
     */
    protected void initAfterSubclassConstructor() {
        initTransitionEndpoints(
                mTaskViewSimulator.getOrientationState().getLauncherDeviceProfile());
    }

    protected void performHapticFeedback() {
        VibratorWrapper.INSTANCE.get(mContext).vibrate(OVERVIEW_HAPTIC);
    }

    public Consumer<MotionEvent> getRecentsViewDispatcher(float navbarRotation) {
        return mRecentsView != null ? mRecentsView.getEventDispatcher(navbarRotation) : null;
    }

    public void setGestureEndCallback(Runnable gestureEndCallback) {
        mGestureEndCallback = gestureEndCallback;
    }

    public abstract Intent getLaunchIntent();

    protected void linkRecentsViewScroll() {
        SurfaceTransactionApplier.create(mRecentsView, applier -> {
            mTransformParams.setSyncTransactionApplier(applier);
            runOnRecentsAnimationStart(() ->
                    mRecentsAnimationTargets.addReleaseCheck(applier));
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
            if (!mCanceled) {
                TaskView nextTask = mRecentsView.getNextPageTaskView();
                if (nextTask != null) {
                    int taskId = nextTask.getTask().key.id;
                    mGestureState.updateLastStartedTaskId(taskId);
                    boolean hasTaskPreviouslyAppeared = mGestureState.getPreviouslyAppearedTaskIds()
                            .contains(taskId);
                    nextTask.launchTask(false /* animate */, true /* freezeTaskList */,
                            success -> {
                                resultCallback.accept(success);
                                if (success) {
                                    if (hasTaskPreviouslyAppeared) {
                                        onRestartPreviouslyAppearedTask();
                                    }
                                } else {
                                    mActivityInterface.onLaunchTaskFailed();
                                    nextTask.notifyTaskLaunchFailed(TAG);
                                    mRecentsAnimationController.finish(true /* toRecents */, null);
                                }
                            }, MAIN_EXECUTOR.getHandler());
                } else {
                    mActivityInterface.onLaunchTaskFailed();
                    Toast.makeText(mContext, R.string.activity_not_available, LENGTH_SHORT).show();
                    mRecentsAnimationController.finish(true /* toRecents */, null);
                }
            }
            mCanceled = false;
        }
    }

    /**
     * Called when we successfully startNewTask() on the task that was previously running. Normally
     * we call resumeLastTask() when returning to the previously running task, but this handles a
     * specific edge case: if we switch from A to B, and back to A before B appears, we need to
     * start A again to ensure it stays on top.
     */
    @CallSuper
    protected void onRestartPreviouslyAppearedTask() {
        // Finish the controller here, since we won't get onTaskAppeared() for a task that already
        // appeared.
        if (mRecentsAnimationController != null) {
            mRecentsAnimationController.finish(false, null);
        }
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
        mTransformParams.setTargetSet(mRecentsAnimationTargets);
        RemoteAnimationTargetCompat runningTaskTarget = targets.findTask(
                mGestureState.getRunningTaskId());

        if (runningTaskTarget != null) {
            mTaskViewSimulator.setPreview(runningTaskTarget);
        }

        // Only initialize the device profile, if it has not been initialized before, as in some
        // configurations targets.homeContentInsets may not be correct.
        if (mActivity == null) {
            DeviceProfile dp = mTaskViewSimulator.getOrientationState().getLauncherDeviceProfile();
            if (targets.minimizedHomeBounds != null && runningTaskTarget != null) {
                Rect overviewStackBounds = mActivityInterface
                        .getOverviewWindowBounds(targets.minimizedHomeBounds, runningTaskTarget);
                dp = dp.getMultiWindowProfile(mContext,
                        new WindowBounds(overviewStackBounds, targets.homeContentInsets));
            } else {
                // If we are not in multi-window mode, home insets should be same as system insets.
                dp = dp.copy(mContext);
            }
            dp.updateInsets(targets.homeContentInsets);
            dp.updateIsSeascape(mContext);
            initTransitionEndpoints(dp);
        }

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
                ActiveGestureLog.INSTANCE.addLog("finishRecentsAnimation", false);
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

    /**
     * Return true if the window should be translated horizontally if the recents view scrolls
     */
    protected abstract boolean moveWindowWithRecentsScroll();

    protected boolean onActivityInit(Boolean alreadyOnHome) {
        T createdActivity = mActivityInterface.getCreatedActivity();
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.PAUSE_NOT_DETECTED, "BaseSwipeUpHandler.1");
        }
        if (createdActivity != null) {
            if (TestProtocol.sDebugTracing) {
                Log.d(TestProtocol.PAUSE_NOT_DETECTED, "BaseSwipeUpHandler.2");
            }
            initTransitionEndpoints(createdActivity.getDeviceProfile());
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
    public void onGestureStarted(boolean isLikelyToStartNewTask) { }

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
            mWindowTransitionController.setProgress(mCurrentShift.value, mDragLengthFactor);
        }
        if (mRecentsAnimationTargets != null) {
            if (mRecentsViewScrollLinked) {
                mTaskViewSimulator.setScroll(mRecentsView.getScrollOffset());
            }
            mTaskViewSimulator.apply(mTransformParams);
        }
    }

    @Override
    protected RectFSpringAnim createWindowAnimationToHome(float startProgress,
            HomeAnimationFactory homeAnimationFactory) {
        RectFSpringAnim anim =
                super.createWindowAnimationToHome(startProgress, homeAnimationFactory);
        if (mRecentsAnimationTargets != null) {
            mRecentsAnimationTargets.addReleaseCheck(anim);
        }
        return anim;
    }

    public interface Factory {

        BaseSwipeUpHandler newHandler(
                GestureState gestureState, long touchTimeMs, boolean continuingLastGesture);
    }
}
