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

import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.allapps.AllAppsTransitionController.ALL_APPS_PROGRESS;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.quickstep.TouchConsumer.INTERACTION_NORMAL;
import static com.android.quickstep.TouchConsumer.INTERACTION_QUICK_SCRUB;
import static com.android.quickstep.TouchConsumer.INTERACTION_QUICK_SWITCH;
import static com.android.quickstep.TouchConsumer.isInteractionQuick;
import static com.android.systemui.shared.recents.utilities.Utilities.postAtFrontOfQueueAsynchronously;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_CLOSING;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.RectEvaluator;
import android.annotation.TargetApi;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.view.View;
import android.view.ViewTreeObserver.OnDrawListener;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherState;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.TraceHelper;
import com.android.quickstep.TouchConsumer.InteractionType;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.RecentsAnimationControllerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.TransactionCompat;
import com.android.systemui.shared.system.WindowManagerWrapper;

@TargetApi(Build.VERSION_CODES.O)
public class WindowTransformSwipeHandler extends BaseSwipeInteractionHandler {

    // Launcher UI related states
    private static final int STATE_LAUNCHER_PRESENT = 1 << 0;
    private static final int STATE_LAUNCHER_DRAWN = 1 << 1;
    private static final int STATE_ACTIVITY_MULTIPLIER_COMPLETE = 1 << 2;

    // Internal initialization states
    private static final int STATE_APP_CONTROLLER_RECEIVED = 1 << 3;

    // Interaction finish states
    private static final int STATE_SCALED_CONTROLLER_RECENTS = 1 << 4;
    private static final int STATE_SCALED_CONTROLLER_APP = 1 << 5;

    private static final int STATE_HANDLER_INVALIDATED = 1 << 6;
    private static final int STATE_GESTURE_STARTED = 1 << 7;

    private static final int LAUNCHER_UI_STATES =
            STATE_LAUNCHER_PRESENT | STATE_LAUNCHER_DRAWN | STATE_ACTIVITY_MULTIPLIER_COMPLETE;

    private static final long MAX_SWIPE_DURATION = 200;
    private static final long MIN_SWIPE_DURATION = 80;
    private static final int QUICK_SWITCH_SNAP_DURATION = 120;

    private static final float MIN_PROGRESS_FOR_OVERVIEW = 0.5f;

    private final Rect mStableInsets = new Rect();
    private final Rect mSourceRect = new Rect();
    private final Rect mTargetRect = new Rect();
    private final Rect mCurrentRect = new Rect();
    private final Rect mClipRect = new Rect();
    private final RectEvaluator mRectEvaluator = new RectEvaluator(mCurrentRect);
    private DeviceProfile mDp;
    private int mTransitionDragLength;

    // Shift in the range of [0, 1].
    // 0 => preview snapShot is completely visible, and hotseat is completely translated down
    // 1 => preview snapShot is completely aligned with the recents view and hotseat is completely
    // visible.
    private final AnimatedFloat mCurrentShift = new AnimatedFloat(this::updateFinalShift);

    private final MainThreadExecutor mMainExecutor = new MainThreadExecutor();

    private final Context mContext;
    private final int mRunningTaskId;

    private MultiStateCallback mStateCallback;
    private AnimatorPlaybackController mLauncherTransitionController;

    private Launcher mLauncher;
    private LauncherLayoutListener mLauncherLayoutListener;
    private RecentsView mRecentsView;
    private QuickScrubController mQuickScrubController;

    private Runnable mLauncherDrawnCallback;

    private boolean mWasLauncherAlreadyVisible;

    private float mCurrentDisplacement;
    private boolean mGestureStarted;

    private @InteractionType int mInteractionType = INTERACTION_NORMAL;
    private boolean mStartedQuickScrubFromHome;

    private final RecentsAnimationWrapper mRecentsAnimationWrapper = new RecentsAnimationWrapper();
    private Matrix mTmpMatrix = new Matrix();

    WindowTransformSwipeHandler(RunningTaskInfo runningTaskInfo, Context context) {
        mContext = context;
        mRunningTaskId = runningTaskInfo.id;

        WindowManagerWrapper.getInstance().getStableInsets(mStableInsets);

        DeviceProfile dp = LauncherAppState.getIDP(mContext).getDeviceProfile(mContext);
        // TODO: If in multi window mode, dp = dp.getMultiWindowProfile()
        dp = dp.copy(mContext);
        // TODO: Use different insets for multi-window mode
        dp.updateInsets(mStableInsets);

        initTransitionEndpoints(dp);
        initStateCallbacks();
    }

    private void initStateCallbacks() {
        mStateCallback = new MultiStateCallback();
        mStateCallback.addCallback(STATE_LAUNCHER_DRAWN | STATE_GESTURE_STARTED,
                this::initializeLauncherAnimationController);
        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_LAUNCHER_DRAWN,
                this::launcherFrameDrawn);
        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_GESTURE_STARTED,
                this::notifyGestureStarted);

        mStateCallback.addCallback(STATE_SCALED_CONTROLLER_APP | STATE_APP_CONTROLLER_RECEIVED,
                this::resumeLastTask);
        mStateCallback.addCallback(STATE_SCALED_CONTROLLER_RECENTS
                        | STATE_ACTIVITY_MULTIPLIER_COMPLETE
                        | STATE_APP_CONTROLLER_RECEIVED,
                this::switchToScreenshot);

        mStateCallback.addCallback(STATE_SCALED_CONTROLLER_RECENTS
                        | STATE_ACTIVITY_MULTIPLIER_COMPLETE,
                this::setupLauncherUiAfterSwipeUpAnimation);

        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_SCALED_CONTROLLER_APP,
                this::reset);
        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_SCALED_CONTROLLER_RECENTS,
                this::reset);

        mStateCallback.addCallback(STATE_HANDLER_INVALIDATED, this::invalidateHandler);
        mStateCallback.addCallback(STATE_LAUNCHER_PRESENT | STATE_HANDLER_INVALIDATED,
                this::invalidateHandlerWithLauncher);
    }

    private void setStateOnUiThread(int stateFlag) {
        Handler handler = mMainExecutor.getHandler();
        if (Looper.myLooper() == handler.getLooper()) {
            mStateCallback.setState(stateFlag);
        } else {
            postAtFrontOfQueueAsynchronously(handler, () -> mStateCallback.setState(stateFlag));
        }
    }

    private void initTransitionEndpoints(DeviceProfile dp) {
        mDp = dp;
        RecentsView.getPageRect(dp, mContext, mTargetRect);
        mSourceRect.set(0, 0, dp.widthPx - mStableInsets.left - mStableInsets.right,
                dp.heightPx - mStableInsets.top - mStableInsets.bottom);

        mTransitionDragLength = dp.hotseatBarSizePx + (dp.isVerticalBarLayout()
                ? (dp.hotseatBarSidePaddingPx + (dp.isSeascape() ? mStableInsets.left : mStableInsets.right))
                : mStableInsets.bottom);
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

    @Override
    protected boolean init(final Launcher launcher, boolean alreadyOnHome) {
        if (launcher == mLauncher) {
            return true;
        }
        if (mLauncher != null) {
            // The launcher may have been recreated as a result of device rotation.
            int oldState = mStateCallback.getState() & ~LAUNCHER_UI_STATES;
            initStateCallbacks();
            mStateCallback.setState(oldState);
            mLauncherLayoutListener.setHandler(null);
        }
        mWasLauncherAlreadyVisible = alreadyOnHome;
        mLauncher = launcher;

        LauncherState startState = mLauncher.getStateManager().getState();
        if (startState.disableRestore) {
            startState = mLauncher.getStateManager().getRestState();
        }
        mLauncher.getStateManager().setRestState(startState);

        AbstractFloatingView.closeAllOpenViews(launcher, alreadyOnHome);

        mRecentsView = mLauncher.getOverviewPanel();
        mLauncherLayoutListener = new LauncherLayoutListener(mLauncher);

        final int state;
        if (mWasLauncherAlreadyVisible) {
            DeviceProfile dp = mLauncher.getDeviceProfile();
            long accuracy = 2 * Math.max(dp.widthPx, dp.heightPx);
            mLauncherTransitionController = launcher.getStateManager()
                    .createAnimationToNewWorkspace(OVERVIEW, accuracy);
            mLauncherTransitionController.dispatchOnStart();
            mLauncherTransitionController.setPlayFraction(mCurrentShift.value);

            state = STATE_ACTIVITY_MULTIPLIER_COMPLETE | STATE_LAUNCHER_DRAWN
                    | STATE_LAUNCHER_PRESENT;
        } else {
            TraceHelper.beginSection("WTS-init");
            launcher.getStateManager().goToState(OVERVIEW, false);
            TraceHelper.partitionSection("WTS-init", "State changed");

            // TODO: Implement a better animation for fading in
            View rootView = launcher.getRootView();
            rootView.setAlpha(0);
            rootView.getViewTreeObserver().addOnDrawListener(new OnDrawListener() {

                @Override
                public void onDraw() {
                    TraceHelper.endSection("WTS-init", "Launcher frame is drawn");
                    rootView.post(() ->
                            rootView.getViewTreeObserver().removeOnDrawListener(this));
                    if (launcher != mLauncher) {
                        return;
                    }

                    mStateCallback.setState(STATE_LAUNCHER_DRAWN);
                }
            });
            state = STATE_LAUNCHER_PRESENT;
        }

        mRecentsView.showTask(mRunningTaskId);
        mLauncherLayoutListener.open();

        // Optimization
        // We are using the internal device profile as launcher may not have got the insets yet.
        if (!mDp.isVerticalBarLayout()) {
            // All-apps search box is visible in vertical bar layout.
            mLauncher.getAppsView().setVisibility(View.GONE);
        }

        mStateCallback.setState(state);
        return true;
    }

    public void setLauncherOnDrawCallback(Runnable callback) {
        mLauncherDrawnCallback = callback;
    }

    private void launcherFrameDrawn() {
        View rootView = mLauncher.getRootView();
        if (rootView.getAlpha() < 1) {
            if (mGestureStarted) {
                final MultiStateCallback callback = mStateCallback;
                rootView.animate().alpha(1)
                        .setDuration(getFadeInDuration())
                        .withEndAction(() -> callback.setState(STATE_ACTIVITY_MULTIPLIER_COMPLETE));
            } else {
                rootView.setAlpha(1);
                mStateCallback.setState(STATE_ACTIVITY_MULTIPLIER_COMPLETE);
            }
        }
        if (mLauncherDrawnCallback != null) {
            mLauncherDrawnCallback.run();
        }
    }

    private void initializeLauncherAnimationController() {
        mLauncherLayoutListener.setHandler(this);
        onLauncherLayoutChanged();
    }

    public void updateInteractionType(@InteractionType int interactionType) {
        Preconditions.assertUIThread();
        if (mInteractionType != INTERACTION_NORMAL) {
            throw new IllegalArgumentException(
                    "Can't change interaction type from " + mInteractionType);
        }
        if (!isInteractionQuick(interactionType)) {
            throw new IllegalArgumentException(
                    "Can't change interaction type to " + interactionType);
        }
        mInteractionType = interactionType;

        if (mLauncher != null) {
            updateUiForQuickScrub();
        }
    }

    private void updateUiForQuickScrub() {
        mStartedQuickScrubFromHome = mWasLauncherAlreadyVisible;
        mQuickScrubController = mRecentsView.getQuickScrubController();
        mQuickScrubController.onQuickScrubStart(mStartedQuickScrubFromHome);
        animateToProgress(1f, MAX_SWIPE_DURATION);
        if (mStartedQuickScrubFromHome) {
            mLauncherLayoutListener.setVisibility(View.INVISIBLE);
        }
    }

    @WorkerThread
    public void updateDisplacement(float displacement) {
        mCurrentDisplacement = displacement;

        float translation = Utilities.boundToRange(-mCurrentDisplacement, 0, mTransitionDragLength);
        float shift = mTransitionDragLength == 0 ? 0 : translation / mTransitionDragLength;
        mCurrentShift.updateValue(shift);
    }

    /**
     * Called by {@link #mLauncherLayoutListener} when launcher layout changes
     */
    public void onLauncherLayoutChanged() {
        WindowManagerWrapper.getInstance().getStableInsets(mStableInsets);
        initTransitionEndpoints(mLauncher.getDeviceProfile());

        if (!mWasLauncherAlreadyVisible) {
            float startProgress;
            AllAppsTransitionController controller = mLauncher.getAllAppsController();

            if (mLauncher.getDeviceProfile().isVerticalBarLayout()) {
                startProgress = 1;
            } else {
                float scrollRange = Math.max(controller.getShiftRange(), 1);
                startProgress = (mTransitionDragLength / scrollRange) + 1;
            }
            AnimatorSet anim = new AnimatorSet();
            ObjectAnimator shiftAnim = ObjectAnimator.ofFloat(controller, ALL_APPS_PROGRESS,
                    startProgress, OVERVIEW.getVerticalProgress(mLauncher));
            shiftAnim.setInterpolator(LINEAR);
            anim.play(shiftAnim);

            // TODO: Link this animation to state animation, so that it is cancelled
            // automatically on state change
            anim.setDuration(mTransitionDragLength * 2);
            mLauncherTransitionController =
                    AnimatorPlaybackController.wrap(anim, mTransitionDragLength * 2);
            mLauncherTransitionController.setPlayFraction(mCurrentShift.value);
        }
    }

    @WorkerThread
    private void updateFinalShift() {
        if (mStartedQuickScrubFromHome) {
            return;
        }

        float shift = mCurrentShift.value;

        synchronized (mRecentsAnimationWrapper) {
            if (mRecentsAnimationWrapper.controller != null) {
                mRectEvaluator.evaluate(shift, mSourceRect, mTargetRect);
                float scale = (float) mCurrentRect.width() / mSourceRect.width();

                mClipRect.left = mSourceRect.left;
                mClipRect.top = (int) (mStableInsets.top * shift);
                mClipRect.bottom = (int) (mDp.heightPx - (mStableInsets.bottom * shift));
                mClipRect.right = mSourceRect.right;

                mTmpMatrix.setScale(scale, scale, 0, 0);
                mTmpMatrix.postTranslate(mCurrentRect.left - mStableInsets.left * scale * shift,
                        mCurrentRect.top - mStableInsets.top * scale * shift);
                TransactionCompat transaction = new TransactionCompat();
                for (RemoteAnimationTargetCompat app : mRecentsAnimationWrapper.targets) {
                    if (app.mode == MODE_CLOSING) {
                        transaction.setMatrix(app.leash, mTmpMatrix)
                                .setWindowCrop(app.leash, mClipRect)
                                .show(app.leash);
                    }
                }
                transaction.apply();
            }
        }

        if (mLauncherTransitionController != null) {
            if (Looper.getMainLooper() == Looper.myLooper()) {
                mLauncherTransitionController.setPlayFraction(shift);
            } else {
                // The fling operation completed even before the launcher was drawn
                mMainExecutor.execute(() -> mLauncherTransitionController.setPlayFraction(shift));
            }
        }
    }

    public void setRecentsAnimation(RecentsAnimationControllerCompat controller,
            RemoteAnimationTargetCompat[] apps) {
        mRecentsAnimationWrapper.setController(controller, apps);
        setStateOnUiThread(STATE_APP_CONTROLLER_RECEIVED);
    }

    public void onGestureStarted() {
        if (mLauncher != null) {
            notifyGestureStarted();
        }

        setStateOnUiThread(STATE_GESTURE_STARTED);
        mGestureStarted = true;
    }

    /**
     * Notifies the launcher that the swipe gesture has started. This can be called multiple times
     * on both background and UI threads
     */
    private void notifyGestureStarted() {
        mLauncher.onQuickstepGestureStarted(mWasLauncherAlreadyVisible);
    }

    @WorkerThread
    public void onGestureEnded(float endVelocity) {
        Resources res = mContext.getResources();
        float flingThreshold = res.getDimension(R.dimen.quickstep_fling_threshold_velocity);
        boolean isFling = Math.abs(endVelocity) > flingThreshold;

        long duration = MAX_SWIPE_DURATION;
        final float endShift;
        if (!isFling) {
            endShift = mCurrentShift.value >= MIN_PROGRESS_FOR_OVERVIEW ? 1 : 0;
        } else {
            endShift = endVelocity < 0 ? 1 : 0;
            float minFlingVelocity = res.getDimension(R.dimen.quickstep_fling_min_velocity);
            if (Math.abs(endVelocity) > minFlingVelocity && mTransitionDragLength > 0) {
                float distanceToTravel = (endShift - mCurrentShift.value) * mTransitionDragLength;

                // we want the page's snap velocity to approximately match the velocity at
                // which the user flings, so we scale the duration by a value near to the
                // derivative of the scroll interpolator at zero, ie. 5.
                duration = 5 * Math.round(1000 * Math.abs(distanceToTravel / endVelocity));
            }
        }

        animateToProgress(endShift, duration);
    }

    /** Animates to the given progress, where 0 is the current app and 1 is overview. */
    private void animateToProgress(float progress, long duration) {
        ObjectAnimator anim = mCurrentShift.animateToValue(progress).setDuration(duration);
        anim.setInterpolator(Interpolators.SCROLL);
        anim.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                setStateOnUiThread((Float.compare(mCurrentShift.value, 0) == 0)
                        ? STATE_SCALED_CONTROLLER_APP : STATE_SCALED_CONTROLLER_RECENTS);
            }
        });
        anim.start();
    }

    @UiThread
    private void resumeLastTask() {
        mRecentsAnimationWrapper.finish(false /* toHome */);
    }

    public void reset() {
        setStateOnUiThread(STATE_HANDLER_INVALIDATED);
    }

    private void invalidateHandler() {
        mCurrentShift.cancelAnimation();

        if (mGestureEndCallback != null) {
            mGestureEndCallback.run();
        }

        clearReference();
    }

    private void invalidateHandlerWithLauncher() {
        mLauncherTransitionController = null;
        mLauncherLayoutListener.setHandler(null);
        mLauncherLayoutListener.close(false);
    }

    public void layoutListenerClosed() {
        if (mWasLauncherAlreadyVisible && mLauncherTransitionController != null) {
            mLauncherTransitionController.setPlayFraction(1);
        }
    }

    private void switchToScreenshot() {
        if (mInteractionType == INTERACTION_QUICK_SWITCH) {
            for (int i = mRecentsView.getFirstTaskIndex(); i < mRecentsView.getPageCount(); i++) {
                TaskView taskView = (TaskView) mRecentsView.getPageAt(i);
                if (taskView.getTask().key.id != mRunningTaskId) {
                    mRecentsView.snapToPage(i, QUICK_SWITCH_SNAP_DURATION);
                    taskView.postDelayed(() -> {taskView.launchTask(true);},
                            QUICK_SWITCH_SNAP_DURATION);
                    break;
                }
            }
        } else if (mInteractionType == INTERACTION_QUICK_SCRUB) {
            if (mQuickScrubController != null) {
                mQuickScrubController.snapToPageForCurrentQuickScrubSection();
            }
        } else {
            synchronized (mRecentsAnimationWrapper) {
                if (mRecentsAnimationWrapper.controller != null) {
                    TransactionCompat transaction = new TransactionCompat();
                    for (RemoteAnimationTargetCompat app : mRecentsAnimationWrapper.targets) {
                        if (app.mode == MODE_CLOSING) {
                            // Update the screenshot of the task
                            final ThumbnailData thumbnail =
                                    mRecentsAnimationWrapper.controller.screenshotTask(app.taskId);
                            mRecentsView.updateThumbnail(app.taskId, thumbnail);
                        }
                    }
                    transaction.apply();
                }
            }
            mRecentsAnimationWrapper.finish(true /* toHome */);
        }
    }

    private void setupLauncherUiAfterSwipeUpAnimation() {
        // Re apply state in case we did something funky during the transition.
        mLauncher.getStateManager().reapplyState();


        // Animate ui the first icon.
        View currentRecentsPage = mRecentsView.getPageAt(mRecentsView.getCurrentPage());
        if (currentRecentsPage instanceof TaskView) {
            ((TaskView) currentRecentsPage).animateIconToScale(1f);
        }
    }

    public void onQuickScrubEnd() {
        if (mQuickScrubController != null) {
            mQuickScrubController.onQuickScrubEnd();
        } else {
            // TODO:
        }
    }

    public void onQuickScrubProgress(float progress) {
        if (mQuickScrubController != null) {
            mQuickScrubController.onQuickScrubProgress(progress);
        } else {
            // TODO:
        }
    }
}
