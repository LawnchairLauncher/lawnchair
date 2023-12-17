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

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.android.launcher3.testing.shared.TestProtocol.ICON_MISSING;
import static com.android.launcher3.util.rule.TestStabilityRule.LOCAL;
import static com.android.launcher3.util.rule.TestStabilityRule.PLATFORM_POSTSUBMIT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;
import android.graphics.Point;
import android.os.SystemClock;
import android.platform.test.annotations.PlatinumTest;
import android.util.Log;

import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.popup.ArrowPopup;
import com.android.launcher3.tapl.AllApps;
import com.android.launcher3.tapl.AppIcon;
import com.android.launcher3.tapl.AppIconMenu;
import com.android.launcher3.tapl.AppIconMenuItem;
import com.android.launcher3.tapl.Folder;
import com.android.launcher3.tapl.FolderIcon;
import com.android.launcher3.tapl.HomeAllApps;
import com.android.launcher3.tapl.HomeAppIcon;
import com.android.launcher3.tapl.HomeAppIconMenuItem;
import com.android.launcher3.tapl.Widgets;
import com.android.launcher3.tapl.Workspace;
import com.android.launcher3.ui.PortraitLandscapeRunner.PortraitLandscape;
import com.android.launcher3.util.LauncherLayoutBuilder;
import com.android.launcher3.util.TestUtil;
import com.android.launcher3.util.Wait;
import com.android.launcher3.util.rule.ScreenRecordRule.ScreenRecord;
import com.android.launcher3.util.rule.TISBindRule;
import com.android.launcher3.util.rule.TestStabilityRule.Stability;
import com.android.launcher3.widget.picker.WidgetsFullSheet;
import com.android.launcher3.widget.picker.WidgetsRecyclerView;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Map;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplTestsLauncher3 extends AbstractLauncherUiTest {
    private static final String APP_NAME = "LauncherTestApp";
    private static final String DUMMY_APP_NAME = "Aardwolf";
    private static final String MAPS_APP_NAME = "Maps";
    private static final String STORE_APP_NAME = "Play Store";
    private static final String GMAIL_APP_NAME = "Gmail";
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
        AbstractLauncherUiTest.checkDetectedLeaks(test.mLauncher);
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

    private WidgetsRecyclerView getWidgetsView(Launcher launcher) {
        return WidgetsFullSheet.getWidgetsView(launcher);
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

    @Test
    public void testPressHomeOnAllAppsContextMenu() throws Exception {
        final AllApps allApps = mLauncher.getWorkspace().switchToAllApps();
        allApps.freeze();
        try {
            allApps.getAppIcon("TestActivity7").openMenu();
        } finally {
            allApps.unfreeze();
        }
        mLauncher.goHome();
    }

    public static void runAllAppsTest(AbstractLauncherUiTest test, AllApps allApps) {
        allApps.freeze();
        try {
            assertNotNull("allApps parameter is null", allApps);

            assertTrue(
                    "Launcher internal state is not All Apps",
                    test.isInState(() -> LauncherState.ALL_APPS));

            // Test flinging forward and backward.
            test.executeOnLauncher(launcher -> assertEquals(
                    "All Apps started in already scrolled state", 0,
                    test.getAllAppsScroll(launcher)));

            allApps.flingForward();
            assertTrue("Launcher internal state is not All Apps",
                    test.isInState(() -> LauncherState.ALL_APPS));
            final Integer flingForwardY = test.getFromLauncher(
                    launcher -> test.getAllAppsScroll(launcher));
            test.executeOnLauncher(
                    launcher -> assertTrue("flingForward() didn't scroll App Apps",
                            flingForwardY > 0));

            allApps.flingBackward();
            assertTrue(
                    "Launcher internal state is not All Apps",
                    test.isInState(() -> LauncherState.ALL_APPS));
            final Integer flingBackwardY = test.getFromLauncher(
                    launcher -> test.getAllAppsScroll(launcher));
            test.executeOnLauncher(launcher -> assertTrue("flingBackward() didn't scroll App Apps",
                    flingBackwardY < flingForwardY));

            // Test scrolling down to YouTube.
            assertNotNull("All apps: can't find YouTube", allApps.getAppIcon("YouTube"));
            // Test scrolling up to Camera.
            assertNotNull("All apps: can't find Camera", allApps.getAppIcon("Camera"));
            // Test failing to find a non-existing app.
            final AllApps allAppsFinal = allApps;
            expectFail("All apps: could find a non-existing app",
                    () -> allAppsFinal.getAppIcon("NO APP"));

            assertTrue(
                    "Launcher internal state is not All Apps",
                    test.isInState(() -> LauncherState.ALL_APPS));
        } finally {
            allApps.unfreeze();
        }
    }

    @Test
    @PortraitLandscape
    public void testWorkspaceSwitchToAllApps() {
        assertNotNull("switchToAllApps() returned null",
                mLauncher.getWorkspace().switchToAllApps());
        assertTrue("Launcher internal state is not All Apps",
                isInState(() -> LauncherState.ALL_APPS));
    }

    @Test
    @PortraitLandscape
    public void testAllAppsSwitchToWorkspace() {
        assertNotNull("switchToWorkspace() returned null",
                mLauncher.getWorkspace().switchToAllApps()
                        .switchToWorkspace(/* swipeDown= */ true));
        assertTrue("Launcher internal state is not Workspace",
                isInState(() -> LauncherState.NORMAL));
    }

    @Test
    @PortraitLandscape
    public void testAllAppsSwipeUpToWorkspace() {
        assertNotNull("testAllAppsSwipeUpToWorkspace() returned null",
                mLauncher.getWorkspace().switchToAllApps()
                        .switchToWorkspace(/* swipeDown= */ false));
        assertTrue("Launcher internal state is not Workspace",
                isInState(() -> LauncherState.NORMAL));
    }

    @Test
    @PortraitLandscape
    public void testAllAppsDeadzoneForTablet() throws Exception {
        assumeTrue(mLauncher.isTablet());

        mLauncher.getWorkspace().switchToAllApps().dismissByTappingOutsideForTablet(
                true /* tapRight */);
        mLauncher.getWorkspace().switchToAllApps().dismissByTappingOutsideForTablet(
                false /* tapRight */);
    }

    @PlatinumTest(focusArea = "launcher")
    @Test
    @ScreenRecord // b/202433017
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

    @Test
    @Stability(flavors = LOCAL | PLATFORM_POSTSUBMIT) // b/293191790
    @ScreenRecord
    @PortraitLandscape
    public void testWidgets() throws Exception {
        // Test opening widgets.
        executeOnLauncher(launcher ->
                assertTrue("Widgets is initially opened", getWidgetsView(launcher) == null));
        Widgets widgets = mLauncher.getWorkspace().openAllWidgets();
        assertNotNull("openAllWidgets() returned null", widgets);
        widgets = mLauncher.getAllWidgets();
        assertNotNull("getAllWidgets() returned null", widgets);
        executeOnLauncher(launcher ->
                assertTrue("Widgets is not shown", getWidgetsView(launcher).isShown()));
        executeOnLauncher(launcher -> assertEquals("Widgets is scrolled upon opening",
                0, getWidgetsScroll(launcher)));

        // Test flinging widgets.
        widgets.flingForward();
        Integer flingForwardY = getFromLauncher(launcher -> getWidgetsScroll(launcher));
        executeOnLauncher(launcher -> assertTrue("Flinging forward didn't scroll widgets",
                flingForwardY > 0));

        widgets.flingBackward();
        executeOnLauncher(launcher -> assertTrue("Flinging backward didn't scroll widgets",
                getWidgetsScroll(launcher) < flingForwardY));

        mLauncher.goHome();
        waitForLauncherCondition("Widgets were not closed",
                launcher -> getWidgetsView(launcher) == null);
    }

    private int getWidgetsScroll(Launcher launcher) {
        return getWidgetsView(launcher).computeVerticalScrollOffset();
    }

    private boolean isOptionsPopupVisible(Launcher launcher) {
        final ArrowPopup<?> popup = launcher.getOptionsPopup();
        return popup != null && popup.isShown();
    }

    @Test
    @PortraitLandscape
    @PlatinumTest(focusArea = "launcher")
    public void testLaunchMenuItem() throws Exception {
        final AllApps allApps = mLauncher.getWorkspace().switchToAllApps();
        allApps.freeze();
        try {
            final AppIconMenu menu = allApps.
                    getAppIcon(APP_NAME).
                    openDeepShortcutMenu();

            executeOnLauncher(
                    launcher -> assertTrue("Launcher internal state didn't switch to Showing Menu",
                            isOptionsPopupVisible(launcher)));

            final AppIconMenuItem menuItem = menu.getMenuItem(1);
            assertEquals("Wrong menu item", "Shortcut 2", menuItem.getText());
            menuItem.launch(getAppPackageName());
        } finally {
            allApps.unfreeze();
        }
    }

    @Test
    public void testLaunchHomeScreenMenuItem() {
        // Drag the test app icon to home screen and open short cut menu from the icon
        final HomeAllApps allApps = mLauncher.getWorkspace().switchToAllApps();
        allApps.freeze();
        try {
            allApps.getAppIcon(APP_NAME).dragToWorkspace(false, false);
            final AppIconMenu menu = mLauncher.getWorkspace().getWorkspaceAppIcon(
                    APP_NAME).openDeepShortcutMenu();

            executeOnLauncher(
                    launcher -> assertTrue("Launcher internal state didn't switch to Showing Menu",
                            isOptionsPopupVisible(launcher)));

            final AppIconMenuItem menuItem = menu.getMenuItem(1);
            assertEquals("Wrong menu item", "Shortcut 2", menuItem.getText());
            menuItem.launch(getAppPackageName());
        } finally {
            allApps.unfreeze();
        }
    }

    @PlatinumTest(focusArea = "launcher")
    @Test
    @PortraitLandscape
    @ScreenRecord // b/256898879
    public void testDragAppIcon() throws Throwable {
        // 1. Open all apps and wait for load complete.
        // 2. Drag icon to homescreen.
        // 3. Verify that the icon works on homescreen.
        final HomeAllApps allApps = mLauncher.getWorkspace().switchToAllApps();
        allApps.freeze();
        try {
            allApps.getAppIcon(APP_NAME).dragToWorkspace(false, false);
            mLauncher.getWorkspace().getWorkspaceAppIcon(APP_NAME).launch(getAppPackageName());
        } finally {
            allApps.unfreeze();
        }
        executeOnLauncher(launcher -> assertTrue(
                "Launcher activity is the top activity; expecting another activity to be the top "
                        + "one",
                isInLaunchedApp(launcher)));
    }

    @Test
    @PortraitLandscape
    @PlatinumTest(focusArea = "launcher")
    public void testDragShortcut() throws Throwable {
        // 1. Open all apps and wait for load complete.
        // 2. Find the app and long press it to show shortcuts.
        // 3. Press icon center until shortcuts appear
        final HomeAllApps allApps = mLauncher
                .getWorkspace()
                .switchToAllApps();
        allApps.freeze();
        try {
            final HomeAppIconMenuItem menuItem = allApps
                    .getAppIcon(APP_NAME)
                    .openDeepShortcutMenu()
                    .getMenuItem(0);
            final String actualShortcutName = menuItem.getText();
            final String expectedShortcutName = "Shortcut 1";

            assertEquals(expectedShortcutName, actualShortcutName);
            menuItem.dragToWorkspace(false, false);
            mLauncher.getWorkspace().getWorkspaceAppIcon(expectedShortcutName)
                    .launch(getAppPackageName());
        } finally {
            allApps.unfreeze();
        }
    }

    @Test
    @PortraitLandscape
    @ScreenRecord
    @Ignore // b/233075289
    @PlatinumTest(focusArea = "launcher")
    public void testDragToFolder() {
        // TODO: add the use case to drag an icon to an existing folder. Currently it either fails
        // on tablets or phones due to difference in resolution.
        final HomeAppIcon playStoreIcon = createShortcutIfNotExist(STORE_APP_NAME, 0, 1);
        final HomeAppIcon gmailIcon = createShortcutInCenterIfNotExist(GMAIL_APP_NAME);

        FolderIcon folderIcon = gmailIcon.dragToIcon(playStoreIcon);
        Folder folder = folderIcon.open();
        folder.getAppIcon(STORE_APP_NAME);
        folder.getAppIcon(GMAIL_APP_NAME);
        Workspace workspace = folder.close();

        workspace.verifyWorkspaceAppIconIsGone(STORE_APP_NAME + " should be moved to a folder.",
                STORE_APP_NAME);
        workspace.verifyWorkspaceAppIconIsGone(GMAIL_APP_NAME + " should be moved to a folder.",
                GMAIL_APP_NAME);

        final HomeAppIcon mapIcon = createShortcutInCenterIfNotExist(MAPS_APP_NAME);
        folderIcon = mapIcon.dragToIcon(folderIcon);
        folder = folderIcon.open();
        folder.getAppIcon(MAPS_APP_NAME);
        workspace = folder.close();

        workspace.verifyWorkspaceAppIconIsGone(MAPS_APP_NAME + " should be moved to a folder.",
                MAPS_APP_NAME);
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
    @PlatinumTest(focusArea = "launcher")
    public void testDragAndCancelAppIcon() {
        final HomeAppIcon homeAppIcon = createShortcutInCenterIfNotExist(GMAIL_APP_NAME);
        Point positionBeforeDrag =
                mLauncher.getWorkspace().getWorkspaceIconsPositions().get(GMAIL_APP_NAME);
        assertNotNull("App not found in Workspace before dragging.", positionBeforeDrag);

        mLauncher.getWorkspace().dragAndCancelAppIcon(homeAppIcon);

        Point positionAfterDrag =
                mLauncher.getWorkspace().getWorkspaceIconsPositions().get(GMAIL_APP_NAME);
        assertNotNull("App not found in Workspace after dragging.", positionAfterDrag);
        assertEquals("App not returned to same position in Workspace after drag & cancel",
                positionBeforeDrag, positionAfterDrag);
    }

    @Test
    @PortraitLandscape
    public void testDeleteFromWorkspace() throws Exception {
        // test delete both built-in apps and user-installed app from workspace
        for (String appName : new String[]{"Gmail", "Play Store", APP_NAME}) {
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

    @Test
    @PortraitLandscape
    // TODO(b/293944634): Remove Screenrecord after flaky debug, and add
    // @PlatinumTest(focusArea = "launcher") back
    @ScreenRecord
    public void testUninstallFromWorkspace() throws Exception {
        installDummyAppAndWaitForUIUpdate();
        try {
            verifyAppUninstalledFromAllApps(
                    createShortcutInCenterIfNotExist(DUMMY_APP_NAME).uninstall(), DUMMY_APP_NAME);
        } finally {
            TestUtil.uninstallDummyApp();
        }
    }

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

    @Test
    @PortraitLandscape
    @PlatinumTest(focusArea = "launcher")
    public void testDragAppIconToWorkspaceCell() throws Exception {
        long startTime, endTime, elapsedTime;
        Point[] targets = getCornersAndCenterPositions();

        for (Point target : targets) {
            startTime = SystemClock.uptimeMillis();
            final HomeAllApps allApps = mLauncher.getWorkspace().switchToAllApps();
            allApps.freeze();
            try {
                allApps.getAppIcon(APP_NAME).dragToWorkspace(target.x, target.y);
            } finally {
                allApps.unfreeze();
            }
            // Reset the workspace for the next shortcut creation.
            initialize(this, true);
            endTime = SystemClock.uptimeMillis();
            elapsedTime = endTime - startTime;
            Log.d("testDragAppIconToWorkspaceCellTime",
                    "Milliseconds taken to drag app icon to workspace cell: " + elapsedTime);
        }

        // test to move a shortcut to other cell.
        final HomeAppIcon launcherTestAppIcon = createShortcutInCenterIfNotExist(APP_NAME);
        for (Point target : targets) {
            startTime = SystemClock.uptimeMillis();
            launcherTestAppIcon.dragToWorkspace(target.x, target.y);
            endTime = SystemClock.uptimeMillis();
            elapsedTime = endTime - startTime;
            Log.d("testDragAppIconToWorkspaceCellTime",
                    "Milliseconds taken to move shortcut to other cell: " + elapsedTime);
        }
    }

    /**
     * Adds three icons to the workspace and removes one of them by dragging to uninstall.
     */
    @Test
    @ScreenRecord // b/241821721
    @PlatinumTest(focusArea = "launcher")
    public void uninstallWorkspaceIcon() throws IOException {
        Point[] gridPositions = getCornersAndCenterPositions();
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

            // Debug for b/288944469 I want to test if we are not waiting enough after removing
            // the icon to request the list of icons again, since the items are not removed
            // immediately. This should reduce the flake rate
            SystemClock.sleep(500);
            Map<String, Point> finalPositions =
                    mLauncher.getWorkspace().getWorkspaceIconsPositions();
            assertThat(finalPositions).doesNotContainKey(DUMMY_APP_NAME);
        } finally {
            TestUtil.uninstallDummyApp();
        }
    }

    @Test
    @PortraitLandscape
    @PlatinumTest(focusArea = "launcher")
    public void testDragShortcutToWorkspaceCell() throws Exception {
        Point[] targets = getCornersAndCenterPositions();

        for (Point target : targets) {
            final HomeAllApps allApps = mLauncher.getWorkspace().switchToAllApps();
            allApps.freeze();
            try {
                allApps.getAppIcon(APP_NAME)
                        .openDeepShortcutMenu()
                        .getMenuItem(0)
                        .dragToWorkspace(target.x, target.y);
            } finally {
                allApps.unfreeze();
            }
        }
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
     * @return List of workspace grid coordinates. Those are not pixels. See {@link
     * Workspace#getIconGridDimensions()}
     */
    private Point[] getCornersAndCenterPositions() {
        final Point dimensions = mLauncher.getWorkspace().getIconGridDimensions();
        return new Point[]{
                new Point(0, 1),
                new Point(0, dimensions.y - 2),
                new Point(dimensions.x - 1, 1),
                new Point(dimensions.x - 1, dimensions.y - 2),
                new Point(dimensions.x / 2, dimensions.y / 2)
        };
    }

    public static String getAppPackageName() {
        return getInstrumentation().getContext().getPackageName();
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
