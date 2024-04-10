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
package com.android.launcher3.allapps;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.tapl.AppIcon;
import com.android.launcher3.tapl.HomeAllApps;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.ui.PortraitLandscapeRunner.PortraitLandscape;

import org.junit.Test;

/**
 * The test runs in Out of process (Oop) and in process.
 * Makes sure the basic behaviors of Icons on AllApps are working.
 */
public class TaplAllAppsIconsWorkingTest extends AbstractLauncherUiTest<Launcher> {

    /**
     * Makes sure we can launch an icon from All apps
     */
    @Test
    @PortraitLandscape
    public void testAppIconLaunchFromAllAppsFromHome() {
        final HomeAllApps allApps = mLauncher.getWorkspace().switchToAllApps();
        assertTrue("Launcher internal state is not All Apps",
                isInState(() -> LauncherState.ALL_APPS));

        allApps.freeze();
        try {
            final AppIcon app = allApps.getAppIcon("TestActivity7");
            assertNotNull("AppIcon.launch returned null", app.launch(getAppPackageName()));
            executeOnLauncher(launcher -> assertTrue(
                    "Launcher activity is the top activity; expecting another activity to be the "
                            + "top one",
                    isInLaunchedApp(launcher)));
        } finally {
            allApps.unfreeze();
        }
        mLauncher.goHome();
    }
}
