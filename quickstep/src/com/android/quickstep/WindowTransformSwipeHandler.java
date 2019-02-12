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
import static com.android.launcher3.config.FeatureFlags.SWIPE_HOME;
import static com.android.quickstep.ActivityControlHelper.AnimationFactory.ShelfAnimState.HIDE;
import static com.android.quickstep.ActivityControlHelper.AnimationFactory.ShelfAnimState.PEEK;
import static com.android.quickstep.QuickScrubController.QUICK_SCRUB_FROM_APP_START_DURATION;
import static com.android.quickstep.QuickScrubController.QUICK_SWITCH_FROM_APP_START_DURATION;
import static com.android.quickstep.TouchConsumer.INTERACTION_NORMAL;
import static com.android.quickstep.TouchConsumer.INTERACTION_QUICK_SCRUB;
import static com.android.quickstep.TouchInteractionService.MAIN_THREAD_EXECUTOR;
import static com.android.quickstep.WindowTransformSwipeHandler.GestureEndTarget.HOME;
import static com.android.quickstep.WindowTransformSwipeHandler.GestureEndTarget.LAST_TASK;
import static com.android.quickstep.WindowTransformSwipeHandler.GestureEndTarget.NEW_TASK;
import static com.android.quickstep.WindowTransformSwipeHandler.GestureEndTarget.RECENTS;
import static com.android.quickstep.views.RecentsView.UPDATE_SYSUI_FLAGS_THRESHOLD;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver.OnDrawListener;
import android.view.WindowManager;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.MultiValueAlpha.AlphaProperty;
import com.android.launcher3.util.RaceConditionTracker;
import com.android.launcher3.util.TraceHelper;
import com.android.quickstep.ActivityControlHelper.ActivityInitListener;
import com.android.quickstep.ActivityControlHelper.AnimationFactory;
import com.android.quickstep.ActivityControlHelper.AnimationFactory.ShelfAnimState;
import com.android.quickstep.ActivityControlHelper.LayoutListener;
import com.android.quickstep.TouchConsumer.InteractionType;
import com.android.quickstep.TouchInteractionService.OverviewTouchConsumer;
import com.android.quickstep.util.ClipAnimationHelper;
import com.android.quickstep.util.RemoteAnimationTargetSet;
import com.android.quickstep.util.SwipeAnimationTargetSet;
import com.android.quickstep.util.SwipeAnimationTargetSet.SwipeAnimationListener;
import com.android.quickstep.util.TransformedRect;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.recents.utilities.RectFEvaluator;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.LatencyTrackerCompat;
import com.android.systemui.shared.system.RecentsAnimationControllerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat;
import com.android.systemui.shared.system.WindowCallbacksCompat;

import java.util.function.BiFunction;

@TargetApi(Build.VERSION_CODES.O)
public class WindowTransformSwipeHandler<T extends BaseDraggingActivity>
        implements SwipeAnimationListener {
    private static final String TAG = WindowTransformSwipeHandler.class.getSimpleName();

    // Launcher UI related states
    private static final int STATE_LAUNCHER_PRESENT = 1 << 0;
    private static final int STATE_LAUNCHER_STARTED = 1 << 1;
    private static final int STATE_LAUNCHER_DRAWN = 1 << 2;
    private static final int STATE_ACTIVITY_MULTIPLIER_COMPLETE = 1 << 3;

    // Internal initialization states
    private static final int STATE_APP_CONTROLLER_RECEIVED = 1 << 4;

    // Interaction finish states
    private static final int STATE_SCALED_CONTROLLER_HOME = 1 << 5;
    private static final int STATE_SCALED_CONTROLLER_RECENTS = 1 << 6;
    private static final int STATE_SCALED_CONTROLLER_LAST_TASK = 1 << 7;

    private static final int STATE_HANDLER_INVALIDATED = 1 << 8;
    private static final int STATE_GESTURE_STARTED_QUICKSTEP = 1 << 9;
    private static final int STATE_GESTURE_STARTED_QUICKSCRUB = 1 << 10;
    private static final int STATE_GESTURE_CANCELLED = 1 << 11;
    private static final int STATE_GESTURE_COMPLETED = 1 << 12;

    // States for quick switch/scrub
    private static final int STATE_CURRENT_TASK_FINISHED = 1 << 13;
    private static final int STATE_QUICK_SCRUB_START = 1 << 14;
    private static final int STATE_QUICK_SCRUB_END = 1 << 15;

    private static final int STATE_CAPTURE_SCREENSHOT = 1 << 16;
    private static final int STATE_SCREENSHOT_CAPTURED = 1 << 17;
    private static final int STATE_SCREENSHOT_VIEW_SHOWN = 1 << 18;

    private static final int STATE_RESUME_LAST_TASK = 1 << 19;
    private static final int STATE_START_NEW_TASK = 1 << 20;
    private static final int STATE_ASSIST_DATA_RECEIVED = 1 << 21;


    private static final int LAUNCHER_UI_STATES =
            STATE_LAUNCHER_PRESENT | STATE_LAUNCHER_DRAWN | STATE_ACTIVITY_MULTIPLIER_COMPLETE
            | STATE_LAUNCHER_STARTED;

    private static final int LONG_SWIPE_ENTER_STATE =
            STATE_ACTIVITY_MULTIPLIER_COMPLETE | STATE_LAUNCHER_STARTED
                    | STATE_APP_CONTROLLER_RECEIVED;

    private static final int LONG_SWIPE_START_STATE =
            STATE_ACTIVITY_MULTIPLIER_COMPLETE | STATE_LAUNCHER_STARTED
                    | STATE_APP_CONTROLLER_RECEIVED | STATE_SCREENSHOT_CAPTURED;

    private static final int QUICK_SCRUB_START_UI_STATE = STATE_LAUNCHER_STARTED
            | STATE_QUICK_SCRUB_START | STATE_APP_CONTROLLER_RECEIVED;

    // For debugging, keep in sync with above states
    public static final String[] STATES = new String[] {
            "STATE_LAUNCHER_PRESENT",
            "STATE_LAUNCHER_STARTED",
            "STATE_LAUNCHER_DRAWN",
            "STATE_ACTIVITY_MULTIPLIER_COMPLETE",
            "STATE_APP_CONTROLLER_RECEIVED",
            "STATE_SCALED_CONTROLLER_HOME",
            "STATE_SCALED_CONTROLLER_RECENTS",
            "STATE_SCALED_CONTROLLER_LAST_TASK",
            "STATE_HANDLER_INVALIDATED",
            "STATE_GESTURE_STARTED_QUICKSTEP",
            "STATE_GESTURE_STARTED_QUICKSCRUB",
            "STATE_GESTURE_CANCELLED",
            "STATE_GESTURE_COMPLETED",
            "STATE_CURRENT_TASK_FINISHED",
            "STATE_QUICK_SCRUB_START",
            "STATE_QUICK_SCRUB_END",
            "STATE_CAPTURE_SCREENSHOT",
            "STATE_SCREENSHOT_CAPTURED",
            "STATE_SCREENSHOT_VIEW_SHOWN",
            "STATE_RESUME_LAST_TASK",
            "STATE_START_NEW_TASK",
            "STATE_ASSIST_DATA_RECEIVED",
    };

    enum GestureEndTarget {
        HOME(1, STATE_SCALED_CONTROLLER_HOME, true, false, ContainerType.WORKSPACE),

        RECENTS(1, STATE_SCALED_CONTROLLER_RECENTS | STATE_CAPTURE_SCREENSHOT
                | STATE_SCREENSHOT_VIEW_SHOWN, true, false, ContainerType.TASKSWITCHER),

        NEW_TASK(0, STATE_START_NEW_TASK, false, true, ContainerType.APP),

        LAST_TASK(0, STATE_SCALED_CONTROLLER_LAST_TASK, false, false, ContainerType.APP);

        GestureEndTarget(float endShift, int endState, boolean isLauncher, boolean canBeContinued,
                int containerType) {
            this.endShift = endShift;
            this.endState = endState;
            this.isLauncher = isLauncher;
            this.canBeContinued = canBeContinued;
            this.containerType = containerType;
        }

        // 0 is app, 1 is overview
        public final float endShift;
        public final int endState;
        public final boolean isLauncher;
        public final boolean canBeContinued;
        public final int containerType;
    }

    public static final long MAX_SWIPE_DURATION = 350;
    public static final long MIN_SWIPE_DURATION = 80;
    public static final long MIN_OVERSHOOT_DURATION = 120;

    public static final float MIN_PROGRESS_FOR_OVERVIEW = 0.7f;
    private static final float SWIPE_DURATION_MULTIPLIER =
            Math.min(1 / MIN_PROGRESS_FOR_OVERVIEW, 1 / (1 - MIN_PROGRESS_FOR_OVERVIEW));
    private static final String SCREENSHOT_CAPTURED_EVT = "ScreenshotCaptured";

    private static final long SHELF_ANIM_DURATION = 120;

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
    private boolean mDispatchedDownEvent;
    private boolean mContinuingLastGesture;
    // To avoid UI jump when gesture is started, we offset the animation by the threshold.
    private float mShiftAtGestureStart = 0;

    private final Handler mMainThreadHandler = MAIN_THREAD_EXECUTOR.getHandler();

    private final Context mContext;
    private final ActivityControlHelper<T> mActivityControlHelper;
    private final ActivityInitListener mActivityInitListener;
    private final TouchInteractionLog mTouchInteractionLog;

    private final int mRunningTaskId;
    private final RunningTaskInfo mRunningTaskInfo;
    private ThumbnailData mTaskSnapshot;

    private MultiStateCallback mStateCallback;
    private AnimatorPlaybackController mLauncherTransitionController;

    private T mActivity;
    private LayoutListener mLayoutListener;
    private RecentsView mRecentsView;
    private SyncRtSurfaceTransactionApplierCompat mSyncTransactionApplier;
    private QuickScrubController mQuickScrubController;
    private AnimationFactory mAnimationFactory = (t, i) -> { };

    private boolean mWasLauncherAlreadyVisible;

    private boolean mPassedOverviewThreshold;
    private boolean mGestureStarted;
    private int mLogAction = Touch.SWIPE;
    private float mCurrentQuickScrubProgress;
    private boolean mQuickScrubBlocked;

    private @InteractionType int mInteractionType = INTERACTION_NORMAL;

    private final RecentsAnimationWrapper mRecentsAnimationWrapper;

    private final long mTouchTimeMs;
    private long mLauncherFrameDrawnTime;

    private boolean mLongSwipeMode = false;
    private float mLongSwipeDisplacement = 0;
    private LongSwipeHelper mLongSwipeController;

    private Bundle mAssistData;

    WindowTransformSwipeHandler(RunningTaskInfo runningTaskInfo, Context context,
            long touchTimeMs, ActivityControlHelper<T> controller, boolean continuingLastGesture,
            InputConsumerController inputConsumer, TouchInteractionLog touchInteractionLog) {
        mContext = context;
        mRunningTaskInfo = runningTaskInfo;
        mRunningTaskId = runningTaskInfo.id;
        mTouchTimeMs = touchTimeMs;
        mActivityControlHelper = controller;
        mActivityInitListener = mActivityControlHelper
                .createActivityInitListener(this::onActivityInit);
        mContinuingLastGesture = continuingLastGesture;
        mTouchInteractionLog = touchInteractionLog;
        mRecentsAnimationWrapper = new RecentsAnimationWrapper(inputConsumer,
                this::createNewTouchProxyHandler);
        mClipAnimationHelper = new ClipAnimationHelper(context);
        mTransformParams = new ClipAnimationHelper.TransformParams();

        initStateCallbacks();
    }

    private void initStateCallbacks() {
        mStateCallback = new MultiStateCallback();

        // Re-setup the recents UI when gesture starts, as the state could have been changed during
        // that time by a previous window transition.
        mStateCallback.addCallback(STATE_LAUNCHER_STARTED | STATE_GESTURE_STARTED_QUICKSTEP,
                this::setupRecentsViewUi);

        mStateCallback.addCallback(STATE_LAUNCHER_DRAWN | STATE_GESTURE_STARTED_QUICKSCRUB,
                this::initializeLauncherAnimationController);
        mStateCallback.addCallback(STATE_LAUNCHER_DRAWN | STATE_GESTURE_STARTED_QUICKSTEP,
                this::initializeLauncherAnimationController);

        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_LAUNCHER_DRAWN,
                this::launcherFrameDrawn);

        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_GESTURE_STARTED_QUICKSTEP,
                this::notifyGestureStartedAsync);
        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_GESTURE_STARTED_QUICKSCRUB,
                this::notifyGestureStartedAsync);

        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_LAUNCHER_STARTED
                        | STATE_GESTURE_CANCELLED,
                this::resetStateForAnimationCancel);

        mStateCallback.addCallback(STATE_LAUNCHER_STARTED | STATE_APP_CONTROLLER_RECEIVED,
                this::sendRemoteAnimationsToAnimationFactory);

        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_SCALED_CONTROLLER_LAST_TASK,
                this::resumeLastTaskForQuickstep);
        mStateCallback.addCallback(STATE_RESUME_LAST_TASK | STATE_APP_CONTROLLER_RECEIVED,
                this::resumeLastTask);
        mStateCallback.addCallback(STATE_START_NEW_TASK | STATE_APP_CONTROLLER_RECEIVED,
                this::startNewTask);

        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_APP_CONTROLLER_RECEIVED
                        | STATE_ACTIVITY_MULTIPLIER_COMPLETE
                        | STATE_CAPTURE_SCREENSHOT,
                this::switchToScreenshot);

        mStateCallback.addCallback(STATE_SCREENSHOT_CAPTURED | STATE_GESTURE_COMPLETED
                        | STATE_SCALED_CONTROLLER_RECENTS,
                this::finishCurrentTransitionToRecents);

        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_GESTURE_COMPLETED
                        | STATE_SCALED_CONTROLLER_HOME | STATE_APP_CONTROLLER_RECEIVED
                        | STATE_ACTIVITY_MULTIPLIER_COMPLETE,
                this::finishCurrentTransitionToHome);
        mStateCallback.addCallback(STATE_SCALED_CONTROLLER_HOME | STATE_CURRENT_TASK_FINISHED,
                this::reset);

        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_APP_CONTROLLER_RECEIVED
                        | STATE_ACTIVITY_MULTIPLIER_COMPLETE | STATE_SCALED_CONTROLLER_RECENTS
                        | STATE_CURRENT_TASK_FINISHED | STATE_GESTURE_COMPLETED
                        | STATE_GESTURE_STARTED_QUICKSTEP,
                this::setupLauncherUiAfterSwipeUpAnimation);
        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_APP_CONTROLLER_RECEIVED
                        | STATE_ACTIVITY_MULTIPLIER_COMPLETE | STATE_SCALED_CONTROLLER_RECENTS
                        | STATE_CURRENT_TASK_FINISHED | STATE_GESTURE_COMPLETED
                        | STATE_GESTURE_STARTED_QUICKSTEP | STATE_ASSIST_DATA_RECEIVED,
                this::preloadAssistData);

        mStateCallback.addCallback(STATE_HANDLER_INVALIDATED, this::invalidateHandler);
        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_HANDLER_INVALIDATED,
                this::invalidateHandlerWithLauncher);
        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_HANDLER_INVALIDATED
                | STATE_SCALED_CONTROLLER_LAST_TASK,
                this::notifyTransitionCancelled);

        mStateCallback.addCallback(QUICK_SCRUB_START_UI_STATE, this::onQuickScrubStartUi);
        mStateCallback.addCallback(STATE_LAUNCHER_STARTED | STATE_QUICK_SCRUB_START
                | STATE_SCALED_CONTROLLER_RECENTS, this::onFinishedTransitionToQuickScrub);
        mStateCallback.addCallback(STATE_LAUNCHER_STARTED | STATE_CURRENT_TASK_FINISHED
                | STATE_QUICK_SCRUB_END, this::switchToFinalAppAfterQuickScrub);

        mStateCallback.addCallback(LONG_SWIPE_ENTER_STATE, this::checkLongSwipeCanEnter);
        mStateCallback.addCallback(LONG_SWIPE_START_STATE, this::checkLongSwipeCanStart);

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

        TransformedRect tempRect = new TransformedRect();
        mTransitionDragLength = mActivityControlHelper.getSwipeUpDestinationAndLength(
                dp, mContext, mInteractionType, tempRect);
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
            mLayoutListener.setHandler(null);
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
        SyncRtSurfaceTransactionApplierCompat.create(mRecentsView, (applier) -> {
            mSyncTransactionApplier = applier;
        });
        mRecentsView.setEnableFreeScroll(false);
        mRecentsView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (!mLongSwipeMode && mGestureEndTarget != HOME) {
                updateFinalShift();
            }
        });
        mRecentsView.setRecentsAnimationWrapper(mRecentsAnimationWrapper);
        mRecentsView.setClipAnimationHelper(mClipAnimationHelper);
        mQuickScrubController = mRecentsView.getQuickScrubController();
        mLayoutListener = mActivityControlHelper.createLayoutListener(mActivity);

        mStateCallback.setState(STATE_LAUNCHER_PRESENT);
        if (alreadyOnHome) {
            onLauncherStart(activity);
        } else {
            activity.setOnStartCallback(this::onLauncherStart);
        }
        return true;
    }

    private void onLauncherStart(final T activity) {
        if (mActivity != activity) {
            return;
        }
        if (mStateCallback.hasStates(STATE_HANDLER_INVALIDATED)) {
            return;
        }

        mAnimationFactory = mActivityControlHelper.prepareRecentsUI(mActivity,
                mWasLauncherAlreadyVisible, true, this::onAnimatorPlaybackControllerCreated);
        AbstractFloatingView.closeAllOpenViews(activity, mWasLauncherAlreadyVisible);

        if (mWasLauncherAlreadyVisible) {
            mStateCallback.setState(STATE_ACTIVITY_MULTIPLIER_COMPLETE | STATE_LAUNCHER_DRAWN);
        } else {
            TraceHelper.beginSection("WTS-init");
            View dragLayer = activity.getDragLayer();
            mActivityControlHelper.getAlphaProperty(activity).setValue(0);
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

        setupRecentsViewUi();
        mLayoutListener.open();
        mStateCallback.setState(STATE_LAUNCHER_STARTED);
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
        AlphaProperty property = mActivityControlHelper.getAlphaProperty(mActivity);
        if (property.getValue() < 1) {
            if (mGestureStarted) {
                final MultiStateCallback callback = mStateCallback;
                ObjectAnimator animator = ObjectAnimator.ofFloat(
                        property, MultiValueAlpha.VALUE, 1);
                animator.setDuration(getFadeInDuration()).addListener(
                        new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                callback.setState(STATE_ACTIVITY_MULTIPLIER_COMPLETE);
                            }
                        });
                animator.start();
            } else {
                property.setValue(1);
                mStateCallback.setState(STATE_ACTIVITY_MULTIPLIER_COMPLETE);
            }
        }
        mLauncherFrameDrawnTime = SystemClock.uptimeMillis();
    }

    private void sendRemoteAnimationsToAnimationFactory() {
        mAnimationFactory.onRemoteAnimationReceived(mRecentsAnimationWrapper.targetSet);
    }

    private void initializeLauncherAnimationController() {
        mLayoutListener.setHandler(this);
        buildAnimationController();

        if (LatencyTrackerCompat.isEnabled(mContext)) {
            LatencyTrackerCompat.logToggleRecents((int) (mLauncherFrameDrawnTime - mTouchTimeMs));
        }

        // This method is only called when STATE_GESTURE_STARTED_QUICKSTEP/
        // STATE_GESTURE_STARTED_QUICKSCRUB is set, so we can enable the high-res thumbnail loader
        // here once we are sure that we will end up in an overview state
        RecentsModel.INSTANCE.get(mContext).getThumbnailCache()
                .getHighResLoadingState().setVisible(true);
    }

    private void shiftAnimationDestinationForQuickscrub() {
        TransformedRect tempRect = new TransformedRect();
        mActivityControlHelper
                .getSwipeUpDestinationAndLength(mDp, mContext, mInteractionType, tempRect);
        mClipAnimationHelper.updateTargetRect(tempRect);

        float offsetY =
                mActivityControlHelper.getTranslationYForQuickScrub(tempRect, mDp, mContext);
        float scale, offsetX;
        Resources res = mContext.getResources();

        if (ActivityManagerWrapper.getInstance().getRecentTasks(2, UserHandle.myUserId()).size()
                < 2) {
            // There are not enough tasks, we don't need to shift
            offsetX = 0;
            scale = 1;
        } else {
            offsetX = res.getDimensionPixelSize(R.dimen.recents_page_spacing)
                    + tempRect.rect.width();
            scale = getTaskCurveScaleForOffsetX(offsetX, tempRect.rect.width());
        }
        mClipAnimationHelper.offsetTarget(scale, Utilities.isRtl(res) ? -offsetX : offsetX, offsetY,
                QuickScrubController.QUICK_SCRUB_START_INTERPOLATOR);
    }

    private float getTaskCurveScaleForOffsetX(float offsetX, float taskWidth) {
        float distanceToReachEdge = mDp.widthPx / 2 + taskWidth / 2 +
                mContext.getResources().getDimensionPixelSize(R.dimen.recents_page_spacing);
        float interpolation = Math.min(1, offsetX / distanceToReachEdge);
        return TaskView.getCurveScaleForInterpolation(interpolation);
    }

    @UiThread
    public void dispatchMotionEventToRecentsView(MotionEvent event, @Nullable Float velocityX) {
        if (mRecentsView == null) {
            return;
        }
        // Pass the motion events to RecentsView to allow scrolling during swipe up.
        if (!mDispatchedDownEvent) {
            // The first event we dispatch should be ACTION_DOWN.
            mDispatchedDownEvent = true;
            MotionEvent downEvent = MotionEvent.obtain(event);
            downEvent.setAction(MotionEvent.ACTION_DOWN);
            int flags = downEvent.getEdgeFlags();
            downEvent.setEdgeFlags(flags | TouchInteractionService.EDGE_NAV_BAR);
            mRecentsView.simulateTouchEvent(downEvent, velocityX);
            downEvent.recycle();
        }

        mRecentsView.simulateTouchEvent(event, velocityX);
    }

    @UiThread
    public void updateDisplacement(float displacement) {
        // We are moving in the negative x/y direction
        displacement = -displacement;
        if (displacement > mTransitionDragLength && mTransitionDragLength > 0) {
            mCurrentShift.updateValue(1);

            if (!mLongSwipeMode && !FeatureFlags.SWIPE_HOME.get()) {
                onLongSwipeEnabled();
            }
            mLongSwipeDisplacement = displacement - mTransitionDragLength;
            onLongSwipeDisplacementUpdated();
        } else {
            if (mLongSwipeMode) {
                onLongSwipeDisabled();
            }
            float translation = Math.max(displacement, 0);
            float shift = mTransitionDragLength == 0 ? 0 : translation / mTransitionDragLength;
            mCurrentShift.updateValue(shift);
        }
    }

    public void onMotionPauseChanged(boolean isPaused) {
        setShelfState(isPaused ? PEEK : HIDE, FAST_OUT_SLOW_IN, SHELF_ANIM_DURATION);
    }

    @UiThread
    public void setShelfState(ShelfAnimState shelfState, Interpolator interpolator, long duration) {
        if (mInteractionType == INTERACTION_NORMAL) {
            mAnimationFactory.setShelfState(shelfState, interpolator, duration);
            mIsShelfPeeking = shelfState == PEEK;
            if (mRecentsView != null && shelfState.shouldPreformHaptic) {
                mRecentsView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
            }
        }
    }

    /**
     * Called by {@link #mLayoutListener} when launcher layout changes
     */
    public void buildAnimationController() {
        initTransitionEndpoints(mActivity.getDeviceProfile());
        mAnimationFactory.createActivityController(mTransitionDragLength, mInteractionType);
    }

    private void onAnimatorPlaybackControllerCreated(AnimatorPlaybackController anim) {
        mLauncherTransitionController = anim;
        mLauncherTransitionController.dispatchOnStart();
        updateLauncherTransitionProgress();
    }

    @UiThread
    private void updateFinalShift() {
        float shift = mCurrentShift.value;

        RecentsAnimationControllerCompat controller = mRecentsAnimationWrapper.getController();
        if (controller != null) {
            float offsetX = 0;
            if (mRecentsView != null && mInteractionType == INTERACTION_NORMAL) {
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

            boolean passedThreshold = shift > 1 - UPDATE_SYSUI_FLAGS_THRESHOLD;
            mRecentsAnimationWrapper.setAnimationTargetsBehindSystemBars(!passedThreshold);
            if (mActivityControlHelper.shouldMinimizeSplitScreen()) {
                mRecentsAnimationWrapper.setSplitScreenMinimizedForTransaction(passedThreshold);
            }
        }

        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            if (mRecentsAnimationWrapper.getController() != null && mLayoutListener != null) {
                mLayoutListener.open();
                mLayoutListener.update(mCurrentShift.value > 1, mLongSwipeMode,
                        mClipAnimationHelper.getCurrentRectWithInsets(),
                        mClipAnimationHelper.getCurrentCornerRadius());
            }
        }

        final boolean passed = mCurrentShift.value >= MIN_PROGRESS_FOR_OVERVIEW;
        if (passed != mPassedOverviewThreshold) {
            mPassedOverviewThreshold = passed;
            if (mInteractionType == INTERACTION_NORMAL && mRecentsView != null
                    && !SWIPE_HOME.get()) {
                mRecentsView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
            }
        }
        // Update insets of the non-running tasks, as we might switch to them.
        int runningTaskIndex = mRecentsView == null ? -1 : mRecentsView.getRunningTaskIndex();
        if (mInteractionType == INTERACTION_NORMAL && runningTaskIndex >= 0) {
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

        mRecentsAnimationWrapper.setController(targetSet.controller, targetSet);
        mTouchInteractionLog.startRecentsAnimationCallback(targetSet.apps.length);
        setStateOnUiThread(STATE_APP_CONTROLLER_RECEIVED);

        mPassedOverviewThreshold = false;
    }

    @Override
    public void onRecentsAnimationCanceled() {
        mRecentsAnimationWrapper.setController(null, null);
        mActivityInitListener.unregister();
        setStateOnUiThread(STATE_GESTURE_CANCELLED | STATE_HANDLER_INVALIDATED);
        mTouchInteractionLog.cancelRecentsAnimation();
    }

    @UiThread
    public void onGestureStarted() {
        notifyGestureStartedAsync();
        mShiftAtGestureStart = mCurrentShift.value;
        setStateOnUiThread(mInteractionType == INTERACTION_NORMAL
                ? STATE_GESTURE_STARTED_QUICKSTEP : STATE_GESTURE_STARTED_QUICKSCRUB);
        mGestureStarted = true;
        mRecentsAnimationWrapper.hideCurrentInputMethod();
        mRecentsAnimationWrapper.enableInputConsumer();
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

    @UiThread
    public void onGestureEnded(float endVelocity, float velocityX) {
        float flingThreshold = mContext.getResources()
                .getDimension(R.dimen.quickstep_fling_threshold_velocity);
        boolean isFling = mGestureStarted && Math.abs(endVelocity) > flingThreshold;
        setStateOnUiThread(STATE_GESTURE_COMPLETED);

        mLogAction = isFling ? Touch.FLING : Touch.SWIPE;

        if (mLongSwipeMode) {
            onLongSwipeGestureFinish(endVelocity, isFling, velocityX);
        } else {
            handleNormalGestureEnd(endVelocity, isFling, velocityX);
        }
    }

    @UiThread
    private TouchConsumer createNewTouchProxyHandler() {
        mCurrentShift.finishAnimation();
        if (mLauncherTransitionController != null) {
            mLauncherTransitionController.getAnimationPlayer().end();
        }
        if (!ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            // Hide the task view, if not already hidden
            setTargetAlphaProvider(WindowTransformSwipeHandler::getHiddenTargetAlpha);
        }

        return OverviewTouchConsumer.newInstance(mActivityControlHelper, true,
                mTouchInteractionLog);
    }

    @UiThread
    private void handleNormalGestureEnd(float endVelocity, boolean isFling, float velocityX) {
        float velocityPxPerMs = endVelocity / 1000;
        float velocityXPxPerMs = velocityX / 1000;
        long duration = MAX_SWIPE_DURATION;
        float currentShift = mCurrentShift.value;
        final GestureEndTarget endTarget;
        float endShift;
        final float startShift;
        Interpolator interpolator = DEACCEL;
        int nextPage = 0;
        int taskToLaunch = 0;
        final boolean goingToNewTask;
        if (mRecentsView != null) {
            nextPage = mRecentsView.getNextPage();
            final int lastTaskIndex = mRecentsView.getTaskViewCount() - 1;
            final int runningTaskIndex = mRecentsView.getRunningTaskIndex();
            taskToLaunch = nextPage <= lastTaskIndex ? nextPage : lastTaskIndex;
            goingToNewTask = mRecentsView != null && taskToLaunch != runningTaskIndex;
        } else {
            goingToNewTask = false;
        }
        final boolean reachedOverviewThreshold = currentShift >= MIN_PROGRESS_FOR_OVERVIEW;
        if (!isFling) {
            if (SWIPE_HOME.get()) {
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
            if (SWIPE_HOME.get() && endVelocity < 0 && !mIsShelfPeeking) {
                // If swiping at a diagonal, base end target on the faster velocity.
                endTarget = goingToNewTask && Math.abs(velocityX) > Math.abs(endVelocity)
                        ? NEW_TASK : HOME;
            } else if (endVelocity < 0 && (!goingToNewTask || reachedOverviewThreshold)) {
                // If user scrolled to a new task, only go to recents if they already passed
                // the overview threshold. Otherwise, we'll snap to the new task and launch it.
                endTarget = RECENTS;
            } else {
                endTarget = goingToNewTask ? NEW_TASK : LAST_TASK;
            }
            endShift = endTarget.endShift;
            startShift = Utilities.boundToRange(currentShift - velocityPxPerMs
                    * SINGLE_FRAME_MS / mTransitionDragLength, 0, 1);
            float minFlingVelocity = mContext.getResources()
                    .getDimension(R.dimen.quickstep_fling_min_velocity);
            if (Math.abs(endVelocity) > minFlingVelocity && mTransitionDragLength > 0) {
                if (endTarget == RECENTS) {
                    Interpolators.OvershootParams overshoot = new Interpolators.OvershootParams(
                            startShift, endShift, endShift, velocityPxPerMs, mTransitionDragLength);
                    endShift = overshoot.end;
                    interpolator = overshoot.interpolator;
                    duration = Utilities.boundToRange(overshoot.duration, MIN_OVERSHOOT_DURATION,
                            MAX_SWIPE_DURATION);
                } else {
                    float distanceToTravel = (endShift - currentShift) * mTransitionDragLength;

                    // we want the page's snap velocity to approximately match the velocity at
                    // which the user flings, so we scale the duration by a value near to the
                    // derivative of the scroll interpolator at zero, ie. 2.
                    long baseDuration = Math.round(Math.abs(distanceToTravel / velocityPxPerMs));
                    duration = Math.min(MAX_SWIPE_DURATION, 2 * baseDuration);
                }
            }
        }

        if (mRecentsView != null && !endTarget.isLauncher && taskToLaunch != nextPage) {
            // Scrolled to Clear all button, snap back to last task and launch it.
            mRecentsView.snapToPage(taskToLaunch, Math.toIntExact(duration), interpolator);
        }

        if (endTarget == HOME) {
            setShelfState(ShelfAnimState.CANCEL, LINEAR, 0);
            duration = Math.max(MIN_OVERSHOOT_DURATION, duration);
        } else if (endTarget == RECENTS) {
            mRecentsAnimationWrapper.enableTouchProxy();
            if (mRecentsView != null) {
                duration = Math.max(duration, mRecentsView.getScroller().getDuration());
            }
            if (SWIPE_HOME.get()) {
                setShelfState(ShelfAnimState.OVERVIEW, interpolator, duration);
            }
        } else if (endTarget == NEW_TASK || endTarget == LAST_TASK) {
            // Let RecentsView handle the scrolling to the task, which we launch in startNewTask()
            // or resumeLastTaskForQuickstep().
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
        boolean toLauncher = endTarget.isLauncher;
        final int direction;
        if (dp.isVerticalBarLayout()) {
            direction = (dp.isSeascape() ^ toLauncher) ? Direction.LEFT : Direction.RIGHT;
        } else {
            direction = toLauncher ? Direction.UP : Direction.DOWN;
        }

        UserEventDispatcher.newInstance(mContext).logStateChangeAction(
                mLogAction, direction,
                ContainerType.NAVBAR, ContainerType.APP,
                endTarget.containerType,
                0);
    }

    /** Animates to the given progress, where 0 is the current app and 1 is overview. */
    @UiThread
    private void animateToProgress(float start, float end, long duration, Interpolator interpolator,
            GestureEndTarget target, float velocityPxPerMs) {
        mRecentsAnimationWrapper.runOnInit(() -> animateToProgressInternal(start, end, duration,
                interpolator, target, velocityPxPerMs));
    }

    @UiThread
    private void animateToProgressInternal(float start, float end, long duration,
            Interpolator interpolator, GestureEndTarget target, float velocityPxPerMs) {
        mGestureEndTarget = target;
        ActivityControlHelper.HomeAnimationFactory homeAnimFactory;
        Animator windowAnim;
        if (mGestureEndTarget == HOME) {
            if (mActivity != null) {
                homeAnimFactory = mActivityControlHelper.prepareHomeUI(mActivity);
            } else {
                homeAnimFactory = new ActivityControlHelper.HomeAnimationFactory() {
                    @NonNull
                    @Override
                    public RectF getWindowTargetRect() {
                        RectF fallbackTarget = new RectF(mClipAnimationHelper.getTargetRect());
                        Utilities.scaleRectFAboutCenter(fallbackTarget, 0.25f);
                        return fallbackTarget;
                    }

                    @NonNull
                    @Override
                    public Animator createActivityAnimationToHome() {
                        return new AnimatorSet();
                    }
                };
                mStateCallback.addChangeHandler(STATE_LAUNCHER_PRESENT | STATE_HANDLER_INVALIDATED,
                        isPresent -> mRecentsView.startHome());
            }
            windowAnim = createWindowAnimationToHome(start, homeAnimFactory.getWindowTargetRect());
            mLauncherTransitionController = null;
        } else {
            windowAnim = mCurrentShift.animateToValue(start, end);
            homeAnimFactory = null;
        }
        windowAnim.setDuration(duration).setInterpolator(interpolator);
        windowAnim.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                setStateOnUiThread(target.endState);
            }
        });
        windowAnim.start();
        // Always play the entire launcher animation when going home, since it is separate from
        // the animation that has been controlled thus far.
        if (mGestureEndTarget == HOME) {
            start = 0;
        }

        // We want to use the same interpolator as the window, but need to adjust it to
        // interpolate over the remaining progress (end - start).
        TimeInterpolator adjustedInterpolator = Interpolators.mapToProgress(
                interpolator, start, end);
        if (homeAnimFactory != null) {
            Animator homeAnim = homeAnimFactory.createActivityAnimationToHome();
            homeAnim.setDuration(duration).setInterpolator(adjustedInterpolator);
            homeAnim.start();
            mLauncherTransitionController = null;
        }
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
                mLauncherTransitionController.dispatchOnStartWithVelocity(end, velocityPxPerMs);
            }
            mLauncherTransitionController.getAnimationPlayer().start();
        }
    }

    /**
     * Creates an Animator that transforms the current app window into the home app.
     * @param startProgress The progress of {@link #mCurrentShift} to start the window from.
     * @param endTarget Where to animate the window towards.
     */
    private Animator createWindowAnimationToHome(float startProgress, RectF endTarget) {
        final RemoteAnimationTargetSet targetSet = mRecentsAnimationWrapper.targetSet;
        RectF startRect = new RectF(mClipAnimationHelper.applyTransform(targetSet,
                mTransformParams.setProgress(startProgress)));
        RectF originalTarget = new RectF(mClipAnimationHelper.getTargetRect());
        final RectF finalTarget = endTarget;

        final RectFEvaluator rectFEvaluator = new RectFEvaluator();
        final RectF targetRect = new RectF();
        final RectF currentRect = new RectF();

        ValueAnimator anim = ValueAnimator.ofFloat(0, 1);
        anim.addUpdateListener(animation -> {
            float progress = animation.getAnimatedFraction();
            float interpolatedProgress = Interpolators.ACCEL_2.getInterpolation(progress);
            // Initially go towards original target (task view in recents),
            // but accelerate towards the final target.
            // TODO: This is technically not correct. Instead, motion should continue at
            // the released velocity but accelerate towards the target.
            targetRect.set(rectFEvaluator.evaluate(interpolatedProgress,
                    originalTarget, finalTarget));
            currentRect.set(rectFEvaluator.evaluate(progress, startRect, targetRect));
            float alpha = 1 - interpolatedProgress;
            mTransformParams.setCurrentRectAndTargetAlpha(currentRect, alpha)
                    .setSyncTransactionApplier(mSyncTransactionApplier);
            mClipAnimationHelper.applyTransform(targetSet, mTransformParams);
        });
        anim.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                if (mRecentsView != null) {
                    mRecentsView.post(mRecentsView::resetTaskVisuals);
                }
            }
        });
        return anim;
    }

    @UiThread
    private void resumeLastTaskForQuickstep() {
        setStateOnUiThread(STATE_RESUME_LAST_TASK);
        doLogGesture(LAST_TASK);
        reset();
    }

    @UiThread
    private void resumeLastTask() {
        mRecentsAnimationWrapper.finish(false /* toRecents */, null);
        mTouchInteractionLog.finishRecentsAnimation(false);
    }

    @UiThread
    private void startNewTask() {
        // Launch the task user scrolled to (mRecentsView.getNextPage()).
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            // We finish recents animation inside launchTask() when live tile is enabled.
            mRecentsView.getTaskViewAt(mRecentsView.getNextPage()).launchTask(false,
                    result -> setStateOnUiThread(STATE_HANDLER_INVALIDATED),
                    mMainThreadHandler);
        } else {
            mRecentsAnimationWrapper.finish(true /* toRecents */, () -> {
                mRecentsView.getTaskViewAt(mRecentsView.getNextPage()).launchTask(false,
                        result -> setStateOnUiThread(STATE_HANDLER_INVALIDATED),
                        mMainThreadHandler);
            });
        }
        mTouchInteractionLog.finishRecentsAnimation(false);
        doLogGesture(NEW_TASK);
    }

    public void reset() {
        if (mInteractionType != INTERACTION_QUICK_SCRUB) {
            // Only invalidate the handler if we are not quick scrubbing, otherwise, it will be
            // invalidated after the quick scrub ends
            setStateOnUiThread(STATE_HANDLER_INVALIDATED);
        }
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
        mLauncherTransitionController = null;
        mLayoutListener.finish();
        mActivityControlHelper.getAlphaProperty(mActivity).setValue(1);

        mRecentsView.setEnableFreeScroll(true);
        mRecentsView.setRunningTaskIconScaledDown(false);
        mRecentsView.setOnScrollChangeListener(null);
        mQuickScrubController.cancelActiveQuickscrub();
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

    public void layoutListenerClosed() {
        mRecentsView.setRunningTaskHidden(false);
        if (mWasLauncherAlreadyVisible && mLauncherTransitionController != null) {
            mLauncherTransitionController.setPlayFraction(1);
        }
        mRecentsView.setEnableDrawingLiveTile(true);
    }

    private void switchToScreenshot() {
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            setStateOnUiThread(STATE_SCREENSHOT_CAPTURED);
        } else {
            boolean finishTransitionPosted = false;
            RecentsAnimationControllerCompat controller = mRecentsAnimationWrapper.getController();
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
        mTouchInteractionLog.finishRecentsAnimation(true);
    }

    private void finishCurrentTransitionToHome() {
        synchronized (mRecentsAnimationWrapper) {
            mRecentsAnimationWrapper.finish(true /* toRecents */,
                    () -> setStateOnUiThread(STATE_CURRENT_TASK_FINISHED));
        }
        mTouchInteractionLog.finishRecentsAnimation(true);
        doLogGesture(HOME);
    }

    private void setupLauncherUiAfterSwipeUpAnimation() {
        if (mLauncherTransitionController != null) {
            mLauncherTransitionController.getAnimationPlayer().end();
            mLauncherTransitionController = null;
        }
        mActivityControlHelper.onSwipeUpComplete(mActivity);

        // Animate the first icon.
        mRecentsView.animateUpRunningTaskIconScale();
        mRecentsView.setSwipeDownShouldLaunchApp(true);

        RecentsModel.INSTANCE.get(mContext).onOverviewShown(false, TAG);

        doLogGesture(RECENTS);
        reset();
    }

    public void onQuickScrubStart() {
        if (mInteractionType != INTERACTION_NORMAL) {
            throw new IllegalArgumentException(
                    "Can't change interaction type from " + mInteractionType);
        }
        mInteractionType = INTERACTION_QUICK_SCRUB;
        mRecentsAnimationWrapper.runOnInit(this::shiftAnimationDestinationForQuickscrub);

        setStateOnUiThread(STATE_QUICK_SCRUB_START | STATE_GESTURE_COMPLETED);

        // Start the window animation without waiting for launcher.
        long duration = FeatureFlags.QUICK_SWITCH.get()
                ? QUICK_SWITCH_FROM_APP_START_DURATION
                : QUICK_SCRUB_FROM_APP_START_DURATION;
        animateToProgress(mCurrentShift.value, 1f, duration, LINEAR, RECENTS, 1f);
    }

    private void onQuickScrubStartUi() {
        if (!mQuickScrubController.prepareQuickScrub(TAG, FeatureFlags.QUICK_SWITCH.get())) {
            mQuickScrubBlocked = true;
            setStateOnUiThread(STATE_RESUME_LAST_TASK | STATE_HANDLER_INVALIDATED);
            return;
        }
        if (mLauncherTransitionController != null) {
            mLauncherTransitionController.getAnimationPlayer().end();
            mLauncherTransitionController = null;
        }

        mActivityControlHelper.onQuickInteractionStart(mActivity, mRunningTaskInfo, false,
                mTouchInteractionLog);

        // Inform the last progress in case we skipped before.
        mQuickScrubController.onQuickScrubProgress(mCurrentQuickScrubProgress);
    }

    private void onFinishedTransitionToQuickScrub() {
        if (mQuickScrubBlocked) {
            return;
        }
        mLayoutListener.finish();
        mQuickScrubController.onFinishedTransitionToQuickScrub();

        mRecentsView.animateUpRunningTaskIconScale();
        if (mQuickScrubController.isQuickSwitch()) {
            // Adjust the running task so that it is centered and fills the screen.
            TaskView runningTask = mRecentsView.getRunningTaskView();
            if (runningTask != null) {
                float insetHeight = mDp.heightPx - mDp.getInsets().top - mDp.getInsets().bottom;
                // Usually insetDiff will be 0, unless we allow apps to draw under the insets. In
                // that case (insetDiff != 0), we need to center in the system-specified available
                // height rather than launcher's inset height by adding half the insetDiff.
                float insetDiff = mDp.availableHeightPx - insetHeight;
                float topMargin = mActivity.getResources().getDimension(
                        R.dimen.task_thumbnail_half_top_margin);
                runningTask.setTranslationY((insetDiff / 2 - topMargin) / mRecentsView.getScaleX());
            }
        }
        RecentsModel.INSTANCE.get(mContext).onOverviewShown(false, TAG);
    }

    public void onQuickScrubProgress(float progress) {
        mCurrentQuickScrubProgress = progress;
        if (mQuickScrubController == null || mQuickScrubBlocked ||
                !mStateCallback.hasStates(QUICK_SCRUB_START_UI_STATE)) {
            return;
        }
        mQuickScrubController.onQuickScrubProgress(progress);
    }

    public void onQuickScrubEnd() {
        setStateOnUiThread(STATE_QUICK_SCRUB_END);
    }

    private void switchToFinalAppAfterQuickScrub() {
        if (mQuickScrubBlocked) {
            return;
        }
        mQuickScrubController.onQuickScrubEnd();

        // Normally this is handled in reset(), but since we are still scrubbing after the
        // transition into recents, we need to defer the handler invalidation for quick scrub until
        // after the gesture ends
        setStateOnUiThread(STATE_HANDLER_INVALIDATED);
    }

    public void setGestureEndCallback(Runnable gestureEndCallback) {
        mGestureEndCallback = gestureEndCallback;
    }

    // Handling long swipe
    private void onLongSwipeEnabled() {
        mLongSwipeMode = true;
        checkLongSwipeCanEnter();
        checkLongSwipeCanStart();
    }

    private void onLongSwipeDisabled() {
        mLongSwipeMode = false;
        mStateCallback.clearState(STATE_SCREENSHOT_VIEW_SHOWN);

        if (mLongSwipeController != null) {
            mLongSwipeController.destroy();
            setTargetAlphaProvider((t, a1) -> a1);

            // Rebuild animations
            buildAnimationController();
        }
    }

    private void onLongSwipeDisplacementUpdated() {
        if (!mLongSwipeMode || mLongSwipeController == null) {
            return;
        }

        mLongSwipeController.onMove(mLongSwipeDisplacement);
    }

    private void checkLongSwipeCanEnter() {
        if (!mLongSwipeMode || !mStateCallback.hasStates(LONG_SWIPE_ENTER_STATE)
                || !mActivityControlHelper.supportsLongSwipe(mActivity)) {
            return;
        }

        // We are entering long swipe mode, make sure the screen shot is captured.
        mStateCallback.setState(STATE_CAPTURE_SCREENSHOT | STATE_SCREENSHOT_VIEW_SHOWN);

    }

    private void checkLongSwipeCanStart() {
        if (!mLongSwipeMode || !mStateCallback.hasStates(LONG_SWIPE_START_STATE)
                || !mActivityControlHelper.supportsLongSwipe(mActivity)) {
            return;
        }

        RemoteAnimationTargetSet targetSet = mRecentsAnimationWrapper.targetSet;
        if (targetSet == null) {
            // This can happen when cancelAnimation comes on the background thread, while we are
            // processing the long swipe on the UI thread.
            return;
        }

        mLongSwipeController = mActivityControlHelper.getLongSwipeController(
                mActivity, mRunningTaskId);
        onLongSwipeDisplacementUpdated();
        if (!ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            setTargetAlphaProvider(WindowTransformSwipeHandler::getHiddenTargetAlpha);
        }
    }

    private void onLongSwipeGestureFinish(float velocity, boolean isFling, float velocityX) {
        if (!mLongSwipeMode || mLongSwipeController == null) {
            mLongSwipeMode = false;
            handleNormalGestureEnd(velocity, isFling, velocityX);
            return;
        }
        mLongSwipeMode = false;
        finishCurrentTransitionToRecents();
        mLongSwipeController.end(velocity, isFling,
                () -> setStateOnUiThread(STATE_HANDLER_INVALIDATED));

    }

    private void setTargetAlphaProvider(
            BiFunction<RemoteAnimationTargetCompat, Float, Float> provider) {
        mClipAnimationHelper.setTaskAlphaCallback(provider);
        updateFinalShift();
    }

    public void onAssistDataReceived(Bundle assistData) {
        mAssistData = assistData;
        setStateOnUiThread(STATE_ASSIST_DATA_RECEIVED);
    }

    private void preloadAssistData() {
        RecentsModel.INSTANCE.get(mContext).preloadAssistData(mRunningTaskId, mAssistData);
    }

    public static float getHiddenTargetAlpha(RemoteAnimationTargetCompat app, Float expectedAlpha) {
        if (!(app.isNotInRecents
                || app.activityType == RemoteAnimationTargetCompat.ACTIVITY_TYPE_HOME)) {
            return 0;
        }
        return expectedAlpha;
    }
}
