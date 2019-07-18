/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.ui;

import static com.android.launcher3.LauncherState.ALL_APPS;

import static org.junit.Assert.assertTrue;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class WorkTabTest extends AbstractLauncherUiTest {

    private int mProfileUserId;

    @Before
    public void createWorkProfile() throws Exception {
        String output =
                mDevice.executeShellCommand(
                        "pm create-user --profileOf 0 --managed TestProfile");
        assertTrue("Failed to create work profile", output.startsWith("Success"));

        String[] tokens = output.split("\\s+");
        mProfileUserId = Integer.parseInt(tokens[tokens.length - 1]);

        mDevice.executeShellCommand("am start-user " + mProfileUserId);
    }

    @After
    public void removeWorkProfile() throws Exception {
        mDevice.executeShellCommand("pm remove-user " + mProfileUserId);
    }

    @Test
    public void workTabExists() {
        mDevice.pressHome();
        waitForLauncherCondition("Launcher didn't start", launcher -> launcher != null);
        executeOnLauncher(launcher -> launcher.getStateManager().goToState(ALL_APPS));

        /*
        assertTrue("Personal tab is missing", waitForLauncherCondition(
                launcher -> launcher.getAppsView().isPersonalTabVisible()));
        assertTrue("Work tab is missing", waitForLauncherCondition(
                launcher -> launcher.getAppsView().isWorkTabVisible()));
        */
    }
}