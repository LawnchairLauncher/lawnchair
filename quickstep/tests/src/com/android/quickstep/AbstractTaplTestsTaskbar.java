/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static junit.framework.TestCase.assertEquals;

import android.content.Context;
import android.content.Intent;

import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.tapl.Taskbar;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.LauncherLayoutBuilder;
import com.android.launcher3.util.TestUtil;

import org.junit.After;
import org.junit.Assume;

import java.util.List;

public class AbstractTaplTestsTaskbar extends AbstractQuickStepTest {

    protected static final String TEST_APP_PACKAGE =
            getInstrumentation().getContext().getPackageName();
    protected static final String CALCULATOR_APP_PACKAGE =
            resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR);

    protected AutoCloseable mLauncherLayout;
    protected boolean mTaskbarWasInTransientMode;


    @Override
    public void setUp() throws Exception {
        Assume.assumeTrue(mLauncher.isTablet());
        super.setUp();

        LauncherLayoutBuilder layoutBuilder = new LauncherLayoutBuilder().atHotseat(0).putApp(
                "com.google.android.apps.nexuslauncher.tests",
                "com.android.launcher3.testcomponent.BaseTestingActivity");
        mLauncherLayout = TestUtil.setLauncherDefaultLayout(mTargetContext, layoutBuilder);
        AbstractLauncherUiTest.initialize(this);
        startAppFast(CALCULATOR_APP_PACKAGE);
        mLauncher.enableBlockTimeout(true);
        mLauncher.showTaskbarIfHidden();
    }

    @After
    public void tearDown() throws Exception {
        mLauncher.enableBlockTimeout(false);
        if (mLauncherLayout != null) {
            mLauncherLayout.close();
        }
    }

    protected static boolean isTaskbarInTransientMode(Context context) {
        return DisplayController.isTransientTaskbar(context);
    }

    protected Taskbar getTaskbar() {
        Taskbar taskbar = mLauncher.getLaunchedAppState().getTaskbar();
        List<String> taskbarIconNames = taskbar.getIconNames();
        List<String> hotseatIconNames = mLauncher.getHotseatIconNames();

        assertEquals("Taskbar and hotseat icon counts do not match",
                taskbarIconNames.size(), hotseatIconNames.size());

        for (int i = 0; i < taskbarIconNames.size(); i++) {
            assertEquals("Taskbar and Hotseat icons do not match",
                    taskbarIconNames, hotseatIconNames);
        }

        return taskbar;
    }

    protected static void setTaskbarMode(LauncherInstrumentation launcher,
            boolean expectTransientTaskbar) {
        launcher.enableTransientTaskbar(expectTransientTaskbar);
        launcher.recreateTaskbar();
        launcher.checkForAnomaly(true, true);
        AbstractLauncherUiTest.checkDetectedLeaks(launcher, true);
    }
}
