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
import static com.android.launcher3.taskbar.TaskbarViewController.ALPHA_INDEX_HOME;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
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
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.MultiValueAlpha.AlphaProperty;
import com.android.launcher3.util.OnboardingPrefs;
import com.android.quickstep.AnimatedFloat;
import com.android.quickstep.RecentsAnimationCallbacks;
import com.android.quickstep.RecentsAnimationCallbacks.RecentsAnimationListener;
import com.android.quickstep.RecentsAnimationController;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.recents.model.ThumbnailData;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * A data source which integrates with a Launcher instance
 */
public class LauncherTaskbarUIController extends TaskbarUIController {

    private final BaseQuickstepLauncher mLauncher;
    private final TaskbarStateHandler mTaskbarStateHandler;

    private final TaskbarActivityContext mContext;
    private final TaskbarDragLayer mTaskbarDragLayer;
    private final TaskbarView mTaskbarView;

    private final AnimatedFloat mIconAlignmentForResumedState =
            new AnimatedFloat(this::onIconAlignmentRatioChanged);
    private final AnimatedFloat mIconAlignmentForGestureState =
            new AnimatedFloat(this::onIconAlignmentRatioChanged);

    private final DeviceProfile.OnDeviceProfileChangeListener mOnDeviceProfileChangeListener =
            this::onStashedInAppChanged;

    // Initialized in init.
    private TaskbarControllers mControllers;
    private AnimatedFloat mTaskbarBackgroundAlpha;
    private AnimatedFloat mTaskbarOverrideBackgroundAlpha;
    private AlphaProperty mIconAlphaForHome;
    private boolean mIsAnimatingToLauncherViaResume;
    private boolean mIsAnimatingToLauncherViaGesture;
    private TaskbarKeyguardController mKeyguardController;

    private LauncherState mTargetStateOverride = null;

    public LauncherTaskbarUIController(
            BaseQuickstepLauncher launcher, TaskbarActivityContext context) {
        mContext = context;
        mTaskbarDragLayer = context.getDragLayer();
        mTaskbarView = mTaskbarDragLayer.findViewById(R.id.taskbar_view);

        mLauncher = launcher;
        mTaskbarStateHandler = mLauncher.getTaskbarStateHandler();
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

        onLauncherResumedOrPaused(mLauncher.hasBeenResumed());
        mIconAlignmentForResumedState.finishAnimation();
        onIconAlignmentRatioChanged();

        onStashedInAppChanged(mLauncher.getDeviceProfile());
        mLauncher.addOnDeviceProfileChangeListener(mOnDeviceProfileChangeListener);
    }

    @Override
    protected void onDestroy() {
        onLauncherResumedOrPaused(false);
        mIconAlignmentForResumedState.finishAnimation();
        mIconAlignmentForGestureState.finishAnimation();

        mLauncher.removeOnDeviceProfileChangeListener(mOnDeviceProfileChangeListener);
        mLauncher.getHotseat().setIconsAlpha(1f);
        mLauncher.setTaskbarUIController(null);
    }

    @Override
    protected boolean isTaskbarTouchable() {
        return !isAnimatingToLauncher() && !mControllers.taskbarStashController.isStashed();
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
        if (mKeyguardController.isScreenOff()) {
            if (!isResumed) {
                return;
            } else {
                // Resuming implicitly means device unlocked
                mKeyguardController.setScreenOn();
            }
        }

        long duration = QuickstepTransitionManager.CONTENT_ALPHA_DURATION;
        ObjectAnimator anim = mIconAlignmentForResumedState.animateToValue(
                getCurrentIconAlignmentRatio(), isResumed ? 1 : 0)
                .setDuration(duration);

        anim.addListener(AnimatorListeners.forEndCallback(
                () -> mIsAnimatingToLauncherViaResume = false));
        anim.start();
        mIsAnimatingToLauncherViaResume = isResumed;

        if (!isResumed) {
            TaskbarStashController stashController = mControllers.taskbarStashController;
            stashController.animateToIsStashed(stashController.isStashedInApp(), duration);
        }
        SystemUiProxy.INSTANCE.get(mContext).notifyTaskbarStatus(!isResumed,
                mControllers.taskbarStashController.isStashedInApp());
    }

    /**
     * Create Taskbar animation when going from an app to Launcher as part of recents transition.
     * @param toState If known, the state we will end up in when reaching Launcher.
     * @param callbacks callbacks to track the recents animation lifecycle. The state change is
     *                 automatically reset once the recents animation finishes
     */
    public Animator createAnimToLauncher(@NonNull LauncherState toState,
            @NonNull RecentsAnimationCallbacks callbacks, long duration) {
        TaskbarStashController stashController = mControllers.taskbarStashController;
        ObjectAnimator animator = mIconAlignmentForGestureState
                .animateToValue(1)
                .setDuration(duration);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mTargetStateOverride = null;
                animator.removeListener(this);
            }

            @Override
            public void onAnimationStart(Animator animation) {
                mTargetStateOverride = toState;
                mIsAnimatingToLauncherViaGesture = true;
                // TODO: base this on launcher state
                stashController.animateToIsStashed(false, duration);
            }
        });

        TaskBarRecentsAnimationListener listener = new TaskBarRecentsAnimationListener(callbacks);
        callbacks.addListener(listener);
        RecentsView recentsView = mLauncher.getOverviewPanel();
        recentsView.setTaskLaunchListener(() -> {
            listener.endGestureStateOverride(true);
            callbacks.removeListener(listener);
        });

        return animator;
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
            if (finishedToApp) {
                controller.animateToIsStashed(controller.isStashedInApp());
            }
        }
    }
}
