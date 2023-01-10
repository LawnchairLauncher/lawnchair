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
package com.android.launcher3.taskbar.overlay;

import android.content.Context;
import android.view.View;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.taskbar.BaseTaskbarContext;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarControllers;
import com.android.launcher3.taskbar.TaskbarDragController;
import com.android.launcher3.taskbar.TaskbarStashController;
import com.android.launcher3.taskbar.allapps.TaskbarAllAppsContainerView;
import com.android.launcher3.util.OnboardingPrefs;

/**
 * Window context for the taskbar overlays such as All Apps and EDU.
 * <p>
 * Overlays have their own window and need a window context. Some properties are delegated to the
 * {@link TaskbarActivityContext} such as {@link PopupDataProvider}.
 */
public class TaskbarOverlayContext extends BaseTaskbarContext {
    private final TaskbarActivityContext mTaskbarContext;
    private final OnboardingPrefs<TaskbarOverlayContext> mOnboardingPrefs;

    private final TaskbarOverlayController mOverlayController;
    private final TaskbarDragController mDragController;
    private final TaskbarOverlayDragLayer mDragLayer;

    // We automatically stash taskbar when All Apps is opened in gesture navigation mode.
    private final boolean mWillTaskbarBeVisuallyStashed;
    private final int mStashedTaskbarHeight;

    public TaskbarOverlayContext(
            Context windowContext,
            TaskbarActivityContext taskbarContext,
            TaskbarControllers controllers) {
        super(windowContext);
        mTaskbarContext = taskbarContext;
        mOverlayController = controllers.taskbarOverlayController;
        mDragController = new TaskbarDragController(this);
        mDragController.init(controllers);
        mOnboardingPrefs = new OnboardingPrefs<>(this, LauncherPrefs.getPrefs(this));
        mDragLayer = new TaskbarOverlayDragLayer(this);

        TaskbarStashController taskbarStashController = controllers.taskbarStashController;
        mWillTaskbarBeVisuallyStashed = taskbarStashController.supportsVisualStashing();
        mStashedTaskbarHeight = taskbarStashController.getStashedHeight();
    }

    boolean willTaskbarBeVisuallyStashed() {
        return mWillTaskbarBeVisuallyStashed;
    }

    int getStashedTaskbarHeight() {
        return mStashedTaskbarHeight;
    }

    public TaskbarOverlayController getOverlayController() {
        return mOverlayController;
    }

    @Override
    public DeviceProfile getDeviceProfile() {
        return mOverlayController.getLauncherDeviceProfile();
    }

    @Override
    public TaskbarDragController getDragController() {
        return mDragController;
    }

    @Override
    public TaskbarOverlayDragLayer getDragLayer() {
        return mDragLayer;
    }

    @Override
    public TaskbarAllAppsContainerView getAppsView() {
        return mDragLayer.findViewById(R.id.apps_view);
    }

    @Override
    public OnboardingPrefs<TaskbarOverlayContext> getOnboardingPrefs() {
        return mOnboardingPrefs;
    }

    @Override
    public boolean isBindingItems() {
        return mTaskbarContext.isBindingItems();
    }

    @Override
    public View.OnClickListener getItemOnClickListener() {
        return mTaskbarContext.getItemOnClickListener();
    }

    @Override
    public PopupDataProvider getPopupDataProvider() {
        return mTaskbarContext.getPopupDataProvider();
    }

    @Override
    public DotInfo getDotInfoForItem(ItemInfo info) {
        return mTaskbarContext.getDotInfoForItem(info);
    }

    @Override
    public void onDragStart() {}

    @Override
    public void onDragEnd() {
        mOverlayController.maybeCloseWindow();
    }

    @Override
    public void onPopupVisibilityChanged(boolean isVisible) {}
}
