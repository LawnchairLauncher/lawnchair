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

import java.util.ArrayList;

/**
 * Operations on search result page opened from home screen qsb.
 */
public class SearchResultFromQsb {
    // The input resource id in the search box.
    private static final String INPUT_RES = "input";
    private static final String BOTTOM_SHEET_RES_ID = "bottom_sheet_background";

    // This particular ID change should happen with caution
    private static final String SEARCH_CONTAINER_RES_ID = "search_results_list_view";
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

    /** Find the web suggestion from search suggestion's title text */
    public void verifyWebSuggestIsPresent(String text) {
        ArrayList<UiObject2> goldenGateResults =
                new ArrayList<>(mLauncher.waitForObjectsInContainer(
                        mLauncher.waitForSystemLauncherObject(SEARCH_CONTAINER_RES_ID),
                        By.clazz(TextView.class)));
        boolean found = false;
        for(UiObject2 uiObject: goldenGateResults) {
            String currentString = uiObject.getText();
            if (currentString.equals(text)) {
                found = true;
            }
        }
        if (!found) {
            throw new IllegalStateException("Web suggestion title: " + text + " not found");
        }
    }

    /** Find the total amount of views being displayed and return the size */
    public int getSearchResultItemSize() {
        ArrayList<UiObject2> searchResultItems =
                new ArrayList<>(mLauncher.waitForObjectsInContainer(
                        mLauncher.waitForSystemLauncherObject(SEARCH_CONTAINER_RES_ID),
                        By.clazz(TextView.class)));
        return searchResultItems.size();
    }

    /**
     * Taps outside bottom sheet to dismiss and return to workspace. Available on tablets only.
     * @param tapRight Tap on the right of bottom sheet if true, or left otherwise.
     */
    public Workspace dismissByTappingOutsideForTablet(boolean tapRight) {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to tap outside AllApps bottom sheet on the "
                             + (tapRight ? "right" : "left"))) {
            final UiObject2 allAppsBottomSheet =
                    mLauncher.waitForLauncherObject(BOTTOM_SHEET_RES_ID);
            mLauncher.touchOutsideContainer(allAppsBottomSheet, tapRight);
            try (LauncherInstrumentation.Closable tapped = mLauncher.addContextLayer(
                    "tapped outside AllApps bottom sheet")) {
                return mLauncher.getWorkspace();
            }
        }
    }
}
