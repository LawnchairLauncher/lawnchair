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

import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_NOTIFICATIONS;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_QUICK_SETTINGS;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.launcher3.R;

/**
 * Controller for managing buttons and status icons in taskbar in a desktop environment.
 */
public class DesktopNavbarButtonsViewController extends NavbarButtonsViewController {

    private final TaskbarActivityContext mContext;
    private final FrameLayout mNavButtonsView;
    private final ViewGroup mNavButtonContainer;
    private final ViewGroup mStartContextualContainer;
    private final View mAllAppsButton;

    private TaskbarControllers mControllers;

    public DesktopNavbarButtonsViewController(TaskbarActivityContext context,
            FrameLayout navButtonsView) {
        super(context, navButtonsView);
        mContext = context;
        mNavButtonsView = navButtonsView;
        mNavButtonContainer = mNavButtonsView.findViewById(R.id.end_nav_buttons);
        mStartContextualContainer = mNavButtonsView.findViewById(R.id.start_contextual_buttons);
        mAllAppsButton = LayoutInflater.from(context)
                .inflate(R.layout.taskbar_all_apps_button, mStartContextualContainer, false);
        mAllAppsButton.setOnClickListener((View v) -> {
            mControllers.taskbarAllAppsController.show();
        });
    }

    /**
     * Initializes the controller
     */
    @Override
    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
        mNavButtonsView.getLayoutParams().height = mContext.getDeviceProfile().taskbarSize;

        // Quick settings and notifications buttons
        addButton(R.drawable.ic_sysbar_quick_settings, BUTTON_QUICK_SETTINGS,
                mNavButtonContainer, mControllers.navButtonController,
                R.id.quick_settings_button);
        addButton(R.drawable.ic_sysbar_notifications, BUTTON_NOTIFICATIONS,
                mNavButtonContainer, mControllers.navButtonController,
                R.id.notifications_button);
        // All apps button
        mStartContextualContainer.addView(mAllAppsButton);
    }

    /** Cleans up on destroy */
    @Override
    public void onDestroy() { }
}
