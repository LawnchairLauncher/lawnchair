/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.launcher3.util.TestUtil.resolveSystemAppInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.PendingIntent;
import android.app.usage.UsageStatsManager;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.BaseLauncherActivityTest;
import com.android.launcher3.util.rule.ScreenRecordRule;
import com.android.quickstep.views.DigitalWellBeingToast;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskContainer;
import com.android.quickstep.views.TaskView;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class DigitalWellBeingToastTest extends BaseLauncherActivityTest<QuickstepLauncher> {

    @Rule
    public ScreenRecordRule mScreenRecordRule = new ScreenRecordRule();


    public final String calculatorPackage =
            resolveSystemAppInfo(Intent.CATEGORY_APP_CALCULATOR).packageName;

    @Test
    public void testToast() {
        startAppFast(calculatorPackage);

        final UsageStatsManager usageStatsManager =
                targetContext().getSystemService(UsageStatsManager.class);
        final int observerId = 0;

        try {
            final String[] packages = new String[]{calculatorPackage};

            // Set time limit for app.
            runWithShellPermission(() ->
                    usageStatsManager.registerAppUsageLimitObserver(observerId, packages,
                            Duration.ofSeconds(600), Duration.ofSeconds(300),
                            PendingIntent.getActivity(targetContext(), -1, new Intent()
                                            .setPackage(targetContext().getPackageName()),
                                    PendingIntent.FLAG_MUTABLE)));

            // b/324261526 when removing BaseLauncherActivityTest we should be able to tell test
            // by test test if they should start the activity from the start or wait, and that
            // should get rid of this.
            getLauncherActivity().close();
            loadLauncherSync();
            final DigitalWellBeingToast toast = getToast();

            waitForLauncherCondition("Toast is not visible", launcher -> toast.getHasLimit());
            assertEquals("Toast text: ", "5 minutes left today", toast.getBannerText());

            // Unset time limit for app.
            runWithShellPermission(
                    () -> usageStatsManager.unregisterAppUsageLimitObserver(observerId));

            getLauncherActivity().goToState(LauncherState.NORMAL);
            assertFalse("Toast is visible", getToast().getHasLimit());
        } finally {
            runWithShellPermission(
                    () -> usageStatsManager.unregisterAppUsageLimitObserver(observerId));
        }
    }

    private DigitalWellBeingToast getToast() {
        getLauncherActivity().goToState(LauncherState.OVERVIEW);
        final TaskView task = getLauncherActivity().getOnceNotNull(
                "No latest task",
                launcher -> getLatestTask(launcher)
        );

        return getLauncherActivity().getFromLauncher(launcher -> {
            TaskContainer taskContainer = task.getFirstTaskContainer();
            assertNotNull(taskContainer);
            assertTrue("Latest task is not Calculator", calculatorPackage.equals(
                    taskContainer.getTask().getTopComponent().getPackageName()));
            return taskContainer.getDigitalWellBeingToast();
        });
    }

    private TaskView getLatestTask(Launcher launcher) {
        return launcher.<RecentsView>getOverviewPanel().getFirstTaskView();
    }

    private void runWithShellPermission(Runnable action) {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
        try {
            action.run();
        } finally {
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
    }
}
