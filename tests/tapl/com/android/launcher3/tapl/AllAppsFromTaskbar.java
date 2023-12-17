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
package com.android.launcher3.tapl;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.testing.shared.TestProtocol;

/**
 * Operations on AllApps opened from the Taskbar.
 */
public class AllAppsFromTaskbar extends AllApps {

    AllAppsFromTaskbar(LauncherInstrumentation launcher) {
        super(launcher);
    }

    @Override
    protected LauncherInstrumentation.ContainerType getContainerType() {
        return LauncherInstrumentation.ContainerType.TASKBAR_ALL_APPS;
    }

    @NonNull
    @Override
    public TaskbarAppIcon getAppIcon(String appName) {
        return (TaskbarAppIcon) super.getAppIcon(appName);
    }

    @NonNull
    @Override
    protected TaskbarAppIcon createAppIcon(UiObject2 icon) {
        return new TaskbarAppIcon(mLauncher, icon);
    }

    @Override
    protected boolean hasSearchBox() {
        return false;
    }

    @Override
    protected int getAppsListRecyclerTopPadding() {
        return mLauncher.getTestInfo(TestProtocol.REQUEST_TASKBAR_ALL_APPS_TOP_PADDING)
                .getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    @Override
    protected int getAllAppsScroll() {
        return mLauncher.getTestInfo(TestProtocol.REQUEST_TASKBAR_APPS_LIST_SCROLL_Y)
                .getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    @NonNull
    @Override
    public TaskbarAllAppsQsb getQsb() {
        return new TaskbarAllAppsQsb(mLauncher, verifyActiveContainer());
    }
}
