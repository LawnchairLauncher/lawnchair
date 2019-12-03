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
import static com.android.launcher3.anim.Interpolators.DEACCEL;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.Interpolators.OVERSHOOT_1_2;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.launcher3.config.FeatureFlags.QUICKSTEP_SPRINGS;
import static com.android.launcher3.util.DefaultDisplay.getSingleFrameMs;
import static com.android.launcher3.util.RaceConditionTracker.ENTER;
import static com.android.launcher3.util.RaceConditionTracker.EXIT;
import static com.android.launcher3.util.SystemUiController.UI_STATE_OVERVIEW;
import static com.android.quickstep.ActivityControlHelper.AnimationFactory.ShelfAnimState.HIDE;
import static com.android.quickstep.ActivityControlHelper.AnimationFactory.ShelfAnimState.PEEK;
import static com.android.quickstep.MultiStateCallback.DEBUG_STATES;
import static com.android.quickstep.TouchInteractionService.TOUCH_INTERACTION_LOG;
import static com.android.quickstep.WindowTransformSwipeHandler.GestureEndTarget.HOME;
import static com.android.quickstep.WindowTransformSwipeHandler.GestureEndTarget.LAST_TASK;
import static com.android.quickstep.WindowTransformSwipeHandler.GestureEndTarget.NEW_TASK;
import static com.android.quickstep.WindowTransformSwipeHandler.GestureEndTarget.RECENTS;
import static com.android.quickstep.views.RecentsView.UPDATE_SYSUI_FLAGS_THRESHOLD;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_OVERVIEW_DISABLED;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.PointF;
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
import com.android.launcher3.util.RaceConditionTracker;
import com.android.launcher3.util.TraceHelper;
import com.android.quickstep.ActivityControlHelper.AnimationFactory;
import com.android.quickstep.ActivityControlHelper.AnimationFactory.ShelfAnimState;
import com.android.quickstep.ActivityControlHelper.HomeAnimationFactory;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.inputconsumers.InputConsumer;
import com.android.quickstep.inputconsumers.OverviewInputConsumer;
import com.android.quickstep.util.ClipAnimationHelper.TargetAlphaProvider;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.SwipeAnimationTargetSet;
import com.android.quickstep.views.LiveTileOverlay;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.LatencyTrackerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.WindowCallbacksCompat;

@TargetApi(Build.VERSION_CODES.O)
public class WindowTransformSwipeHandler<T extends BaseDraggingActivity>
        extends BaseSwipeUpHandler<T, RecentsView>
        implements OnApplyWindowInsetsListener {
    private static final String TAG = WindowTransformSwipeHandler.class.getSimpleName();

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

    public enum GestureEndTarget {
        HOME(1, STATE_SCALED_CONTROLLER_HOME | STATE_CAPTURE_SCREENSHOT, true, false,
                ContainerType.WORKSPACE, false),

        RECENTS(1, STATE_SCALED_CONTROLLER_RECENTS | STATE_CAPTURE_SCREENSHOT
                | STATE_SCREENSHOT_VIEW_SHOWN, true, false, ContainerType.TASKSWITCHER, true),

        NEW_TASK(0, STATE_START_NEW_TASK | STATE_CAPTURE_SCREENSHOT, false, true,
                ContainerType.APP, true),

        LAST_TASK(0, STATE_RESUME_LAST_TASK, false, true, ContainerType.APP, false);

        GestureEndTarget(float endShift, int endState, boolean isLauncher, boolean canBeContinued,
                int containerType, boolean recentsAttachedToAppWindow) {
            this.endShift = endShift;
            this.endState = endState;
            this.isLauncher = isLauncher;
            this.canBeContinued = canBeContinued;
            this.containerType = containerType;
            this.recentsAttachedToAppWindow = recentsAttachedToAppWindow;
        }

        /** 0 is app, 1 is overview */
        public final float endShift;
        /** The state to apply when we reach this final target */
        public final int endState;
        /** Whether the target is in the launcher activity */
        public final boolean isLauncher;
        /** Whether the user can start a new gesture while this one is finishing */
        public final boolean canBeContinued;
        /** Used to log where the user ended up after the gesture ends */
        public final int containerType;
        /** Whether RecentsView should be attached to the window as we animate to this target */
        public final boolean recentsAttachedToAppWindow;
    }

    public static final long MAX_SWIPE_DURATION = 350;
    public static final long MIN_SWIPE_DURATION = 80;
    public static final long MIN_OVERSHOOT_DURATION = 120;

    public static final float MIN_PROGRESS_FOR_OVERVIEW = 0.7f;
    private static final float SWIPE_DURATION_MULTIPLIER =
            Math.min(1 / MIN_PROGRESS_FOR_OVERVIEW, 1 / (1 - MIN_PROGRESS_FOR_OVERVIEW));
    private static final String SCREENSHOT_CAPTURED_EVT = "ScreenshotCaptured";

    private static final long SHELF_ANIM_DURATION = 240;
    public static final long RECENTS_ATTACH_DURATION = 300;

    /**
     * Used as the page index for logging when we return to the last task at the end of the gesture.
     */
    private static final int LOG_NO_OP_PAGE_INDEX = -1;

    private GestureEndTarget mGestureEndTarget;
    // Either RectFSpringAnim (if animating home) or ObjectAnimator (from mCurrentShift) otherwise
    private RunningWindowAnim mRunningWindowAnim;
    private boolean mIsShelfPeeking;

    private boolean mContinuingLastGesture;
    // To avoid UI jump when gesture is started, we offset the animation by the threshold.
    private float mShiftAtGestureStart = 0;

    private ThumbnailData mTaskSnapshot;

    // Used to control launcher components throughout the swipe gesture.
    private AnimatorPlaybackController mLauncherTransitionController;
    private boolean mHasLauncherTransitionControllerStarted;

    private AnimationFactory mAnimationFactory = (t) -> { };
    private LiveTileOverlay mLiveTileOverlay = new LiveTileOverlay();
    private boolean mLiveTileOverlayAttached = false;

    private boolean mWasLauncherAlreadyVisible;

    private boolean mPassedOverviewThreshold;
    private boolean mGestureStarted;
    private int mLogAction = Touch.SWIPE;
    private int mLogDirection = Direction.UP;
    private PointF mDownPos;
    private boolean mIsLikelyToStartNewTask;

    private final long mTouchTimeMs;
    private long mLauncherFrameDrawnTime;

    public WindowTransformSwipeHandler(RunningTaskInfo runningTaskInfo, Context context,
            long touchTimeMs, OverviewComponentObserver overviewComponentObserver,
            boolean continuingLastGesture,
            InputConsumerController inputConsumer, RecentsModel recentsModel) {
        super(context, overviewComponentObserver, recentsModel, inputConsumer, runningTaskInfo.id);
        mTouchTimeMs = touchTimeMs;
        mContinuingLastGesture = continuingLastGesture;
        initStateCallbacks();
    }

    private void initStateCallbacks() {
        mStateCallback = new MultiStateCallback(STATE_NAMES);

        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_GESTURE_STARTED,
                this::onLauncherPresentAndGestureStarted);

        mStateCallback.addCallback(STATE_LAUNCHER_DRAWN | STATE_GESTURE_STARTED,
                this::initializeLauncherAnimationController);

        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_LAUNCHER_DRAWN,
                this::launcherFrameDrawn);

        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_LAUNCHER_STARTED
                        | STATE_GESTURE_CANCELLED,
                this::resetStateForAnimationCancel);

        mStateCallback.addCallback(STATE_LAUNCHER_STARTED | STATE_APP_CONTROLLER_RECEIVED,
                this::sendRemoteAnimationsToAnimationFactory);

        mStateCallback.addCallback(STATE_RESUME_LAST_TASK | STATE_APP_CONTROLLER_RECEIVED,
                this::resumeLastTask);
        mStateCallback.addCallback(STATE_START_NEW_TASK | STATE_SCREENSHOT_CAPTURED,
                this::startNewTask);

        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_APP_CONTROLLER_RECEIVED
                        | STATE_LAUNCHER_DRAWN | STATE_CAPTURE_SCREENSHOT,
                this::switchToScreenshot);

        mStateCallback.addCallback(STATE_SCREENSHOT_CAPTURED | STATE_GESTURE_COMPLETED
                        | STATE_SCALED_CONTROLLER_RECENTS,
                this::finishCurrentTransitionToRecents);

        mStateCallback.addCallback(STATE_SCREENSHOT_CAPTURED | STATE_GESTURE_COMPLETED
                        | STATE_SCALED_CONTROLLER_HOME,
                this::finishCurrentTransitionToHome);
        mStateCallback.addCallback(STATE_SCALED_CONTROLLER_HOME | STATE_CURRENT_TASK_FINISHED,
                this::reset);

        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_APP_CONTROLLER_RECEIVED
                        | STATE_LAUNCHER_DRAWN | STATE_SCALED_CONTROLLER_RECENTS
                        | STATE_CURRENT_TASK_FINISHED | STATE_GESTURE_COMPLETED
                        | STATE_GESTURE_STARTED,
                this::setupLauncherUiAfterSwipeUpToRecentsAnimation);

        mStateCallback.addCallback(STATE_HANDLER_INVALIDATED, this::invalidateHandler);
        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_HANDLER_INVALIDATED,
                this::invalidateHandlerWithLauncher);
        mStateCallback.addCallback(STATE_HANDLER_INVALIDATED | STATE_RESUME_LAST_TASK,
                this::notifyTransitionCancelled);

        mStateCallback.addCallback(STATE_APP_CONTROLLER_RECEIVED | STATE_GESTURE_STARTED,
                mRecentsAnimationWrapper::enableInputConsumer);

        if (!ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            mStateCallback.addChangeHandler(STATE_APP_CONTROLLER_RECEIVED | STATE_LAUNCHER_PRESENT
                            | STATE_SCREENSHOT_VIEW_SHOWN | STATE_CAPTURE_SCREENSHOT,
                    (b) -> mRecentsView.setRunningTaskHidden(!b));
        }
    }

    @Override
    protected boolean onActivityInit(final T activity, Boolean alreadyOnHome) {
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
        linkRecentsViewScroll();
        addLiveTileOverlay();

        mStateCallback.setState(STATE_LAUNCHER_PRESENT);
        if (alreadyOnHome) {
            onLauncherStart(activity);
        } else {
            activity.setOnStartCallback(this::onLauncherStart);
        }

        setupRecentsViewUi();
        return true;
    }

    @Override
    protected boolean moveWindowWithRecentsScroll() {
        return mGestureEndTarget != HOME;
    }

    private void onLauncherStart(final T activity) {
        if (mActivity != activity) {
            return;
        }
        if (mStateCallback.hasStates(STATE_HANDLER_INVALIDATED)) {
            return;
        }

        // If we've already ended the gesture and are going home, don't prepare recents UI,
        // as that will set the state as BACKGROUND_APP, overriding the animation to NORMAL.
        if (mGestureEndTarget != HOME) {
            Runnable initAnimFactory = () -> {
                mAnimationFactory = mActivityControlHelper.prepareRecentsUI(mActivity,
                        mWasLauncherAlreadyVisible, true,
                        this::onAnimatorPlaybackControllerCreated);
                maybeUpdateRecentsAttachedState(false /* animate */);
            };
            if (mWasLauncherAlreadyVisible) {
                // Launcher is visible, but might be about to stop. Thus, if we prepare recents
                // now, it might get overridden by moveToRestState() in onStop(). To avoid this,
                // wait until the next gesture (and possibly launcher) starts.
                mStateCallback.addCallback(STATE_GESTURE_STARTED, initAnimFactory);
            } else {
                initAnimFactory.run();
            }
        }
        AbstractFloatingView.closeAllOpenViewsExcept(activity, mWasLauncherAlreadyVisible,
                AbstractFloatingView.TYPE_LISTENER);

        if (mWasLauncherAlreadyVisible) {
            mStateCallback.setState(STATE_LAUNCHER_DRAWN);
        } else {
            TraceHelper.beginSection("WTS-init");
            View dragLayer = activity.getDragLayer();
            dragLayer.getViewTreeObserver().addOnDrawListener(new OnDrawListener() {

                @Override
                public void onDraw() {
                    TraceHelper.endSection("WTS-init", "Launcher frame is drawn");
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

        notifyGestureStartedAsync();
    }

    private void setupRecentsViewUi() {
        if (mContinuingLastGesture) {
            updateSysUiFlags(mCurrentShift.value);
            return;
        }
        mRecentsView.onGestureAnimationStart(mRunningTaskId);
    }

    private void launcherFrameDrawn() {
        mLauncherFrameDrawnTime = SystemClock.uptimeMillis();
    }

    private void sendRemoteAnimationsToAnimationFactory() {
        mAnimationFactory.onRemoteAnimationReceived(mRecentsAnimationWrapper.targetSet);
    }

    private void initializeLauncherAnimationController() {
        buildAnimationController();

        if (LatencyTrackerCompat.isEnabled(mContext)) {
            LatencyTrackerCompat.logToggleRecents((int) (mLauncherFrameDrawnTime - mTouchTimeMs));
        }

        // This method is only called when STATE_GESTURE_STARTED is set, so we can enable the
        // high-res thumbnail loader here once we are sure that we will end up in an overview state
        RecentsModel.INSTANCE.get(mContext).getThumbnailCache()
                .getHighResLoadingState().setVisible(true);
    }

    private float getTaskCurveScaleForOffsetX(float offsetX, float taskWidth) {
        float distanceToReachEdge = mDp.widthPx / 2 + taskWidth / 2 +
                mContext.getResources().getDimensionPixelSize(R.dimen.recents_page_spacing);
        float interpolation = Math.min(1, offsetX / distanceToReachEdge);
        return TaskView.getCurveScaleForInterpolation(interpolation);
    }

    @Override
    public void onMotionPauseChanged(boolean isPaused) {
        setShelfState(isPaused ? PEEK : HIDE, OVERSHOOT_1_2, SHELF_ANIM_DURATION);
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
        if (mMode != Mode.NO_BUTTON || mRecentsView == null) {
            return;
        }
        RemoteAnimationTargetCompat runningTaskTarget = mRecentsAnimationWrapper.targetSet == null
                ? null
                : mRecentsAnimationWrapper.targetSet.findTask(mRunningTaskId);
        final boolean recentsAttachedToAppWindow;
        int runningTaskIndex = mRecentsView.getRunningTaskIndex();
        if (mGestureEndTarget != null) {
            recentsAttachedToAppWindow = mGestureEndTarget.recentsAttachedToAppWindow;
        } else if (mContinuingLastGesture
                && mRecentsView.getRunningTaskIndex() != mRecentsView.getNextPage()) {
            recentsAttachedToAppWindow = true;
            animate = false;
        } else if (runningTaskTarget != null && isNotInRecents(runningTaskTarget)) {
            // The window is going away so make sure recents is always visible in this case.
            recentsAttachedToAppWindow = true;
            animate = false;
        } else {
            recentsAttachedToAppWindow = mIsShelfPeeking || mIsLikelyToStartNewTask;
            if (animate) {
                // Only animate if an adjacent task view is visible on screen.
                TaskView adjacentTask1 = mRecentsView.getTaskViewAt(runningTaskIndex + 1);
                TaskView adjacentTask2 = mRecentsView.getTaskViewAt(runningTaskIndex - 1);
                float prevTranslationX = mRecentsView.getTranslationX();
                mRecentsView.setTranslationX(0);
                animate = (adjacentTask1 != null && adjacentTask1.getGlobalVisibleRect(TEMP_RECT))
                        || (adjacentTask2 != null && adjacentTask2.getGlobalVisibleRect(TEMP_RECT));
                mRecentsView.setTranslationX(prevTranslationX);
            }
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
        if (mGestureEndTarget == HOME || mHasLauncherTransitionControllerStarted) {
            // We don't want a new mLauncherTransitionController if mGestureEndTarget == HOME (it
            // has its own animation) or if we're already animating the current controller.
            return;
        }
        initTransitionEndpoints(mActivity.getDeviceProfile());
        mAnimationFactory.createActivityController(mTransitionDragLength);
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
        mAnimationFactory.adjustActivityControllerInterpolators();
        mLauncherTransitionController.dispatchOnStart();
        updateLauncherTransitionProgress();
    }

    @Override
    public Intent getLaunchIntent() {
        return mOverviewComponentObserver.getOverviewIntent();
    }

    @Override
    public void updateFinalShift() {

        SwipeAnimationTargetSet controller = mRecentsAnimationWrapper.getController();
        if (controller != null) {
            applyTransformUnchecked();
            updateSysUiFlags(mCurrentShift.value);
        }

        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            if (mRecentsAnimationWrapper.getController() != null) {
                mLiveTileOverlay.update(mClipAnimationHelper.getCurrentRectWithInsets(),
                        mClipAnimationHelper.getCurrentCornerRadius());
            }
        }

        final boolean passed = mCurrentShift.value >= MIN_PROGRESS_FOR_OVERVIEW;
        if (passed != mPassedOverviewThreshold) {
            mPassedOverviewThreshold = passed;
            if (mMode != Mode.NO_BUTTON) {
                performHapticFeedback();
            }
        }

        if (mLauncherTransitionController == null || mLauncherTransitionController
                .getAnimationPlayer().isStarted()) {
            return;
        }
        updateLauncherTransitionProgress();
    }

    private void updateLauncherTransitionProgress() {
        if (mGestureEndTarget == HOME) {
            return;
        }
        // Normalize the progress to 0 to 1, as the animation controller will clamp it to that
        // anyway. The controller mimics the drag length factor by applying it to its interpolators.
        float progress = mCurrentShift.value / mDragLengthFactor;
        mLauncherTransitionController.setPlayFraction(
                progress <= mShiftAtGestureStart || mShiftAtGestureStart >= 1
                        ? 0 : (progress - mShiftAtGestureStart) / (1 - mShiftAtGestureStart));
    }

    /**
     * @param windowProgress 0 == app, 1 == overview
     */
    private void updateSysUiFlags(float windowProgress) {
        if (mRecentsView != null) {
            TaskView centermostTask = mRecentsView.getTaskViewAt(mRecentsView
                    .getPageNearestToCenterOfScreen());
            int centermostTaskFlags = centermostTask == null ? 0
                    : centermostTask.getThumbnail().getSysUiStatusNavFlags();
            boolean useHomeScreenFlags = windowProgress > 1 - UPDATE_SYSUI_FLAGS_THRESHOLD;
            // We will handle the sysui flags based on the centermost task view.
            mRecentsAnimationWrapper.setWindowThresholdCrossed(centermostTaskFlags != 0
                    || useHomeScreenFlags);
            int sysuiFlags = useHomeScreenFlags ? 0 : centermostTaskFlags;
            mActivity.getSystemUiController().updateUiState(UI_STATE_OVERVIEW, sysuiFlags);
        }
    }

    @Override
    public void onRecentsAnimationStart(SwipeAnimationTargetSet targetSet) {
        super.onRecentsAnimationStart(targetSet);
        TOUCH_INTERACTION_LOG.addLog("startRecentsAnimationCallback", targetSet.apps.length);
        setStateOnUiThread(STATE_APP_CONTROLLER_RECEIVED);

        mPassedOverviewThreshold = false;
    }

    @Override
    public void onRecentsAnimationCanceled() {
        mRecentsAnimationWrapper.setController(null);
        mActivityInitListener.unregister();
        setStateOnUiThread(STATE_GESTURE_CANCELLED | STATE_HANDLER_INVALIDATED);
        TOUCH_INTERACTION_LOG.addLog("cancelRecentsAnimation");
    }

    @Override
    public void onGestureStarted() {
        notifyGestureStartedAsync();
        mShiftAtGestureStart = mCurrentShift.value;
        setStateOnUiThread(STATE_GESTURE_STARTED);
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
        setStateOnUiThread(STATE_GESTURE_COMPLETED);
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
        setStateOnUiThread(STATE_GESTURE_COMPLETED);

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
        endRunningWindowAnim(mGestureEndTarget == HOME /* cancel */);
        endLauncherTransitionController();
        if (!ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            // Hide the task view, if not already hidden
            setTargetAlphaProvider(WindowTransformSwipeHandler::getHiddenTargetAlpha);
        }

        BaseDraggingActivity activity = mActivityControlHelper.getCreatedActivity();
        return activity == null
                ? InputConsumer.NO_OP : new OverviewInputConsumer(activity, null, true);
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

    private GestureEndTarget calculateEndTarget(PointF velocity, float endVelocity, boolean isFling,
            boolean isCancel) {
        final GestureEndTarget endTarget;
        final boolean goingToNewTask;
        if (mRecentsView != null) {
            if (!mRecentsAnimationWrapper.hasTargets()) {
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
            } else if (mMode == Mode.NO_BUTTON) {
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

            if (mMode == Mode.NO_BUTTON && isSwipeUp && !willGoToNewTaskOnSwipeUp) {
                endTarget = HOME;
            } else if (mMode == Mode.NO_BUTTON && isSwipeUp && !mIsShelfPeeking) {
                // If swiping at a diagonal, base end target on the faster velocity.
                endTarget = NEW_TASK;
            } else if (isSwipeUp) {
                endTarget = !reachedOverviewThreshold && willGoToNewTaskOnSwipeUp
                        ? NEW_TASK : RECENTS;
            } else {
                endTarget = goingToNewTask ? NEW_TASK : LAST_TASK;
            }
        }

        int stateFlags = OverviewInteractionState.INSTANCE.get(mActivity).getSystemUiStateFlags();
        if ((stateFlags & SYSUI_STATE_OVERVIEW_DISABLED) != 0
                && (endTarget == RECENTS || endTarget == LAST_TASK)) {
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
        float endShift = endTarget.endShift;
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
                if (endTarget == RECENTS && mMode != Mode.NO_BUTTON) {
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

        if (endTarget.isLauncher) {
            mRecentsAnimationWrapper.enableInputProxy();
        }

        if (endTarget == HOME) {
            setShelfState(ShelfAnimState.CANCEL, LINEAR, 0);
            duration = Math.max(MIN_OVERSHOOT_DURATION, duration);
        } else if (endTarget == RECENTS) {
            mLiveTileOverlay.startIconAnimation();
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
            if (mMode == Mode.NO_BUTTON) {
                setShelfState(ShelfAnimState.OVERVIEW, interpolator, duration);
            }
        } else if (endTarget == NEW_TASK || endTarget == LAST_TASK) {
            // Let RecentsView handle the scrolling to the task, which we launch in startNewTask()
            // or resumeLastTask().
            if (mRecentsView != null) {
                duration = Math.max(duration, mRecentsView.getScroller().getDuration());
            }
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
        mRecentsAnimationWrapper.runOnInit(() -> animateToProgressInternal(start, end, duration,
                interpolator, target, velocityPxPerMs));
    }

    @UiThread
    private void animateToProgressInternal(float start, float end, long duration,
            Interpolator interpolator, GestureEndTarget target, PointF velocityPxPerMs) {
        mGestureEndTarget = target;

        maybeUpdateRecentsAttachedState();

        if (mGestureEndTarget == HOME) {
            HomeAnimationFactory homeAnimFactory;
            if (mActivity != null) {
                homeAnimFactory = mActivityControlHelper.prepareHomeUI(mActivity);
            } else {
                homeAnimFactory = new HomeAnimationFactory() {
                    @NonNull
                    @Override
                    public RectF getWindowTargetRect() {
                        RectF fallbackTarget = new RectF(mClipAnimationHelper.getTargetRect());
                        Utilities.scaleRectFAboutCenter(fallbackTarget, 0.25f);
                        return fallbackTarget;
                    }

                    @NonNull
                    @Override
                    public AnimatorPlaybackController createActivityAnimationToHome() {
                        return AnimatorPlaybackController.wrap(new AnimatorSet(), duration);
                    }
                };
                mStateCallback.addChangeHandler(STATE_LAUNCHER_PRESENT | STATE_HANDLER_INVALIDATED,
                        isPresent -> mRecentsView.startHome());
            }
            RectFSpringAnim windowAnim = createWindowAnimationToHome(start, homeAnimFactory);
            windowAnim.addAnimatorListener(new AnimationSuccessListener() {
                @Override
                public void onAnimationSuccess(Animator animator) {
                    setStateOnUiThread(target.endState);
                }
            });
            windowAnim.start(velocityPxPerMs);
            homeAnimFactory.playAtomicAnimation(velocityPxPerMs.y);
            mRunningWindowAnim = RunningWindowAnim.wrap(windowAnim);
            mLauncherTransitionController = null;
        } else {
            ValueAnimator windowAnim = mCurrentShift.animateToValue(start, end);
            windowAnim.setDuration(duration).setInterpolator(interpolator);
            windowAnim.addUpdateListener(valueAnimator -> {
                if (mRecentsView != null && mRecentsView.getVisibility() != View.VISIBLE) {
                    // Views typically don't compute scroll when invisible as an optimization,
                    // but in our case we need to since the window offset depends on the scroll.
                    mRecentsView.computeScroll();
                }
            });
            windowAnim.addListener(new AnimationSuccessListener() {
                @Override
                public void onAnimationSuccess(Animator animator) {
                    if (target == NEW_TASK && mRecentsView != null
                            && mRecentsView.getNextPage() == mRecentsView.getRunningTaskIndex()) {
                        // We are about to launch the current running task, so use LAST_TASK state
                        // instead of NEW_TASK. This could happen, for example, if our scroll is
                        // aborted after we determined the target to be NEW_TASK.
                        setStateOnUiThread(LAST_TASK.endState);
                    } else {
                        setStateOnUiThread(target.endState);
                    }
                }
            });
            windowAnim.start();
            mRunningWindowAnim = RunningWindowAnim.wrap(windowAnim);
        }
        // Always play the entire launcher animation when going home, since it is separate from
        // the animation that has been controlled thus far.
        if (mGestureEndTarget == HOME) {
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
            mAnimationFactory.adjustActivityControllerInterpolators();
        }
        mLauncherTransitionController.getAnimationPlayer().setDuration(Math.max(0, duration));

        if (QUICKSTEP_SPRINGS.get()) {
            mLauncherTransitionController.dispatchOnStartWithVelocity(end, velocityPxPerMs.y);
        }
        mLauncherTransitionController.getAnimationPlayer().start();
        mHasLauncherTransitionControllerStarted = true;
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
        anim.addOnUpdateListener((r, p) -> updateSysUiFlags(Math.max(p, mCurrentShift.value)));
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
                mActivityControlHelper.onSwipeUpToHomeComplete(mActivity);
            }
        });
        return anim;
    }

    @Override
    public void onConsumerAboutToBeSwitched(SwipeSharedState sharedState) {
        if (mGestureEndTarget != null) {
            sharedState.canGestureBeContinued = mGestureEndTarget.canBeContinued;
            sharedState.goingToLauncher = mGestureEndTarget.isLauncher;
        }

        if (sharedState.canGestureBeContinued) {
            cancelCurrentAnimation(sharedState);
        } else {
            reset();
        }
    }

    @UiThread
    private void resumeLastTask() {
        mRecentsAnimationWrapper.finish(false /* toRecents */, null);
        TOUCH_INTERACTION_LOG.addLog("finishRecentsAnimation", false);
        doLogGesture(LAST_TASK);
        reset();
    }

    @UiThread
    private void startNewTask() {
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
        setStateOnUiThread(STATE_HANDLER_INVALIDATED);
    }

    /**
     * Cancels any running animation so that the active target can be overriden by a new swipe
     * handle (in case of quick switch).
     */
    private void cancelCurrentAnimation(SwipeSharedState sharedState) {
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
            sharedState.setRecentsAnimationFinishInterrupted(newRunningTaskId);
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

        mActivity.getRootView().setOnApplyWindowInsetsListener(null);
        removeLiveTileOverlay();
    }

    private void endLauncherTransitionController() {
        setShelfState(ShelfAnimState.CANCEL, LINEAR, 0);
        if (mLauncherTransitionController != null) {
            mLauncherTransitionController.getAnimationPlayer().end();
            mLauncherTransitionController = null;
        }
    }

    private void notifyTransitionCancelled() {
        mAnimationFactory.onTransitionCancelled();
    }

    private void resetStateForAnimationCancel() {
        boolean wasVisible = mWasLauncherAlreadyVisible || mGestureStarted;
        mActivityControlHelper.onTransitionCancelled(mActivity, wasVisible);

        // Leave the pending invisible flag, as it may be used by wallpaper open animation.
        mActivity.clearForceInvisibleFlag(INVISIBLE_BY_STATE_HANDLER);
    }

    private void switchToScreenshot() {
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            setStateOnUiThread(STATE_SCREENSHOT_CAPTURED);
        } else if (!mRecentsAnimationWrapper.hasTargets()) {
            // If there are no targets, then we don't need to capture anything
            setStateOnUiThread(STATE_SCREENSHOT_CAPTURED);
        } else {
            boolean finishTransitionPosted = false;
            SwipeAnimationTargetSet controller = mRecentsAnimationWrapper.getController();
            if (controller != null) {
                // Update the screenshot of the task
                if (mTaskSnapshot == null) {
                    mTaskSnapshot = controller.screenshotTask(mRunningTaskId);
                }
                final TaskView taskView;
                if (mGestureEndTarget == HOME) {
                    // Capture the screenshot before finishing the transition to home to ensure it's
                    // taken in the correct orientation, but no need to update the thumbnail.
                    taskView = null;
                } else {
                    taskView = mRecentsView.updateThumbnail(mRunningTaskId, mTaskSnapshot);
                }
                if (taskView != null && !mCanceled) {
                    // Defer finishing the animation until the next launcher frame with the
                    // new thumbnail
                    finishTransitionPosted = new WindowCallbacksCompat(taskView) {

                        // The number of frames to defer until we actually finish the animation
                        private int mDeferFrameCount = 2;

                        @Override
                        public void onPostDraw(Canvas canvas) {
                            // If we were cancelled after this was attached, do not update
                            // the state.
                            if (mCanceled) {
                                detach();
                                return;
                            }

                            if (mDeferFrameCount > 0) {
                                mDeferFrameCount--;
                                // Workaround, detach and reattach to invalidate the root node for
                                // another draw
                                detach();
                                attach();
                                taskView.invalidate();
                                return;
                            }

                            setStateOnUiThread(STATE_SCREENSHOT_CAPTURED);
                            detach();
                        }
                    }.attach();
                }
            }
            if (!finishTransitionPosted) {
                // If we haven't posted a draw callback, set the state immediately.
                RaceConditionTracker.onEvent(SCREENSHOT_CAPTURED_EVT, ENTER);
                setStateOnUiThread(STATE_SCREENSHOT_CAPTURED);
                RaceConditionTracker.onEvent(SCREENSHOT_CAPTURED_EVT, EXIT);
            }
        }
    }

    private void finishCurrentTransitionToRecents() {
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            setStateOnUiThread(STATE_CURRENT_TASK_FINISHED);
        } else if (!mRecentsAnimationWrapper.hasTargets()) {
            // If there are no targets, then there is nothing to finish
            setStateOnUiThread(STATE_CURRENT_TASK_FINISHED);
        } else {
            synchronized (mRecentsAnimationWrapper) {
                mRecentsAnimationWrapper.finish(true /* toRecents */,
                        () -> setStateOnUiThread(STATE_CURRENT_TASK_FINISHED));
            }
        }
        TOUCH_INTERACTION_LOG.addLog("finishRecentsAnimation", true);
    }

    private void finishCurrentTransitionToHome() {
        synchronized (mRecentsAnimationWrapper) {
            mRecentsAnimationWrapper.finish(true /* toRecents */,
                    () -> setStateOnUiThread(STATE_CURRENT_TASK_FINISHED),
                    true /* sendUserLeaveHint */);
        }
        TOUCH_INTERACTION_LOG.addLog("finishRecentsAnimation", true);
        doLogGesture(HOME);
    }

    private void setupLauncherUiAfterSwipeUpToRecentsAnimation() {
        endLauncherTransitionController();
        mActivityControlHelper.onSwipeUpToRecentsComplete(mActivity);
        mRecentsAnimationWrapper.setDeferCancelUntilNextTransition(true /* defer */,
                true /* screenshot */);
        mRecentsView.onSwipeUpAnimationSuccess();

        RecentsModel.INSTANCE.get(mContext).onOverviewShown(false, TAG);

        doLogGesture(RECENTS);
        reset();
    }

    private void setTargetAlphaProvider(TargetAlphaProvider provider) {
        mClipAnimationHelper.setTaskAlphaCallback(provider);
        updateFinalShift();
    }

    private synchronized void addLiveTileOverlay() {
        if (!mLiveTileOverlayAttached) {
            mActivity.getRootView().getOverlay().add(mLiveTileOverlay);
            mRecentsView.setLiveTileOverlay(mLiveTileOverlay);
            mLiveTileOverlayAttached = true;
        }
    }

    private synchronized void removeLiveTileOverlay() {
        if (mLiveTileOverlayAttached) {
            mActivity.getRootView().getOverlay().remove(mLiveTileOverlay);
            mRecentsView.setLiveTileOverlay(null);
            mLiveTileOverlayAttached = false;
        }
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
