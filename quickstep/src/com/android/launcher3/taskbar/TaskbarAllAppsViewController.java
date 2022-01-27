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

import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.data.AppInfo;

/** Handles the {@link TaskbarAllAppsContainerView} initialization and updates. */
public final class TaskbarAllAppsViewController {

    private final TaskbarActivityContext mContext;
    private final TaskbarAllAppsSlideInView mSlideInView;
    private final TaskbarAllAppsContainerView mAppsView;

    public TaskbarAllAppsViewController(
            TaskbarActivityContext context, TaskbarAllAppsSlideInView slideInView) {
        mContext = context;
        mSlideInView = slideInView;
        mAppsView = mSlideInView.getAppsView();
    }

    /** Initialize the controller. */
    public void init(TaskbarControllers controllers) {
        if (!FeatureFlags.ENABLE_ALL_APPS_IN_TASKBAR.get()) {
            return;
        }

        mAppsView.setOnIconLongClickListener(
                controllers.taskbarDragController::startDragOnLongClick);

        // TODO(b/205803230): Remove once entry point button is implemented.
        mContext.getDragLayer().findViewById(R.id.taskbar_view).setOnClickListener(v -> show());
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
            mContext.setTaskbarWindowFullscreen(true);
            mSlideInView.show();
        }
    }
}
