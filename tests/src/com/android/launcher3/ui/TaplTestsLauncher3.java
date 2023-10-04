/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.launcher3.ui;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.LauncherState;
import com.android.launcher3.tapl.AllApps;
import com.android.launcher3.tapl.AppIcon;
import com.android.launcher3.tapl.HomeAllApps;
import com.android.launcher3.ui.PortraitLandscapeRunner.PortraitLandscape;
import com.android.launcher3.util.rule.TISBindRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplTestsLauncher3 extends AbstractLauncherUiTest {
    public static final String APP_NAME = "LauncherTestApp";
    public static final String DUMMY_APP_NAME = "Aardwolf";
    public static final String MAPS_APP_NAME = "Maps";
    public static final String STORE_APP_NAME = "Play Store";
    public static final String GMAIL_APP_NAME = "Gmail";

    @Rule
    public TISBindRule mTISBindRule = new TISBindRule();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        initialize(this);
    }

    public static void initialize(AbstractLauncherUiTest test) throws Exception {
        initialize(test, false);
    }

    public static void initialize(
            AbstractLauncherUiTest test, boolean clearWorkspace) throws Exception {
        test.reinitializeLauncherData(clearWorkspace);
        test.mDevice.pressHome();
        test.waitForLauncherCondition("Launcher didn't start", launcher -> launcher != null);
        test.waitForState("Launcher internal state didn't switch to Home",
                () -> LauncherState.NORMAL);
        test.waitForResumed("Launcher internal state is still Background");
        // Check that we switched to home.
        test.mLauncher.getWorkspace();
        AbstractLauncherUiTest.checkDetectedLeaks(test.mLauncher, true);
    }

    // Please don't add negative test cases for methods that fail only after a long wait.
    public static void expectFail(String message, Runnable action) {
        boolean failed = false;
        try {
            action.run();
        } catch (AssertionError e) {
            failed = true;
        }
        assertTrue(message, failed);
    }

    @Test
    public void testDevicePressMenu() throws Exception {
        mDevice.pressMenu();
        mDevice.waitForIdle();
        executeOnLauncher(
                launcher -> assertNotNull("Launcher internal state didn't switch to Showing Menu",
                        launcher.getOptionsPopup()));
        // Check that pressHome works when the menu is shown.
        mLauncher.goHome();
    }

    public static void runIconLaunchFromAllAppsTest(AbstractLauncherUiTest test, AllApps allApps) {
        allApps.freeze();
        try {
            final AppIcon app = allApps.getAppIcon("TestActivity7");
            assertNotNull("AppIcon.launch returned null", app.launch(getAppPackageName()));
            test.executeOnLauncher(launcher -> assertTrue(
                    "Launcher activity is the top activity; expecting another activity to be the "
                            + "top one",
                    test.isInLaunchedApp(launcher)));
        } finally {
            allApps.unfreeze();
        }
    }

    @Test
    @PortraitLandscape
    public void testAppIconLaunchFromAllAppsFromHome() throws Exception {
        final HomeAllApps allApps = mLauncher.getWorkspace().switchToAllApps();
        assertTrue("Launcher internal state is not All Apps",
                isInState(() -> LauncherState.ALL_APPS));

        runIconLaunchFromAllAppsTest(this, allApps);
    }

    @Test
    @PortraitLandscape
    public void testAddDeleteShortcutOnHotseat() {
        mLauncher.getWorkspace()
                .deleteAppIcon(mLauncher.getWorkspace().getHotseatAppIcon(0))
                .switchToAllApps()
                .getAppIcon(APP_NAME)
                .dragToHotseat(0);
        mLauncher.getWorkspace().deleteAppIcon(
                mLauncher.getWorkspace().getHotseatAppIcon(APP_NAME));
    }

    @Test
    public void testGetAppIconName() {
        HomeAllApps allApps = mLauncher.getWorkspace().switchToAllApps();
        allApps.freeze();
        try {
            // getAppIcon() already verifies that the icon is not null and is the correct icon name.
            allApps.getAppIcon(APP_NAME);
        } finally {
            allApps.unfreeze();
        }
    }
}
