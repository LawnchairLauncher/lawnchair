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

import static android.view.HapticFeedbackConstants.LONG_PRESS;

import static com.android.launcher3.anim.Interpolators.FINAL_FRAME;
import static com.android.launcher3.anim.Interpolators.INSTANT;
import static com.android.launcher3.config.FeatureFlags.FORCE_PERSISTENT_TASKBAR;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_LONGPRESS_HIDE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_LONGPRESS_SHOW;
import static com.android.launcher3.taskbar.Utilities.appendFlag;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BOUNCER_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_IME_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_IME_SWITCHER_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_QUICK_SETTINGS_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_SCREEN_PINNING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.Nullable;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.util.Log;
import android.view.InsetsController;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.internal.jank.InteractionJankMonitor;
import com.android.launcher3.Alarm;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;
import com.android.quickstep.SystemUiProxy;

import java.io.PrintWriter;
import java.util.StringJoiner;
import java.util.function.IntPredicate;

/**
 * Coordinates between controllers such as TaskbarViewController and StashedHandleViewController to
 * create a cohesive animation between stashed/unstashed states.
 */
public class TaskbarStashController implements TaskbarControllers.LoggableTaskbarController {

    private static final String TAG = "TaskbarStashController";

    public static final int FLAG_IN_APP = 1 << 0;
    public static final int FLAG_STASHED_IN_APP_MANUAL = 1 << 1; // long press, persisted
    public static final int FLAG_STASHED_IN_SYSUI_STATE = 1 << 2; // app pinning, keyguard, etc.
    public static final int FLAG_STASHED_IN_APP_EMPTY = 1 << 3; // no hotseat icons
    public static final int FLAG_STASHED_IN_APP_SETUP = 1 << 4; // setup wizard and AllSetActivity
    public static final int FLAG_STASHED_IN_APP_IME = 1 << 5; // IME is visible
    public static final int FLAG_IN_STASHED_LAUNCHER_STATE = 1 << 6;
    public static final int FLAG_STASHED_IN_TASKBAR_ALL_APPS = 1 << 7; // All apps is visible.
    public static final int FLAG_IN_SETUP = 1 << 8; // In the Setup Wizard
    public static final int FLAG_STASHED_SMALL_SCREEN = 1 << 9; // phone screen gesture nav, stashed
    public static final int FLAG_STASHED_IN_APP_AUTO = 1 << 10; // Autohide (transient taskbar).

    // If any of these flags are enabled, isInApp should return true.
    private static final int FLAGS_IN_APP = FLAG_IN_APP | FLAG_IN_SETUP;

    // If we're in an app and any of these flags are enabled, taskbar should be stashed.
    private static final int FLAGS_STASHED_IN_APP = FLAG_STASHED_IN_APP_MANUAL
            | FLAG_STASHED_IN_SYSUI_STATE | FLAG_STASHED_IN_APP_EMPTY | FLAG_STASHED_IN_APP_SETUP
            | FLAG_STASHED_IN_APP_IME | FLAG_STASHED_IN_TASKBAR_ALL_APPS
            | FLAG_STASHED_SMALL_SCREEN | FLAG_STASHED_IN_APP_AUTO;

    private static final int FLAGS_STASHED_IN_APP_IGNORING_IME =
            FLAGS_STASHED_IN_APP & ~FLAG_STASHED_IN_APP_IME;

    // If any of these flags are enabled, inset apps by our stashed height instead of our unstashed
    // height. This way the reported insets are consistent even during transitions out of the app.
    // Currently any flag that causes us to stash in an app is included, except for IME or All Apps
    // since those cover the underlying app anyway and thus the app shouldn't change insets.
    private static final int FLAGS_REPORT_STASHED_INSETS_TO_APP = FLAGS_STASHED_IN_APP
            & ~FLAG_STASHED_IN_APP_IME & ~FLAG_STASHED_IN_TASKBAR_ALL_APPS;

    /**
     * How long to stash/unstash when manually invoked via long press.
     */
    public static final long TASKBAR_STASH_DURATION =
            InsetsController.ANIMATION_DURATION_RESIZE;

    /**
     * How long to stash/unstash when keyboard is appearing/disappearing.
     */
    private static final long TASKBAR_STASH_DURATION_FOR_IME = 80;

    /**
     * The scale TaskbarView animates to when being stashed.
     */
    private static final float STASHED_TASKBAR_SCALE = 0.5f;

    /**
     * How long the hint animation plays, starting on motion down.
     */
    private static final long TASKBAR_HINT_STASH_DURATION =
            ViewConfiguration.DEFAULT_LONG_PRESS_TIMEOUT;

    /**
     * The scale that TaskbarView animates to when hinting towards the stashed state.
     */
    private static final float STASHED_TASKBAR_HINT_SCALE = 0.9f;

    /**
     * The scale that the stashed handle animates to when hinting towards the unstashed state.
     */
    private static final float UNSTASHED_TASKBAR_HANDLE_HINT_SCALE = 1.1f;

    /**
     * The SharedPreferences key for whether user has manually stashed the taskbar.
     */
    private static final String SHARED_PREFS_STASHED_KEY = "taskbar_is_stashed";

    /**
     * Whether taskbar should be stashed out of the box.
     */
    private static final boolean DEFAULT_STASHED_PREF = false;

    // Auto stashes when user has not interacted with the Taskbar after X ms.
    private static final long NO_TOUCH_TIMEOUT_TO_STASH_MS = 5000;

    private final TaskbarActivityContext mActivity;
    private final SharedPreferences mPrefs;
    private final int mStashedHeight;
    private final int mUnstashedHeight;
    private final SystemUiProxy mSystemUiProxy;

    // Initialized in init.
    private TaskbarControllers mControllers;
    // Taskbar background properties.
    private AnimatedFloat mTaskbarBackgroundOffset;
    private AnimatedFloat mTaskbarImeBgAlpha;
    // TaskbarView icon properties.
    private MultiProperty mIconAlphaForStash;
    private AnimatedFloat mIconScaleForStash;
    private AnimatedFloat mIconTranslationYForStash;
    // Stashed handle properties.
    private MultiProperty mTaskbarStashedHandleAlpha;
    private AnimatedFloat mTaskbarStashedHandleHintScale;

    /** Whether we are currently visually stashed (might change based on launcher state). */
    private boolean mIsStashed = false;
    private int mState;

    private @Nullable AnimatorSet mAnimator;
    private boolean mIsSystemGestureInProgress;
    private boolean mIsImeShowing;
    private boolean mIsImeSwitcherShowing;

    private boolean mEnableManualStashingDuringTests = false;

    private final Alarm mTimeoutAlarm = new Alarm();
    private boolean mEnableBlockingTimeoutDuringTests = false;

    // Evaluate whether the handle should be stashed
    private final StatePropertyHolder mStatePropertyHolder = new StatePropertyHolder(
            flags -> {
                boolean inApp = hasAnyFlag(flags, FLAGS_IN_APP);
                boolean stashedInApp = hasAnyFlag(flags, FLAGS_STASHED_IN_APP);
                boolean stashedLauncherState = hasAnyFlag(flags, FLAG_IN_STASHED_LAUNCHER_STATE);
                boolean stashedInTaskbarAllApps =
                        hasAnyFlag(flags, FLAG_STASHED_IN_TASKBAR_ALL_APPS);
                boolean stashedForSmallScreen = hasAnyFlag(flags, FLAG_STASHED_SMALL_SCREEN);
                return (inApp && stashedInApp) || (!inApp && stashedLauncherState)
                        || stashedInTaskbarAllApps || stashedForSmallScreen;
            });

    public TaskbarStashController(TaskbarActivityContext activity) {
        mActivity = activity;
        mPrefs = LauncherPrefs.getPrefs(mActivity);
        mSystemUiProxy = SystemUiProxy.INSTANCE.get(activity);
        if (isPhoneMode()) {
            // DeviceProfile's taskbar vars aren't initialized w/ the flag off
            Resources resources = mActivity.getResources();
            boolean isTransientTaskbar = DisplayController.isTransientTaskbar(mActivity);
            mUnstashedHeight = resources.getDimensionPixelSize(isTransientTaskbar
                    ? R.dimen.transient_taskbar_size
                    : R.dimen.taskbar_size);
            mStashedHeight = resources.getDimensionPixelSize(isTransientTaskbar
                    ? R.dimen.transient_taskbar_stashed_size
                    : R.dimen.taskbar_stashed_size);
        } else {
            mUnstashedHeight = mActivity.getDeviceProfile().taskbarSize;
            mStashedHeight = mActivity.getDeviceProfile().stashedTaskbarSize;
        }

    }

    public void init(TaskbarControllers controllers, boolean setupUIVisible) {
        mControllers = controllers;

        TaskbarDragLayerController dragLayerController = controllers.taskbarDragLayerController;
        mTaskbarBackgroundOffset = dragLayerController.getTaskbarBackgroundOffset();
        mTaskbarImeBgAlpha = dragLayerController.getImeBgTaskbar();

        TaskbarViewController taskbarViewController = controllers.taskbarViewController;
        mIconAlphaForStash = taskbarViewController.getTaskbarIconAlpha().get(
                TaskbarViewController.ALPHA_INDEX_STASH);
        mIconScaleForStash = taskbarViewController.getTaskbarIconScaleForStash();
        mIconTranslationYForStash = taskbarViewController.getTaskbarIconTranslationYForStash();

        StashedHandleViewController stashedHandleController =
                controllers.stashedHandleViewController;
        mTaskbarStashedHandleAlpha = stashedHandleController.getStashedHandleAlpha().get(
                StashedHandleViewController.ALPHA_INDEX_STASHED);
        mTaskbarStashedHandleHintScale = stashedHandleController.getStashedHandleHintScale();

        boolean isTransientTaskbar = DisplayController.isTransientTaskbar(mActivity);
        // We use supportsVisualStashing() here instead of supportsManualStashing() because we want
        // it to work properly for tests that recreate taskbar. This check is here just to ensure
        // that taskbar unstashes when going to 3 button mode (supportsVisualStashing() false).
        boolean isManuallyStashedInApp = supportsVisualStashing()
                && !isTransientTaskbar
                && !FORCE_PERSISTENT_TASKBAR.get()
                && mPrefs.getBoolean(SHARED_PREFS_STASHED_KEY, DEFAULT_STASHED_PREF);
        boolean isInSetup = !mActivity.isUserSetupComplete() || setupUIVisible;
        updateStateForFlag(FLAG_STASHED_IN_APP_MANUAL, isManuallyStashedInApp);
        updateStateForFlag(FLAG_STASHED_IN_APP_AUTO, isTransientTaskbar);
        updateStateForFlag(FLAG_STASHED_IN_APP_SETUP, isInSetup);
        updateStateForFlag(FLAG_IN_SETUP, isInSetup);
        updateStateForFlag(FLAG_STASHED_SMALL_SCREEN, isPhoneMode()
                && !mActivity.isThreeButtonNav());
        // For now, assume we're in an app, since LauncherTaskbarUIController won't be able to tell
        // us that we're paused until a bit later. This avoids flickering upon recreating taskbar.
        updateStateForFlag(FLAG_IN_APP, true);
        applyState(/* duration = */ 0);

        notifyStashChange(/* visible */ false, /* stashed */ isStashedInApp());
    }

    /**
     * Returns whether the taskbar can visually stash into a handle based on the current device
     * state.
     */
    public boolean supportsVisualStashing() {
        return !mActivity.isThreeButtonNav() && mControllers.uiController.supportsVisualStashing();
    }

    /**
     * Returns whether the user can manually stash the taskbar based on the current device state.
     */
    protected boolean supportsManualStashing() {
        if (FORCE_PERSISTENT_TASKBAR.get()) {
            return false;
        }
        return supportsVisualStashing()
                && isInApp()
                && (!Utilities.IS_RUNNING_IN_TEST_HARNESS || mEnableManualStashingDuringTests)
                && !DisplayController.isTransientTaskbar(mActivity);
    }

    /**
     * Enables support for manual stashing. This should only be used to add this functionality
     * to Launcher specific tests.
     */
    @VisibleForTesting
    public void enableManualStashingDuringTests(boolean enableManualStashing) {
        mEnableManualStashingDuringTests = enableManualStashing;
    }

    /**
     * Enables the auto timeout for taskbar stashing. This method should only be used for taskbar
     * testing.
     */
    @VisibleForTesting
    public void enableBlockingTimeoutDuringTests(boolean enableBlockingTimeout) {
        mEnableBlockingTimeoutDuringTests = enableBlockingTimeout;
    }

    /**
     * Sets the flag indicating setup UI is visible
     */
    protected void setSetupUIVisible(boolean isVisible) {
        boolean hideTaskbar = isVisible || !mActivity.isUserSetupComplete();
        updateStateForFlag(FLAG_IN_SETUP, hideTaskbar);
        updateStateForFlag(FLAG_STASHED_IN_APP_SETUP, hideTaskbar);
        applyState(hideTaskbar ? 0 : TASKBAR_STASH_DURATION);
    }

    /**
     * Returns whether the taskbar is currently visually stashed.
     */
    public boolean isStashed() {
        return mIsStashed;
    }

    /**
     * Returns whether the taskbar should be stashed in apps (e.g. user long pressed to stash).
     */
    public boolean isStashedInApp() {
        return hasAnyFlag(FLAGS_STASHED_IN_APP);
    }

    /**
     * Returns whether the taskbar should be stashed in apps regardless of the IME visibility.
     */
    public boolean isStashedInAppIgnoringIme() {
        return hasAnyFlag(FLAGS_STASHED_IN_APP_IGNORING_IME);
    }

    /**
     * Returns whether the taskbar should be stashed in the current LauncherState.
     */
    public boolean isInStashedLauncherState() {
        return (hasAnyFlag(FLAG_IN_STASHED_LAUNCHER_STATE) && supportsVisualStashing());
    }

    /**
     * @return {@code true} if we're not on a large screen AND using gesture nav
     */
    private boolean isPhoneMode() {
        return TaskbarManager.isPhoneMode(mActivity.getDeviceProfile());
    }

    private boolean hasAnyFlag(int flagMask) {
        return hasAnyFlag(mState, flagMask);
    }

    private boolean hasAnyFlag(int flags, int flagMask) {
        return (flags & flagMask) != 0;
    }


    /**
     * Returns whether the taskbar is currently visible and in an app.
     */
    public boolean isInAppAndNotStashed() {
        return !mIsStashed && isInApp();
    }

    public boolean isInApp() {
        return hasAnyFlag(FLAGS_IN_APP);
    }

    /**
     * Returns the height that taskbar will be touchable.
     */
    public int getTouchableHeight() {
        return mIsStashed ? mStashedHeight : mUnstashedHeight;
    }

    /**
     * Returns the height that taskbar will inset when inside apps.
     * @see WindowInsets.Type#navigationBars()
     * @see WindowInsets.Type#systemBars()
     */
    public int getContentHeightToReportToApps() {
        if ((isPhoneMode() && !mActivity.isThreeButtonNav())
                || DisplayController.isTransientTaskbar(mActivity)) {
            return getStashedHeight();
        }

        if (supportsVisualStashing() && hasAnyFlag(FLAGS_REPORT_STASHED_INSETS_TO_APP)) {
            DeviceProfile dp = mActivity.getDeviceProfile();
            if (hasAnyFlag(FLAG_STASHED_IN_APP_SETUP) && dp.isTaskbarPresent) {
                // We always show the back button in SUW but in portrait the SUW layout may not
                // be wide enough to support overlapping the nav bar with its content.
                // We're sending different res values in portrait vs landscape
                return mActivity.getResources().getDimensionPixelSize(R.dimen.taskbar_suw_insets);
            }
            boolean isAnimating = mAnimator != null && mAnimator.isStarted();
            if (!mControllers.stashedHandleViewController.isStashedHandleVisible()
                    && isInApp()
                    && !isAnimating) {
                // We are in a settled state where we're not showing the handle even though taskbar
                // is stashed. This can happen for example when home button is disabled (see
                // StashedHandleViewController#setIsHomeButtonDisabled()).
                return 0;
            }
            return mStashedHeight;
        }

        return mUnstashedHeight;
    }

    /**
     * Returns the height that taskbar will inset when inside apps.
     * @see WindowInsets.Type#tappableElement()
     */
    public int getTappableHeightToReportToApps() {
        int contentHeight = getContentHeightToReportToApps();
        return contentHeight <= mStashedHeight ? 0 : contentHeight;
    }

    public int getStashedHeight() {
        return mStashedHeight;
    }

    /**
     * Stash or unstashes the transient taskbar.
     */
    public void updateAndAnimateTransientTaskbar(boolean stash) {
        if (!DisplayController.isTransientTaskbar(mActivity)) {
            return;
        }

        if (hasAnyFlag(FLAG_STASHED_IN_APP_AUTO) != stash) {
            updateStateForFlag(FLAG_STASHED_IN_APP_AUTO, stash);
            applyState();
        }
    }

    /**
     * Should be called when long pressing the nav region when taskbar is present.
     * @return Whether taskbar was stashed and now is unstashed.
     */
    public boolean onLongPressToUnstashTaskbar() {
        if (!isStashed()) {
            // We only listen for long press on the nav region to unstash the taskbar. To stash the
            // taskbar, we use an OnLongClickListener on TaskbarView instead.
            return false;
        }
        if (!canCurrentlyManuallyUnstash()) {
            return false;
        }
        if (updateAndAnimateIsManuallyStashedInApp(false)) {
            mControllers.taskbarActivityContext.getDragLayer().performHapticFeedback(LONG_PRESS);
            return true;
        }
        return false;
    }

    /**
     * Returns whether taskbar will unstash when long pressing it based on the current state. The
     * only time this is true is if the user is in an app and the taskbar is only stashed because
     * the user previously long pressed to manually stash (not due to other reasons like IME).
     */
    private boolean canCurrentlyManuallyUnstash() {
        return (mState & (FLAG_IN_APP | FLAGS_STASHED_IN_APP))
                == (FLAG_IN_APP | FLAG_STASHED_IN_APP_MANUAL);
    }

    /**
     * Updates whether we should stash the taskbar when in apps, and animates to the changed state.
     * @return Whether we started an animation to either be newly stashed or unstashed.
     */
    public boolean updateAndAnimateIsManuallyStashedInApp(boolean isManuallyStashedInApp) {
        if (!supportsManualStashing()) {
            return false;
        }
        if (hasAnyFlag(FLAG_STASHED_IN_APP_MANUAL) != isManuallyStashedInApp) {
            mPrefs.edit().putBoolean(SHARED_PREFS_STASHED_KEY, isManuallyStashedInApp).apply();
            updateStateForFlag(FLAG_STASHED_IN_APP_MANUAL, isManuallyStashedInApp);
            applyState();
            return true;
        }
        return false;
    }

    /**
     * Adds the Taskbar unstash to Hotseat animator to the animator set.
     *
     * This should be used to run a Taskbar unstash to Hotseat animation whose progress matches a
     * swipe progress.
     *
     * @param placeholderDuration a placeholder duration to be used to ensure all full-length
     *                            sub-animations are properly coordinated. This duration should not
     *                            actually be used since this animation tracks a swipe progress.
     */
    protected void addUnstashToHotseatAnimation(AnimatorSet animation, int placeholderDuration) {
        createAnimToIsStashed(
                /* isStashed= */ false,
                placeholderDuration,
                /* startDelay= */ 0,
                /* animateBg= */ false,
                /* changedFlags=*/ 0);
        animation.play(mAnimator);
    }

    /**
     * Create a stash animation and save to {@link #mAnimator}.
     * @param isStashed whether it's a stash animation or an unstash animation
     * @param duration duration of the animation
     * @param startDelay how many milliseconds to delay the animation after starting it.
     * @param animateBg whether the taskbar's background should be animated
     */
    private void createAnimToIsStashed(boolean isStashed, long duration, long startDelay,
            boolean animateBg, int changedFlags) {
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        mAnimator = new AnimatorSet();
        addJankMonitorListener(mAnimator, /* appearing= */ !mIsStashed);
        final float stashTranslation = isPhoneMode() ? 0 :
                (mUnstashedHeight - mStashedHeight) / 2f;

        if (!supportsVisualStashing()) {
            // Just hide/show the icons and background instead of stashing into a handle.
            mAnimator.play(mIconAlphaForStash.animateToValue(isStashed ? 0 : 1)
                    .setDuration(duration));
            mAnimator.playTogether(mTaskbarBackgroundOffset.animateToValue(isStashed ? 1 : 0)
                    .setDuration(duration));
            mAnimator.playTogether(mIconTranslationYForStash.animateToValue(isStashed ?
                            stashTranslation : 0)
                    .setDuration(duration));
            mAnimator.play(mTaskbarImeBgAlpha.animateToValue(
                    hasAnyFlag(FLAG_STASHED_IN_APP_IME) ? 0 : 1).setDuration(duration));
            mAnimator.setStartDelay(startDelay);
            mAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mAnimator = null;
                }
            });
            return;
        }

        AnimatorSet fullLengthAnimatorSet = new AnimatorSet();
        // Not exactly half and may overlap. See [first|second]HalfDurationScale below.
        AnimatorSet firstHalfAnimatorSet = new AnimatorSet();
        AnimatorSet secondHalfAnimatorSet = new AnimatorSet();

        final float firstHalfDurationScale;
        final float secondHalfDurationScale;

        // If Hotseat is not the top element during animation to/from Launcher, fade in/out a
        // already stashed Taskbar.
        boolean skipStashAnimation = !mControllers.uiController.isHotseatIconOnTopWhenAligned()
                && hasAnyFlag(changedFlags, FLAG_IN_APP);
        if (isStashed) {
            firstHalfDurationScale = 0.75f;
            secondHalfDurationScale = 0.5f;

            fullLengthAnimatorSet.play(mIconTranslationYForStash.animateToValue(stashTranslation));
            if (animateBg) {
                fullLengthAnimatorSet.play(mTaskbarBackgroundOffset.animateToValue(1));
            } else {
                fullLengthAnimatorSet.addListener(AnimatorListeners.forEndCallback(
                        () -> mTaskbarBackgroundOffset.updateValue(1)));
            }

            firstHalfAnimatorSet.playTogether(
                    mIconAlphaForStash.animateToValue(0),
                    mIconScaleForStash.animateToValue(isPhoneMode() ?
                            0 : STASHED_TASKBAR_SCALE)
            );
            secondHalfAnimatorSet.playTogether(
                    mTaskbarStashedHandleAlpha.animateToValue(1)
            );

            if (skipStashAnimation) {
                fullLengthAnimatorSet.setInterpolator(INSTANT);
                firstHalfAnimatorSet.setInterpolator(INSTANT);
            }
        } else  {
            firstHalfDurationScale = 0.5f;
            secondHalfDurationScale = 0.75f;

            fullLengthAnimatorSet.playTogether(
                    mIconScaleForStash.animateToValue(1),
                    mIconTranslationYForStash.animateToValue(0));
            if (animateBg) {
                fullLengthAnimatorSet.play(mTaskbarBackgroundOffset.animateToValue(0));
            } else {
                fullLengthAnimatorSet.addListener(AnimatorListeners.forEndCallback(
                        () -> mTaskbarBackgroundOffset.updateValue(0)));
            }

            firstHalfAnimatorSet.playTogether(
                    mTaskbarStashedHandleAlpha.animateToValue(0)
            );
            secondHalfAnimatorSet.playTogether(
                    mIconAlphaForStash.animateToValue(1)
            );

            if (skipStashAnimation) {
                fullLengthAnimatorSet.setInterpolator(FINAL_FRAME);
                secondHalfAnimatorSet.setInterpolator(FINAL_FRAME);
            }
        }

        fullLengthAnimatorSet.play(mControllers.stashedHandleViewController
                .createRevealAnimToIsStashed(isStashed));
        // Return the stashed handle to its default scale in case it was changed as part of the
        // feedforward hint. Note that the reveal animation above also visually scales it.
        fullLengthAnimatorSet.play(mTaskbarStashedHandleHintScale.animateToValue(1f));

        fullLengthAnimatorSet.setDuration(duration);
        firstHalfAnimatorSet.setDuration((long) (duration * firstHalfDurationScale));
        secondHalfAnimatorSet.setDuration((long) (duration * secondHalfDurationScale));
        secondHalfAnimatorSet.setStartDelay((long) (duration * (1 - secondHalfDurationScale)));

        mAnimator.playTogether(fullLengthAnimatorSet, firstHalfAnimatorSet,
                secondHalfAnimatorSet);
        mAnimator.setStartDelay(startDelay);
        mAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mIsStashed = isStashed;
                onIsStashedChanged(mIsStashed);

                cancelTimeoutIfExists();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimator = null;

                if (!mIsStashed) {
                    tryStartTaskbarTimeout();
                }
            }
        });
    }

    private void addJankMonitorListener(AnimatorSet animator, boolean expanding) {
        View v = mControllers.taskbarActivityContext.getDragLayer();
        int action = expanding ? InteractionJankMonitor.CUJ_TASKBAR_EXPAND :
                InteractionJankMonitor.CUJ_TASKBAR_COLLAPSE;
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(@NonNull Animator animation) {
                InteractionJankMonitor.getInstance().begin(v, action);
            }

            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                InteractionJankMonitor.getInstance().end(action);
            }
        });
    }
    /**
     * Creates and starts a partial stash animation, hinting at the new state that will trigger when
     * long press is detected.
     * @param animateForward Whether we are going towards the new stashed state or returning to the
     *                       unstashed state.
     */
    public void startStashHint(boolean animateForward) {
        if (isStashed() || !supportsManualStashing()) {
            // Already stashed, no need to hint in that direction.
            return;
        }
        mIconScaleForStash.animateToValue(
                animateForward ? STASHED_TASKBAR_HINT_SCALE : 1)
                .setDuration(TASKBAR_HINT_STASH_DURATION).start();
    }

    /**
     * Creates and starts a partial unstash animation, hinting at the new state that will trigger
     * when long press is detected.
     * @param animateForward Whether we are going towards the new unstashed state or returning to
     *                       the stashed state.
     */
    public void startUnstashHint(boolean animateForward) {
        if (!isStashed()) {
            // Already unstashed, no need to hint in that direction.
            return;
        }
        if (!canCurrentlyManuallyUnstash()) {
            // If any other flags are causing us to be stashed, long press won't cause us to
            // unstash, so don't hint that it will.
            return;
        }
        mTaskbarStashedHandleHintScale.animateToValue(
                animateForward ? UNSTASHED_TASKBAR_HANDLE_HINT_SCALE : 1)
                .setDuration(TASKBAR_HINT_STASH_DURATION).start();
    }

    private void onIsStashedChanged(boolean isStashed) {
        mControllers.runAfterInit(() -> {
            mControllers.stashedHandleViewController.onIsStashedChanged(isStashed);
            mControllers.taskbarInsetsController.onTaskbarWindowHeightOrInsetsChanged();
        });
    }

    public void applyState() {
        applyState(hasAnyFlag(FLAG_IN_SETUP) ? 0 : TASKBAR_STASH_DURATION);
    }

    public void applyState(long duration) {
        mStatePropertyHolder.setState(mState, duration, true);
    }

    public void applyState(long duration, long startDelay) {
        mStatePropertyHolder.setState(mState, duration, startDelay, true);
    }

    public Animator applyStateWithoutStart() {
        return applyStateWithoutStart(TASKBAR_STASH_DURATION);
    }

    public Animator applyStateWithoutStart(long duration) {
        return mStatePropertyHolder.setState(mState, duration, false);
    }

    /**
     * Should be called when a system gesture starts and settles, so we can defer updating
     * FLAG_STASHED_IN_APP_IME until after the gesture transition completes.
     */
    public void setSystemGestureInProgress(boolean inProgress) {
        mIsSystemGestureInProgress = inProgress;
        if (mIsSystemGestureInProgress) {
            return;
        }

        // Only update the following flags when system gesture is not in progress.
        boolean shouldStashForIme = shouldStashForIme();
        updateStateForFlag(FLAG_STASHED_IN_TASKBAR_ALL_APPS, false);
        if (hasAnyFlag(FLAG_STASHED_IN_APP_IME) != shouldStashForIme) {
            updateStateForFlag(FLAG_STASHED_IN_APP_IME, shouldStashForIme);
            applyState(TASKBAR_STASH_DURATION_FOR_IME, getTaskbarStashStartDelayForIme());
        } else {
            applyState(mControllers.taskbarOverlayController.getCloseDuration());
        }
    }

    /**
     * Resets the flag if no system gesture is in progress.
     * <p>
     * Otherwise, the reset should be deferred until after the gesture is finished.
     *
     * @see #setSystemGestureInProgress
     */
    public void resetFlagIfNoGestureInProgress(int flag) {
        if (!mIsSystemGestureInProgress) {
            updateStateForFlag(flag, false);
            applyState(mControllers.taskbarOverlayController.getCloseDuration());
        }
    }

    /**
     * When hiding the IME, delay the unstash animation to align with the end of the transition.
     */
    private long getTaskbarStashStartDelayForIme() {
        if (mIsImeShowing) {
            // Only delay when IME is exiting, not entering.
            return 0;
        }
        // This duration is based on input_method_extract_exit.xml.
        long imeExitDuration = mControllers.taskbarActivityContext.getResources()
                .getInteger(android.R.integer.config_shortAnimTime);
        return imeExitDuration - TASKBAR_STASH_DURATION_FOR_IME;
    }

    /** Called when some system ui state has changed. (See SYSUI_STATE_... in QuickstepContract) */
    public void updateStateForSysuiFlags(int systemUiStateFlags, boolean skipAnim) {
        long animDuration = TASKBAR_STASH_DURATION;
        long startDelay = 0;

        updateStateForFlag(FLAG_STASHED_IN_SYSUI_STATE, hasAnyFlag(systemUiStateFlags,
                SYSUI_STATE_SCREEN_PINNING
                        | SYSUI_STATE_BOUNCER_SHOWING
                        | SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING
                        | SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED
                        | SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED
                        | SYSUI_STATE_QUICK_SETTINGS_EXPANDED));

        // Only update FLAG_STASHED_IN_APP_IME when system gesture is not in progress.
        mIsImeShowing = hasAnyFlag(systemUiStateFlags, SYSUI_STATE_IME_SHOWING);
        mIsImeSwitcherShowing = hasAnyFlag(systemUiStateFlags, SYSUI_STATE_IME_SWITCHER_SHOWING);

        if (!mIsSystemGestureInProgress) {
            updateStateForFlag(FLAG_STASHED_IN_APP_IME, shouldStashForIme());
            animDuration = TASKBAR_STASH_DURATION_FOR_IME;
            startDelay = getTaskbarStashStartDelayForIme();
        }

        applyState(skipAnim ? 0 : animDuration, skipAnim ? 0 : startDelay);
    }

    /**
     * We stash when IME or IME switcher is showing AND NOT
     *  * in small screen AND
     *  * 3 button nav AND
     *  * landscape (or seascape)
     * We do not stash if taskbar is transient
     */
    private boolean shouldStashForIme() {
        if (DisplayController.isTransientTaskbar(mActivity)) {
            return false;
        }
        return (mIsImeShowing || mIsImeSwitcherShowing) &&
                !(isPhoneMode() && mActivity.isThreeButtonNav()
                        && mActivity.getDeviceProfile().isLandscape);
    }

    /**
     * Updates the proper flag to indicate whether the task bar should be stashed.
     *
     * Note that this only updates the flag. {@link #applyState()} needs to be called separately.
     *
     * @param flag The flag to update.
     * @param enabled Whether to enable the flag: True will cause the task bar to be stashed /
     *                unstashed.
     */
    public void updateStateForFlag(int flag, boolean enabled) {
        if (flag == FLAG_IN_APP && TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.TASKBAR_IN_APP_STATE, String.format(
                    "setting flag FLAG_IN_APP to: %b", enabled), new Exception());
        }
        if (enabled) {
            mState |= flag;
        } else {
            mState &= ~flag;
        }
    }

    /**
     * Called after updateStateForFlag() and applyState() have been called.
     * @param changedFlags The flags that have changed.
     */
    private void onStateChangeApplied(int changedFlags) {
        if (hasAnyFlag(changedFlags, FLAGS_STASHED_IN_APP)) {
            mControllers.uiController.onStashedInAppChanged();
        }
        if (hasAnyFlag(changedFlags, FLAGS_STASHED_IN_APP | FLAGS_IN_APP)) {
            notifyStashChange(/* visible */ hasAnyFlag(FLAGS_IN_APP),
                            /* stashed */ isStashedInApp());
        }
        if (hasAnyFlag(changedFlags, FLAG_STASHED_IN_APP_MANUAL)) {
            if (hasAnyFlag(FLAG_STASHED_IN_APP_MANUAL)) {
                mActivity.getStatsLogManager().logger().log(LAUNCHER_TASKBAR_LONGPRESS_HIDE);
            } else {
                mActivity.getStatsLogManager().logger().log(LAUNCHER_TASKBAR_LONGPRESS_SHOW);
            }
        }
    }

    private void notifyStashChange(boolean visible, boolean stashed) {
        mSystemUiProxy.notifyTaskbarStatus(visible, stashed);
        // If stashing taskbar is caused by IME visibility, we could just skip updating rounded
        // corner insets since the rounded corners will be covered by IME during IME is showing and
        // taskbar will be restored back to unstashed when IME is hidden.
        mControllers.taskbarActivityContext.updateInsetRoundedCornerFrame(
                    visible && !isStashedInAppIgnoringIme());
        mControllers.rotationButtonController.onTaskbarStateChange(visible, stashed);
    }

    /**
     * Cancels a timeout if any exists.
     */
    public void cancelTimeoutIfExists() {
        if (mTimeoutAlarm.alarmPending()) {
            mTimeoutAlarm.cancelAlarm();
        }
    }

    /**
     * Updates the status of the taskbar timeout.
     * @param isAutohideSuspended If true, cancels any existing timeout
     *                            If false, attempts to re/start the timeout
     */
    public void updateTaskbarTimeout(boolean isAutohideSuspended) {
        if (!DisplayController.isTransientTaskbar(mActivity)) {
            return;
        }
        if (isAutohideSuspended) {
            cancelTimeoutIfExists();
        } else {
            tryStartTaskbarTimeout();
        }
    }

    /**
     * Attempts to start timer to auto hide the taskbar based on time.
     */
    public void tryStartTaskbarTimeout() {
        if (!DisplayController.isTransientTaskbar(mActivity)
                || mIsStashed
                || mEnableBlockingTimeoutDuringTests) {
            return;
        }

        cancelTimeoutIfExists();

        mTimeoutAlarm.setOnAlarmListener(this::onTaskbarTimeout);
        mTimeoutAlarm.setAlarm(NO_TOUCH_TIMEOUT_TO_STASH_MS);
    }

    private void onTaskbarTimeout(Alarm alarm) {
        if (mControllers.taskbarAutohideSuspendController.isSuspended()) {
            return;
        }
        updateAndAnimateTransientTaskbar(true);
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarStashController:");

        pw.println(prefix + "\tmStashedHeight=" + mStashedHeight);
        pw.println(prefix + "\tmUnstashedHeight=" + mUnstashedHeight);
        pw.println(prefix + "\tmIsStashed=" + mIsStashed);
        pw.println(prefix + "\tappliedState=" + getStateString(mStatePropertyHolder.mPrevFlags));
        pw.println(prefix + "\tmState=" + getStateString(mState));
        pw.println(prefix + "\tmIsSystemGestureInProgress=" + mIsSystemGestureInProgress);
        pw.println(prefix + "\tmIsImeShowing=" + mIsImeShowing);
        pw.println(prefix + "\tmIsImeSwitcherShowing=" + mIsImeSwitcherShowing);
    }

    private static String getStateString(int flags) {
        StringJoiner sj = new StringJoiner("|");
        appendFlag(sj, flags, FLAGS_IN_APP, "FLAG_IN_APP");
        appendFlag(sj, flags, FLAG_STASHED_IN_APP_MANUAL, "FLAG_STASHED_IN_APP_MANUAL");
        appendFlag(sj, flags, FLAG_STASHED_IN_SYSUI_STATE, "FLAG_STASHED_IN_SYSUI_STATE");
        appendFlag(sj, flags, FLAG_STASHED_IN_APP_EMPTY, "FLAG_STASHED_IN_APP_EMPTY");
        appendFlag(sj, flags, FLAG_STASHED_IN_APP_SETUP, "FLAG_STASHED_IN_APP_SETUP");
        appendFlag(sj, flags, FLAG_STASHED_IN_APP_IME, "FLAG_STASHED_IN_APP_IME");
        appendFlag(sj, flags, FLAG_IN_STASHED_LAUNCHER_STATE, "FLAG_IN_STASHED_LAUNCHER_STATE");
        appendFlag(sj, flags, FLAG_STASHED_IN_TASKBAR_ALL_APPS, "FLAG_STASHED_IN_TASKBAR_ALL_APPS");
        appendFlag(sj, flags, FLAG_IN_SETUP, "FLAG_IN_SETUP");
        appendFlag(sj, flags, FLAG_STASHED_IN_APP_AUTO, "FLAG_STASHED_IN_APP_AUTO");
        return sj.toString();
    }

    private class StatePropertyHolder {
        private final IntPredicate mStashCondition;

        private boolean mIsStashed;
        private boolean mIsHotseatIconOnTopWhenAligned;
        private int mPrevFlags;

        StatePropertyHolder(IntPredicate stashCondition) {
            mStashCondition = stashCondition;
        }

        /**
         * @see #setState(int, long, long, boolean) with a default startDelay = 0.
         */
        public Animator setState(int flags, long duration, boolean start) {
            return setState(flags, duration, 0 /* startDelay */, start);
        }

        /**
         * Applies the latest state, potentially calling onStateChangeApplied() and creating a new
         * animation (stored in mAnimator) which is started if {@param start} is true.
         * @param flags The latest flags to apply (see the top of this file).
         * @param duration The length of the animation.
         * @param startDelay How long to delay the animation after calling start().
         * @param start Whether to start mAnimator immediately.
         * @return mAnimator if mIsStashed changed, else null.
         */
        public Animator setState(int flags, long duration, long startDelay, boolean start) {
            int changedFlags = mPrevFlags ^ flags;
            if (mPrevFlags != flags) {
                onStateChangeApplied(changedFlags);
                mPrevFlags = flags;
            }
            boolean isStashed = mStashCondition.test(flags);
            boolean isHotseatIconOnTopWhenAligned =
                    mControllers.uiController.isHotseatIconOnTopWhenAligned();
            // If an animation has started and mIsHotseatIconOnTopWhenAligned is changed, we need
            // to restart the animation with new parameters.
            if (mIsStashed != isStashed
                    || (mIsHotseatIconOnTopWhenAligned != isHotseatIconOnTopWhenAligned
                    && mAnimator != null && mAnimator.isStarted())) {
                if (TestProtocol.sDebugTracing) {
                    Log.d(TestProtocol.TASKBAR_IN_APP_STATE, String.format(
                            "setState: mIsStashed=%b, isStashed=%b, duration=%d, start=:%b",
                            mIsStashed,
                            isStashed,
                            duration,
                            start));
                }
                mIsStashed = isStashed;
                mIsHotseatIconOnTopWhenAligned = isHotseatIconOnTopWhenAligned;

                // This sets mAnimator.
                createAnimToIsStashed(
                        mIsStashed, duration, startDelay, /* animateBg= */ true, changedFlags);
                if (start) {
                    mAnimator.start();
                }
                return mAnimator;
            }
            return null;
        }
    }
}
