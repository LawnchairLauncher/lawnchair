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
import com.android.launcher3.tapl.TestHelpers;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.TestUtil;
import com.android.launcher3.util.Wait;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.fallback.window.RecentsWindowManager;
import com.android.quickstep.views.RecentsView;

import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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

    // Cannot be used in TaplTests between a Tapl call injecting a gesture and a tapl call
    // expecting the results of that gesture because the wait can hide flakeness.
    protected void waitForRecentsWindowState(String message, Supplier<RecentsState> state) {
        waitForRecentsWindowCondition(message, recentsWindow ->
                recentsWindow.getStateManager().getCurrentStableState() == state.get());
    }

    // Cannot be used in TaplTests after injecting any gesture using Tapl because this can hide
    // flakiness.
    protected void waitForRecentsWindowCondition(String
            message, Function<RecentsWindowManager, Boolean> condition) {
        waitForRecentsWindowCondition(message, condition, TestUtil.DEFAULT_UI_TIMEOUT);
    }

    protected <T> T getFromRecentsWindow(Function<RecentsWindowManager, T> f) {
        if (!TestHelpers.isInLauncherProcess()) return null;
        return getOnUiThread(() -> {
            RecentsWindowManager recentsWindowManager =
                    RecentsWindowManager.getRecentsWindowTracker().getCreatedContext();
            return recentsWindowManager != null ? f.apply(recentsWindowManager) : null;
        });
    }

    // Cannot be used in TaplTests after injecting any gesture using Tapl because this can hide
    // flakiness.
    protected void waitForRecentsWindowCondition(
            String message, Function<RecentsWindowManager, Boolean> condition, long timeout) {
        verifyKeyguardInvisible();
        if (!TestHelpers.isInLauncherProcess()) return;
        Wait.atMost(message, () -> getFromRecentsWindow(condition), mLauncher, timeout);
    }

    protected boolean isInRecentsWindowState(Supplier<RecentsState> state) {
        if (!TestHelpers.isInLauncherProcess()) return true;
        return getFromRecentsWindow(
                recentsWindow -> recentsWindow.getStateManager().getState() == state.get());
    }

    protected void assertTestActivityIsRunning(int activityNumber, String message) {
        assertTrue(message, mDevice.wait(
                Until.hasObject(By.pkg(getAppPackageName()).text("TestActivity" + activityNumber)),
                TestUtil.DEFAULT_UI_TIMEOUT));
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
