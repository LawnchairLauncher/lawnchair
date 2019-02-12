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
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.Interpolators.OVERSHOOT_1_2;
import static com.android.quickstep.QuickScrubController.QUICK_SCRUB_FROM_APP_START_DURATION;
import static com.android.quickstep.TouchConsumer.INTERACTION_NORMAL;
import static com.android.quickstep.TouchConsumer.INTERACTION_QUICK_SCRUB;
import static com.android.quickstep.views.RecentsView.UPDATE_SYSUI_FLAGS_THRESHOLD;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.support.annotation.AnyThread;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewTreeObserver.OnDrawListener;
import android.view.WindowManager;
import android.view.animation.Interpolator;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.MultiValueAlpha.AlphaProperty;
import com.android.launcher3.util.TraceHelper;
import com.android.quickstep.ActivityControlHelper.ActivityInitListener;
import com.android.quickstep.ActivityControlHelper.AnimationFactory;
import com.android.quickstep.ActivityControlHelper.LayoutListener;
import com.android.quickstep.TouchConsumer.InteractionType;
import com.android.quickstep.util.ClipAnimationHelper;
import com.android.quickstep.util.RemoteAnimationTargetSet;
import com.android.quickstep.util.TransformedRect;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.LatencyTrackerCompat;
import com.android.systemui.shared.system.RecentsAnimationControllerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplier;
import com.android.systemui.shared.system.WindowCallbacksCompat;
import com.android.systemui.shared.system.WindowManagerWrapper;

import java.util.StringJoiner;
import java.util.function.BiFunction;

@TargetApi(Build.VERSION_CODES.O)
public class WindowTransformSwipeHandler<T extends BaseDraggingActivity> {
    private static final String TAG = WindowTransformSwipeHandler.class.getSimpleName();
    private static final boolean DEBUG_STATES = false;

    // Launcher UI related states
    private static final int STATE_LAUNCHER_PRESENT = 1 << 0;
    private static final int STATE_LAUNCHER_STARTED = 1 << 1;
    private static final int STATE_LAUNCHER_DRAWN = 1 << 2;
    private static final int STATE_ACTIVITY_MULTIPLIER_COMPLETE = 1 << 3;

    // Internal initialization states
    private static final int STATE_APP_CONTROLLER_RECEIVED = 1 << 4;

    // Interaction finish states
    private static final int STATE_SCALED_CONTROLLER_RECENTS = 1 << 5;
    private static final int STATE_SCALED_CONTROLLER_APP = 1 << 6;

    private static final int STATE_HANDLER_INVALIDATED = 1 << 7;
    private static final int STATE_GESTURE_STARTED_QUICKSTEP = 1 << 8;
    private static final int STATE_GESTURE_STARTED_QUICKSCRUB = 1 << 9;
    private static final int STATE_GESTURE_CANCELLED = 1 << 10;
    private static final int STATE_GESTURE_COMPLETED = 1 << 11;

    // States for quick switch/scrub
    private static final int STATE_CURRENT_TASK_FINISHED = 1 << 12;
    private static final int STATE_QUICK_SCRUB_START = 1 << 13;
    private static final int STATE_QUICK_SCRUB_END = 1 << 14;

    private static final int STATE_CAPTURE_SCREENSHOT = 1 << 15;
    private static final int STATE_SCREENSHOT_CAPTURED = 1 << 16;

    private static final int STATE_RESUME_LAST_TASK = 1 << 17;
    private static final int STATE_ASSIST_DATA_RECEIVED = 1 << 18;

    private static final int LAUNCHER_UI_STATES =
            STATE_LAUNCHER_PRESENT | STATE_LAUNCHER_DRAWN | STATE_ACTIVITY_MULTIPLIER_COMPLETE
            | STATE_LAUNCHER_STARTED;

    private static final int LONG_SWIPE_ENTER_STATE =
            STATE_ACTIVITY_MULTIPLIER_COMPLETE | STATE_LAUNCHER_STARTED
                    | STATE_APP_CONTROLLER_RECEIVED;

    private static final int LONG_SWIPE_START_STATE =
            STATE_ACTIVITY_MULTIPLIER_COMPLETE | STATE_LAUNCHER_STARTED
                    | STATE_APP_CONTROLLER_RECEIVED | STATE_SCREENSHOT_CAPTURED;

    // For debugging, keep in sync with above states
    private static final String[] STATES = new String[] {
            "STATE_LAUNCHER_PRESENT",
            "STATE_LAUNCHER_STARTED",
            "STATE_LAUNCHER_DRAWN",
            "STATE_ACTIVITY_MULTIPLIER_COMPLETE",
            "STATE_APP_CONTROLLER_RECEIVED",
            "STATE_SCALED_CONTROLLER_RECENTS",
            "STATE_SCALED_CONTROLLER_APP",
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
            "STATE_RESUME_LAST_TASK",
            "STATE_ASSIST_DATA_RECEIVED",
    };

    public static final long MAX_SWIPE_DURATION = 350;
    public static final long MIN_SWIPE_DURATION = 80;
    public static final long MIN_OVERSHOOT_DURATION = 120;

    public static final float MIN_PROGRESS_FOR_OVERVIEW = 0.5f;
    private static final float SWIPE_DURATION_MULTIPLIER =
            Math.min(1 / MIN_PROGRESS_FOR_OVERVIEW, 1 / (1 - MIN_PROGRESS_FOR_OVERVIEW));

    private final ClipAnimationHelper mClipAnimationHelper = new ClipAnimationHelper();

    protected Runnable mGestureEndCallback;
    protected boolean mIsGoingToHome;
    private DeviceProfile mDp;
    private int mTransitionDragLength;

    // Shift in the range of [0, 1].
    // 0 => preview snapShot is completely visible, and hotseat is completely translated down
    // 1 => preview snapShot is completely aligned with the recents view and hotseat is completely
    // visible.
    private final AnimatedFloat mCurrentShift = new AnimatedFloat(this::updateFinalShift);

    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

    // An increasing identifier per single instance of OtherActivityTouchConsumer. Generally one
    // instance of OtherActivityTouchConsumer will only have one swipe handle, but sometimes we can
    // end up with multiple handlers if we get recents command in the middle of a swipe gesture.
    // This is used to match the corresponding activity manager callbacks in
    // OtherActivityTouchConsumer
    public final int id;
    private final Context mContext;
    private final ActivityControlHelper<T> mActivityControlHelper;
    private final ActivityInitListener mActivityInitListener;

    private final int mRunningTaskId;
    private final RunningTaskInfo mRunningTaskInfo;
    private ThumbnailData mTaskSnapshot;

    private MultiStateCallback mStateCallback;
    private AnimatorPlaybackController mLauncherTransitionController;

    private T mActivity;
    private LayoutListener mLayoutListener;
    private RecentsView mRecentsView;
    private SyncRtSurfaceTransactionApplier mSyncTransactionApplier;
    private QuickScrubController mQuickScrubController;
    private AnimationFactory mAnimationFactory = (t, i) -> { };

    private Runnable mLauncherDrawnCallback;

    private boolean mWasLauncherAlreadyVisible;

    private boolean mPassedOverviewThreshold;
    private boolean mGestureStarted;
    private int mLogAction = Touch.SWIPE;
    private float mCurrentQuickScrubProgress;
    private boolean mQuickScrubBlocked;

    private @InteractionType int mInteractionType = INTERACTION_NORMAL;

    private InputConsumerController mInputConsumer =
            InputConsumerController.getRecentsAnimationInputConsumer();

    private final RecentsAnimationWrapper mRecentsAnimationWrapper = new RecentsAnimationWrapper();

    private final long mTouchTimeMs;
    private long mLauncherFrameDrawnTime;

    private boolean mBgLongSwipeMode = false;
    private boolean mUiLongSwipeMode = false;
    private float mLongSwipeDisplacement = 0;
    private LongSwipeHelper mLongSwipeController;

    private Bundle mAssistData;

    WindowTransformSwipeHandler(int id, RunningTaskInfo runningTaskInfo, Context context,
            long touchTimeMs, ActivityControlHelper<T> controller) {
        this.id = id;
        mContext = context;
        mRunningTaskInfo = runningTaskInfo;
        mRunningTaskId = runningTaskInfo.id;
        mTouchTimeMs = touchTimeMs;
        mActivityControlHelper = controller;
        mActivityInitListener = mActivityControlHelper
                .createActivityInitListener(this::onActivityInit);

        initStateCallbacks();
        // Register the input consumer on the UI thread, to ensure that it runs after any pending
        // unregister calls
        executeOnUiThread(mInputConsumer::registerInputConsumer);
    }

    private void initStateCallbacks() {
        mStateCallback = new MultiStateCallback() {
            @Override
            public void setState(int stateFlag) {
                debugNewState(stateFlag);
                super.setState(stateFlag);
            }
        };

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

        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_SCALED_CONTROLLER_APP,
                this::resumeLastTaskForQuickstep);
        mStateCallback.addCallback(STATE_RESUME_LAST_TASK | STATE_APP_CONTROLLER_RECEIVED,
                this::resumeLastTask);

        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_APP_CONTROLLER_RECEIVED
                        | STATE_ACTIVITY_MULTIPLIER_COMPLETE
                        | STATE_CAPTURE_SCREENSHOT,
                this::switchToScreenshot);

        mStateCallback.addCallback(STATE_SCREENSHOT_CAPTURED | STATE_GESTURE_COMPLETED
                        | STATE_SCALED_CONTROLLER_RECENTS,
                this::finishCurrentTransitionToHome);

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
                | STATE_SCALED_CONTROLLER_APP,
                this::notifyTransitionCancelled);

        mStateCallback.addCallback(STATE_LAUNCHER_STARTED | STATE_QUICK_SCRUB_START
                        | STATE_APP_CONTROLLER_RECEIVED, this::onQuickScrubStart);
        mStateCallback.addCallback(STATE_LAUNCHER_STARTED | STATE_QUICK_SCRUB_START
                | STATE_SCALED_CONTROLLER_RECENTS, this::onFinishedTransitionToQuickScrub);
        mStateCallback.addCallback(STATE_LAUNCHER_STARTED | STATE_CURRENT_TASK_FINISHED
                | STATE_QUICK_SCRUB_END, this::switchToFinalAppAfterQuickScrub);

        mStateCallback.addCallback(LONG_SWIPE_ENTER_STATE, this::checkLongSwipeCanEnter);
        mStateCallback.addCallback(LONG_SWIPE_START_STATE, this::checkLongSwipeCanStart);
    }

    private void executeOnUiThread(Runnable action) {
        if (Looper.myLooper() == mMainThreadHandler.getLooper()) {
            action.run();
        } else {
            postAsyncCallback(mMainThreadHandler, action);
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
        mTransitionDragLength = mActivityControlHelper
                .getSwipeUpDestinationAndLength(dp, mContext, mInteractionType, tempRect);
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
        mSyncTransactionApplier = new SyncRtSurfaceTransactionApplier(mRecentsView);
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
                mWasLauncherAlreadyVisible, this::onAnimatorPlaybackControllerCreated);
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

        mRecentsView.showTask(mRunningTaskId);
        mRecentsView.setRunningTaskHidden(true);
        mRecentsView.setRunningTaskIconScaledDown(true /* isScaledDown */, false /* animate */);
        mLayoutListener.open();
        mStateCallback.setState(STATE_LAUNCHER_STARTED);
    }

    public void setLauncherOnDrawCallback(Runnable callback) {
        mLauncherDrawnCallback = callback;
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
        if (mLauncherDrawnCallback != null) {
            mLauncherDrawnCallback.run();
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
        RecentsModel.getInstance(mContext).getRecentsTaskLoader()
                .getHighResThumbnailLoader().setVisible(true);
    }

    public void updateInteractionType(@InteractionType int interactionType) {
        if (mInteractionType != INTERACTION_NORMAL) {
            throw new IllegalArgumentException(
                    "Can't change interaction type from " + mInteractionType);
        }
        if (interactionType != INTERACTION_QUICK_SCRUB) {
            throw new IllegalArgumentException(
                    "Can't change interaction type to " + interactionType);
        }
        mInteractionType = interactionType;
        mRecentsAnimationWrapper.runOnInit(this::shiftAnimationDestinationForQuickscrub);

        setStateOnUiThread(STATE_QUICK_SCRUB_START | STATE_GESTURE_COMPLETED);

        // Start the window animation without waiting for launcher.
        animateToProgress(mCurrentShift.value, 1f, QUICK_SCRUB_FROM_APP_START_DURATION, LINEAR,
                true /* goingToHome */);
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
            float distanceToReachEdge = mDp.widthPx / 2 + tempRect.rect.width() / 2 +
                    res.getDimensionPixelSize(R.dimen.recents_page_spacing);
            float interpolation = Math.min(1, offsetX / distanceToReachEdge);
            scale = TaskView.getCurveScaleForInterpolation(interpolation);
        }
        mClipAnimationHelper.offsetTarget(scale, Utilities.isRtl(res) ? -offsetX : offsetX, offsetY,
                QuickScrubController.QUICK_SCRUB_START_INTERPOLATOR);
    }

    @WorkerThread
    public void updateDisplacement(float displacement) {
        // We are moving in the negative x/y direction
        displacement = -displacement;
        if (displacement > mTransitionDragLength) {
            mCurrentShift.updateValue(1);

            if (!mBgLongSwipeMode) {
                mBgLongSwipeMode = true;
                executeOnUiThread(this::onLongSwipeEnabledUi);
            }
            mLongSwipeDisplacement = displacement - mTransitionDragLength;
            executeOnUiThread(this::onLongSwipeDisplacementUpdated);
        } else {
            if (mBgLongSwipeMode) {
                mBgLongSwipeMode = false;
                executeOnUiThread(this::onLongSwipeDisabledUi);
            }
            float translation = Math.max(displacement, 0);
            float shift = mTransitionDragLength == 0 ? 0 : translation / mTransitionDragLength;
            mCurrentShift.updateValue(shift);
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
        mLauncherTransitionController.setPlayFraction(mCurrentShift.value);
    }

    @WorkerThread
    private void updateFinalShift() {
        float shift = mCurrentShift.value;

        RecentsAnimationControllerCompat controller = mRecentsAnimationWrapper.getController();
        if (controller != null) {

            mClipAnimationHelper.applyTransform(mRecentsAnimationWrapper.targetSet, shift,
                    Looper.myLooper() == mMainThreadHandler.getLooper()
                            ? mSyncTransactionApplier
                            : null);

            boolean passedThreshold = shift > 1 - UPDATE_SYSUI_FLAGS_THRESHOLD;
            mRecentsAnimationWrapper.setAnimationTargetsBehindSystemBars(!passedThreshold);
            if (mActivityControlHelper.shouldMinimizeSplitScreen()) {
                mRecentsAnimationWrapper.setSplitScreenMinimizedForTransaction(passedThreshold);
            }
        }

        executeOnUiThread(this::updateFinalShiftUi);
    }

    private void updateFinalShiftUi() {
        final boolean passed = mCurrentShift.value >= MIN_PROGRESS_FOR_OVERVIEW;
        if (passed != mPassedOverviewThreshold) {
            mPassedOverviewThreshold = passed;
            if (mInteractionType == INTERACTION_NORMAL && mRecentsView != null) {
                mRecentsView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
            }
        }

        if (mLauncherTransitionController == null || mLauncherTransitionController
                .getAnimationPlayer().isStarted()) {
            return;
        }
        mLauncherTransitionController.setPlayFraction(mCurrentShift.value);
    }

    public void onRecentsAnimationStart(RecentsAnimationControllerCompat controller,
            RemoteAnimationTargetSet targets, Rect homeContentInsets, Rect minimizedHomeBounds) {
        LauncherAppState appState = LauncherAppState.getInstanceNoCreate();
        InvariantDeviceProfile idp = appState == null ?
                new InvariantDeviceProfile(mContext) : appState.getInvariantDeviceProfile();
        DeviceProfile dp = idp.getDeviceProfile(mContext);
        final Rect overviewStackBounds;
        RemoteAnimationTargetCompat runningTaskTarget = targets.findTask(mRunningTaskId);

        if (minimizedHomeBounds != null && runningTaskTarget != null) {
            overviewStackBounds = mActivityControlHelper
                    .getOverviewWindowBounds(minimizedHomeBounds, runningTaskTarget);
            dp = dp.getMultiWindowProfile(mContext,
                    new Point(minimizedHomeBounds.width(), minimizedHomeBounds.height()));
            dp.updateInsets(homeContentInsets);
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
            Rect insets = new Rect();
            WindowManagerWrapper.getInstance().getStableInsets(insets);
            dp = dp.copy(mContext);
            dp.updateInsets(insets);
        }
        dp.updateIsSeascape(mContext.getSystemService(WindowManager.class));

        if (runningTaskTarget != null) {
            mClipAnimationHelper.updateSource(overviewStackBounds, runningTaskTarget);
        }
        mClipAnimationHelper.prepareAnimation(false /* isOpening */);
        initTransitionEndpoints(dp);

        mRecentsAnimationWrapper.setController(controller, targets);
        setStateOnUiThread(STATE_APP_CONTROLLER_RECEIVED);

        mPassedOverviewThreshold = false;
    }

    public void onRecentsAnimationCanceled() {
        mRecentsAnimationWrapper.setController(null, null);
        mActivityInitListener.unregister();
        setStateOnUiThread(STATE_GESTURE_CANCELLED | STATE_HANDLER_INVALIDATED);
    }

    public void onGestureStarted() {
        notifyGestureStartedAsync();
        setStateOnUiThread(mInteractionType == INTERACTION_NORMAL
                ? STATE_GESTURE_STARTED_QUICKSTEP : STATE_GESTURE_STARTED_QUICKSCRUB);
        mGestureStarted = true;
        mRecentsAnimationWrapper.hideCurrentInputMethod();
        mRecentsAnimationWrapper.enableInputConsumer();
    }

    /**
     * Notifies the launcher that the swipe gesture has started. This can be called multiple times
     * on both background and UI threads
     */
    @AnyThread
    private void notifyGestureStartedAsync() {
        final T curActivity = mActivity;
        if (curActivity != null) {
            // Once the gesture starts, we can no longer transition home through the button, so
            // reset the force override of the activity visibility
            mActivity.clearForceInvisibleFlag(STATE_HANDLER_INVISIBILITY_FLAGS);
        }
    }

    @WorkerThread
    public void onGestureEnded(float endVelocity) {
        float flingThreshold = mContext.getResources()
                .getDimension(R.dimen.quickstep_fling_threshold_velocity);
        boolean isFling = mGestureStarted && Math.abs(endVelocity) > flingThreshold;
        setStateOnUiThread(STATE_GESTURE_COMPLETED);

        mLogAction = isFling ? Touch.FLING : Touch.SWIPE;

        if (mBgLongSwipeMode) {
            executeOnUiThread(() -> onLongSwipeGestureFinishUi(endVelocity, isFling));
        } else {
            handleNormalGestureEnd(endVelocity, isFling);
        }
    }

    private void handleNormalGestureEnd(float endVelocity, boolean isFling) {
        float velocityPxPerMs = endVelocity / 1000;
        long duration = MAX_SWIPE_DURATION;
        float currentShift = mCurrentShift.value;
        final boolean goingToHome;
        float endShift;
        final float startShift;
        Interpolator interpolator = DEACCEL;
        if (!isFling) {
            goingToHome = currentShift >= MIN_PROGRESS_FOR_OVERVIEW && mGestureStarted;
            endShift = goingToHome ? 1 : 0;
            long expectedDuration = Math.abs(Math.round((endShift - currentShift)
                    * MAX_SWIPE_DURATION * SWIPE_DURATION_MULTIPLIER));
            duration = Math.min(MAX_SWIPE_DURATION, expectedDuration);
            startShift = currentShift;
            interpolator = goingToHome ? OVERSHOOT_1_2 : DEACCEL;
        } else {
            goingToHome = endVelocity < 0;
            endShift = goingToHome ? 1 : 0;
            startShift = Utilities.boundToRange(currentShift - velocityPxPerMs
                    * SINGLE_FRAME_MS / mTransitionDragLength, 0, 1);
            float minFlingVelocity = mContext.getResources()
                    .getDimension(R.dimen.quickstep_fling_min_velocity);
            if (Math.abs(endVelocity) > minFlingVelocity && mTransitionDragLength > 0) {
                if (goingToHome) {
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
        animateToProgress(startShift, endShift, duration, interpolator, goingToHome);
    }

    private void doLogGesture(boolean toLauncher) {
        DeviceProfile dp = mDp;
        if (dp == null) {
            // We probably never received an animation controller, skip logging.
            return;
        }
        final int direction;
        if (dp.isVerticalBarLayout()) {
            direction = (dp.isSeascape() ^ toLauncher) ? Direction.LEFT : Direction.RIGHT;
        } else {
            direction = toLauncher ? Direction.UP : Direction.DOWN;
        }

        int dstContainerType = toLauncher ? ContainerType.TASKSWITCHER : ContainerType.APP;
        UserEventDispatcher.newInstance(mContext, dp).logStateChangeAction(
                mLogAction, direction,
                ContainerType.NAVBAR, ContainerType.APP,
                dstContainerType,
                0);
    }

    /** Animates to the given progress, where 0 is the current app and 1 is overview. */
    private void animateToProgress(float start, float end, long duration,
            Interpolator interpolator, boolean goingToHome) {
        mRecentsAnimationWrapper.runOnInit(() -> animateToProgressInternal(start, end, duration,
                interpolator, goingToHome));
    }

    private void animateToProgressInternal(float start, float end, long duration,
            Interpolator interpolator, boolean goingToHome) {
        mIsGoingToHome = goingToHome;
        ObjectAnimator anim = mCurrentShift.animateToValue(start, end).setDuration(duration);
        anim.setInterpolator(interpolator);
        anim.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                setStateOnUiThread(mIsGoingToHome
                        ? (STATE_SCALED_CONTROLLER_RECENTS | STATE_CAPTURE_SCREENSHOT)
                        : STATE_SCALED_CONTROLLER_APP);
            }
        });
        anim.start();
        long startMillis = SystemClock.uptimeMillis();
        executeOnUiThread(() -> {
            // Animate the launcher components at the same time as the window, always on UI thread.
            if (mLauncherTransitionController != null && !mWasLauncherAlreadyVisible
                    && start != end && duration > 0) {
                // Adjust start progress and duration in case we are on a different thread.
                long elapsedMillis = SystemClock.uptimeMillis() - startMillis;
                elapsedMillis = Utilities.boundToRange(elapsedMillis, 0, duration);
                float elapsedProgress = (float) elapsedMillis / duration;
                float adjustedStart = Utilities.mapRange(elapsedProgress, start, end);
                long adjustedDuration = duration - elapsedMillis;
                // We want to use the same interpolator as the window, but need to adjust it to
                // interpolate over the remaining progress (end - start).
                mLauncherTransitionController.dispatchSetInterpolator(Interpolators.mapToProgress(
                        interpolator, adjustedStart, end));
                mLauncherTransitionController.getAnimationPlayer().setDuration(adjustedDuration);
                mLauncherTransitionController.getAnimationPlayer().start();
            }
        });
    }

    @UiThread
    private void resumeLastTaskForQuickstep() {
        setStateOnUiThread(STATE_RESUME_LAST_TASK);
        doLogGesture(false /* toLauncher */);
        reset();
    }

    @UiThread
    private void resumeLastTask() {
        mRecentsAnimationWrapper.finish(false /* toHome */, null);
    }

    public void reset() {
        if (mInteractionType != INTERACTION_QUICK_SCRUB) {
            // Only invalidate the handler if we are not quick scrubbing, otherwise, it will be
            // invalidated after the quick scrub ends
            setStateOnUiThread(STATE_HANDLER_INVALIDATED);
        }
    }

    private void invalidateHandler() {
        mCurrentShift.finishAnimation();

        if (mGestureEndCallback != null) {
            mGestureEndCallback.run();
        }

        mActivityInitListener.unregister();
        mInputConsumer.unregisterInputConsumer();
        mTaskSnapshot = null;
    }

    private void invalidateHandlerWithLauncher() {
        mLauncherTransitionController = null;
        mLayoutListener.finish();
        mActivityControlHelper.getAlphaProperty(mActivity).setValue(1);

        mRecentsView.setRunningTaskHidden(false);
        mRecentsView.setRunningTaskIconScaledDown(false /* isScaledDown */, false /* animate */);
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
        if (mWasLauncherAlreadyVisible && mLauncherTransitionController != null) {
            mLauncherTransitionController.setPlayFraction(1);
        }
    }

    private void switchToScreenshot() {
        boolean finishTransitionPosted = false;
        RecentsAnimationControllerCompat controller = mRecentsAnimationWrapper.getController();
        if (controller != null) {
            // Update the screenshot of the task
            if (mTaskSnapshot == null) {
                mTaskSnapshot = controller.screenshotTask(mRunningTaskId);
            }
            TaskView taskView = mRecentsView.updateThumbnail(mRunningTaskId, mTaskSnapshot);
            mRecentsView.setRunningTaskHidden(false);
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
            setStateOnUiThread(STATE_SCREENSHOT_CAPTURED);
        }
    }

    private void finishCurrentTransitionToHome() {
        synchronized (mRecentsAnimationWrapper) {
            mRecentsAnimationWrapper.finish(true /* toHome */,
                    () -> setStateOnUiThread(STATE_CURRENT_TASK_FINISHED));
        }
    }

    private void setupLauncherUiAfterSwipeUpAnimation() {
        if (mLauncherTransitionController != null) {
            mLauncherTransitionController.getAnimationPlayer().end();
            mLauncherTransitionController = null;
        }
        mActivityControlHelper.onSwipeUpComplete(mActivity);

        // Animate the first icon.
        mRecentsView.setRunningTaskIconScaledDown(false /* isScaledDown */, true /* animate */);
        mRecentsView.setSwipeDownShouldLaunchApp(true);

        RecentsModel.getInstance(mContext).onOverviewShown(false, TAG);

        doLogGesture(true /* toLauncher */);
        reset();
    }

    private void onQuickScrubStart() {
        if (!mQuickScrubController.prepareQuickScrub(TAG)) {
            mQuickScrubBlocked = true;
            setStateOnUiThread(STATE_RESUME_LAST_TASK | STATE_HANDLER_INVALIDATED);
            return;
        }
        if (mLauncherTransitionController != null) {
            mLauncherTransitionController.getAnimationPlayer().end();
            mLauncherTransitionController = null;
        }

        mActivityControlHelper.onQuickInteractionStart(mActivity, mRunningTaskInfo, false);

        // Inform the last progress in case we skipped before.
        mQuickScrubController.onQuickScrubProgress(mCurrentQuickScrubProgress);
    }

    private void onFinishedTransitionToQuickScrub() {
        if (mQuickScrubBlocked) {
            return;
        }
        mQuickScrubController.onFinishedTransitionToQuickScrub();

        mRecentsView.setRunningTaskIconScaledDown(false /* isScaledDown */, true /* animate */);
        RecentsModel.getInstance(mContext).onOverviewShown(false, TAG);
    }

    public void onQuickScrubProgress(float progress) {
        mCurrentQuickScrubProgress = progress;
        if (Looper.myLooper() != Looper.getMainLooper() || mQuickScrubController == null
                || mQuickScrubBlocked) {
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

    private void debugNewState(int stateFlag) {
        if (!DEBUG_STATES) {
            return;
        }

        int state = mStateCallback.getState();
        StringJoiner currentStateStr = new StringJoiner(", ", "[", "]");
        String stateFlagStr = "Unknown-" + stateFlag;
        for (int i = 0; i < STATES.length; i++) {
            if ((state & (i << i)) != 0) {
                currentStateStr.add(STATES[i]);
            }
            if (stateFlag == (1 << i)) {
                stateFlagStr = STATES[i] + " (" + stateFlag + ")";
            }
        }
        Log.d(TAG, "[" + System.identityHashCode(this) + "] Adding " + stateFlagStr + " to "
                + currentStateStr);
    }

    public void setGestureEndCallback(Runnable gestureEndCallback) {
        mGestureEndCallback = gestureEndCallback;
    }

    // Handling long swipe
    private void onLongSwipeEnabledUi() {
        mUiLongSwipeMode = true;
        checkLongSwipeCanEnter();
        checkLongSwipeCanStart();
    }

    private void onLongSwipeDisabledUi() {
        mUiLongSwipeMode = false;

        if (mLongSwipeController != null) {
            mLongSwipeController.destroy();
            setTargetAlphaProvider((t, a1) -> a1);

            // Rebuild animations
            buildAnimationController();
        }
    }

    private void onLongSwipeDisplacementUpdated() {
        if (!mUiLongSwipeMode || mLongSwipeController == null) {
            return;
        }

        mLongSwipeController.onMove(mLongSwipeDisplacement);
    }

    private void checkLongSwipeCanEnter() {
        if (!mUiLongSwipeMode || !mStateCallback.hasStates(LONG_SWIPE_ENTER_STATE)
                || !mActivityControlHelper.supportsLongSwipe(mActivity)) {
            return;
        }

        // We are entering long swipe mode, make sure the screen shot is captured.
        mStateCallback.setState(STATE_CAPTURE_SCREENSHOT);

    }

    private void checkLongSwipeCanStart() {
        if (!mUiLongSwipeMode || !mStateCallback.hasStates(LONG_SWIPE_START_STATE)
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
                mActivity, mRecentsAnimationWrapper.targetSet);
        onLongSwipeDisplacementUpdated();
        setTargetAlphaProvider(mLongSwipeController::getTargetAlpha);
    }

    private void onLongSwipeGestureFinishUi(float velocity, boolean isFling) {
        if (!mUiLongSwipeMode || mLongSwipeController == null) {
            mUiLongSwipeMode = false;
            handleNormalGestureEnd(velocity, isFling);
            return;
        }
        mUiLongSwipeMode = false;
        finishCurrentTransitionToHome();
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
        RecentsModel.getInstance(mContext).preloadAssistData(mRunningTaskId, mAssistData);
    }
}
