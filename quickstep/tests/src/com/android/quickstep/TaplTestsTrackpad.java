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

import static com.android.quickstep.NavigationModeSwitchRule.Mode.ZERO_BUTTON;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.tapl.LauncherInstrumentation.TrackpadGestureType;
import com.android.launcher3.tapl.Workspace;
import com.android.launcher3.ui.PortraitLandscapeRunner.PortraitLandscape;
import com.android.quickstep.NavigationModeSwitchRule.NavigationModeSwitch;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplTestsTrackpad extends AbstractQuickStepTest {

    private static final String READ_DEVICE_CONFIG_PERMISSION =
            "android.permission.READ_DEVICE_CONFIG";

    @After
    public void tearDown() {
        mLauncher.setTrackpadGestureType(TrackpadGestureType.NONE);
    }

    @Test
    @PortraitLandscape
    @NavigationModeSwitch
    public void goHome() throws Exception {
        assumeTrue(mLauncher.isTablet());

        mLauncher.setTrackpadGestureType(TrackpadGestureType.THREE_FINGER);
        startTestActivity(2);
        mLauncher.goHome();
    }

    @Test
    @PortraitLandscape
    // TODO(b/291944684): Support back in 3-button mode. It requires triggering the logic to enable
    //  trackpad gesture back in SysUI. Normally it's triggered by the attachment of a trackpad. We
    //  need to figure out a way to emulate that in the test, or bypass the logic altogether.
    @NavigationModeSwitch(mode = ZERO_BUTTON)
    public void pressBack() throws Exception {
        assumeTrue(mLauncher.isTablet());
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        try {
            instrumentation.getUiAutomation().adoptShellPermissionIdentity(
                    READ_DEVICE_CONFIG_PERMISSION);
            mLauncher.setTrackpadGestureType(TrackpadGestureType.THREE_FINGER);

            startTestActivity(2);
            mLauncher.getLaunchedAppState().pressBackToWorkspace();
        } finally {
            instrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    @PortraitLandscape
    @NavigationModeSwitch
    public void switchToOverview() throws Exception {
        assumeTrue(mLauncher.isTablet());

        mLauncher.setTrackpadGestureType(TrackpadGestureType.THREE_FINGER);
        startTestActivity(2);
        mLauncher.goHome().switchToOverview();
    }

    @Test
    @PortraitLandscape
    @NavigationModeSwitch
    public void testAllAppsFromHome() throws Exception {
        assumeTrue(mLauncher.isTablet());

        mLauncher.setTrackpadGestureType(TrackpadGestureType.TWO_FINGER);
        assertNotNull("switchToAllApps() returned null",
                mLauncher.getWorkspace().switchToAllApps());
    }

    @Test
    @NavigationModeSwitch
    @PortraitLandscape
    public void testQuickSwitchFromHome() throws Exception {
        assumeTrue(mLauncher.isTablet());

        startTestActivity(2);
        Workspace workspace = mLauncher.goHome();
        mLauncher.setTrackpadGestureType(TrackpadGestureType.FOUR_FINGER);
        workspace.quickSwitchToPreviousApp();
        assertTestActivityIsRunning(2,
                "The most recent task is not running after quick switching from home");
        getAndAssertLaunchedApp();
    }
}
