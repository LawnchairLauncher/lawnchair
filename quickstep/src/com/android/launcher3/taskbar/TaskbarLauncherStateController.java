/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.taskbar;

import static com.android.app.animation.Interpolators.EMPHASIZED;
import static com.android.app.animation.Interpolators.FINAL_FRAME;
import static com.android.app.animation.Interpolators.INSTANT;
import static com.android.launcher3.Flags.enableScalingRevealHomeAnimation;
import static com.android.launcher3.Hotseat.ALPHA_CHANNEL_TASKBAR_ALIGNMENT;
import static com.android.launcher3.Hotseat.ALPHA_CHANNEL_TASKBAR_STASH;
import static com.android.launcher3.LauncherState.HOTSEAT_ICONS;
import static com.android.launcher3.Utilities.isRtl;
import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_APP;
import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_OVERVIEW;
import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_STASHED_LAUNCHER_STATE;
import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_STASHED_FOR_BUBBLES;
import static com.android.launcher3.taskbar.TaskbarStashController.UNLOCK_TRANSITION_MEMOIZATION_MS;
import static com.android.launcher3.taskbar.TaskbarViewController.ALPHA_INDEX_HOME;
import static com.android.launcher3.taskbar.bubbles.BubbleBarView.FADE_IN_ANIM_ALPHA_DURATION_MS;
import static com.android.launcher3.taskbar.bubbles.BubbleBarView.FADE_OUT_ANIM_POSITION_DURATION_MS;
import static com.android.launcher3.util.FlagDebugUtils.appendFlag;
import static com.android.launcher3.util.FlagDebugUtils.formatFlagChange;
import static com.android.quickstep.fallback.RecentsStateUtilsKt.toLauncherState;
import static com.android.quickstep.util.SystemUiFlagUtils.isTaskbarHidden;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_AWAKE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.SystemClock;
import android.util.Log;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Hotseat;
import com.android.launcher3.Hotseat.HotseatQsbAlphaId;
import com.android.launcher3.LauncherState;
import com.android.launcher3.QuickstepTransitionManager;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController.BubbleLauncherState;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;
import com.android.quickstep.RecentsAnimationCallbacks;
import com.android.quickstep.RecentsAnimationController;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.fallback.window.RecentsDisplayModel;
import com.android.quickstep.fallback.window.RecentsWindowFlags;
import com.android.quickstep.fallback.window.RecentsWindowManager;
import com.android.quickstep.util.ScalingWorkspaceRevealAnim;
import com.android.quickstep.util.SystemUiFlagUtils;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.animation.ViewRootSync;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags;
import com.android.wm.shell.shared.bubbles.BubbleBarLocation;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.StringJoiner;
import java.util.function.Consumer;

/**
 * Track LauncherState, RecentsAnimation, resumed state for task bar in one place here and animate
 * the task bar accordingly.
 */
public class TaskbarLauncherStateController {

    private static final String TAG = "TaskbarLauncherStateController";
    private static final boolean DEBUG = false;

    /** Launcher activity is visible and focused. */
    public static final int FLAG_VISIBLE = 1 << 0;

    /**
     * A external transition / animation is running that will result in FLAG_VISIBLE being set.
     **/
    public static final int FLAG_TRANSITION_TO_VISIBLE = 1 << 1;

    /**
     * Set while the launcher state machine is performing a state transition, see {@link
     * StateManager.StateListener}.
     */
    public static final int FLAG_LAUNCHER_IN_STATE_TRANSITION = 1 << 2;

    /**
     * Whether the screen is currently on, or is transitioning to be on.
     *
     * This is cleared as soon as the screen begins to transition off.
     */
    private static final int FLAG_AWAKE = 1 << 3;

    /**
     * Captures whether the launcher was active at the time the FLAG_AWAKE was cleared.
     * Always cleared when FLAG_AWAKE is set.
     * <p>
     * FLAG_RESUMED will be cleared when the device is asleep, since all apps get paused at this
     * point. Thus, this flag indicates whether the launcher will be shown when the device wakes up
     * again.
     */
    private static final int FLAG_LAUNCHER_WAS_ACTIVE_WHILE_AWAKE = 1 << 4;

    /**
     * Whether the device is currently locked.
     * <ul>
     *  <li>While locked, the taskbar is always stashed.<li/>
     *  <li>Navbar animations on FLAG_DEVICE_LOCKED transitions will get special treatment.</li>
     * </ul>
     */
    private static final int FLAG_DEVICE_LOCKED = 1 << 5;

    /**
     * Whether the complete taskbar is completely hidden (neither visible stashed or unstashed).
     * This is tracked to allow a nice transition of the taskbar before SysUI forces it away by
     * hiding the inset.
     *
     * This flag is predominanlty set while FLAG_DEVICE_LOCKED is set, thus the taskbar's invisible
     * resting state while hidden is stashed.
     */
    private static final int FLAG_TASKBAR_HIDDEN = 1 << 6;

    private static final int FLAGS_LAUNCHER_ACTIVE = FLAG_VISIBLE | FLAG_TRANSITION_TO_VISIBLE;
    /** Equivalent to an int with all 1s for binary operation purposes */
    private static final int FLAGS_ALL = ~0;

    private static final float TASKBAR_BG_ALPHA_LAUNCHER_NOT_ALIGNED_DELAY_MULT = 0.33f;
    private static final float TASKBAR_BG_ALPHA_NOT_LAUNCHER_NOT_ALIGNED_DELAY_MULT = 0.33f;
    private static final float TASKBAR_BG_ALPHA_LAUNCHER_IS_ALIGNED_DURATION_MULT = 0.25f;

    /**
     * Delay for the taskbar fade-in.
     *
     * Helps to avoid visual noise when unlocking successfully via SFPS, and the device transitions
     * to launcher directly. The delay avoids the navbar to become briefly visible. The duration
     * is the same as in SysUI, see http://shortn/_uNSbDoRUSr.
     */
    private static final long TASKBAR_SHOW_DELAY_MS = 250;

    private final AnimatedFloat mIconAlignment =
            new AnimatedFloat(this::onIconAlignmentRatioChanged);

    private TaskbarControllers mControllers;
    private AnimatedFloat mTaskbarBackgroundAlpha;
    private AnimatedFloat mTaskbarAlpha;
    private AnimatedFloat mTaskbarCornerRoundness;
    private MultiProperty mTaskbarAlphaForHome;
    private @Nullable Animator mHotseatTranslationXAnimation;
    private QuickstepLauncher mLauncher;

    private boolean mIsDestroyed = false;
    private Integer mPrevState;
    private int mState;
    private LauncherState mLauncherState = LauncherState.NORMAL;
    private boolean mSkipNextRecentsAnimEnd;

    // Time when FLAG_TASKBAR_HIDDEN was last cleared, SystemClock.elapsedRealtime (milliseconds).
    private long mLastRemoveTaskbarHiddenTimeMs = 0;
    /**
     * Time when FLAG_DEVICE_LOCKED was last cleared, plus
     * {@link TaskbarStashController#UNLOCK_TRANSITION_MEMOIZATION_MS}
     */
    private long mLastUnlockTransitionTimeout;

    private @Nullable TaskBarRecentsAnimationListener mTaskBarRecentsAnimationListener;

    private boolean mIsAnimatingToLauncher;

    private boolean mShouldDelayLauncherStateAnim;

    private @Nullable BubbleBarLocation mBubbleBarLocation;

    // We skip any view synchronizations during init/destroy.
    private boolean mCanSyncViews;

    private boolean mIsQsbInline;

    private RecentsAnimationCallbacks mRecentsAnimationCallbacks;

    private final DeviceProfile.OnDeviceProfileChangeListener mOnDeviceProfileChangeListener =
            new DeviceProfile.OnDeviceProfileChangeListener() {
                @Override
                public void onDeviceProfileChanged(DeviceProfile dp) {
                    if (mIsQsbInline && !dp.isQsbInline) {
                        // We only modify QSB alpha if isQsbInline = true. If we switch to a DP
                        // where isQsbInline = false, then we need to reset the alpha.
                        mLauncher.getHotseat().setQsbAlpha(1f, ALPHA_CHANNEL_TASKBAR_ALIGNMENT);
                    }
                    mIsQsbInline = dp.isQsbInline;
                    TaskbarLauncherStateController.this.updateIconAlphaForHome(
                            mTaskbarAlphaForHome.getValue(), ALPHA_CHANNEL_TASKBAR_ALIGNMENT);
                    TaskbarLauncherStateController.this.onBubbleBarLocationChanged(
                            mBubbleBarLocation, /* animate = */ false);
                }
            };

    private final StateManager.StateListener<LauncherState> mStateListener =
            new StateManager.StateListener<>() {

                @Override
                public void onStateTransitionStart(LauncherState toState) {
                    if (toState != mLauncherState) {
                        // Treat FLAG_LAUNCHER_IN_STATE_TRANSITION as a changed flag even if a
                        // previous state transition was already running, so we update the new
                        // target.
                        mPrevState &= ~FLAG_LAUNCHER_IN_STATE_TRANSITION;
                        mLauncherState = toState;
                    }
                    updateStateForFlag(FLAG_LAUNCHER_IN_STATE_TRANSITION, true);
                    if (!mShouldDelayLauncherStateAnim) {
                        if (toState == LauncherState.NORMAL) {
                            TaskbarActivityContext activity = mControllers.taskbarActivityContext;
                            boolean isPinnedTaskbarAndNotInDesktopMode =
                                    !activity.isInDesktopMode() && activity.isPinnedTaskbar();
                            applyState(QuickstepTransitionManager.getTaskbarToHomeDuration(
                                    isPinnedTaskbarAndNotInDesktopMode));
                        } else {
                            applyState();
                        }
                    }
                }

                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    mLauncherState = finalState;
                    updateStateForFlag(FLAG_LAUNCHER_IN_STATE_TRANSITION, false);
                    applyState();
                    updateOverviewDragState(finalState);
                }
            };

    private final StateManager.StateListener<RecentsState> mRecentsStateListener =
            new StateManager.StateListener<>() {

                @Override
                public void onStateTransitionStart(RecentsState toState) {
                    mStateListener.onStateTransitionStart(toLauncherState(toState));
                }

                @Override
                public void onStateTransitionComplete(RecentsState finalState) {
                    mStateListener.onStateTransitionComplete(toLauncherState(finalState));
                }
            };

    /**
     * Callback for when launcher state transition completes after user swipes to home.
     * @param finalState The final state of the transition.
     */
    public void onStateTransitionCompletedAfterSwipeToHome(LauncherState finalState) {
        // TODO(b/279514548) Cleans up bad state that can occur when user interacts with
        // taskbar on top of transparent activity.
        if ((finalState == LauncherState.NORMAL)
                && mLauncher.hasBeenResumed()) {
            updateStateForFlag(FLAG_VISIBLE, true);
            applyState();
        }
    }

    /** Initializes the controller instance, and applies the initial state immediately. */
    public void init(TaskbarControllers controllers, QuickstepLauncher launcher,
            @SystemUiStateFlags long sysuiStateFlags) {
        mCanSyncViews = false;

        mControllers = controllers;
        mLauncher = launcher;

        mIsQsbInline = mLauncher.getDeviceProfile().isQsbInline;

        mTaskbarBackgroundAlpha = mControllers.taskbarDragLayerController
                .getTaskbarBackgroundAlpha();
        mTaskbarAlpha = mControllers.taskbarDragLayerController.getTaskbarAlpha();
        mTaskbarCornerRoundness = mControllers.getTaskbarCornerRoundness();
        mTaskbarAlphaForHome = mControllers.taskbarViewController
                .getTaskbarIconAlpha().get(ALPHA_INDEX_HOME);

        resetIconAlignment();

        if (!mControllers.taskbarActivityContext.isPhoneMode()) {
            mLauncher.getStateManager().addStateListener(mStateListener);
            runForRecentsWindowManager(recentsWindowManager ->
                    recentsWindowManager.getStateManager().addStateListener(mRecentsStateListener));
        }
        mLauncherState = launcher.getStateManager().getState();
        updateStateForSysuiFlags(sysuiStateFlags, /*applyState*/ false);

        applyState(0);

        mCanSyncViews = !mControllers.taskbarActivityContext.isPhoneMode();
        mLauncher.addOnDeviceProfileChangeListener(mOnDeviceProfileChangeListener);
        updateOverviewDragState(mLauncherState);
    }

    public void onDestroy() {
        mIsDestroyed = true;
        mCanSyncViews = false;

        if (mRecentsAnimationCallbacks != null) {
            mRecentsAnimationCallbacks.removeListener(mTaskBarRecentsAnimationListener);
            mRecentsAnimationCallbacks = null;
        }

        mIconAlignment.finishAnimation();

        mLauncher.getHotseat().setIconsAlpha(1f, ALPHA_CHANNEL_TASKBAR_ALIGNMENT);
        mLauncher.getStateManager().removeStateListener(mStateListener);
        runForRecentsWindowManager(recentsWindowManager ->
                recentsWindowManager.getStateManager().removeStateListener(mRecentsStateListener));

        mCanSyncViews = !mControllers.taskbarActivityContext.isPhoneMode();
        mLauncher.removeOnDeviceProfileChangeListener(mOnDeviceProfileChangeListener);
    }

    /**
     * Creates a transition animation to the launcher activity.
     *
     * Warning: the resulting animation must be played, since this method has side effects on this
     * controller's state.
     */
    public Animator createAnimToLauncher(@NonNull LauncherState toState,
            @NonNull RecentsAnimationCallbacks callbacks, long duration) {
        // If going to overview, stash the task bar
        // If going home, align the icons to hotseat
        AnimatorSet animatorSet = new AnimatorSet();
        mRecentsAnimationCallbacks = callbacks;

        // Update stashed flags first to ensure goingToUnstashedLauncherState() returns correctly.
        TaskbarStashController stashController = mControllers.taskbarStashController;
        stashController.updateStateForFlag(FLAG_IN_STASHED_LAUNCHER_STATE,
                toState.isTaskbarStashed(mLauncher));
        if (DEBUG) {
            Log.d(TAG, "createAnimToLauncher - FLAG_IN_APP: " + false);
        }
        stashController.updateStateForFlag(FLAG_IN_APP, false);

        updateStateForFlag(FLAG_TRANSITION_TO_VISIBLE, true);
        mLauncherState = toState;
        animatorSet.play(stashController.createApplyStateAnimator(duration));
        animatorSet.play(applyState(duration, false));

        if (mTaskBarRecentsAnimationListener != null) {
            mTaskBarRecentsAnimationListener.endGestureStateOverride(
                    !mLauncher.isInState(LauncherState.OVERVIEW), false /*canceled*/);
        }
        mTaskBarRecentsAnimationListener = new TaskBarRecentsAnimationListener(callbacks);
        callbacks.addListener(mTaskBarRecentsAnimationListener);
        RecentsView recentsView = mControllers.uiController.getRecentsView();
        if (recentsView != null) {
            recentsView.setTaskLaunchListener(() -> mTaskBarRecentsAnimationListener
                    .endGestureStateOverride(true, false /*canceled*/));
            recentsView.setTaskLaunchCancelledRunnable(() -> {
                updateStateForUserFinishedToApp(false /* finishedToApp */);
            });
        }

        return animatorSet;
    }

    public boolean isAnimatingToLauncher() {
        return mIsAnimatingToLauncher;
    }

    public void setShouldDelayLauncherStateAnim(boolean shouldDelayLauncherStateAnim) {
        if (!shouldDelayLauncherStateAnim && mShouldDelayLauncherStateAnim) {
            // Animate the animation we have delayed immediately. This is usually triggered when
            // the user has released their finger.
            applyState();
        }
        mShouldDelayLauncherStateAnim = shouldDelayLauncherStateAnim;
    }

    /** Will make the next onRecentsAnimationFinished() a no-op. */
    public void setSkipNextRecentsAnimEnd() {
        mSkipNextRecentsAnimEnd = true;
    }

    /** SysUI flags updated, see QuickStepContract.SYSUI_STATE_* values. */
    public void updateStateForSysuiFlags(@SystemUiStateFlags long systemUiStateFlags) {
        updateStateForSysuiFlags(systemUiStateFlags, /* applyState */ true);
    }

    private void updateStateForSysuiFlags(@SystemUiStateFlags long systemUiStateFlags,
            boolean applyState) {
        final boolean prevIsAwake = hasAnyFlag(FLAG_AWAKE);
        final boolean currIsAwake = hasAnyFlag(systemUiStateFlags, SYSUI_STATE_AWAKE);

        updateStateForFlag(FLAG_AWAKE, currIsAwake);
        if (prevIsAwake != currIsAwake) {
            // The screen is switching between on/off. When turning off, capture whether the
            // launcher is active and memoize this state.
            updateStateForFlag(FLAG_LAUNCHER_WAS_ACTIVE_WHILE_AWAKE,
                    prevIsAwake && hasAnyFlag(FLAGS_LAUNCHER_ACTIVE));
        }

        updateStateForFlag(FLAG_DEVICE_LOCKED, SystemUiFlagUtils.isLocked(systemUiStateFlags));

        updateStateForFlag(FLAG_TASKBAR_HIDDEN, isTaskbarHidden(systemUiStateFlags));

        if (applyState) {
            applyState();
        }
    }

    /**
     * Updates overview drag state on various controllers based on {@link #mLauncherState}.
     *
     * @param launcherState The current state launcher is in
     */
    private void updateOverviewDragState(LauncherState launcherState) {
        boolean disallowLongClick = mLauncher.isSplitSelectionActive() || mIsAnimatingToLauncher;
        com.android.launcher3.taskbar.Utilities.setOverviewDragState(
                mControllers, launcherState.disallowTaskbarGlobalDrag(),
                disallowLongClick, launcherState.allowTaskbarInitialSplitSelection());
    }

    /**
     * Updates the proper flag to change the state of the task bar.
     *
     * Note that this only updates the flag. {@link #applyState()} needs to be called separately.
     *
     * @param flag    The flag to update.
     * @param enabled Whether to enable the flag
     */
    public void updateStateForFlag(int flag, boolean enabled) {
        if (enabled) {
            mState |= flag;
        } else {
            mState &= ~flag;
        }
    }

    private boolean hasAnyFlag(long flagMask) {
        return hasAnyFlag(mState, flagMask);
    }

    private boolean hasAnyFlag(long flags, long flagMask) {
        return (flags & flagMask) != 0;
    }

    public void applyState() {
        applyState(mControllers.taskbarStashController.getStashDuration());
    }

    public void applyState(long duration) {
        applyState(duration, true);
    }

    public Animator applyState(long duration, boolean start) {
        if (mIsDestroyed || mControllers.taskbarActivityContext.isPhoneMode()) {
            return null;
        }
        Animator animator = null;
        if (mPrevState == null || mPrevState != mState) {
            // If this is our initial state, treat all flags as changed.
            int changedFlags = mPrevState == null ? FLAGS_ALL : mPrevState ^ mState;

            if (DEBUG) {
                String stateString;
                if (mPrevState == null) {
                    stateString = getStateString(mState) + "(initial update)";
                } else {
                    stateString = formatFlagChange(mState, mPrevState,
                            TaskbarLauncherStateController::getStateString);
                }
                Log.d(TAG, "applyState: " + stateString
                        + ", duration: " + duration
                        + ", start: " + start);
            }
            mPrevState = mState;
            animator = onStateChangeApplied(changedFlags, duration, start);
        }
        return animator;
    }

    private Animator onStateChangeApplied(int changedFlags, long duration, boolean start) {
        final boolean isInLauncher = isInLauncher();
        final boolean isInOverview = mControllers.uiController.isInOverviewUi();
        final boolean isIconAlignedWithHotseat = isIconAlignedWithHotseat();
        final float toAlignment = isIconAlignedWithHotseat ? 1 : 0;
        boolean handleOpenFloatingViews = false;
        boolean isPinnedTaskbar =
                mControllers.taskbarActivityContext.isPinnedTaskbar();
        if (DEBUG) {
            Log.d(TAG, "onStateChangeApplied - isInLauncher: " + isInLauncher
                    + ", mLauncherState: " + mLauncherState
                    + ", toAlignment: " + toAlignment);
        }
        mControllers.bubbleControllers.ifPresent(controllers -> {
            // Show the bubble bar when on launcher home (hotseat icons visible) or in overview
            boolean onOverview = isInLauncher && mLauncherState == LauncherState.OVERVIEW;
            boolean hotseatIconsVisible = isInLauncher && mLauncherState.areElementsVisible(
                    mLauncher, HOTSEAT_ICONS);
            BubbleLauncherState state = onOverview
                    ? BubbleLauncherState.OVERVIEW
                    : hotseatIconsVisible
                            ? BubbleLauncherState.HOME
                            : BubbleLauncherState.IN_APP;
            controllers.bubbleStashController.setLauncherState(state);
        });

        TaskbarStashController stashController = mControllers.taskbarStashController;
        stashController.updateStateForFlag(FLAG_IN_OVERVIEW,
                mLauncherState == LauncherState.OVERVIEW);

        AnimatorSet animatorSet = new AnimatorSet();

        if (hasAnyFlag(changedFlags, FLAG_LAUNCHER_IN_STATE_TRANSITION)) {
            boolean launcherTransitionCompleted = !hasAnyFlag(FLAG_LAUNCHER_IN_STATE_TRANSITION);
            playStateTransitionAnim(animatorSet, duration, launcherTransitionCompleted);

            if (launcherTransitionCompleted
                    && mLauncherState == LauncherState.QUICK_SWITCH_FROM_HOME) {
                // We're about to be paused, set immediately to ensure seamless handoff.
                updateStateForFlag(FLAG_VISIBLE, false);
                applyState(0 /* duration */);
            }
            if (mLauncherState == LauncherState.NORMAL) {
                // We're changing state to home, should close open popups e.g. Taskbar AllApps
                handleOpenFloatingViews = true;
            }
            if (mLauncherState == LauncherState.OVERVIEW
                    && !mControllers.taskbarActivityContext.isPhoneMode()) {
                // Calling to update the insets in TaskbarInsetController#updateInsetsTouchability
                mControllers.taskbarActivityContext.notifyUpdateLayoutParams();
            }
        }

        if (hasAnyFlag(changedFlags, FLAGS_LAUNCHER_ACTIVE)) {
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    mIsAnimatingToLauncher = isInLauncher;

                    if (DEBUG) {
                        Log.d(TAG, "onAnimationStart - FLAG_IN_APP: " + !isInLauncher);
                    }
                    stashController.updateStateForFlag(FLAG_IN_APP, !isInLauncher);
                    stashController.applyState(duration);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mIsAnimatingToLauncher = false;
                }
            });

            // Handle closing open popups when going home/overview
            handleOpenFloatingViews = true;
        } else {
            stashController.applyState();
        }

        if (handleOpenFloatingViews && isInLauncher) {
            AbstractFloatingView.closeAllOpenViews(mControllers.taskbarActivityContext);
        }

        if (hasAnyFlag(changedFlags, FLAG_TASKBAR_HIDDEN) && !hasAnyFlag(FLAG_TASKBAR_HIDDEN)) {
            // Take note of the current time, as the taskbar is made visible again.
            mLastRemoveTaskbarHiddenTimeMs = SystemClock.elapsedRealtime();
        }

        boolean isHidden = hasAnyFlag(FLAG_TASKBAR_HIDDEN);
        float taskbarAlpha = isHidden ? 0 : 1;
        if (mTaskbarAlpha.isAnimating() || mTaskbarAlpha.value != taskbarAlpha) {
            Animator taskbarVisibility = mTaskbarAlpha.animateToValue(taskbarAlpha);

            taskbarVisibility.setDuration(duration);
            if (isHidden) {
                // Stash the transient taskbar once the taskbar is not visible. This reduces
                // visual noise when unlocking the device afterwards.
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        TaskbarStashController stashController =
                                mControllers.taskbarStashController;
                        stashController.updateAndAnimateTransientTaskbar(
                                /* stash */ true, /* bubblesShouldFollow */ true);
                    }
                });
            } else {
                // delay the fade in animation a bit to reduce visual noise when waking up a device
                // with a fingerprint reader. This should only be done when the device was woken
                // up via fingerprint reader, however since this information is currently not
                // available, opting to always delay the fade-in a bit.
                long durationSinceLastUnlockMs = SystemClock.elapsedRealtime()
                        - mLastRemoveTaskbarHiddenTimeMs;
                taskbarVisibility.setStartDelay(
                        Math.max(0, TASKBAR_SHOW_DELAY_MS - durationSinceLastUnlockMs));
            }
            animatorSet.play(taskbarVisibility);
        }

        float backgroundAlpha = isInLauncher && isTaskbarAlignedWithHotseat() ? 0 : 1;
        AnimatedFloat taskbarBgOffset =
                mControllers.taskbarDragLayerController.getTaskbarBackgroundOffset();
        boolean showTaskbar = shouldShowTaskbar(mControllers.taskbarActivityContext, isInLauncher,
                isInOverview);
        float taskbarBgOffsetEnd = showTaskbar ? 0f : 1f;
        float taskbarBgOffsetStart = showTaskbar ? 1f : 0f;

        // Don't animate if background has reached desired value.
        if (mTaskbarBackgroundAlpha.isAnimating()
                || mTaskbarBackgroundAlpha.value != backgroundAlpha
                || taskbarBgOffset.isAnimatingToValue(taskbarBgOffsetStart)
                || taskbarBgOffset.value != taskbarBgOffsetEnd) {
            mTaskbarBackgroundAlpha.cancelAnimation();
            if (DEBUG) {
                Log.d(TAG, "onStateChangeApplied - taskbarBackgroundAlpha - "
                        + mTaskbarBackgroundAlpha.value
                        + " -> " + backgroundAlpha + ": " + duration);
            }

            boolean isInLauncherIconNotAligned = isInLauncher && !isIconAlignedWithHotseat;
            boolean notInLauncherIconNotAligned = !isInLauncher && !isIconAlignedWithHotseat;
            boolean isInLauncherIconIsAligned = isInLauncher && isIconAlignedWithHotseat;
            // When Hotseat icons are not on top don't change duration or add start delay.
            // This will keep the duration in sync for icon alignment and background fade in/out.
            // For example, launching app from launcher all apps.
            boolean isHotseatIconOnTopWhenAligned =
                    mControllers.uiController.isHotseatIconOnTopWhenAligned();

            float startDelay = 0;
            // We want to delay the background from fading in so that the icons have time to move
            // into the bounds of the background before it appears.
            if (isInLauncherIconNotAligned) {
                startDelay = duration * TASKBAR_BG_ALPHA_LAUNCHER_NOT_ALIGNED_DELAY_MULT;
            } else if (notInLauncherIconNotAligned && isHotseatIconOnTopWhenAligned) {
                startDelay = duration * TASKBAR_BG_ALPHA_NOT_LAUNCHER_NOT_ALIGNED_DELAY_MULT;
            }
            float newDuration = duration - startDelay;
            if (isInLauncherIconIsAligned && isHotseatIconOnTopWhenAligned) {
                // Make the background fade out faster so that it is gone by the time the
                // icons move outside of the bounds of the background.
                newDuration = duration * TASKBAR_BG_ALPHA_LAUNCHER_IS_ALIGNED_DURATION_MULT;
            }
            Animator taskbarBackgroundAlpha = mTaskbarBackgroundAlpha.animateToValue(
                    backgroundAlpha);
            if (isPinnedTaskbar) {
                setupPinnedTaskbarAnimation(animatorSet, showTaskbar, taskbarBgOffset,
                        taskbarBgOffsetStart, taskbarBgOffsetEnd, duration, taskbarBackgroundAlpha);
            } else {
                taskbarBackgroundAlpha.setDuration((long) newDuration);
                taskbarBackgroundAlpha.setStartDelay((long) startDelay);
            }
            animatorSet.play(taskbarBackgroundAlpha);
        }

        float cornerRoundness = isInLauncher ? 0 : 1;

        if (mControllers.taskbarDesktopModeController.isInDesktopModeAndNotInOverview(
                mControllers.taskbarActivityContext.getDisplayId())
                && mControllers.getSharedState() != null) {
            cornerRoundness =
                    mControllers.taskbarDesktopModeController.getTaskbarCornerRoundness(
                            mControllers.getSharedState().showCornerRadiusInDesktopMode);
        }

        // Don't animate if corner roundness has reached desired value.
        if (mTaskbarCornerRoundness.isAnimating()
                || mTaskbarCornerRoundness.value != cornerRoundness) {
            mTaskbarCornerRoundness.cancelAnimation();
            if (DEBUG) {
                Log.d(TAG, "onStateChangeApplied - taskbarCornerRoundness - "
                        + mTaskbarCornerRoundness.value
                        + " -> " + cornerRoundness + ": " + duration);
            }
            animatorSet.play(mTaskbarCornerRoundness.animateToValue(cornerRoundness));
        }

        // Keep isUnlockTransition in sync with its counterpart in
        // TaskbarStashController#createAnimToIsStashed.
        boolean isUnlockTransition =
                hasAnyFlag(changedFlags, FLAG_DEVICE_LOCKED) && !hasAnyFlag(FLAG_DEVICE_LOCKED);
        if (isUnlockTransition) {
            // the launcher might not be resumed at the time the device is considered
            // unlocked (when the keyguard goes away), but possibly shortly afterwards.
            // To play the unlock transition at the time the unstash animation actually happens,
            // this memoizes the state transition for UNLOCK_TRANSITION_MEMOIZATION_MS.
            mLastUnlockTransitionTimeout =
                    SystemClock.elapsedRealtime() + UNLOCK_TRANSITION_MEMOIZATION_MS;
        }
        boolean isInUnlockTimeout = SystemClock.elapsedRealtime() < mLastUnlockTransitionTimeout;
        if (isUnlockTransition || isInUnlockTimeout) {
            // When transitioning to unlocked, ensure the hotseat is fully visible from the
            // beginning. The hotseat itself is animated by LauncherUnlockAnimationController.
            mIconAlignment.cancelAnimation();
            // updateValue ensures onIconAlignmentRatioChanged will be called if there is an actual
            // change in value
            mIconAlignment.updateValue(toAlignment);

            // Make sure FLAG_IN_APP is set when launching applications from keyguard.
            if (!isInLauncher) {
                mControllers.taskbarStashController.updateStateForFlag(FLAG_IN_APP, true);
                mControllers.taskbarStashController.applyState(0);
            }
        } else if (mIconAlignment.isAnimatingToValue(toAlignment)
                || mIconAlignment.isSettledOnValue(toAlignment)) {
            // Already at desired value, but make sure we run the callback at the end.
            animatorSet.addListener(AnimatorListeners.forEndCallback(() -> {
                if (!mIconAlignment.isAnimating()) {
                    onIconAlignmentRatioChanged();
                }
            }));
        } else {
            mIconAlignment.cancelAnimation();
            ObjectAnimator iconAlignAnim = mIconAlignment
                    .animateToValue(toAlignment)
                    .setDuration(duration);
            if (DEBUG) {
                Log.d(TAG, "onStateChangeApplied - iconAlignment - "
                        + mIconAlignment.value
                        + " -> " + toAlignment + ": " + duration);
            }
            if (!isPinnedTaskbar) {
                if (hasAnyFlag(FLAG_TASKBAR_HIDDEN)) {
                    iconAlignAnim.setInterpolator(FINAL_FRAME);
                } else {
                    animatorSet.play(iconAlignAnim);
                }
            }
        }

        Interpolator interpolator = enableScalingRevealHomeAnimation() && !isPinnedTaskbar
                ? ScalingWorkspaceRevealAnim.SCALE_INTERPOLATOR : EMPHASIZED;

        animatorSet.setInterpolator(interpolator);

        if (start) {
            animatorSet.start();
        }
        return animatorSet;
    }

    private static boolean shouldShowTaskbar(TaskbarActivityContext activityContext,
            boolean isInLauncher, boolean isInOverview) {
        if (activityContext.showDesktopTaskbarForFreeformDisplay()) {
            return true;
        }

        if (activityContext.showLockedTaskbarOnHome() && isInLauncher) {
            return true;
        }
        return !isInLauncher || isInOverview;
    }

    private void setupPinnedTaskbarAnimation(AnimatorSet animatorSet, boolean showTaskbar,
            AnimatedFloat taskbarBgOffset, float taskbarBgOffsetStart, float taskbarBgOffsetEnd,
            long duration, Animator taskbarBackgroundAlpha) {
        float targetAlpha = !showTaskbar ? 1 : 0;
        mLauncher.getHotseat().setIconsAlpha(targetAlpha, ALPHA_CHANNEL_TASKBAR_ALIGNMENT);
        if (mIsQsbInline) {
            mLauncher.getHotseat().setQsbAlpha(targetAlpha,
                    ALPHA_CHANNEL_TASKBAR_ALIGNMENT);
        }

        if ((taskbarBgOffset.value != taskbarBgOffsetEnd && !taskbarBgOffset.isAnimating())
                || taskbarBgOffset.isAnimatingToValue(taskbarBgOffsetStart)) {
            taskbarBgOffset.cancelAnimation();
            Animator taskbarIconAlpha = mTaskbarAlphaForHome.animateToValue(
                    showTaskbar ? 1f : 0f);
            AnimatedFloat taskbarIconTranslationYForHome =
                    mControllers.taskbarViewController.mTaskbarIconTranslationYForHome;
            ObjectAnimator taskbarBackgroundOffset = taskbarBgOffset.animateToValue(
                    taskbarBgOffsetStart,
                    taskbarBgOffsetEnd);
            ObjectAnimator taskbarIconsYTranslation = null;
            float taskbarHeight =
                    mControllers.taskbarActivityContext.getDeviceProfile().taskbarHeight;
            if (showTaskbar) {
                taskbarIconsYTranslation = taskbarIconTranslationYForHome.animateToValue(
                        taskbarHeight, 0);
            } else {
                taskbarIconsYTranslation = taskbarIconTranslationYForHome.animateToValue(0,
                        taskbarHeight);
            }

            taskbarIconAlpha.setDuration(duration);
            taskbarIconsYTranslation.setDuration(duration);
            taskbarBackgroundOffset.setDuration(duration);

            animatorSet.play(taskbarIconAlpha);
            animatorSet.play(taskbarIconsYTranslation);
            animatorSet.play(taskbarBackgroundOffset);
        }
        taskbarBackgroundAlpha.setInterpolator(showTaskbar ? INSTANT : FINAL_FRAME);
        taskbarBackgroundAlpha.setDuration(duration);
    }

    /**
     * Whether the taskbar is aligned with the hotseat in the current/target launcher state.
     *
     * This refers to the intended state - a transition to this state might be in progress.
     */
    public boolean isTaskbarAlignedWithHotseat() {
        if (mControllers.taskbarActivityContext.showDesktopTaskbarForFreeformDisplay()) {
            return false;
        }

        if (mControllers.taskbarActivityContext.showLockedTaskbarOnHome() && isInLauncher()) {
            return false;
        }

        return mLauncherState.isTaskbarAlignedWithHotseat(mLauncher);
    }

    /**
     * Returns if icons should be aligned to hotseat in the current transition
     */
    public boolean isIconAlignedWithHotseat() {
        if (isInLauncher()) {
            boolean isInStashedState = mLauncherState.isTaskbarStashed(mLauncher);
            boolean willStashVisually = isInStashedState
                    && mControllers.taskbarStashController.supportsVisualStashing();
            boolean isTaskbarAlignedWithHotseat = isTaskbarAlignedWithHotseat();
            return isTaskbarAlignedWithHotseat && !willStashVisually;
        } else {
            return false;
        }
    }

    /**
     * Returns if the current Launcher state has hotseat on top of other elemnets.
     */
    public boolean isInHotseatOnTopStates() {
        return mLauncherState != LauncherState.ALL_APPS
                && !mLauncher.getWorkspace().isOverlayShown();
    }

    boolean isInOverviewUi() {
        return mLauncherState.isRecentsViewVisible;
    }

    /**
     * Returns the current mLauncherState. Note that this could represent RecentsState as well, as
     * we convert those to equivalent LauncherStates even if Launcher Activity is not actually in
     * those states (for the case where the state is represented in a separate Window instead).
     */
    public LauncherState getLauncherState() {
        return mLauncherState;
    }

    private void playStateTransitionAnim(AnimatorSet animatorSet, long duration,
            boolean committed) {
        boolean isInStashedState = mLauncherState.isTaskbarStashed(mLauncher);
        TaskbarStashController stashController = mControllers.taskbarStashController;
        stashController.updateStateForFlag(FLAG_IN_STASHED_LAUNCHER_STATE, isInStashedState);
        Animator stashAnimator = stashController.createApplyStateAnimator(duration);
        if (stashAnimator != null) {
            stashAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (isInStashedState && committed) {
                        // Reset hotseat alpha to default
                        mLauncher.getHotseat().setIconsAlpha(1, ALPHA_CHANNEL_TASKBAR_ALIGNMENT);
                    }
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    float hotseatIconsAlpha = mLauncher.getHotseat()
                            .getIconsAlpha(ALPHA_CHANNEL_TASKBAR_ALIGNMENT)
                            .getValue();
                    if (hotseatIconsAlpha > 0) {
                        updateIconAlphaForHome(hotseatIconsAlpha, ALPHA_CHANNEL_TASKBAR_ALIGNMENT);
                    }
                }
            });
            animatorSet.play(stashAnimator);
        }

        // Translate back to 0 at a shorter or same duration as the icon alignment animation.
        // This ensures there is no jump after switching to hotseat, e.g. when swiping up from
        // overview to home. When not in app, we do duration / 2 just to make it feel snappier.
        long resetDuration = mControllers.taskbarStashController.isInApp()
                ? duration
                : duration / 2;
        if (!mControllers.taskbarTranslationController.willAnimateToZeroBefore(resetDuration)
                && (isAnimatingToLauncher() || mLauncherState == LauncherState.NORMAL)) {
            animatorSet.play(mControllers.taskbarTranslationController
                    .createAnimToResetTranslation(resetDuration));
        }
    }

    /** Whether the launcher is considered active. */
    private boolean isInLauncher() {
        if (hasAnyFlag(FLAG_AWAKE)) {
            return hasAnyFlag(FLAGS_LAUNCHER_ACTIVE);
        } else {
            return hasAnyFlag(FLAG_LAUNCHER_WAS_ACTIVE_WHILE_AWAKE);
        }
    }

    protected void stashHotseat(boolean stash) {
        // align taskbar with the hotseat icons before performing any animation
        mControllers.taskbarViewController.setLauncherIconAlignment(/* alignmentRatio = */ 1,
                mLauncher.getDeviceProfile());
        TaskbarStashController stashController = mControllers.taskbarStashController;
        stashController.updateStateForFlag(FLAG_STASHED_FOR_BUBBLES, stash);
        Runnable swapHotseatWithTaskbar = new Runnable() {
            @Override
            public void run() {
                updateIconAlphaForHome(stash ? 1 : 0, ALPHA_CHANNEL_TASKBAR_STASH);
            }
        };
        if (stash) {
            stashController.applyState();
            // if we stashing the hotseat we need to immediately swap it with the animating taskbar
            swapHotseatWithTaskbar.run();
        } else {
            // if we revert stashing make swap after taskbar animation is complete
            stashController.applyState(/* postApplyAction = */ swapHotseatWithTaskbar);
        }
    }

    protected void unStashHotseatInstantly() {
        TaskbarStashController stashController = mControllers.taskbarStashController;
        stashController.updateStateForFlag(FLAG_STASHED_FOR_BUBBLES, false);
        stashController.applyState(/* duration = */ 0);
        updateIconAlphaForHome(/* taskbarAlpha = */ 0,
                ALPHA_CHANNEL_TASKBAR_STASH, /* updateTaskbarAlpha = */ false);
    }

    /**
     * Resets and updates the icon alignment.
     */
    protected void resetIconAlignment() {
        mIconAlignment.finishAnimation();
        onIconAlignmentRatioChanged();
    }

    private void onIconAlignmentRatioChanged() {
        float currentValue = mTaskbarAlphaForHome.getValue();
        boolean taskbarWillBeVisible = mIconAlignment.value < 1;
        boolean firstFrameVisChanged = (taskbarWillBeVisible && Float.compare(currentValue, 1) != 0)
                || (!taskbarWillBeVisible && Float.compare(currentValue, 0) != 0);

        mControllers.taskbarViewController.setLauncherIconAlignment(
                mIconAlignment.value, mLauncher.getDeviceProfile());
        mControllers.navbarButtonsViewController.updateTaskbarAlignment(mIconAlignment.value);
        // Switch taskbar and hotseat in last frame and if taskbar is not hidden for bubbles
        boolean isHiddenForBubbles = mControllers.taskbarStashController.isHiddenForBubbles();
        updateIconAlphaForHome(taskbarWillBeVisible ? 1 : 0, ALPHA_CHANNEL_TASKBAR_ALIGNMENT,
                /* updateTaskbarAlpha = */ !isHiddenForBubbles);

        // Sync the first frame where we swap taskbar and hotseat.
        if (firstFrameVisChanged && mCanSyncViews && !Utilities.isRunningInTestHarness()) {
            ViewRootSync.synchronizeNextDraw(mLauncher.getHotseat(),
                    mControllers.taskbarActivityContext.getDragLayer(),
                    () -> {});
        }
    }

    private void updateIconAlphaForHome(float taskbarAlpha, @HotseatQsbAlphaId int alphaChannel) {
        updateIconAlphaForHome(taskbarAlpha, alphaChannel, /* updateTaskbarAlpha = */ true);
    }

    private void updateIconAlphaForHome(float taskbarAlpha,
            @HotseatQsbAlphaId int alphaChannel,
            boolean updateTaskbarAlpha) {
        if (mIsDestroyed) {
            return;
        }
        if (updateTaskbarAlpha) {
            mTaskbarAlphaForHome.setValue(taskbarAlpha);
        }
        boolean hotseatVisible = taskbarAlpha == 0
                || mControllers.taskbarActivityContext.isPhoneMode()
                || (!mControllers.uiController.isHotseatIconOnTopWhenAligned()
                && mIconAlignment.value > 0);
        /*
         * Hide Launcher Hotseat icons when Taskbar icons have opacity. Both icon sets
         * should not be visible at the same time.
         */
        float targetAlpha = hotseatVisible ? 1 : 0;
        mLauncher.getHotseat().setIconsAlpha(targetAlpha, alphaChannel);
        if (mIsQsbInline) {
            mLauncher.getHotseat().setQsbAlpha(targetAlpha, alphaChannel);
        }
    }

    /** Updates launcher home screen appearance accordingly to the bubble bar location. */
    public void onBubbleBarLocationChanged(@Nullable BubbleBarLocation location, boolean animate) {
        mBubbleBarLocation = location;
        if (location == null) {
            // bubble bar is not present, hence no location, resetting the hotseat
            updateHotseatAndQsbTranslationX(/* targetValue = */ 0, animate);
            mBubbleBarLocation = null;
            return;
        }
        DeviceProfile deviceProfile = mLauncher.getDeviceProfile();
        if (!deviceProfile.shouldAdjustHotseatOnNavBarLocationUpdate(
                mControllers.taskbarActivityContext)) {
            return;
        }
        boolean isBubblesOnLeft = location.isOnLeft(isRtl(mLauncher.getResources()));
        int targetX = deviceProfile
                .getHotseatTranslationXForNavBar(mLauncher, isBubblesOnLeft);
        updateHotseatAndQsbTranslationX(targetX, animate);
    }

    /** Used to translate hotseat and QSB to make room for bubbles. */
    private void updateHotseatAndQsbTranslationX(float targetValue, boolean animate) {
        // cancel existing animation
        if (mHotseatTranslationXAnimation != null) {
            mHotseatTranslationXAnimation.cancel();
            mHotseatTranslationXAnimation = null;
        }
        Hotseat hotseat = mLauncher.getHotseat();
        AnimatorSet translationXAnimation = new AnimatorSet();
        MultiProperty iconsTranslationX = mLauncher.getHotseat()
                .getIconsTranslationX(Hotseat.ICONS_TRANSLATION_X_NAV_BAR_ALIGNMENT);
        if (animate) {
            translationXAnimation.playTogether(iconsTranslationX.animateToValue(targetValue));
        } else {
            iconsTranslationX.setValue(targetValue);
        }
        float qsbTargetX = 0;
        if (mIsQsbInline) {
            qsbTargetX = targetValue;
        }
        MultiProperty qsbTranslationX = hotseat.getQsbTranslationX();
        if (qsbTranslationX != null) {
            if (animate) {
                translationXAnimation.playTogether(qsbTranslationX.animateToValue(qsbTargetX));
            } else {
                qsbTranslationX.setValue(qsbTargetX);
            }
        }
        if (!animate) {
            return;
        }
        mHotseatTranslationXAnimation = translationXAnimation;
        translationXAnimation.setStartDelay(FADE_OUT_ANIM_POSITION_DURATION_MS);
        translationXAnimation.setDuration(FADE_IN_ANIM_ALPHA_DURATION_MS);
        translationXAnimation.setInterpolator(Interpolators.EMPHASIZED);
        translationXAnimation.start();
    }

    private final class TaskBarRecentsAnimationListener implements
            RecentsAnimationCallbacks.RecentsAnimationListener {
        private final RecentsAnimationCallbacks mCallbacks;

        TaskBarRecentsAnimationListener(RecentsAnimationCallbacks callbacks) {
            mCallbacks = callbacks;
        }

        @Override
        public void onRecentsAnimationCanceled(HashMap<Integer, ThumbnailData> thumbnailDatas) {
            boolean isInOverview = mLauncher.isInState(LauncherState.OVERVIEW);
            endGestureStateOverride(!isInOverview, true /*canceled*/);
        }

        @Override
        public void onRecentsAnimationFinished(RecentsAnimationController controller) {
            endGestureStateOverride(!controller.getFinishTargetIsLauncher(),
                    controller.getLauncherIsVisibleAtFinish(), false /*canceled*/);
        }

        private void endGestureStateOverride(boolean finishedToApp, boolean canceled) {
            endGestureStateOverride(finishedToApp, finishedToApp, canceled);
        }

        /**
         * Handles whatever cleanup is needed after the recents animation is completed.
         * NOTE: If {@link #mSkipNextRecentsAnimEnd} is set and we're coming from a non-cancelled
         * path, this will not call {@link #updateStateForUserFinishedToApp(boolean)}
         *
         * @param finishedToApp {@code true} if the recents animation finished to showing an app and
         *                      not workspace or overview
         * @param launcherIsVisible {code true} if launcher is visible at finish
         * @param canceled      {@code true} if the recents animation was canceled instead of
         *                      finishing
         *                      to completion
         */
        private void endGestureStateOverride(boolean finishedToApp, boolean launcherIsVisible,
                boolean canceled) {
            mCallbacks.removeListener(this);
            mTaskBarRecentsAnimationListener = null;
            RecentsView recentsView = mControllers.uiController.getRecentsView();
            if (recentsView != null) {
                recentsView.setTaskLaunchListener(null);
            }

            if (mSkipNextRecentsAnimEnd && !canceled) {
                mSkipNextRecentsAnimEnd = false;
                return;
            }
            updateStateForUserFinishedToApp(finishedToApp, launcherIsVisible);
        }
    }

    /**
     * @see #updateStateForUserFinishedToApp(boolean, boolean)
     */
    private void updateStateForUserFinishedToApp(boolean finishedToApp) {
        updateStateForUserFinishedToApp(finishedToApp, !finishedToApp);
    }

    /**
     * Updates the visible state immediately to ensure a seamless handoff.
     *
     * @param finishedToApp True iff user is in an app.
     * @param launcherIsVisible True iff launcher is still visible (ie. transparent app)
     */
    private void updateStateForUserFinishedToApp(boolean finishedToApp,
            boolean launcherIsVisible) {
        // Update the visible state immediately to ensure a seamless handoff
        boolean launcherVisible = !finishedToApp || launcherIsVisible;
        updateStateForFlag(FLAG_TRANSITION_TO_VISIBLE, false);
        updateStateForFlag(FLAG_VISIBLE, launcherVisible);
        applyState();

        TaskbarStashController controller = mControllers.taskbarStashController;
        if (DEBUG) {
            Log.d(TAG, "endGestureStateOverride - FLAG_IN_APP: " + finishedToApp);
        }
        controller.updateStateForFlag(FLAG_IN_APP, finishedToApp && !launcherIsVisible);
        controller.applyState();
    }

    /**
     * Helper function to run a callback on the RecentsWindowManager (if it exists).
     */
    private void runForRecentsWindowManager(Consumer<RecentsWindowManager> callback) {
        if (RecentsWindowFlags.getEnableOverviewInWindow()) {
            final TaskbarActivityContext taskbarContext = mControllers.taskbarActivityContext;
            RecentsWindowManager recentsWindowManager = RecentsDisplayModel.getINSTANCE()
                    .get(taskbarContext).getRecentsWindowManager(taskbarContext.getDisplayId());
            if (recentsWindowManager != null) {
                callback.accept(recentsWindowManager);
            }
        }
    }

    private static String getStateString(int flags) {
        StringJoiner result = new StringJoiner("|");
        appendFlag(result, flags, FLAG_VISIBLE, "flag_visible");
        appendFlag(result, flags, FLAG_TRANSITION_TO_VISIBLE, "transition_to_visible");
        appendFlag(result, flags, FLAG_LAUNCHER_IN_STATE_TRANSITION,
                "launcher_in_state_transition");
        appendFlag(result, flags, FLAG_AWAKE, "awake");
        appendFlag(result, flags, FLAG_LAUNCHER_WAS_ACTIVE_WHILE_AWAKE,
                "was_active_while_awake");
        appendFlag(result, flags, FLAG_DEVICE_LOCKED, "device_locked");
        appendFlag(result, flags, FLAG_TASKBAR_HIDDEN, "taskbar_hidden");
        return result.toString();
    }

    protected void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarLauncherStateController:");
        pw.println(String.format(
                "%s\tmIconAlignment=%.2f",
                prefix,
                mIconAlignment.value));
        pw.println(String.format(
                "%s\tmTaskbarBackgroundAlpha=%.2f", prefix, mTaskbarBackgroundAlpha.value));
        pw.println(String.format(
                "%s\tmTaskbarAlphaForHome=%.2f", prefix, mTaskbarAlphaForHome.getValue()));
        pw.println(String.format("%s\tmPrevState=%s", prefix,
                mPrevState == null ? null : getStateString(mPrevState)));
        pw.println(String.format("%s\tmState=%s", prefix, getStateString(mState)));
        pw.println(String.format("%s\tmLauncherState=%s", prefix, mLauncherState));
        pw.println(String.format(
                "%s\tmIsAnimatingToLauncher=%b",
                prefix,
                mIsAnimatingToLauncher));
        pw.println(String.format(
                "%s\tmShouldDelayLauncherStateAnim=%b", prefix, mShouldDelayLauncherStateAnim));
    }
}
