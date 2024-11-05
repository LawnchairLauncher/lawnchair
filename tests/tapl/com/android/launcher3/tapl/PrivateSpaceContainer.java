/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.graphics.Point;

import androidx.test.uiautomator.UiObject2;

/**
 * View containing Private Space elements.
 */
public class PrivateSpaceContainer {
    private static final String PS_HEADER_RES_ID = "ps_header_layout";
    private static final String INSTALL_APP_TITLE = "Install";
    private static final String DIVIDER_RES_ID = "private_space_divider";

    private final LauncherInstrumentation mLauncher;
    private final UiObject2 mAppListRecycler;
    private final AllApps mAppList;
    private final boolean mPrivateSpaceEnabled;

    PrivateSpaceContainer(LauncherInstrumentation launcherInstrumentation,
            UiObject2 appListRecycler, AllApps appList, boolean privateSpaceEnabled) {
        mLauncher = launcherInstrumentation;
        mAppListRecycler = appListRecycler;
        mAppList = appList;
        mPrivateSpaceEnabled = privateSpaceEnabled;

        if (mPrivateSpaceEnabled) {
            verifyHeaderIsPresent();
            verifyInstallAppButtonIsPresent();
            verifyDividerIsPresent();
        } else {
            verifyHeaderIsPresent();
            verifyInstallAppButtonIsNotPresent();
            verifyDividerIsNotPresent();
        }
    }

    // Assert PS Header is in view.
    // Assert PS header has the correct elements.
    private void verifyHeaderIsPresent() {
        final UiObject2 psHeader = mLauncher.waitForObjectInContainer(mAppListRecycler,
                PS_HEADER_RES_ID);
        new PrivateSpaceHeader(mLauncher, psHeader, mPrivateSpaceEnabled);
    }


    // Assert Install App Item is present in view.
    private void verifyInstallAppButtonIsPresent() {
        mAppList.getAppIcon(INSTALL_APP_TITLE);
    }

    // Assert Install App Item is not present in view.
    private void verifyInstallAppButtonIsNotPresent() {
        mLauncher.waitUntilLauncherObjectGone(DIVIDER_RES_ID);
    }

    // Assert Sys App Divider is present in view.
    private void verifyDividerIsPresent() {
        mLauncher.waitForObjectInContainer(mAppListRecycler, DIVIDER_RES_ID);
    }

    // Assert Sys App Divider is not present in view.
    private void verifyDividerIsNotPresent() {
        mLauncher.waitUntilLauncherObjectGone(DIVIDER_RES_ID);
    }

    /**
     * Verifies that a user installed app is present above the divider.
     */
    public void verifyInstalledAppIsPresent(String appName) {
        AppIcon appIcon = mAppList.getAppIcon(appName);
        final Point iconCenter = appIcon.mObject.getVisibleCenter();
        UiObject2 divider = mLauncher.waitForObjectInContainer(mAppListRecycler, DIVIDER_RES_ID);
        final Point dividerCenter = divider.getVisibleCenter();
        mLauncher.assertTrue("Installed App: " + appName + " is not above the divider",
                iconCenter.y < dividerCenter.y);
    }
}