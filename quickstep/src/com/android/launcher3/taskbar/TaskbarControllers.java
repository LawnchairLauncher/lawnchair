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

import android.content.pm.ActivityInfo.Config;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.taskbar.allapps.TaskbarAllAppsController;
import com.android.systemui.shared.rotation.RotationButtonController;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

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
    public final TaskbarEduController taskbarEduController;
    public final TaskbarAutohideSuspendController taskbarAutohideSuspendController;
    public final TaskbarPopupController taskbarPopupController;
    public final TaskbarForceVisibleImmersiveController taskbarForceVisibleImmersiveController;
    public final TaskbarAllAppsController taskbarAllAppsController;
    public final TaskbarInsetsController taskbarInsetsController;
    public final VoiceInteractionWindowController voiceInteractionWindowController;
    public final TaskbarRecentAppsController taskbarRecentAppsController;

    @Nullable private LoggableTaskbarController[] mControllersToLog = null;

    /** Do not store this controller, as it may change at runtime. */
    @NonNull public TaskbarUIController uiController = TaskbarUIController.DEFAULT;

    private boolean mAreAllControllersInitialized;
    private final List<Runnable> mPostInitCallbacks = new ArrayList<>();

    @Nullable private TaskbarSharedState mSharedState = null;

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
            TaskbarEduController taskbarEduController,
            TaskbarAutohideSuspendController taskbarAutoHideSuspendController,
            TaskbarPopupController taskbarPopupController,
            TaskbarForceVisibleImmersiveController taskbarForceVisibleImmersiveController,
            TaskbarAllAppsController taskbarAllAppsController,
            TaskbarInsetsController taskbarInsetsController,
            VoiceInteractionWindowController voiceInteractionWindowController,
            TaskbarRecentAppsController taskbarRecentAppsController) {
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
        this.taskbarEduController = taskbarEduController;
        this.taskbarAutohideSuspendController = taskbarAutoHideSuspendController;
        this.taskbarPopupController = taskbarPopupController;
        this.taskbarForceVisibleImmersiveController = taskbarForceVisibleImmersiveController;
        this.taskbarAllAppsController = taskbarAllAppsController;
        this.taskbarInsetsController = taskbarInsetsController;
        this.voiceInteractionWindowController = voiceInteractionWindowController;
        this.taskbarRecentAppsController = taskbarRecentAppsController;
    }

    /**
     * Initializes all controllers. Note that controllers can now reference each other through this
     * TaskbarControllers instance, but should be careful to only access things that were created
     * in constructors for now, as some controllers may still be waiting for init().
     */
    public void init(@NonNull TaskbarSharedState sharedState) {
        mAreAllControllersInitialized = false;
        mSharedState = sharedState;

        taskbarDragController.init(this);
        navbarButtonsViewController.init(this);
        rotationButtonController.init();
        taskbarDragLayerController.init(this);
        taskbarViewController.init(this);
        taskbarScrimViewController.init(this);
        taskbarUnfoldAnimationController.init(this);
        taskbarKeyguardController.init(navbarButtonsViewController);
        stashedHandleViewController.init(this);
        taskbarStashController.init(this, sharedState.setupUIVisible);
        taskbarEduController.init(this);
        taskbarPopupController.init(this);
        taskbarForceVisibleImmersiveController.init(this);
        taskbarAllAppsController.init(this, sharedState.allAppsVisible);
        navButtonController.init(this);
        taskbarInsetsController.init(this);
        voiceInteractionWindowController.init(this);
        taskbarRecentAppsController.init(this);

        mControllersToLog = new LoggableTaskbarController[] {
                taskbarDragController, navButtonController, navbarButtonsViewController,
                taskbarDragLayerController, taskbarScrimViewController, taskbarViewController,
                taskbarUnfoldAnimationController, taskbarKeyguardController,
                stashedHandleViewController, taskbarStashController, taskbarEduController,
                taskbarAutohideSuspendController, taskbarPopupController, taskbarInsetsController,
                voiceInteractionWindowController
        };

        mAreAllControllersInitialized = true;
        for (Runnable postInitCallback : mPostInitCallbacks) {
            postInitCallback.run();
        }
        mPostInitCallbacks.clear();
    }

    @Nullable
    public TaskbarSharedState getSharedState() {
        // This should only be null if called before init() and after destroy().
        return mSharedState;
    }

    public void onConfigurationChanged(@Config int configChanges) {
        navbarButtonsViewController.onConfigurationChanged(configChanges);
    }

    /**
     * Cleans up all controllers.
     */
    public void onDestroy() {
        mSharedState = null;

        navbarButtonsViewController.onDestroy();
        uiController.onDestroy();
        rotationButtonController.onDestroy();
        taskbarDragLayerController.onDestroy();
        taskbarKeyguardController.onDestroy();
        taskbarUnfoldAnimationController.onDestroy();
        taskbarViewController.onDestroy();
        stashedHandleViewController.onDestroy();
        taskbarAutohideSuspendController.onDestroy();
        taskbarPopupController.onDestroy();
        taskbarForceVisibleImmersiveController.onDestroy();
        taskbarAllAppsController.onDestroy();
        navButtonController.onDestroy();
        taskbarInsetsController.onDestroy();
        voiceInteractionWindowController.onDestroy();
        taskbarRecentAppsController.onDestroy();

        mControllersToLog = null;
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
    }

    @VisibleForTesting
    TaskbarActivityContext getTaskbarActivityContext() {
        // Used to mock
        return taskbarActivityContext;
    }

    protected interface LoggableTaskbarController {
        void dumpLogs(String prefix, PrintWriter pw);
    }
}
