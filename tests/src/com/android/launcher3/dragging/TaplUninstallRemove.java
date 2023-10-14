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
package com.android.launcher3.dragging;

import static com.android.launcher3.testing.shared.TestProtocol.ICON_MISSING;
import static com.android.launcher3.ui.TaplTestsLauncher3.initialize;
import static com.android.launcher3.util.TestConstants.AppNames.DUMMY_APP_NAME;
import static com.android.launcher3.util.TestConstants.AppNames.GMAIL_APP_NAME;
import static com.android.launcher3.util.TestConstants.AppNames.MAPS_APP_NAME;
import static com.android.launcher3.util.TestConstants.AppNames.STORE_APP_NAME;
import static com.android.launcher3.util.TestConstants.AppNames.TEST_APP_NAME;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Point;
import android.platform.test.annotations.PlatinumTest;
import android.util.Log;

import com.android.launcher3.tapl.HomeAllApps;
import com.android.launcher3.tapl.HomeAppIcon;
import com.android.launcher3.tapl.Workspace;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.ui.PortraitLandscapeRunner.PortraitLandscape;
import com.android.launcher3.util.TestUtil;
import com.android.launcher3.util.Wait;
import com.android.launcher3.util.rule.ScreenRecordRule;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

/**
 * Test runs in Out of process (Oop) and In process (Ipc)
 * Test the behaviour of uninstalling and removing apps both from AllApps, Workspace and Hotseat.
 */
public class TaplUninstallRemove extends AbstractLauncherUiTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        initialize(this);
    }

    /**
     * Deletes app both built-in and user-installed from the Workspace and makes sure it's no longer
     * in the Workspace.
     */
    @Test
    @PortraitLandscape
    public void testDeleteFromWorkspace() {
        for (String appName : new String[]{GMAIL_APP_NAME, STORE_APP_NAME, TEST_APP_NAME}) {
            final HomeAppIcon homeAppIcon = createShortcutInCenterIfNotExist(appName);
            Workspace workspace = mLauncher.getWorkspace().deleteAppIcon(homeAppIcon);
            workspace.verifyWorkspaceAppIconIsGone(
                    appName + " app was found after being deleted from workspace",
                    appName);
        }
    }

    private void verifyAppUninstalledFromAllApps(Workspace workspace, String appName) {
        final HomeAllApps allApps = workspace.switchToAllApps();
        Wait.atMost(appName + " app was found on all apps after being uninstalled",
                () -> allApps.tryGetAppIcon(appName) == null,
                DEFAULT_UI_TIMEOUT, mLauncher);
    }

    private void installDummyAppAndWaitForUIUpdate() throws IOException {
        TestUtil.installDummyApp();
        waitForLauncherUIUpdate();
    }

    private void waitForLauncherUIUpdate() {
        // Wait for model thread completion as it may be processing
        // the install event from the SystemService
        mLauncher.waitForModelQueueCleared();
        // Wait for Launcher UI thread completion, as it may be processing updating the UI in
        // response to the model update. Not that `waitForLauncherInitialized` is just a proxy
        // method, we can use any method which touches Launcher UI thread,
        mLauncher.waitForLauncherInitialized();
    }

    /**
     * Makes sure you can uninstall an app from the Workspace.
     * @throws Exception
     */
    @Test
    @PortraitLandscape
    // TODO(b/293944634): Remove Screenrecord after flaky debug, and add
    // @PlatinumTest(focusArea = "launcher") back
    @ScreenRecordRule.ScreenRecord
    public void testUninstallFromWorkspace() throws Exception {
        installDummyAppAndWaitForUIUpdate();
        try {
            verifyAppUninstalledFromAllApps(
                    createShortcutInCenterIfNotExist(DUMMY_APP_NAME).uninstall(), DUMMY_APP_NAME);
        } finally {
            TestUtil.uninstallDummyApp();
        }
    }

    /**
     * Makes sure you can uninstall an app from AllApps.
     * @throws Exception
     */
    @Test
    @PortraitLandscape
    @PlatinumTest(focusArea = "launcher")
    public void testUninstallFromAllApps() throws Exception {
        installDummyAppAndWaitForUIUpdate();
        try {
            Workspace workspace = mLauncher.getWorkspace();
            final HomeAllApps allApps = workspace.switchToAllApps();
            workspace = allApps.getAppIcon(DUMMY_APP_NAME).uninstall();
            verifyAppUninstalledFromAllApps(workspace, DUMMY_APP_NAME);
        } finally {
            TestUtil.uninstallDummyApp();
        }
    }

    /**
     * Adds three icons to the workspace and removes one of them by dragging to uninstall.
     */
    @Test
    @PlatinumTest(focusArea = "launcher")
    public void uninstallWorkspaceIcon() throws IOException {
        Point[] gridPositions = TestUtil.getCornersAndCenterPositions(mLauncher);
        StringBuilder sb = new StringBuilder();
        for (Point p : gridPositions) {
            sb.append(p).append(", ");
        }
        Log.d(ICON_MISSING, "allGridPositions: " + sb);
        createShortcutIfNotExist(STORE_APP_NAME, gridPositions[0]);
        createShortcutIfNotExist(MAPS_APP_NAME, gridPositions[1]);
        installDummyAppAndWaitForUIUpdate();
        try {
            createShortcutIfNotExist(DUMMY_APP_NAME, gridPositions[2]);
            Map<String, Point> initialPositions =
                    mLauncher.getWorkspace().getWorkspaceIconsPositions();
            assertThat(initialPositions.keySet())
                    .containsAtLeast(DUMMY_APP_NAME, MAPS_APP_NAME, STORE_APP_NAME);

            mLauncher.getWorkspace().getWorkspaceAppIcon(DUMMY_APP_NAME).uninstall();
            mLauncher.getWorkspace().verifyWorkspaceAppIconIsGone(
                    DUMMY_APP_NAME + " was expected to disappear after uninstall.", DUMMY_APP_NAME);

            Map<String, Point> finalPositions =
                    mLauncher.getWorkspace().getWorkspaceIconsPositions();
            assertThat(finalPositions).doesNotContainKey(DUMMY_APP_NAME);
        } finally {
            TestUtil.uninstallDummyApp();
        }
    }

    /**
     * Drag icon from the Hotseat to the delete drop target
     */
    @Test
    @PortraitLandscape
    public void testAddDeleteShortcutOnHotseat() {
        mLauncher.getWorkspace()
                .deleteAppIcon(mLauncher.getWorkspace().getHotseatAppIcon(0))
                .switchToAllApps()
                .getAppIcon(TEST_APP_NAME)
                .dragToHotseat(0);
        mLauncher.getWorkspace().deleteAppIcon(
                mLauncher.getWorkspace().getHotseatAppIcon(TEST_APP_NAME));
    }
}
