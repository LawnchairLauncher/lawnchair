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
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.allapps.AllAppsStore.DEFER_UPDATES_TEST;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.widget.TextView;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.allapps.AllAppsPagedView;
import com.android.launcher3.allapps.WorkModeSwitch;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.views.WorkEduView;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class WorkTabTest extends AbstractLauncherUiTest {

    private int mProfileUserId;

    private static final int WORK_PAGE = AllAppsContainerView.AdapterHolder.WORK;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        String output =
                mDevice.executeShellCommand(
                        "pm create-user --profileOf 0 --managed TestProfile");
        assertTrue("Failed to create work profile", output.startsWith("Success"));

        String[] tokens = output.split("\\s+");
        mProfileUserId = Integer.parseInt(tokens[tokens.length - 1]);
        Log.d(TestProtocol.WORK_PROFILE_REMOVED, "Created new user uid" + mProfileUserId);
        mDevice.executeShellCommand("am start-user " + mProfileUserId);
    }

    @After
    public void removeWorkProfile() throws Exception {
        Log.d(TestProtocol.WORK_PROFILE_REMOVED, "(teardown) removing uid" + mProfileUserId,
                new Exception());
        mDevice.executeShellCommand("pm remove-user " + mProfileUserId);
    }

    @After
    public void resumeAppStoreUpdate() {
        executeOnLauncher(launcher -> {
            if (launcher == null || launcher.getAppsView() == null) {
                return;
            }
            launcher.getAppsView().getAppsStore().disableDeferUpdates(DEFER_UPDATES_TEST);
            Log.d(TestProtocol.WORK_PROFILE_REMOVED, "resuming AppStore updates");
        });
    }

    @Ignore("b/182844465")
    @Test
    public void workTabExists() {
        mDevice.pressHome();
        waitForLauncherCondition("Launcher didn't start", Objects::nonNull);
        executeOnLauncher(launcher -> launcher.getStateManager().goToState(ALL_APPS));
        waitForState("Launcher internal state didn't switch to All Apps", () -> ALL_APPS);
        waitForLauncherCondition("Personal tab is missing",
                launcher -> launcher.getAppsView().isPersonalTabVisible(), 60000);
        waitForLauncherCondition("Work tab is missing",
                launcher -> launcher.getAppsView().isWorkTabVisible(), 60000);
    }

    @Ignore("b/182844465")
    @Test
    public void toggleWorks() {
        mDevice.pressHome();
        waitForLauncherCondition("Launcher didn't start", Objects::nonNull);
        executeOnLauncher(launcher -> launcher.getStateManager().goToState(ALL_APPS));
        waitForState("Launcher internal state didn't switch to All Apps", () -> ALL_APPS);
        getOnceNotNull("Apps view did not bind",
                launcher -> launcher.getAppsView().getWorkModeSwitch(), 60000);

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
            WorkModeSwitch wf = launcher.getAppsView().getWorkModeSwitch();
            ((AllAppsPagedView) launcher.getAppsView().getContentView()).snapToPageImmediately(
                    AllAppsContainerView.AdapterHolder.WORK);
            wf.toggle();
        });
        waitForLauncherCondition("Work toggle did not work",
                l -> l.getSystemService(UserManager.class).isQuietModeEnabled(workProfile));
    }

    @Ignore("b/182844465")
    @Test
    public void testWorkEduFlow() {
        mDevice.pressHome();
        waitForLauncherCondition("Launcher didn't start", Objects::nonNull);
        executeOnLauncher(launcher -> launcher.getSharedPrefs().edit().remove(
                WorkEduView.KEY_WORK_EDU_STEP).remove(
                WorkEduView.KEY_LEGACY_WORK_EDU_SEEN).commit());

        waitForLauncherCondition("Work tab not setup", launcher -> {
            if (launcher.getAppsView().getContentView() instanceof AllAppsPagedView) {
                launcher.getAppsView().getAppsStore().enableDeferUpdates(DEFER_UPDATES_TEST);
                return true;
            }
            return false;
        }, LauncherInstrumentation.WAIT_TIME_MS);

        executeOnLauncher(launcher -> launcher.getStateManager().goToState(ALL_APPS));
        WorkEduView workEduView = getEduView();
        // verify personal app edu is seen first and click "next"
        executeOnLauncher(l -> {
            assertEquals(((TextView) workEduView.findViewById(R.id.content_text)).getText(),
                    l.getResources().getString(R.string.work_profile_edu_personal_apps));
            workEduView.findViewById(R.id.proceed).callOnClick();
        });

        AtomicInteger attempt = new AtomicInteger(0);
        // verify work edu is seen next
        waitForLauncherCondition("Launcher did not show the next edu screen", l -> {
            Log.d(TestProtocol.WORK_PROFILE_REMOVED,
                    "running test attempt" + attempt.getAndIncrement());
            if (!(l.getAppsView().getContentView() instanceof AllAppsPagedView)) {
                Log.d(TestProtocol.WORK_PROFILE_REMOVED, "Work tab not setup. Skipping test");
                return false;
            }
            if (((AllAppsPagedView) l.getAppsView().getContentView()).getCurrentPage()
                    != WORK_PAGE) {
                Log.d(TestProtocol.WORK_PROFILE_REMOVED, "Work page not highlighted");
            }
            return ((TextView) workEduView.findViewById(R.id.content_text)).getText().equals(
                    l.getResources().getString(R.string.work_profile_edu_work_apps));
        });
    }

    @Ignore("b/182844465")
    @Test
    public void testWorkEduIntermittent() {
        mDevice.pressHome();
        waitForLauncherCondition("Launcher didn't start", Objects::nonNull);
        executeOnLauncher(launcher -> launcher.getSharedPrefs().edit().remove(
                WorkEduView.KEY_WORK_EDU_STEP).remove(
                WorkEduView.KEY_LEGACY_WORK_EDU_SEEN).commit());


        waitForLauncherCondition("Work tab not setup",
                launcher -> launcher.getAppsView().getContentView() instanceof AllAppsPagedView,
                60000);
        executeOnLauncher(launcher -> launcher.getStateManager().goToState(ALL_APPS));

        // verify personal app edu is seen
        getEduView();

        // dismiss personal edu
        mDevice.pressHome();
        waitForState("Launcher did not go home", () -> NORMAL);

        // open work tab
        executeOnLauncher(launcher -> launcher.getStateManager().goToState(ALL_APPS));
        waitForState("Launcher did not switch to all apps", () -> ALL_APPS);
        waitForLauncherCondition("Work tab not setup",
                launcher -> launcher.getAppsView().getContentView() instanceof AllAppsPagedView,
                60000);

        executeOnLauncher(launcher -> {
            AllAppsPagedView pagedView = (AllAppsPagedView) launcher.getAppsView().getContentView();
            pagedView.setCurrentPage(WORK_PAGE);
        });

        WorkEduView workEduView = getEduView();

        // verify work tab edu is shown
        waitForLauncherCondition("Launcher did not show the next edu screen",
                l -> ((TextView) workEduView.findViewById(R.id.content_text)).getText().equals(
                        l.getResources().getString(R.string.work_profile_edu_work_apps)));
    }


    private WorkEduView getEduView() {
        waitForLauncherCondition("Edu did not show", l -> {
            DragLayer dragLayer = l.getDragLayer();
            return dragLayer.getChildCount() > 0 && dragLayer.getChildAt(
                    dragLayer.getChildCount() - 1) instanceof WorkEduView;
        }, 6000);
        return getFromLauncher(launcher -> (WorkEduView) launcher.getDragLayer().getChildAt(
                launcher.getDragLayer().getChildCount() - 1));
    }

}