/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.allapps.AllAppsStore.DEFER_UPDATES_TEST;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.util.Log;
import android.view.View;

import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.allapps.AllAppsPagedView;
import com.android.launcher3.allapps.WorkAdapterProvider;
import com.android.launcher3.allapps.WorkEduCard;
import com.android.launcher3.allapps.WorkProfileManager;
import com.android.launcher3.tapl.LauncherInstrumentation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Objects;

public class WorkProfileTest extends AbstractLauncherUiTest {

    private static final int WORK_PAGE = AllAppsContainerView.AdapterHolder.WORK;

    private int mProfileUserId;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        String output =
                mDevice.executeShellCommand(
                        "pm create-user --profileOf 0 --managed TestProfile");
        Log.d("b/203817455", "pm create-user; output: " + output);
        assertTrue("Failed to create work profile", output.startsWith("Success"));

        String[] tokens = output.split("\\s+");
        mProfileUserId = Integer.parseInt(tokens[tokens.length - 1]);
        mDevice.executeShellCommand("am start-user " + mProfileUserId);
    }

    @After
    public void removeWorkProfile() throws Exception {
        mDevice.executeShellCommand("pm remove-user " + mProfileUserId);
    }

    @After
    public void resumeAppStoreUpdate() {
        executeOnLauncher(launcher -> {
            if (launcher == null || launcher.getAppsView() == null) {
                return;
            }
            launcher.getAppsView().getAppsStore().disableDeferUpdates(DEFER_UPDATES_TEST);
        });
    }

    private void waitForWorkTabSetup() {
        waitForLauncherCondition("Work tab not setup", launcher -> {
            if (launcher.getAppsView().getContentView() instanceof AllAppsPagedView) {
                launcher.getAppsView().getAppsStore().enableDeferUpdates(DEFER_UPDATES_TEST);
                return true;
            }
            return false;
        }, LauncherInstrumentation.WAIT_TIME_MS);
    }

    @Test
    public void workTabExists() {
        mDevice.pressHome();
        waitForLauncherCondition("Launcher didn't start", Objects::nonNull);
        waitForState("Launcher internal state didn't switch to Normal", () -> NORMAL);
        executeOnLauncher(launcher -> launcher.getStateManager().goToState(ALL_APPS));
        waitForState("Launcher internal state didn't switch to All Apps", () -> ALL_APPS);
        waitForLauncherCondition("Personal tab is missing",
                launcher -> launcher.getAppsView().isPersonalTabVisible(),
                LauncherInstrumentation.WAIT_TIME_MS);
        waitForLauncherCondition("Work tab is missing",
                launcher -> launcher.getAppsView().isWorkTabVisible(),
                LauncherInstrumentation.WAIT_TIME_MS);
    }

    @Test
    public void toggleWorks() {
        mDevice.pressHome();
        waitForLauncherCondition("Launcher didn't start", Objects::nonNull);
        waitForState("Launcher internal state didn't switch to Normal", () -> NORMAL);
        executeOnLauncher(launcher -> launcher.getStateManager().goToState(ALL_APPS));
        waitForState("Launcher internal state didn't switch to All Apps", () -> ALL_APPS);

        waitForWorkTabSetup();

        executeOnLauncher(launcher -> {
            AllAppsPagedView pagedView = (AllAppsPagedView) launcher.getAppsView().getContentView();
            pagedView.setCurrentPage(WORK_PAGE);
        });

        WorkProfileManager manager = getFromLauncher(l -> l.getAppsView().getWorkManager());


        waitForLauncherCondition("work profile initial state check failed", launcher ->
                        manager.getWorkModeSwitch() != null
                                && manager.getCurrentState() == WorkProfileManager.STATE_ENABLED
                                && manager.getWorkModeSwitch().isEnabled(),
                LauncherInstrumentation.WAIT_TIME_MS);

        //start work profile toggle OFF test
        executeOnLauncher(l -> l.getAppsView().getWorkManager().getWorkModeSwitch().performClick());

        waitForLauncherCondition("Work profile toggle OFF failed", launcher -> {
            manager.reset(); // pulls current state from system
            return manager.getCurrentState() == WorkProfileManager.STATE_DISABLED;
        }, LauncherInstrumentation.WAIT_TIME_MS);

        // start work profile toggle ON test
        executeOnLauncher(l -> {
            AllAppsContainerView allApps = l.getAppsView();
            assertEquals("Work tab is not focused", allApps.getCurrentPage(), WORK_PAGE);
            View workPausedCard = allApps.getActiveRecyclerView().findViewHolderForAdapterPosition(
                    0).itemView;
            workPausedCard.findViewById(R.id.enable_work_apps).performClick();
        });
        waitForLauncherCondition("Work profile toggle ON failed", launcher -> {
            manager.reset(); // pulls current state from system
            return manager.getCurrentState() == WorkProfileManager.STATE_ENABLED;
        }, LauncherInstrumentation.WAIT_TIME_MS);

    }

    @Test
    public void testEdu() {
        mDevice.pressHome();
        waitForLauncherCondition("Launcher didn't start", Objects::nonNull);
        waitForState("Launcher internal state didn't switch to Normal", () -> NORMAL);
        executeOnLauncher(launcher -> launcher.getStateManager().goToState(ALL_APPS));
        waitForState("Launcher internal state didn't switch to All Apps", () -> ALL_APPS);
        waitForWorkTabSetup();
        executeOnLauncher(l -> {
            l.getSharedPrefs().edit().putInt(WorkAdapterProvider.KEY_WORK_EDU_STEP, 0).commit();
            ((AllAppsPagedView) l.getAppsView().getContentView()).setCurrentPage(WORK_PAGE);
            l.getAppsView().getWorkManager().reset();
        });

        waitForLauncherCondition("Work profile education not shown",
                l -> l.getAppsView().getActiveRecyclerView()
                        .findViewHolderForAdapterPosition(0).itemView instanceof WorkEduCard,
                LauncherInstrumentation.WAIT_TIME_MS);
    }
}
