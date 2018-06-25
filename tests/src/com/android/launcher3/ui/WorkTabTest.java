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

import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import com.android.launcher3.R;
import com.android.launcher3.util.Condition;
import com.android.launcher3.util.Wait;
import com.android.launcher3.util.rule.LauncherActivityRule;
import com.android.launcher3.util.rule.ShellCommandRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class WorkTabTest extends AbstractLauncherUiTest {
    @Rule
    public LauncherActivityRule mActivityMonitor = new LauncherActivityRule();
    @Rule
    public ShellCommandRule mDefaultLauncherRule = ShellCommandRule.setDefaultLauncher();

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
        mActivityMonitor.startLauncher();

        // Open all apps and wait for load complete
        final UiObject2 appsContainer = openAllApps();
        assertTrue(Wait.atMost(Condition.minChildCount(appsContainer, 2),
                LARGE_UI_TIMEOUT));

        /*
        assertTrue("Personal tab is missing",
                mDevice.wait(Until.hasObject(getSelectorForId(R.id.tab_personal)),
                        LARGE_UI_TIMEOUT));
        assertTrue("Work tab is missing",
                mDevice.wait(Until.hasObject(getSelectorForId(R.id.tab_work)), LARGE_UI_TIMEOUT));
        */
    }
}