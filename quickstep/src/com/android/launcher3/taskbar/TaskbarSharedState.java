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

import static com.android.launcher3.taskbar.LauncherTaskbarUIController.DISPLAY_PROGRESS_COUNT;

import android.app.PendingIntent;

/**
 * State shared across different taskbar instance
 */
public class TaskbarSharedState {

    // TaskbarManager#onSystemUiFlagsChanged
    public int sysuiStateFlags;

    // TaskbarManager#disableNavBarElements()
    public int disableNavBarDisplayId;
    public int disableNavBarState1;
    public int disableNavBarState2;

    // TaskbarManager#onSystemBarAttributesChanged()
    public int systemBarAttrsDisplayId;
    public int systemBarAttrsBehavior;

    // TaskbarManager#onNavButtonsDarkIntensityChanged()
    public float navButtonsDarkIntensity;

    public boolean setupUIVisible = false;

    public boolean allAppsVisible = false;

    // LauncherTaskbarUIController#mTaskbarInAppDisplayProgressMultiProp
    public float[] inAppDisplayProgressMultiPropValues = new float[DISPLAY_PROGRESS_COUNT];

    // Taskbar System Action
    public PendingIntent taskbarSystemActionPendingIntent;
}
