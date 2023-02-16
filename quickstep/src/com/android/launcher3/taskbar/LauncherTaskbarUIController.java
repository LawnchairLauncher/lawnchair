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

import static android.view.InsetsState.ITYPE_EXTRA_NAVIGATION_BAR;

import static com.android.launcher3.QuickstepTransitionManager.TRANSIENT_TASKBAR_TRANSITION_DURATION;
import static com.android.launcher3.config.FeatureFlags.ENABLE_TASKBAR_EDU_TOOLTIP;
import static com.android.launcher3.statemanager.BaseState.FLAG_NON_INTERACTIVE;
import static com.android.launcher3.taskbar.TaskbarEduTooltipControllerKt.TOOLTIP_STEP_FEATURES;
import static com.android.launcher3.taskbar.TaskbarLauncherStateController.FLAG_RESUMED;
import static com.android.quickstep.TaskAnimationManager.ENABLE_SHELL_TRANSITIONS;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.annotation.ColorInt;
import android.os.RemoteException;
import android.util.Log;
import android.view.TaskTransitionSpec;
import android.view.View;
import android.view.WindowManagerGlobal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherState;
import com.android.launcher3.QuickstepTransitionManager;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.InstanceIdSequence;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.OnboardingPrefs;
import com.android.quickstep.RecentsAnimationCallbacks;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.views.RecentsView;

import java.io.PrintWriter;
import java.util.Set;

/**
 * A data source which integrates with a Launcher instance
 */
public class LauncherTaskbarUIController extends TaskbarUIController {

    private static final String TAG = "TaskbarUIController";

    public static final int MINUS_ONE_PAGE_PROGRESS_INDEX = 0;
    public static final int ALL_APPS_PAGE_PROGRESS_INDEX = 1;
    public static final int WIDGETS_PAGE_PROGRESS_INDEX = 2;
    public static final int SYSUI_SURFACE_PROGRESS_INDEX = 3;

    private static final int DISPLAY_PROGRESS_COUNT = 4;

    private final AnimatedFloat mTaskbarInAppDisplayProgress = new AnimatedFloat();
    private final MultiPropertyFactory<AnimatedFloat> mTaskbarInAppDisplayProgressMultiProp =
            new MultiPropertyFactory<>(mTaskbarInAppDisplayProgress,
                    AnimatedFloat.VALUE, DISPLAY_PROGRESS_COUNT, Float::max);

    private final QuickstepLauncher mLauncher;

    private final DeviceProfile.OnDeviceProfileChangeListener mOnDeviceProfileChangeListener =
            dp -> {
                onStashedInAppChanged(dp);
                if (mControllers != null && mControllers.taskbarViewController != null) {
                    mControllers.taskbarViewController.onRotationChanged(dp);
                }
            };

    // Initialized in init.
    private AnimatedFloat mTaskbarOverrideBackgroundAlpha;
    private TaskbarKeyguardController mKeyguardController;
    private final TaskbarLauncherStateController
            mTaskbarLauncherStateController = new TaskbarLauncherStateController();

    public LauncherTaskbarUIController(QuickstepLauncher launcher) {
        mLauncher = launcher;
    }

    @Override
    protected void init(TaskbarControllers taskbarControllers) {
        super.init(taskbarControllers);

        mTaskbarLauncherStateController.init(mControllers, mLauncher);
        mTaskbarOverrideBackgroundAlpha = mControllers.taskbarDragLayerController
                .getOverrideBackgroundAlpha();

        mLauncher.setTaskbarUIController(this);
        mKeyguardController = taskbarControllers.taskbarKeyguardController;

        onLauncherResumedOrPaused(mLauncher.hasBeenResumed(), true /* fromInit */);

        onStashedInAppChanged(mLauncher.getDeviceProfile());
        mLauncher.addOnDeviceProfileChangeListener(mOnDeviceProfileChangeListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        onLauncherResumedOrPaused(false);
        mTaskbarLauncherStateController.onDestroy();

        mLauncher.setTaskbarUIController(null);
        mLauncher.removeOnDeviceProfileChangeListener(mOnDeviceProfileChangeListener);
        updateTaskTransitionSpec(true);
    }

    @Override
    protected boolean isTaskbarTouchable() {
        return !(mTaskbarLauncherStateController.isAnimatingToLauncher()
                && mTaskbarLauncherStateController.goingToAlignedLauncherState());
    }

    public void setShouldDelayLauncherStateAnim(boolean shouldDelayLauncherStateAnim) {
        mTaskbarLauncherStateController.setShouldDelayLauncherStateAnim(
                shouldDelayLauncherStateAnim);
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
    protected void addLauncherResumeAnimation(AnimatorSet animation, int placeholderDuration) {
        animation.play(onLauncherResumedOrPaused(
                /* isResumed= */ true,
                /* fromInit= */ false,
                /* startAnimation= */ false,
                placeholderDuration));
    }

    /**
     * Should be called from onResume() and onPause(), and animates the Taskbar accordingly.
     */
    public void onLauncherResumedOrPaused(boolean isResumed) {
        onLauncherResumedOrPaused(isResumed, false /* fromInit */);
    }

    private void onLauncherResumedOrPaused(boolean isResumed, boolean fromInit) {
        onLauncherResumedOrPaused(
                isResumed,
                fromInit,
                /* startAnimation= */ true,
                DisplayController.isTransientTaskbar(mLauncher)
                        ? TRANSIENT_TASKBAR_TRANSITION_DURATION
                        : (!isResumed
                                ? QuickstepTransitionManager.TASKBAR_TO_APP_DURATION
                                : QuickstepTransitionManager.TASKBAR_TO_HOME_DURATION));
    }

    @Nullable
    private Animator onLauncherResumedOrPaused(
            boolean isResumed, boolean fromInit, boolean startAnimation, int duration) {
        if (mKeyguardController.isScreenOff()) {
            if (!isResumed) {
                return null;
            } else {
                // Resuming implicitly means device unlocked
                mKeyguardController.setScreenOn();
            }
        }

        // Launcher is resumed during the swipe-to-overview gesture under shell-transitions, so
        // avoid updating taskbar state in that situation (when it's non-interactive -- or
        // "background") to avoid premature animations.
        if (ENABLE_SHELL_TRANSITIONS && isResumed
                && mLauncher.getStateManager().getState().hasFlag(FLAG_NON_INTERACTIVE)
                && !mLauncher.getStateManager().getState().isTaskbarAlignedWithHotseat(mLauncher)) {
            return null;
        }

        mTaskbarLauncherStateController.updateStateForFlag(FLAG_RESUMED, isResumed);
        return mTaskbarLauncherStateController.applyState(fromInit ? 0 : duration, startAnimation);
    }

    /**
     * Create Taskbar animation when going from an app to Launcher as part of recents transition.
     * @param toState If known, the state we will end up in when reaching Launcher.
     * @param callbacks callbacks to track the recents animation lifecycle. The state change is
     *                 automatically reset once the recents animation finishes
     */
    public Animator createAnimToLauncher(@NonNull LauncherState toState,
            @NonNull RecentsAnimationCallbacks callbacks, long duration) {
        AnimatorSet set = new AnimatorSet();
        Animator taskbarState = mTaskbarLauncherStateController
                .createAnimToLauncher(toState, callbacks, duration);
        long halfDuration = Math.round(duration * 0.5f);
        Animator translation =
                mControllers.taskbarTranslationController.createAnimToLauncher(halfDuration);

        set.playTogether(taskbarState, translation);
        return set;
    }

    public boolean isDraggingItem() {
        return mControllers.taskbarDragController.isDragging();
    }

    @Override
    protected void onStashedInAppChanged() {
        onStashedInAppChanged(mLauncher.getDeviceProfile());
    }

    private void onStashedInAppChanged(DeviceProfile deviceProfile) {
        boolean taskbarStashedInApps = mControllers.taskbarStashController.isStashedInApp();
        deviceProfile.isTaskbarPresentInApps = !taskbarStashedInApps;
        updateTaskTransitionSpec(taskbarStashedInApps);
    }

    private void updateTaskTransitionSpec(boolean taskbarIsHidden) {
        try {
            if (taskbarIsHidden) {
                // Clear custom task transition settings when the taskbar is stashed
                WindowManagerGlobal.getWindowManagerService().clearTaskTransitionSpec();
            } else {
                // Adjust task transition spec to account for taskbar being visible
                @ColorInt int taskAnimationBackgroundColor =
                        DisplayController.isTransientTaskbar(mLauncher)
                                ? mLauncher.getColor(R.color.transient_taskbar_background)
                                : mLauncher.getColor(R.color.taskbar_background);

                TaskTransitionSpec customTaskAnimationSpec = new TaskTransitionSpec(
                        taskAnimationBackgroundColor,
                        Set.of(ITYPE_EXTRA_NAVIGATION_BAR)
                );
                WindowManagerGlobal.getWindowManagerService()
                        .setTaskTransitionSpec(customTaskAnimationSpec);
            }
        } catch (RemoteException e) {
            // This shouldn't happen but if it does task animations won't look good until the
            // taskbar stashing state is changed.
            Log.e(TAG, "Failed to update task transition spec to account for new taskbar state",
                    e);
        }
    }

    /**
     * Sets whether the background behind the taskbar/nav bar should be hidden.
     */
    public void forceHideBackground(boolean forceHide) {
        mTaskbarOverrideBackgroundAlpha.updateValue(forceHide ? 0 : 1);
    }

    /**
     * Starts a Taskbar EDU flow, if the user should see one upon launching an application.
     */
    public void showEduOnAppLaunch() {
        if (!shouldShowEduOnAppLaunch()) {
            return;
        }

        // Transient and persistent bottom sheet.
        if (!ENABLE_TASKBAR_EDU_TOOLTIP.get()) {
            mLauncher.getOnboardingPrefs().markChecked(OnboardingPrefs.TASKBAR_EDU_SEEN);
            mControllers.taskbarEduController.showEdu();
            return;
        }

        // Persistent features EDU tooltip.
        if (!DisplayController.isTransientTaskbar(mLauncher)) {
            mControllers.taskbarEduTooltipController.maybeShowFeaturesEdu();
            return;
        }

        // Transient swipe EDU tooltip.
        mControllers.taskbarEduTooltipController.maybeShowSwipeEdu();
    }

    /**
     * Returns {@code true} if a Taskbar education should be shown on application launch.
     */
    public boolean shouldShowEduOnAppLaunch() {
        if (Utilities.IS_RUNNING_IN_TEST_HARNESS) {
            return false;
        }

        // Transient and persistent bottom sheet.
        if (!ENABLE_TASKBAR_EDU_TOOLTIP.get()) {
            return !mLauncher.getOnboardingPrefs().getBoolean(OnboardingPrefs.TASKBAR_EDU_SEEN);
        }

        // Persistent features EDU tooltip.
        if (!DisplayController.isTransientTaskbar(mLauncher)) {
            return !mLauncher.getOnboardingPrefs().hasReachedMaxCount(
                    OnboardingPrefs.TASKBAR_EDU_TOOLTIP_STEP);
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

    @Override
    public void setSystemGestureInProgress(boolean inProgress) {
        super.setSystemGestureInProgress(inProgress);
        if (DisplayController.isTransientTaskbar(mLauncher)) {
            forceHideBackground(false);
            return;
        }
        if (!FeatureFlags.ENABLE_TASKBAR_IN_OVERVIEW.get()) {
            // Launcher's ScrimView will draw the background throughout the gesture. But once the
            // gesture ends, start drawing taskbar's background again since launcher might stop
            // drawing.
            forceHideBackground(inProgress);
        }
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
        if (mControllers.uiController.isIconAlignedWithHotseat()
                && !mTaskbarLauncherStateController.isAnimatingToLauncher()) {
            // Only animate the nav buttons while home and not animating home, otherwise let
            // the TaskbarViewController handle it.
            mControllers.navbarButtonsViewController
                    .getTaskbarNavButtonTranslationYForInAppDisplay()
                    .updateValue(mLauncher.getDeviceProfile().getTaskbarOffsetY()
                            * mTaskbarInAppDisplayProgress.value);
        }
    }

    /** Returns true iff any in-app display progress > 0. */
    public boolean shouldUseInAppLayout() {
        return mTaskbarInAppDisplayProgress.value > 0;
    }

    @Override
    public void onExpandPip() {
        super.onExpandPip();
        mTaskbarLauncherStateController.updateStateForFlag(FLAG_RESUMED, false);
        mTaskbarLauncherStateController.applyState();
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
    public RecentsView getRecentsView() {
        return mLauncher.getOverviewPanel();
    }

    @Override
    public void launchSplitTasks(View taskView, GroupTask groupTask) {
        super.launchSplitTasks(taskView, groupTask);
        mLauncher.launchSplitTasks(taskView, groupTask);
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        super.dumpLogs(prefix, pw);

        pw.println(String.format(
                "%s\tmTaskbarOverrideBackgroundAlpha=%.2f",
                prefix,
                mTaskbarOverrideBackgroundAlpha.value));
        pw.println(String.format("%s\tTaskbar in-app display progress:", prefix));
        mTaskbarInAppDisplayProgressMultiProp.dump(
                prefix + "\t",
                pw,
                "mTaskbarInAppDisplayProgressMultiProp",
                "MINUS_ONE_PAGE_PROGRESS_INDEX",
                "ALL_APPS_PAGE_PROGRESS_INDEX",
                "WIDGETS_PAGE_PROGRESS_INDEX",
                "SYSUI_SURFACE_PROGRESS_INDEX");

        mTaskbarLauncherStateController.dumpLogs(prefix + "\t", pw);
    }
}
