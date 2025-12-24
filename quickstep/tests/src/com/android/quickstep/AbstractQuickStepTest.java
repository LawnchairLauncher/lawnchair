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

import static com.android.quickstep.fallback.RecentsStateUtilsKt.hasEquivalentRecentsState;
import static com.android.quickstep.fallback.RecentsStateUtilsKt.toLauncherState;

import static org.junit.Assert.assertTrue;

import androidx.annotation.Nullable;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;

import com.android.launcher3.LauncherState;
import com.android.launcher3.tapl.LaunchedAppState;
import com.android.launcher3.tapl.TestHelpers;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.TestUtil;
import com.android.launcher3.util.Wait;
import com.android.launcher3.util.ui.AbstractLauncherUiTest;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.window.RecentsWindowFlags;
import com.android.quickstep.window.RecentsWindowManager;
import com.android.quickstep.window.RecentsWindowTracker;

import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Base class for all instrumentation tests that deal with Quickstep.
 */
public abstract class AbstractQuickStepTest
        extends AbstractLauncherUiTest<QuickstepLauncher, RecentsView<?, ?>> {

    @Override
    protected TestRule getRulesInsideActivityMonitor() {
        return RuleChain.
                outerRule(new NavigationModeSwitchRule(mLauncher)).
                around(new TaskbarModeSwitchRule(mLauncher)).
                around(super.getRulesInsideActivityMonitor());
    }

    @Override
    protected void onLauncherActivityClose(QuickstepLauncher launcher) {
        super.onLauncherActivityClose(launcher);
        if (RecentsWindowFlags.enableLauncherOverviewInWindow.isTrue()) {
            executeOnRecentsWindowIfPresent(RecentsWindowManager::hideRecentsWindow);
        }
        RecentsView recentsView = launcher.getOverviewPanel();
        if (recentsView != null) {
            recentsView.finishRecentsAnimation(false /* toHome */, null);
        }
    }

    @Nullable
    private RecentsWindowManager getRecentsWindowManager() {
        RecentsWindowTracker recentsWindowTracker =
                RecentsWindowTracker.REPOSITORY_INSTANCE.get(mTargetContext).get(mDisplayId);
        return recentsWindowTracker == null ? null : recentsWindowTracker.getCreatedContext();
    }

    // Cannot be used in TaplTests after injecting any gesture using Tapl because this can hide
    // flakiness.
    protected void waitForRecentsWindowCondition(String
            message, Function<RecentsWindowManager, Boolean> condition) {
        waitForRecentsWindowCondition(message, condition, TestUtil.DEFAULT_UI_TIMEOUT);
    }

    // Cannot be used in TaplTests after injecting any gesture using Tapl because this can hide
    // flakiness.
    protected void waitForRecentsWindowCondition(
            String message, Function<RecentsWindowManager, Boolean> condition, long timeout) {
        verifyKeyguardInvisible();
        if (!TestHelpers.isInLauncherProcess()) return;
        Wait.atMost(message, () -> getFromRecentsWindow(condition), mLauncher, timeout);
    }

    protected <T> T getFromRecentsWindowIfPresent(Function<RecentsWindowManager, T> f) {
        if (!TestHelpers.isInLauncherProcess()) return null;
        return getFromRecentsWindow(recentsWindowManager ->
                recentsWindowManager == null ? null : f.apply(recentsWindowManager));
    }

    protected <T> T getFromRecentsWindow(Function<RecentsWindowManager, T> f) {
        if (!TestHelpers.isInLauncherProcess()) return null;
        return getOnUiThread(() -> f.apply(getRecentsWindowManager()));
    }

    protected void executeOnRecentsWindowIfPresent(Consumer<RecentsWindowManager> f) {
        if (!TestHelpers.isInLauncherProcess()) return;
        getFromRecentsWindowIfPresent(recentsWindowManager -> {
            f.accept(recentsWindowManager);
            return null;
        });
    }

    @Override
    protected boolean isInState(Supplier<LauncherState> state) {
        if (!TestHelpers.isInLauncherProcess()) return true;
        if (!RecentsWindowFlags.enableLauncherOverviewInWindow.isTrue()
                || !hasEquivalentRecentsState(state.get())) {
            return super.isInState(state);
        }
        return getFromRecentsWindow(recentsWindowManager ->
                recentsWindowManager != null && toLauncherState(
                        recentsWindowManager.getStateManager().getState()) == state.get());
    }

    @Override
    protected void waitForState(
            boolean forInitialization, String message, Supplier<LauncherState> state) {
        if (!TestHelpers.isInLauncherProcess()) return;
        if (!RecentsWindowFlags.enableLauncherOverviewInWindow.isTrue()
                || !hasEquivalentRecentsState(state.get())
                || (forInitialization
                && getRecentsWindowManager() == null)) {
            super.waitForState(forInitialization, message, state);
            return;
        }
        waitForRecentsWindowCondition(message, recentsWindowManager ->
                recentsWindowManager != null && toLauncherState(
                        recentsWindowManager.getStateManager().getState()) == state.get());
    }

    @Override
    @Nullable
    protected RecentsView getOverviewPanel() {
        if (!TestHelpers.isInLauncherProcess()) return null;
        if (!RecentsWindowFlags.enableLauncherOverviewInWindow.isTrue()) {
            return super.getOverviewPanel();
        }
        return getFromRecentsWindowIfPresent(RecentsWindowManager::getOverviewPanel);
    }

    @Override
    protected boolean useNullOverview() {
        return super.useNullOverview()
                && !RecentsWindowFlags.enableLauncherOverviewInWindow.isTrue();
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
