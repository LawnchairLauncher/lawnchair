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

import android.content.Intent;

import com.android.launcher3.ui.TaplTestsLauncher3;
import com.android.launcher3.util.rule.TestStabilityRule;
import com.android.quickstep.TaskbarModeSwitchRule.TaskbarModeSwitch;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class TaplTestsSplitscreen extends AbstractQuickStepTest {
    private static final String CALCULATOR_APP_NAME = "Calculator";
    private static final String CALCULATOR_APP_PACKAGE =
            resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR);

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        TaplTestsLauncher3.initialize(this);

        mLauncher.getWorkspace()
                .deleteAppIcon(mLauncher.getWorkspace().getHotseatAppIcon(0))
                .switchToAllApps()
                .getAppIcon(CALCULATOR_APP_NAME)
                .dragToHotseat(0);

        startAppFast(CALCULATOR_APP_PACKAGE);
        if (mLauncher.isTablet()) {
            mLauncher.enableBlockTimeout(true);
            mLauncher.showTaskbarIfHidden();
        }
    }

    @After
    public void tearDown() {
        if (mLauncher.isTablet()) {
            mLauncher.enableBlockTimeout(false);
        }
    }

    @Test
    // TODO (b/270201357): When this test is proven stable, remove this TestStabilityRule and
    // introduce into presubmit as well.
    @TestStabilityRule.Stability(
            flavors = TestStabilityRule.LOCAL | TestStabilityRule.PLATFORM_POSTSUBMIT)
    @PortraitLandscape
    @TaskbarModeSwitch
    public void testSplitAppFromHomeWithItself() throws Exception {
        Assume.assumeTrue(mLauncher.isTablet());

        mLauncher.goHome()
                .switchToAllApps()
                .getAppIcon(CALCULATOR_APP_NAME)
                .openMenu()
                .getSplitScreenMenuItem()
                .click();

        mLauncher.getLaunchedAppState()
                .getTaskbar()
                .getAppIcon(CALCULATOR_APP_NAME)
                .launchIntoSplitScreen();
    }
}
