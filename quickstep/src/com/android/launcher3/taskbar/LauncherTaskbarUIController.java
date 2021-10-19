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

import static com.android.launcher3.LauncherState.HOTSEAT_ICONS;
import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_APP;
import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_STASHED_LAUNCHER_STATE;
import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_STASHED_IN_APP_SETUP;
import static com.android.launcher3.taskbar.TaskbarViewController.ALPHA_INDEX_HOME;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherState;
import com.android.launcher3.QuickstepTransitionManager;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.InstanceIdSequence;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.MultiValueAlpha.AlphaProperty;
import com.android.launcher3.util.OnboardingPrefs;
import com.android.quickstep.AnimatedFloat;
import com.android.quickstep.RecentsAnimationCallbacks;
import com.android.quickstep.RecentsAnimationCallbacks.RecentsAnimationListener;
import com.android.quickstep.RecentsAnimationController;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.recents.model.ThumbnailData;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * A data source which integrates with a Launcher instance
 */
public class LauncherTaskbarUIController extends TaskbarUIController {

    private final BaseQuickstepLauncher mLauncher;

    private final TaskbarActivityContext mContext;
    private final TaskbarDragLayer mTaskbarDragLayer;
    private final TaskbarView mTaskbarView;

    private final AnimatedFloat mIconAlignmentForResumedState =
            new AnimatedFloat(this::onIconAlignmentRatioChanged);
    private final AnimatedFloat mIconAlignmentForGestureState =
            new AnimatedFloat(this::onIconAlignmentRatioChanged);

    private final DeviceProfile.OnDeviceProfileChangeListener mOnDeviceProfileChangeListener =
            this::onStashedInAppChanged;

    private final StateManager.StateListener<LauncherState> mStateListener =
            new StateManager.StateListener<LauncherState>() {
                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    TaskbarStashController controller = mControllers.taskbarStashController;
                    controller.updateStateForFlag(FLAG_IN_STASHED_LAUNCHER_STATE,
                            finalState.isTaskbarStashed());
                }
            };

    // Initialized in init.
    private TaskbarControllers mControllers;
    private AnimatedFloat mTaskbarBackgroundAlpha;
    private AnimatedFloat mTaskbarOverrideBackgroundAlpha;
    private AlphaProperty mIconAlphaForHome;
    private boolean mIsAnimatingToLauncherViaResume;
    private boolean mIsAnimatingToLauncherViaGesture;
    private TaskbarKeyguardController mKeyguardController;

    private LauncherState mTargetStateOverride = null;
    private final DeviceProfile.OnDeviceProfileChangeListener mProfileChangeListener =
            new DeviceProfile.OnDeviceProfileChangeListener() {
                @Override
                public void onDeviceProfileChanged(DeviceProfile dp) {
                    mControllers.taskbarViewController.onRotationChanged(
                            mLauncher.getDeviceProfile());
                }
            };

    public LauncherTaskbarUIController(
            BaseQuickstepLauncher launcher, TaskbarActivityContext context) {
        mContext = context;
        mTaskbarDragLayer = context.getDragLayer();
        mTaskbarView = mTaskbarDragLayer.findViewById(R.id.taskbar_view);

        mLauncher = launcher;
    }

    @Override
    protected void init(TaskbarControllers taskbarControllers) {
        mControllers = taskbarControllers;

        mTaskbarBackgroundAlpha = mControllers.taskbarDragLayerController
                .getTaskbarBackgroundAlpha();
        mTaskbarOverrideBackgroundAlpha = mControllers.taskbarDragLayerController
                .getOverrideBackgroundAlpha();

        MultiValueAlpha taskbarIconAlpha = mControllers.taskbarViewController.getTaskbarIconAlpha();
        mIconAlphaForHome = taskbarIconAlpha.getProperty(ALPHA_INDEX_HOME);

        mLauncher.setTaskbarUIController(this);
        mKeyguardController = taskbarControllers.taskbarKeyguardController;

        onLauncherResumedOrPaused(mLauncher.hasBeenResumed(), true /* fromInit */);
        mIconAlignmentForResumedState.finishAnimation();
        onIconAlignmentRatioChanged();

        onStashedInAppChanged(mLauncher.getDeviceProfile());
        mLauncher.addOnDeviceProfileChangeListener(mOnDeviceProfileChangeListener);
        mLauncher.getStateManager().addStateListener(mStateListener);
        mLauncher.addOnDeviceProfileChangeListener(mProfileChangeListener);
    }

    @Override
    protected void onDestroy() {
        onLauncherResumedOrPaused(false);
        mIconAlignmentForResumedState.finishAnimation();
        mIconAlignmentForGestureState.finishAnimation();

        mLauncher.removeOnDeviceProfileChangeListener(mOnDeviceProfileChangeListener);
        mLauncher.getStateManager().removeStateListener(mStateListener);
        mLauncher.getHotseat().setIconsAlpha(1f);
        mLauncher.setTaskbarUIController(null);
        mLauncher.removeOnDeviceProfileChangeListener(mProfileChangeListener);
    }

    @Override
    protected boolean isTaskbarTouchable() {
        return !isAnimatingToLauncher();
    }

    private boolean isAnimatingToLauncher() {
        return mIsAnimatingToLauncherViaResume || mIsAnimatingToLauncherViaGesture;
    }

    @Override
    protected void updateContentInsets(Rect outContentInsets) {
        int contentHeight = mControllers.taskbarStashController.getContentHeight();
        outContentInsets.top = mTaskbarDragLayer.getHeight() - contentHeight;
    }

    /**
     * Should be called from onResume() and onPause(), and animates the Taskbar accordingly.
     */
    public void onLauncherResumedOrPaused(boolean isResumed) {
        onLauncherResumedOrPaused(isResumed, false /* fromInit */);
    }

    private void onLauncherResumedOrPaused(boolean isResumed, boolean fromInit) {
        if (mKeyguardController.isScreenOff()) {
            if (!isResumed) {
                return;
            } else {
                // Resuming implicitly means device unlocked
                mKeyguardController.setScreenOn();
            }
        }

        long duration = QuickstepTransitionManager.CONTENT_ALPHA_DURATION;
        if (fromInit) {
            // Since we are creating the starting state, we don't have a state to animate from, so
            // set our state immediately.
            duration = 0;
        }
        ObjectAnimator anim = mIconAlignmentForResumedState.animateToValue(
                getCurrentIconAlignmentRatio(), isResumed ? 1 : 0)
                .setDuration(duration);

        anim.addListener(AnimatorListeners.forEndCallback(
                () -> mIsAnimatingToLauncherViaResume = false));
        anim.start();
        mIsAnimatingToLauncherViaResume = isResumed;

        TaskbarStashController stashController = mControllers.taskbarStashController;
        stashController.updateStateForFlag(FLAG_IN_APP, !isResumed);
        if (isResumed) {
            // Launcher is resumed, meaning setup must be finished.
            stashController.updateStateForFlag(FLAG_STASHED_IN_APP_SETUP, false);
        }
        stashController.applyState(duration);
    }

    /**
     * Create Taskbar animation when going from an app to Launcher as part of recents transition.
     * @param toState If known, the state we will end up in when reaching Launcher.
     * @param callbacks callbacks to track the recents animation lifecycle. The state change is
     *                 automatically reset once the recents animation finishes
     */
    public Animator createAnimToLauncher(@NonNull LauncherState toState,
            @NonNull RecentsAnimationCallbacks callbacks, long duration) {
        AnimatorSet animatorSet = new AnimatorSet();
        TaskbarStashController stashController = mControllers.taskbarStashController;
        stashController.updateStateForFlag(FLAG_IN_STASHED_LAUNCHER_STATE,
                toState.isTaskbarStashed());
        if (toState.isTaskbarStashed()) {
            animatorSet.play(stashController.applyStateWithoutStart(duration));
        } else {
            animatorSet.play(mIconAlignmentForGestureState
                    .animateToValue(1)
                    .setDuration(duration));
        }
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                mTargetStateOverride = null;
                animator.removeListener(this);
            }

            @Override
            public void onAnimationStart(Animator animator) {
                mTargetStateOverride = toState;
                mIsAnimatingToLauncherViaGesture = true;
                stashController.updateStateForFlag(FLAG_IN_APP, false);
                stashController.applyState(duration);
            }
        });

        TaskBarRecentsAnimationListener listener = new TaskBarRecentsAnimationListener(callbacks);
        callbacks.addListener(listener);
        RecentsView recentsView = mLauncher.getOverviewPanel();
        recentsView.setTaskLaunchListener(() -> {
            listener.endGestureStateOverride(true);
            callbacks.removeListener(listener);
        });

        return animatorSet;
    }

    private float getCurrentIconAlignmentRatio() {
        return Math.max(mIconAlignmentForResumedState.value, mIconAlignmentForGestureState.value);
    }

    private void onIconAlignmentRatioChanged() {
        if (mControllers == null) {
            return;
        }
        float alignment = getCurrentIconAlignmentRatio();
        mControllers.taskbarViewController.setLauncherIconAlignment(
                alignment, mLauncher.getDeviceProfile());

        mTaskbarBackgroundAlpha.updateValue(1 - alignment);

        LauncherState state = mTargetStateOverride != null ? mTargetStateOverride
                : mLauncher.getStateManager().getState();
        if ((state.getVisibleElements(mLauncher) & HOTSEAT_ICONS) != 0) {
            // If the hotseat icons are visible, then switch taskbar in last frame
            setTaskbarViewVisible(alignment < 1);
        } else {
            mLauncher.getHotseat().setIconsAlpha(1);
            mIconAlphaForHome.setValue(1 - alignment);
        }
    }

    /**
     * @param ev MotionEvent in screen coordinates.
     * @return Whether any Taskbar item could handle the given MotionEvent if given the chance.
     */
    public boolean isEventOverAnyTaskbarItem(MotionEvent ev) {
        return mTaskbarView.isEventOverAnyItem(ev);
    }

    public boolean isDraggingItem() {
        return mContext.getDragController().isDragging();
    }

    public View getRootView() {
        return mTaskbarDragLayer;
    }

    private void setTaskbarViewVisible(boolean isVisible) {
        mIconAlphaForHome.setValue(isVisible ? 1 : 0);
        mLauncher.getHotseat().setIconsAlpha(isVisible ? 0f : 1f);
    }

    @Override
    protected void onStashedInAppChanged() {
        onStashedInAppChanged(mLauncher.getDeviceProfile());
    }

    private void onStashedInAppChanged(DeviceProfile deviceProfile) {
        boolean taskbarStashedInApps = mControllers.taskbarStashController.isStashedInApp();
        deviceProfile.isTaskbarPresentInApps = !taskbarStashedInApps;
    }

    /**
     * Sets whether the background behind the taskbar/nav bar should be hidden.
     */
    public void forceHideBackground(boolean forceHide) {
        mTaskbarOverrideBackgroundAlpha.updateValue(forceHide ? 0 : 1);
    }

    @Override
    public Stream<ItemInfoWithIcon> getAppIconsForEdu() {
        return Arrays.stream(mLauncher.getAppsView().getAppsStore().getApps());
    }

    /**
     * Starts the taskbar education flow, if the user hasn't seen it yet.
     */
    public void showEdu() {
        if (!FeatureFlags.ENABLE_TASKBAR_EDU.get()
                || Utilities.IS_RUNNING_IN_TEST_HARNESS
                || mLauncher.getOnboardingPrefs().getBoolean(OnboardingPrefs.TASKBAR_EDU_SEEN)) {
            return;
        }
        mLauncher.getOnboardingPrefs().markChecked(OnboardingPrefs.TASKBAR_EDU_SEEN);

        mControllers.taskbarEduController.showEdu();
    }

    /**
     * Manually ends the taskbar education flow.
     */
    public void hideEdu() {
        if (!FeatureFlags.ENABLE_TASKBAR_EDU.get()) {
            return;
        }

        mControllers.taskbarEduController.hideEdu();
    }

    @Override
    public void onTaskbarIconLaunched(WorkspaceItemInfo item) {
        InstanceId instanceId = new InstanceIdSequence().newInstanceId();
        mLauncher.logAppLaunch(mContext.getStatsLogManager(), item, instanceId);
    }

    private final class TaskBarRecentsAnimationListener implements RecentsAnimationListener {
        private final RecentsAnimationCallbacks mCallbacks;

        TaskBarRecentsAnimationListener(RecentsAnimationCallbacks callbacks) {
            mCallbacks = callbacks;
        }

        @Override
        public void onRecentsAnimationCanceled(ThumbnailData thumbnailData) {
            endGestureStateOverride(true);
        }

        @Override
        public void onRecentsAnimationFinished(RecentsAnimationController controller) {
            endGestureStateOverride(!controller.getFinishTargetIsLauncher());
        }

        private void endGestureStateOverride(boolean finishedToApp) {
            mCallbacks.removeListener(this);
            mIsAnimatingToLauncherViaGesture = false;

            mIconAlignmentForGestureState
                    .animateToValue(0)
                    .start();

            TaskbarStashController controller = mControllers.taskbarStashController;
            controller.updateStateForFlag(FLAG_IN_APP, finishedToApp);
            controller.applyState();
        }
    }
}
