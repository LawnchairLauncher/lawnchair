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
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

/**
 * Operations on home screen qsb.
 */
public class HomeQsb {

    private final LauncherInstrumentation mLauncher;
    private static final String ASSISTANT_APP_PACKAGE = "com.google.android.googlequicksearchbox";
    private static final String ASSISTANT_ICON_RES_ID = "mic_icon";


    HomeQsb(LauncherInstrumentation launcher) {
        mLauncher = launcher;
        mLauncher.waitForLauncherObject("search_container_hotseat");
    }

    /**
     * Launch assistant app by tapping mic icon on qsb.
     */
    @NonNull
    public LaunchedAppState launchAssistant() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to click assistant mic icon button");
             LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            UiObject2 assistantIcon = mLauncher.waitForLauncherObject(ASSISTANT_ICON_RES_ID);

            LauncherInstrumentation.log("HomeQsb.launchAssistant before click "
                    + assistantIcon.getVisibleCenter() + " in "
                    + mLauncher.getVisibleBounds(assistantIcon));

            mLauncher.clickLauncherObject(assistantIcon);

            try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer("clicked")) {
                // assert Assistant App Launched
                BySelector selector = By.pkg(ASSISTANT_APP_PACKAGE);
                mLauncher.assertTrue(
                        "assistant app didn't start: (" + selector + ")",
                        mLauncher.getDevice().wait(Until.hasObject(selector),
                                LauncherInstrumentation.WAIT_TIME_MS)
                );
                return new LaunchedAppState(mLauncher);
            }
        }
    }

    /**
     * Show search result page from tapping qsb.
     */
    public SearchResultFromQsb showSearchResult() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to open search result page");
             LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            mLauncher.clickLauncherObject(
                    mLauncher.waitForLauncherObject("search_container_hotseat"));
            try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer(
                    "clicked qsb to open search result page")) {
                return new SearchResultFromQsb(mLauncher);
            }
        }
    }
}
