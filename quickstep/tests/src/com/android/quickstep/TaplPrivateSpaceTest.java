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
import static org.junit.Assume.assumeTrue;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.android.launcher3.tapl.LauncherInstrumentation;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Objects;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplPrivateSpaceTest extends AbstractQuickStepTest {

    private int mProfileUserId;
    private boolean mPrivateProfileSetupSuccessful;
    private static final String TAG = "TaplPrivateSpaceTest";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        initialize(this);

        createAndStartPrivateProfileUser();
        assumeTrue("Private Profile Setup not successful, aborting",
                mPrivateProfileSetupSuccessful);

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
                + "android.os.usertype.profile.PRIVATE LauncherPrivateProfile");
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
        String output = executeShellCommand("pm remove-user " + mProfileUserId);
        updateProfileRemovalSuccessful("pm remove-user", output);
        waitForPrivateSpaceRemoval();
    }

    @Test
    public void testPrivateSpaceContainerIsPresent() {
        assumeTrue(mPrivateProfileSetupSuccessful);
        // Scroll to the bottom of All Apps
        executeOnLauncher(launcher -> launcher.getAppsView().resetAndScrollToPrivateSpaceHeader());
        waitForResumed("Launcher internal state is still Background");

        // Verify Unlocked View elements are present.
        assertNotNull("Private Space Unlocked View not found, or is not correct",
                mLauncher.getAllApps().getPrivateSpaceUnlockedView());
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
        mPrivateProfileSetupSuccessful = output.startsWith("Success");
    }

    private void updateProfileRemovalSuccessful(String cli, String output) {
        Log.d(TAG, "updateProfileRemovalSuccessful, cli=" + cli + " " + "output=" + output);
        assertTrue(output, output.startsWith("Success"));
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
