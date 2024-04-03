/*
 * Copyright (C) 2023 The Android Open Source Project
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

import androidx.test.uiautomator.UiObject2;

/**
 * Operations on search result page opened from Taskbar qsb.
 */
public class SearchResultFromTaskbarQsb extends SearchResultFromQsb {

    SearchResultFromTaskbarQsb(LauncherInstrumentation launcher) {
        super(launcher);
    }

    @Override
    public TaskbarAppIcon findAppIcon(String appName) {
        return (TaskbarAppIcon) super.findAppIcon(appName);
    }

    @Override
    protected TaskbarAppIcon createAppIcon(UiObject2 icon) {
        return new TaskbarAppIcon(mLauncher, icon);
    }

    @Override
    public TaskbarSearchWebSuggestion findWebSuggestion(String text) {
        return (TaskbarSearchWebSuggestion) super.findWebSuggestion(text);
    }

    @Override
    protected TaskbarSearchWebSuggestion createWebSuggestion(UiObject2 webSuggestion) {
        return new TaskbarSearchWebSuggestion(mLauncher, webSuggestion);
    }

    @Override
    protected void verifyVisibleContainerOnDismiss() {
        mLauncher.getLaunchedAppState().assertTaskbarVisible();
    }
}
