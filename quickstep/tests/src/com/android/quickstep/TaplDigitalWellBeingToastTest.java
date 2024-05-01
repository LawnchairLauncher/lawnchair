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

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.PendingIntent;
import android.app.usage.UsageStatsManager;
import android.content.Intent;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.Launcher;
import com.android.quickstep.views.DigitalWellBeingToast;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplDigitalWellBeingToastTest extends AbstractQuickStepTest {
    private static final String CALCULATOR_PACKAGE =
            resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR);

    @Test
    public void testToast() throws Exception {
        startAppFast(CALCULATOR_PACKAGE);

        final UsageStatsManager usageStatsManager =
                mTargetContext.getSystemService(UsageStatsManager.class);
        final int observerId = 0;

        try {
            final String[] packages = new String[]{CALCULATOR_PACKAGE};

            // Set time limit for app.
            runWithShellPermission(() ->
                    usageStatsManager.registerAppUsageLimitObserver(observerId, packages,
                            Duration.ofSeconds(600), Duration.ofSeconds(300),
                            PendingIntent.getActivity(mTargetContext, -1, new Intent()
                                            .setPackage(mTargetContext.getPackageName()),
                                    PendingIntent.FLAG_MUTABLE)));

            mLauncher.goHome();
            final DigitalWellBeingToast toast = getToast();

            waitForLauncherCondition("Toast is not visible", launcher -> toast.hasLimit());
            assertEquals("Toast text: ", "5 minutes left today", toast.getText());

            // Unset time limit for app.
            runWithShellPermission(
                    () -> usageStatsManager.unregisterAppUsageLimitObserver(observerId));

            mLauncher.goHome();
            assertFalse("Toast is visible", getToast().hasLimit());
        } finally {
            runWithShellPermission(
                    () -> usageStatsManager.unregisterAppUsageLimitObserver(observerId));
        }
    }

    private DigitalWellBeingToast getToast() {
        mLauncher.getWorkspace().switchToOverview();
        final TaskView task = getOnceNotNull("No latest task", launcher -> getLatestTask(launcher));

        return getFromLauncher(launcher -> {
            assertTrue("Latest task is not Calculator", CALCULATOR_PACKAGE.equals(
                    task.getFirstTask().getTopComponent().getPackageName()));
            return task.getDigitalWellBeingToast();
        });
    }

    private TaskView getLatestTask(Launcher launcher) {
        return launcher.<RecentsView>getOverviewPanel().getTaskViewAt(0);
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
