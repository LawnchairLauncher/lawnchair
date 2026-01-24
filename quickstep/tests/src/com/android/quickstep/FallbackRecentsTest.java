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
import static android.view.Display.DEFAULT_DISPLAY;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.android.launcher3.tapl.LauncherInstrumentation.WAIT_TIME_MS;
import static com.android.launcher3.tapl.TestHelpers.getHomeIntentInPackage;
import static com.android.launcher3.tapl.TestHelpers.getLauncherInMyProcess;
import static com.android.launcher3.ui.TaplTestsLauncher3Test.getAppPackageName;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.rule.ShellCommandRule.disableHeadsUpNotification;
import static com.android.launcher3.util.rule.ShellCommandRule.getLauncherCommand;
import static com.android.launcher3.util.ui.AbstractLauncherUiTest.DEFAULT_BROADCAST_TIMEOUT_SECS;
import static com.android.launcher3.util.ui.AbstractLauncherUiTest.resolveSystemApp;
import static com.android.launcher3.util.ui.AbstractLauncherUiTest.startAppFast;
import static com.android.launcher3.util.ui.AbstractLauncherUiTest.startTestActivity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.RemoteException;
import android.platform.test.rule.ExtendedLongPressTimeoutRule;

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
import com.android.launcher3.util.TestUtil;
import com.android.launcher3.util.Wait;
import com.android.launcher3.util.rule.FailureWatcher;
import com.android.launcher3.util.rule.SamplerRule;
import com.android.launcher3.util.rule.ScreenRecordRule;
import com.android.launcher3.util.rule.SkipAfterTimeOutRule;
import com.android.launcher3.util.rule.TestIsolationRule;
import com.android.launcher3.util.rule.TestStabilityRule;
import com.android.launcher3.util.ui.AbstractLauncherUiTest;
import com.android.quickstep.OverviewComponentObserver.OverviewChangeListener;
import com.android.quickstep.fallback.window.RecentsWindowFlags;
import com.android.quickstep.fallback.window.RecentsWindowManager;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class FallbackRecentsTest {

    private static final String FALLBACK_LAUNCHER_TITLE = "Test launcher";
    private static final Pattern COMPONENT_INFO_REGEX = Pattern.compile("ComponentInfo\\{(.*)\\}");

    private final UiDevice mDevice;
    private final LauncherInstrumentation mLauncher;
    private final ActivityInfo mOtherLauncherActivity;

    @Rule
    public final TestRule mDisableHeadsUpNotification = disableHeadsUpNotification();

    @Rule
    public final TestRule mOrderSensitiveRules;

    @Rule
    public ScreenRecordRule mScreenRecordRule = new ScreenRecordRule();

    @Rule
    public ExtendedLongPressTimeoutRule mLongPressTimeoutRule = new ExtendedLongPressTimeoutRule();

    @Rule(order = -1000) // This should be the outermost rule
    public SkipAfterTimeOutRule mSkipAfterTimeOutRule = new SkipAfterTimeOutRule();

    public FallbackRecentsTest() throws RemoteException {
        Instrumentation instrumentation = getInstrumentation();
        Context context = instrumentation.getContext();
        mDevice = UiDevice.getInstance(instrumentation);
        mDevice.setOrientationNatural();
        mLauncher = AbstractLauncherUiTest.createLauncherInstrumentation();
        mLauncher.enableDebugTracing();

        if (TestHelpers.isInLauncherProcess()) {
            Utilities.enableRunningInTestHarnessForTests();
        }

        final TestRule setLauncherCommand = (base, desc) -> new Statement() {
            @Override
            public void evaluate() throws Throwable {
                TestCommandReceiver.callCommand(TestCommandReceiver.ENABLE_TEST_LAUNCHER);
                OverviewUpdateHandler updateHandler =
                        MAIN_EXECUTOR.submit(OverviewUpdateHandler::new).get();
                UiDevice.getInstance(getInstrumentation()).executeShellCommand(
                        getLauncherCommand(mOtherLauncherActivity));
                updateHandler.mChangeCounter
                        .await(DEFAULT_BROADCAST_TIMEOUT_SECS, TimeUnit.SECONDS);
                mLauncher.setTestLauncherPackage(mOtherLauncherActivity.packageName);
                try {
                    base.evaluate();
                } finally {
                    MAIN_EXECUTOR.submit(updateHandler::destroy).get();
                    TestCommandReceiver.callCommand(TestCommandReceiver.DISABLE_TEST_LAUNCHER);
                    UiDevice.getInstance(getInstrumentation()).executeShellCommand(
                            getLauncherCommand(getLauncherInMyProcess()));
                    mLauncher.setTestLauncherPackage(null);
                    pressHomeAndWaitForOverviewClose();
                }
            }
        };

        mOrderSensitiveRules = RuleChain
                .outerRule(new SamplerRule())
                .around(new TestStabilityRule())
                .around(new NavigationModeSwitchRule(mLauncher))
                .around(new FailureWatcher(mLauncher))
                .around(new TestIsolationRule(mLauncher, false))
                .around(setLauncherCommand);

        mOtherLauncherActivity = context.getPackageManager().queryIntentActivities(
                getHomeIntentInPackage(context),
                MATCH_DISABLED_COMPONENTS).get(0).activityInfo;

        if (TestHelpers.isInLauncherProcess()) {
            mLauncher.setSystemHealthSupplier(startTime -> TestCommandReceiver.callCommand(
                    TestCommandReceiver.GET_SYSTEM_HEALTH_MESSAGE, startTime.toString()).
                    getString("result"));
        }
    }

    @Before
    public void setUp() {
        mLauncher.onTestStart();
        AbstractLauncherUiTest.onTestStart();
    }

    @After
    public void tearDown() {
        try {
            // Limits UI tests affecting tests running after them.
            AbstractQuickStepTest.checkDetectedLeaks(mLauncher, true);
        } finally {
            mLauncher.onTestFinish();
        }
    }

    @Test
    public void goToOverviewFromHome() {
        mDevice.pressHome();
        assertTrue("Fallback Launcher not visible", mDevice.wait(Until.hasObject(By.pkg(
                mOtherLauncherActivity.packageName).text(FALLBACK_LAUNCHER_TITLE)), WAIT_TIME_MS));

        mLauncher.getLaunchedAppState().switchToOverview();
    }

    //@NavigationModeSwitch
    @Test
    public void goToOverviewFromApp() {
        startAppFast(resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR));
        waitForRecentsClosed();

        mLauncher.getLaunchedAppState().switchToOverview();
    }

    protected void executeOnRecents(Consumer<RecentsViewContainer> f) {
        getFromRecents(r -> {
            f.accept(r);
            return true;
        });
    }

    protected <T> T getFromRecents(Function<RecentsViewContainer, T> f) {
        if (!TestHelpers.isInLauncherProcess()) return null;
        Object[] result = new Object[1];
        Wait.atMost("Failed to get from recents", () -> MAIN_EXECUTOR.submit(() -> {
            RecentsViewContainer recentsViewContainer =
                    RecentsWindowFlags.enableFallbackOverviewInWindow.isTrue()
                            ? RecentsWindowManager.getRecentsWindowTracker().getCreatedContext()
                            : RecentsActivity.ACTIVITY_TRACKER.getCreatedContext();
            if (recentsViewContainer == null) {
                return false;
            }
            result[0] = f.apply(recentsViewContainer);
            return true;
        }).get(), mLauncher);
        return (T) result[0];
    }

    private BaseOverview pressHomeAndGoToOverview() {
        pressHomeAndWaitForOverviewClose();
        return mLauncher.getLaunchedAppState().switchToOverview();
    }

    private void pressHomeAndWaitForOverviewClose() {
        mDevice.pressHome();
        waitForRecentsClosed();
    }

    private void waitForRecentsClosed() {
        try {
            final boolean isRecentsContainerNUll = MAIN_EXECUTOR.submit(() -> {
                RecentsViewContainer recentsViewContainer =
                        RecentsWindowFlags.enableFallbackOverviewInWindow.isTrue()
                                ? RecentsWindowManager.getRecentsWindowTracker().getCreatedContext()
                                : RecentsActivity.ACTIVITY_TRACKER.getCreatedContext();

                return recentsViewContainer == null;
            }).get();
            if (isRecentsContainerNUll) {
                // Null activity counts as a "stopped" one.
                return;
            }
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        Wait.atMost("Recents view container didn't close",
                () -> getFromRecents(recents -> !recents.isStarted()),
                mLauncher);
    }

    @Test
    public void testOverview() throws IOException {
        startAppFast(getAppPackageName());
        startAppFast(resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR));
        startTestActivity(2);
        waitForRecentsClosed();
        Wait.atMost("Expected three apps in the task list",
                () -> mLauncher.getRecentTasks().size() >= 3,
                mLauncher);

        checkTestLauncher();
        BaseOverview overview = mLauncher.getLaunchedAppState().switchToOverview();
        checkTestLauncher();

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
                TestUtil.DEFAULT_UI_TIMEOUT));


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

    private void checkTestLauncher() throws IOException {
        final Matcher matcher = COMPONENT_INFO_REGEX.matcher(
                mDevice.executeShellCommand("cmd shortcut get-default-launcher"));
        assertTrue("Incorrect output from get-default-launcher", matcher.find());
        assertEquals("Current Launcher activity is incorrect",
                "com.google.android.apps.nexuslauncher.tests/com.android"
                        + ".launcher3.testcomponent.TestLauncherActivity",
                matcher.group(1)
        );
    }

    private int getCurrentOverviewPage(RecentsViewContainer recentsViewContainer) {
        return recentsViewContainer.<RecentsView>getOverviewPanel().getCurrentPage();
    }

    private int getTaskCount(RecentsViewContainer recentsViewContainer) {
        return recentsViewContainer.<RecentsView>getOverviewPanel().getTaskViewCount();
    }

    private class OverviewUpdateHandler implements OverviewChangeListener {

        final OverviewComponentObserver mObserver;
        final CountDownLatch mChangeCounter;

        OverviewUpdateHandler() {
            Context ctx = getInstrumentation().getTargetContext();
            mObserver = OverviewComponentObserver.INSTANCE.get(ctx);
            mChangeCounter = new CountDownLatch(1);
            if (mObserver.getHomeIntent(DEFAULT_DISPLAY).getComponent()
                    .getPackageName().equals(mOtherLauncherActivity.packageName)) {
                // Home already same
                mChangeCounter.countDown();
            } else {
                mObserver.addOverviewChangeListener(this);
            }
        }

        @Override
        public void onOverviewTargetChange(boolean isHomeAndOverviewSame) {
            mChangeCounter.countDown();
        }

        void destroy() {
            mObserver.removeOverviewChangeListener(this);
        }
    }
}
