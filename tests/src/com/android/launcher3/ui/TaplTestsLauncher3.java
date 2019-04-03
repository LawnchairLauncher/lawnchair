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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.util.Log;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.TestProtocol;
import com.android.launcher3.popup.ArrowPopup;
import com.android.launcher3.tapl.AllApps;
import com.android.launcher3.tapl.AppIcon;
import com.android.launcher3.tapl.AppIconMenu;
import com.android.launcher3.tapl.AppIconMenuItem;
import com.android.launcher3.tapl.TestHelpers;
import com.android.launcher3.tapl.Widgets;
import com.android.launcher3.tapl.Workspace;
import com.android.launcher3.views.OptionsPopupView;
import com.android.launcher3.widget.WidgetsFullSheet;
import com.android.launcher3.widget.WidgetsRecyclerView;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplTestsLauncher3 extends AbstractLauncherUiTest {
    private static final String TAG = "TaplTestsAosp";

    private static int sScreenshotCount = 0;

    public static class FailureWatcher extends TestWatcher {
        private UiDevice mDevice;

        public FailureWatcher(UiDevice device) {
            this.mDevice = device;
        }

        private void dumpViewHierarchy() {
            final ByteArrayOutputStream stream = new ByteArrayOutputStream();
            try {
                mDevice.dumpWindowHierarchy(stream);
                stream.flush();
                stream.close();
                for (String line : stream.toString().split("\\r?\\n")) {
                    Log.e(TAG, line.trim());
                }
            } catch (IOException e) {
                Log.e(TAG, "error dumping XML to logcat", e);
            }
        }

        @Override
        protected void failed(Throwable e, Description description) {
            if (mDevice == null) return;
            final String pathname = getInstrumentation().getTargetContext().
                    getFilesDir().getPath() + "/TaplTestScreenshot" + sScreenshotCount++ + ".png";
            Log.e(TAG, "Failed test " + description.getMethodName() +
                    ", screenshot will be saved to " + pathname +
                    ", track trace is below, UI object dump is further below:\n" +
                    Log.getStackTraceString(e));
            dumpViewHierarchy();
            mDevice.takeScreenshot(new File(pathname));
        }
    }

    @Rule
    public TestWatcher mFailureWatcher = new FailureWatcher(mDevice);

    @Before
    public void setUp() throws Exception {
        super.setUp();
        initialize(this);
    }

    public static void initialize(AbstractLauncherUiTest test) throws Exception {
        test.clearLauncherData();
        if (TestHelpers.isInLauncherProcess()) {
            test.mActivityMonitor.returnToHome();
        } else {
            test.mDevice.pressHome();
        }
        test.waitForLauncherCondition("Launcher didn't start", launcher -> launcher != null);
        test.waitForState("Launcher internal state didn't switch to Home", LauncherState.NORMAL);
        test.waitForResumed("Launcher internal state is still Background");
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

    private boolean isWorkspaceScrollable(Launcher launcher) {
        return launcher.getWorkspace().getPageCount() > 1;
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
                launcher -> assertTrue("Launcher internal state didn't switch to Showing Menu",
                        OptionsPopupView.getOptionsPopup(launcher) != null));
    }

    public static void runAllAppsTest(AbstractLauncherUiTest test, AllApps allApps) {
        assertNotNull("allApps parameter is null", allApps);

        assertTrue(
                "Launcher internal state is not All Apps", test.isInState(LauncherState.ALL_APPS));

        // Test flinging forward and backward.
        test.executeOnLauncher(launcher -> assertEquals(
                "All Apps started in already scrolled state", 0, test.getAllAppsScroll(launcher)));

        allApps.flingForward();
        assertTrue("Launcher internal state is not All Apps",
                test.isInState(LauncherState.ALL_APPS));
        final Integer flingForwardY = test.getFromLauncher(
                launcher -> test.getAllAppsScroll(launcher));
        test.executeOnLauncher(
                launcher -> assertTrue("flingForward() didn't scroll App Apps", flingForwardY > 0));

        allApps.flingBackward();
        assertTrue(
                "Launcher internal state is not All Apps", test.isInState(LauncherState.ALL_APPS));
        final Integer flingBackwardY = test.getFromLauncher(
                launcher -> test.getAllAppsScroll(launcher));
        test.executeOnLauncher(launcher -> assertTrue("flingBackward() didn't scroll App Apps",
                flingBackwardY < flingForwardY));

        // Test scrolling down to YouTube.
        assertNotNull("All apps: can't fine YouTube", allApps.getAppIcon("YouTube"));
        // Test scrolling up to Camera.
        assertNotNull("All apps: can't fine Camera", allApps.getAppIcon("Camera"));
        // Test failing to find a non-existing app.
        final AllApps allAppsFinal = allApps;
        expectFail("All apps: could find a non-existing app",
                () -> allAppsFinal.getAppIcon("NO APP"));

        assertTrue(
                "Launcher internal state is not All Apps", test.isInState(LauncherState.ALL_APPS));
    }

    @Test
    @PortraitLandscape
    public void testWorkspaceSwitchToAllApps() {
        assertNotNull("switchToAllApps() returned null",
                mLauncher.getWorkspace().switchToAllApps());
        assertTrue("Launcher internal state is not All Apps", isInState(LauncherState.ALL_APPS));
    }

    @Test
    public void testWorkspace() throws Exception {
        final Workspace workspace = mLauncher.getWorkspace();

        // Test that ensureWorkspaceIsScrollable adds a page by dragging an icon there.
        executeOnLauncher(launcher -> assertFalse("Initial workspace state is scrollable",
                isWorkspaceScrollable(launcher)));
        assertNull("Play Store app was found on empty workspace",
                workspace.tryGetWorkspaceAppIcon("Play Store"));

        workspace.ensureWorkspaceIsScrollable();

        executeOnLauncher(
                launcher -> assertEquals("Ensuring workspace scrollable didn't switch to page #1",
                        1, getCurrentWorkspacePage(launcher)));
        executeOnLauncher(
                launcher -> assertTrue("ensureScrollable didn't make workspace scrollable",
                        isWorkspaceScrollable(launcher)));
        assertNotNull("ensureScrollable didn't add Play Store app",
                workspace.tryGetWorkspaceAppIcon("Play Store"));

        // Test flinging workspace.
        workspace.flingBackward();
        assertTrue("Launcher internal state is not Home", isInState(LauncherState.NORMAL));
        executeOnLauncher(
                launcher -> assertEquals("Flinging back didn't switch workspace to page #0",
                        0, getCurrentWorkspacePage(launcher)));

        workspace.flingForward();
        executeOnLauncher(
                launcher -> assertEquals("Flinging forward didn't switch workspace to page #1",
                        1, getCurrentWorkspacePage(launcher)));
        assertTrue("Launcher internal state is not Home", isInState(LauncherState.NORMAL));

        // Test starting a workspace app.
        final AppIcon app = workspace.tryGetWorkspaceAppIcon("Play Store");
        assertNotNull("No Play Store app in workspace", app);
        assertNotNull("AppIcon.launch returned null",
                app.launch(resolveSystemApp(Intent.CATEGORY_APP_MARKET)));
        executeOnLauncher(launcher -> assertTrue(
                "Launcher activity is the top activity; expecting another activity to be the top "
                        + "one",
                isInBackground(launcher)));
    }

    public static void runIconLaunchFromAllAppsTest(AbstractLauncherUiTest test, AllApps allApps) {
        final AppIcon app = allApps.getAppIcon("Calculator");
        assertNotNull("AppIcon.launch returned null", app.launch(
                test.resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR)));
        test.executeOnLauncher(launcher -> assertTrue(
                "Launcher activity is the top activity; expecting another activity to be the top "
                        + "one",
                test.isInBackground(launcher)));
    }

    @Test
    @PortraitLandscape
    public void testAppIconLaunchFromAllAppsFromHome() throws Exception {
        final AllApps allApps = mLauncher.getWorkspace().switchToAllApps();
        assertTrue("Launcher internal state is not All Apps", isInState(LauncherState.ALL_APPS));

        runIconLaunchFromAllAppsTest(this, allApps);
    }

    @Test
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

        mLauncher.pressHome();
        waitForLauncherCondition("Widgets were not closed",
                launcher -> getWidgetsView(launcher) == null);
    }

    private int getWidgetsScroll(Launcher launcher) {
        return getWidgetsView(launcher).getCurrentScrollY();
    }

    private boolean isOptionsPopupVisible(Launcher launcher) {
        final ArrowPopup popup = OptionsPopupView.getOptionsPopup(launcher);
        return popup != null && popup.isShown();
    }

    @Test
    @PortraitLandscape
    public void testLaunchMenuItem() throws Exception {
        if (!TestHelpers.isInLauncherProcess()) return;
        final LauncherActivityInfo testApp = getSettingsApp();

        final AppIconMenu menu = mLauncher.
                getWorkspace().
                switchToAllApps().
                getAppIcon(testApp.getLabel().toString()).
                openMenu();

        executeOnLauncher(
                launcher -> assertTrue("Launcher internal state didn't switch to Showing Menu",
                        isOptionsPopupVisible(launcher)));

        final AppIconMenuItem menuItem = menu.getMenuItem(1);
        final String itemName = menuItem.getText();

        menuItem.launch(testApp.getComponentName().getPackageName(), itemName);
    }

    @Test
    @PortraitLandscape
    public void testDragAppIcon() throws Throwable {
        try {
            TestProtocol.sDebugTracing = true;
            LauncherActivityInfo settingsApp = getSettingsApp();

            final String appName = settingsApp.getLabel().toString();
            // 1. Open all apps and wait for load complete.
            // 2. Drag icon to homescreen.
            // 3. Verify that the icon works on homescreen.
            mLauncher.getWorkspace().
                    switchToAllApps().
                    getAppIcon(appName).
                    dragToWorkspace().
                    getWorkspaceAppIcon(appName).
                    launch(settingsApp.getComponentName().getPackageName());
        } finally {
            TestProtocol.sDebugTracing = false;
        }
    }

    @Test
    @PortraitLandscape
    public void testDragShortcut() throws Throwable {
        if (!TestHelpers.isInLauncherProcess()) return;
        LauncherActivityInfo testApp = getSettingsApp();

        // 1. Open all apps and wait for load complete.
        // 2. Find the app and long press it to show shortcuts.
        // 3. Press icon center until shortcuts appear
        final AppIconMenuItem menuItem = mLauncher.
                getWorkspace().
                switchToAllApps().
                getAppIcon(testApp.getLabel().toString()).
                openMenu().
                getMenuItem(0);
        final String shortcutName = menuItem.getText();

        // 4. Drag the first shortcut to the home screen.
        // 5. Verify that the shortcut works on home screen
        //    (the app opens and has the same text as the shortcut).
        menuItem.
                dragToWorkspace().
                getWorkspaceAppIcon(shortcutName).
                launch(testApp.getComponentName().getPackageName(), shortcutName);
    }
}
