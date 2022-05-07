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

import android.widget.TextView;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;

/**
 * Operations on search result page opened from home screen qsb.
 */
public class SearchResultFromQsb {
    // The input resource id in the search box.
    private static final String INPUT_RES = "input";
    private final LauncherInstrumentation mLauncher;

    SearchResultFromQsb(LauncherInstrumentation launcher) {
        mLauncher = launcher;
        mLauncher.waitForLauncherObject("search_container_all_apps");
    }

    /** Set the input to the search input edit text and update search results. */
    public void searchForInput(String input) {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to search for result with an input");
             LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            mLauncher.waitForLauncherObject(INPUT_RES).setText(input);
        }
    }

    /** Find the app from search results with app name. */
    public Launchable findAppIcon(String appName) {
        UiObject2 icon = mLauncher.waitForLauncherObject(By.clazz(TextView.class).text(appName));
        return new AllAppsAppIcon(mLauncher, icon);
    }
}
