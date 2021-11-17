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

import androidx.annotation.NonNull;

import com.android.systemui.shared.rotation.RotationButtonController;

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

    /** Do not store this controller, as it may change at runtime. */
    @NonNull public TaskbarUIController uiController = TaskbarUIController.DEFAULT;

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
            TaskbarPopupController taskbarPopupController) {
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
    }

    /**
     * Initializes all controllers. Note that controllers can now reference each other through this
     * TaskbarControllers instance, but should be careful to only access things that were created
     * in constructors for now, as some controllers may still be waiting for init().
     */
    public void init(TaskbarSharedState sharedState) {
        taskbarDragController.init(this);
        navbarButtonsViewController.init(this);
        rotationButtonController.init();
        taskbarDragLayerController.init(this);
        taskbarViewController.init(this);
        taskbarScrimViewController.init(this);
        taskbarUnfoldAnimationController.init(this);
        taskbarKeyguardController.init(navbarButtonsViewController);
        stashedHandleViewController.init(this);
        taskbarStashController.init(this, sharedState);
        taskbarEduController.init(this);
        taskbarPopupController.init(this);
    }

    /**
     * Cleans up all controllers.
     */
    public void onDestroy() {
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
    }
}
