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

import static java.util.Collections.emptySet;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;

import java.util.Set;

/**
 * Base class for providing recent apps functionality
 */
public class TaskbarRecentAppsController {

    public static final TaskbarRecentAppsController DEFAULT = new TaskbarRecentAppsController();

    // Initialized in init.
    protected TaskbarControllers mControllers;

    @CallSuper
    protected void init(TaskbarControllers taskbarControllers) {
        mControllers = taskbarControllers;
    }

    @CallSuper
    protected void onDestroy() {
        mControllers = null;
    }

    /** Stores the current {@link AppInfo} instances, no-op except in desktop environment. */
    protected void setApps(AppInfo[] apps) {
    }

    /**
     * Indicates whether recent apps functionality is enabled, should return false except in
     * desktop environment.
     */
    protected boolean isEnabled() {
        return false;
    }

    /** Called to update hotseatItems, no-op except in desktop environment. */
    protected ItemInfo[] updateHotseatItemInfos(@NonNull ItemInfo[] hotseatItems) {
        return hotseatItems;
    }

    /** Called to update the list of currently running apps, no-op except in desktop environment. */
    protected void updateRunningApps() {}

    /** Returns the currently running apps, or an empty Set if outside of Desktop environment. */
    public Set<String> getRunningApps() {
        return emptySet();
    }

    /** Returns the set of apps whose tasks are all minimized. */
    public Set<String> getMinimizedApps() {
        return emptySet();
    }
}
