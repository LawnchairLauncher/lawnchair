/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.taskbar.allapps;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_STASHED_IN_APP_ALL_APPS;
import static com.android.launcher3.util.OnboardingPrefs.ALL_APPS_VISITED_COUNT;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.appprediction.AppsDividerView;
import com.android.launcher3.appprediction.PredictionRowView;
import com.android.launcher3.taskbar.NavbarButtonsViewController;
import com.android.launcher3.taskbar.TaskbarControllers;
import com.android.launcher3.taskbar.TaskbarStashController;

/**
 * Handles the {@link TaskbarAllAppsContainerView} behavior and synchronizes its transitions with
 * taskbar stashing.
 */
final class TaskbarAllAppsViewController {

    private final TaskbarAllAppsContext mContext;
    private final TaskbarAllAppsSlideInView mSlideInView;
    private final TaskbarAllAppsContainerView mAppsView;
    private final TaskbarStashController mTaskbarStashController;
    private final NavbarButtonsViewController mNavbarButtonsViewController;

    TaskbarAllAppsViewController(
            TaskbarAllAppsContext context,
            TaskbarAllAppsSlideInView slideInView,
            TaskbarAllAppsController windowController,
            TaskbarControllers taskbarControllers) {

        mContext = context;
        mSlideInView = slideInView;
        mAppsView = mSlideInView.getAppsView();
        mTaskbarStashController = taskbarControllers.taskbarStashController;
        mNavbarButtonsViewController = taskbarControllers.navbarButtonsViewController;

        setUpIconLongClick();
        setUpAppDivider();
        setUpTaskbarStashing();
        mSlideInView.addOnCloseListener(windowController::maybeCloseWindow);
    }

    /** Starts the {@link TaskbarAllAppsSlideInView} enter transition. */
    void show(boolean animate) {
        mSlideInView.show(animate);
    }

    /** Closes the {@link TaskbarAllAppsSlideInView}. */
    void close(boolean animate) {
        mSlideInView.close(animate);
    }

    private void setUpIconLongClick() {
        mAppsView.setOnIconLongClickListener(
                mContext.getDragController()::startDragOnLongClick);
        mAppsView.getFloatingHeaderView()
                .findFixedRowByType(PredictionRowView.class)
                .setOnIconLongClickListener(
                        mContext.getDragController()::startDragOnLongClick);
    }

    private void setUpAppDivider() {
        mAppsView.getFloatingHeaderView()
                .findFixedRowByType(AppsDividerView.class)
                .setShowAllAppsLabel(!mContext.getOnboardingPrefs().hasReachedMaxCount(
                        ALL_APPS_VISITED_COUNT));
        mContext.getOnboardingPrefs().incrementEventCount(ALL_APPS_VISITED_COUNT);
    }

    private void setUpTaskbarStashing() {
        mTaskbarStashController.updateStateForFlag(FLAG_STASHED_IN_APP_ALL_APPS, true);
        mTaskbarStashController.applyState(
                ALL_APPS.getTransitionDuration(mContext, true /* isToState */));
        mNavbarButtonsViewController.setSlideInViewVisible(true);
        mSlideInView.setOnCloseBeginListener(() -> {
            mNavbarButtonsViewController.setSlideInViewVisible(false);
            AbstractFloatingView.closeOpenContainer(
                    mContext, AbstractFloatingView.TYPE_ACTION_POPUP);
            // Post in case view is closing due to gesture navigation. If a gesture is in progress,
            // wait to unstash until after the gesture is finished.
            mSlideInView.post(mTaskbarStashController::maybeResetStashedInAppAllApps);
        });
    }
}
