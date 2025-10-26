/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.ui;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Process;
import android.system.OsConstants;
import android.util.Log;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Until;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.tapl.TestHelpers;
import com.android.launcher3.testcomponent.TestCommandReceiver;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.util.TestUtil;
import com.android.launcher3.util.Wait;
import com.android.launcher3.util.rule.FailureWatcher;
import com.android.launcher3.util.rule.ShellCommandRule;
import com.android.launcher3.util.rule.TestIsolationRule;
import com.android.launcher3.util.rule.ViewCaptureRule;

import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Base class for all instrumentation tests providing various utility methods.
 */
public abstract class AbstractLauncherUiTest<LAUNCHER_TYPE extends Launcher>
        extends BaseLauncherTaplTest {

    private static final String TAG = "AbstractLauncherUiTest";

    protected LooperExecutor mMainThreadExecutor = MAIN_EXECUTOR;

    protected AbstractLauncherUiTest() {
        if (TestHelpers.isInLauncherProcess()) {
            Utilities.enableRunningInTestHarnessForTests();
            mLauncher.setSystemHealthSupplier(startTime -> TestCommandReceiver.callCommand(
                            TestCommandReceiver.GET_SYSTEM_HEALTH_MESSAGE, startTime.toString())
                    .getString("result"));
        }
    }

    /**
     * @deprecated call {@link #performInitialization} instead
     */
    @Deprecated
    public static void initialize(AbstractLauncherUiTest test) throws Exception {
        test.performInitialization();
    }

    @Override
    protected void performInitialization() {
        reinitializeLauncherData();
        mDevice.pressHome();
        // Check that we switched to home.
        mLauncher.getWorkspace();

        waitForLauncherCondition("Launcher didn't start", Objects::nonNull);
        waitForState("Launcher internal state didn't switch to Home",
                () -> LauncherState.NORMAL);
        waitForResumed("Launcher internal state is still Background");

        checkDetectedLeaks(mLauncher, true);
    }

    @Override
    protected TestRule getRulesInsideActivityMonitor() {
        final ViewCaptureRule viewCaptureRule = new ViewCaptureRule(
                Launcher.ACTIVITY_TRACKER::getCreatedContext);
        final RuleChain inner = RuleChain
                .outerRule(new PortraitLandscapeRunner<>(this))
                .around(new FailureWatcher(mLauncher, viewCaptureRule::getViewCaptureData))
                // .around(viewCaptureRule) // b/315482167
                .around(new TestIsolationRule(mLauncher, true));

        return TestHelpers.isInLauncherProcess()
                ? RuleChain.outerRule(ShellCommandRule.setDefaultLauncher()).around(inner)
                : inner;
    }

    /**
     * Runs the callback on the UI thread and returns the result.
     */
    protected <T> T getOnUiThread(final Callable<T> callback) {
        try {
            return mMainThreadExecutor.submit(callback).get(TestUtil.DEFAULT_UI_TIMEOUT,
                    TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            Log.e(TAG, "Timeout in getOnUiThread, sending SIGABRT", e);
            Process.sendSignal(Process.myPid(), OsConstants.SIGABRT);
            throw new RuntimeException(e);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    protected <T> T getFromLauncher(Function<LAUNCHER_TYPE, T> f) {
        if (!TestHelpers.isInLauncherProcess()) return null;
        return getOnUiThread(() -> f.apply(Launcher.ACTIVITY_TRACKER.getCreatedContext()));
    }

    protected void executeOnLauncher(Consumer<LAUNCHER_TYPE> f) {
        getFromLauncher(launcher -> {
            f.accept(launcher);
            return null;
        });
    }

    // Execute an action on Launcher, but forgive it when launcher is null.
    // Launcher can be null if teardown is happening after a failed setup step where launcher
    // activity failed to be created.
    protected void executeOnLauncherInTearDown(Consumer<LAUNCHER_TYPE> f) {
        executeOnLauncher(launcher -> {
            if (launcher != null) f.accept(launcher);
        });
    }

    // Cannot be used in TaplTests between a Tapl call injecting a gesture and a tapl call
    // expecting the results of that gesture because the wait can hide flakeness.
    protected void waitForState(String message, Supplier<LauncherState> state) {
        waitForLauncherCondition(message,
                launcher -> launcher.getStateManager().getCurrentStableState() == state.get());
    }

    // Cannot be used in TaplTests between a Tapl call injecting a gesture and a tapl call
    // expecting the results of that gesture because the wait can hide flakeness.
    protected void waitForStateTransitionToEnd(String message, Supplier<LauncherState> state) {
        waitForLauncherCondition(message,
                launcher -> launcher.getStateManager().isInStableState(state.get())
                        && !launcher.getStateManager().isInTransition());
    }

    protected void waitForResumed(String message) {
        waitForLauncherCondition(message, launcher -> launcher.hasBeenResumed());
    }

    // Cannot be used in TaplTests after injecting any gesture using Tapl because this can hide
    // flakiness.
    protected void waitForLauncherCondition(String
            message, Function<LAUNCHER_TYPE, Boolean> condition) {
        waitForLauncherCondition(message, condition, TestUtil.DEFAULT_UI_TIMEOUT);
    }

    // Cannot be used in TaplTests after injecting any gesture using Tapl because this can hide
    // flakiness.
    protected void waitForLauncherCondition(
            String message, Function<LAUNCHER_TYPE, Boolean> condition, long timeout) {
        verifyKeyguardInvisible();
        if (!TestHelpers.isInLauncherProcess()) return;
        Wait.atMost(message, () -> getFromLauncher(condition), mLauncher, timeout);
    }

    // Cannot be used in TaplTests after injecting any gesture using Tapl because this can hide
    // flakiness.
    protected <T> T getOnceNotNull(String message, Function<LAUNCHER_TYPE, T> f) {
        if (!TestHelpers.isInLauncherProcess()) return null;

        final Object[] output = new Object[1];
        Wait.atMost(message, () -> {
            final Object fromLauncher = getFromLauncher(f);
            output[0] = fromLauncher;
            return fromLauncher != null;
        }, mLauncher);
        return (T) output[0];
    }

    // Cannot be used in TaplTests after injecting any gesture using Tapl because this can hide
    // flakiness.
    protected void waitForLauncherCondition(
            String message,
            Runnable testThreadAction, Function<LAUNCHER_TYPE, Boolean> condition,
            long timeout) {
        if (!TestHelpers.isInLauncherProcess()) return;
        Wait.atMost(message, () -> {
            testThreadAction.run();
            return getFromLauncher(condition);
        }, mLauncher, timeout);
    }

    public static void startAppFast(String packageName) {
        startIntent(
                getInstrumentation().getContext().getPackageManager().getLaunchIntentForPackage(
                        packageName),
                By.pkg(packageName).depth(0),
                true /* newTask */);
    }

    public static void startTestActivity(String activityName, String activityLabel) {
        final String packageName = getAppPackageName();
        final Intent intent = getInstrumentation().getContext().getPackageManager().
                getLaunchIntentForPackage(packageName);
        intent.setComponent(new ComponentName(packageName,
                "com.android.launcher3.tests." + activityName));
        startIntent(intent, By.pkg(packageName).text(activityLabel),
                false /* newTask */);
    }

    public static void startTestActivity(int activityNumber) {
        startTestActivity("Activity" + activityNumber, "TestActivity" + activityNumber);
    }

    public static void startImeTestActivity() {
        final String packageName = getAppPackageName();
        final Intent intent = getInstrumentation().getContext().getPackageManager().
                getLaunchIntentForPackage(packageName);
        intent.setComponent(new ComponentName(packageName,
                "com.android.launcher3.testcomponent.ImeTestActivity"));
        startIntent(intent, By.pkg(packageName).text("ImeTestActivity"),
                false /* newTask */);
    }

    /** Starts ExcludeFromRecentsTestActivity, which has excludeFromRecents="true". */
    public static void startExcludeFromRecentsTestActivity() {
        final String packageName = getAppPackageName();
        final Intent intent = getInstrumentation().getContext().getPackageManager()
                .getLaunchIntentForPackage(packageName);
        intent.setComponent(new ComponentName(packageName,
                "com.android.launcher3.testcomponent.ExcludeFromRecentsTestActivity"));
        startIntent(intent, By.pkg(packageName).text("ExcludeFromRecentsTestActivity"),
                false /* newTask */);
    }

    private static void startIntent(Intent intent, BySelector selector, boolean newTask) {
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        if (newTask) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        } else {
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        }
        getInstrumentation().getTargetContext().startActivity(intent);
        assertTrue("App didn't start: " + selector,
                TestHelpers.wait(Until.hasObject(selector), TestUtil.DEFAULT_UI_TIMEOUT));

        // Wait for the Launcher to stop.
        final LauncherInstrumentation launcherInstrumentation = new LauncherInstrumentation();
        Wait.atMost("Launcher activity didn't stop",
                () -> !launcherInstrumentation.isLauncherActivityStarted(),
                launcherInstrumentation);
    }


    public static String resolveSystemApp(String category) {
        return resolveSystemAppInfo(category).packageName;
    }

    protected void closeLauncherActivity() {
        // Destroy Launcher activity.
        executeOnLauncher(launcher -> {
            if (launcher != null) {
                onLauncherActivityClose(launcher);
                launcher.finish();
            }
        });
        waitForLauncherCondition("Launcher still active", launcher -> launcher == null);
    }

    protected boolean isInLaunchedApp(LAUNCHER_TYPE launcher) {
        return launcher == null || !launcher.hasBeenResumed();
    }

    protected boolean isInState(Supplier<LauncherState> state) {
        if (!TestHelpers.isInLauncherProcess()) return true;
        return getFromLauncher(
                launcher -> launcher.getStateManager().getState() == state.get());
    }

    protected int getAllAppsScroll(LAUNCHER_TYPE launcher) {
        return launcher.getAppsView().getActiveRecyclerView().computeVerticalScrollOffset();
    }

    protected void onLauncherActivityClose(LAUNCHER_TYPE launcher) {
    }
}
