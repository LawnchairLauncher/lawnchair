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

import static com.android.launcher3.taskbar.TaskbarLauncherStateController.FLAG_RESUMED;
import static com.android.systemui.shared.system.WindowManagerWrapper.ITYPE_EXTRA_NAVIGATION_BAR;

import android.animation.Animator;
import android.annotation.ColorInt;
import android.graphics.Rect;
import android.os.RemoteException;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TaskTransitionSpec;
import android.view.WindowManagerGlobal;

import androidx.annotation.NonNull;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherState;
import com.android.launcher3.QuickstepTransitionManager;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.InstanceIdSequence;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.OnboardingPrefs;
import com.android.quickstep.AnimatedFloat;
import com.android.quickstep.RecentsAnimationCallbacks;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A data source which integrates with a Launcher instance
 */
public class LauncherTaskbarUIController extends TaskbarUIController {

    private static final String TAG = "TaskbarUIController";

    private final BaseQuickstepLauncher mLauncher;

    private final DeviceProfile.OnDeviceProfileChangeListener mOnDeviceProfileChangeListener =
            this::onStashedInAppChanged;

    // Initialized in init.
    private AnimatedFloat mTaskbarOverrideBackgroundAlpha;
    private TaskbarKeyguardController mKeyguardController;
    private final TaskbarLauncherStateController
            mTaskbarLauncherStateController = new TaskbarLauncherStateController();

    private final DeviceProfile.OnDeviceProfileChangeListener mProfileChangeListener =
            new DeviceProfile.OnDeviceProfileChangeListener() {
                @Override
                public void onDeviceProfileChanged(DeviceProfile dp) {
                    mControllers.taskbarViewController.onRotationChanged(
                            mLauncher.getDeviceProfile());
                }
            };

    public LauncherTaskbarUIController(BaseQuickstepLauncher launcher) {
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
        mLauncher.addOnDeviceProfileChangeListener(mProfileChangeListener);
    }

    @Override
    protected void onDestroy() {
        onLauncherResumedOrPaused(false);
        mTaskbarLauncherStateController.onDestroy();

        mLauncher.removeOnDeviceProfileChangeListener(mOnDeviceProfileChangeListener);
        mLauncher.setTaskbarUIController(null);
        mLauncher.removeOnDeviceProfileChangeListener(mProfileChangeListener);
        updateTaskTransitionSpec(true);
    }

    @Override
    protected boolean isTaskbarTouchable() {
        return !mTaskbarLauncherStateController.isAnimatingToLauncher();
    }

    @Override
    protected void updateContentInsets(Rect outContentInsets) {
        int contentHeight = mControllers.taskbarStashController.getContentHeight();
        TaskbarDragLayer dragLayer = mControllers.taskbarActivityContext.getDragLayer();
        outContentInsets.top = dragLayer.getHeight() - contentHeight;
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

        mTaskbarLauncherStateController.updateStateForFlag(FLAG_RESUMED, isResumed);
        mTaskbarLauncherStateController.applyState(
                fromInit ? 0 : QuickstepTransitionManager.CONTENT_ALPHA_DURATION);
    }

    /**
     * Create Taskbar animation when going from an app to Launcher as part of recents transition.
     * @param toState If known, the state we will end up in when reaching Launcher.
     * @param callbacks callbacks to track the recents animation lifecycle. The state change is
     *                 automatically reset once the recents animation finishes
     */
    public Animator createAnimToLauncher(@NonNull LauncherState toState,
            @NonNull RecentsAnimationCallbacks callbacks, long duration) {
        return mTaskbarLauncherStateController.createAnimToLauncher(toState, callbacks, duration);
    }

    /**
     * @param ev MotionEvent in screen coordinates.
     * @return Whether any Taskbar item could handle the given MotionEvent if given the chance.
     */
    public boolean isEventOverAnyTaskbarItem(MotionEvent ev) {
        return mControllers.taskbarViewController.isEventOverAnyItem(ev)
                || mControllers.navbarButtonsViewController.isEventOverAnyItem(ev);
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
                        mLauncher.getColor(R.color.taskbar_background);

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
        mLauncher.logAppLaunch(mControllers.taskbarActivityContext.getStatsLogManager(), item,
                instanceId);
    }

    @Override
    public void setSystemGestureInProgress(boolean inProgress) {
        super.setSystemGestureInProgress(inProgress);
        // Launcher's ScrimView will draw the background throughout the gesture. But once the
        // gesture ends, start drawing taskbar's background again since launcher might stop drawing.
        forceHideBackground(inProgress);
    }
}
