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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import android.content.Intent;
import android.platform.test.annotations.PlatinumTest;

import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.tapl.AllApps;
import com.android.launcher3.tapl.AppIcon;
import com.android.launcher3.tapl.HomeAllApps;
import com.android.launcher3.tapl.HomeAppIcon;
import com.android.launcher3.tapl.Workspace;
import com.android.launcher3.ui.PortraitLandscapeRunner.PortraitLandscape;
import com.android.launcher3.util.LauncherLayoutBuilder;
import com.android.launcher3.util.TestUtil;
import com.android.launcher3.util.rule.TISBindRule;

import org.junit.After;
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
    private static final String READ_DEVICE_CONFIG_PERMISSION =
            "android.permission.READ_DEVICE_CONFIG";

    @Rule
    public TISBindRule mTISBindRule = new TISBindRule();

    private AutoCloseable mLauncherLayout;

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

    @After
    public void tearDown() throws Exception {
        if (mLauncherLayout != null) {
            mLauncherLayout.close();
        }
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

    public static boolean isWorkspaceScrollable(Launcher launcher) {
        return launcher.getWorkspace().getPageCount() > launcher.getWorkspace().getPanelCount();
    }

    private int getCurrentWorkspacePage(Launcher launcher) {
        return launcher.getWorkspace().getCurrentPage();
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

    @PlatinumTest(focusArea = "launcher")
    @Test
    public void testWorkspace() throws Exception {
        // Set workspace  that includes the chrome Activity app icon on the hotseat.
        LauncherLayoutBuilder builder = new LauncherLayoutBuilder()
                .atHotseat(0).putApp("com.android.chrome", "com.google.android.apps.chrome.Main");
        mLauncherLayout = TestUtil.setLauncherDefaultLayout(mTargetContext, builder);
        reinitializeLauncherData();

        final Workspace workspace = mLauncher.getWorkspace();

        // Test that ensureWorkspaceIsScrollable adds a page by dragging an icon there.
        executeOnLauncher(launcher -> assertFalse("Initial workspace state is scrollable",
                isWorkspaceScrollable(launcher)));
        assertEquals("Initial workspace doesn't have the correct page", workspace.pagesPerScreen(),
                workspace.getPageCount());
        workspace.verifyWorkspaceAppIconIsGone("Chrome app was found on empty workspace", "Chrome");
        workspace.ensureWorkspaceIsScrollable();

        executeOnLauncher(
                launcher -> assertEquals(
                        "Ensuring workspace scrollable didn't switch to next screen",
                        workspace.pagesPerScreen(), getCurrentWorkspacePage(launcher)));
        executeOnLauncher(
                launcher -> assertTrue("ensureScrollable didn't make workspace scrollable",
                        isWorkspaceScrollable(launcher)));
        assertNotNull("ensureScrollable didn't add Chrome app",
                workspace.getWorkspaceAppIcon("Chrome"));

        // Test flinging workspace.
        workspace.flingBackward();
        assertTrue("Launcher internal state is not Home", isInState(() -> LauncherState.NORMAL));
        executeOnLauncher(
                launcher -> assertEquals("Flinging back didn't switch workspace to page #0",
                        0, getCurrentWorkspacePage(launcher)));

        workspace.flingForward();
        executeOnLauncher(
                launcher -> assertEquals("Flinging forward didn't switch workspace to next screen",
                        workspace.pagesPerScreen(), getCurrentWorkspacePage(launcher)));
        assertTrue("Launcher internal state is not Home", isInState(() -> LauncherState.NORMAL));

        // Test starting a workspace app.
        final HomeAppIcon app = workspace.getWorkspaceAppIcon("Chrome");
        assertNotNull("No Chrome app in workspace", app);
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

    @FlakyTest(bugId = 256615483)
    @Test
    @PortraitLandscape
    public void testPressBack() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                READ_DEVICE_CONFIG_PERMISSION);
        assumeFalse(FeatureFlags.ENABLE_BACK_SWIPE_LAUNCHER_ANIMATION.get());
        mLauncher.getWorkspace().switchToAllApps();
        mLauncher.pressBack();
        mLauncher.getWorkspace();
        waitForState("Launcher internal state didn't switch to Home", () -> LauncherState.NORMAL);
        startAppFast(resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR));
        mLauncher.pressBack();
        mLauncher.getWorkspace();
        waitForState("Launcher internal state didn't switch to Home", () -> LauncherState.NORMAL);
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

    @PlatinumTest(focusArea = "launcher")
    @Test
    public void testAddAndDeletePageAndFling() {
        Workspace workspace = mLauncher.getWorkspace();
        // Get the first app from the hotseat
        HomeAppIcon hotSeatIcon = workspace.getHotseatAppIcon(0);
        String appName = hotSeatIcon.getIconName();

        // Add one page by dragging app to page 1.
        workspace.dragIcon(hotSeatIcon, workspace.pagesPerScreen());
        assertEquals("Incorrect Page count Number",
                workspace.pagesPerScreen() * 2,
                workspace.getPageCount());

        // Delete one page by dragging app to hot seat.
        workspace.getWorkspaceAppIcon(appName).dragToHotseat(0);

        // Refresh workspace to avoid using stale container error.
        workspace = mLauncher.getWorkspace();
        assertEquals("Incorrect Page count Number",
                workspace.pagesPerScreen(),
                workspace.getPageCount());
    }
}
