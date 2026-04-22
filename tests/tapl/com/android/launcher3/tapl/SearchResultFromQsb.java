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

import static com.android.launcher3.testing.shared.TestProtocol.NORMAL_STATE_ORDINAL;

import android.graphics.Rect;
import android.widget.TextView;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject2;

import java.util.ArrayList;

/**
 * Operations on search result page opened from qsb.
 */
public class SearchResultFromQsb implements SearchInputSource {
    private static final String BOTTOM_SHEET_RES_ID = "bottom_sheet_background";

    // This particular ID change should happen with caution
    private static final String SEARCH_CONTAINER_RES_ID = "search_results_list_view";
    protected final LauncherInstrumentation mLauncher;
    private final UiObject2 mSearchContainer;

    SearchResultFromQsb(LauncherInstrumentation launcher) {
        mLauncher = launcher;
        mLauncher.waitForLauncherObject("search_container_all_apps");
        mSearchContainer = mLauncher.waitForSystemLauncherObject(SEARCH_CONTAINER_RES_ID);
    }

    /** Find the app from search results with app name. */
    public AppIcon findAppIcon(String appName) {
        UiObject2 icon = mLauncher.waitForLauncherObject(AppIcon.getAppIconSelector(appName));
        return createAppIcon(icon);
    }

    protected AppIcon createAppIcon(UiObject2 icon) {
        return new AllAppsAppIcon(mLauncher, icon);
    }

    /** Find the web suggestion from search suggestion's title text */
    public SearchWebSuggestion findWebSuggestion(String text) {
        UiObject2 webSuggestion = mLauncher.waitForObjectInContainer(mSearchContainer,
                getSearchResultSelector(text));
        return createWebSuggestion(webSuggestion);
    }

    protected SearchWebSuggestion createWebSuggestion(UiObject2 webSuggestion) {
        return new SearchWebSuggestion(mLauncher, webSuggestion);
    }

    /** Find the total amount of views being displayed and return the size */
    public int getSearchResultItemSize() {
        ArrayList<UiObject2> searchResultItems =
                new ArrayList<>(mLauncher.waitForObjectsInContainer(mSearchContainer,
                        By.clazz(TextView.class)));
        return searchResultItems.size();
    }

    /**
     * Scroll down to make next page search results rendered.
     */
    public void getNextPageSearchResults() {
        final int searchContainerHeight = mLauncher.getVisibleBounds(mSearchContainer).height();
        // Start scrolling from center of the screen to top of the screen.
        mLauncher.scroll(mSearchContainer,
                Direction.DOWN,
                new Rect(0, 0, 0, searchContainerHeight / 2),
                /* steps= */ 10,
                /*slowDown= */ false);
    }

    /**
     * Taps outside bottom sheet to dismiss and return to workspace. Available on tablets only.
     * @param tapRight Tap on the right of bottom sheet if true, or left otherwise.
     */
    public void dismissByTappingOutsideForTablet(boolean tapRight) {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to tap outside AllApps bottom sheet on the "
                             + (tapRight ? "right" : "left"))) {
            final UiObject2 allAppsBottomSheet =
                    mLauncher.waitForLauncherObject(BOTTOM_SHEET_RES_ID);
            tapOutside(tapRight, allAppsBottomSheet);
            try (LauncherInstrumentation.Closable tapped = mLauncher.addContextLayer(
                    "tapped outside AllApps bottom sheet")) {
                verifyVisibleContainerOnDismiss();
            }
        }
    }

    protected void tapOutside(boolean tapRight, UiObject2 allAppsBottomSheet) {
        mLauncher.runToState(
                () -> mLauncher.touchOutsideContainer(allAppsBottomSheet, tapRight),
                NORMAL_STATE_ORDINAL,
                "tappig outside");
    }

    protected void verifyVisibleContainerOnDismiss() {
        mLauncher.getWorkspace();
    }

    @Override
    public LauncherInstrumentation getLauncher() {
        return mLauncher;
    }

    @Override
    public SearchResultFromQsb getSearchResultForInput() {
        return this;
    }

    /** Verify a tile is present by checking its title and subtitle. */
    public void verifyTileIsPresent(String title, String subtitle) {
        mLauncher.waitForObjectInContainer(mSearchContainer,
                getSearchResultSelector(title));
        mLauncher.waitForObjectInContainer(mSearchContainer,
                getSearchResultSelector(subtitle));
    }

    private BySelector getSearchResultSelector(String text) {
        return By.clazz(TextView.class).text(text);
    }
}
