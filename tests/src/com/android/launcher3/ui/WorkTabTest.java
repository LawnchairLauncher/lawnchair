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
import static com.android.launcher3.util.rule.TestStabilityRule.LOCAL;
import static com.android.launcher3.util.rule.TestStabilityRule.UNBUNDLED_POSTSUBMIT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.allapps.AllAppsPagedView;
import com.android.launcher3.util.rule.TestStabilityRule;
import com.android.launcher3.views.WorkFooterContainer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Objects;

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
    // b/143285809 Remove @Stability on 02/21/20 if the test doesn't flake.
    @TestStabilityRule.Stability(flavors = LOCAL | UNBUNDLED_POSTSUBMIT)
    public void workTabExists() {
        mDevice.pressHome();
        waitForLauncherCondition("Launcher didn't start", Objects::nonNull);
        executeOnLauncher(launcher -> launcher.getStateManager().goToState(ALL_APPS));
        waitForLauncherCondition("Personal tab is missing",
                launcher -> launcher.getAppsView().isPersonalTabVisible(), 60000);
        waitForLauncherCondition("Work tab is missing",
                launcher -> launcher.getAppsView().isWorkTabVisible(), 60000);
    }

    @Test
    // b/143285809 Remove @Stability on 02/21/20 if the test doesn't flake.
    @TestStabilityRule.Stability(flavors = LOCAL | UNBUNDLED_POSTSUBMIT)
    public void toggleWorks() {
        mDevice.pressHome();
        waitForLauncherCondition("Launcher didn't start", Objects::nonNull);
        executeOnLauncher(launcher -> launcher.getStateManager().goToState(ALL_APPS));
        waitForState("Launcher internal state didn't switch to All Apps", () -> ALL_APPS);
        getOnceNotNull("Apps view did not bind",
                launcher -> launcher.getAppsView().getWorkFooterContainer());

        UserManager userManager = getFromLauncher(l -> l.getSystemService(UserManager.class));
        assertEquals(2, userManager.getUserProfiles().size());
        UserHandle workProfile = getFromLauncher(l -> {
            UserHandle myHandle = Process.myUserHandle();
            List<UserHandle> userProfiles = userManager.getUserProfiles();
            return userProfiles.get(0) == myHandle ? userProfiles.get(1) : userProfiles.get(0);
        });

        waitForLauncherCondition("work profile can't be turned off",
                l -> userManager.requestQuietModeEnabled(true, workProfile));

        assertTrue(userManager.isQuietModeEnabled(workProfile));
        executeOnLauncher(launcher -> {
            WorkFooterContainer wf = launcher.getAppsView().getWorkFooterContainer();
            ((AllAppsPagedView) launcher.getAppsView().getContentView()).snapToPageImmediately(
                    AllAppsContainerView.AdapterHolder.WORK);
            wf.getWorkModeSwitch().toggle();
        });
        waitForLauncherCondition("Work toggle did not work",
                l -> l.getSystemService(UserManager.class).isQuietModeEnabled(workProfile));
    }

}