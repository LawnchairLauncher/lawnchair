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

import java.util.regex.Pattern;

/**
 * Operations on qsb from either Home screen or AllApp screen.
 */
public abstract class Qsb implements SearchInputSource {

    private static final String ASSISTANT_APP_PACKAGE = "com.google.android.googlequicksearchbox";
    private static final String ASSISTANT_ICON_RES_ID = "mic_icon";
    private static final String LENS_ICON_RES_ID = "lens_icon";
    private static final Pattern LENS_APP_RES_PATTERN = Pattern.compile(
            ASSISTANT_APP_PACKAGE + ":id/lens.*");
    protected final LauncherInstrumentation mLauncher;
    private final UiObject2 mContainer;
    private final String mQsbResName;

    protected Qsb(LauncherInstrumentation launcher, UiObject2 container, String qsbResName) {
        mLauncher = launcher;
        mContainer = container;
        mQsbResName = qsbResName;
        waitForQsbObject();
    }

    // Waits for the quick search box.
    private UiObject2 waitForQsbObject() {
        return mLauncher.waitForObjectInContainer(mContainer, mQsbResName);
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

            LauncherInstrumentation.log("Qsb.launchAssistant before click "
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
     * Launches lens app by tapping lens icon on qsb.
     */
    @NonNull
    public LaunchedAppState launchLens() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to click lens icon button");
             LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            UiObject2 lensIcon = mLauncher.waitForLauncherObject(LENS_ICON_RES_ID);

            LauncherInstrumentation.log("Qsb.launchLens before click "
                    + lensIcon.getVisibleCenter() + " in "
                    + mLauncher.getVisibleBounds(lensIcon));

            mLauncher.clickLauncherObject(lensIcon);

            try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer("clicked")) {
                // Package name is not enough to check if the app is launched, because many
                // elements are having googlequicksearchbox as package name. So it checks if the
                // corresponding app resource is displayed
                BySelector selector = By.res(LENS_APP_RES_PATTERN);
                mLauncher.assertTrue(
                        "Lens app didn't start: (" + selector + ")",
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
            mLauncher.clickLauncherObject(waitForQsbObject());
            // wait for the result rendering to complete
            mLauncher.waitForIdle();
            try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer(
                    "clicked qsb to open search result page")) {
                return createSearchResult();
            }
        }
    }

    @Override
    public LauncherInstrumentation getLauncher() {
        return mLauncher;
    }

    @Override
    public SearchResultFromQsb getSearchResultForInput() {
        return createSearchResult();
    }

    protected SearchResultFromQsb createSearchResult() {
        return new SearchResultFromQsb(mLauncher);
    }
}
