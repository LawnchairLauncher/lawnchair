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

import static com.android.launcher3.model.data.AppInfo.EMPTY_ARRAY;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.R;
import com.android.launcher3.appprediction.PredictionRowView;
import com.android.launcher3.dragndrop.DragOptions.PreDragCondition;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.taskbar.TaskbarControllers;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext;
import com.android.launcher3.util.PackageUserKey;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
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
    private @Nullable TaskbarOverlayContext mOverlayContext;
    private @Nullable TaskbarAllAppsSlideInView mSlideInView;
    private @Nullable TaskbarAllAppsContainerView mAppsView;
    private @Nullable TaskbarSearchSessionController mSearchSessionController;

    // Application data models.
    private @NonNull AppInfo[] mApps = EMPTY_ARRAY;
    private int mAppsModelFlags;
    private @NonNull List<ItemInfo> mPredictedApps = Collections.emptyList();
    private @Nullable List<ItemInfo> mZeroStateSearchSuggestions;
    private boolean mDisallowGlobalDrag;
    private boolean mDisallowLongClick;

    private Map<PackageUserKey, Integer> mPackageUserKeytoUidMap = Collections.emptyMap();

    /** Initialize the controller. */
    public void init(TaskbarControllers controllers, boolean allAppsVisible) {
        mControllers = controllers;

        /*
         * Recreate All Apps if it was open in the previous Taskbar instance (e.g. the configuration
         * changed).
         */
        if (allAppsVisible) {
            show(false);
        }
    }

    /** Clean up the controller. */
    public void onDestroy() {
        cleanUpOverlay();
    }

    /** Updates the current {@link AppInfo} instances. */
    public void setApps(@Nullable AppInfo[] apps, int flags, Map<PackageUserKey, Integer> map) {
        mApps = apps == null ? EMPTY_ARRAY : apps;
        mAppsModelFlags = flags;
        mPackageUserKeytoUidMap = map;
        if (mAppsView != null) {
            mAppsView.getAppsStore().setApps(mApps, mAppsModelFlags, mPackageUserKeytoUidMap);
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
        mPredictedApps = predictedApps;
        if (mAppsView != null) {
            mAppsView.getFloatingHeaderView()
                    .findFixedRowByType(PredictionRowView.class)
                    .setPredictedApps(mPredictedApps);
        }
        if (mSearchSessionController != null) {
            mSearchSessionController.setZeroStatePredictedItems(predictedApps);
        }
    }

    /** Updates the current search suggestions. */
    public void setZeroStateSearchSuggestions(List<ItemInfo> zeroStateSearchSuggestions) {
        mZeroStateSearchSuggestions = zeroStateSearchSuggestions;
    }

    /** Updates the current notification dots. */
    public void updateNotificationDots(Predicate<PackageUserKey> updatedDots) {
        if (mAppsView != null) {
            mAppsView.getAppsStore().updateNotificationDots(updatedDots);
        }
    }

    /** Toggles visibility of {@link TaskbarAllAppsContainerView} in the overlay window. */
    public void toggle() {
        if (isOpen()) {
            mSlideInView.close(true);
        } else {
            show(true);
        }
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

        mOverlayContext = mControllers.taskbarOverlayController.requestWindow();

        // Initialize search session for All Apps.
        mSearchSessionController = TaskbarSearchSessionController.newInstance(mOverlayContext);
        mOverlayContext.setSearchSessionController(mSearchSessionController);
        mSearchSessionController.setZeroStatePredictedItems(mPredictedApps);
        if (mZeroStateSearchSuggestions != null) {
            mSearchSessionController.setZeroStateSearchSuggestions(mZeroStateSearchSuggestions);
        }
        mSearchSessionController.startLifecycle();

        mSlideInView = (TaskbarAllAppsSlideInView) mOverlayContext.getLayoutInflater().inflate(
                R.layout.taskbar_all_apps_sheet, mOverlayContext.getDragLayer(), false);
        mSlideInView.addOnCloseListener(() -> {
            mControllers.getSharedState().allAppsVisible = false;
            cleanUpOverlay();
        });
        TaskbarAllAppsViewController viewController = new TaskbarAllAppsViewController(
                mOverlayContext, mSlideInView, mControllers, mSearchSessionController);

        viewController.show(animate);
        mAppsView = mOverlayContext.getAppsView();
        mAppsView.getAppsStore().setApps(mApps, mAppsModelFlags, mPackageUserKeytoUidMap);
        mAppsView.getFloatingHeaderView()
                .findFixedRowByType(PredictionRowView.class)
                .setPredictedApps(mPredictedApps);
        // 1 alternative that would be more work:
        // Create a shared drag layer between taskbar and taskbarAllApps so that when dragging
        // starts and taskbarAllApps can close, but the drag layer that the view is being dragged in
        // doesn't also close
        mOverlayContext.getDragController().setDisallowGlobalDrag(mDisallowGlobalDrag);
        mOverlayContext.getDragController().setDisallowLongClick(mDisallowLongClick);
    }

    private void cleanUpOverlay() {
        // Floating search bar is added to the drag layer in ActivityAllAppsContainerView onAttach;
        // removed here as this is a special case that we remove the all apps panel.
        if (mAppsView != null && mOverlayContext != null
                && mAppsView.getSearchUiDelegate().isSearchBarFloating()) {
            mOverlayContext.getDragLayer().removeView(mAppsView.getSearchView());
            mAppsView.getSearchUiDelegate().onDestroySearchBar();
        }
        if (mSearchSessionController != null) {
            mSearchSessionController.onDestroy();
            mSearchSessionController = null;
        }
        if (mOverlayContext != null) {
            mOverlayContext.setSearchSessionController(null);
            mOverlayContext = null;
        }
        mSlideInView = null;
        mAppsView = null;
    }

    @VisibleForTesting
    public int getTaskbarAllAppsTopPadding() {
        // Allow null-pointer since this should only be null if the apps view is not showing.
        return mAppsView.getActiveRecyclerView().getClipBounds().top;
    }

    @VisibleForTesting
    public int getTaskbarAllAppsScroll() {
        // Allow null-pointer since this should only be null if the apps view is not showing.
        return mAppsView.getActiveRecyclerView().computeVerticalScrollOffset();
    }

    /** @see TaskbarSearchSessionController#createPreDragConditionForSearch(View) */
    @Nullable
    public PreDragCondition createPreDragConditionForSearch(View view) {
        return mSearchSessionController != null
                ? mSearchSessionController.createPreDragConditionForSearch(view)
                : null;
    }
}
