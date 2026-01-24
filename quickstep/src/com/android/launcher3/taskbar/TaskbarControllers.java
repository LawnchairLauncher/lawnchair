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

import android.animation.AnimatorSet;
import android.content.pm.ActivityInfo.Config;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.taskbar.allapps.TaskbarAllAppsController;
import com.android.launcher3.taskbar.bubbles.BubbleControllers;
import com.android.launcher3.taskbar.growth.NudgeController;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayController;
import com.android.systemui.shared.rotation.RotationButtonController;
import com.android.wm.shell.shared.bubbles.BubbleBarLocation;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Hosts various taskbar controllers to facilitate passing between one another.
 */
public class TaskbarControllers {

    public final TaskbarActivityContext taskbarActivityContext;

    public final TaskbarDragController taskbarDragController;
    public final TaskbarNavButtonController navButtonController;
    public final NavbarButtonsViewController navbarButtonsViewController;
    public final RotationButtonController rotationButtonController;
    public final TaskbarDragLayerController taskbarDragLayerController;
    public final TaskbarScrimViewController taskbarScrimViewController;
    public final TaskbarViewController taskbarViewController;
    public final TaskbarUnfoldAnimationController taskbarUnfoldAnimationController;
    public final TaskbarKeyguardController taskbarKeyguardController;
    public final StashedHandleViewController stashedHandleViewController;
    public final TaskbarStashController taskbarStashController;
    public final TaskbarAutohideSuspendController taskbarAutohideSuspendController;
    public final TaskbarPopupController taskbarPopupController;
    public final TaskbarForceVisibleImmersiveController taskbarForceVisibleImmersiveController;
    public final TaskbarAllAppsController taskbarAllAppsController;
    public final TaskbarInsetsController taskbarInsetsController;
    public final VoiceInteractionWindowController voiceInteractionWindowController;
    public final TaskbarRecentAppsController taskbarRecentAppsController;
    public final TaskbarTranslationController taskbarTranslationController;
    public final TaskbarSpringOnStashController taskbarSpringOnStashController;
    public final TaskbarOverlayController taskbarOverlayController;
    public final TaskbarEduTooltipController taskbarEduTooltipController;
    public final KeyboardQuickSwitchController keyboardQuickSwitchController;
    public final TaskbarPinningController taskbarPinningController;
    public final Optional<BubbleControllers> bubbleControllers;
    public final TaskbarDesktopModeController taskbarDesktopModeController;
    public final NudgeController nudgeController;
    public final NudgeViewController nudgeViewController;

    @Nullable private LoggableTaskbarController[] mControllersToLog = null;
    @Nullable private BackgroundRendererController[] mBackgroundRendererControllers = null;

    /** Do not store this controller, as it may change at runtime. */
    @NonNull public TaskbarUIController uiController = TaskbarUIController.DEFAULT;

    private boolean mAreAllControllersInitialized;
    private final List<Runnable> mPostInitCallbacks = new ArrayList<>();

    @Nullable private TaskbarSharedState mSharedState = null;

    // Roundness property for round corner above taskbar .
    private final AnimatedFloat mCornerRoundness = new AnimatedFloat(this::updateCornerRoundness);

    /**
     * Want to add a new controller? Don't forget to:
     *   * Call init
     *   * Call onDestroy
     *   * Add to mControllersToLog
     *   * Add tests by adding this controller to TaskbarBaseTestCase.kt and extending that class
     */
    public TaskbarControllers(TaskbarActivityContext taskbarActivityContext,
            TaskbarDragController taskbarDragController,
            TaskbarNavButtonController navButtonController,
            NavbarButtonsViewController navbarButtonsViewController,
            RotationButtonController rotationButtonController,
            TaskbarDragLayerController taskbarDragLayerController,
            TaskbarViewController taskbarViewController,
            TaskbarScrimViewController taskbarScrimViewController,
            TaskbarUnfoldAnimationController taskbarUnfoldAnimationController,
            TaskbarKeyguardController taskbarKeyguardController,
            StashedHandleViewController stashedHandleViewController,
            TaskbarStashController taskbarStashController,
            TaskbarAutohideSuspendController taskbarAutoHideSuspendController,
            TaskbarPopupController taskbarPopupController,
            TaskbarForceVisibleImmersiveController taskbarForceVisibleImmersiveController,
            TaskbarOverlayController taskbarOverlayController,
            TaskbarAllAppsController taskbarAllAppsController,
            TaskbarInsetsController taskbarInsetsController,
            VoiceInteractionWindowController voiceInteractionWindowController,
            TaskbarTranslationController taskbarTranslationController,
            TaskbarSpringOnStashController taskbarSpringOnStashController,
            TaskbarRecentAppsController taskbarRecentAppsController,
            TaskbarEduTooltipController taskbarEduTooltipController,
            KeyboardQuickSwitchController keyboardQuickSwitchController,
            TaskbarPinningController taskbarPinningController,
            Optional<BubbleControllers> bubbleControllers,
            TaskbarDesktopModeController taskbarDesktopModeController,
            NudgeController nudgeController,
            NudgeViewController nudgeViewController) {
        this.taskbarActivityContext = taskbarActivityContext;
        this.taskbarDragController = taskbarDragController;
        this.navButtonController = navButtonController;
        this.navbarButtonsViewController = navbarButtonsViewController;
        this.rotationButtonController = rotationButtonController;
        this.taskbarDragLayerController = taskbarDragLayerController;
        this.taskbarViewController = taskbarViewController;
        this.taskbarScrimViewController = taskbarScrimViewController;
        this.taskbarUnfoldAnimationController = taskbarUnfoldAnimationController;
        this.taskbarKeyguardController = taskbarKeyguardController;
        this.stashedHandleViewController = stashedHandleViewController;
        this.taskbarStashController = taskbarStashController;
        this.taskbarAutohideSuspendController = taskbarAutoHideSuspendController;
        this.taskbarPopupController = taskbarPopupController;
        this.taskbarForceVisibleImmersiveController = taskbarForceVisibleImmersiveController;
        this.taskbarOverlayController = taskbarOverlayController;
        this.taskbarAllAppsController = taskbarAllAppsController;
        this.taskbarInsetsController = taskbarInsetsController;
        this.voiceInteractionWindowController = voiceInteractionWindowController;
        this.taskbarTranslationController = taskbarTranslationController;
        this.taskbarSpringOnStashController = taskbarSpringOnStashController;
        this.taskbarRecentAppsController = taskbarRecentAppsController;
        this.taskbarEduTooltipController = taskbarEduTooltipController;
        this.keyboardQuickSwitchController = keyboardQuickSwitchController;
        this.taskbarPinningController = taskbarPinningController;
        this.bubbleControllers = bubbleControllers;
        this.taskbarDesktopModeController = taskbarDesktopModeController;
        this.nudgeController = nudgeController;
        this.nudgeViewController = nudgeViewController;
    }

    /**
     * Initializes all controllers. Note that controllers can now reference each other through this
     * TaskbarControllers instance, but should be careful to only access things that were created
     * in constructors for now, as some controllers may still be waiting for init().
     */
    public void init(@NonNull TaskbarSharedState sharedState, AnimatorSet startAnimation) {
        mAreAllControllersInitialized = false;
        mSharedState = sharedState;

        taskbarDragController.init(this);
        navbarButtonsViewController.init(this);
        rotationButtonController.init();
        taskbarDragLayerController.init(this, startAnimation);
        taskbarViewController.init(this, startAnimation);
        taskbarScrimViewController.init(this);
        taskbarUnfoldAnimationController.init(this);
        taskbarKeyguardController.init(navbarButtonsViewController);
        taskbarSpringOnStashController.init(this);
        stashedHandleViewController.init(this);
        taskbarStashController.init(this, sharedState.setupUIVisible, mSharedState);
        taskbarPopupController.init(this);
        taskbarForceVisibleImmersiveController.init(this);
        taskbarOverlayController.init(this);
        taskbarAllAppsController.init(this, sharedState.allAppsVisible);
        navButtonController.init(this);
        bubbleControllers.ifPresentOrElse(controllers -> controllers.init(sharedState, this),
                sharedState::clearBubbleData);
        taskbarInsetsController.init(this);
        voiceInteractionWindowController.init(this);
        taskbarRecentAppsController.init(this, sharedState.recentTasksBeforeTaskbarRecreate);
        taskbarTranslationController.init(this);
        taskbarEduTooltipController.init(this);
        keyboardQuickSwitchController.init(this);
        taskbarPinningController.init(this, mSharedState);
        taskbarDesktopModeController.init(this, mSharedState);
        nudgeController.init(this);

        mControllersToLog = new LoggableTaskbarController[] {
                taskbarDragController, navButtonController, navbarButtonsViewController,
                taskbarDragLayerController, taskbarScrimViewController, taskbarViewController,
                taskbarUnfoldAnimationController, taskbarKeyguardController,
                stashedHandleViewController, taskbarStashController,
                taskbarAutohideSuspendController, taskbarPopupController, taskbarInsetsController,
                voiceInteractionWindowController, taskbarRecentAppsController,
                taskbarTranslationController, taskbarEduTooltipController,
                keyboardQuickSwitchController, taskbarPinningController,
                nudgeController
        };
        mBackgroundRendererControllers = new BackgroundRendererController[] {
                taskbarDragLayerController, taskbarScrimViewController,
                voiceInteractionWindowController
        };

        // TODO(b/401061748): get primary status from
        //  TaskbarDesktopModeController/DesktopVisibilityController.
        if (taskbarDesktopModeController.isInDesktopModeAndNotInOverview(
                taskbarActivityContext.getDisplayId())
                || !taskbarActivityContext.isPrimaryDisplay()) {
            mCornerRoundness.value = taskbarDesktopModeController.getTaskbarCornerRoundness(
                    mSharedState.showCornerRadiusInDesktopMode);
        } else {
            mCornerRoundness.value = TaskbarBackgroundRenderer.MAX_ROUNDNESS;
        }
        updateCornerRoundness();
        onPostInit();
    }

    @VisibleForTesting
    public void onPostInit() {
        mAreAllControllersInitialized = true;
        for (Runnable postInitCallback : mPostInitCallbacks) {
            postInitCallback.run();
        }
        mPostInitCallbacks.clear();
    }

    /**
     * Sets the ui controller.
     */
    public void setUiController(@NonNull TaskbarUIController newUiController) {
        uiController.onDestroy();
        uiController = newUiController;
        uiController.init(this);
        uiController.updateStateForSysuiFlags(mSharedState.sysuiStateFlags);
        // if bubble controllers are present configure the UI controller
        bubbleControllers.ifPresentOrElse(bubbleControllers -> {
            BubbleBarLocation location =
                    bubbleControllers.bubbleBarViewController.getBubbleBarLocation();
            boolean hiddenForBubbles =
                    bubbleControllers.bubbleBarViewController.isHiddenForNoBubbles();
            if (!hiddenForBubbles) {
                uiController.adjustHotseatForBubbleBar(/* isBubbleBarVisible= */ true);
            }
            uiController.onBubbleBarLocationUpdated(location);
        }, () -> uiController.onBubbleBarLocationUpdated(null));
        // Notify that the ui controller has changed
        navbarButtonsViewController.onUiControllerChanged();
        taskbarViewController.onUiControllerChanged();
    }

    @Nullable
    public TaskbarSharedState getSharedState() {
        // This should only be null if called before init() and after destroy().
        return mSharedState;
    }

    public void onConfigurationChanged(@Config int configChanges) {
        navbarButtonsViewController.onConfigurationChanged(configChanges);
        taskbarDragLayerController.onConfigurationChanged();
        keyboardQuickSwitchController.onConfigurationChanged(configChanges);
    }

    /**
     * Cleans up all controllers.
     */
    public void onDestroy() {
        mAreAllControllersInitialized = false;

        taskbarDragController.onDestroy();
        navbarButtonsViewController.onDestroy();
        uiController.onDestroy();
        rotationButtonController.onDestroy();
        taskbarDragLayerController.onDestroy();
        taskbarUnfoldAnimationController.onDestroy();
        taskbarViewController.onDestroy();
        stashedHandleViewController.onDestroy();
        nudgeViewController.onDestroy();
        taskbarAutohideSuspendController.onDestroy();
        taskbarPopupController.onDestroy();
        taskbarForceVisibleImmersiveController.onDestroy();
        taskbarOverlayController.onDestroy();
        taskbarAllAppsController.onDestroy();
        navButtonController.onDestroy();
        taskbarInsetsController.onDestroy();
        voiceInteractionWindowController.onDestroy();
        taskbarRecentAppsController.onDestroy();
        keyboardQuickSwitchController.onDestroy();
        taskbarStashController.onDestroy();
        bubbleControllers.ifPresent(controllers -> controllers.onDestroy());
        taskbarDesktopModeController.onDestroy();
        mControllersToLog = null;
        mBackgroundRendererControllers = null;
        mSharedState = null;
    }

    /**
     * If all controllers are already initialized, runs the given callback immediately. Otherwise,
     * queues it to run after calling init() on all controllers. This should likely be used in any
     * case where one controller is telling another controller to do something inside init().
     */
    public void runAfterInit(Runnable callback) {
        if (mAreAllControllersInitialized) {
            callback.run();
        } else {
            mPostInitCallbacks.add(callback);
        }
    }

    protected void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarControllers:");

        if (mControllersToLog == null) {
            pw.println(String.format(
                    "%s\t%s", prefix, "All taskbar controllers have already been destroyed."));
            return;
        }

        pw.println(String.format(
                "%s\tmAreAllControllersInitialized=%b", prefix, mAreAllControllersInitialized));
        for (LoggableTaskbarController controller : mControllersToLog) {
            controller.dumpLogs(prefix + "\t", pw);
        }
        uiController.dumpLogs(prefix + "\t", pw);
        rotationButtonController.dumpLogs(prefix + "\t", pw);
        if (bubbleControllers.isPresent()) {
            bubbleControllers.get().dump(pw);
        } else {
            pw.println(String.format("%s\t%s", prefix, "Bubble controllers are empty."));
        }
    }

    /**
     * Returns a float property that animates roundness of the round corner above Taskbar.
     */
    public AnimatedFloat getTaskbarCornerRoundness() {
        return mCornerRoundness;
    }

    private void updateCornerRoundness() {
        if (mBackgroundRendererControllers == null) {
            return;
        }

        for (BackgroundRendererController controller : mBackgroundRendererControllers) {
            controller.setCornerRoundness(mCornerRoundness.value);
        }
    }

    @VisibleForTesting
    TaskbarActivityContext getTaskbarActivityContext() {
        // Used to mock
        return taskbarActivityContext;
    }

    public interface LoggableTaskbarController {
        void dumpLogs(String prefix, PrintWriter pw);
    }

    protected interface BackgroundRendererController {
        /**
         * Sets the roundness of the round corner above Taskbar.
         * @param cornerRoundness 0 has no round corner, 1 has complete round corner.
         */
        void setCornerRoundness(float cornerRoundness);
    }
}
