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
import static com.android.launcher3.taskbar.TaskbarKeyguardController.MASK_ANY_SYSUI_LOCKED;
import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_APP;
import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_OVERVIEW;
import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_STASHED_LAUNCHER_STATE;
import static com.android.launcher3.taskbar.TaskbarViewController.ALPHA_INDEX_HOME;
import static com.android.launcher3.util.FlagDebugUtils.appendFlag;
import static com.android.launcher3.util.FlagDebugUtils.formatFlagChange;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_AWAKE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_DEVICE_DREAMING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_WAKEFULNESS_MASK;
import static com.android.systemui.shared.system.QuickStepContract.WAKEFULNESS_AWAKE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherState;
import com.android.launcher3.QuickstepTransitionManager;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;
import com.android.quickstep.RecentsAnimationCallbacks;
import com.android.quickstep.RecentsAnimationController;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.animation.ViewRootSync;
import com.android.systemui.shared.recents.model.ThumbnailData;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.StringJoiner;

/**
 * Track LauncherState, RecentsAnimation, resumed state for task bar in one place here and animate
 * the task bar accordingly.
 */
public class TaskbarLauncherStateController {

    private static final String TAG = TaskbarLauncherStateController.class.getSimpleName();
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
    private MultiProperty mIconAlphaForHome;
    private QuickstepLauncher mLauncher;

    private Integer mPrevState;
    private int mState;
    private LauncherState mLauncherState = LauncherState.NORMAL;

    // Time when FLAG_TASKBAR_HIDDEN was last cleared, SystemClock.elapsedRealtime (milliseconds).
    private long mLastUnlockTimeMs = 0;

    private @Nullable TaskBarRecentsAnimationListener mTaskBarRecentsAnimationListener;

    private boolean mIsAnimatingToLauncher;

    private boolean mShouldDelayLauncherStateAnim;

    // We skip any view synchronizations during init/destroy.
    private boolean mCanSyncViews;

    private boolean mIsQsbInline;

    private final DeviceProfile.OnDeviceProfileChangeListener mOnDeviceProfileChangeListener =
            new DeviceProfile.OnDeviceProfileChangeListener() {
                @Override
                public void onDeviceProfileChanged(DeviceProfile dp) {
                    if (mIsQsbInline && !dp.isQsbInline) {
                        // We only modify QSB alpha if isQsbInline = true. If we switch to a DP
                        // where isQsbInline = false, then we need to reset the alpha.
                        mLauncher.getHotseat().setQsbAlpha(1f);
                    }
                    mIsQsbInline = dp.isQsbInline;
                    TaskbarLauncherStateController.this.updateIconAlphaForHome(
                            mIconAlphaForHome.getValue());
                }
            };

    private final StateManager.StateListener<LauncherState> mStateListener =
            new StateManager.StateListener<LauncherState>() {

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
                            applyState(QuickstepTransitionManager.getTaskbarToHomeDuration());
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
            int sysuiStateFlags) {
        mCanSyncViews = false;

        mControllers = controllers;
        mLauncher = launcher;

        mIsQsbInline = mLauncher.getDeviceProfile().isQsbInline;

        mTaskbarBackgroundAlpha = mControllers.taskbarDragLayerController
                .getTaskbarBackgroundAlpha();
        mTaskbarAlpha = mControllers.taskbarDragLayerController.getTaskbarAlpha();
        mTaskbarCornerRoundness = mControllers.getTaskbarCornerRoundness();
        mIconAlphaForHome = mControllers.taskbarViewController
                .getTaskbarIconAlpha().get(ALPHA_INDEX_HOME);

        resetIconAlignment();

        mLauncher.getStateManager().addStateListener(mStateListener);
        mLauncherState = launcher.getStateManager().getState();
        updateStateForSysuiFlags(sysuiStateFlags, /*applyState*/ false);

        applyState(0);

        mCanSyncViews = true;
        mLauncher.addOnDeviceProfileChangeListener(mOnDeviceProfileChangeListener);
        updateOverviewDragState(mLauncherState);
    }

    public void onDestroy() {
        mCanSyncViews = false;

        mIconAlignment.finishAnimation();

        mLauncher.getHotseat().setIconsAlpha(1f);
        mLauncher.getStateManager().removeStateListener(mStateListener);

        mCanSyncViews = true;
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

        // Update stashed flags first to ensure goingToUnstashedLauncherState() returns correctly.
        TaskbarStashController stashController = mControllers.taskbarStashController;
        stashController.updateStateForFlag(FLAG_IN_STASHED_LAUNCHER_STATE,
                toState.isTaskbarStashed(mLauncher));
        if (DEBUG) {
            Log.d(TAG, "createAnimToLauncher - FLAG_IN_APP: " + false);
        }
        stashController.updateStateForFlag(FLAG_IN_APP, false);

        updateStateForFlag(FLAG_TRANSITION_TO_VISIBLE, true);
        animatorSet.play(stashController.createApplyStateAnimator(duration));
        animatorSet.play(applyState(duration, false));

        if (mTaskBarRecentsAnimationListener != null) {
            mTaskBarRecentsAnimationListener.endGestureStateOverride(
                    !mLauncher.isInState(LauncherState.OVERVIEW));
        }
        mTaskBarRecentsAnimationListener = new TaskBarRecentsAnimationListener(callbacks);
        callbacks.addListener(mTaskBarRecentsAnimationListener);
        ((RecentsView) mLauncher.getOverviewPanel()).setTaskLaunchListener(() ->
                mTaskBarRecentsAnimationListener.endGestureStateOverride(true));

        ((RecentsView) mLauncher.getOverviewPanel()).setTaskLaunchCancelledRunnable(() -> {
            updateStateForUserFinishedToApp(false /* finishedToApp */);
        });
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

    /** SysUI flags updated, see QuickStepContract.SYSUI_STATE_* values. */
    public void updateStateForSysuiFlags(int systemUiStateFlags) {
        updateStateForSysuiFlags(systemUiStateFlags, /* applyState */ true);
    }

    private void updateStateForSysuiFlags(int systemUiStateFlags, boolean applyState) {
        final boolean prevIsAwake = hasAnyFlag(FLAG_AWAKE);
        final boolean currIsAwake = hasAnyFlag(systemUiStateFlags, SYSUI_STATE_AWAKE);

        updateStateForFlag(FLAG_AWAKE, currIsAwake);
        if (prevIsAwake != currIsAwake) {
            // The screen is switching between on/off. When turning off, capture whether the
            // launcher is active and memoize this state.
            updateStateForFlag(FLAG_LAUNCHER_WAS_ACTIVE_WHILE_AWAKE,
                    prevIsAwake && hasAnyFlag(FLAGS_LAUNCHER_ACTIVE));
        }

        boolean isDeviceLocked = hasAnyFlag(systemUiStateFlags, MASK_ANY_SYSUI_LOCKED);
        updateStateForFlag(FLAG_DEVICE_LOCKED, isDeviceLocked);

        // Taskbar is hidden whenever the device is dreaming. The dreaming state includes the
        // interactive dreams, AoD, screen off. Since the SYSUI_STATE_DEVICE_DREAMING only kicks in
        // when the device is asleep, the second condition extends ensures that the transition from
        // and to the WAKEFULNESS_ASLEEP state also hide the taskbar, and improves the taskbar
        // hide/reveal animation timings.
        boolean isTaskbarHidden = hasAnyFlag(systemUiStateFlags, SYSUI_STATE_DEVICE_DREAMING)
                || (systemUiStateFlags & SYSUI_STATE_WAKEFULNESS_MASK) != WAKEFULNESS_AWAKE;
        updateStateForFlag(FLAG_TASKBAR_HIDDEN, isTaskbarHidden);

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
        boolean disallowLongClick =
                FeatureFlags.enableSplitContextually()
                        ? mLauncher.isSplitSelectionActive()
                        : launcherState == LauncherState.OVERVIEW_SPLIT_SELECT;
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

    private boolean hasAnyFlag(int flagMask) {
        return hasAnyFlag(mState, flagMask);
    }

    private boolean hasAnyFlag(int flags, int flagMask) {
        return (flags & flagMask) != 0;
    }

    public void applyState() {
        applyState(mControllers.taskbarStashController.getStashDuration());
    }

    public void applyState(long duration) {
        applyState(duration, true);
    }

    public Animator applyState(long duration, boolean start) {
        if (mControllers.taskbarActivityContext.isDestroyed()) {
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
        final boolean isIconAlignedWithHotseat = isIconAlignedWithHotseat();
        final float toAlignment = isIconAlignedWithHotseat ? 1 : 0;
        boolean handleOpenFloatingViews = false;
        if (DEBUG) {
            Log.d(TAG, "onStateChangeApplied - isInLauncher: " + isInLauncher
                    + ", mLauncherState: " + mLauncherState
                    + ", toAlignment: " + toAlignment);
        }
        mControllers.bubbleControllers.ifPresent(controllers -> {
            // Show the bubble bar when on launcher home or in overview.
            boolean onHome = isInLauncher && mLauncherState == LauncherState.NORMAL;
            boolean onOverview = mLauncherState == LauncherState.OVERVIEW;
            controllers.bubbleStashController.setBubblesShowingOnHome(onHome);
            controllers.bubbleStashController.setBubblesShowingOnOverview(onOverview);
        });

        mControllers.taskbarStashController.updateStateForFlag(FLAG_IN_OVERVIEW,
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
            if (mLauncherState == LauncherState.OVERVIEW) {
                // Calling to update the insets in TaskbarInsetController#updateInsetsTouchability
                mControllers.taskbarActivityContext.notifyUpdateLayoutParams();
            }
        }

        if (hasAnyFlag(changedFlags, FLAGS_LAUNCHER_ACTIVE)) {
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    mIsAnimatingToLauncher = isInLauncher;

                    TaskbarStashController stashController =
                            mControllers.taskbarStashController;
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
        }

        if (handleOpenFloatingViews && isInLauncher) {
            AbstractFloatingView.closeAllOpenViews(mControllers.taskbarActivityContext);
        }

        if (hasAnyFlag(changedFlags, FLAG_TASKBAR_HIDDEN) && !hasAnyFlag(FLAG_TASKBAR_HIDDEN)) {
            // Take note of the current time, as the taskbar is made visible again.
            mLastUnlockTimeMs = SystemClock.elapsedRealtime();
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
                long durationSinceLastUnlockMs = SystemClock.elapsedRealtime() - mLastUnlockTimeMs;
                taskbarVisibility.setStartDelay(
                        Math.max(0, TASKBAR_SHOW_DELAY_MS - durationSinceLastUnlockMs));
            }
            animatorSet.play(taskbarVisibility);
        }

        float backgroundAlpha = isInLauncher && isTaskbarAlignedWithHotseat() ? 0 : 1;

        // Don't animate if background has reached desired value.
        if (mTaskbarBackgroundAlpha.isAnimating()
                || mTaskbarBackgroundAlpha.value != backgroundAlpha) {
            mTaskbarBackgroundAlpha.cancelAnimation();
            if (DEBUG) {
                Log.d(TAG, "onStateChangeApplied - taskbarBackgroundAlpha - "
                        + mTaskbarBackgroundAlpha.value
                        + " -> " + backgroundAlpha + ": " + duration);
            }

            boolean isInLauncherIconNotAligned = isInLauncher && !isIconAlignedWithHotseat;
            boolean notInLauncherIconNotAligned = !isInLauncher && !isIconAlignedWithHotseat;
            boolean isInLauncherIconIsAligned = isInLauncher && isIconAlignedWithHotseat;

            float startDelay = 0;
            // We want to delay the background from fading in so that the icons have time to move
            // into the bounds of the background before it appears.
            if (isInLauncherIconNotAligned) {
                startDelay = duration * TASKBAR_BG_ALPHA_LAUNCHER_NOT_ALIGNED_DELAY_MULT;
            } else if (notInLauncherIconNotAligned) {
                startDelay = duration * TASKBAR_BG_ALPHA_NOT_LAUNCHER_NOT_ALIGNED_DELAY_MULT;
            }
            float newDuration = duration - startDelay;
            if (isInLauncherIconIsAligned) {
                // Make the background fade out faster so that it is gone by the time the
                // icons move outside of the bounds of the background.
                newDuration = duration * TASKBAR_BG_ALPHA_LAUNCHER_IS_ALIGNED_DURATION_MULT;
            }
            Animator taskbarBackgroundAlpha = mTaskbarBackgroundAlpha
                    .animateToValue(backgroundAlpha)
                    .setDuration((long) newDuration);
            taskbarBackgroundAlpha.setStartDelay((long) startDelay);
            animatorSet.play(taskbarBackgroundAlpha);
        }

        float cornerRoundness = isInLauncher ? 0 : 1;

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
            animatorSet.addListener(AnimatorListeners.forEndCallback(
                    this::onIconAlignmentRatioChanged));
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
            animatorSet.play(iconAlignAnim);
        }

        animatorSet.setInterpolator(EMPHASIZED);

        if (start) {
            animatorSet.start();
        }
        return animatorSet;
    }

    /**
     * Whether the taskbar is aligned with the hotseat in the current/target launcher state.
     *
     * This refers to the intended state - a transition to this state might be in progress.
     */
    public boolean isTaskbarAlignedWithHotseat() {
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
            boolean isTaskbarAlignedWithHotseat =
                    mLauncherState.isTaskbarAlignedWithHotseat(mLauncher);
            return isTaskbarAlignedWithHotseat && !willStashVisually;
        } else {
            return false;
        }
    }

    /**
     * Returns if the current Launcher state has hotseat on top of other elemnets.
     */
    public boolean isInHotseatOnTopStates() {
        return mLauncherState != LauncherState.ALL_APPS;
    }

    boolean isInOverview() {
        return mLauncherState == LauncherState.OVERVIEW;
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
                        mLauncher.getHotseat().setIconsAlpha(1);
                    }
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    if (mLauncher.getHotseat().getIconsAlpha() > 0) {
                        updateIconAlphaForHome(mLauncher.getHotseat().getIconsAlpha());
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

    /**
     * Resets and updates the icon alignment.
     */
    protected void resetIconAlignment() {
        mIconAlignment.finishAnimation();
        onIconAlignmentRatioChanged();
    }

    private void onIconAlignmentRatioChanged() {
        float currentValue = mIconAlphaForHome.getValue();
        boolean taskbarWillBeVisible = mIconAlignment.value < 1;
        boolean firstFrameVisChanged = (taskbarWillBeVisible && Float.compare(currentValue, 1) != 0)
                || (!taskbarWillBeVisible && Float.compare(currentValue, 0) != 0);

        mControllers.taskbarViewController.setLauncherIconAlignment(
                mIconAlignment.value, mLauncher.getDeviceProfile());
        mControllers.navbarButtonsViewController.updateTaskbarAlignment(mIconAlignment.value);
        // Switch taskbar and hotseat in last frame
        updateIconAlphaForHome(taskbarWillBeVisible ? 1 : 0);

        // Sync the first frame where we swap taskbar and hotseat.
        if (firstFrameVisChanged && mCanSyncViews && !Utilities.isRunningInTestHarness()) {
            ViewRootSync.synchronizeNextDraw(mLauncher.getHotseat(),
                    mControllers.taskbarActivityContext.getDragLayer(),
                    () -> {
                    });
        }
    }

    private void updateIconAlphaForHome(float alpha) {
        if (mControllers.taskbarActivityContext.isDestroyed()) {
            return;
        }
        mIconAlphaForHome.setValue(alpha);
        boolean hotseatVisible = alpha == 0
                || mControllers.taskbarActivityContext.isPhoneMode()
                || (!mControllers.uiController.isHotseatIconOnTopWhenAligned()
                && mIconAlignment.value > 0);
        /*
         * Hide Launcher Hotseat icons when Taskbar icons have opacity. Both icon sets
         * should not be visible at the same time.
         */
        mLauncher.getHotseat().setIconsAlpha(hotseatVisible ? 1 : 0);
        if (mIsQsbInline) {
            mLauncher.getHotseat().setQsbAlpha(hotseatVisible ? 1 : 0);
        }
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
            endGestureStateOverride(!isInOverview);
        }

        @Override
        public void onRecentsAnimationFinished(RecentsAnimationController controller) {
            endGestureStateOverride(!controller.getFinishTargetIsLauncher());
        }

        private void endGestureStateOverride(boolean finishedToApp) {
            mCallbacks.removeListener(this);
            mTaskBarRecentsAnimationListener = null;
            ((RecentsView) mLauncher.getOverviewPanel()).setTaskLaunchListener(null);

            updateStateForUserFinishedToApp(finishedToApp);
        }
    }

    /**
     * Updates the visible state immediately to ensure a seamless handoff.
     * @param finishedToApp True iff user is in an app.
     */
    private void updateStateForUserFinishedToApp(boolean finishedToApp) {
        // Update the visible state immediately to ensure a seamless handoff
        boolean launcherVisible = !finishedToApp;
        updateStateForFlag(FLAG_TRANSITION_TO_VISIBLE, false);
        updateStateForFlag(FLAG_VISIBLE, launcherVisible);
        applyState();

        TaskbarStashController controller = mControllers.taskbarStashController;
        if (DEBUG) {
            Log.d(TAG, "endGestureStateOverride - FLAG_IN_APP: " + finishedToApp);
        }
        controller.updateStateForFlag(FLAG_IN_APP, finishedToApp);
        controller.applyState();
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
                "%s\tmIconAlphaForHome=%.2f", prefix, mIconAlphaForHome.getValue()));
        pw.println(String.format("%s\tmPrevState=%s", prefix, getStateString(mPrevState)));
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
