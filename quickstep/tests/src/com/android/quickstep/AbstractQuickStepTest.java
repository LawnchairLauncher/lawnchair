/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.quickstep;

import static org.junit.Assert.assertTrue;

import android.os.SystemProperties;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;

import com.android.launcher3.tapl.LaunchedAppState;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.quickstep.views.RecentsView;

import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

/**
 * Base class for all instrumentation tests that deal with Quickstep.
 */
public abstract class AbstractQuickStepTest extends AbstractLauncherUiTest<QuickstepLauncher> {
    public static final boolean ENABLE_SHELL_TRANSITIONS =
            SystemProperties.getBoolean("persist.wm.debug.shell_transit", true);
    @Override
    protected TestRule getRulesInsideActivityMonitor() {
        return RuleChain.
                outerRule(new NavigationModeSwitchRule(mLauncher)).
                around(new TaskbarModeSwitchRule(mLauncher)).
                around(super.getRulesInsideActivityMonitor());
    }

    @Override
    protected void onLauncherActivityClose(QuickstepLauncher launcher) {
        RecentsView recentsView = launcher.getOverviewPanel();
        if (recentsView != null) {
            recentsView.finishRecentsAnimation(false /* toRecents */, null);
        }
    }

    protected void assertTestActivityIsRunning(int activityNumber, String message) {
        assertTrue(message, mDevice.wait(
                Until.hasObject(By.pkg(getAppPackageName()).text("TestActivity" + activityNumber)),
                DEFAULT_UI_TIMEOUT));
    }

    protected LaunchedAppState getAndAssertLaunchedApp() {
        final LaunchedAppState launchedAppState = mLauncher.getLaunchedAppState();
        executeOnLauncher(launcher -> assertTrue(
                "Launcher activity is the top activity; expecting another activity to be the top "
                        + "one",
                isInLaunchedApp(launcher)));
        return launchedAppState;
    }
}
