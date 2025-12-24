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

import static com.android.launcher3.Flags.enableTaskbarUiThread;
import static com.android.launcher3.Flags.refactorTaskbarUiState;
import static com.android.launcher3.Flags.syncAppLaunchWithTaskbarStash;
import static com.android.launcher3.QuickstepTransitionManager.TASKBAR_TO_APP_DURATION;
import static com.android.launcher3.QuickstepTransitionManager.TRANSIENT_TASKBAR_TRANSITION_DURATION;
import static com.android.launcher3.QuickstepTransitionManager.getTaskbarToHomeDuration;
import static com.android.launcher3.statemanager.BaseState.FLAG_NON_INTERACTIVE;
import static com.android.launcher3.taskbar.TaskbarEduTooltipControllerKt.TOOLTIP_STEP_FEATURES;
import static com.android.launcher3.taskbar.TaskbarLauncherStateController.FLAG_VISIBLE;
import static com.android.launcher3.taskbar.TaskbarManagerImpl.TASKBAR_UI_THREAD;
import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_IGNORE_IN_APP;
import static com.android.launcher3.taskbar.navbutton.SetupNavLayoutterKt.GLIF_EXPRESSIVE_LIGHT_THEME;
import static com.android.launcher3.taskbar.navbutton.SetupNavLayoutterKt.GLIF_EXPRESSIVE_THEME;
import static com.android.launcher3.taskbar.navbutton.SetupNavLayoutterKt.SUW_THEME_SYSTEM_PROPERTY;
import static com.android.quickstep.interaction.AllSetActivity.ALL_SET_SWIPE_THRESHOLD_FOR_WORKSPACE_ANIM;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.os.SystemProperties;
import android.window.RemoteTransition;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.launcher3.BuildConfig;
import com.android.launcher3.BuildConfigs;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.LauncherInteractor;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherUiState;
import com.android.launcher3.LauncherUiStateUtil;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.InstanceIdSequence;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.taskbar.bubbles.BubbleBarController;
import com.android.launcher3.taskbar.bubbles.BubbleControllers;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.OnboardingPrefs;
import com.android.launcher3.util.SafeCloseable;
import com.android.quickstep.BaseContainerInterface;
import com.android.quickstep.GestureState;
import com.android.quickstep.HomeVisibilityState;
import com.android.quickstep.LauncherActivityInterface;
import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.RecentsAnimationCallbacks;
import com.android.quickstep.util.SplitTask;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.quickstep.window.RecentsWindowManager;
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags;
import com.android.wm.shell.shared.bubbles.BubbleBarLocation;

import kotlin.Unit;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.concurrent.Executor;

/**
 * A data source which integrates with a Launcher instance
 */
public class LauncherTaskbarUIController extends TaskbarUIController {

    private static final String TAG = "TaskbarUIController";

    public static final int MINUS_ONE_PAGE_PROGRESS_INDEX = 0;
    public static final int ALL_APPS_PAGE_PROGRESS_INDEX = 1;
    public static final int WIDGETS_PAGE_PROGRESS_INDEX = 2;
    public static final int SYSUI_SURFACE_PROGRESS_INDEX = 3;
    public static final int LAUNCHER_PAUSE_PROGRESS_INDEX = 4;

    public static final int DISPLAY_PROGRESS_COUNT = 5;

    private final AnimatedFloat mTaskbarInAppDisplayProgress = new AnimatedFloat(
            this::onInAppDisplayProgressChanged);
    private final MultiPropertyFactory<AnimatedFloat> mTaskbarInAppDisplayProgressMultiProp =
            new MultiPropertyFactory<>(mTaskbarInAppDisplayProgress,
                    AnimatedFloat.VALUE, DISPLAY_PROGRESS_COUNT, Float::max);
    private final AnimatedFloat mLauncherPauseProgress = new AnimatedFloat(
            this::onLauncherPauseProgressUpdate);

    private final LauncherInteractor mLauncher;
    private final LauncherUiState mLauncherUiState;
    private final Executor mTaskbarExecutor;
    private final HomeVisibilityState mHomeState;

    private final DeviceProfile.OnDeviceProfileChangeListener mOnDeviceProfileChangeListener =
            dp -> {
                onStashedInAppChanged(dp);
                postAdjustHotseatForBubbleBar();
                if (mControllers != null && mControllers.taskbarViewController != null) {
                    mControllers.taskbarViewController.onRotationChanged(dp);
                }
            };
    private SafeCloseable mOnDeviceProfileChangeListenerClosable;
    private final HomeVisibilityState.VisibilityChangeListener  mVisibilityChangeListener =
            this::onLauncherVisibilityChanged;

    // Initialized in init.
    private final TaskbarLauncherStateController
            mTaskbarLauncherStateController = new TaskbarLauncherStateController();
    // When overview-in-a-window is enabled, that window is the container, else it is mLauncher.
    private RecentsViewContainer mRecentsViewContainer;

    public LauncherTaskbarUIController(
            LauncherInteractor launcher,
            LauncherUiState launcherUiState,
            HomeVisibilityState homeState) {
        mLauncher = launcher;
        mLauncherUiState = launcherUiState;
        mHomeState = homeState;
        mTaskbarExecutor = enableTaskbarUiThread() ? TASKBAR_UI_THREAD : Runnable::run;
    }

    @Override
    protected void init(TaskbarControllers taskbarControllers) {
        super.init(taskbarControllers);

        mTaskbarLauncherStateController.init(mControllers, mLauncher, mLauncherUiState,
                mControllers.getSharedState().sysuiStateFlags, mTaskbarExecutor);
        final TaskbarActivityContext taskbarContext = mControllers.taskbarActivityContext;
        int displayId = taskbarContext.getDisplayId();
        BaseContainerInterface<?, ?> containerInterface = OverviewComponentObserver.INSTANCE.get(
                taskbarContext).getContainerInterface(displayId);
        if (containerInterface != null
                && containerInterface.getCreatedContainer()
                instanceof RecentsWindowManager recentsWindowManager) {
            mRecentsViewContainer = recentsWindowManager;
            mRecentsViewContainer.setTaskbarInteractor(new TaskbarInteractor(this));
        } else {
            // TODO(b/404636836) Refactor API calls on mRecentsViewContainer
            mRecentsViewContainer = mLauncher.getLauncherAsRecentViewContainer();
        }
        mLauncher.setTaskbarInteractor(new TaskbarInteractor(this));

        mHomeState.addListener(mVisibilityChangeListener);
        onLauncherVisibilityChanged(mHomeState.isHomeVisible(), true /* fromInit */);

        onStashedInAppChanged(getDeviceProfile());
        mOnDeviceProfileChangeListenerClosable =
                mLauncherUiState.getDeviceProfileRef().forEach(mTaskbarExecutor, dp -> {
                    if (mLauncherUiState.isDeviceProfileInitialized()) {
                        mOnDeviceProfileChangeListener.onDeviceProfileChanged(dp);
                    }
            return Unit.INSTANCE;
        });

        // Restore the in-app display progress from before Taskbar was recreated.
        float[] prevProgresses = mControllers.getSharedState().inAppDisplayProgressMultiPropValues;
        // Make a copy of the previous progress to set since updating the multiprop will update
        // the property which also calls onInAppDisplayProgressChanged() which writes the current
        // values into the shared state
        prevProgresses = Arrays.copyOf(prevProgresses, prevProgresses.length);
        for (int i = 0; i < prevProgresses.length; i++) {
            mTaskbarInAppDisplayProgressMultiProp.get(i).setValue(prevProgresses[i]);
        }
    }

    private DeviceProfile getDeviceProfile() {
        return LauncherUiStateUtil.getDeviceProfile(mLauncher, mLauncherUiState);
    }

    @Override
    protected void onDestroy() {
        onLauncherVisibilityChanged(false /* isVisible */, true /* fromInitOrDestroy */);
        if (mOnDeviceProfileChangeListenerClosable != null) {
            mOnDeviceProfileChangeListenerClosable.close();
        }
        super.onDestroy();
        mTaskbarLauncherStateController.onDestroy();

        mLauncher.setTaskbarInteractor(null);
        mRecentsViewContainer.setTaskbarInteractor(null);
        mHomeState.removeListener(mVisibilityChangeListener);
    }

    private void onInAppDisplayProgressChanged() {
        if (mControllers != null) {
            // Update our shared state so we can restore it if taskbar gets recreated.
            for (int i = 0; i < DISPLAY_PROGRESS_COUNT; i++) {
                mControllers.getSharedState().inAppDisplayProgressMultiPropValues[i] =
                        mTaskbarInAppDisplayProgressMultiProp.get(i).getValue();
            }
            // Ensure nav buttons react to our latest state if necessary.
            mControllers.navbarButtonsViewController.updateNavButtonTranslationY();
        }
    }

    @Override
    protected boolean isTaskbarTouchable() {
        // Touching down during animation to Hotseat will end the transition and allow the touch to
        // go through to the Hotseat directly.
        return !isAnimatingToHotseat();
    }

    public void setShouldDelayLauncherStateAnim(boolean shouldDelayLauncherStateAnim) {
        mTaskbarLauncherStateController.setShouldDelayLauncherStateAnim(
                shouldDelayLauncherStateAnim);
    }

    @Override
    public void stashHotseat(boolean stash) {
        mTaskbarLauncherStateController.stashHotseat(stash);
    }

    @Override
    public void unStashHotseatInstantly() {
        mTaskbarLauncherStateController.unStashHotseatInstantly();
    }

    /**
     * Adds the Launcher resume animator to the given animator set.
     *
     * This should be used to run a Launcher resume animation whose progress matches a
     * swipe progress.
     *
     * @param placeholderDuration a placeholder duration to be used to ensure all full-length
     *                            sub-animations are properly coordinated. This duration should not
     *                            actually be used since this animation tracks a swipe progress.
     */
    protected void addLauncherVisibilityChangedAnimation(AnimatorSet animation,
            int placeholderDuration) {
        animation.play(onLauncherVisibilityChanged(
                /* isResumed= */ true,
                /* fromInit= */ false,
                /* startAnimation= */ false,
                placeholderDuration));
    }

    protected void addWorkspaceRevealAnim(AnimatorSet as, int duration) {
        String SUWTheme = SystemProperties.get(SUW_THEME_SYSTEM_PROPERTY, "");
        boolean isExpressiveTheme = SUWTheme.equals(GLIF_EXPRESSIVE_THEME)
                || SUWTheme.equals(GLIF_EXPRESSIVE_LIGHT_THEME);
        if (!isExpressiveTheme || !Flags.enableNewAllSetAnimation()) {
            return;
        }
        ValueAnimator autoPlayAnimator = ValueAnimator.ofFloat(0f, 1f);
        autoPlayAnimator.setDuration(duration);
        autoPlayAnimator.setInterpolator(Interpolators.LINEAR);
        autoPlayAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            private boolean mCanAutoPlay = true;
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                if (mCanAutoPlay && valueAnimator.getAnimatedFraction()
                        > ALL_SET_SWIPE_THRESHOLD_FOR_WORKSPACE_ANIM) {
                    mLauncher.startScalingWorkspaceRevealAnim(true, false);
                    mCanAutoPlay = false;
                }
            }
        });
        as.play(autoPlayAnimator);
    }
    /**
     * Should be called from onResume() and onPause(), and animates the Taskbar accordingly.
     */
    @Override
    public void onLauncherVisibilityChanged(boolean isVisible) {
        final TaskbarActivityContext taskbarContext = mControllers.taskbarActivityContext;
        if (taskbarContext.showLockedTaskbarOnHome()
                && !taskbarContext.showDesktopTaskbarForFreeformDisplay()
                && taskbarContext.isPrimaryDisplay()) {
            DisplayController.INSTANCE.get(taskbarContext).notifyConfigChange();
        }

        if (android.view.accessibility.Flags.launcherAppDisplayProgressUpdateOnVisibilityChange()) {
            onInAppDisplayProgressChanged();
        }

        onLauncherVisibilityChanged(isVisible, false /* fromInit */);
    }

    private void onLauncherVisibilityChanged(boolean isVisible, boolean fromInitOrDestroy) {
        if (mControllers == null) {
            return;
        }
        onLauncherVisibilityChanged(
                isVisible,
                fromInitOrDestroy,
                /* startAnimation= */ true,
                getTaskbarAnimationDuration(isVisible));
    }

    private int getTaskbarAnimationDuration(boolean isVisible) {
        // fast animation duration since we will not be playing workspace reveal animation.
        boolean shouldOverrideToFastAnimation = !isHotseatIconOnTopWhenAligned();
        boolean isPersistent =
                mControllers.taskbarActivityContext.getTaskbarFeatureEvaluator().isPersistent();
        if (isVisible || isPersistent) {
            return getTaskbarToHomeDuration(shouldOverrideToFastAnimation, isPersistent);
        } else {
            return (mControllers.taskbarActivityContext.isTransientTaskbar())
                    ? TRANSIENT_TASKBAR_TRANSITION_DURATION : TASKBAR_TO_APP_DURATION;
        }
    }

    @Nullable
    private Animator onLauncherVisibilityChanged(
            boolean isVisible, boolean fromInitOrDestroy, boolean startAnimation, int duration) {
        // Launcher is resumed during the swipe-to-overview gesture under shell-transitions, so
        // avoid updating taskbar state in that situation (when it's non-interactive -- or
        // "background") to avoid premature animations.
        LauncherState state = mTaskbarLauncherStateController.getLauncherState();
        boolean nonInteractiveState = state.hasFlag(FLAG_NON_INTERACTIVE)
                && !state.isTaskbarAlignedWithHotseat();
        if (isVisible && (nonInteractiveState || mSkipLauncherVisibilityChange)) {
            return null;
        }

        mTaskbarLauncherStateController.updateStateForFlag(FLAG_VISIBLE, isVisible);
        if (fromInitOrDestroy) {
            duration = 0;
        }
        return mTaskbarLauncherStateController.applyState(duration, startAnimation);
    }

    @Override
    public void onStateTransitionCompletedAfterSwipeToHome(LauncherState state) {
        mTaskbarLauncherStateController.onStateTransitionCompletedAfterSwipeToHome(state);
    }

    @Override
    public void refreshResumedState() {
        onLauncherVisibilityChanged(mHomeState.isHomeVisible());
    }

    @Override
    public void adjustHotseatForBubbleBar(boolean isBubbleBarVisible) {
        mLauncher.adjustHotseatForBubbleBar(isBubbleBarVisible);
    }

    private void postAdjustHotseatForBubbleBar() {
        mLauncher.postAdjustHotseatForBubbleBar(isBubbleBarVisible(), mControllers != null);
    }

    private boolean isBubbleBarVisible() {
        BubbleControllers bubbleControllers = mControllers.bubbleControllers.orElse(null);
        return bubbleControllers != null
                && bubbleControllers.bubbleBarViewController.isBubbleBarVisible();
    }

    /**
     * Create Taskbar animation when going from an app to Launcher as part of recents transition.
     * {@inheritDoc}
     */
    @Override
    public Animator getParallelAnimationToGestureEndTarget(
            GestureState.GestureEndTarget gestureEndTarget, long duration,
            RecentsAnimationCallbacks callbacks) {
        return mTaskbarLauncherStateController.createAnimToLauncher(
                LauncherActivityInterface.INSTANCE.stateFromGestureEndTarget(gestureEndTarget),
                callbacks,
                duration);
    }

    /**
     * Create Taskbar animation to be played alongside the Launcher app launch animation.
     */
    public @Nullable Animator createAnimToApp() {
        if (!syncAppLaunchWithTaskbarStash()) {
            return null;
        }
        TaskbarStashController stashController = mControllers.taskbarStashController;
        stashController.updateStateForFlag(TaskbarStashController.FLAG_IN_APP, true);
        return stashController.createApplyStateAnimator(stashController.getStashDuration());
    }

    /**
     * Temporarily ignore FLAG_IN_APP for app launches to prevent premature taskbar stashing.
     * This is needed because taskbar gets a signal to stash before we actually start the
     * app launch animation.
     */
    public void setIgnoreInAppFlagForSync(boolean enabled) {
        if (syncAppLaunchWithTaskbarStash()
                && mControllers != null
                && mControllers.taskbarStashController != null) {
            mControllers.taskbarStashController.updateStateForFlag(FLAG_IGNORE_IN_APP, enabled);
        }
    }

    public void updateTaskbarLauncherStateGoingHome() {
        mTaskbarLauncherStateController.updateStateForFlag(FLAG_VISIBLE, true);
        mTaskbarLauncherStateController.applyState();
    }

    @Override
    protected void onStashedInAppChanged() {
        onStashedInAppChanged(getDeviceProfile());
    }

    private void onStashedInAppChanged(DeviceProfile deviceProfile) {
        boolean taskbarStashedInApps = mControllers.taskbarStashController.isStashedInApp();
        deviceProfile.isTaskbarPresentInApps = !taskbarStashedInApps;
    }

    /**
     * Starts a Taskbar EDU flow, if the user should see one upon launching an application.
     */
    public void showEduOnAppLaunch() {
        if (!shouldShowEduOnAppLaunch()) {
            // Called in case the edu finishes and search edu is still pending
            mControllers.taskbarEduTooltipController.maybeShowSearchEdu();
            return;
        }

        // Persistent features EDU tooltip.
        if (!mControllers.taskbarActivityContext.isTransientTaskbar()) {
            mControllers.taskbarEduTooltipController.maybeShowFeaturesEdu();
            return;
        }

        // Transient swipe EDU tooltip.
        mControllers.taskbarEduTooltipController.maybeShowSwipeEdu();
    }

    /** Will make the next onRecentsAnimationFinished() animation a no-op. */
    public void setSkipNextRecentsAnimEnd() {
        mTaskbarLauncherStateController.setSkipNextRecentsAnimEnd();
    }

    /**
     * Returns {@code true} if a Taskbar education should be shown on application launch.
     */
    public boolean shouldShowEduOnAppLaunch() {
        if (Utilities.isRunningInTestHarness()) {
            return false;
        }

        // Persistent features EDU tooltip.
        if (!mControllers.taskbarActivityContext.isTransientTaskbar()) {
            return !OnboardingPrefs.TASKBAR_EDU_TOOLTIP_STEP.hasReachedMax(
                    mControllers.taskbarActivityContext);
        }

        // Transient swipe EDU tooltip.
        return mControllers.taskbarEduTooltipController.getTooltipStep() < TOOLTIP_STEP_FEATURES;
    }

    @Override
    public void onTaskbarIconLaunched(ItemInfo item) {
        super.onTaskbarIconLaunched(item);
        InstanceId instanceId = new InstanceIdSequence().newInstanceId();
        mLauncher.logAppLaunch(mControllers.taskbarActivityContext.getStatsLogManager(), item,
                instanceId);
    }

    /**
     * Animates Taskbar elements during a transition to a Launcher state that should use in-app
     * layouts.
     *
     * @param progress [0, 1]
     *                 0 => use home layout
     *                 1 => use in-app layout
     */
    public void onTaskbarInAppDisplayProgressUpdate(float progress, int progressIndex) {
        mTaskbarInAppDisplayProgressMultiProp.get(progressIndex).setValue(progress);
        if (mControllers == null) {
            // This method can be called before init() is called.
            return;
        }
        if (mControllers.uiController.isIconAlignedWithHotseat()) {
            if (!mTaskbarLauncherStateController.isAnimatingToLauncher()) {
                // Only animate the nav buttons while home and not animating home, otherwise let
                // the TaskbarViewController handle it.
                mControllers.navbarButtonsViewController
                        .getTaskbarNavButtonTranslationYForInAppDisplay()
                        .updateValue(getDeviceProfile().getTaskbarOffsetY()
                                * mTaskbarInAppDisplayProgress.value);
                mControllers.navbarButtonsViewController
                        .getOnTaskbarBackgroundNavButtonColorOverride().updateValue(progress);
            }
            if (isBubbleBarEnabled()) {
                mControllers.bubbleControllers.ifPresent(
                        c -> c.bubbleStashController.setInAppDisplayOverrideProgress(
                                mTaskbarInAppDisplayProgress.value));
            }
        }
    }

    /** Returns true iff any in-app display progress > 0. */
    public boolean shouldUseInAppLayout() {
        return mTaskbarInAppDisplayProgress.value > 0;
    }

    public boolean isBubbleBarEnabled() {
        return BubbleBarController.isBubbleBarEnabled();
    }

    /** Whether the bubble bar has any bubbles. */
    public boolean hasBubbles() {
        if (mControllers == null) {
            return false;
        }
        if (mControllers.bubbleControllers.isEmpty()) {
            return false;
        }
        return mControllers.bubbleControllers.get().bubbleBarViewController.hasBubbles();
    }

    @Override
    public void onExpandPip() {
        super.onExpandPip();
        mTaskbarLauncherStateController.updateStateForFlag(FLAG_VISIBLE, false);
        mTaskbarLauncherStateController.applyState();
    }

    @Override
    public void updateStateForSysuiFlags(@SystemUiStateFlags long sysuiFlags) {
        mTaskbarLauncherStateController.updateStateForSysuiFlags(sysuiFlags);
    }

    @Override
    public boolean isIconAlignedWithHotseat() {
        return mTaskbarLauncherStateController.isIconAlignedWithHotseat();
    }

    @Override
    public boolean isHotseatIconOnTopWhenAligned() {
        return mTaskbarLauncherStateController.isInHotseatOnTopStates()
                && mTaskbarInAppDisplayProgressMultiProp.get(MINUS_ONE_PAGE_PROGRESS_INDEX)
                    .getValue() == 0;
    }

    @Override
    public boolean isAnimatingToHotseat() {
        return mTaskbarLauncherStateController.isAnimatingToLauncher()
                && isIconAlignedWithHotseat();
    }

    @Override
    public void endAnimationToHotseat() {
        mTaskbarLauncherStateController.resetIconAlignment();
    }

    @Override
    protected boolean isInOverviewUi() {
        return mTaskbarLauncherStateController.isInOverviewUi();
    }

    @Override
    protected void toggleAllApps(boolean focusSearch) {
        final boolean canToggleHomeAllApps = isLauncherResumed()
                && !mTaskbarLauncherStateController.isInOverviewUi()
                && isLauncherTopResumedActivity();
        if (canToggleHomeAllApps) {
            mLauncher.toggleAllApps(focusSearch);
            return;
        }
        super.toggleAllApps(focusSearch);
    }

    private boolean isLauncherResumed() {
        if (refactorTaskbarUiState()) {
            final boolean ret = mLauncherUiState.isResumed();
            if (BuildConfigs.IS_STUDIO_BUILD && ret != mLauncher.isResumed()) {
                throw new IllegalStateException("hasBeenResumed doesn't match");
            }
            return ret;
        } else {
            return mLauncher.isResumed();
        }
    }

    private boolean isLauncherTopResumedActivity() {
        if (refactorTaskbarUiState()) {
            final boolean ret = mLauncherUiState.isTopResumedActivityRef().getValue();
            if (BuildConfigs.IS_STUDIO_BUILD && ret != mLauncher.isTopResumedActivity()) {
                throw new IllegalStateException("isTopResumedActivity doesn't match");
            }
            return ret;
        } else {
            return mLauncher.isTopResumedActivity();
        }
    }

    @Override
    public RecentsView getRecentsView() {
        return mRecentsViewContainer.getOverviewPanel();
    }

    @Override
    public void launchSplitTasks(
            @NonNull SplitTask splitTask, @Nullable RemoteTransition remoteTransition) {
        mLauncher.launchSplitTasks(splitTask, remoteTransition);
    }

    @Override
    protected void onIconLayoutBoundsChanged() {
        mTaskbarLauncherStateController.resetIconAlignment();
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        super.dumpLogs(prefix, pw);

        pw.println(String.format("%s\tTaskbar in-app display progress: %.2f", prefix,
                mTaskbarInAppDisplayProgress.value));
        mTaskbarInAppDisplayProgressMultiProp.dump(
                prefix + "\t\t",
                pw,
                "mTaskbarInAppDisplayProgressMultiProp",
                "MINUS_ONE_PAGE_PROGRESS_INDEX",
                "ALL_APPS_PAGE_PROGRESS_INDEX",
                "WIDGETS_PAGE_PROGRESS_INDEX",
                "SYSUI_SURFACE_PROGRESS_INDEX",
                "LAUNCHER_PAUSE_PROGRESS_INDEX");
        pw.println(String.format("%s\tmRecentsWindowContainer=%s", prefix, mRecentsViewContainer));

        mTaskbarLauncherStateController.dumpLogs(prefix + "\t", pw);
    }

    @Override
    protected String getTaskbarUIControllerName() {
        return "LauncherTaskbarUIController";
    }

    @Override
    public void onBubbleBarLocationAnimated(BubbleBarLocation location) {
        mTaskbarLauncherStateController.onBubbleBarLocationChanged(location, /* animate = */ true);
        mLauncher.setBubbleBarLocation(location);
    }

    @Override
    public void onBubbleBarLocationUpdated(BubbleBarLocation location) {
        mTaskbarLauncherStateController.onBubbleBarLocationChanged(location, /* animate = */ false);
        mLauncher.setBubbleBarLocation(location);
    }

    @Override
    public void onSwipeToUnstashTaskbar() {
        // Once taskbar is unstashed, the user cannot return back to the overlay. We can
        // clear it here to set the expected state once the user goes home.
        mLauncher.resetOverlayScroll();
    }

    /**
     * Called when Launcher Activity resumed while staying at home.
     * <p>
     * Shift nav buttons up to at-home position.
     */
    public void onLauncherResume() {
        mLauncherPauseProgress.animateToValue(0.0f).start();
    }

    /**
     * Called when Launcher Activity paused while staying at home.
     * <p>
     * To avoid UI clash between taskbar & bottom sheet, shift nav buttons down to in-app position.
     */
    public void onLauncherPause() {
        mLauncherPauseProgress.animateToValue(1.0f).start();
    }

    /**
     * On launcher stop, avoid animating taskbar & overriding pre-existing animations.
     */
    public void onLauncherStop() {
        mLauncherPauseProgress.cancelAnimation();
        mLauncherPauseProgress.updateValue(0.0f);
    }

    private void onLauncherPauseProgressUpdate() {
        // If we are not aligned with hotseat, setting this will clobber the 3 button nav position.
        // So in that case, treat the progress as 0 instead.
        float pauseProgress = isIconAlignedWithHotseat() ? mLauncherPauseProgress.value : 0;
        onTaskbarInAppDisplayProgressUpdate(pauseProgress, LAUNCHER_PAUSE_PROGRESS_INDEX);
    }
}
