/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.launcher3.BaseActivity.INVISIBLE_BY_STATE_HANDLER;
import static com.android.launcher3.BaseActivity.STATE_HANDLER_INVISIBILITY_FLAGS;
import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.anim.Interpolators.DEACCEL;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.Interpolators.OVERSHOOT_1_2;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.launcher3.config.FeatureFlags.UNSTABLE_SPRINGS;
import static com.android.launcher3.util.DefaultDisplay.getSingleFrameMs;
import static com.android.launcher3.util.SystemUiController.UI_STATE_OVERVIEW;
import static com.android.quickstep.GestureState.GestureEndTarget.HOME;
import static com.android.quickstep.GestureState.GestureEndTarget.LAST_TASK;
import static com.android.quickstep.GestureState.GestureEndTarget.NEW_TASK;
import static com.android.quickstep.GestureState.GestureEndTarget.RECENTS;
import static com.android.quickstep.GestureState.STATE_END_TARGET_ANIMATION_FINISHED;
import static com.android.quickstep.GestureState.STATE_RECENTS_SCROLLING_FINISHED;
import static com.android.quickstep.GestureState.STATE_TASK_APPEARED_DURING_SWITCH;
import static com.android.quickstep.MultiStateCallback.DEBUG_STATES;
import static com.android.quickstep.SysUINavigationMode.Mode.TWO_BUTTONS;
import static com.android.quickstep.util.ShelfPeekAnim.ShelfAnimState.HIDE;
import static com.android.quickstep.util.ShelfPeekAnim.ShelfAnimState.PEEK;
import static com.android.quickstep.util.WindowSizeStrategy.LAUNCHER_ACTIVITY_SIZE_STRATEGY;
import static com.android.quickstep.views.RecentsView.UPDATE_SYSUI_FLAGS_THRESHOLD;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.View.OnApplyWindowInsetsListener;
import android.view.ViewTreeObserver.OnDrawListener;
import android.view.WindowInsets;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.util.TraceHelper;
import com.android.quickstep.BaseActivityInterface.AnimationFactory;
import com.android.quickstep.BaseActivityInterface.HomeAnimationFactory;
import com.android.quickstep.GestureState.GestureEndTarget;
import com.android.quickstep.inputconsumers.OverviewInputConsumer;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.util.AppWindowAnimationHelper.TargetAlphaProvider;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.ShelfPeekAnim;
import com.android.quickstep.util.ShelfPeekAnim.ShelfAnimState;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.views.LiveTileOverlay;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.LatencyTrackerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

/**
 * Handles the navigation gestures when Launcher is the default home activity.
 */
@TargetApi(Build.VERSION_CODES.O)
public class LauncherSwipeHandler<T extends BaseDraggingActivity>
        extends BaseSwipeUpHandler<T, RecentsView> implements OnApplyWindowInsetsListener {
    private static final String TAG = LauncherSwipeHandler.class.getSimpleName();

    private static final String[] STATE_NAMES = DEBUG_STATES ? new String[16] : null;

    private static int getFlagForIndex(int index, String name) {
        if (DEBUG_STATES) {
            STATE_NAMES[index] = name;
        }
        return 1 << index;
    }

    // Launcher UI related states
    private static final int STATE_LAUNCHER_PRESENT = getFlagForIndex(0, "STATE_LAUNCHER_PRESENT");
    private static final int STATE_LAUNCHER_STARTED = getFlagForIndex(1, "STATE_LAUNCHER_STARTED");
    private static final int STATE_LAUNCHER_DRAWN = getFlagForIndex(2, "STATE_LAUNCHER_DRAWN");

    // Internal initialization states
    private static final int STATE_APP_CONTROLLER_RECEIVED =
            getFlagForIndex(3, "STATE_APP_CONTROLLER_RECEIVED");

    // Interaction finish states
    private static final int STATE_SCALED_CONTROLLER_HOME =
            getFlagForIndex(4, "STATE_SCALED_CONTROLLER_HOME");
    private static final int STATE_SCALED_CONTROLLER_RECENTS =
            getFlagForIndex(5, "STATE_SCALED_CONTROLLER_RECENTS");

    private static final int STATE_HANDLER_INVALIDATED =
            getFlagForIndex(6, "STATE_HANDLER_INVALIDATED");
    private static final int STATE_GESTURE_STARTED =
            getFlagForIndex(7, "STATE_GESTURE_STARTED");
    private static final int STATE_GESTURE_CANCELLED =
            getFlagForIndex(8, "STATE_GESTURE_CANCELLED");
    private static final int STATE_GESTURE_COMPLETED =
            getFlagForIndex(9, "STATE_GESTURE_COMPLETED");

    private static final int STATE_CAPTURE_SCREENSHOT =
            getFlagForIndex(10, "STATE_CAPTURE_SCREENSHOT");
    private static final int STATE_SCREENSHOT_CAPTURED =
            getFlagForIndex(11, "STATE_SCREENSHOT_CAPTURED");
    private static final int STATE_SCREENSHOT_VIEW_SHOWN =
            getFlagForIndex(12, "STATE_SCREENSHOT_VIEW_SHOWN");

    private static final int STATE_RESUME_LAST_TASK =
            getFlagForIndex(13, "STATE_RESUME_LAST_TASK");
    private static final int STATE_START_NEW_TASK =
            getFlagForIndex(14, "STATE_START_NEW_TASK");
    private static final int STATE_CURRENT_TASK_FINISHED =
            getFlagForIndex(15, "STATE_CURRENT_TASK_FINISHED");

    private static final int LAUNCHER_UI_STATES =
            STATE_LAUNCHER_PRESENT | STATE_LAUNCHER_DRAWN | STATE_LAUNCHER_STARTED;

    public static final long MAX_SWIPE_DURATION = 350;
    public static final long MIN_SWIPE_DURATION = 80;
    public static final long MIN_OVERSHOOT_DURATION = 120;

    public static final float MIN_PROGRESS_FOR_OVERVIEW = 0.7f;
    private static final float SWIPE_DURATION_MULTIPLIER =
            Math.min(1 / MIN_PROGRESS_FOR_OVERVIEW, 1 / (1 - MIN_PROGRESS_FOR_OVERVIEW));
    private static final String SCREENSHOT_CAPTURED_EVT = "ScreenshotCaptured";

    public static final long RECENTS_ATTACH_DURATION = 300;

    /**
     * Used as the page index for logging when we return to the last task at the end of the gesture.
     */
    private static final int LOG_NO_OP_PAGE_INDEX = -1;

    private final TaskAnimationManager mTaskAnimationManager;

    // Either RectFSpringAnim (if animating home) or ObjectAnimator (from mCurrentShift) otherwise
    private RunningWindowAnim mRunningWindowAnim;
    private boolean mIsShelfPeeking;

    private boolean mContinuingLastGesture;

    private ThumbnailData mTaskSnapshot;

    // Used to control launcher components throughout the swipe gesture.
    private AnimatorPlaybackController mLauncherTransitionController;
    private boolean mHasLauncherTransitionControllerStarted;

    private final TaskViewSimulator mTaskViewSimulator;
    private AnimatorPlaybackController mWindowTransitionController;

    private AnimationFactory mAnimationFactory = (t) -> { };

    private boolean mWasLauncherAlreadyVisible;

    private boolean mPassedOverviewThreshold;
    private boolean mGestureStarted;
    private int mLogAction = Touch.SWIPE;
    private int mLogDirection = Direction.UP;
    private PointF mDownPos;
    private boolean mIsLikelyToStartNewTask;

    private final long mTouchTimeMs;
    private long mLauncherFrameDrawnTime;

    private final Runnable mOnDeferredActivityLaunch = this::onDeferredActivityLaunch;

    public LauncherSwipeHandler(Context context, RecentsAnimationDeviceState deviceState,
            TaskAnimationManager taskAnimationManager, GestureState gestureState,
            long touchTimeMs, boolean continuingLastGesture,
            InputConsumerController inputConsumer) {
        super(context, deviceState, gestureState, inputConsumer);
        mTaskAnimationManager = taskAnimationManager;
        mTouchTimeMs = touchTimeMs;
        mContinuingLastGesture = continuingLastGesture;
        mTaskViewSimulator = new TaskViewSimulator(context, LAUNCHER_ACTIVITY_SIZE_STRATEGY);

        initAfterSubclassConstructor();
        initStateCallbacks();
    }

    private void initStateCallbacks() {
        mStateCallback = new MultiStateCallback(STATE_NAMES);

        mStateCallback.runOnceAtState(STATE_LAUNCHER_PRESENT | STATE_GESTURE_STARTED,
                this::onLauncherPresentAndGestureStarted);

        mStateCallback.runOnceAtState(STATE_LAUNCHER_DRAWN | STATE_GESTURE_STARTED,
                this::initializeLauncherAnimationController);

        mStateCallback.runOnceAtState(STATE_LAUNCHER_PRESENT | STATE_LAUNCHER_DRAWN,
                this::launcherFrameDrawn);

        mStateCallback.runOnceAtState(STATE_LAUNCHER_PRESENT | STATE_LAUNCHER_STARTED
                        | STATE_GESTURE_CANCELLED,
                this::resetStateForAnimationCancel);

        mStateCallback.runOnceAtState(STATE_LAUNCHER_STARTED | STATE_APP_CONTROLLER_RECEIVED,
                this::sendRemoteAnimationsToAnimationFactory);

        mStateCallback.runOnceAtState(STATE_RESUME_LAST_TASK | STATE_APP_CONTROLLER_RECEIVED,
                this::resumeLastTask);
        mStateCallback.runOnceAtState(STATE_START_NEW_TASK | STATE_SCREENSHOT_CAPTURED,
                this::startNewTask);

        mStateCallback.runOnceAtState(STATE_LAUNCHER_PRESENT | STATE_APP_CONTROLLER_RECEIVED
                        | STATE_LAUNCHER_DRAWN | STATE_CAPTURE_SCREENSHOT,
                this::switchToScreenshot);

        mStateCallback.runOnceAtState(STATE_SCREENSHOT_CAPTURED | STATE_GESTURE_COMPLETED
                        | STATE_SCALED_CONTROLLER_RECENTS,
                this::finishCurrentTransitionToRecents);

        mStateCallback.runOnceAtState(STATE_SCREENSHOT_CAPTURED | STATE_GESTURE_COMPLETED
                        | STATE_SCALED_CONTROLLER_HOME,
                this::finishCurrentTransitionToHome);
        mStateCallback.runOnceAtState(STATE_SCALED_CONTROLLER_HOME | STATE_CURRENT_TASK_FINISHED,
                this::reset);

        mStateCallback.runOnceAtState(STATE_LAUNCHER_PRESENT | STATE_APP_CONTROLLER_RECEIVED
                        | STATE_LAUNCHER_DRAWN | STATE_SCALED_CONTROLLER_RECENTS
                        | STATE_CURRENT_TASK_FINISHED | STATE_GESTURE_COMPLETED
                        | STATE_GESTURE_STARTED,
                this::setupLauncherUiAfterSwipeUpToRecentsAnimation);

        mGestureState.runOnceAtState(STATE_END_TARGET_ANIMATION_FINISHED,
                this::continueComputingRecentsScrollIfNecessary);
        mGestureState.runOnceAtState(STATE_END_TARGET_ANIMATION_FINISHED
                        | STATE_RECENTS_SCROLLING_FINISHED,
                this::onSettledOnEndTarget);

        mGestureState.runOnceAtState(STATE_TASK_APPEARED_DURING_SWITCH, this::onTaskAppeared);

        mStateCallback.runOnceAtState(STATE_HANDLER_INVALIDATED, this::invalidateHandler);
        mStateCallback.runOnceAtState(STATE_LAUNCHER_PRESENT | STATE_HANDLER_INVALIDATED,
                this::invalidateHandlerWithLauncher);
        mStateCallback.runOnceAtState(STATE_HANDLER_INVALIDATED | STATE_RESUME_LAST_TASK,
                this::notifyTransitionCancelled);

        if (!ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            mStateCallback.addChangeListener(STATE_APP_CONTROLLER_RECEIVED | STATE_LAUNCHER_PRESENT
                            | STATE_SCREENSHOT_VIEW_SHOWN | STATE_CAPTURE_SCREENSHOT,
                    (b) -> mRecentsView.setRunningTaskHidden(!b));
        }
    }

    @Override
    protected boolean onActivityInit(Boolean alreadyOnHome) {
        super.onActivityInit(alreadyOnHome);
        final T activity = mActivityInterface.getCreatedActivity();
        if (mActivity == activity) {
            return true;
        }
        if (mActivity != null) {
            // The launcher may have been recreated as a result of device rotation.
            int oldState = mStateCallback.getState() & ~LAUNCHER_UI_STATES;
            initStateCallbacks();
            mStateCallback.setState(oldState);
        }
        mWasLauncherAlreadyVisible = alreadyOnHome;
        mActivity = activity;
        // Override the visibility of the activity until the gesture actually starts and we swipe
        // up, or until we transition home and the home animation is composed
        if (alreadyOnHome) {
            mActivity.clearForceInvisibleFlag(STATE_HANDLER_INVISIBILITY_FLAGS);
        } else {
            mActivity.addForceInvisibleFlag(STATE_HANDLER_INVISIBILITY_FLAGS);
        }

        mRecentsView = activity.getOverviewPanel();
        mRecentsView.setOnPageTransitionEndCallback(null);
        linkRecentsViewScroll();
        addLiveTileOverlay();

        mStateCallback.setState(STATE_LAUNCHER_PRESENT);
        if (alreadyOnHome) {
            onLauncherStart();
        } else {
            activity.runOnceOnStart(this::onLauncherStart);
        }

        setupRecentsViewUi();

        if (mDeviceState.getNavMode() == TWO_BUTTONS) {
            // If the device is in two button mode, swiping up will show overview with predictions
            // so we need to kick off switching to the overview predictions as soon as possible
            mActivityInterface.updateOverviewPredictionState();
        }
        return true;
    }

    @Override
    protected boolean moveWindowWithRecentsScroll() {
        return mGestureState.getEndTarget() != HOME;
    }

    private void onLauncherStart() {
        final T activity = mActivityInterface.getCreatedActivity();
        if (mActivity != activity) {
            return;
        }
        if (mStateCallback.hasStates(STATE_HANDLER_INVALIDATED)) {
            return;
        }

        // If we've already ended the gesture and are going home, don't prepare recents UI,
        // as that will set the state as BACKGROUND_APP, overriding the animation to NORMAL.
        if (mGestureState.getEndTarget() != HOME) {
            Runnable initAnimFactory = () -> {
                mAnimationFactory = mActivityInterface.prepareRecentsUI(
                        mWasLauncherAlreadyVisible, true,
                        this::onAnimatorPlaybackControllerCreated);
                maybeUpdateRecentsAttachedState(false /* animate */);
            };
            if (mWasLauncherAlreadyVisible) {
                // Launcher is visible, but might be about to stop. Thus, if we prepare recents
                // now, it might get overridden by moveToRestState() in onStop(). To avoid this,
                // wait until the next gesture (and possibly launcher) starts.
                mStateCallback.runOnceAtState(STATE_GESTURE_STARTED, initAnimFactory);
            } else {
                initAnimFactory.run();
            }
        }
        AbstractFloatingView.closeAllOpenViewsExcept(activity, mWasLauncherAlreadyVisible,
                AbstractFloatingView.TYPE_LISTENER);

        if (mWasLauncherAlreadyVisible) {
            mStateCallback.setState(STATE_LAUNCHER_DRAWN);
        } else {
            Object traceToken = TraceHelper.INSTANCE.beginSection("WTS-init");
            View dragLayer = activity.getDragLayer();
            dragLayer.getViewTreeObserver().addOnDrawListener(new OnDrawListener() {
                boolean mHandled = false;

                @Override
                public void onDraw() {
                    if (mHandled) {
                        return;
                    }
                    mHandled = true;

                    TraceHelper.INSTANCE.endSection(traceToken);
                    dragLayer.post(() ->
                            dragLayer.getViewTreeObserver().removeOnDrawListener(this));
                    if (activity != mActivity) {
                        return;
                    }

                    mStateCallback.setState(STATE_LAUNCHER_DRAWN);
                }
            });
        }

        activity.getRootView().setOnApplyWindowInsetsListener(this);
        mStateCallback.setState(STATE_LAUNCHER_STARTED);
    }

    private void onLauncherPresentAndGestureStarted() {
        // Re-setup the recents UI when gesture starts, as the state could have been changed during
        // that time by a previous window transition.
        setupRecentsViewUi();

        // For the duration of the gesture, in cases where an activity is launched while the
        // activity is not yet resumed, finish the animation to ensure we get resumed
        mGestureState.getActivityInterface().setOnDeferredActivityLaunchCallback(
                mOnDeferredActivityLaunch);

        notifyGestureStartedAsync();
    }

    private void onDeferredActivityLaunch() {
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            mActivityInterface.switchRunningTaskViewToScreenshot(
                    null, () -> {
                        mTaskAnimationManager.finishRunningRecentsAnimation(true /* toHome */);
                    });
        } else {
            mTaskAnimationManager.finishRunningRecentsAnimation(true /* toHome */);
        }
    }

    private void setupRecentsViewUi() {
        if (mContinuingLastGesture) {
            updateSysUiFlags(mCurrentShift.value);
            return;
        }
        mRecentsView.onGestureAnimationStart(mGestureState.getRunningTaskId());
    }

    private void launcherFrameDrawn() {
        mLauncherFrameDrawnTime = SystemClock.uptimeMillis();
    }

    private void sendRemoteAnimationsToAnimationFactory() {
        mAnimationFactory.onRemoteAnimationReceived(mRecentsAnimationTargets);
    }

    private void initializeLauncherAnimationController() {
        buildAnimationController();

        Object traceToken = TraceHelper.INSTANCE.beginSection("logToggleRecents",
                TraceHelper.FLAG_IGNORE_BINDERS);
        // Only used in debug builds
        if (LatencyTrackerCompat.isEnabled(mContext)) {
            LatencyTrackerCompat.logToggleRecents(
                    (int) (mLauncherFrameDrawnTime - mTouchTimeMs));
        }
        TraceHelper.INSTANCE.endSection(traceToken);

        // This method is only called when STATE_GESTURE_STARTED is set, so we can enable the
        // high-res thumbnail loader here once we are sure that we will end up in an overview state
        RecentsModel.INSTANCE.get(mContext).getThumbnailCache()
                .getHighResLoadingState().setVisible(true);
    }

    @Override
    public void onMotionPauseChanged(boolean isPaused) {
        setShelfState(isPaused ? PEEK : HIDE, ShelfPeekAnim.INTERPOLATOR, ShelfPeekAnim.DURATION);

        if (mDeviceState.isFullyGesturalNavMode() && isPaused) {
            // In fully gestural nav mode, switch to overview predictions once the user has paused
            // (this is a no-op if the predictions are already in that state)
            mActivityInterface.updateOverviewPredictionState();
        }
    }

    public void maybeUpdateRecentsAttachedState() {
        maybeUpdateRecentsAttachedState(true /* animate */);
    }

    /**
     * Determines whether to show or hide RecentsView. The window is always
     * synchronized with its corresponding TaskView in RecentsView, so if
     * RecentsView is shown, it will appear to be attached to the window.
     *
     * Note this method has no effect unless the navigation mode is NO_BUTTON.
     */
    private void maybeUpdateRecentsAttachedState(boolean animate) {
        if (!mDeviceState.isFullyGesturalNavMode() || mRecentsView == null) {
            return;
        }
        RemoteAnimationTargetCompat runningTaskTarget = mRecentsAnimationTargets != null
                ? mRecentsAnimationTargets.findTask(mGestureState.getRunningTaskId())
                : null;
        final boolean recentsAttachedToAppWindow;
        if (mGestureState.getEndTarget() != null) {
            recentsAttachedToAppWindow = mGestureState.getEndTarget().recentsAttachedToAppWindow;
        } else if (mContinuingLastGesture
                && mRecentsView.getRunningTaskIndex() != mRecentsView.getNextPage()) {
            recentsAttachedToAppWindow = true;
        } else if (runningTaskTarget != null && isNotInRecents(runningTaskTarget)) {
            // The window is going away so make sure recents is always visible in this case.
            recentsAttachedToAppWindow = true;
        } else {
            recentsAttachedToAppWindow = mIsShelfPeeking || mIsLikelyToStartNewTask;
        }
        mAnimationFactory.setRecentsAttachedToAppWindow(recentsAttachedToAppWindow, animate);
    }

    @Override
    public void setIsLikelyToStartNewTask(boolean isLikelyToStartNewTask) {
        if (mIsLikelyToStartNewTask != isLikelyToStartNewTask) {
            mIsLikelyToStartNewTask = isLikelyToStartNewTask;
            maybeUpdateRecentsAttachedState();
        }
    }

    @UiThread
    public void setShelfState(ShelfAnimState shelfState, Interpolator interpolator, long duration) {
        mAnimationFactory.setShelfState(shelfState, interpolator, duration);
        boolean wasShelfPeeking = mIsShelfPeeking;
        mIsShelfPeeking = shelfState == PEEK;
        if (mIsShelfPeeking != wasShelfPeeking) {
            maybeUpdateRecentsAttachedState();
        }
        if (shelfState.shouldPreformHaptic) {
            performHapticFeedback();
        }
    }

    private void buildAnimationController() {
        if (!canCreateNewOrUpdateExistingLauncherTransitionController()) {
            return;
        }
        initTransitionEndpoints(mActivity.getDeviceProfile());
        mAnimationFactory.createActivityInterface(mTransitionDragLength);
    }

    @Override
    protected void updateSource(Rect stackBounds, RemoteAnimationTargetCompat runningTarget) {
        super.updateSource(stackBounds, runningTarget);
        mTaskViewSimulator.setPreview(runningTarget, mRecentsAnimationTargets);
    }

    @Override
    protected void initTransitionEndpoints(DeviceProfile dp) {
        super.initTransitionEndpoints(dp);
        mTaskViewSimulator.setDp(dp);
        mTaskViewSimulator.setLayoutRotation(
                mDeviceState.getCurrentActiveRotation(),
                mDeviceState.getDisplayRotation());

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
     * We don't want to change mLauncherTransitionController if mGestureState.getEndTarget() == HOME
     * (it has its own animation) or if we're already animating the current controller.
     * @return Whether we can create the launcher controller or update its progress.
     */
    private boolean canCreateNewOrUpdateExistingLauncherTransitionController() {
        return mGestureState.getEndTarget() != HOME && !mHasLauncherTransitionControllerStarted;
    }

    @Override
    public WindowInsets onApplyWindowInsets(View view, WindowInsets windowInsets) {
        WindowInsets result = view.onApplyWindowInsets(windowInsets);
        buildAnimationController();
        return result;
    }

    private void onAnimatorPlaybackControllerCreated(AnimatorPlaybackController anim) {
        mLauncherTransitionController = anim;
        mLauncherTransitionController.dispatchSetInterpolator(t -> t * mDragLengthFactor);
        mLauncherTransitionController.dispatchOnStart();
        updateLauncherTransitionProgress();
    }

    @Override
    public Intent getLaunchIntent() {
        return mGestureState.getOverviewIntent();
    }

    @Override
    public void updateFinalShift() {
        if (mRecentsAnimationTargets != null) {
            // Base class expects applyTransformUnchecked to be called here.
            // TODO: Remove this dependency for swipe-up animation.
            // applyTransformUnchecked();
            updateSysUiFlags(mCurrentShift.value);
        }

        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            if (mRecentsAnimationTargets != null) {
                LiveTileOverlay.INSTANCE.update(
                        mAppWindowAnimationHelper.getCurrentRectWithInsets(),
                        mAppWindowAnimationHelper.getCurrentCornerRadius());
            }
        }

        final boolean passed = mCurrentShift.value >= MIN_PROGRESS_FOR_OVERVIEW;
        if (passed != mPassedOverviewThreshold) {
            mPassedOverviewThreshold = passed;
            if (!mDeviceState.isFullyGesturalNavMode()) {
                performHapticFeedback();
            }
        }

        if (mWindowTransitionController != null) {
            float progress = mCurrentShift.value / mDragLengthFactor;
            mWindowTransitionController.setPlayFraction(progress);
            mTransformParams
                    .setTargetSet(mRecentsAnimationTargets)
                    .setLauncherOnTop(true);

            mTaskViewSimulator.setScroll(mRecentsView == null ? 0 : mRecentsView.getScrollOffset());
            mTaskViewSimulator.apply(mTransformParams);
        }
        updateLauncherTransitionProgress();
    }

    private void updateLauncherTransitionProgress() {
        if (mLauncherTransitionController == null
                || !canCreateNewOrUpdateExistingLauncherTransitionController()) {
            return;
        }
        // Normalize the progress to 0 to 1, as the animation controller will clamp it to that
        // anyway. The controller mimics the drag length factor by applying it to its interpolators.
        float progress = mCurrentShift.value / mDragLengthFactor;
        mLauncherTransitionController.setPlayFraction(progress);
    }

    /**
     * @param windowProgress 0 == app, 1 == overview
     */
    private void updateSysUiFlags(float windowProgress) {
        if (mRecentsAnimationController != null && mRecentsView != null) {
            TaskView runningTask = mRecentsView.getRunningTaskView();
            TaskView centermostTask = mRecentsView.getTaskViewNearestToCenterOfScreen();
            int centermostTaskFlags = centermostTask == null ? 0
                    : centermostTask.getThumbnail().getSysUiStatusNavFlags();
            boolean swipeUpThresholdPassed = windowProgress > 1 - UPDATE_SYSUI_FLAGS_THRESHOLD;
            boolean quickswitchThresholdPassed = centermostTask != runningTask;

            // We will handle the sysui flags based on the centermost task view.
            mRecentsAnimationController.setUseLauncherSystemBarFlags(
                    (swipeUpThresholdPassed || quickswitchThresholdPassed)
                            && centermostTaskFlags != 0);
            mRecentsAnimationController.setSplitScreenMinimized(swipeUpThresholdPassed);

            int sysuiFlags = swipeUpThresholdPassed ? 0 : centermostTaskFlags;
            mActivity.getSystemUiController().updateUiState(UI_STATE_OVERVIEW, sysuiFlags);
        }
    }

    @Override
    public void onRecentsAnimationStart(RecentsAnimationController controller,
            RecentsAnimationTargets targets) {
        ActiveGestureLog.INSTANCE.addLog("startRecentsAnimationCallback", targets.apps.length);
        super.onRecentsAnimationStart(controller, targets);

        // Only add the callback to enable the input consumer after we actually have the controller
        mStateCallback.runOnceAtState(STATE_APP_CONTROLLER_RECEIVED | STATE_GESTURE_STARTED,
                mRecentsAnimationController::enableInputConsumer);
        mStateCallback.setStateOnUiThread(STATE_APP_CONTROLLER_RECEIVED);

        mPassedOverviewThreshold = false;
    }

    @Override
    public void onRecentsAnimationCanceled(ThumbnailData thumbnailData) {
        ActiveGestureLog.INSTANCE.addLog("cancelRecentsAnimation");
        mActivityInitListener.unregister();
        mStateCallback.setStateOnUiThread(STATE_GESTURE_CANCELLED | STATE_HANDLER_INVALIDATED);

        // Defer clearing the controller and the targets until after we've updated the state
        super.onRecentsAnimationCanceled(thumbnailData);
    }

    @Override
    public void onGestureStarted() {
        notifyGestureStartedAsync();
        mStateCallback.setStateOnUiThread(STATE_GESTURE_STARTED);
        mGestureStarted = true;
    }

    /**
     * Notifies the launcher that the swipe gesture has started. This can be called multiple times.
     */
    @UiThread
    private void notifyGestureStartedAsync() {
        final T curActivity = mActivity;
        if (curActivity != null) {
            // Once the gesture starts, we can no longer transition home through the button, so
            // reset the force override of the activity visibility
            mActivity.clearForceInvisibleFlag(STATE_HANDLER_INVISIBILITY_FLAGS);
        }
    }

    /**
     * Called as a result on ACTION_CANCEL to return the UI to the start state.
     */
    @Override
    public void onGestureCancelled() {
        updateDisplacement(0);
        mStateCallback.setStateOnUiThread(STATE_GESTURE_COMPLETED);
        mLogAction = Touch.SWIPE_NOOP;
        handleNormalGestureEnd(0, false, new PointF(), true /* isCancel */);
    }

    /**
     * @param endVelocity The velocity in the direction of the nav bar to the middle of the screen.
     * @param velocity The x and y components of the velocity when the gesture ends.
     * @param downPos The x and y value of where the gesture started.
     */
    @Override
    public void onGestureEnded(float endVelocity, PointF velocity, PointF downPos) {
        float flingThreshold = mContext.getResources()
                .getDimension(R.dimen.quickstep_fling_threshold_velocity);
        boolean isFling = mGestureStarted && Math.abs(endVelocity) > flingThreshold;
        mStateCallback.setStateOnUiThread(STATE_GESTURE_COMPLETED);

        mLogAction = isFling ? Touch.FLING : Touch.SWIPE;
        boolean isVelocityVertical = Math.abs(velocity.y) > Math.abs(velocity.x);
        if (isVelocityVertical) {
            mLogDirection = velocity.y < 0 ? Direction.UP : Direction.DOWN;
        } else {
            mLogDirection = velocity.x < 0 ? Direction.LEFT : Direction.RIGHT;
        }
        mDownPos = downPos;
        handleNormalGestureEnd(endVelocity, isFling, velocity, false /* isCancel */);
    }

    @Override
    protected InputConsumer createNewInputProxyHandler() {
        endRunningWindowAnim(mGestureState.getEndTarget() == HOME /* cancel */);
        endLauncherTransitionController();
        if (!ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            // Hide the task view, if not already hidden
            setTargetAlphaProvider(LauncherSwipeHandler::getHiddenTargetAlpha);
        }

        BaseDraggingActivity activity = mActivityInterface.getCreatedActivity();
        return activity == null ? InputConsumer.NO_OP
                : new OverviewInputConsumer(mGestureState, activity, null, true);
    }

    private void endRunningWindowAnim(boolean cancel) {
        if (mRunningWindowAnim != null) {
            if (cancel) {
                mRunningWindowAnim.cancel();
            } else {
                mRunningWindowAnim.end();
            }
        }
    }

    private void onSettledOnEndTarget() {
        switch (mGestureState.getEndTarget()) {
            case HOME:
                mStateCallback.setState(STATE_SCALED_CONTROLLER_HOME | STATE_CAPTURE_SCREENSHOT);
                // Notify swipe-to-home (recents animation) is finished
                SystemUiProxy.INSTANCE.get(mContext).notifySwipeToHomeFinished();
                break;
            case RECENTS:
                mStateCallback.setState(STATE_SCALED_CONTROLLER_RECENTS | STATE_CAPTURE_SCREENSHOT
                        | STATE_SCREENSHOT_VIEW_SHOWN);
                break;
            case NEW_TASK:
                mStateCallback.setState(STATE_START_NEW_TASK | STATE_CAPTURE_SCREENSHOT);
                break;
            case LAST_TASK:
                mStateCallback.setState(STATE_RESUME_LAST_TASK);
                break;
        }
    }

    private void onTaskAppeared() {
        RemoteAnimationTargetCompat app = mGestureState.getAnimationTarget();
        if (mRecentsAnimationController != null && app != null) {

            // TODO(b/152480470): Update Task target animation after onTaskAppeared holistically.
            /* android.util.Log.d("LauncherSwipeHandler", "onTaskAppeared");

            final boolean result = mRecentsAnimationController.removeTaskTarget(app);
            mGestureState.setAnimationTarget(null);
            android.util.Log.d("LauncherSwipeHandler", "removeTask, result=" + result); */

            mRecentsAnimationController.finish(false /* toRecents */,
                    null /* onFinishComplete */);
        }
    }

    private GestureEndTarget calculateEndTarget(PointF velocity, float endVelocity, boolean isFling,
            boolean isCancel) {
        final GestureEndTarget endTarget;
        final boolean goingToNewTask;
        if (mRecentsView != null) {
            if (!hasTargets()) {
                // If there are no running tasks, then we can assume that this is a continuation of
                // the last gesture, but after the recents animation has finished
                goingToNewTask = true;
            } else {
                final int runningTaskIndex = mRecentsView.getRunningTaskIndex();
                final int taskToLaunch = mRecentsView.getNextPage();
                goingToNewTask = runningTaskIndex >= 0 && taskToLaunch != runningTaskIndex;
            }
        } else {
            goingToNewTask = false;
        }
        final boolean reachedOverviewThreshold = mCurrentShift.value >= MIN_PROGRESS_FOR_OVERVIEW;
        if (!isFling) {
            if (isCancel) {
                endTarget = LAST_TASK;
            } else if (mDeviceState.isFullyGesturalNavMode()) {
                if (mIsShelfPeeking) {
                    endTarget = RECENTS;
                } else if (goingToNewTask) {
                    endTarget = NEW_TASK;
                } else {
                    endTarget = !reachedOverviewThreshold ? LAST_TASK : HOME;
                }
            } else {
                endTarget = reachedOverviewThreshold && mGestureStarted
                        ? RECENTS
                        : goingToNewTask
                                ? NEW_TASK
                                : LAST_TASK;
            }
        } else {
            // If swiping at a diagonal, base end target on the faster velocity.
            boolean isSwipeUp = endVelocity < 0;
            boolean willGoToNewTaskOnSwipeUp =
                    goingToNewTask && Math.abs(velocity.x) > Math.abs(endVelocity);

            if (mDeviceState.isFullyGesturalNavMode() && isSwipeUp && !willGoToNewTaskOnSwipeUp) {
                endTarget = HOME;
            } else if (mDeviceState.isFullyGesturalNavMode() && isSwipeUp && !mIsShelfPeeking) {
                // If swiping at a diagonal, base end target on the faster velocity.
                endTarget = NEW_TASK;
            } else if (isSwipeUp) {
                endTarget = !reachedOverviewThreshold && willGoToNewTaskOnSwipeUp
                        ? NEW_TASK : RECENTS;
            } else {
                endTarget = goingToNewTask ? NEW_TASK : LAST_TASK;
            }
        }

        if (endTarget == RECENTS || endTarget == HOME) {
            // Since we're now done quickStepping, we want to only listen for touch events
            // for the main orientation's nav bar, instead of multiple
            mDeviceState.enableMultipleRegions(false);
        }

        if (mDeviceState.isOverviewDisabled() && (endTarget == RECENTS || endTarget == LAST_TASK)) {
            return LAST_TASK;
        }
        return endTarget;
    }

    @UiThread
    private void handleNormalGestureEnd(float endVelocity, boolean isFling, PointF velocity,
            boolean isCancel) {
        PointF velocityPxPerMs = new PointF(velocity.x / 1000, velocity.y / 1000);
        long duration = MAX_SWIPE_DURATION;
        float currentShift = mCurrentShift.value;
        final GestureEndTarget endTarget = calculateEndTarget(velocity, endVelocity,
                isFling, isCancel);
        float endShift = endTarget.isLauncher ? 1 : 0;
        final float startShift;
        Interpolator interpolator = DEACCEL;
        if (!isFling) {
            long expectedDuration = Math.abs(Math.round((endShift - currentShift)
                    * MAX_SWIPE_DURATION * SWIPE_DURATION_MULTIPLIER));
            duration = Math.min(MAX_SWIPE_DURATION, expectedDuration);
            startShift = currentShift;
            interpolator = endTarget == RECENTS ? OVERSHOOT_1_2 : DEACCEL;
        } else {
            startShift = Utilities.boundToRange(currentShift - velocityPxPerMs.y
                    * getSingleFrameMs(mContext) / mTransitionDragLength, 0, mDragLengthFactor);
            float minFlingVelocity = mContext.getResources()
                    .getDimension(R.dimen.quickstep_fling_min_velocity);
            if (Math.abs(endVelocity) > minFlingVelocity && mTransitionDragLength > 0) {
                if (endTarget == RECENTS && !mDeviceState.isFullyGesturalNavMode()) {
                    Interpolators.OvershootParams overshoot = new Interpolators.OvershootParams(
                            startShift, endShift, endShift, endVelocity / 1000,
                            mTransitionDragLength, mContext);
                    endShift = overshoot.end;
                    interpolator = overshoot.interpolator;
                    duration = Utilities.boundToRange(overshoot.duration, MIN_OVERSHOOT_DURATION,
                            MAX_SWIPE_DURATION);
                } else {
                    float distanceToTravel = (endShift - currentShift) * mTransitionDragLength;

                    // we want the page's snap velocity to approximately match the velocity at
                    // which the user flings, so we scale the duration by a value near to the
                    // derivative of the scroll interpolator at zero, ie. 2.
                    long baseDuration = Math.round(Math.abs(distanceToTravel / velocityPxPerMs.y));
                    duration = Math.min(MAX_SWIPE_DURATION, 2 * baseDuration);

                    if (endTarget == RECENTS) {
                        interpolator = OVERSHOOT_1_2;
                    }
                }
            }
        }

        if (endTarget.isLauncher && mRecentsAnimationController != null) {
            mRecentsAnimationController.enableInputProxy(mInputConsumer,
                    this::createNewInputProxyHandler);
        }

        if (endTarget == HOME) {
            setShelfState(ShelfAnimState.CANCEL, LINEAR, 0);
            duration = Math.max(MIN_OVERSHOOT_DURATION, duration);
        } else if (endTarget == RECENTS) {
            LiveTileOverlay.INSTANCE.startIconAnimation();
            if (mRecentsView != null) {
                int nearestPage = mRecentsView.getPageNearestToCenterOfScreen();
                if (mRecentsView.getNextPage() != nearestPage) {
                    // We shouldn't really scroll to the next page when swiping up to recents.
                    // Only allow settling on the next page if it's nearest to the center.
                    mRecentsView.snapToPage(nearestPage, Math.toIntExact(duration));
                }
                if (mRecentsView.getScroller().getDuration() > MAX_SWIPE_DURATION) {
                    mRecentsView.snapToPage(mRecentsView.getNextPage(), (int) MAX_SWIPE_DURATION);
                }
                duration = Math.max(duration, mRecentsView.getScroller().getDuration());
            }
            if (mDeviceState.isFullyGesturalNavMode()) {
                setShelfState(ShelfAnimState.OVERVIEW, interpolator, duration);
            }
        }

        // Let RecentsView handle the scrolling to the task, which we launch in startNewTask()
        // or resumeLastTask().
        if (mRecentsView != null) {
            mRecentsView.setOnPageTransitionEndCallback(
                    () -> mGestureState.setState(STATE_RECENTS_SCROLLING_FINISHED));
        } else {
            mGestureState.setState(STATE_RECENTS_SCROLLING_FINISHED);
        }

        animateToProgress(startShift, endShift, duration, interpolator, endTarget, velocityPxPerMs);
    }

    private void doLogGesture(GestureEndTarget endTarget) {
        DeviceProfile dp = mDp;
        if (dp == null || mDownPos == null) {
            // We probably never received an animation controller, skip logging.
            return;
        }

        int pageIndex = endTarget == LAST_TASK
                ? LOG_NO_OP_PAGE_INDEX
                : mRecentsView.getNextPage();
        UserEventDispatcher.newInstance(mContext).logStateChangeAction(
                mLogAction, mLogDirection,
                (int) mDownPos.x, (int) mDownPos.y,
                ContainerType.NAVBAR, ContainerType.APP,
                endTarget.containerType,
                pageIndex);
    }

    /** Animates to the given progress, where 0 is the current app and 1 is overview. */
    @UiThread
    private void animateToProgress(float start, float end, long duration, Interpolator interpolator,
            GestureEndTarget target, PointF velocityPxPerMs) {
        runOnRecentsAnimationStart(() -> animateToProgressInternal(start, end, duration,
                interpolator, target, velocityPxPerMs));
    }

    @UiThread
    private void animateToProgressInternal(float start, float end, long duration,
            Interpolator interpolator, GestureEndTarget target, PointF velocityPxPerMs) {
        // Set the state, but don't notify until the animation completes
        mGestureState.setEndTarget(target, false /* isAtomic */);

        maybeUpdateRecentsAttachedState();

        if (mGestureState.getEndTarget() == HOME) {
            HomeAnimationFactory homeAnimFactory;
            if (mActivity != null) {
                homeAnimFactory = mActivityInterface.prepareHomeUI();
            } else {
                homeAnimFactory = new HomeAnimationFactory() {
                    @NonNull
                    @Override
                    public RectF getWindowTargetRect() {
                        RectF fallbackTarget = new RectF(mAppWindowAnimationHelper.getTargetRect());
                        Utilities.scaleRectFAboutCenter(fallbackTarget, 0.25f);
                        return fallbackTarget;
                    }

                    @NonNull
                    @Override
                    public AnimatorPlaybackController createActivityAnimationToHome() {
                        return AnimatorPlaybackController.wrap(new AnimatorSet(), duration);
                    }
                };
                mStateCallback.addChangeListener(STATE_LAUNCHER_PRESENT | STATE_HANDLER_INVALIDATED,
                        isPresent -> mRecentsView.startHome());
            }
            RectFSpringAnim windowAnim = createWindowAnimationToHome(start, homeAnimFactory);
            windowAnim.addAnimatorListener(new AnimationSuccessListener() {
                @Override
                public void onAnimationSuccess(Animator animator) {
                    if (mRecentsAnimationController == null) {
                        // If the recents animation is interrupted, we still end the running
                        // animation (not canceled) so this is still called. In that case, we can
                        // skip doing any future work here for the current gesture.
                        return;
                    }
                    // Finalize the state and notify of the change
                    mGestureState.setState(STATE_END_TARGET_ANIMATION_FINISHED);
                }
            });
            getOrientationHandler().adjustFloatingIconStartVelocity(velocityPxPerMs);
            windowAnim.start(mContext, velocityPxPerMs);
            homeAnimFactory.playAtomicAnimation(velocityPxPerMs.y);
            mRunningWindowAnim = RunningWindowAnim.wrap(windowAnim);
            mLauncherTransitionController = null;
        } else {
            ValueAnimator windowAnim = mCurrentShift.animateToValue(start, end);
            windowAnim.setDuration(duration).setInterpolator(interpolator);
            windowAnim.addUpdateListener(valueAnimator -> {
                computeRecentsScrollIfInvisible();
            });
            windowAnim.addListener(new AnimationSuccessListener() {
                @Override
                public void onAnimationSuccess(Animator animator) {
                    if (mRecentsAnimationController == null) {
                        // If the recents animation is interrupted, we still end the running
                        // animation (not canceled) so this is still called. In that case, we can
                        // skip doing any future work here for the current gesture.
                        return;
                    }
                    if (target == NEW_TASK && mRecentsView != null
                            && mRecentsView.getNextPage() == mRecentsView.getRunningTaskIndex()) {
                        // We are about to launch the current running task, so use LAST_TASK state
                        // instead of NEW_TASK. This could happen, for example, if our scroll is
                        // aborted after we determined the target to be NEW_TASK.
                        mGestureState.setEndTarget(LAST_TASK);
                    }
                    mGestureState.setState(STATE_END_TARGET_ANIMATION_FINISHED);
                }
            });
            windowAnim.start();
            mRunningWindowAnim = RunningWindowAnim.wrap(windowAnim);
        }
        // Always play the entire launcher animation when going home, since it is separate from
        // the animation that has been controlled thus far.
        if (mGestureState.getEndTarget() == HOME) {
            start = 0;
        }

        // We want to use the same interpolator as the window, but need to adjust it to
        // interpolate over the remaining progress (end - start).
        TimeInterpolator adjustedInterpolator = Interpolators.mapToProgress(
                interpolator, start, end);
        if (mLauncherTransitionController == null) {
            return;
        }
        if (start == end || duration <= 0) {
            mLauncherTransitionController.dispatchSetInterpolator(t -> end);
        } else {
            mLauncherTransitionController.dispatchSetInterpolator(adjustedInterpolator);
        }
        mLauncherTransitionController.getAnimationPlayer().setDuration(Math.max(0, duration));

        if (UNSTABLE_SPRINGS.get()) {
            mLauncherTransitionController.dispatchOnStart();
        }
        mLauncherTransitionController.getAnimationPlayer().start();
        mHasLauncherTransitionControllerStarted = true;
    }

    private void computeRecentsScrollIfInvisible() {
        if (mRecentsView != null && mRecentsView.getVisibility() != View.VISIBLE) {
            // Views typically don't compute scroll when invisible as an optimization,
            // but in our case we need to since the window offset depends on the scroll.
            mRecentsView.computeScroll();
        }
    }

    private void continueComputingRecentsScrollIfNecessary() {
        if (!mGestureState.hasState(STATE_RECENTS_SCROLLING_FINISHED)
                && !mStateCallback.hasStates(STATE_HANDLER_INVALIDATED)
                && !mCanceled) {
            computeRecentsScrollIfInvisible();
            mRecentsView.postOnAnimation(this::continueComputingRecentsScrollIfNecessary);
        }
    }

    /**
     * Creates an animation that transforms the current app window into the home app.
     * @param startProgress The progress of {@link #mCurrentShift} to start the window from.
     * @param homeAnimationFactory The home animation factory.
     */
    @Override
    protected RectFSpringAnim createWindowAnimationToHome(float startProgress,
            HomeAnimationFactory homeAnimationFactory) {
        RectFSpringAnim anim =
                super.createWindowAnimationToHome(startProgress, homeAnimationFactory);
        anim.addOnUpdateListener((r, p) -> {
            updateSysUiFlags(Math.max(p, mCurrentShift.value));
        });
        anim.addAnimatorListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (mActivity != null) {
                    removeLiveTileOverlay();
                }
            }

            @Override
            public void onAnimationSuccess(Animator animator) {
                if (mRecentsView != null) {
                    mRecentsView.post(mRecentsView::resetTaskVisuals);
                }
                // Make sure recents is in its final state
                maybeUpdateRecentsAttachedState(false);
                mActivityInterface.onSwipeUpToHomeComplete();
            }
        });
        return anim;
    }

    @Override
    public void onConsumerAboutToBeSwitched() {
        if (mActivity != null) {
            // In the off chance that the gesture ends before Launcher is started, we should clear
            // the callback here so that it doesn't update with the wrong state
            mActivity.clearRunOnceOnStartCallback();
            resetLauncherListenersAndOverlays();
        }
        if (mGestureState.getEndTarget() != null && !mGestureState.isRunningAnimationToLauncher()) {
            cancelCurrentAnimation();
        } else {
            reset();
        }
    }

    public boolean isCanceled() {
        return mCanceled;
    }

    @UiThread
    private void resumeLastTask() {
        mRecentsAnimationController.finish(false /* toRecents */, null);
        ActiveGestureLog.INSTANCE.addLog("finishRecentsAnimation", false);
        doLogGesture(LAST_TASK);
        reset();
    }

    @UiThread
    private void startNewTask() {
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            mRecentsAnimationController.finish(true /* toRecents */, this::startNewTaskInternal);
        } else {
            startNewTaskInternal();
        }
    }

    @UiThread
    private void startNewTaskInternal() {
        startNewTask(STATE_HANDLER_INVALIDATED, success -> {
            if (!success) {
                // We couldn't launch the task, so take user to overview so they can
                // decide what to do instead of staying in this broken state.
                endLauncherTransitionController();
                updateSysUiFlags(1 /* windowProgress == overview */);
            }
            doLogGesture(NEW_TASK);
        });
    }

    private void reset() {
        mStateCallback.setStateOnUiThread(STATE_HANDLER_INVALIDATED);
    }

    /**
     * Cancels any running animation so that the active target can be overriden by a new swipe
     * handle (in case of quick switch).
     */
    private void cancelCurrentAnimation() {
        mCanceled = true;
        mCurrentShift.cancelAnimation();
        if (mLauncherTransitionController != null && mLauncherTransitionController
                .getAnimationPlayer().isStarted()) {
            mLauncherTransitionController.getAnimationPlayer().cancel();
        }

        if (mFinishingRecentsAnimationForNewTaskId != -1) {
            // If we are canceling mid-starting a new task, switch to the screenshot since the
            // recents animation has finished
            switchToScreenshot();
            TaskView newRunningTaskView = mRecentsView.getTaskView(
                    mFinishingRecentsAnimationForNewTaskId);
            int newRunningTaskId = newRunningTaskView != null
                    ? newRunningTaskView.getTask().key.id
                    : -1;
            mRecentsView.setCurrentTask(newRunningTaskId);
            mGestureState.setFinishingRecentsAnimationTaskId(newRunningTaskId);
        }
    }

    private void invalidateHandler() {
        endRunningWindowAnim(false /* cancel */);

        if (mGestureEndCallback != null) {
            mGestureEndCallback.run();
        }

        mActivityInitListener.unregister();
        mTaskSnapshot = null;
    }

    private void invalidateHandlerWithLauncher() {
        endLauncherTransitionController();

        mRecentsView.onGestureAnimationEnd();
        resetLauncherListenersAndOverlays();
    }

    private void endLauncherTransitionController() {
        setShelfState(ShelfAnimState.CANCEL, LINEAR, 0);
        if (mLauncherTransitionController != null) {
            mLauncherTransitionController.getAnimationPlayer().end();
            mLauncherTransitionController = null;
        }
    }

    private void resetLauncherListenersAndOverlays() {
        // Reset the callback for deferred activity launches
        if (!ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            mActivityInterface.setOnDeferredActivityLaunchCallback(null);
        }
        mActivity.getRootView().setOnApplyWindowInsetsListener(null);
        removeLiveTileOverlay();
    }

    private void notifyTransitionCancelled() {
        mAnimationFactory.onTransitionCancelled();
    }

    private void resetStateForAnimationCancel() {
        boolean wasVisible = mWasLauncherAlreadyVisible || mGestureStarted;
        mActivityInterface.onTransitionCancelled(wasVisible);

        // Leave the pending invisible flag, as it may be used by wallpaper open animation.
        mActivity.clearForceInvisibleFlag(INVISIBLE_BY_STATE_HANDLER);
    }

    private void switchToScreenshot() {
        final int runningTaskId = mGestureState.getRunningTaskId();
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            if (mRecentsAnimationController != null) {
                mRecentsAnimationController.getController().setWillFinishToHome(true);
                // Update the screenshot of the task
                if (mTaskSnapshot == null) {
                    mTaskSnapshot = mRecentsAnimationController.screenshotTask(runningTaskId);
                }
                mRecentsView.updateThumbnail(runningTaskId, mTaskSnapshot, false /* refreshNow */);
            }
            mStateCallback.setStateOnUiThread(STATE_SCREENSHOT_CAPTURED);
        } else if (!hasTargets()) {
            // If there are no targets, then we don't need to capture anything
            mStateCallback.setStateOnUiThread(STATE_SCREENSHOT_CAPTURED);
        } else {
            boolean finishTransitionPosted = false;
            if (mRecentsAnimationController != null) {
                // Update the screenshot of the task
                if (mTaskSnapshot == null) {
                    mTaskSnapshot = mRecentsAnimationController.screenshotTask(runningTaskId);
                }
                final TaskView taskView;
                if (mGestureState.getEndTarget() == HOME) {
                    // Capture the screenshot before finishing the transition to home to ensure it's
                    // taken in the correct orientation, but no need to update the thumbnail.
                    taskView = null;
                } else {
                    taskView = mRecentsView.updateThumbnail(runningTaskId, mTaskSnapshot);
                }
                if (taskView != null && !mCanceled) {
                    // Defer finishing the animation until the next launcher frame with the
                    // new thumbnail
                    finishTransitionPosted = ViewUtils.postDraw(taskView,
                            () -> mStateCallback.setStateOnUiThread(STATE_SCREENSHOT_CAPTURED),
                                    this::isCanceled);
                }
            }
            if (!finishTransitionPosted) {
                // If we haven't posted a draw callback, set the state immediately.
                Object traceToken = TraceHelper.INSTANCE.beginSection(SCREENSHOT_CAPTURED_EVT,
                        TraceHelper.FLAG_CHECK_FOR_RACE_CONDITIONS);
                mStateCallback.setStateOnUiThread(STATE_SCREENSHOT_CAPTURED);
                TraceHelper.INSTANCE.endSection(traceToken);
            }
        }
    }

    private void finishCurrentTransitionToRecents() {
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            mStateCallback.setStateOnUiThread(STATE_CURRENT_TASK_FINISHED);
        } else if (!hasTargets() || mRecentsAnimationController == null) {
            // If there are no targets or the animation not started, then there is nothing to finish
            mStateCallback.setStateOnUiThread(STATE_CURRENT_TASK_FINISHED);
        } else {
            mRecentsAnimationController.finish(true /* toRecents */,
                    () -> mStateCallback.setStateOnUiThread(STATE_CURRENT_TASK_FINISHED));
        }
        ActiveGestureLog.INSTANCE.addLog("finishRecentsAnimation", true);
    }

    private void finishCurrentTransitionToHome() {
        if (!hasTargets() || mRecentsAnimationController == null) {
            // If there are no targets or the animation not started, then there is nothing to finish
            mStateCallback.setStateOnUiThread(STATE_CURRENT_TASK_FINISHED);
        } else {
            mRecentsAnimationController.finish(true /* toRecents */,
                    () -> mStateCallback.setStateOnUiThread(STATE_CURRENT_TASK_FINISHED),
                    true /* sendUserLeaveHint */);
        }
        ActiveGestureLog.INSTANCE.addLog("finishRecentsAnimation", true);
        doLogGesture(HOME);
    }

    private void setupLauncherUiAfterSwipeUpToRecentsAnimation() {
        endLauncherTransitionController();
        mActivityInterface.onSwipeUpToRecentsComplete();
        if (mRecentsAnimationController != null) {
            mRecentsAnimationController.setDeferCancelUntilNextTransition(true /* defer */,
                    true /* screenshot */);
        }
        mRecentsView.onSwipeUpAnimationSuccess();

        SystemUiProxy.INSTANCE.get(mContext).onOverviewShown(false, TAG);
        doLogGesture(RECENTS);
        reset();
    }

    private void setTargetAlphaProvider(TargetAlphaProvider provider) {
        mAppWindowAnimationHelper.setTaskAlphaCallback(provider);
        mTaskViewSimulator.setTaskAlphaCallback(provider);
        updateFinalShift();
    }

    private void addLiveTileOverlay() {
        if (LiveTileOverlay.INSTANCE.attach(mActivity.getRootView().getOverlay())) {
            mRecentsView.setLiveTileOverlayAttached(true);
        }
    }

    private void removeLiveTileOverlay() {
        LiveTileOverlay.INSTANCE.detach(mActivity.getRootView().getOverlay());
        mRecentsView.setLiveTileOverlayAttached(false);
    }

    public static float getHiddenTargetAlpha(RemoteAnimationTargetCompat app, float expectedAlpha) {
        if (!isNotInRecents(app)) {
            return 0;
        }
        return expectedAlpha;
    }

    private static boolean isNotInRecents(RemoteAnimationTargetCompat app) {
        return app.isNotInRecents
                || app.activityType == RemoteAnimationTargetCompat.ACTIVITY_TYPE_HOME;
    }
}
