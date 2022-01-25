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
package com.android.launcher3.taskbar;

import android.view.View;

import com.android.launcher3.DropTarget;
import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.model.data.AppInfo;

/** Handles the {@link TaskbarAllAppsContainerView} initialization and updates. */
public final class TaskbarAllAppsViewController implements DragController.DragListener {

    private final TaskbarActivityContext mContext;
    private final TaskbarAllAppsContainerView mAppsView;

    private TaskbarControllers mControllers; // Initialized in init.
    private boolean mIsOpen;

    public TaskbarAllAppsViewController(
            TaskbarActivityContext context, TaskbarAllAppsContainerView appsView) {
        mContext = context;
        mAppsView = appsView;
    }

    /** Initialize the controller. */
    public void init(TaskbarControllers controllers) {
        if (!FeatureFlags.ENABLE_ALL_APPS_IN_TASKBAR.get()) {
            return;
        }
        mControllers = controllers;

        mAppsView.setOnIconLongClickListener(icon -> {
            mControllers.taskbarDragController.addDragListener(this);
            mControllers.taskbarDragController.startDragOnLongClick(icon);
            return true;
        });

        // TODO(b/205803230): Remove once entry point button is implemented.
        mContext.getDragLayer().findViewById(R.id.taskbar_view).setOnClickListener(v -> {
            if (mIsOpen) {
                hide();
            } else {
                show();
            }
        });
    }

    /** The taskbar apps view. */
    public TaskbarAllAppsContainerView getAppsView() {
        return mAppsView;
    }

    /** Binds the current {@link AppInfo} instances to the {@link TaskbarAllAppsContainerView}. */
    public void setApps(AppInfo[] apps, int flags) {
        if (FeatureFlags.ENABLE_ALL_APPS_IN_TASKBAR.get()) {
            mAppsView.getAppsStore().setApps(apps, flags);
        }
    }

    /** Opens the {@link TaskbarAllAppsContainerView}. */
    public void show() {
        if (FeatureFlags.ENABLE_ALL_APPS_IN_TASKBAR.get()) {
            mIsOpen = true;
            mContext.setTaskbarWindowFullscreen(true);
            mAppsView.setVisibility(View.VISIBLE);
        }
    }

    /** Hides the {@link TaskbarAllAppsContainerView}. */
    public void hide() {
        if (FeatureFlags.ENABLE_ALL_APPS_IN_TASKBAR.get()) {
            mIsOpen = false;
            mContext.setTaskbarWindowFullscreen(false);
            mAppsView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        mControllers.taskbarDragController.removeDragListener(this);
        mIsOpen = false;
        mAppsView.setVisibility(View.GONE);
    }

    @Override
    public void onDragEnd() { }
}
