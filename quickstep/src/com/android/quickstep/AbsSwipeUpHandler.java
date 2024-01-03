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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;
import static android.widget.Toast.LENGTH_SHORT;

import static com.android.app.animation.Interpolators.ACCELERATE_DECELERATE;
import static com.android.app.animation.Interpolators.DECELERATE;
import static com.android.app.animation.Interpolators.OVERSHOOT_1_2;
import static com.android.launcher3.BaseActivity.EVENT_DESTROYED;
import static com.android.launcher3.BaseActivity.EVENT_STARTED;
import static com.android.launcher3.BaseActivity.INVISIBLE_BY_STATE_HANDLER;
import static com.android.launcher3.BaseActivity.STATE_HANDLER_INVISIBILITY_FLAGS;
import static com.android.launcher3.LauncherPrefs.ALL_APPS_OVERVIEW_THRESHOLD;
import static com.android.launcher3.PagedView.INVALID_PAGE;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_BACKGROUND;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.IGNORE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOME_GESTURE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_OVERVIEW_GESTURE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_QUICKSWITCH_LEFT;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_QUICKSWITCH_RIGHT;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.SystemUiController.UI_STATE_FULLSCREEN_TASK;
import static com.android.launcher3.util.VibratorWrapper.OVERVIEW_HAPTIC;
import static com.android.launcher3.util.window.RefreshRateTracker.getSingleFrameMs;
import static com.android.quickstep.GestureState.GestureEndTarget.ALL_APPS;
import static com.android.quickstep.GestureState.GestureEndTarget.HOME;
import static com.android.quickstep.GestureState.GestureEndTarget.LAST_TASK;
import static com.android.quickstep.GestureState.GestureEndTarget.NEW_TASK;
import static com.android.quickstep.GestureState.GestureEndTarget.RECENTS;
import static com.android.quickstep.GestureState.STATE_END_TARGET_ANIMATION_FINISHED;
import static com.android.quickstep.GestureState.STATE_END_TARGET_SET;
import static com.android.quickstep.GestureState.STATE_RECENTS_ANIMATION_CANCELED;
import static com.android.quickstep.GestureState.STATE_RECENTS_SCROLLING_FINISHED;
import static com.android.quickstep.MultiStateCallback.DEBUG_STATES;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.CANCEL_RECENTS_ANIMATION;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.EXPECTING_TASK_APPEARED;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.INVALID_VELOCITY_ON_SWIPE_UP;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.LAUNCHER_DESTROYED;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.ON_SETTLED_ON_END_TARGET;
import static com.android.quickstep.views.DesktopTaskView.isDesktopModeSupported;
import static com.android.quickstep.views.RecentsView.UPDATE_SYSUI_FLAGS_THRESHOLD;
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_RECENTS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.TaskInfo;
import android.app.WindowConfiguration;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.View;
import android.view.View.OnApplyWindowInsetsListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnDrawListener;
import android.view.ViewTreeObserver.OnScrollChangedListener;
import android.view.WindowInsets;
import android.view.animation.Interpolator;
import android.widget.Toast;
import android.window.PictureInPictureSurfaceTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.internal.util.LatencyTracker;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.logging.StatsLogManager.StatsLogger;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.taskbar.TaskbarThresholdUtils;
import com.android.launcher3.taskbar.TaskbarUIController;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.util.VibratorWrapper;
import com.android.launcher3.util.WindowBounds;
import com.android.quickstep.BaseActivityInterface.AnimationFactory;
import com.android.quickstep.GestureState.GestureEndTarget;
import com.android.quickstep.RemoteTargetGluer.RemoteTargetHandle;
import com.android.quickstep.util.ActiveGestureErrorDetector;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.util.ActivityInitListener;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.util.InputConsumerProxy;
import com.android.quickstep.util.InputProxyHandlerFactory;
import com.android.quickstep.util.MotionPauseDetector;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.StaggeredWorkspaceAnim;
import com.android.quickstep.util.SurfaceTransaction;
import com.android.quickstep.util.SurfaceTransactionApplier;
import com.android.quickstep.util.SwipePipToHomeAnimator;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.quickstep.views.TaskView.TaskIdAttributeContainer;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;
import com.android.systemui.shared.system.SysUiStatsLog;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;
import com.android.wm.shell.common.TransactionPool;
import com.android.wm.shell.startingsurface.SplashScreenExitAnimationUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * Handles the navigation gestures when Launcher is the default home activity.
 */
@TargetApi(Build.VERSION_CODES.R)
public abstract class AbsSwipeUpHandler<T extends StatefulActivity<S>,
        Q extends RecentsView, S extends BaseState<S>>
        extends SwipeUpAnimationLogic implements OnApplyWindowInsetsListener,
        RecentsAnimationCallbacks.RecentsAnimationListener {
    private static final String TAG = "AbsSwipeUpHandler";

    private static final ArrayList<String> STATE_NAMES = new ArrayList<>();

    protected final BaseActivityInterface<S, T> mActivityInterface;
    protected final InputConsumerProxy mInputConsumerProxy;
    protected final ActivityInitListener mActivityInitListener;
    // Callbacks to be made once the recents animation starts
    private final ArrayList<Runnable> mRecentsAnimationStartCallbacks = new ArrayList<>();
    private final OnScrollChangedListener mOnRecentsScrollListener = this::onRecentsViewScroll;

    // Null if the recents animation hasn't started yet or has been canceled or finished.
    protected @Nullable RecentsAnimationController mRecentsAnimationController;
    protected @Nullable RecentsAnimationController mDeferredCleanupRecentsAnimationController;
    protected RecentsAnimationTargets mRecentsAnimationTargets;
    protected @Nullable T mActivity;
    protected @Nullable Q mRecentsView;
    protected Runnable mGestureEndCallback;
    protected MultiStateCallback mStateCallback;
    protected boolean mCanceled;
    private boolean mRecentsViewScrollLinked = false;

    private final Runnable mLauncherOnDestroyCallback = () -> {
        ActiveGestureLog.INSTANCE.addLog("Launcher destroyed", LAUNCHER_DESTROYED);
        mRecentsView = null;
        mActivity = null;
        mStateCallback.clearState(STATE_LAUNCHER_PRESENT);
    };

    private static int FLAG_COUNT = 0;
    private static int getNextStateFlag(String name) {
        if (DEBUG_STATES) {
            STATE_NAMES.add(name);
        }
        int index = 1 << FLAG_COUNT;
        FLAG_COUNT++;
        return index;
    }

    // Launcher UI related states
    protected static final int STATE_LAUNCHER_PRESENT =
            getNextStateFlag("STATE_LAUNCHER_PRESENT");
    protected static final int STATE_LAUNCHER_STARTED =
            getNextStateFlag("STATE_LAUNCHER_STARTED");
    protected static final int STATE_LAUNCHER_DRAWN =
            getNextStateFlag("STATE_LAUNCHER_DRAWN");
    // Called when the Launcher has connected to the touch interaction service (and the taskbar
    // ui controller is initialized)
    protected static final int STATE_LAUNCHER_BIND_TO_SERVICE =
            getNextStateFlag("STATE_LAUNCHER_BIND_TO_SERVICE");

    // Internal initialization states
    private static final int STATE_APP_CONTROLLER_RECEIVED =
            getNextStateFlag("STATE_APP_CONTROLLER_RECEIVED");

    // Interaction finish states
    private static final int STATE_SCALED_CONTROLLER_HOME =
            getNextStateFlag("STATE_SCALED_CONTROLLER_HOME");
    private static final int STATE_SCALED_CONTROLLER_RECENTS =
            getNextStateFlag("STATE_SCALED_CONTROLLER_RECENTS");

    protected static final int STATE_HANDLER_INVALIDATED =
            getNextStateFlag("STATE_HANDLER_INVALIDATED");
    private static final int STATE_GESTURE_STARTED =
            getNextStateFlag("STATE_GESTURE_STARTED");
    private static final int STATE_GESTURE_CANCELLED =
            getNextStateFlag("STATE_GESTURE_CANCELLED");
    private static final int STATE_GESTURE_COMPLETED =
            getNextStateFlag("STATE_GESTURE_COMPLETED");

    private static final int STATE_CAPTURE_SCREENSHOT =
            getNextStateFlag("STATE_CAPTURE_SCREENSHOT");
    protected static final int STATE_SCREENSHOT_CAPTURED =
            getNextStateFlag("STATE_SCREENSHOT_CAPTURED");
    private static final int STATE_SCREENSHOT_VIEW_SHOWN =
            getNextStateFlag("STATE_SCREENSHOT_VIEW_SHOWN");

    private static final int STATE_RESUME_LAST_TASK =
            getNextStateFlag("STATE_RESUME_LAST_TASK");
    private static final int STATE_START_NEW_TASK =
            getNextStateFlag("STATE_START_NEW_TASK");
    private static final int STATE_CURRENT_TASK_FINISHED =
            getNextStateFlag("STATE_CURRENT_TASK_FINISHED");
    private static final int STATE_FINISH_WITH_NO_END =
            getNextStateFlag("STATE_FINISH_WITH_NO_END");
    private static final int STATE_SETTLED_ON_ALL_APPS =
            getNextStateFlag("STATE_SETTLED_ON_ALL_APPS");

    private static final int LAUNCHER_UI_STATES =
            STATE_LAUNCHER_PRESENT | STATE_LAUNCHER_DRAWN | STATE_LAUNCHER_STARTED |
                    STATE_LAUNCHER_BIND_TO_SERVICE;

    public static final long MAX_SWIPE_DURATION = 350;

    public static final float MIN_PROGRESS_FOR_OVERVIEW = 0.7f;
    private static final float SWIPE_DURATION_MULTIPLIER =
            Math.min(1 / MIN_PROGRESS_FOR_OVERVIEW, 1 / (1 - MIN_PROGRESS_FOR_OVERVIEW));
    private static final String SCREENSHOT_CAPTURED_EVT = "ScreenshotCaptured";

    public static final long RECENTS_ATTACH_DURATION = 300;

    private static final float MAX_QUICK_SWITCH_RECENTS_SCALE_PROGRESS = 0.07f;

    // Controls task thumbnail splash's reveal animation after landing on a task from quickswitch.
    // These values match WindowManager/Shell starting_window_app_reveal_* config values.
    private static final int SPLASH_FADE_OUT_DURATION = 133;
    private static final int SPLASH_APP_REVEAL_DELAY = 83;
    private static final int SPLASH_APP_REVEAL_DURATION = 266;
    private static final int SPLASH_ANIMATION_DURATION = 349;

    /**
     * Used as the page index for logging when we return to the last task at the end of the gesture.
     */
    private static final int LOG_NO_OP_PAGE_INDEX = -1;

    protected final TaskAnimationManager mTaskAnimationManager;

    // Either RectFSpringAnim (if animating home) or ObjectAnimator (from mCurrentShift) otherwise
    private RunningWindowAnim[] mRunningWindowAnim;
    // Possible second animation running at the same time as mRunningWindowAnim
    private Animator mParallelRunningAnim;
    private boolean mIsMotionPaused;
    private boolean mHasMotionEverBeenPaused;

    private boolean mContinuingLastGesture;

    // Cache of recently-updated task snapshots, mapping task id to ThumbnailData
    private HashMap<Integer, ThumbnailData> mTaskSnapshotCache = new HashMap<>();

    // Used to control launcher components throughout the swipe gesture.
    private AnimatorControllerWithResistance mLauncherTransitionController;
    private boolean mHasEndedLauncherTransition;

    private AnimationFactory mAnimationFactory = (t) -> { };

    private boolean mWasLauncherAlreadyVisible;

    private boolean mGestureStarted;
    private boolean mLogDirectionUpOrLeft = true;
    private boolean mIsLikelyToStartNewTask;
    private boolean mIsInAllAppsRegion;

    private final long mTouchTimeMs;
    private long mLauncherFrameDrawnTime;

    private final int mSplashMainWindowShiftLength;

    private final Runnable mOnDeferredActivityLaunch = this::onDeferredActivityLaunch;
    private final Runnable mLauncherOnStartCallback = this::onLauncherStart;

    @Nullable private SwipePipToHomeAnimator mSwipePipToHomeAnimator;
    protected boolean mIsSwipingPipToHome;
    // TODO(b/195473090) no split PIP for now, remove once we have more clarity
    //  can try to have RectFSpringAnim evaluate multiple rects at once
    private final SwipePipToHomeAnimator[] mSwipePipToHomeAnimators =
            new SwipePipToHomeAnimator[2];

    // Interpolate RecentsView scale from start of quick switch scroll until this scroll threshold
    private final float mQuickSwitchScaleScrollThreshold;

    private final int mTaskbarAppWindowThreshold;
    private final int mTaskbarHomeOverviewThreshold;
    private final int mTaskbarCatchUpThreshold;
    private final boolean mTaskbarAlreadyOpen;
    private final boolean mIsTaskbarAllAppsOpen;
    private final boolean mIsTransientTaskbar;
    // May be set to false when mIsTransientTaskbar is true.
    private boolean mCanSlowSwipeGoHome = true;
    // Indicates whether the divider is shown, only used when split screen is activated.
    private boolean mIsDividerShown = true;
    private boolean mStartMovingTasks;

    @Nullable
    private RemoteAnimationTargets.ReleaseCheck mSwipePipToHomeReleaseCheck = null;

    public AbsSwipeUpHandler(Context context, RecentsAnimationDeviceState deviceState,
            TaskAnimationManager taskAnimationManager, GestureState gestureState,
            long touchTimeMs, boolean continuingLastGesture,
            InputConsumerController inputConsumer) {
        super(context, deviceState, gestureState);
        mActivityInterface = gestureState.getActivityInterface();
        mActivityInitListener = mActivityInterface.createActivityInitListener(this::onActivityInit);
        mInputConsumerProxy =
                new InputConsumerProxy(context, /* rotationSupplier = */ () -> {
                    if (mRecentsView == null) {
                        return ROTATION_0;
                    }
                    return mRecentsView.getPagedViewOrientedState().getRecentsActivityRotation();
                }, inputConsumer, /* onTouchDownCallback = */ () -> {
                    endRunningWindowAnim(mGestureState.getEndTarget() == HOME /* cancel */);
                    endLauncherTransitionController();
                }, new InputProxyHandlerFactory(mActivityInterface, mGestureState));
        mTaskAnimationManager = taskAnimationManager;
        mTouchTimeMs = touchTimeMs;
        mContinuingLastGesture = continuingLastGesture;

        Resources res = context.getResources();
        mQuickSwitchScaleScrollThreshold = res
                .getDimension(R.dimen.quick_switch_scaling_scroll_threshold);

        mSplashMainWindowShiftLength = -res
                .getDimensionPixelSize(R.dimen.starting_surface_exit_animation_window_shift_length);

        initTransitionEndpoints(mRemoteTargetHandles[0].getTaskViewSimulator()
                .getOrientationState().getLauncherDeviceProfile());
        initStateCallbacks();

        mIsTransientTaskbar = mDp.isTaskbarPresent
                && DisplayController.isTransientTaskbar(mActivity);
        TaskbarUIController controller = mActivityInterface.getTaskbarController();
        mTaskbarAlreadyOpen = controller != null && !controller.isTaskbarStashed();
        mIsTaskbarAllAppsOpen = controller != null && controller.isTaskbarAllAppsOpen();
        mTaskbarAppWindowThreshold =
                TaskbarThresholdUtils.getAppWindowThreshold(res, mDp);
        boolean swipeWillNotShowTaskbar = mTaskbarAlreadyOpen || mGestureState.isTrackpadGesture();
        mTaskbarHomeOverviewThreshold = swipeWillNotShowTaskbar
                ? 0
                : TaskbarThresholdUtils.getHomeOverviewThreshold(res, mDp);
        mTaskbarCatchUpThreshold = TaskbarThresholdUtils.getCatchUpThreshold(res, mDp);
    }

    @Nullable
    private static ActiveGestureErrorDetector.GestureEvent getTrackedEventForState(int stateFlag) {
        if (stateFlag == STATE_GESTURE_STARTED) {
            return ActiveGestureErrorDetector.GestureEvent.STATE_GESTURE_STARTED;
        } else if (stateFlag == STATE_GESTURE_COMPLETED) {
            return ActiveGestureErrorDetector.GestureEvent.STATE_GESTURE_COMPLETED;
        } else if (stateFlag == STATE_GESTURE_CANCELLED) {
            return ActiveGestureErrorDetector.GestureEvent.STATE_GESTURE_CANCELLED;
        } else if (stateFlag == STATE_SCREENSHOT_CAPTURED) {
            return ActiveGestureErrorDetector.GestureEvent.STATE_SCREENSHOT_CAPTURED;
        } else if (stateFlag == STATE_CAPTURE_SCREENSHOT) {
            return ActiveGestureErrorDetector.GestureEvent.STATE_CAPTURE_SCREENSHOT;
        } else if (stateFlag == STATE_HANDLER_INVALIDATED) {
            return ActiveGestureErrorDetector.GestureEvent.STATE_HANDLER_INVALIDATED;
        } else if (stateFlag == STATE_LAUNCHER_DRAWN) {
            return ActiveGestureErrorDetector.GestureEvent.STATE_LAUNCHER_DRAWN;
        }
        return null;
    }

    private void initStateCallbacks() {
        mStateCallback = new MultiStateCallback(
                STATE_NAMES.toArray(new String[0]), AbsSwipeUpHandler::getTrackedEventForState);

        mStateCallback.runOnceAtState(STATE_LAUNCHER_PRESENT | STATE_GESTURE_STARTED,
                this::onLauncherPresentAndGestureStarted);

        mStateCallback.runOnceAtState(STATE_LAUNCHER_DRAWN | STATE_GESTURE_STARTED,
                this::initializeLauncherAnimationController);

        mStateCallback.runOnceAtState(STATE_LAUNCHER_PRESENT | STATE_LAUNCHER_DRAWN,
                this::launcherFrameDrawn);

        mStateCallback.runOnceAtState(STATE_LAUNCHER_PRESENT | STATE_LAUNCHER_STARTED
                        | STATE_GESTURE_CANCELLED,
                this::resetStateForAnimationCancel);

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
        mStateCallback.runOnceAtState(STATE_SETTLED_ON_ALL_APPS | STATE_SCREENSHOT_CAPTURED
                        | STATE_GESTURE_COMPLETED,
                this::finishCurrentTransitionToAllApps);

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

        mStateCallback.runOnceAtState(STATE_HANDLER_INVALIDATED, this::invalidateHandler);
        mStateCallback.runOnceAtState(STATE_LAUNCHER_PRESENT | STATE_HANDLER_INVALIDATED,
                this::invalidateHandlerWithLauncher);
        mStateCallback.runOnceAtState(STATE_HANDLER_INVALIDATED | STATE_RESUME_LAST_TASK,
                this::resetStateForAnimationCancel);
        mStateCallback.runOnceAtState(STATE_HANDLER_INVALIDATED | STATE_FINISH_WITH_NO_END,
                this::resetStateForAnimationCancel);
    }

    protected boolean onActivityInit(Boolean alreadyOnHome) {
        if (mStateCallback.hasStates(STATE_HANDLER_INVALIDATED)) {
            return false;
        }

        T createdActivity = mActivityInterface.getCreatedActivity();
        if (createdActivity != null) {
            initTransitionEndpoints(createdActivity.getDeviceProfile());
        }
        final T activity = mActivityInterface.getCreatedActivity();
        if (mActivity == activity) {
            return true;
        }

        if (mActivity != null) {
            if (mStateCallback.hasStates(STATE_GESTURE_COMPLETED)) {
                // If the activity has restarted between setting the page scroll settling callback
                // and actually receiving the callback, just mark the gesture completed
                mGestureState.setState(STATE_RECENTS_SCROLLING_FINISHED);
                return true;
            }
            resetLauncherListeners();

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

        mStateCallback.setState(STATE_LAUNCHER_PRESENT);
        if (alreadyOnHome) {
            onLauncherStart();
        } else {
            activity.addEventCallback(EVENT_STARTED, mLauncherOnStartCallback);
        }

        // Set up a entire animation lifecycle callback to notify the current recents view when
        // the animation is canceled
        mGestureState.runOnceAtState(STATE_RECENTS_ANIMATION_CANCELED, () -> {
            if (mRecentsView == null) return;

            HashMap<Integer, ThumbnailData> snapshots =
                    mGestureState.consumeRecentsAnimationCanceledSnapshot();
            if (snapshots != null) {
                mRecentsView.switchToScreenshot(snapshots, () -> {
                    if (mRecentsAnimationController != null) {
                        mRecentsAnimationController.cleanupScreenshot();
                    } else if (mDeferredCleanupRecentsAnimationController != null) {
                        mDeferredCleanupRecentsAnimationController.cleanupScreenshot();
                        mDeferredCleanupRecentsAnimationController = null;
                    }
                });
                mRecentsView.onRecentsAnimationComplete();
            }
        });

        setupRecentsViewUi();
        mRecentsView.runOnPageScrollsInitialized(this::linkRecentsViewScroll);
        mActivity.runOnBindToTouchInteractionService(this::onLauncherBindToService);
        mActivity.addEventCallback(EVENT_DESTROYED, mLauncherOnDestroyCallback);
        return true;
    }

    /**
     * Return true if the window should be translated horizontally if the recents view scrolls
     */
    protected boolean moveWindowWithRecentsScroll() {
        return mGestureState.getEndTarget() != HOME;
    }

    private void onLauncherStart() {
        final T activity = mActivityInterface.getCreatedActivity();
        if (activity == null || mActivity != activity) {
            return;
        }
        if (mStateCallback.hasStates(STATE_HANDLER_INVALIDATED)) {
            return;
        }
        // RecentsView never updates the display rotation until swipe-up, force update
        // RecentsOrientedState before passing to TaskViewSimulator.
        mRecentsView.updateRecentsRotation();
        runActionOnRemoteHandles(remoteTargetHandle -> remoteTargetHandle.getTaskViewSimulator()
                .setOrientationState(mRecentsView.getPagedViewOrientedState()));

        // If we've already ended the gesture and are going home, don't prepare recents UI,
        // as that will set the state as BACKGROUND_APP, overriding the animation to NORMAL.
        if (mGestureState.getEndTarget() != HOME) {
            Runnable initAnimFactory = () -> {
                mAnimationFactory = mActivityInterface.prepareRecentsUI(mDeviceState,
                        mWasLauncherAlreadyVisible, this::onAnimatorPlaybackControllerCreated);
                maybeUpdateRecentsAttachedState(false /* animate */);
                if (mGestureState.getEndTarget() != null) {
                    // Update the end target in case the gesture ended before we init.
                    mAnimationFactory.setEndTarget(mGestureState.getEndTarget());
                }
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
            SafeCloseable traceToken = TraceHelper.INSTANCE.beginAsyncSection("WTS-init");
            View dragLayer = activity.getDragLayer();
            dragLayer.getViewTreeObserver().addOnDrawListener(new OnDrawListener() {
                boolean mHandled = false;

                @Override
                public void onDraw() {
                    if (mHandled) {
                        return;
                    }
                    mHandled = true;

                    traceToken.close();
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

    private void onLauncherBindToService() {
        mStateCallback.setState(STATE_LAUNCHER_BIND_TO_SERVICE);
        flushOnRecentsAnimationAndLauncherBound();
    }

    private void onLauncherPresentAndGestureStarted() {
        // Re-setup the recents UI when gesture starts, as the state could have been changed during
        // that time by a previous window transition.
        setupRecentsViewUi();

        // For the duration of the gesture, in cases where an activity is launched while the
        // activity is not yet resumed, finish the animation to ensure we get resumed
        mGestureState.getActivityInterface().setOnDeferredActivityLaunchCallback(
                mOnDeferredActivityLaunch);

        mGestureState.runOnceAtState(STATE_END_TARGET_SET,
                () -> {
                    mDeviceState.getRotationTouchHelper()
                            .onEndTargetCalculated(mGestureState.getEndTarget(),
                                    mActivityInterface);
                });

        notifyGestureStarted();
    }

    private void onDeferredActivityLaunch() {
        mActivityInterface.switchRunningTaskViewToScreenshot(
                null, () -> {
                    mTaskAnimationManager.finishRunningRecentsAnimation(true /* toHome */);
                });
    }

    private void setupRecentsViewUi() {
        if (mContinuingLastGesture) {
            updateSysUiFlags(mCurrentShift.value);
            return;
        }
        notifyGestureAnimationStartToRecents();
    }

    protected void notifyGestureAnimationStartToRecents() {
        Task[] runningTasks;
        TopTaskTracker.CachedTaskInfo cachedTaskInfo = mGestureState.getRunningTask();
        if (mIsSwipeForSplit) {
            int[] splitTaskIds = TopTaskTracker.INSTANCE.get(mContext).getRunningSplitTaskIds();
            runningTasks = cachedTaskInfo.getPlaceholderTasks(splitTaskIds);
        } else {
            runningTasks = cachedTaskInfo.getPlaceholderTasks();
        }

        // Safeguard against any null tasks being sent to recents view, happens when quickswitching
        // very quickly w/ split tasks because TopTaskTracker provides stale information compared to
        // actual running tasks in the recents animation.
        // TODO(b/236226779), Proper fix (ag/22237143)
        if (Arrays.stream(runningTasks).anyMatch(Objects::isNull)) {
            return;
        }
        if (mRecentsView == null) {
            return;
        }
        mRecentsView.onGestureAnimationStart(runningTasks, mDeviceState.getRotationTouchHelper());
    }

    private void launcherFrameDrawn() {
        mLauncherFrameDrawnTime = SystemClock.uptimeMillis();
    }

    private void initializeLauncherAnimationController() {
        buildAnimationController();

        try (SafeCloseable c = TraceHelper.INSTANCE.allowIpcs("logToggleRecents")) {
            LatencyTracker.getInstance(mContext).logAction(LatencyTracker.ACTION_TOGGLE_RECENTS,
                    (int) (mLauncherFrameDrawnTime - mTouchTimeMs));
        }

        // This method is only called when STATE_GESTURE_STARTED is set, so we can enable the
        // high-res thumbnail loader here once we are sure that we will end up in an overview state
        RecentsModel.INSTANCE.get(mContext).getThumbnailCache()
                .getHighResLoadingState().setVisible(true);
    }

    public MotionPauseDetector.OnMotionPauseListener getMotionPauseListener() {
        return new MotionPauseDetector.OnMotionPauseListener() {
            @Override
            public void onMotionPauseDetected() {
                mHasMotionEverBeenPaused = true;
                maybeUpdateRecentsAttachedState(true/* animate */, true/* moveRunningTask */);
                Optional.ofNullable(mActivityInterface.getTaskbarController())
                        .ifPresent(TaskbarUIController::startTranslationSpring);
                if (!mIsInAllAppsRegion) {
                    performHapticFeedback();
                }
            }

            @Override
            public void onMotionPauseChanged(boolean isPaused) {
                mIsMotionPaused = isPaused;
            }
        };
    }

    private void maybeUpdateRecentsAttachedState() {
        maybeUpdateRecentsAttachedState(true /* animate */);
    }

    protected void maybeUpdateRecentsAttachedState(boolean animate) {
        maybeUpdateRecentsAttachedState(animate, false /* moveRunningTask */);
    }

    /**
     * Determines whether to show or hide RecentsView. The window is always
     * synchronized with its corresponding TaskView in RecentsView, so if
     * RecentsView is shown, it will appear to be attached to the window.
     *
     * Note this method has no effect unless the navigation mode is NO_BUTTON.
     * @param animate whether to animate when attaching RecentsView
     * @param moveRunningTask whether to move running task to front when attaching
     */
    private void maybeUpdateRecentsAttachedState(boolean animate, boolean moveRunningTask) {
        if ((!mDeviceState.isFullyGesturalNavMode() && !mGestureState.isTrackpadGesture())
                || mRecentsView == null) {
            return;
        }
        // looking at single target is fine here since either app of a split pair would
        // have their "isInRecents" field set? (that's what this is used for below)
        RemoteAnimationTarget runningTaskTarget = mRecentsAnimationTargets != null
                ? mRecentsAnimationTargets
                .findTask(mGestureState.getTopRunningTaskId())
                : null;
        final boolean recentsAttachedToAppWindow;
        if (mIsInAllAppsRegion) {
            recentsAttachedToAppWindow = false;
        } else if (mGestureState.getEndTarget() != null) {
            recentsAttachedToAppWindow = mGestureState.getEndTarget().recentsAttachedToAppWindow;
        } else if (mContinuingLastGesture
                && mRecentsView.getRunningTaskIndex() != mRecentsView.getNextPage()) {
            recentsAttachedToAppWindow = true;
        } else if (runningTaskTarget != null && isNotInRecents(runningTaskTarget)) {
            // The window is going away so make sure recents is always visible in this case.
            recentsAttachedToAppWindow = true;
        } else {
            recentsAttachedToAppWindow = mHasMotionEverBeenPaused || mIsLikelyToStartNewTask;
        }
        if (moveRunningTask && !mAnimationFactory.hasRecentsEverAttachedToAppWindow()
                && recentsAttachedToAppWindow) {
            // Only move running task if RecentsView has never been attached before, to avoid
            // TaskView jumping to new position as we move the tasks.
            mRecentsView.moveRunningTaskToFront();
        }
        mAnimationFactory.setRecentsAttachedToAppWindow(recentsAttachedToAppWindow, animate);

        // Reapply window transform throughout the attach animation, as the animation affects how
        // much the window is bound by overscroll (vs moving freely).
        if (animate) {
            ValueAnimator reapplyWindowTransformAnim = ValueAnimator.ofFloat(0, 1);
            reapplyWindowTransformAnim.addUpdateListener(anim -> {
                if (mRunningWindowAnim == null || mRunningWindowAnim.length == 0) {
                    applyScrollAndTransform();
                }
            });
            reapplyWindowTransformAnim.setDuration(RECENTS_ATTACH_DURATION).start();
            mStateCallback.runOnceAtState(STATE_HANDLER_INVALIDATED,
                    reapplyWindowTransformAnim::cancel);
        } else {
            applyScrollAndTransform();
        }
    }

    /**
     * Returns threshold that needs to be met in order for motion pause to be allowed.
     */
    public float getThresholdToAllowMotionPause() {
        return mIsTransientTaskbar
                ? mTaskbarHomeOverviewThreshold
                : 0;
    }

    public void setIsLikelyToStartNewTask(boolean isLikelyToStartNewTask) {
        setIsLikelyToStartNewTask(isLikelyToStartNewTask, true /* animate */);
    }

    private void setIsLikelyToStartNewTask(boolean isLikelyToStartNewTask, boolean animate) {
        if (mIsLikelyToStartNewTask != isLikelyToStartNewTask) {
            mIsLikelyToStartNewTask = isLikelyToStartNewTask;
            maybeUpdateRecentsAttachedState(animate);
        }
    }

    /**
     * Update whether user is currently dragging in a region that will trigger all apps.
     */
    private void setIsInAllAppsRegion(boolean isInAllAppsRegion) {
        if (mIsInAllAppsRegion == isInAllAppsRegion
                || !mActivityInterface.allowAllAppsFromOverview()) {
            return;
        }
        mIsInAllAppsRegion = isInAllAppsRegion;

        // Newly entering or exiting the zone - do haptic and animate recent tasks.
        VibratorWrapper.INSTANCE.get(mContext).vibrate(OVERVIEW_HAPTIC);
        maybeUpdateRecentsAttachedState(true);

        if (mActivity != null) {
            mActivity.getAppsView().getSearchUiManager().prepareToFocusEditText(mIsInAllAppsRegion);
        }

        // Draw active task below Launcher so that All Apps can appear over it.
        runActionOnRemoteHandles(remoteTargetHandle ->
                remoteTargetHandle.getTaskViewSimulator().setDrawsBelowRecents(isInAllAppsRegion));
    }


    private void buildAnimationController() {
        if (!canCreateNewOrUpdateExistingLauncherTransitionController()) {
            return;
        }
        initTransitionEndpoints(mActivity.getDeviceProfile());
        mAnimationFactory.createActivityInterface(mTransitionDragLength);
    }

    /**
     * We don't want to change mLauncherTransitionController if mGestureState.getEndTarget() == HOME
     * (it has its own animation) or if we explicitly ended the controller already.
     * @return Whether we can create the launcher controller or update its progress.
     */
    private boolean canCreateNewOrUpdateExistingLauncherTransitionController() {
        return mGestureState.getEndTarget() != HOME
                && !mHasEndedLauncherTransition && mActivity != null;
    }

    @Override
    public WindowInsets onApplyWindowInsets(View view, WindowInsets windowInsets) {
        WindowInsets result = view.onApplyWindowInsets(windowInsets);
        // Don't rebuild animation when we are animating the IME, because it will cause a loop
        // where the insets change -> animation changes (updating ime) -> insets change -> ...
        if (windowInsets.isVisible(WindowInsets.Type.ime())) {
            return result;
        }
        if (mGestureState.getEndTarget() == null) {
            buildAnimationController();
        }
        // Reapply the current shift to ensure it takes new insets into account, e.g. when long
        // pressing to stash taskbar without moving the finger.
        onCurrentShiftUpdated();
        return result;
    }

    private void onAnimatorPlaybackControllerCreated(AnimatorControllerWithResistance anim) {
        boolean isFirstCreation = mLauncherTransitionController == null;
        mLauncherTransitionController = anim;
        if (isFirstCreation) {
            mStateCallback.runOnceAtState(STATE_GESTURE_STARTED, () -> {
                // Wait until the gesture is started (touch slop was passed) to start in sync with
                // mWindowTransitionController. This ensures we don't hide the taskbar background
                // when long pressing to stash it, for instance.
                mLauncherTransitionController.getNormalController().dispatchOnStart();
                updateLauncherTransitionProgress();
            });
        }
    }

    public Intent getLaunchIntent() {
        return mGestureState.getOverviewIntent();
    }

    /**
     * Called when the value of {@link #mCurrentShift} changes
     */
    @UiThread
    @Override
    public void onCurrentShiftUpdated() {
        float threshold = LauncherPrefs.get(mContext).get(ALL_APPS_OVERVIEW_THRESHOLD) / 100f;
        setIsInAllAppsRegion(mCurrentShift.value >= threshold);
        updateSysUiFlags(mCurrentShift.value);
        applyScrollAndTransform();

        updateLauncherTransitionProgress();
    }

    private void updateLauncherTransitionProgress() {
        if (mLauncherTransitionController == null
                || !canCreateNewOrUpdateExistingLauncherTransitionController()) {
            return;
        }
        mLauncherTransitionController.setProgress(
                Math.max(mCurrentShift.value, getScaleProgressDueToScroll()), mDragLengthFactor);
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
            mRecentsAnimationController.setUseLauncherSystemBarFlags(swipeUpThresholdPassed
                    ||  (quickswitchThresholdPassed && centermostTaskFlags != 0));
            mRecentsAnimationController.setSplitScreenMinimized(mContext, swipeUpThresholdPassed);
            // Provide a hint to WM the direction that we will be settling in case the animation
            // needs to be canceled
            mRecentsAnimationController.setWillFinishToHome(swipeUpThresholdPassed);

            if (mActivity == null) return;
            if (swipeUpThresholdPassed) {
                mActivity.getSystemUiController().updateUiState(UI_STATE_FULLSCREEN_TASK, 0);
            } else {
                mActivity.getSystemUiController().updateUiState(
                        UI_STATE_FULLSCREEN_TASK, centermostTaskFlags);
            }
        }
    }

    @Override
    public void onRecentsAnimationStart(RecentsAnimationController controller,
            RecentsAnimationTargets targets) {
        super.onRecentsAnimationStart(controller, targets);
        if (isDesktopModeSupported() && targets.hasDesktopTasks()) {
            mRemoteTargetHandles = mTargetGluer.assignTargetsForDesktop(targets);
        } else {
            int untrimmedAppCount = mRemoteTargetHandles.length;
            mRemoteTargetHandles = mTargetGluer.assignTargetsForSplitScreen(targets);
            if (mRemoteTargetHandles.length < untrimmedAppCount && mIsSwipeForSplit) {
                updateIsGestureForSplit(mRemoteTargetHandles.length);
                setupRecentsViewUi();
            }
        }
        mRecentsAnimationController = controller;
        mRecentsAnimationTargets = targets;
        mSwipePipToHomeReleaseCheck = new RemoteAnimationTargets.ReleaseCheck();
        mSwipePipToHomeReleaseCheck.setCanRelease(true);
        mRecentsAnimationTargets.addReleaseCheck(mSwipePipToHomeReleaseCheck);

        // Only initialize the device profile, if it has not been initialized before, as in some
        // configurations targets.homeContentInsets may not be correct.
        if (mActivity == null) {
            RemoteAnimationTarget primaryTaskTarget = targets.apps[0];
            // orientation state is independent of which remote target handle we use since both
            // should be pointing to the same one. Just choose index 0 for now since that works for
            // both split and non-split
            RecentsOrientedState orientationState = mRemoteTargetHandles[0].getTaskViewSimulator()
                    .getOrientationState();
            DeviceProfile dp = orientationState.getLauncherDeviceProfile();
            if (targets.minimizedHomeBounds != null && primaryTaskTarget != null) {
                Rect overviewStackBounds = mActivityInterface
                        .getOverviewWindowBounds(targets.minimizedHomeBounds, primaryTaskTarget);
                dp = dp.getMultiWindowProfile(mContext,
                        new WindowBounds(overviewStackBounds, targets.homeContentInsets));
            } else {
                // If we are not in multi-window mode, home insets should be same as system insets.
                dp = dp.copy(mContext);
            }
            dp.updateInsets(targets.homeContentInsets);
            dp.updateIsSeascape(mContext);
            initTransitionEndpoints(dp);
            orientationState.setMultiWindowMode(dp.isMultiWindowMode);
        }

        // Notify when the animation starts
        flushOnRecentsAnimationAndLauncherBound();

        // Only add the callback to enable the input consumer after we actually have the controller
        mStateCallback.runOnceAtState(STATE_APP_CONTROLLER_RECEIVED | STATE_GESTURE_STARTED,
                this::startInterceptingTouchesForGesture);
        mStateCallback.setStateOnUiThread(STATE_APP_CONTROLLER_RECEIVED);
    }

    @Override
    public void onRecentsAnimationCanceled(HashMap<Integer, ThumbnailData> thumbnailDatas) {
        ActiveGestureLog.INSTANCE.addLog(
                /* event= */ "cancelRecentsAnimation",
                /* gestureEvent= */ CANCEL_RECENTS_ANIMATION);
        mActivityInitListener.unregister();
        // Cache the recents animation controller so we can defer its cleanup to after having
        // properly cleaned up the screenshot without accidentally using it.
        mDeferredCleanupRecentsAnimationController = mRecentsAnimationController;
        mStateCallback.setStateOnUiThread(STATE_GESTURE_CANCELLED | STATE_HANDLER_INVALIDATED);
        // Defer clearing the controller and the targets until after we've updated the state
        mRecentsAnimationController = null;
        mRecentsAnimationTargets = null;
        if (mRecentsView != null) {
            mRecentsView.setRecentsAnimationTargets(null, null);
        }
    }

    @UiThread
    public void onGestureStarted(boolean isLikelyToStartNewTask) {
        mActivityInterface.closeOverlay();
        TaskUtils.closeSystemWindowsAsync(CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);

        if (mRecentsView != null) {
            final View rv = mRecentsView;
            mRecentsView.getViewTreeObserver().addOnDrawListener(new OnDrawListener() {
                boolean mHandled = false;

                @Override
                public void onDraw() {
                    if (mHandled) {
                        return;
                    }
                    mHandled = true;

                    InteractionJankMonitorWrapper.begin(mRecentsView,
                            InteractionJankMonitorWrapper.CUJ_QUICK_SWITCH, 2000 /* ms timeout */);
                    InteractionJankMonitorWrapper.begin(mRecentsView,
                            InteractionJankMonitorWrapper.CUJ_APP_CLOSE_TO_HOME);
                    InteractionJankMonitorWrapper.begin(mRecentsView,
                            InteractionJankMonitorWrapper.CUJ_APP_SWIPE_TO_RECENTS);

                    rv.post(() -> rv.getViewTreeObserver().removeOnDrawListener(this));
                }
            });
        }
        notifyGestureStarted();
        setIsLikelyToStartNewTask(isLikelyToStartNewTask, false /* animate */);

        if (mIsTransientTaskbar && !mTaskbarAlreadyOpen && !isLikelyToStartNewTask) {
            setClampScrollOffset(true);
        }
        mStateCallback.setStateOnUiThread(STATE_GESTURE_STARTED);
        mGestureStarted = true;
    }

    /**
     * Sets whether or not we should clamp the scroll offset.
     * This is used to avoid x-axis movement when swiping up transient taskbar.
     * @param clampScrollOffset When true, we clamp the scroll to 0 before the clamp threshold is
     *                          met.
     */
    private void setClampScrollOffset(boolean clampScrollOffset) {
        if (!mIsTransientTaskbar) {
            return;
        }
        if (mRecentsView == null) {
            mStateCallback.runOnceAtState(STATE_LAUNCHER_PRESENT,
                    () -> mRecentsView.setClampScrollOffset(clampScrollOffset));
            return;
        }
        mRecentsView.setClampScrollOffset(clampScrollOffset);
    }


    /**
     * Notifies the launcher that the swipe gesture has started. This can be called multiple times.
     */
    @UiThread
    private void notifyGestureStarted() {
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
    @UiThread
    public void onGestureCancelled() {
        updateDisplacement(0);
        mStateCallback.setStateOnUiThread(STATE_GESTURE_COMPLETED);
        handleNormalGestureEnd(0, false, new PointF(), true /* isCancel */);
    }

    /**
     * @param endVelocityPxPerMs The velocity in the direction of the nav bar to the middle of the
     *                           screen.
     * @param velocityPxPerMs The x and y components of the velocity when the gesture ends.
     */
    @UiThread
    public void onGestureEnded(float endVelocityPxPerMs, PointF velocityPxPerMs) {
        float flingThreshold = mContext.getResources()
                .getDimension(R.dimen.quickstep_fling_threshold_speed);
        boolean isFling = mGestureStarted && !mIsMotionPaused
                && Math.abs(endVelocityPxPerMs) > flingThreshold;
        mStateCallback.setStateOnUiThread(STATE_GESTURE_COMPLETED);
        boolean isVelocityVertical = Math.abs(velocityPxPerMs.y) > Math.abs(velocityPxPerMs.x);
        if (isVelocityVertical) {
            mLogDirectionUpOrLeft = velocityPxPerMs.y < 0;
        } else {
            mLogDirectionUpOrLeft = velocityPxPerMs.x < 0;
        }
        Runnable handleNormalGestureEndCallback = () -> handleNormalGestureEnd(
                endVelocityPxPerMs, isFling, velocityPxPerMs, /* isCancel= */ false);
        if (mRecentsView != null) {
            mRecentsView.runOnPageScrollsInitialized(handleNormalGestureEndCallback);
        } else {
            handleNormalGestureEndCallback.run();
        }
    }

    private void endRunningWindowAnim(boolean cancel) {
        if (mRunningWindowAnim != null) {
            if (cancel) {
                for (RunningWindowAnim r : mRunningWindowAnim) {
                    if (r != null) {
                        r.cancel();
                    }
                }
            } else {
                for (RunningWindowAnim r : mRunningWindowAnim) {
                    if (r != null) {
                        r.end();
                    }
                }
            }
        }
        if (mParallelRunningAnim != null) {
            // Unlike the above animation, the parallel animation won't have anything to take up
            // the work if it's canceled, so just end it instead.
            mParallelRunningAnim.end();
        }
    }

    private void onSettledOnEndTarget() {
        // Fast-finish the attaching animation if it's still running.
        maybeUpdateRecentsAttachedState(false);
        final GestureEndTarget endTarget = mGestureState.getEndTarget();
        // Wait until the given View (if supplied) draws before resuming the last task.
        View postResumeLastTask = mActivityInterface.onSettledOnEndTarget(endTarget);

        if (endTarget != NEW_TASK) {
            InteractionJankMonitorWrapper.cancel(
                    InteractionJankMonitorWrapper.CUJ_QUICK_SWITCH);
        }
        if (endTarget != HOME) {
            InteractionJankMonitorWrapper.cancel(
                    InteractionJankMonitorWrapper.CUJ_APP_CLOSE_TO_HOME);
        }
        if (endTarget != RECENTS) {
            InteractionJankMonitorWrapper.cancel(
                    InteractionJankMonitorWrapper.CUJ_APP_SWIPE_TO_RECENTS);
        }

        switch (endTarget) {
            case ALL_APPS:
                mStateCallback.setState(STATE_SETTLED_ON_ALL_APPS | STATE_CAPTURE_SCREENSHOT);
                break;
            case HOME:
                mStateCallback.setState(STATE_SCALED_CONTROLLER_HOME | STATE_CAPTURE_SCREENSHOT);
                // Notify the SysUI to use fade-in animation when entering PiP
                SystemUiProxy.INSTANCE.get(mContext).setPipAnimationTypeToAlpha();
                if (isDesktopModeSupported()) {
                    // Notify the SysUI to stash desktop apps if they are visible
                    DesktopVisibilityController desktopVisibilityController =
                            mActivityInterface.getDesktopVisibilityController();
                    if (desktopVisibilityController != null) {
                        desktopVisibilityController.onHomeActionTriggered();
                    }
                }
                break;
            case RECENTS:
                mStateCallback.setState(STATE_SCALED_CONTROLLER_RECENTS | STATE_CAPTURE_SCREENSHOT
                        | STATE_SCREENSHOT_VIEW_SHOWN);
                break;
            case NEW_TASK:
                mStateCallback.setState(STATE_START_NEW_TASK | STATE_CAPTURE_SCREENSHOT);
                break;
            case LAST_TASK:
                if (postResumeLastTask != null) {
                    ViewUtils.postFrameDrawn(postResumeLastTask,
                            () -> mStateCallback.setState(STATE_RESUME_LAST_TASK));
                } else {
                    mStateCallback.setState(STATE_RESUME_LAST_TASK);
                }
                // Restore the divider as it resumes the last top-tasks.
                setDividerShown(true);
                break;
        }
        ActiveGestureLog.INSTANCE.addLog(
                new ActiveGestureLog.CompoundString("onSettledOnEndTarget ")
                        .append(endTarget.name()),
                /* gestureEvent= */ ON_SETTLED_ON_END_TARGET);
    }

    /** @return Whether this was the task we were waiting to appear, and thus handled it. */
    protected boolean handleTaskAppeared(RemoteAnimationTarget[] appearedTaskTarget) {
        if (mStateCallback.hasStates(STATE_HANDLER_INVALIDATED)) {
            return false;
        }
        boolean hasStartedTaskBefore = Arrays.stream(appearedTaskTarget).anyMatch(
                mGestureState.mLastStartedTaskIdPredicate);
        if (mStateCallback.hasStates(STATE_START_NEW_TASK) && hasStartedTaskBefore) {
            reset();
            return true;
        }
        return false;
    }

    private float dpiFromPx(float pixels) {
        return Utilities.dpiFromPx(pixels, mContext.getResources().getDisplayMetrics().densityDpi);
    }

    private GestureEndTarget calculateEndTarget(
            PointF velocityPxPerMs, float endVelocityPxPerMs, boolean isFlingY, boolean isCancel) {

        ActiveGestureErrorDetector.GestureEvent gestureEvent =
                velocityPxPerMs.x == 0 && velocityPxPerMs.y == 0
                        ? INVALID_VELOCITY_ON_SWIPE_UP
                        : null;
        ActiveGestureLog.INSTANCE.addLog(
                new ActiveGestureLog.CompoundString("calculateEndTarget: velocities=(x=")
                        .append(dpiFromPx(velocityPxPerMs.x))
                        .append("dp/ms, y=")
                        .append(dpiFromPx(velocityPxPerMs.y))
                        .append("dp/ms), angle=")
                        .append(Math.toDegrees(Math.atan2(
                                -velocityPxPerMs.y, velocityPxPerMs.x))), gestureEvent);

        if (mGestureState.isHandlingAtomicEvent()) {
            // Button mode, this is only used to go to recents.
            return RECENTS;
        }

        GestureEndTarget endTarget;
        if (isCancel) {
            endTarget = LAST_TASK;
        } else if (isFlingY) {
            endTarget = calculateEndTargetForFlingY(velocityPxPerMs, endVelocityPxPerMs);
        } else {
            endTarget = calculateEndTargetForNonFling(velocityPxPerMs);
        }

        if (mDeviceState.isOverviewDisabled() && endTarget == RECENTS) {
            return LAST_TASK;
        }

        if (isDesktopModeSupported() && endTarget == NEW_TASK) {
            // TODO(b/268075592): add support for quickswitch to/from desktop
            return LAST_TASK;
        }

        return endTarget;
    }

    private GestureEndTarget calculateEndTargetForFlingY(PointF velocity, float endVelocity) {
        // If swiping at a diagonal, base end target on the faster velocity direction.
        final boolean willGoToNewTask =
                isScrollingToNewTask() && Math.abs(velocity.x) > Math.abs(endVelocity);
        final boolean isSwipeUp = endVelocity < 0;
        if (mIsInAllAppsRegion) {
            return isSwipeUp ? ALL_APPS : LAST_TASK;
        }
        if (!isSwipeUp) {
            final boolean isCenteredOnNewTask = mRecentsView != null
                    && mRecentsView.getDestinationPage() != mRecentsView.getRunningTaskIndex();
            return willGoToNewTask || isCenteredOnNewTask ? NEW_TASK : LAST_TASK;
        }

        return willGoToNewTask ? NEW_TASK : HOME;
    }

    private GestureEndTarget calculateEndTargetForNonFling(PointF velocity) {
        final boolean isScrollingToNewTask = isScrollingToNewTask();

        // Fully gestural mode.
        final boolean isFlingX = Math.abs(velocity.x) > mContext.getResources()
                .getDimension(R.dimen.quickstep_fling_threshold_speed);
        if (mIsInAllAppsRegion) {
            return ALL_APPS;
        } else if (isScrollingToNewTask && isFlingX) {
            // Flinging towards new task takes precedence over mIsMotionPaused (which only
            // checks y-velocity).
            return NEW_TASK;
        } else if (mIsMotionPaused) {
            return RECENTS;
        } else if (isScrollingToNewTask) {
            return NEW_TASK;
        }
        return velocity.y < 0 && mCanSlowSwipeGoHome ? HOME : LAST_TASK;
    }

    private boolean isScrollingToNewTask() {
        if (mRecentsView == null) {
            return false;
        }
        if (!hasTargets()) {
            // If there are no running tasks, then we can assume that this is a continuation of
            // the last gesture, but after the recents animation has finished.
            return true;
        }
        int runningTaskIndex = mRecentsView.getRunningTaskIndex();
        return runningTaskIndex >= 0 && mRecentsView.getNextPage() != runningTaskIndex;
    }

    /**
     * Sets whether a slow swipe can go to the HOME end target when the user lets go. A slow swipe
     * for this purpose must meet two criteria:
     *   1) y-velocity is less than quickstep_fling_threshold_speed
     *   AND
     *   2) motion pause has not been detected (possibly because
     *   {@link MotionPauseDetector#setDisallowPause} has been called with disallowPause == true)
     */
    public void setCanSlowSwipeGoHome(boolean canSlowSwipeGoHome) {
        mCanSlowSwipeGoHome = canSlowSwipeGoHome;
    }

    @UiThread
    private void handleNormalGestureEnd(
            float endVelocityPxPerMs, boolean isFling, PointF velocityPxPerMs, boolean isCancel) {
        long duration = MAX_SWIPE_DURATION;
        float currentShift = mCurrentShift.value;
        final GestureEndTarget endTarget = calculateEndTarget(
                velocityPxPerMs, endVelocityPxPerMs, isFling, isCancel);
        // Set the state, but don't notify until the animation completes
        mGestureState.setEndTarget(endTarget, false /* isAtomic */);
        mAnimationFactory.setEndTarget(endTarget);

        float endShift = endTarget == ALL_APPS ? mDragLengthFactor
                : endTarget.isLauncher ? 1 : 0;
        final float startShift;
        if (!isFling) {
            long expectedDuration = Math.abs(Math.round((endShift - currentShift)
                    * MAX_SWIPE_DURATION * SWIPE_DURATION_MULTIPLIER));
            duration = Math.min(MAX_SWIPE_DURATION, expectedDuration);
            startShift = currentShift;
        } else {
            startShift = Utilities.boundToRange(currentShift - velocityPxPerMs.y
                    * getSingleFrameMs(mContext) / mTransitionDragLength, 0, mDragLengthFactor);
            if (mTransitionDragLength > 0) {
                float distanceToTravel = (endShift - currentShift) * mTransitionDragLength;

                // we want the page's snap velocity to approximately match the velocity at
                // which the user flings, so we scale the duration by a value near to the
                // derivative of the scroll interpolator at zero, ie. 2.
                long baseDuration = Math.round(Math.abs(distanceToTravel / velocityPxPerMs.y));
                duration = Math.min(MAX_SWIPE_DURATION, 2 * baseDuration);
            }
        }
        Interpolator interpolator;
        S state = mActivityInterface.stateFromGestureEndTarget(endTarget);
        if (state.displayOverviewTasksAsGrid(mDp)) {
            interpolator = ACCELERATE_DECELERATE;
        } else if (endTarget == RECENTS) {
            interpolator = OVERSHOOT_1_2;
        } else {
            interpolator = DECELERATE;
        }

        if (endTarget.isLauncher) {
            mInputConsumerProxy.enable();
        }
        if (endTarget == HOME) {
            duration = mActivity != null && mActivity.getDeviceProfile().isTaskbarPresent
                    ? StaggeredWorkspaceAnim.DURATION_TASKBAR_MS
                    : StaggeredWorkspaceAnim.DURATION_MS;
            // Early detach the nav bar once the endTarget is determined as HOME
            if (mRecentsAnimationController != null) {
                mRecentsAnimationController.detachNavigationBarFromApp(true);
            }
        } else if (endTarget == RECENTS) {
            if (mRecentsView != null) {
                int nearestPage = mRecentsView.getDestinationPage();
                if (nearestPage == INVALID_PAGE) {
                    // Allow the snap to invalid page to catch future error cases.
                    Log.e(TAG,
                            "RecentsView destination page is invalid",
                            new IllegalStateException());
                }

                boolean isScrolling = false;
                if (mRecentsView.getNextPage() != nearestPage) {
                    // We shouldn't really scroll to the next page when swiping up to recents.
                    // Only allow settling on the next page if it's nearest to the center.
                    mRecentsView.snapToPage(nearestPage, Math.toIntExact(duration));
                    isScrolling = true;
                }
                if (mRecentsView.getScroller().getDuration() > MAX_SWIPE_DURATION) {
                    mRecentsView.snapToPage(mRecentsView.getNextPage(), (int) MAX_SWIPE_DURATION);
                    isScrolling = true;
                }
                if (!mGestureState.isHandlingAtomicEvent() || isScrolling) {
                    duration = Math.max(duration, mRecentsView.getScroller().getDuration());
                }
            }
        } else if (endTarget == LAST_TASK && mRecentsView != null
                && mRecentsView.getNextPage() != mRecentsView.getRunningTaskIndex()) {
            mRecentsView.snapToPage(mRecentsView.getRunningTaskIndex(), Math.toIntExact(duration));
        }

        // Let RecentsView handle the scrolling to the task, which we launch in startNewTask()
        // or resumeLastTask().
        Runnable onPageTransitionEnd = () -> {
            mGestureState.setState(STATE_RECENTS_SCROLLING_FINISHED);
            setClampScrollOffset(false);
        };
        if (mRecentsView != null) {
            ActiveGestureLog.INSTANCE.trackEvent(ActiveGestureErrorDetector.GestureEvent
                    .SET_ON_PAGE_TRANSITION_END_CALLBACK);
            mRecentsView.setOnPageTransitionEndCallback(onPageTransitionEnd);
        } else {
            onPageTransitionEnd.run();
        }

        animateToProgress(startShift, endShift, duration, interpolator, endTarget, velocityPxPerMs);
    }

    private void doLogGesture(GestureEndTarget endTarget, @Nullable TaskView targetTask) {
        if (mDp == null || !mDp.isGestureMode) {
            // We probably never received an animation controller, skip logging.
            return;
        }

        StatsLogManager.EventEnum event;
        switch (endTarget) {
            case HOME:
                event = LAUNCHER_HOME_GESTURE;
                break;
            case RECENTS:
                event = LAUNCHER_OVERVIEW_GESTURE;
                break;
            case LAST_TASK:
            case NEW_TASK:
                event = mLogDirectionUpOrLeft ? LAUNCHER_QUICKSWITCH_LEFT
                        : LAUNCHER_QUICKSWITCH_RIGHT;
                break;
            default:
                event = IGNORE;
        }
        StatsLogger logger = StatsLogManager.newInstance(mContext).logger()
                .withSrcState(LAUNCHER_STATE_BACKGROUND)
                .withDstState(endTarget.containerType)
                .withInputType(mGestureState.isTrackpadGesture()
                        ? SysUiStatsLog.LAUNCHER_UICHANGED__INPUT_TYPE__TRACKPAD
                        : SysUiStatsLog.LAUNCHER_UICHANGED__INPUT_TYPE__TOUCH);
        if (targetTask != null) {
            logger.withItemInfo(targetTask.getItemInfo());
        }

        int pageIndex = endTarget == LAST_TASK || mRecentsView == null
                ? LOG_NO_OP_PAGE_INDEX
                : mRecentsView.getNextPage();
        logger.withRank(pageIndex);
        logger.log(event);
    }

    /** Animates to the given progress, where 0 is the current app and 1 is overview. */
    @UiThread
    private void animateToProgress(float start, float end, long duration, Interpolator interpolator,
            GestureEndTarget target, PointF velocityPxPerMs) {
        runOnRecentsAnimationAndLauncherBound(() -> animateToProgressInternal(start, end, duration,
                interpolator, target, velocityPxPerMs));
    }

    protected abstract HomeAnimationFactory createHomeAnimationFactory(
            ArrayList<IBinder> launchCookies, long duration, boolean isTargetTranslucent,
            boolean appCanEnterPip, RemoteAnimationTarget runningTaskTarget);

    private final TaskStackChangeListener mActivityRestartListener = new TaskStackChangeListener() {
        @Override
        public void onActivityRestartAttempt(ActivityManager.RunningTaskInfo task,
                boolean homeTaskVisible, boolean clearedTask, boolean wasVisible) {
            boolean taskRunningAndNotHome = Arrays.stream(mGestureState
                            .getRunningTaskIds(true /*getMultipleTasks*/))
                    .anyMatch(taskId -> task.taskId == taskId
                            && task.configuration.windowConfiguration.getActivityType()
                            != ACTIVITY_TYPE_HOME);
            if (taskRunningAndNotHome) {
                // Since this is an edge case, just cancel and relaunch with default activity
                // options (since we don't know if there's an associated app icon to launch from)
                endRunningWindowAnim(true /* cancel */);
                TaskStackChangeListeners.getInstance().unregisterTaskStackListener(
                        mActivityRestartListener);
                ActivityManagerWrapper.getInstance().startActivityFromRecents(task.taskId, null);
            }
        }
    };

    @UiThread
    private void animateToProgressInternal(float start, float end, long duration,
            Interpolator interpolator, GestureEndTarget target, PointF velocityPxPerMs) {
        maybeUpdateRecentsAttachedState();

        // If we are transitioning to launcher, then listen for the activity to be restarted while
        // the transition is in progress
        if (mGestureState.getEndTarget().isLauncher) {
            // This is also called when the launcher is resumed, in order to clear the pending
            // widgets that have yet to be configured.
            if (mActivity != null) {
                DragView.removeAllViews(mActivity);
            }

            TaskStackChangeListeners.getInstance().registerTaskStackListener(
                    mActivityRestartListener);

            mParallelRunningAnim = mActivityInterface.getParallelAnimationToLauncher(
                    mGestureState.getEndTarget(), duration,
                    mTaskAnimationManager.getCurrentCallbacks());
            if (mParallelRunningAnim != null) {
                mParallelRunningAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mParallelRunningAnim = null;
                    }
                });
                mParallelRunningAnim.start();
            }
        }

        if (mGestureState.getEndTarget() == HOME) {
            getOrientationHandler().adjustFloatingIconStartVelocity(velocityPxPerMs);
            // Take first task ID, if there are multiple we don't have any special home
            // animation so doesn't matter for splitscreen.. though the "allowEnterPip" might change
            // depending on which task it is..
            final RemoteAnimationTarget runningTaskTarget = mRecentsAnimationTargets != null
                    ? mRecentsAnimationTargets
                    .findTask(mGestureState.getTopRunningTaskId())
                    : null;
            final ArrayList<IBinder> cookies = runningTaskTarget != null
                    ? runningTaskTarget.taskInfo.launchCookies
                    : new ArrayList<>();
            boolean isTranslucent = runningTaskTarget != null && runningTaskTarget.isTranslucent;
            boolean hasValidLeash = runningTaskTarget != null
                    && runningTaskTarget.leash != null
                    && runningTaskTarget.leash.isValid();
            boolean appCanEnterPip = !mDeviceState.isPipActive()
                    && hasValidLeash
                    && runningTaskTarget.allowEnterPip
                    && runningTaskTarget.taskInfo.pictureInPictureParams != null
                    && runningTaskTarget.taskInfo.pictureInPictureParams.isAutoEnterEnabled();
            HomeAnimationFactory homeAnimFactory =
                    createHomeAnimationFactory(cookies, duration, isTranslucent, appCanEnterPip,
                            runningTaskTarget);
            SwipePipToHomeAnimator swipePipToHomeAnimator = !mIsSwipeForSplit && appCanEnterPip
                    ? createWindowAnimationToPip(homeAnimFactory, runningTaskTarget, start)
                    : null;
            mIsSwipingPipToHome = swipePipToHomeAnimator != null;
            final RectFSpringAnim[] windowAnim;
            if (mIsSwipingPipToHome) {
                mSwipePipToHomeAnimator = swipePipToHomeAnimator;
                mSwipePipToHomeAnimators[0] = mSwipePipToHomeAnimator;
                if (mSwipePipToHomeReleaseCheck != null) {
                    mSwipePipToHomeReleaseCheck.setCanRelease(false);
                }

                // grab a screenshot before the PipContentOverlay gets parented on top of the task
                UI_HELPER_EXECUTOR.execute(() -> {
                    if (mRecentsAnimationController == null) {
                        return;
                    }
                    // Directly use top task, split to pip handled on shell side
                    final int taskId = mGestureState.getTopRunningTaskId();
                    mTaskSnapshotCache.put(taskId,
                            mRecentsAnimationController.screenshotTask(taskId));
                });

                // let SystemUi reparent the overlay leash as soon as possible
                SystemUiProxy.INSTANCE.get(mContext).stopSwipePipToHome(
                        mSwipePipToHomeAnimator.getTaskId(),
                        mSwipePipToHomeAnimator.getComponentName(),
                        mSwipePipToHomeAnimator.getDestinationBounds(),
                        mSwipePipToHomeAnimator.getContentOverlay(),
                        mSwipePipToHomeAnimator.getAppBounds());

                windowAnim = mSwipePipToHomeAnimators;
            } else {
                mSwipePipToHomeAnimator = null;
                if (mSwipePipToHomeReleaseCheck != null) {
                    mSwipePipToHomeReleaseCheck.setCanRelease(true);
                    mSwipePipToHomeReleaseCheck = null;
                }
                windowAnim = createWindowAnimationToHome(start, homeAnimFactory);

                windowAnim[0].addAnimatorListener(new AnimationSuccessListener() {
                    @Override
                    public void onAnimationSuccess(Animator animator) {
                        if (mRecentsAnimationController == null) {
                            // If the recents animation is interrupted, we still end the running
                            // animation (not canceled) so this is still called. In that case,
                            // we can skip doing any future work here for the current gesture.
                            return;
                        }
                        // Finalize the state and notify of the change
                        mGestureState.setState(STATE_END_TARGET_ANIMATION_FINISHED);
                    }
                });
            }
            mRunningWindowAnim = new RunningWindowAnim[windowAnim.length];
            for (int i = 0, windowAnimLength = windowAnim.length; i < windowAnimLength; i++) {
                RectFSpringAnim windowAnimation = windowAnim[i];
                if (windowAnimation == null) {
                    continue;
                }
                DeviceProfile dp = mActivity == null ? null : mActivity.getDeviceProfile();
                windowAnimation.start(mContext, dp, velocityPxPerMs);
                mRunningWindowAnim[i] = RunningWindowAnim.wrap(windowAnimation);
            }
            homeAnimFactory.setSwipeVelocity(velocityPxPerMs.y);
            homeAnimFactory.playAtomicAnimation(velocityPxPerMs.y);
            mLauncherTransitionController = null;

            if (mRecentsView != null) {
                mRecentsView.onPrepareGestureEndAnimation(null, mGestureState.getEndTarget(),
                        getRemoteTaskViewSimulators());
            }
        } else {
            AnimatorSet animatorSet = new AnimatorSet();
            ValueAnimator windowAnim = mCurrentShift.animateToValue(start, end);
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
                    if (mRecentsView != null) {
                        int taskToLaunch = mRecentsView.getNextPage();
                        int runningTask = getLastAppearedTaskIndex();
                        boolean hasStartedNewTask = hasStartedNewTask();
                        if (target == NEW_TASK && taskToLaunch == runningTask
                                && !hasStartedNewTask) {
                            // We are about to launch the current running task, so use LAST_TASK
                            // state instead of NEW_TASK. This could happen, for example, if our
                            // scroll is aborted after we determined the target to be NEW_TASK.
                            mGestureState.setEndTarget(LAST_TASK);
                        } else if (target == LAST_TASK && hasStartedNewTask) {
                            // We are about to re-launch the previously running task, but we can't
                            // just finish the controller like we normally would because that would
                            // instead resume the last task that appeared, and not ensure that this
                            // task is restored to the top. To address this, re-launch the task as
                            // if it were a new task.
                            mGestureState.setEndTarget(NEW_TASK);
                        }
                    }
                    mGestureState.setState(STATE_END_TARGET_ANIMATION_FINISHED);
                }
            });
            animatorSet.play(windowAnim);
            if (mRecentsView != null) {
                mRecentsView.onPrepareGestureEndAnimation(
                        animatorSet, mGestureState.getEndTarget(),
                        getRemoteTaskViewSimulators());
            }
            animatorSet.setDuration(duration).setInterpolator(interpolator);
            animatorSet.start();
            mRunningWindowAnim = new RunningWindowAnim[]{RunningWindowAnim.wrap(animatorSet)};
        }
    }

    private int calculateWindowRotation(RemoteAnimationTarget runningTaskTarget,
            RecentsOrientedState orientationState) {
        if (runningTaskTarget.rotationChange != 0
                && TaskAnimationManager.ENABLE_SHELL_TRANSITIONS) {
            return Math.abs(runningTaskTarget.rotationChange) == ROTATION_90
                    ? ROTATION_270 : ROTATION_90;
        } else {
            return orientationState.getDisplayRotation();
        }
    }

    @Nullable
    private SwipePipToHomeAnimator createWindowAnimationToPip(HomeAnimationFactory homeAnimFactory,
            RemoteAnimationTarget runningTaskTarget, float startProgress) {
        if (mRecentsView == null) {
            // Overview was destroyed, bail early.
            return null;
        }
        // Directly animate the app to PiP (picture-in-picture) mode
        final ActivityManager.RunningTaskInfo taskInfo = runningTaskTarget.taskInfo;
        final RecentsOrientedState orientationState = mRemoteTargetHandles[0].getTaskViewSimulator()
                .getOrientationState();
        final int windowRotation = calculateWindowRotation(runningTaskTarget, orientationState);
        final int homeRotation = orientationState.getRecentsActivityRotation();

        final Matrix[] homeToWindowPositionMaps = new Matrix[mRemoteTargetHandles.length];
        final RectF startRect = updateProgressForStartRect(homeToWindowPositionMaps,
                startProgress)[0];
        final Matrix homeToWindowPositionMap = homeToWindowPositionMaps[0];
        // Move the startRect to Launcher space as floatingIconView runs in Launcher
        final Matrix windowToHomePositionMap = new Matrix();
        homeToWindowPositionMap.invert(windowToHomePositionMap);
        windowToHomePositionMap.mapRect(startRect);

        final Rect hotseatKeepClearArea = getKeepClearAreaForHotseat();
        final Rect destinationBounds = SystemUiProxy.INSTANCE.get(mContext)
                .startSwipePipToHome(taskInfo.topActivity,
                        taskInfo.topActivityInfo,
                        runningTaskTarget.taskInfo.pictureInPictureParams,
                        homeRotation,
                        hotseatKeepClearArea);
        if (destinationBounds == null) {
            // No destination bounds returned from SystemUI, bail early.
            return null;
        }
        final Rect appBounds = new Rect();
        final WindowConfiguration winConfig = taskInfo.configuration.windowConfiguration;
        // Adjust the appBounds for TaskBar by using the calculated window crop Rect
        // from TaskViewSimulator and fallback to the bounds in TaskInfo when it's originated
        // from windowing modes other than full-screen.
        if (winConfig.getWindowingMode() == WindowConfiguration.WINDOWING_MODE_FULLSCREEN) {
            mRemoteTargetHandles[0].getTaskViewSimulator().getCurrentCropRect().round(appBounds);
        } else {
            appBounds.set(winConfig.getBounds());
        }
        final SwipePipToHomeAnimator.Builder builder = new SwipePipToHomeAnimator.Builder()
                .setContext(mContext)
                .setTaskId(runningTaskTarget.taskId)
                .setActivityInfo(taskInfo.topActivityInfo)
                .setAppIconSizePx(mDp.iconSizePx)
                .setLeash(runningTaskTarget.leash)
                .setSourceRectHint(
                        runningTaskTarget.taskInfo.pictureInPictureParams.getSourceRectHint())
                .setAppBounds(appBounds)
                .setHomeToWindowPositionMap(homeToWindowPositionMap)
                .setStartBounds(startRect)
                .setDestinationBounds(destinationBounds)
                .setCornerRadius(mRecentsView.getPipCornerRadius())
                .setShadowRadius(mRecentsView.getPipShadowRadius())
                .setAttachedView(mRecentsView);
        // We would assume home and app window always in the same rotation While homeRotation
        // is not ROTATION_0 (which implies the rotation is turned on in launcher settings).
        if (homeRotation == ROTATION_0
                && (windowRotation == ROTATION_90 || windowRotation == ROTATION_270)) {
            builder.setFromRotation(mRemoteTargetHandles[0].getTaskViewSimulator(), windowRotation,
                    taskInfo.displayCutoutInsets);
        }
        final SwipePipToHomeAnimator swipePipToHomeAnimator = builder.build();
        AnimatorPlaybackController activityAnimationToHome =
                homeAnimFactory.createActivityAnimationToHome();
        swipePipToHomeAnimator.addAnimatorListener(new AnimatorListenerAdapter() {
            private boolean mHasAnimationEnded;
            @Override
            public void onAnimationStart(Animator animation) {
                if (mHasAnimationEnded) return;
                // Ensure Launcher ends in NORMAL state
                activityAnimationToHome.dispatchOnStart();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mHasAnimationEnded) return;
                mHasAnimationEnded = true;
                activityAnimationToHome.getAnimationPlayer().end();
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
        setupWindowAnimation(new RectFSpringAnim[]{swipePipToHomeAnimator});
        return swipePipToHomeAnimator;
    }

    private Rect getKeepClearAreaForHotseat() {
        Rect keepClearArea;
        // the keep clear area in global screen coordinates, in pixels
        if (mDp.isPhone) {
            if (mDp.isSeascape()) {
                // in seascape the Hotseat is on the left edge of the screen
                keepClearArea = new Rect(0, 0, mDp.hotseatBarSizePx, mDp.heightPx);
            } else if (mDp.isLandscape) {
                // in landscape the Hotseat is on the right edge of the screen
                keepClearArea = new Rect(mDp.widthPx - mDp.hotseatBarSizePx, 0,
                        mDp.widthPx, mDp.heightPx);
            } else {
                // in portrait mode the Hotseat is at the bottom of the screen
                keepClearArea = new Rect(0, mDp.heightPx - mDp.hotseatBarSizePx,
                        mDp.widthPx, mDp.heightPx);
            }
        } else {
            // large screens have Hotseat always at the bottom of the screen
            keepClearArea = new Rect(0, mDp.heightPx - mDp.hotseatBarSizePx,
                    mDp.widthPx, mDp.heightPx);
        }
        return keepClearArea;
    }

    /**
     * Notifies to start intercepting touches in the app window and hide the divider bar if needed.
     * @see RecentsAnimationController#enableInputConsumer()
     */
    private void startInterceptingTouchesForGesture() {
        if (mRecentsAnimationController == null || !mStartMovingTasks) {
            return;
        }

        mRecentsAnimationController.enableInputConsumer();

        // Hide the divider as it starts intercepting touches in the app window.
        setDividerShown(false);
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
                && !mCanceled
                && mRecentsView != null) {
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
    protected RectFSpringAnim[] createWindowAnimationToHome(float startProgress,
            HomeAnimationFactory homeAnimationFactory) {
        RectFSpringAnim[] anim =
                super.createWindowAnimationToHome(startProgress, homeAnimationFactory);
        setupWindowAnimation(anim);
        return anim;
    }

    private void setupWindowAnimation(RectFSpringAnim[] anims) {
        anims[0].addOnUpdateListener((r, p) -> {
            updateSysUiFlags(Math.max(p, mCurrentShift.value));
        });
        anims[0].addAnimatorListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                if (mRecentsView != null) {
                    mRecentsView.post(mRecentsView::resetTaskVisuals);
                }
                // Make sure recents is in its final state
                maybeUpdateRecentsAttachedState(false);
                mActivityInterface.onSwipeUpToHomeComplete(mDeviceState);
            }
        });
        if (mRecentsAnimationTargets != null) {
            mRecentsAnimationTargets.addReleaseCheck(anims[0]);
        }
    }

    public void onConsumerAboutToBeSwitched() {
        // In the off chance that the gesture ends before Launcher is started, we should clear
        // the callback here so that it doesn't update with the wrong state
        resetLauncherListeners();
        if (mGestureState.isRecentsAnimationRunning() && mGestureState.getEndTarget() != null
                && !mGestureState.getEndTarget().isLauncher) {
            // Continued quick switch.
            cancelCurrentAnimation();
        } else {
            mStateCallback.setStateOnUiThread(STATE_FINISH_WITH_NO_END);
            reset();
        }
    }

    public boolean isCanceled() {
        return mCanceled;
    }

    @UiThread
    private void resumeLastTask() {
        if (mRecentsAnimationController != null) {
            mRecentsAnimationController.finish(false /* toRecents */, null);
        }
        doLogGesture(LAST_TASK, null);
        reset();
    }

    @UiThread
    private void startNewTask() {
        TaskView taskToLaunch = mRecentsView == null ? null : mRecentsView.getNextPageTaskView();
        startNewTask(success -> {
            if (!success) {
                reset();
                // We couldn't launch the task, so take user to overview so they can
                // decide what to do instead of staying in this broken state.
                endLauncherTransitionController();
                updateSysUiFlags(1 /* windowProgress == overview */);
            }
            doLogGesture(NEW_TASK, taskToLaunch);
        });
    }

    /**
     * Called when we successfully startNewTask() on the task that was previously running. Normally
     * we call resumeLastTask() when returning to the previously running task, but this handles a
     * specific edge case: if we switch from A to B, and back to A before B appears, we need to
     * start A again to ensure it stays on top.
     */
    @androidx.annotation.CallSuper
    protected void onRestartPreviouslyAppearedTask() {
        // Finish the controller here, since we won't get onTaskAppeared() for a task that already
        // appeared.
        if (mRecentsAnimationController != null) {
            mRecentsAnimationController.finish(false, null);
        }
        reset();
    }

    @UiThread
    private void finishCurrentTransitionToAllApps() {
        finishCurrentTransitionToHome();
        reset();
    }

    private void reset() {
        mStateCallback.setStateOnUiThread(STATE_HANDLER_INVALIDATED);
        if (mActivity != null) {
            mActivity.removeEventCallback(EVENT_DESTROYED, mLauncherOnDestroyCallback);
        }
    }

    /**
     * Cancels any running animation so that the active target can be overriden by a new swipe
     * handler (in case of quick switch).
     */
    private void cancelCurrentAnimation() {
        ActiveGestureLog.INSTANCE.addLog(
                "AbsSwipeUpHandler.cancelCurrentAnimation",
                ActiveGestureErrorDetector.GestureEvent.CANCEL_CURRENT_ANIMATION);
        mCanceled = true;
        mCurrentShift.cancelAnimation();

        // Cleanup when switching handlers
        mInputConsumerProxy.unregisterOnTouchDownCallback();
        mActivityInitListener.unregister();
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(
                mActivityRestartListener);
        mTaskSnapshotCache.clear();
    }

    private void invalidateHandler() {
        if (!mActivityInterface.isInLiveTileMode() || mGestureState.getEndTarget() != RECENTS) {
            mInputConsumerProxy.destroy();
            mTaskAnimationManager.setLiveTileCleanUpHandler(null);
        }
        mInputConsumerProxy.unregisterOnTouchDownCallback();
        endRunningWindowAnim(false /* cancel */);

        if (mGestureEndCallback != null) {
            mGestureEndCallback.run();
        }

        mActivityInitListener.unregister();
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(
                mActivityRestartListener);
        mTaskSnapshotCache.clear();
    }

    private void invalidateHandlerWithLauncher() {
        endLauncherTransitionController();

        if (mRecentsView != null) {
            mRecentsView.onGestureAnimationEnd();
        }
        resetLauncherListeners();
    }

    private void endLauncherTransitionController() {
        mHasEndedLauncherTransition = true;

        if (mLauncherTransitionController != null) {
            // End the animation, but stay at the same visual progress.
            mLauncherTransitionController.getNormalController().dispatchSetInterpolator(
                    t -> Utilities.boundToRange(mCurrentShift.value, 0, 1));
            mLauncherTransitionController.getNormalController().getAnimationPlayer().end();
            mLauncherTransitionController = null;
        }

        if (mRecentsView != null) {
            mRecentsView.abortScrollerAnimation();
        }
    }

    /**
     * Unlike invalidateHandlerWithLauncher, this is called even when switching consumers, e.g. on
     * continued quick switch gesture, which cancels the previous handler but doesn't invalidate it.
     */
    private void resetLauncherListeners() {
        if (mActivity != null) {
            mActivity.removeEventCallback(EVENT_STARTED, mLauncherOnStartCallback);
            mActivity.removeEventCallback(EVENT_DESTROYED, mLauncherOnDestroyCallback);

            mActivity.getRootView().setOnApplyWindowInsetsListener(null);
        }
        if (mRecentsView != null) {
            mRecentsView.removeOnScrollChangedListener(mOnRecentsScrollListener);
        }
    }

    private void resetStateForAnimationCancel() {
        boolean wasVisible = mWasLauncherAlreadyVisible || mGestureStarted;
        mActivityInterface.onTransitionCancelled(wasVisible, mGestureState.getEndTarget());

        // Leave the pending invisible flag, as it may be used by wallpaper open animation.
        if (mActivity != null) {
            mActivity.clearForceInvisibleFlag(INVISIBLE_BY_STATE_HANDLER);
        }
    }

    protected void switchToScreenshot() {
        if (!hasTargets()) {
            // If there are no targets, then we don't need to capture anything
            mStateCallback.setStateOnUiThread(STATE_SCREENSHOT_CAPTURED);
        } else {
            boolean finishTransitionPosted = false;
            // If we already have cached screenshot(s) from running tasks, skip update
            boolean shouldUpdate = false;
            int[] runningTaskIds = mGestureState.getRunningTaskIds(mIsSwipeForSplit);
            for (int id : runningTaskIds) {
                if (!mTaskSnapshotCache.containsKey(id)) {
                    shouldUpdate = true;
                    break;
                }
            }

            if (mRecentsAnimationController != null) {
                // Update the screenshot of the task
                if (shouldUpdate) {
                    UI_HELPER_EXECUTOR.execute(() -> {
                        RecentsAnimationController recentsAnimationController =
                                mRecentsAnimationController;
                        if (recentsAnimationController == null) return;
                        for (int id : runningTaskIds) {
                            mTaskSnapshotCache.put(
                                    id, recentsAnimationController.screenshotTask(id));
                        }

                        MAIN_EXECUTOR.execute(() -> {
                            if (!updateThumbnail(false /* refreshView */)) {
                                setScreenshotCapturedState();
                            }
                        });
                    });
                    return;
                }

                finishTransitionPosted = updateThumbnail(false /* refreshView */);
            }

            if (!finishTransitionPosted) {
                setScreenshotCapturedState();
            }
        }
    }

    // Returns whether finish transition was posted.
    private boolean updateThumbnail(boolean refreshView) {
        if (mGestureState.getEndTarget() == HOME
                || mGestureState.getEndTarget() == NEW_TASK
                || mGestureState.getEndTarget() == ALL_APPS
                || mRecentsView == null) {
            // Capture the screenshot before finishing the transition to home or quickswitching to
            // ensure it's taken in the correct orientation, but no need to update the thumbnail.
            return false;
        }

        boolean finishTransitionPosted = false;
        TaskView updatedTaskView = mRecentsView.updateThumbnail(mTaskSnapshotCache, refreshView);
        if (updatedTaskView != null && refreshView && !mCanceled) {
            // Defer finishing the animation until the next launcher frame with the
            // new thumbnail
            finishTransitionPosted = ViewUtils.postFrameDrawn(updatedTaskView,
                    () -> mStateCallback.setStateOnUiThread(STATE_SCREENSHOT_CAPTURED),
                    this::isCanceled);
        }

        return finishTransitionPosted;
    }

    private void setScreenshotCapturedState() {
        // If we haven't posted a draw callback, set the state immediately.
        TraceHelper.INSTANCE.beginSection(SCREENSHOT_CAPTURED_EVT);
        mStateCallback.setStateOnUiThread(STATE_SCREENSHOT_CAPTURED);
        TraceHelper.INSTANCE.endSection();
    }

    private void finishCurrentTransitionToRecents() {
        mStateCallback.setStateOnUiThread(STATE_CURRENT_TASK_FINISHED);
        if (mRecentsAnimationController != null) {
            mRecentsAnimationController.detachNavigationBarFromApp(true);
        }
    }

    private void finishCurrentTransitionToHome() {
        if (!hasTargets() || mRecentsAnimationController == null) {
            // If there are no targets or the animation not started, then there is nothing to finish
            mStateCallback.setStateOnUiThread(STATE_CURRENT_TASK_FINISHED);
            maybeAbortSwipePipToHome();
        } else {
            maybeFinishSwipePipToHome();
            finishRecentsControllerToHome(
                    () -> mStateCallback.setStateOnUiThread(STATE_CURRENT_TASK_FINISHED));
        }
        if (mSwipePipToHomeReleaseCheck != null) {
            mSwipePipToHomeReleaseCheck.setCanRelease(true);
            mSwipePipToHomeReleaseCheck = null;
        }
        doLogGesture(HOME, mRecentsView == null ? null : mRecentsView.getCurrentPageTaskView());
    }

    /**
     * Notifies SysUI that transition is aborted if applicable and also pass leash transactions
     * from Launcher to WM.
     */
    private void maybeAbortSwipePipToHome() {
        if (mIsSwipingPipToHome && mSwipePipToHomeAnimator != null) {
            SystemUiProxy.INSTANCE.get(mContext).abortSwipePipToHome(
                    mSwipePipToHomeAnimator.getTaskId(),
                    mSwipePipToHomeAnimator.getComponentName());
            mIsSwipingPipToHome = false;
        }
    }

    /**
     * Notifies SysUI that transition is finished if applicable and also pass leash transactions
     * from Launcher to WM.
     * This should happen before {@link #finishRecentsControllerToHome(Runnable)}.
     */
    private void maybeFinishSwipePipToHome() {
        if (mRecentsAnimationController == null) {
            return;
        }
        if (mIsSwipingPipToHome && mSwipePipToHomeAnimator != null) {
            mRecentsAnimationController.setFinishTaskTransaction(
                    mSwipePipToHomeAnimator.getTaskId(),
                    mSwipePipToHomeAnimator.getFinishTransaction(),
                    mSwipePipToHomeAnimator.getContentOverlay());
            mIsSwipingPipToHome = false;
        } else if (mIsSwipeForSplit) {
            // Transaction to hide the task to avoid flicker for entering PiP from split-screen.
            PictureInPictureSurfaceTransaction tx =
                    new PictureInPictureSurfaceTransaction.Builder()
                            .setAlpha(0f)
                            .build();
            tx.setShouldDisableCanAffectSystemUiFlags(false);
            int[] taskIds = TopTaskTracker.INSTANCE.get(mContext).getRunningSplitTaskIds();
            for (int taskId : taskIds) {
                mRecentsAnimationController.setFinishTaskTransaction(taskId,
                        tx, null /* overlay */);
            }
        }
    }

    protected abstract void finishRecentsControllerToHome(Runnable callback);

    private void setupLauncherUiAfterSwipeUpToRecentsAnimation() {
        if (mStateCallback.hasStates(STATE_HANDLER_INVALIDATED) || mRecentsView == null) {
            return;
        }
        endLauncherTransitionController();
        mRecentsView.onSwipeUpAnimationSuccess();
        mTaskAnimationManager.setLiveTileCleanUpHandler(() -> {
            mRecentsView.cleanupRemoteTargets();
            mInputConsumerProxy.destroy();
        });
        mTaskAnimationManager.enableLiveTileRestartListener();

        SystemUiProxy.INSTANCE.get(mContext).onOverviewShown(false, TAG);
        doLogGesture(RECENTS, mRecentsView.getCurrentPageTaskView());
        reset();
    }

    private static boolean isNotInRecents(RemoteAnimationTarget app) {
        return app.isNotInRecents
                || app.windowConfiguration.getActivityType() == ACTIVITY_TYPE_HOME;
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

    protected void linkRecentsViewScroll() {
        if (mRecentsView == null) {
            return;
        }
        SurfaceTransactionApplier applier = new SurfaceTransactionApplier(mRecentsView);
        runActionOnRemoteHandles(remoteTargetHandle -> remoteTargetHandle.getTransformParams()
                        .setSyncTransactionApplier(applier));
        runOnRecentsAnimationAndLauncherBound(() ->
                mRecentsAnimationTargets.addReleaseCheck(applier));

        mRecentsView.addOnScrollChangedListener(mOnRecentsScrollListener);
        runOnRecentsAnimationAndLauncherBound(() -> {
            if (mRecentsView == null) {
                return;
            }
            mRecentsView.setRecentsAnimationTargets(
                    mRecentsAnimationController, mRecentsAnimationTargets);
        });

        // Disable scrolling in RecentsView for trackpad 3-finger swipe up gesture.
        if (!mGestureState.isThreeFingerTrackpadGesture()) {
            mRecentsViewScrollLinked = true;
        }
    }

    private void onRecentsViewScroll() {
        if (moveWindowWithRecentsScroll()) {
            onCurrentShiftUpdated();
        }
    }

    protected void startNewTask(Consumer<Boolean> resultCallback) {
        // Launch the task user scrolled to (mRecentsView.getNextPage()).
        if (!mCanceled) {
            TaskView nextTask = mRecentsView == null ? null : mRecentsView.getNextPageTaskView();
            if (nextTask != null) {
                int[] taskIds = nextTask.getTaskIds();
                ActiveGestureLog.CompoundString nextTaskLog = new ActiveGestureLog.CompoundString(
                        "Launching task: ");
                for (TaskIdAttributeContainer c : nextTask.getTaskIdAttributeContainers()) {
                    if (c == null) {
                        continue;
                    }
                    nextTaskLog
                            .append("[id: ")
                            .append(c.getTask().key.id)
                            .append(", pkg: ")
                            .append(c.getTask().key.getPackageName())
                            .append("] | ");
                }
                mGestureState.updateLastStartedTaskIds(taskIds);
                boolean hasTaskPreviouslyAppeared = Arrays.stream(taskIds).anyMatch(
                                taskId -> mGestureState.getPreviouslyAppearedTaskIds()
                                        .contains(taskId));
                if (!hasTaskPreviouslyAppeared) {
                    ActiveGestureLog.INSTANCE.trackEvent(EXPECTING_TASK_APPEARED);
                }
                ActiveGestureLog.INSTANCE.addLog(nextTaskLog);
                nextTask.launchTask(success -> {
                    resultCallback.accept(success);
                    if (success) {
                        if (hasTaskPreviouslyAppeared) {
                            onRestartPreviouslyAppearedTask();
                        }
                    } else {
                        mActivityInterface.onLaunchTaskFailed();
                        if (mRecentsAnimationController != null) {
                            mRecentsAnimationController.finish(true /* toRecents */, null);
                        }
                    }
                }, true /* freezeTaskList */);
            } else {
                mActivityInterface.onLaunchTaskFailed();
                Toast.makeText(mContext, R.string.activity_not_available, LENGTH_SHORT).show();
                if (mRecentsAnimationController != null) {
                    mRecentsAnimationController.finish(true /* toRecents */, null);
                }
            }
        }
        mCanceled = false;
    }

    /**
     * Runs the given {@param action} if the recents animation has already started and Launcher has
     * been created and bound to the TouchInteractionService, or queues it to be run when it this
     * next happens.
     */
    private void runOnRecentsAnimationAndLauncherBound(Runnable action) {
        mRecentsAnimationStartCallbacks.add(action);
        flushOnRecentsAnimationAndLauncherBound();
    }

    private void flushOnRecentsAnimationAndLauncherBound() {
        if (mRecentsAnimationTargets == null ||
                !mStateCallback.hasStates(STATE_LAUNCHER_BIND_TO_SERVICE)) {
            return;
        }

        if (!mRecentsAnimationStartCallbacks.isEmpty()) {
            for (Runnable action : new ArrayList<>(mRecentsAnimationStartCallbacks)) {
                action.run();
            }
            mRecentsAnimationStartCallbacks.clear();
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
    public void onRecentsAnimationFinished(@NonNull RecentsAnimationController controller) {
        mRecentsAnimationController = null;
        mRecentsAnimationTargets = null;
        if (mRecentsView != null) {
            mRecentsView.setRecentsAnimationTargets(null, null);
        }
    }

    @Override
    public void onTasksAppeared(@NonNull RemoteAnimationTarget[] appearedTaskTargets) {
        if (mRecentsAnimationController == null) {
            return;
        }
        boolean hasStartedTaskBefore = Arrays.stream(appearedTaskTargets).anyMatch(
                mGestureState.mLastStartedTaskIdPredicate);
        if (!mStateCallback.hasStates(STATE_GESTURE_COMPLETED) && !hasStartedTaskBefore) {
            // This is a special case, if a task is started mid-gesture that wasn't a part of a
            // previous quickswitch task launch, then cancel the animation back to the app
            RemoteAnimationTarget appearedTaskTarget = appearedTaskTargets[0];
            TaskInfo taskInfo = appearedTaskTarget.taskInfo;
            ActiveGestureLog.INSTANCE.addLog(
                    new ActiveGestureLog.CompoundString("Unexpected task appeared")
                            .append(" id=")
                            .append(taskInfo.taskId)
                            .append(" pkg=")
                            .append(taskInfo.baseIntent.getComponent().getPackageName()));
            finishRecentsAnimationOnTasksAppeared(null /* onFinishComplete */);
            return;
        }
        if (!handleTaskAppeared(appearedTaskTargets)) {
            return;
        }
        Optional<RemoteAnimationTarget> taskTargetOptional =
                Arrays.stream(appearedTaskTargets)
                        .filter(mGestureState.mLastStartedTaskIdPredicate)
                        .findFirst();
        if (!taskTargetOptional.isPresent()) {
            ActiveGestureLog.INSTANCE.addLog("No appeared task matching started task id");
            finishRecentsAnimationOnTasksAppeared(null /* onFinishComplete */);
            return;
        }
        RemoteAnimationTarget taskTarget = taskTargetOptional.get();
        TaskView taskView = mRecentsView == null
                ? null : mRecentsView.getTaskViewByTaskId(taskTarget.taskId);
        if (taskView == null || !taskView.getThumbnail().shouldShowSplashView()) {
            ActiveGestureLog.INSTANCE.addLog("Invalid task view splash state");
            finishRecentsAnimationOnTasksAppeared(null /* onFinishComplete */);
            return;
        }
        if (mActivity == null) {
            ActiveGestureLog.INSTANCE.addLog("Activity destroyed");
            finishRecentsAnimationOnTasksAppeared(null /* onFinishComplete */);
            return;
        }
        animateSplashScreenExit(mActivity, appearedTaskTargets, taskTarget.leash);
    }

    private void animateSplashScreenExit(
            @NonNull T activity,
            @NonNull RemoteAnimationTarget[] appearedTaskTargets,
            @NonNull SurfaceControl leash) {
        ViewGroup splashView = activity.getDragLayer();
        final QuickstepLauncher quickstepLauncher = activity instanceof QuickstepLauncher
                ? (QuickstepLauncher) activity : null;
        if (quickstepLauncher != null) {
            quickstepLauncher.getDepthController().pauseBlursOnWindows(true);
        }

        // When revealing the app with launcher splash screen, make the app visible
        // and behind the splash view before the splash is animated away.
        SurfaceTransactionApplier surfaceApplier =
                new SurfaceTransactionApplier(splashView);
        SurfaceTransaction transaction = new SurfaceTransaction();
        for (RemoteAnimationTarget target : appearedTaskTargets) {
            transaction.forSurface(target.leash).setAlpha(1).setLayer(-1).setShow();
        }
        surfaceApplier.scheduleApply(transaction);

        SplashScreenExitAnimationUtils.startAnimations(splashView, leash,
                mSplashMainWindowShiftLength, new TransactionPool(), new Rect(),
                SPLASH_ANIMATION_DURATION, SPLASH_FADE_OUT_DURATION,
                /* iconStartAlpha= */ 0, /* brandingStartAlpha= */ 0,
                SPLASH_APP_REVEAL_DELAY, SPLASH_APP_REVEAL_DURATION,
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // Hiding launcher which shows the app surface behind, then
                        // finishing recents to the app. After transition finish, showing
                        // the views on launcher again, so it can be visible when next
                        // animation starts.
                        splashView.setAlpha(0);
                        if (quickstepLauncher != null) {
                            quickstepLauncher.getDepthController()
                                    .pauseBlursOnWindows(false);
                        }
                        finishRecentsAnimationOnTasksAppeared(() -> splashView.setAlpha(1));
                    }
                });
    }

    private void finishRecentsAnimationOnTasksAppeared(Runnable onFinishComplete) {
        if (mRecentsAnimationController != null) {
            mRecentsAnimationController.finish(false /* toRecents */, onFinishComplete);
        }
        ActiveGestureLog.INSTANCE.addLog("finishRecentsAnimationOnTasksAppeared");
    }

    /**
     * @return The index of the TaskView in RecentsView whose taskId matches the task that will
     * resume if we finish the controller.
     */
    protected int getLastAppearedTaskIndex() {
        if (mRecentsView == null) {
            return -1;
        }

        OptionalInt firstValidTaskId = Arrays.stream(mGestureState.getLastAppearedTaskIds())
                .filter(i -> i != -1)
                .findFirst();
        return firstValidTaskId.isPresent()
                ? mRecentsView.getTaskIndexForId(firstValidTaskId.getAsInt())
                : mRecentsView.getRunningTaskIndex();
    }

    /**
     * @return Whether we are continuing a gesture that already landed on a new task,
     * but before that task appeared.
     */
    protected boolean hasStartedNewTask() {
        return mGestureState.getLastStartedTaskIds()[0] != -1;
    }

    /**
     * Registers a callback to run when the activity is ready.
     */
    public void initWhenReady() {
        // Preload the plan
        RecentsModel.INSTANCE.get(mContext).getTasks(null);

        mActivityInitListener.register();
    }

    /**
     * Applies the transform on the recents animation
     */
    protected void applyScrollAndTransform() {
        // No need to apply any transform if there is ongoing swipe-to-home animator
        //    swipe-to-pip handles the leash solely
        //    swipe-to-icon animation is handled by RectFSpringAnim anim
        boolean notSwipingToHome = mRecentsAnimationTargets != null
                && mGestureState.getEndTarget() != HOME;
        boolean setRecentsScroll = mRecentsViewScrollLinked && mRecentsView != null;
        float progress = Math.max(mCurrentShift.value, getScaleProgressDueToScroll());
        int scrollOffset = setRecentsScroll ? mRecentsView.getScrollOffset() : 0;
        if (!mStartMovingTasks && (progress > 0 || scrollOffset != 0)) {
            mStartMovingTasks = true;
            startInterceptingTouchesForGesture();
        }
        for (RemoteTargetHandle remoteHandle : mRemoteTargetHandles) {
            AnimatorControllerWithResistance playbackController =
                    remoteHandle.getPlaybackController();
            if (playbackController != null) {
                playbackController.setProgress(progress, mDragLengthFactor);
            }

            if (notSwipingToHome) {
                TaskViewSimulator taskViewSimulator = remoteHandle.getTaskViewSimulator();
                if (setRecentsScroll) {
                    taskViewSimulator.setScroll(scrollOffset);
                }
                taskViewSimulator.apply(remoteHandle.getTransformParams());
            }
        }
    }

    // Scaling of RecentsView during quick switch based on amount of recents scroll
    private float getScaleProgressDueToScroll() {
        if (mActivity == null || !mActivity.getDeviceProfile().isTablet || mRecentsView == null
                || !mRecentsViewScrollLinked) {
            return 0;
        }

        float scrollOffset = Math.abs(mRecentsView.getScrollOffset(mRecentsView.getCurrentPage()));
        int maxScrollOffset = mRecentsView.getPagedOrientationHandler().getPrimaryValue(
                mRecentsView.getLastComputedTaskSize().width(),
                mRecentsView.getLastComputedTaskSize().height());
        maxScrollOffset += mRecentsView.getPageSpacing();

        float maxScaleProgress =
                MAX_QUICK_SWITCH_RECENTS_SCALE_PROGRESS * mRecentsView.getMaxScaleForFullScreen();
        float scaleProgress = maxScaleProgress;

        if (scrollOffset < mQuickSwitchScaleScrollThreshold) {
            scaleProgress = Utilities.mapToRange(scrollOffset, 0, mQuickSwitchScaleScrollThreshold,
                    0, maxScaleProgress, ACCELERATE_DECELERATE);
        } else if (scrollOffset > (maxScrollOffset - mQuickSwitchScaleScrollThreshold)) {
            scaleProgress = Utilities.mapToRange(scrollOffset,
                    (maxScrollOffset - mQuickSwitchScaleScrollThreshold), maxScrollOffset,
                    maxScaleProgress, 0, ACCELERATE_DECELERATE);
        }

        return scaleProgress;
    }

    /**
     * Overrides the gesture displacement to keep the app window at the bottom of the screen while
     * the transient taskbar is being swiped in.
     *
     * There is also a catch up period so that the window can start moving 1:1 with the swipe.
     */
    @Override
    protected float overrideDisplacementForTransientTaskbar(float displacement) {
        if (!mIsTransientTaskbar) {
            return displacement;
        }

        if (mTaskbarAlreadyOpen || mIsTaskbarAllAppsOpen || mGestureState.isTrackpadGesture()) {
            return displacement;
        }

        if (displacement < mTaskbarAppWindowThreshold) {
            return 0;
        }

        // "Catch up" with the displacement at mTaskbarCatchUpThreshold.
        if (displacement < mTaskbarCatchUpThreshold) {
            return Utilities.mapToRange(displacement, mTaskbarAppWindowThreshold,
                    mTaskbarCatchUpThreshold, 0, mTaskbarCatchUpThreshold, ACCELERATE_DECELERATE);
        }

        return displacement;
    }

    private void setDividerShown(boolean shown) {
        if (mRecentsAnimationTargets == null || mIsDividerShown == shown) {
            return;
        }
        mIsDividerShown = shown;
        TaskViewUtils.createSplitAuxiliarySurfacesAnimator(
                mRecentsAnimationTargets.nonApps, shown, null /* animatorHandler */);
    }

    public interface Factory {
        AbsSwipeUpHandler newHandler(GestureState gestureState, long touchTimeMs);
    }
}
