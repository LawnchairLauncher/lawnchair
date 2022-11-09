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

import com.android.launcher3.Launcher;
import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.tapl.LauncherInstrumentation.ContainerType;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.quickstep.views.RecentsView;

import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

/**
 * Base class for all instrumentation tests that deal with Quickstep.
 */
public abstract class AbstractQuickStepTest extends AbstractLauncherUiTest {
    public static final boolean ENABLE_SHELL_TRANSITIONS =
            SystemProperties.getBoolean("persist.wm.debug.shell_transit", false);
    @Override
    protected TestRule getRulesInsideActivityMonitor() {
        return RuleChain.
                outerRule(new NavigationModeSwitchRule(mLauncher)).
                around(new TaskbarModeSwitchRule(mLauncher)).
                around(super.getRulesInsideActivityMonitor());
    }

    @Override
    protected void onLauncherActivityClose(Launcher launcher) {
        RecentsView recentsView = launcher.getOverviewPanel();
        if (recentsView != null) {
            recentsView.finishRecentsAnimation(false /* toRecents */, null);
        }
    }

    @Override
    protected void checkLauncherState(Launcher launcher, ContainerType expectedContainerType,
            boolean isResumed, boolean isStarted) {
        if (ENABLE_SHELL_TRANSITIONS || !isInLiveTileMode(launcher, expectedContainerType)) {
            super.checkLauncherState(launcher, expectedContainerType, isResumed, isStarted);
        } else {
            assertTrue("[Live Tile] hasBeenResumed() == isStarted(), hasBeenResumed(): "
                            + isResumed, isResumed != isStarted);
        }
    }

    @Override
    protected void checkLauncherStateInOverview(Launcher launcher,
            ContainerType expectedContainerType, boolean isStarted, boolean isResumed) {
        if (ENABLE_SHELL_TRANSITIONS || !isInLiveTileMode(launcher, expectedContainerType)) {
            super.checkLauncherStateInOverview(launcher, expectedContainerType, isStarted,
                    isResumed);
        } else {
            assertTrue(
                    "[Live Tile] Launcher is not started or has been resumed in state: "
                            + expectedContainerType,
                    isStarted && !isResumed);
        }
    }

    private boolean isInLiveTileMode(Launcher launcher,
            LauncherInstrumentation.ContainerType expectedContainerType) {
        if (expectedContainerType != LauncherInstrumentation.ContainerType.OVERVIEW) {
            return false;
        }

        RecentsView recentsView = launcher.getOverviewPanel();
        return recentsView.getSizeStrategy().isInLiveTileMode()
                && recentsView.getRunningTaskViewId() != -1;
    }
}
