/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.quickstep;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.os.RemoteException;
import android.util.Log;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.tapl.AllApps;
import com.android.launcher3.tapl.AllAppsFromOverview;
import com.android.launcher3.tapl.AppIcon;
import com.android.launcher3.tapl.Background;
import com.android.launcher3.tapl.Overview;
import com.android.launcher3.tapl.OverviewTask;
import com.android.launcher3.tapl.TestHelpers;
import com.android.launcher3.tapl.Widgets;
import com.android.launcher3.tapl.Workspace;
import com.android.launcher3.views.OptionsPopupView;
import com.android.launcher3.widget.WidgetsFullSheet;
import com.android.launcher3.widget.WidgetsRecyclerView;
import com.android.quickstep.QuickStepOnOffRule.QuickstepOnOff;
import com.android.quickstep.views.RecentsView;

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
public class TaplTests extends AbstractQuickStepTest {
    private static final String TAG = "TaplTests";

    private static int sScreenshotCount = 0;

    @Rule
    public TestWatcher mFailureWatcher = new TestWatcher() {
        private void dumpViewHierarchy() {
            final ByteArrayOutputStream stream = new ByteArrayOutputStream();
            try {
                mDevice.dumpWindowHierarchy(stream);
                stream.flush();
                stream.close();
                for (String line : stream.toString().split("\\r?\\n")) {
                    Log.e(TaplTests.TAG, line.trim());
                }
            } catch (IOException e) {
                Log.e(TaplTests.TAG, "error dumping XML to logcat", e);
            }
        }

        @Override
        protected void failed(Throwable e, Description description) {
            if (mDevice == null) return;
            final String pathname = getInstrumentation().getTargetContext().
                    getFilesDir().getPath() + "/TaplTestScreenshot" + sScreenshotCount++ + ".png";
            Log.e(TaplTests.TAG, "Failed test " + description.getMethodName() +
                    ", screenshot will be saved to " + pathname +
                    ", track trace is below, UI object dump is further below:\n" +
                    Log.getStackTraceString(e));
            dumpViewHierarchy();
            mDevice.takeScreenshot(new File(pathname));
        }
    };

    @Before
    public void setUp() throws Exception {
        super.setUp();

        clearLauncherData();

        mDevice.pressHome();
        waitForState("Launcher internal state didn't switch to Home", LauncherState.NORMAL);
        waitForResumed("Launcher internal state is still Background");
    }

    private boolean isInState(LauncherState state) {
        if (!TestHelpers.isInLauncherProcess()) return true;
        return getFromLauncher(launcher -> launcher.getStateManager().getState() == state);
    }

    // Please don't add negative test cases for methods that fail only after a long wait.
    private void expectFail(String message, Runnable action) {
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

    private boolean isInBackground(Launcher launcher) {
        return !launcher.hasBeenResumed();
    }

    private void startTestApps() throws Exception {
        startAppFast(resolveSystemApp(Intent.CATEGORY_APP_MESSAGING));
        startAppFast(resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR));
        startAppFast(resolveSystemApp(Intent.CATEGORY_APP_CONTACTS));

        executeOnLauncher(launcher -> assertTrue(
                "Launcher activity is the top activity; expecting another activity to be the top "
                        + "one",
                isInBackground(launcher)));
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

    @Test
    @PortraitLandscape
    public void testPressRecentAppsLauncherAndGetOverview() throws RemoteException {
        mDevice.pressRecentApps();
        waitForState("Launcher internal state didn't switch to Overview", LauncherState.OVERVIEW);

        assertNotNull("getOverview() returned null", mLauncher.getOverview());
    }

    private void runAllAppsTest(AllApps allApps) throws Exception {
        assertNotNull("allApps parameter is null", allApps);

        assertTrue("Launcher internal state is not All Apps", isInState(LauncherState.ALL_APPS));

        // Test flinging forward and backward.
        executeOnLauncher(launcher -> assertEquals("All Apps started in already scrolled state", 0,
                getAllAppsScroll(launcher)));

        allApps.flingForward();
        assertTrue("Launcher internal state is not All Apps", isInState(LauncherState.ALL_APPS));
        final Integer flingForwardY = getFromLauncher(launcher -> getAllAppsScroll(launcher));
        executeOnLauncher(
                launcher -> assertTrue("flingForward() didn't scroll App Apps", flingForwardY > 0));

        allApps.flingBackward();
        assertTrue("Launcher internal state is not All Apps", isInState(LauncherState.ALL_APPS));
        final Integer flingBackwardY = getFromLauncher(launcher -> getAllAppsScroll(launcher));
        executeOnLauncher(launcher -> assertTrue("flingBackward() didn't scroll App Apps",
                flingBackwardY < flingForwardY));

        // Test scrolling down to YouTube.
        assertNotNull("All apps: can't fine YouTube", allApps.getAppIcon("YouTube"));
        // Test scrolling up to Camera.
        assertNotNull("All apps: can't fine Camera", allApps.getAppIcon("Camera"));
        // Test failing to find a non-existing app.
        final AllApps allAppsFinal = allApps;
        expectFail("All apps: could find a non-existing app",
                () -> allAppsFinal.getAppIcon("NO APP"));

        assertTrue("Launcher internal state is not All Apps", isInState(LauncherState.ALL_APPS));
    }

    private int getAllAppsScroll(Launcher launcher) {
        return launcher.getAppsView().getActiveRecyclerView().getCurrentScrollY();
    }

    @Test
    @PortraitLandscape
    public void testAllAppsFromHome() throws Exception {
        // Test opening all apps
        assertNotNull("switchToAllApps() returned null",
                mLauncher.getWorkspace().switchToAllApps());

        runAllAppsTest(mLauncher.getAllApps());

        // Testing pressHome.
        assertTrue("Launcher internal state is not All Apps", isInState(LauncherState.ALL_APPS));
        assertNotNull("pressHome returned null", mLauncher.pressHome());
        assertTrue("Launcher internal state is not Home", isInState(LauncherState.NORMAL));
        assertNotNull("getHome returned null", mLauncher.getWorkspace());
    }

    @Test
    @QuickstepOnOff
    @PortraitLandscape
    public void testWorkspaceSwitchToAllApps() {
        assertNotNull("switchToAllApps() returned null",
                mLauncher.getWorkspace().switchToAllApps());
        assertTrue("Launcher internal state is not All Apps", isInState(LauncherState.ALL_APPS));
    }

    @Test
    public void testAllAppsFromOverview() throws Exception {
        // Test opening all apps from Overview.
        assertNotNull("switchToAllApps() returned null",
                mLauncher.getWorkspace().switchToOverview().switchToAllApps());

        runAllAppsTest(mLauncher.getAllAppsFromOverview());
    }

    @Test
    public void testWorkspace() throws Exception {
        final Workspace workspace = mLauncher.getWorkspace();

        // Test that ensureWorkspaceIsScrollable adds a page by dragging an icon there.
        executeOnLauncher(launcher -> assertFalse("Initial workspace state is scrollable",
                isWorkspaceScrollable(launcher)));
        assertNull("Messages app was found on empty workspace",
                workspace.tryGetWorkspaceAppIcon("Messages"));

        workspace.ensureWorkspaceIsScrollable();

        executeOnLauncher(
                launcher -> assertEquals("Ensuring workspace scrollable didn't switch to page #1",
                        1, getCurrentWorkspacePage(launcher)));
        executeOnLauncher(
                launcher -> assertTrue("ensureScrollable didn't make workspace scrollable",
                        isWorkspaceScrollable(launcher)));
        assertNotNull("ensureScrollable didn't add Messages app",
                workspace.tryGetWorkspaceAppIcon("Messages"));

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
        final AppIcon app = workspace.tryGetWorkspaceAppIcon("Messages");
        assertNotNull("No Messages app in workspace", app);
        assertNotNull("AppIcon.launch returned null",
                app.launch(resolveSystemApp(Intent.CATEGORY_APP_MESSAGING)));
        executeOnLauncher(launcher -> assertTrue(
                "Launcher activity is the top activity; expecting another activity to be the top "
                        + "one",
                isInBackground(launcher)));
    }

    @Test
    @PortraitLandscape
    public void testOverview() throws Exception {
        startTestApps();
        Overview overview = mLauncher.pressHome().switchToOverview();
        assertTrue("Launcher internal state didn't switch to Overview",
                isInState(LauncherState.OVERVIEW));
        executeOnLauncher(
                launcher -> assertTrue("Don't have at least 3 tasks", getTaskCount(launcher) >= 3));

        // Test flinging forward and backward.
        executeOnLauncher(launcher -> assertEquals("Current task in Overview is not 0",
                0, getCurrentOverviewPage(launcher)));

        overview.flingForward();
        assertTrue("Launcher internal state is not Overview", isInState(LauncherState.OVERVIEW));
        final Integer currentTaskAfterFlingForward = getFromLauncher(
                launcher -> getCurrentOverviewPage(launcher));
        executeOnLauncher(launcher -> assertTrue("Current task in Overview is still 0",
                currentTaskAfterFlingForward > 0));

        overview.flingBackward();
        assertTrue("Launcher internal state is not Overview", isInState(LauncherState.OVERVIEW));
        executeOnLauncher(launcher -> assertTrue("Flinging back in Overview did nothing",
                getCurrentOverviewPage(launcher) < currentTaskAfterFlingForward));

        // Test opening a task.
        OverviewTask task = mLauncher.pressHome().switchToOverview().getCurrentTask();
        assertNotNull("overview.getCurrentTask() returned null (1)", task);
        assertNotNull("OverviewTask.open returned null", task.open());
        assertTrue("Contacts app didn't open from Overview", mDevice.wait(Until.hasObject(
                By.pkg(resolveSystemApp(Intent.CATEGORY_APP_CONTACTS)).depth(0)),
                LONG_WAIT_TIME_MS));
        executeOnLauncher(launcher -> assertTrue(
                "Launcher activity is the top activity; expecting another activity to be the top "
                        + "one",
                isInBackground(launcher)));

        // Test dismissing a task.
        overview = mLauncher.pressHome().switchToOverview();
        assertTrue("Launcher internal state didn't switch to Overview",
                isInState(LauncherState.OVERVIEW));
        final Integer numTasks = getFromLauncher(launcher -> getTaskCount(launcher));
        task = overview.getCurrentTask();
        assertNotNull("overview.getCurrentTask() returned null (2)", task);
        task.dismiss();
        executeOnLauncher(
                launcher -> assertEquals("Dismissing a task didn't remove 1 task from Overview",
                        numTasks - 1, getTaskCount(launcher)));

        if (!TestHelpers.isInLauncherProcess() ||
                getFromLauncher(launcher -> !launcher.getDeviceProfile().isLandscape)) {
            // Test switching to all apps and back.
            final AllAppsFromOverview allApps = overview.switchToAllApps();
            assertNotNull("overview.switchToAllApps() returned null (1)", allApps);
            assertTrue("Launcher internal state is not All Apps (1)",
                    isInState(LauncherState.ALL_APPS));

            overview = allApps.switchBackToOverview();
            assertNotNull("allApps.switchBackToOverview() returned null", overview);
            assertTrue("Launcher internal state didn't switch to Overview",
                    isInState(LauncherState.OVERVIEW));

            // Test UIDevice.pressBack()
            overview.switchToAllApps();
            assertNotNull("overview.switchToAllApps() returned null (2)", allApps);
            assertTrue("Launcher internal state is not All Apps (2)",
                    isInState(LauncherState.ALL_APPS));
            mDevice.pressBack();
            mLauncher.getOverview();
        }

        // Test UIDevice.pressHome, once we are in AllApps.
        mDevice.pressHome();
        waitForState("Launcher internal state didn't switch to Home", LauncherState.NORMAL);
    }

    private int getCurrentOverviewPage(Launcher launcher) {
        return launcher.<RecentsView>getOverviewPanel().getCurrentPage();
    }

    private int getTaskCount(Launcher launcher) {
        return launcher.<RecentsView>getOverviewPanel().getTaskViewCount();
    }

    private void runIconLaunchFromAllAppsTest(AllApps allApps) throws Exception {
        final AppIcon app = allApps.getAppIcon("Calculator");
        assertNotNull("AppIcon.launch returned null", app.launch(
                resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR)));
        executeOnLauncher(launcher -> assertTrue(
                "Launcher activity is the top activity; expecting another activity to be the top "
                        + "one",
                isInBackground(launcher)));
    }

    @Test
    @PortraitLandscape
    public void testAppIconLaunchFromAllAppsFromHome() throws Exception {
        final AllApps allApps = mLauncher.getWorkspace().switchToAllApps();
        assertTrue("Launcher internal state is not All Apps", isInState(LauncherState.ALL_APPS));

        runIconLaunchFromAllAppsTest(allApps);
    }

    @Test
    public void testAppIconLaunchFromAllAppsFromOverview() throws Exception {
        final AllApps allApps =
                mLauncher.getWorkspace().switchToOverview().switchToAllApps();
        assertTrue("Launcher internal state is not All Apps", isInState(LauncherState.ALL_APPS));

        runIconLaunchFromAllAppsTest(allApps);
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

        mDevice.pressHome();
        waitForLauncherCondition("Widgets were not closed",
                launcher -> getWidgetsView(launcher) == null);
    }

    private int getWidgetsScroll(Launcher launcher) {
        return getWidgetsView(launcher).getCurrentScrollY();
    }

    @Test
    @QuickstepOnOff
    @PortraitLandscape
    public void testSwitchToOverview() throws Exception {
        assertNotNull("Workspace.switchToOverview() returned null",
                mLauncher.pressHome().switchToOverview());
        assertTrue("Launcher internal state didn't switch to Overview",
                isInState(LauncherState.OVERVIEW));
    }

    @Test
    @QuickstepOnOff
    @PortraitLandscape
    public void testBackground() throws Exception {
        startAppFast(resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR));
        final Background background = mLauncher.getBackground();
        assertNotNull("Launcher.getBackground() returned null", background);
        executeOnLauncher(launcher -> assertTrue(
                "Launcher activity is the top activity; expecting another activity to be the top "
                        + "one",
                isInBackground(launcher)));

        assertNotNull("Background.switchToOverview() returned null", background.switchToOverview());
        assertTrue("Launcher internal state didn't switch to Overview",
                isInState(LauncherState.OVERVIEW));
    }
}
