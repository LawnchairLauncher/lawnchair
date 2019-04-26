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
import static com.android.launcher3.Utilities.SINGLE_FRAME_MS;
import static com.android.launcher3.Utilities.postAsyncCallback;
import static com.android.launcher3.anim.Interpolators.DEACCEL;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.Interpolators.OVERSHOOT_1_2;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.launcher3.config.FeatureFlags.QUICKSTEP_SPRINGS;
import static com.android.launcher3.util.RaceConditionTracker.ENTER;
import static com.android.launcher3.util.RaceConditionTracker.EXIT;
import static com.android.launcher3.views.FloatingIconView.SHAPE_PROGRESS_DURATION;
import static com.android.quickstep.ActivityControlHelper.AnimationFactory.ShelfAnimState.HIDE;
import static com.android.quickstep.ActivityControlHelper.AnimationFactory.ShelfAnimState.PEEK;
import static com.android.quickstep.MultiStateCallback.DEBUG_STATES;
import static com.android.quickstep.TouchInteractionService.MAIN_THREAD_EXECUTOR;
import static com.android.quickstep.TouchInteractionService.TOUCH_INTERACTION_LOG;
import static com.android.quickstep.WindowTransformSwipeHandler.GestureEndTarget.HOME;
import static com.android.quickstep.WindowTransformSwipeHandler.GestureEndTarget.LAST_TASK;
import static com.android.quickstep.WindowTransformSwipeHandler.GestureEndTarget.NEW_TASK;
import static com.android.quickstep.WindowTransformSwipeHandler.GestureEndTarget.RECENTS;
import static com.android.quickstep.views.RecentsView.UPDATE_SYSUI_FLAGS_THRESHOLD;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnApplyWindowInsetsListener;
import android.view.ViewTreeObserver.OnDrawListener;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
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
import com.android.launcher3.views.FloatingIconView;
import com.android.quickstep.ActivityControlHelper.ActivityInitListener;
import com.android.quickstep.ActivityControlHelper.AnimationFactory;
import com.android.quickstep.ActivityControlHelper.AnimationFactory.ShelfAnimState;
import com.android.quickstep.ActivityControlHelper.HomeAnimationFactory;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.util.ClipAnimationHelper;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.RemoteAnimationTargetSet;
import com.android.quickstep.util.SwipeAnimationTargetSet;
import com.android.quickstep.util.SwipeAnimationTargetSet.SwipeAnimationListener;
import com.android.quickstep.views.LiveTileOverlay;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.LatencyTrackerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat;
import com.android.systemui.shared.system.WindowCallbacksCompat;

import java.util.function.BiFunction;
import java.util.function.Consumer;

@TargetApi(Build.VERSION_CODES.O)
public class WindowTransformSwipeHandler<T extends BaseDraggingActivity>
        implements SwipeAnimationListener, OnApplyWindowInsetsListener {
    private static final String TAG = WindowTransformSwipeHandler.class.getSimpleName();

    private static final Rect TEMP_RECT = new Rect();

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

    enum GestureEndTarget {
        HOME(1, STATE_SCALED_CONTROLLER_HOME, true, false, ContainerType.WORKSPACE, false),

        RECENTS(1, STATE_SCALED_CONTROLLER_RECENTS | STATE_CAPTURE_SCREENSHOT
                | STATE_SCREENSHOT_VIEW_SHOWN, true, false, ContainerType.TASKSWITCHER, true),

        NEW_TASK(0, STATE_START_NEW_TASK, false, true, ContainerType.APP, true),

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

    private static final long SHELF_ANIM_DURATION = 120;
    public static final long RECENTS_ATTACH_DURATION = 300;

    /**
     * Used as the page index for logging when we return to the last task at the end of the gesture.
     */
    private static final int LOG_NO_OP_PAGE_INDEX = -1;

    private final ClipAnimationHelper mClipAnimationHelper;
    private final ClipAnimationHelper.TransformParams mTransformParams;

    protected Runnable mGestureEndCallback;
    protected GestureEndTarget mGestureEndTarget;
    private boolean mIsShelfPeeking;
    private DeviceProfile mDp;
    private int mTransitionDragLength;

    // Shift in the range of [0, 1].
    // 0 => preview snapShot is completely visible, and hotseat is completely translated down
    // 1 => preview snapShot is completely aligned with the recents view and hotseat is completely
    // visible.
    private final AnimatedFloat mCurrentShift = new AnimatedFloat(this::updateFinalShift);
    private boolean mContinuingLastGesture;
    // To avoid UI jump when gesture is started, we offset the animation by the threshold.
    private float mShiftAtGestureStart = 0;

    private final Handler mMainThreadHandler = MAIN_THREAD_EXECUTOR.getHandler();

    private final Context mContext;
    private final ActivityControlHelper<T> mActivityControlHelper;
    private final ActivityInitListener mActivityInitListener;

    private final SysUINavigationMode.Mode mMode;

    private final int mRunningTaskId;
    private ThumbnailData mTaskSnapshot;

    private MultiStateCallback mStateCallback;
    // Used to control launcher components throughout the swipe gesture.
    private AnimatorPlaybackController mLauncherTransitionController;

    private T mActivity;
    private RecentsView mRecentsView;
    private SyncRtSurfaceTransactionApplierCompat mSyncTransactionApplier;
    private AnimationFactory mAnimationFactory = (t) -> { };
    private LiveTileOverlay mLiveTileOverlay = new LiveTileOverlay();

    private boolean mWasLauncherAlreadyVisible;

    private boolean mPassedOverviewThreshold;
    private boolean mGestureStarted;
    private int mLogAction = Touch.SWIPE;
    private int mLogDirection = Direction.UP;
    private PointF mDownPos;
    private boolean mIsLikelyToStartNewTask;

    private final RecentsAnimationWrapper mRecentsAnimationWrapper;

    private final long mTouchTimeMs;
    private long mLauncherFrameDrawnTime;

    WindowTransformSwipeHandler(RunningTaskInfo runningTaskInfo, Context context,
            long touchTimeMs, ActivityControlHelper<T> controller, boolean continuingLastGesture,
            InputConsumerController inputConsumer) {
        mContext = context;
        mRunningTaskId = runningTaskInfo.id;
        mTouchTimeMs = touchTimeMs;
        mActivityControlHelper = controller;
        mActivityInitListener = mActivityControlHelper
                .createActivityInitListener(this::onActivityInit);
        mContinuingLastGesture = continuingLastGesture;
        mRecentsAnimationWrapper = new RecentsAnimationWrapper(inputConsumer,
                this::createNewInputProxyHandler);
        mClipAnimationHelper = new ClipAnimationHelper(context);
        mTransformParams = new ClipAnimationHelper.TransformParams();

        mMode = SysUINavigationMode.getMode(context);
        initStateCallbacks();

        DeviceProfile dp = InvariantDeviceProfile.INSTANCE.get(mContext).getDeviceProfile(mContext);
        initTransitionEndpoints(dp);
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
        mStateCallback.addCallback(STATE_START_NEW_TASK | STATE_APP_CONTROLLER_RECEIVED,
                this::startNewTask);

        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_APP_CONTROLLER_RECEIVED
                        | STATE_LAUNCHER_DRAWN | STATE_CAPTURE_SCREENSHOT,
                this::switchToScreenshot);

        mStateCallback.addCallback(STATE_SCREENSHOT_CAPTURED | STATE_GESTURE_COMPLETED
                        | STATE_SCALED_CONTROLLER_RECENTS,
                this::finishCurrentTransitionToRecents);

        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_GESTURE_COMPLETED
                        | STATE_SCALED_CONTROLLER_HOME | STATE_APP_CONTROLLER_RECEIVED
                        | STATE_LAUNCHER_DRAWN,
                this::finishCurrentTransitionToHome);
        mStateCallback.addCallback(STATE_SCALED_CONTROLLER_HOME | STATE_CURRENT_TASK_FINISHED,
                this::reset);

        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_APP_CONTROLLER_RECEIVED
                        | STATE_LAUNCHER_DRAWN | STATE_SCALED_CONTROLLER_RECENTS
                        | STATE_CURRENT_TASK_FINISHED | STATE_GESTURE_COMPLETED
                        | STATE_GESTURE_STARTED,
                this::setupLauncherUiAfterSwipeUpAnimation);

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

    private void setStateOnUiThread(int stateFlag) {
        if (Looper.myLooper() == mMainThreadHandler.getLooper()) {
            mStateCallback.setState(stateFlag);
        } else {
            postAsyncCallback(mMainThreadHandler, () -> mStateCallback.setState(stateFlag));
        }
    }

    private void initTransitionEndpoints(DeviceProfile dp) {
        mDp = dp;

        Rect tempRect = new Rect();
        mTransitionDragLength = mActivityControlHelper.getSwipeUpDestinationAndLength(
                dp, mContext, tempRect);
        mClipAnimationHelper.updateTargetRect(tempRect);
    }

    private long getFadeInDuration() {
        if (mCurrentShift.getCurrentAnimation() != null) {
            ObjectAnimator anim = mCurrentShift.getCurrentAnimation();
            long theirDuration = anim.getDuration() - anim.getCurrentPlayTime();

            // TODO: Find a better heuristic
            return Math.min(MAX_SWIPE_DURATION, Math.max(theirDuration, MIN_SWIPE_DURATION));
        } else {
            return MAX_SWIPE_DURATION;
        }
    }

    public void initWhenReady() {
        mActivityInitListener.register();
    }

    private boolean onActivityInit(final T activity, Boolean alreadyOnHome) {
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
        SyncRtSurfaceTransactionApplierCompat.create(mRecentsView,
                applier ->  mSyncTransactionApplier = applier );
        mRecentsView.setEnableFreeScroll(false);

        mRecentsView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (mGestureEndTarget != HOME) {
                updateFinalShift();
            }
        });
        mRecentsView.setRecentsAnimationWrapper(mRecentsAnimationWrapper);
        mRecentsView.setClipAnimationHelper(mClipAnimationHelper);
        mRecentsView.setLiveTileOverlay(mLiveTileOverlay);
        mActivity.getRootView().getOverlay().add(mLiveTileOverlay);

        mStateCallback.setState(STATE_LAUNCHER_PRESENT);
        if (alreadyOnHome) {
            onLauncherStart(activity);
        } else {
            activity.setOnStartCallback(this::onLauncherStart);
        }

        setupRecentsViewUi();
        return true;
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
        AbstractFloatingView.closeAllOpenViews(activity, mWasLauncherAlreadyVisible);

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
            return;
        }
        mRecentsView.setEnableDrawingLiveTile(false);
        mRecentsView.showTask(mRunningTaskId);
        mRecentsView.setRunningTaskHidden(true);
        mRecentsView.setRunningTaskIconScaledDown(true);
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

    public Consumer<MotionEvent> getRecentsViewDispatcher() {
        return mRecentsView != null ? mRecentsView::dispatchTouchEvent : null;
    }

    @UiThread
    public void updateDisplacement(float displacement) {
        // We are moving in the negative x/y direction
        displacement = -displacement;
        if (displacement > mTransitionDragLength && mTransitionDragLength > 0) {
            mCurrentShift.updateValue(1);
        } else {
            float translation = Math.max(displacement, 0);
            float shift = mTransitionDragLength == 0 ? 0 : translation / mTransitionDragLength;
            mCurrentShift.updateValue(shift);
        }
    }

    public void onMotionPauseChanged(boolean isPaused) {
        setShelfState(isPaused ? PEEK : HIDE, FAST_OUT_SLOW_IN, SHELF_ANIM_DURATION);
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
        if (mContinuingLastGesture) {
            recentsAttachedToAppWindow = true;
            animate = false;
        } else if (runningTaskTarget != null && isNotInRecents(runningTaskTarget)) {
            // The window is going away so make sure recents is always visible in this case.
            recentsAttachedToAppWindow = true;
            animate = false;
        } else {
            if (mGestureEndTarget != null) {
                recentsAttachedToAppWindow = mGestureEndTarget.recentsAttachedToAppWindow;
            } else {
                recentsAttachedToAppWindow = mIsShelfPeeking || mIsLikelyToStartNewTask;
            }
            if (animate) {
                // Only animate if an adjacent task view is visible on screen.
                TaskView adjacentTask1 = mRecentsView.getTaskViewAt(runningTaskIndex + 1);
                TaskView adjacentTask2 = mRecentsView.getTaskViewAt(runningTaskIndex - 1);
                animate = (adjacentTask1 != null && adjacentTask1.getGlobalVisibleRect(TEMP_RECT))
                        || (adjacentTask2 != null && adjacentTask2.getGlobalVisibleRect(TEMP_RECT));
            }
        }
        mAnimationFactory.setRecentsAttachedToAppWindow(recentsAttachedToAppWindow, animate);
    }

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
        if (mRecentsView != null && shelfState.shouldPreformHaptic) {
            mRecentsView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        }
    }

    private void buildAnimationController() {
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
        mLauncherTransitionController.dispatchOnStart();
        updateLauncherTransitionProgress();
    }

    @UiThread
    private void updateFinalShift() {
        float shift = mCurrentShift.value;

        SwipeAnimationTargetSet controller = mRecentsAnimationWrapper.getController();
        if (controller != null) {
            float offsetX = 0;
            if (mRecentsView != null) {
                int startScroll = mRecentsView.getScrollForPage(mRecentsView.indexOfChild(
                        mRecentsView.getRunningTaskView()));
                offsetX = startScroll - mRecentsView.getScrollX();
                offsetX *= mRecentsView.getScaleX();
            }
            float offsetScale = getTaskCurveScaleForOffsetX(offsetX,
                    mClipAnimationHelper.getTargetRect().width());
            mTransformParams.setProgress(shift).setOffsetX(offsetX).setOffsetScale(offsetScale)
                    .setSyncTransactionApplier(mSyncTransactionApplier);
            mClipAnimationHelper.applyTransform(mRecentsAnimationWrapper.targetSet,
                    mTransformParams);
            mRecentsAnimationWrapper.setWindowThresholdCrossed(
                    shift > 1 - UPDATE_SYSUI_FLAGS_THRESHOLD);
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
            if (mRecentsView != null && mMode != Mode.NO_BUTTON) {
                mRecentsView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
            }
        }
        // Update insets of the non-running tasks, as we might switch to them.
        int runningTaskIndex = mRecentsView == null ? -1 : mRecentsView.getRunningTaskIndex();
        if (runningTaskIndex >= 0) {
            for (int i = 0; i < mRecentsView.getTaskViewCount(); i++) {
                if (i != runningTaskIndex) {
                    mRecentsView.getTaskViewAt(i).setFullscreenProgress(1 - mCurrentShift.value);
                }
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
        float progress = mCurrentShift.value;
        mLauncherTransitionController.setPlayFraction(
                progress <= mShiftAtGestureStart || mShiftAtGestureStart >= 1
                        ? 0 : (progress - mShiftAtGestureStart) / (1 - mShiftAtGestureStart));
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
                    targetSet.minimizedHomeBounds.width(), targetSet.minimizedHomeBounds.height()));
            dp.updateInsets(targetSet.homeContentInsets);
        } else {
            if (mActivity != null) {
                int loc[] = new int[2];
                View rootView = mActivity.getRootView();
                rootView.getLocationOnScreen(loc);
                overviewStackBounds = new Rect(loc[0], loc[1], loc[0] + rootView.getWidth(),
                        loc[1] + rootView.getHeight());
            } else {
                overviewStackBounds = new Rect(0, 0, dp.widthPx, dp.heightPx);
            }
            // If we are not in multi-window mode, home insets should be same as system insets.
            dp = dp.copy(mContext);
            dp.updateInsets(targetSet.homeContentInsets);
        }
        dp.updateIsSeascape(mContext.getSystemService(WindowManager.class));

        if (runningTaskTarget != null) {
            mClipAnimationHelper.updateSource(overviewStackBounds, runningTaskTarget);
        }
        mClipAnimationHelper.prepareAnimation(false /* isOpening */);
        initTransitionEndpoints(dp);

        mRecentsAnimationWrapper.setController(targetSet);
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

    @UiThread
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
     * @param endVelocity The velocity in the direction of the nav bar to the middle of the screen.
     * @param velocity The x and y components of the velocity when the gesture ends.
     * @param downPos The x and y value of where the gesture started.
     */
    @UiThread
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
        handleNormalGestureEnd(endVelocity, isFling, velocity);
    }

    @UiThread
    private InputConsumer createNewInputProxyHandler() {
        mCurrentShift.finishAnimation();
        if (mLauncherTransitionController != null) {
            mLauncherTransitionController.getAnimationPlayer().end();
        }
        if (!ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            // Hide the task view, if not already hidden
            setTargetAlphaProvider(WindowTransformSwipeHandler::getHiddenTargetAlpha);
        }

        return OverviewInputConsumer.newInstance(mActivityControlHelper, true);
    }

    @UiThread
    private void handleNormalGestureEnd(float endVelocity, boolean isFling, PointF velocity) {
        PointF velocityPxPerMs = new PointF(velocity.x / 1000, velocity.y / 1000);
        long duration = MAX_SWIPE_DURATION;
        float currentShift = mCurrentShift.value;
        final GestureEndTarget endTarget;
        float endShift;
        final float startShift;
        Interpolator interpolator = DEACCEL;
        final boolean goingToNewTask;
        if (mRecentsView != null) {
            final int runningTaskIndex = mRecentsView.getRunningTaskIndex();
            final int taskToLaunch = mRecentsView.getNextPage();
            goingToNewTask = runningTaskIndex >= 0 && taskToLaunch != runningTaskIndex;
        } else {
            goingToNewTask = false;
        }
        final boolean reachedOverviewThreshold = currentShift >= MIN_PROGRESS_FOR_OVERVIEW;
        if (!isFling) {
            if (mMode == Mode.NO_BUTTON) {
                if (mIsShelfPeeking) {
                    endTarget = RECENTS;
                } else if (goingToNewTask) {
                    endTarget = NEW_TASK;
                } else {
                    endTarget = currentShift < MIN_PROGRESS_FOR_OVERVIEW ? LAST_TASK : HOME;
                }
            } else {
                endTarget = reachedOverviewThreshold && mGestureStarted
                        ? RECENTS
                        : goingToNewTask
                                ? NEW_TASK
                                : LAST_TASK;
            }
            endShift = endTarget.endShift;
            long expectedDuration = Math.abs(Math.round((endShift - currentShift)
                    * MAX_SWIPE_DURATION * SWIPE_DURATION_MULTIPLIER));
            duration = Math.min(MAX_SWIPE_DURATION, expectedDuration);
            startShift = currentShift;
            interpolator = endTarget == RECENTS ? OVERSHOOT_1_2 : DEACCEL;
        } else {
            if (mMode == Mode.NO_BUTTON && endVelocity < 0 && !mIsShelfPeeking) {
                // If swiping at a diagonal, base end target on the faster velocity.
                endTarget = goingToNewTask && Math.abs(velocity.x) > Math.abs(endVelocity)
                        ? NEW_TASK : HOME;
            } else if (endVelocity < 0 && (!goingToNewTask || reachedOverviewThreshold)) {
                // If user scrolled to a new task, only go to recents if they already passed
                // the overview threshold. Otherwise, we'll snap to the new task and launch it.
                endTarget = RECENTS;
            } else {
                endTarget = goingToNewTask ? NEW_TASK : LAST_TASK;
            }
            endShift = endTarget.endShift;
            startShift = Utilities.boundToRange(currentShift - velocityPxPerMs.y
                    * SINGLE_FRAME_MS / mTransitionDragLength, 0, 1);
            float minFlingVelocity = mContext.getResources()
                    .getDimension(R.dimen.quickstep_fling_min_velocity);
            if (Math.abs(endVelocity) > minFlingVelocity && mTransitionDragLength > 0) {
                if (endTarget == RECENTS) {
                    Interpolators.OvershootParams overshoot = new Interpolators.OvershootParams(
                            startShift, endShift, endShift, velocityPxPerMs.y,
                            mTransitionDragLength);
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
                }
            }
        }

        if (endTarget == HOME) {
            setShelfState(ShelfAnimState.CANCEL, LINEAR, 0);
            duration = Math.max(MIN_OVERSHOOT_DURATION, duration);
        } else if (endTarget == RECENTS) {
            mLiveTileOverlay.startIconAnimation();
            mRecentsAnimationWrapper.enableInputProxy();
            if (mRecentsView != null) {
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
        if (dp == null) {
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
                    setStateOnUiThread(target.endState);
                }
            });
            windowAnim.start();
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
            mLauncherTransitionController.getAnimationPlayer().end();
        } else {
            mLauncherTransitionController.dispatchSetInterpolator(adjustedInterpolator);
            mLauncherTransitionController.getAnimationPlayer().setDuration(duration);

            if (QUICKSTEP_SPRINGS.get()) {
                mLauncherTransitionController.dispatchOnStartWithVelocity(end, velocityPxPerMs.y);
            }
            mLauncherTransitionController.getAnimationPlayer().start();
        }
    }

    /**
     * Creates an animation that transforms the current app window into the home app.
     * @param startProgress The progress of {@link #mCurrentShift} to start the window from.
     * @param homeAnimationFactory The home animation factory.
     */
    private RectFSpringAnim createWindowAnimationToHome(float startProgress,
            HomeAnimationFactory homeAnimationFactory) {
        final RemoteAnimationTargetSet targetSet = mRecentsAnimationWrapper.targetSet;
        final RectF startRect = new RectF(mClipAnimationHelper.applyTransform(targetSet,
                mTransformParams.setProgress(startProgress), false /* launcherOnTop */));
        final RectF targetRect = homeAnimationFactory.getWindowTargetRect();

        final View floatingView = homeAnimationFactory.getFloatingView();
        final boolean isFloatingIconView = floatingView instanceof FloatingIconView;

        RectFSpringAnim anim = new RectFSpringAnim(startRect, targetRect);
        if (isFloatingIconView) {
            anim.addAnimatorListener((FloatingIconView) floatingView);
        }

        AnimatorPlaybackController homeAnim = homeAnimationFactory.createActivityAnimationToHome();

        // We want the window alpha to be 0 once this threshold is met, so that the
        // FolderIconView can be seen morphing into the icon shape.
        final float windowAlphaThreshold = isFloatingIconView ? 1f - SHAPE_PROGRESS_DURATION : 1f;
        anim.addOnUpdateListener((currentRect, progress) -> {
            float interpolatedProgress = Interpolators.ACCEL_1_5.getInterpolation(progress);

            homeAnim.setPlayFraction(progress);

            float iconAlpha = Utilities.mapToRange(interpolatedProgress, 0,
                    windowAlphaThreshold, 0f, 1f, Interpolators.LINEAR);
            mTransformParams.setCurrentRectAndTargetAlpha(currentRect, 1f - iconAlpha)
                    .setSyncTransactionApplier(mSyncTransactionApplier);
            mClipAnimationHelper.applyTransform(targetSet, mTransformParams,
                    false /* launcherOnTop */);

            if (isFloatingIconView) {
                ((FloatingIconView) floatingView).update(currentRect, iconAlpha, progress,
                        windowAlphaThreshold, mClipAnimationHelper.getCurrentCornerRadius(), false);
            }

        });
        anim.addAnimatorListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                homeAnim.dispatchOnStart();
                mActivity.getRootView().getOverlay().remove(mLiveTileOverlay);
            }

            @Override
            public void onAnimationSuccess(Animator animator) {
                homeAnim.getAnimationPlayer().end();
                if (mRecentsView != null) {
                    mRecentsView.post(mRecentsView::resetTaskVisuals);
                }
            }
        });
        return anim;
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
        // Launch the task user scrolled to (mRecentsView.getNextPage()).
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            // We finish recents animation inside launchTask() when live tile is enabled.
            mRecentsView.getTaskViewAt(mRecentsView.getNextPage()).launchTask(false /* animate */,
                    true /* freezeTaskList */);
        } else {
            mRecentsAnimationWrapper.finish(true /* toRecents */, () -> {
                mRecentsView.getTaskViewAt(mRecentsView.getNextPage()).launchTask(
                        false /* animate */, true /* freezeTaskList */);
            });
        }
        TOUCH_INTERACTION_LOG.addLog("finishRecentsAnimation", false);
        doLogGesture(NEW_TASK);
        reset();
    }

    public void reset() {
        setStateOnUiThread(STATE_HANDLER_INVALIDATED);
    }

    public void cancel() {
        mCurrentShift.cancelAnimation();
        if (mLauncherTransitionController != null && mLauncherTransitionController
                .getAnimationPlayer().isStarted()) {
            mLauncherTransitionController.getAnimationPlayer().cancel();
        }
    }

    private void invalidateHandler() {
        mCurrentShift.finishAnimation();

        if (mGestureEndCallback != null) {
            mGestureEndCallback.run();
        }

        mActivityInitListener.unregister();
        mTaskSnapshot = null;
    }

    private void invalidateHandlerWithLauncher() {
        if (mLauncherTransitionController != null) {
            if (mLauncherTransitionController.getAnimationPlayer().isStarted()) {
                mLauncherTransitionController.getAnimationPlayer().cancel();
            }
            mLauncherTransitionController = null;
        }

        mRecentsView.setEnableFreeScroll(true);
        mRecentsView.setRunningTaskIconScaledDown(false);
        mRecentsView.setOnScrollChangeListener(null);
        mRecentsView.setRunningTaskHidden(false);
        mRecentsView.setEnableDrawingLiveTile(true);

        mActivity.getRootView().setOnApplyWindowInsetsListener(null);
        mActivity.getRootView().getOverlay().remove(mLiveTileOverlay);
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
        } else {
            boolean finishTransitionPosted = false;
            SwipeAnimationTargetSet controller = mRecentsAnimationWrapper.getController();
            if (controller != null) {
                // Update the screenshot of the task
                if (mTaskSnapshot == null) {
                    mTaskSnapshot = controller.screenshotTask(mRunningTaskId);
                }
                TaskView taskView = mRecentsView.updateThumbnail(mRunningTaskId, mTaskSnapshot);
                if (taskView != null) {
                    // Defer finishing the animation until the next launcher frame with the
                    // new thumbnail
                    finishTransitionPosted = new WindowCallbacksCompat(taskView) {

                        // The number of frames to defer until we actually finish the animation
                        private int mDeferFrameCount = 2;

                        @Override
                        public void onPostDraw(Canvas canvas) {
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

    private void setupLauncherUiAfterSwipeUpAnimation() {
        if (mLauncherTransitionController != null) {
            mLauncherTransitionController.getAnimationPlayer().end();
            mLauncherTransitionController = null;
        }
        mActivityControlHelper.onSwipeUpComplete(mActivity);
        mRecentsAnimationWrapper.setCancelWithDeferredScreenshot(true);

        // Animate the first icon.
        mRecentsView.animateUpRunningTaskIconScale(mLiveTileOverlay.cancelIconAnimation());
        mRecentsView.setSwipeDownShouldLaunchApp(true);

        RecentsModel.INSTANCE.get(mContext).onOverviewShown(false, TAG);

        doLogGesture(RECENTS);
        reset();
    }

    public void setGestureEndCallback(Runnable gestureEndCallback) {
        mGestureEndCallback = gestureEndCallback;
    }

    private void setTargetAlphaProvider(
            BiFunction<RemoteAnimationTargetCompat, Float, Float> provider) {
        mClipAnimationHelper.setTaskAlphaCallback(provider);
        updateFinalShift();
    }

    public static float getHiddenTargetAlpha(RemoteAnimationTargetCompat app, Float expectedAlpha) {
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
