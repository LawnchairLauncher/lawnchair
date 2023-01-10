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

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.R;
import com.android.launcher3.appprediction.PredictionRowView;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.taskbar.TaskbarControllers;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext;

import java.util.List;

/**
 * Handles the all apps overlay window initialization, updates, and its data.
 * <p>
 * All apps is in an application overlay window instead of taskbar's navigation bar panel window,
 * because a navigation bar panel is higher than UI components that all apps should be below such as
 * the notification tray.
 * <p>
 * The all apps window is created and destroyed upon opening and closing all apps, respectively.
 * Application data may be bound while the window does not exist, so this controller will store
 * the models for the next all apps session.
 */
public final class TaskbarAllAppsController {

    private TaskbarControllers mControllers;
    private @Nullable TaskbarAllAppsSlideInView mSlideInView;
    private @Nullable TaskbarAllAppsContainerView mAppsView;

    // Application data models.
    private AppInfo[] mApps;
    private int mAppsModelFlags;
    private List<ItemInfo> mPredictedApps;
    private boolean mDisallowGlobalDrag;
    private boolean mDisallowLongClick;

    /** Initialize the controller. */
    public void init(TaskbarControllers controllers, boolean allAppsVisible) {
        if (!FeatureFlags.ENABLE_ALL_APPS_IN_TASKBAR.get()) {
            return;
        }
        mControllers = controllers;

        /*
         * Recreate All Apps if it was open in the previous Taskbar instance (e.g. the configuration
         * changed).
         */
        if (allAppsVisible) {
            show(false);
        }
    }

    /** Updates the current {@link AppInfo} instances. */
    public void setApps(AppInfo[] apps, int flags) {
        if (!FeatureFlags.ENABLE_ALL_APPS_IN_TASKBAR.get()) {
            return;
        }

        mApps = apps;
        mAppsModelFlags = flags;
        if (mAppsView != null) {
            mAppsView.getAppsStore().setApps(mApps, mAppsModelFlags);
        }
    }

    public void setDisallowGlobalDrag(boolean disableDragForOverviewState) {
        mDisallowGlobalDrag = disableDragForOverviewState;
    }

    public void setDisallowLongClick(boolean disallowLongClick) {
        mDisallowLongClick = disallowLongClick;
    }

    /** Updates the current predictions. */
    public void setPredictedApps(List<ItemInfo> predictedApps) {
        if (!FeatureFlags.ENABLE_ALL_APPS_IN_TASKBAR.get()) {
            return;
        }

        mPredictedApps = predictedApps;
        if (mAppsView != null) {
            mAppsView.getFloatingHeaderView()
                    .findFixedRowByType(PredictionRowView.class)
                    .setPredictedApps(mPredictedApps);
        }
    }

    /** Opens the {@link TaskbarAllAppsContainerView} in a new window. */
    public void show() {
        show(true);
    }

    /** Returns {@code true} if All Apps is open. */
    public boolean isOpen() {
        return mSlideInView != null && mSlideInView.isOpen();
    }

    private void show(boolean animate) {
        if (mAppsView != null) {
            return;
        }
        // mControllers and getSharedState should never be null here. Do not handle null-pointer
        // to catch invalid states.
        mControllers.getSharedState().allAppsVisible = true;

        TaskbarOverlayContext overlayContext =
                mControllers.taskbarOverlayController.requestWindow();
        mSlideInView = (TaskbarAllAppsSlideInView) overlayContext.getLayoutInflater().inflate(
                R.layout.taskbar_all_apps, overlayContext.getDragLayer(), false);
        mSlideInView.addOnCloseListener(() -> {
            mControllers.getSharedState().allAppsVisible = false;
            mSlideInView = null;
            mAppsView = null;
        });
        TaskbarAllAppsViewController viewController = new TaskbarAllAppsViewController(
                overlayContext, mSlideInView, mControllers);

        viewController.show(animate);
        mAppsView = overlayContext.getAppsView();
        mAppsView.getAppsStore().setApps(mApps, mAppsModelFlags);
        mAppsView.getFloatingHeaderView()
                .findFixedRowByType(PredictionRowView.class)
                .setPredictedApps(mPredictedApps);
        // 1 alternative that would be more work:
        // Create a shared drag layer between taskbar and taskbarAllApps so that when dragging
        // starts and taskbarAllApps can close, but the drag layer that the view is being dragged in
        // doesn't also close
        overlayContext.getDragController().setDisallowGlobalDrag(mDisallowGlobalDrag);
        overlayContext.getDragController().setDisallowLongClick(mDisallowLongClick);
    }


    @VisibleForTesting
    public int getTaskbarAllAppsTopPadding() {
        // Allow null-pointer since this should only be null if the apps view is not showing.
        return mAppsView.getActiveRecyclerView().getClipBounds().top;
    }
}
