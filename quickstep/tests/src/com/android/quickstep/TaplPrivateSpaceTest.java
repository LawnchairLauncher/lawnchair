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

package com.android.quickstep;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.android.launcher3.allapps.PrivateProfileManager;
import com.android.launcher3.tapl.HomeAllApps;
import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.tapl.PrivateSpaceContainer;
import com.android.launcher3.util.TestUtil;
import com.android.launcher3.util.rule.ScreenRecordRule;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Objects;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplPrivateSpaceTest extends AbstractQuickStepTest {

    private int mProfileUserId;

    private static final String PRIVATE_PROFILE_NAME = "LauncherPrivateProfile";
    private static final String INSTALLED_APP_NAME = "Aardwolf";
    private static final int MAX_STATE_TOGGLE_TRIES = 2;
    private static final String TAG = "TaplPrivateSpaceTest";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        initialize(this);

        createAndStartPrivateProfileUser();

        mDevice.pressHome();
        waitForLauncherCondition("Launcher didn't start", Objects::nonNull);
        waitForStateTransitionToEnd("Launcher internal state didn't switch to Normal",
                () -> NORMAL);
        waitForResumed("Launcher internal state is still Background");
        mLauncher.getWorkspace().switchToAllApps();
        waitForStateTransitionToEnd("Launcher internal state didn't switch to All Apps",
                () -> ALL_APPS);

        // Wait for Private Space being available in Launcher.
        waitForPrivateSpaceSetup();
        // Wait for Launcher UI to be updated with Private Space Items.
        waitForLauncherUIUpdate();
    }

    private void createAndStartPrivateProfileUser() {
        String createUserOutput = executeShellCommand("pm create-user --profileOf 0 --user-type "
                + "android.os.usertype.profile.PRIVATE " + PRIVATE_PROFILE_NAME);
        updatePrivateProfileSetupSuccessful("pm create-user", createUserOutput);
        String[] tokens = createUserOutput.split("\\s+");
        mProfileUserId = Integer.parseInt(tokens[tokens.length - 1]);
        StringBuilder logStr = new StringBuilder().append("profileId: ").append(mProfileUserId);
        for (String str : tokens) {
            logStr.append(str).append("\n");
        }
        String startUserOutput = executeShellCommand("am start-user " + mProfileUserId);
        updatePrivateProfileSetupSuccessful("am start-user", startUserOutput);
    }

    @After
    public void removePrivateProfile() {
        String userListOutput = executeShellCommand("pm list users");
        if (isPrivateProfilePresent("pm list users", userListOutput)) {
            String output = executeShellCommand("pm remove-user " + mProfileUserId);
            updateProfileRemovalSuccessful("pm remove-user", output);
            waitForPrivateSpaceRemoval();
        }
    }

    @Test
    public void testPrivateSpaceContainerIsPresent() {
        // Scroll to the bottom of All Apps
        executeOnLauncher(launcher -> launcher.getAppsView().resetAndScrollToPrivateSpaceHeader());
        // Freeze All Apps
        HomeAllApps homeAllApps = mLauncher.getAllApps();
        homeAllApps.freeze();

        try {
            // Verify Unlocked View elements are present.
            assertNotNull("Private Space Unlocked View not found, or is not correct",
                    homeAllApps.getPrivateSpaceUnlockedView());
        } finally {
            // UnFreeze
            homeAllApps.unfreeze();
        }
    }

    @Test
    @ScreenRecordRule.ScreenRecord // b/334946529
    public void testUserInstalledAppIsShownAboveDivider() throws IOException {
        // Ensure that the App is not installed in main user otherwise, it may not be found in
        // PS container.
        TestUtil.uninstallDummyApp();
        // Install the app in Private Profile
        TestUtil.installDummyAppForUser(mProfileUserId);
        waitForLauncherUIUpdate();
        // Scroll to the bottom of All Apps
        executeOnLauncher(launcher -> launcher.getAppsView().resetAndScrollToPrivateSpaceHeader());
        // Freeze All Apps
        HomeAllApps homeAllApps = mLauncher.getAllApps();
        homeAllApps.freeze();

        try {
            // Verify the Installed App is displayed in correct position.
            PrivateSpaceContainer psContainer = homeAllApps.getPrivateSpaceUnlockedView();
            psContainer.verifyInstalledAppIsPresent(INSTALLED_APP_NAME);
        } finally {
            // UnFreeze
            homeAllApps.unfreeze();
        }
    }

    @Test
    @ScreenRecordRule.ScreenRecord // b/334946529
    public void testPrivateSpaceAppLongPressUninstallMenu() throws IOException {
        // Ensure that the App is not installed in main user otherwise, it may not be found in
        // PS container.
        TestUtil.uninstallDummyApp();
        // Install the app in Private Profile
        TestUtil.installDummyAppForUser(mProfileUserId);
        waitForLauncherUIUpdate();
        // Scroll to the bottom of All Apps
        executeOnLauncher(launcher -> launcher.getAppsView().resetAndScrollToPrivateSpaceHeader());
        // Freeze All Apps
        HomeAllApps homeAllApps = mLauncher.getAllApps();
        homeAllApps.freeze();

        try {
            // Get the "uninstall" menu item.
            homeAllApps.getAppIcon(INSTALLED_APP_NAME).openMenu().getMenuItem("Uninstall app");
        } finally {
            // UnFreeze
            homeAllApps.unfreeze();
        }
    }

    @Test
    @ScreenRecordRule.ScreenRecord // b/334946529
    public void testPrivateSpaceLockingBehaviour() throws IOException {
        // Scroll to the bottom of All Apps
        executeOnLauncher(launcher -> launcher.getAppsView().resetAndScrollToPrivateSpaceHeader());
        HomeAllApps homeAllApps = mLauncher.getAllApps();

        // Disable Private Space
        togglePrivateSpaceWithRetry(PrivateProfileManager.STATE_DISABLED, homeAllApps);
        // Scroll to the bottom of All Apps
        executeOnLauncher(launcher -> launcher.getAppsView().resetAndScrollToPrivateSpaceHeader());

        homeAllApps.freeze();
        try {
            // Verify Locked View elements are present.
            homeAllApps.getPrivateSpaceLockedView();
        } finally {
            // UnFreeze
            homeAllApps.unfreeze();
        }

        // Enable Private Space
        togglePrivateSpaceWithRetry(PrivateProfileManager.STATE_ENABLED, homeAllApps);
        // Scroll to the bottom of All Apps
        executeOnLauncher(launcher -> launcher.getAppsView().resetAndScrollToPrivateSpaceHeader());

        homeAllApps.freeze();
        try {
            // Verify UnLocked View elements are present.
            homeAllApps.getPrivateSpaceUnlockedView();
        } finally {
            // UnFreeze
            homeAllApps.unfreeze();
        }
    }

    private void togglePrivateSpace(int state, HomeAllApps homeAllApps) {
        homeAllApps.freeze();
        try {
            // Try Toggling Private Space
            homeAllApps.togglePrivateSpace();
        }  finally {
            // UnFreeze
            homeAllApps.unfreeze();
        }
        PrivateProfileManager manager = getFromLauncher(l -> l.getAppsView()
                .getPrivateProfileManager());
        waitForLauncherCondition("Private profile toggle to state: " + state + " failed",
                launcher -> {
                    manager.reset();
                    return manager.getCurrentState() == state;
                },
                LauncherInstrumentation.WAIT_TIME_MS);
        // Wait for Launcher UI to be updated with Private Space Items.
        waitForLauncherUIUpdate();
    }

    private void togglePrivateSpaceWithRetry(int state, HomeAllApps homeAllApps) {
        int togglePsCount = 0;
        boolean shouldRetry;
        do {
            togglePsCount ++;
            try {
                togglePrivateSpace(state, homeAllApps);
                // No need to retry if the toggle was successful.
                shouldRetry = false;
            } catch (AssertionError error) {
                if (togglePsCount < MAX_STATE_TOGGLE_TRIES) {
                    shouldRetry = true;
                } else {
                    throw error;
                }
            }
        } while (shouldRetry);
    }

    private void waitForPrivateSpaceSetup() {
        waitForLauncherCondition("Private Profile not setup",
                launcher -> launcher.getAppsView().hasPrivateProfile(),
                LauncherInstrumentation.WAIT_TIME_MS);
    }

    private void waitForPrivateSpaceRemoval() {
        waitForLauncherCondition("Private Profile not setup",
                launcher -> !launcher.getAppsView().hasPrivateProfile(),
                LauncherInstrumentation.WAIT_TIME_MS);
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

    private void updatePrivateProfileSetupSuccessful(String cli, String output) {
        Log.d(TAG, "updatePrivateProfileSetupSuccessful, cli=" + cli + " " + "output="
                + output);
        assertTrue(output, output.startsWith("Success"));
    }

    private void updateProfileRemovalSuccessful(String cli, String output) {
        Log.d(TAG, "updateProfileRemovalSuccessful, cli=" + cli + " " + "output=" + output);
        assertTrue(output, output.startsWith("Success"));
    }

    private boolean isPrivateProfilePresent(String cli, String output) {
        Log.d(TAG, "updatePrivateProfilePresent, cli=" + cli + " " + "output=" + output);
        return output.contains(PRIVATE_PROFILE_NAME);
    }

    private String executeShellCommand(String command) {
        try {
            return mDevice.executeShellCommand(command);
        } catch (IOException e) {
            Log.e(TAG, "error running shell command", e);
            throw new RuntimeException(e);
        }
    }
}
