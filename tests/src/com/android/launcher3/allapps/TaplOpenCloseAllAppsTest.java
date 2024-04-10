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
package com.android.launcher3.allapps;

import static com.android.launcher3.util.TestUtil.expectFail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;
import android.platform.test.annotations.PlatinumTest;

import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.launcher3.LauncherState;
import com.android.launcher3.tapl.AllApps;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.ui.PortraitLandscapeRunner.PortraitLandscape;
import com.android.launcher3.util.rule.ScreenRecordRule;

import org.junit.Test;

/**
 * Test that we can open and close the all apps in multiple situations.
 * The test runs in Out of process (Oop) and in process.
 */
public class TaplOpenCloseAllAppsTest extends AbstractLauncherUiTest {

    public static final String READ_DEVICE_CONFIG_PERMISSION =
            "android.permission.READ_DEVICE_CONFIG";

    /**
     * Make sure we can go home after pressing the context menu on an Icon on the AllApps.
     */
    @Test
    public void testPressHomeOnAllAppsContextMenu() {
        final AllApps allApps = mLauncher.getWorkspace().switchToAllApps();
        allApps.freeze();
        try {
            allApps.getAppIcon("TestActivity7").openMenu();
        } finally {
            allApps.unfreeze();
        }
        mLauncher.goHome();
    }

    /**
     * Make sure we can open AllApps from the Workspace.
     */
    @Test
    @PortraitLandscape
    public void testWorkspaceSwitchToAllApps() {
        assertNotNull("switchToAllApps() returned null",
                mLauncher.getWorkspace().switchToAllApps());
        assertTrue("Launcher internal state is not All Apps",
                isInState(() -> LauncherState.ALL_APPS));
    }

    /**
     * Make sure we can go to Workspace from AllApps
     */
    @Test
    @PortraitLandscape
    public void testAllAppsSwitchToWorkspace() {
        assertNotNull("switchToWorkspace() returned null",
                mLauncher.getWorkspace().switchToAllApps()
                        .switchToWorkspace(/* swipeDown= */ true));
        assertTrue("Launcher internal state is not Workspace",
                isInState(() -> LauncherState.NORMAL));
    }

    /**
     * Make sure the swipe up gesture can take us back to the workspace from AllApps
     */
    @PlatinumTest(focusArea = "launcher")
    @Test
    @PortraitLandscape
    public void testAllAppsSwipeUpToWorkspace() {
        assertNotNull("testAllAppsSwipeUpToWorkspace() returned null",
                mLauncher.getWorkspace().switchToAllApps()
                        .switchToWorkspace(/* swipeDown= */ false));
        assertTrue("Launcher internal state is not Workspace",
                isInState(() -> LauncherState.NORMAL));
    }

    /**
     * Make sure we can go to the Workspace from AllApps on tablets by tapping on the background.
     */
    @Test
    @PortraitLandscape
    public void testAllAppsDeadzoneForTablet() {
        assumeTrue(mLauncher.isTablet());

        mLauncher.getWorkspace().switchToAllApps().dismissByTappingOutsideForTablet(
                true /* tapRight */);
        mLauncher.getWorkspace().switchToAllApps().dismissByTappingOutsideForTablet(
                false /* tapRight */);
    }

    /**
     * Make sure that AllApps closes when pressing the home button
     */
    @Test
    @PortraitLandscape
    @PlatinumTest(focusArea = "launcher")
    @ScreenRecordRule.ScreenRecord // b/322228038
    public void testAllAppsFromHome() {
        // Test opening all apps
        assertNotNull("switchToAllApps() returned null",
                mLauncher.getWorkspace().switchToAllApps());

        runAllAppsTest(mLauncher.getAllApps());

        // Testing pressHome.
        assertTrue("Launcher internal state is not All Apps",
                isInState(() -> LauncherState.ALL_APPS));
        assertNotNull("pressHome returned null", mLauncher.goHome());
        assertTrue("Launcher internal state is not Home",
                isInState(() -> LauncherState.NORMAL));
        assertNotNull("getHome returned null", mLauncher.getWorkspace());
    }

    /**
     * Makes sure the state of AllApps is correct.
     */
    public void runAllAppsTest(AllApps allApps) {
        allApps.freeze();
        try {
            assertNotNull("allApps parameter is null", allApps);

            assertTrue(
                    "Launcher internal state is not All Apps",
                    isInState(() -> LauncherState.ALL_APPS));

            // Test flinging forward and backward.
            executeOnLauncher(launcher -> assertEquals(
                    "All Apps started in already scrolled state", 0,
                    getAllAppsScroll(launcher)));

            allApps.flingForward();
            assertTrue("Launcher internal state is not All Apps",
                    isInState(() -> LauncherState.ALL_APPS));
            final Integer flingForwardY = getFromLauncher(
                    launcher -> getAllAppsScroll(launcher));
            executeOnLauncher(
                    launcher -> assertTrue("flingForward() didn't scroll App Apps",
                            flingForwardY > 0));

            allApps.flingBackward();
            assertTrue(
                    "Launcher internal state is not All Apps",
                    isInState(() -> LauncherState.ALL_APPS));
            final Integer flingBackwardY = getFromLauncher(
                    launcher -> getAllAppsScroll(launcher));
            executeOnLauncher(launcher -> assertTrue("flingBackward() didn't scroll App Apps",
                    flingBackwardY < flingForwardY));

            // Test scrolling down to the end of the app list.
            assertNotNull("All apps: can't find YouTube", allApps.getAppIcon("ZZZ"));
            // Test scrolling up to the beginning oof the app list.
            assertNotNull("All apps: can't find Camera", allApps.getAppIcon("AAA"));
            // Test failing to find a non-existing app.
            final AllApps allAppsFinal = allApps;
            expectFail("All apps: could find a non-existing app",
                    () -> allAppsFinal.getAppIcon("NO APP"));

            assertTrue(
                    "Launcher internal state is not All Apps",
                    isInState(() -> LauncherState.ALL_APPS));
        } finally {
            allApps.unfreeze();
        }
    }

    /**
     * Makes sure that when pressing back when AllApps is open we go back to the Home screen.
     */
    @FlakyTest(bugId = 256615483)
    @Test
    @PortraitLandscape
    public void testPressBackFromAllAppsToHome() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                READ_DEVICE_CONFIG_PERMISSION);
        mLauncher
                .getWorkspace()
                .switchToAllApps()
                .pressBackToWorkspace();
        waitForState("Launcher internal state didn't switch to Home", () -> LauncherState.NORMAL);
        startAppFast(resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR));
        mLauncher.getLaunchedAppState().pressBackToWorkspace();
        waitForState("Launcher internal state didn't switch to Home", () -> LauncherState.NORMAL);
    }

    @Test
    public void testDismissAllAppsWithEscKey() {
        mLauncher.goHome().switchToAllApps().dismissByEscKey();
        waitForState("Launcher internal state didn't switch to Home", () -> LauncherState.NORMAL);
    }
}
