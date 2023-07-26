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

import static android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.android.launcher3.tapl.LauncherInstrumentation.WAIT_TIME_MS;
import static com.android.launcher3.tapl.TestHelpers.getHomeIntentInPackage;
import static com.android.launcher3.tapl.TestHelpers.getLauncherInMyProcess;
import static com.android.launcher3.ui.AbstractLauncherUiTest.DEFAULT_ACTIVITY_TIMEOUT;
import static com.android.launcher3.ui.AbstractLauncherUiTest.DEFAULT_BROADCAST_TIMEOUT_SECS;
import static com.android.launcher3.ui.AbstractLauncherUiTest.DEFAULT_UI_TIMEOUT;
import static com.android.launcher3.ui.AbstractLauncherUiTest.resolveSystemApp;
import static com.android.launcher3.ui.AbstractLauncherUiTest.startAppFast;
import static com.android.launcher3.ui.AbstractLauncherUiTest.startTestActivity;
import static com.android.launcher3.ui.TaplTestsLauncher3.getAppPackageName;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.rule.ShellCommandRule.disableHeadsUpNotification;
import static com.android.launcher3.util.rule.ShellCommandRule.getLauncherCommand;
import static com.android.launcher3.util.rule.TestStabilityRule.LOCAL;
import static com.android.launcher3.util.rule.TestStabilityRule.PLATFORM_POSTSUBMIT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.RemoteException;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.launcher3.Utilities;
import com.android.launcher3.tapl.BaseOverview;
import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.tapl.OverviewTask;
import com.android.launcher3.tapl.TestHelpers;
import com.android.launcher3.testcomponent.TestCommandReceiver;
import com.android.launcher3.util.Wait;
import com.android.launcher3.util.rule.FailureWatcher;
import com.android.launcher3.util.rule.SamplerRule;
import com.android.launcher3.util.rule.ScreenRecordRule;
import com.android.launcher3.util.rule.TestStabilityRule;
import com.android.launcher3.util.rule.ViewCaptureRule;
import com.android.quickstep.views.RecentsView;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class FallbackRecentsTest {

    private static final String FALLBACK_LAUNCHER_TITLE = "Test launcher";

    private final UiDevice mDevice;
    private final LauncherInstrumentation mLauncher;
    private final ActivityInfo mOtherLauncherActivity;

    @Rule
    public final TestRule mDisableHeadsUpNotification = disableHeadsUpNotification();

    @Rule
    public final TestRule mSetLauncherCommand;

    @Rule
    public final TestRule mOrderSensitiveRules;

    @Rule
    public ScreenRecordRule mScreenRecordRule = new ScreenRecordRule();

    public FallbackRecentsTest() throws RemoteException {
        Instrumentation instrumentation = getInstrumentation();
        Context context = instrumentation.getContext();
        mDevice = UiDevice.getInstance(instrumentation);
        mDevice.setOrientationNatural();
        mLauncher = new LauncherInstrumentation();
        mLauncher.enableDebugTracing();
        // b/143488140
        //mLauncher.enableCheckEventsForSuccessfulGestures();

        if (TestHelpers.isInLauncherProcess()) {
            Utilities.enableRunningInTestHarnessForTests();
        }

        final ViewCaptureRule viewCaptureRule = new ViewCaptureRule(
                RecentsActivity.ACTIVITY_TRACKER::getCreatedActivity);
        mOrderSensitiveRules = RuleChain
                .outerRule(new SamplerRule())
                .around(new NavigationModeSwitchRule(mLauncher))
                .around(new FailureWatcher(mLauncher, viewCaptureRule::getViewCaptureData))
                .around(viewCaptureRule);

        mOtherLauncherActivity = context.getPackageManager().queryIntentActivities(
                getHomeIntentInPackage(context),
                MATCH_DISABLED_COMPONENTS).get(0).activityInfo;

        mSetLauncherCommand = (base, desc) -> new Statement() {
            @Override
            public void evaluate() throws Throwable {
                TestCommandReceiver.callCommand(TestCommandReceiver.ENABLE_TEST_LAUNCHER);
                OverviewUpdateHandler updateHandler =
                        MAIN_EXECUTOR.submit(OverviewUpdateHandler::new).get();
                UiDevice.getInstance(getInstrumentation()).executeShellCommand(
                        getLauncherCommand(mOtherLauncherActivity));
                updateHandler.mChangeCounter
                        .await(DEFAULT_BROADCAST_TIMEOUT_SECS, TimeUnit.SECONDS);
                try {
                    base.evaluate();
                } finally {
                    MAIN_EXECUTOR.submit(updateHandler::destroy).get();
                    TestCommandReceiver.callCommand(TestCommandReceiver.DISABLE_TEST_LAUNCHER);
                    UiDevice.getInstance(getInstrumentation()).executeShellCommand(
                            getLauncherCommand(getLauncherInMyProcess()));
                    // b/143488140
                    mDevice.pressHome();
                    mDevice.waitForIdle();
                }
            }
        };
        if (TestHelpers.isInLauncherProcess()) {
            mLauncher.setSystemHealthSupplier(startTime -> TestCommandReceiver.callCommand(
                    TestCommandReceiver.GET_SYSTEM_HEALTH_MESSAGE, startTime.toString()).
                    getString("result"));
        }
    }

    @Before
    public void setUp() {
        mLauncher.onTestStart();
    }

    @After
    public void tearDown() {
        try {
            // Limits UI tests affecting tests running after them.
            AbstractQuickStepTest.checkDetectedLeaks(mLauncher);
        } finally {
            mLauncher.onTestFinish();
        }
    }

    // b/143488140
    //@NavigationModeSwitch
    @TestStabilityRule.Stability(flavors = LOCAL | PLATFORM_POSTSUBMIT) // b/266606727
    @Test
    public void goToOverviewFromHome() {
        mDevice.pressHome();
        assertTrue("Fallback Launcher not visible", mDevice.wait(Until.hasObject(By.pkg(
                mOtherLauncherActivity.packageName).text(FALLBACK_LAUNCHER_TITLE)), WAIT_TIME_MS));

        mLauncher.getLaunchedAppState().switchToOverview();
    }

    // b/143488140
    //@NavigationModeSwitch
    @Ignore
    @Test
    public void goToOverviewFromApp() {
        startAppFast(resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR));

        mLauncher.getLaunchedAppState().switchToOverview();
    }

    protected void executeOnRecents(Consumer<RecentsActivity> f) {
        getFromRecents(r -> {
            f.accept(r);
            return true;
        });
    }

    protected <T> T getFromRecents(Function<RecentsActivity, T> f) {
        if (!TestHelpers.isInLauncherProcess()) return null;
        Object[] result = new Object[1];
        Wait.atMost("Failed to get from recents", () -> MAIN_EXECUTOR.submit(() -> {
            RecentsActivity activity = RecentsActivity.ACTIVITY_TRACKER.getCreatedActivity();
            if (activity == null) {
                return false;
            }
            result[0] = f.apply(activity);
            return true;
        }).get(), DEFAULT_UI_TIMEOUT, mLauncher);
        return (T) result[0];
    }

    private BaseOverview pressHomeAndGoToOverview() {
        mDevice.pressHome();
        return mLauncher.getLaunchedAppState().switchToOverview();
    }

    // b/143488140
    //@NavigationModeSwitch
    @TestStabilityRule.Stability(flavors = LOCAL | PLATFORM_POSTSUBMIT) // b/266606727
    @Test
    public void testOverview() {
        startAppFast(getAppPackageName());
        startAppFast(resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR));
        startTestActivity(2);
        Wait.atMost("Expected three apps in the task list",
                () -> mLauncher.getRecentTasks().size() >= 3, DEFAULT_ACTIVITY_TIMEOUT, mLauncher);

        BaseOverview overview = mLauncher.getLaunchedAppState().switchToOverview();
        executeOnRecents(recents -> {
            assertTrue("Don't have at least 3 tasks", getTaskCount(recents) >= 3);
        });

        // Test flinging forward and backward.
        overview.flingForward();
        final Integer currentTaskAfterFlingForward = getFromRecents(this::getCurrentOverviewPage);
        executeOnRecents(recents -> assertTrue("Current task in Overview is still 0",
                currentTaskAfterFlingForward > 0));

        overview.flingBackward();
        executeOnRecents(recents -> assertTrue("Flinging back in Overview did nothing",
                getCurrentOverviewPage(recents) < currentTaskAfterFlingForward));

        // Test opening a task.
        overview = pressHomeAndGoToOverview();

        OverviewTask task = overview.getCurrentTask();
        assertNotNull("overview.getCurrentTask() returned null (1)", task);
        assertNotNull("OverviewTask.open returned null", task.open());
        assertTrue("Test activity didn't open from Overview", TestHelpers.wait(Until.hasObject(
                By.pkg(getAppPackageName()).text("TestActivity2")),
                DEFAULT_UI_TIMEOUT));


        // Test dismissing a task.
        overview = pressHomeAndGoToOverview();
        final Integer numTasks = getFromRecents(this::getTaskCount);
        task = overview.getCurrentTask();
        assertNotNull("overview.getCurrentTask() returned null (2)", task);
        task.dismiss();
        executeOnRecents(
                recents -> assertEquals("Dismissing a task didn't remove 1 task from Overview",
                        numTasks - 1, getTaskCount(recents)));

        // Test dismissing all tasks.
        pressHomeAndGoToOverview().dismissAllTasks();
        assertTrue("Fallback Launcher not visible", TestHelpers.wait(Until.hasObject(By.pkg(
                mOtherLauncherActivity.packageName).text(FALLBACK_LAUNCHER_TITLE)), WAIT_TIME_MS));
    }

    private int getCurrentOverviewPage(RecentsActivity recents) {
        return recents.<RecentsView>getOverviewPanel().getCurrentPage();
    }

    private int getTaskCount(RecentsActivity recents) {
        return recents.<RecentsView>getOverviewPanel().getTaskViewCount();
    }

    private class OverviewUpdateHandler {

        final RecentsAnimationDeviceState mRads;
        final OverviewComponentObserver mObserver;
        final CountDownLatch mChangeCounter;

        OverviewUpdateHandler() {
            Context ctx = getInstrumentation().getTargetContext();
            mRads = new RecentsAnimationDeviceState(ctx);
            mObserver = new OverviewComponentObserver(ctx, mRads);
            mChangeCounter = new CountDownLatch(1);
            if (mObserver.getHomeIntent().getComponent()
                    .getPackageName().equals(mOtherLauncherActivity.packageName)) {
                // Home already same
                mChangeCounter.countDown();
            } else {
                mObserver.setOverviewChangeListener(b -> mChangeCounter.countDown());
            }
        }

        void destroy() {
            mObserver.onDestroy();
            mRads.destroy();
        }
    }
}
