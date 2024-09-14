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

import static android.platform.test.flag.junit.SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.android.launcher3.testing.shared.TestProtocol.ICON_MISSING;
import static com.android.launcher3.testing.shared.TestProtocol.WIDGET_CONFIG_NULL_EXTRA_INTENT;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Debug;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.flag.junit.SetFlagsRule;
import android.system.OsConstants;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.celllayout.FavoriteItemsTransaction;
import com.android.launcher3.tapl.HomeAllApps;
import com.android.launcher3.tapl.HomeAppIcon;
import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.tapl.TestHelpers;
import com.android.launcher3.testcomponent.TestCommandReceiver;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.util.SimpleBroadcastReceiver;
import com.android.launcher3.util.TestUtil;
import com.android.launcher3.util.Wait;
import com.android.launcher3.util.rule.ExtendedLongPressTimeoutRule;
import com.android.launcher3.util.rule.FailureWatcher;
import com.android.launcher3.util.rule.SamplerRule;
import com.android.launcher3.util.rule.ScreenRecordRule;
import com.android.launcher3.util.rule.ShellCommandRule;
import com.android.launcher3.util.rule.TestIsolationRule;
import com.android.launcher3.util.rule.TestStabilityRule;
import com.android.launcher3.util.rule.ViewCaptureRule;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Base class for all instrumentation tests providing various utility methods.
 */
public abstract class AbstractLauncherUiTest<LAUNCHER_TYPE extends Launcher> {

    public static final long DEFAULT_ACTIVITY_TIMEOUT = TimeUnit.SECONDS.toMillis(10);
    public static final long DEFAULT_BROADCAST_TIMEOUT_SECS = 10;

    public static final long DEFAULT_UI_TIMEOUT = TestUtil.DEFAULT_UI_TIMEOUT;
    private static final String TAG = "AbstractLauncherUiTest";

    private static boolean sDumpWasGenerated = false;
    private static boolean sActivityLeakReported = false;
    private static boolean sSeenKeyguard = false;
    private static boolean sFirstTimeWaitingForWizard = true;

    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    protected LooperExecutor mMainThreadExecutor = MAIN_EXECUTOR;
    protected final UiDevice mDevice = getUiDevice();
    protected final LauncherInstrumentation mLauncher = createLauncherInstrumentation();

    @NonNull
    public static LauncherInstrumentation createLauncherInstrumentation() {
        waitForSetupWizardDismissal(); // precondition for creating LauncherInstrumentation
        return new LauncherInstrumentation(true);
    }

    protected Context mTargetContext;
    protected String mTargetPackage;
    private int mLauncherPid;

    /** Detects activity leaks and throws an exception if a leak is found. */
    public static void checkDetectedLeaks(LauncherInstrumentation launcher) {
        checkDetectedLeaks(launcher, false);
    }

    /** Detects activity leaks and throws an exception if a leak is found. */
    public static void checkDetectedLeaks(LauncherInstrumentation launcher,
            boolean requireOneActiveActivityUnused) {
        if (TestStabilityRule.isPresubmit()) return; // b/313501215

        final boolean requireOneActiveActivity =
                false; // workaround for leaks when there is an unexpected Recents activity

        if (sActivityLeakReported) return;

        // Check whether activity leak detector has found leaked activities.
        Wait.atMost(() -> getActivityLeakErrorMessage(launcher, requireOneActiveActivity),
                () -> {
                    launcher.forceGc();
                    return MAIN_EXECUTOR.submit(
                            () -> launcher.noLeakedActivities(requireOneActiveActivity)).get();
                }, DEFAULT_UI_TIMEOUT, launcher);
    }

    public static String getAppPackageName() {
        return getInstrumentation().getContext().getPackageName();
    }

    private static String getActivityLeakErrorMessage(LauncherInstrumentation launcher,
            boolean requireOneActiveActivity) {
        sActivityLeakReported = true;
        return "Activity leak detector has found leaked activities, requirining 1 activity: "
                + requireOneActiveActivity + "; "
                + dumpHprofData(launcher, false, requireOneActiveActivity) + ".";
    }

    private static String dumpHprofData(LauncherInstrumentation launcher, boolean intentionalLeak,
            boolean requireOneActiveActivity) {
        if (intentionalLeak) return "intentional leak; not generating dump";

        String result;
        if (sDumpWasGenerated) {
            result = "dump has already been generated by another test";
        } else {
            try {
                final String fileName =
                        getInstrumentation().getTargetContext().getFilesDir().getPath()
                                + "/ActivityLeakHeapDump.hprof";
                if (TestHelpers.isInLauncherProcess()) {
                    Debug.dumpHprofData(fileName);
                } else {
                    final UiDevice device = getUiDevice();
                    device.executeShellCommand(
                            "am dumpheap " + device.getLauncherPackageName() + " " + fileName);
                }
                Log.d(TAG, "Saved leak dump, the leak is still present: "
                        + !launcher.noLeakedActivities(requireOneActiveActivity));
                sDumpWasGenerated = true;
                result = "saved memory dump as an artifact";
            } catch (Throwable e) {
                Log.e(TAG, "dumpHprofData failed", e);
                result = "failed to save memory dump";
            }
        }
        return result + ". Full list of activities: " + launcher.getRootedActivitiesList();
    }

    protected AbstractLauncherUiTest() {
        mLauncher.enableCheckEventsForSuccessfulGestures();
        mLauncher.setAnomalyChecker(AbstractLauncherUiTest::verifyKeyguardInvisible);
        try {
            mDevice.setOrientationNatural();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        if (TestHelpers.isInLauncherProcess()) {
            Utilities.enableRunningInTestHarnessForTests();
            mLauncher.setSystemHealthSupplier(startTime -> TestCommandReceiver.callCommand(
                            TestCommandReceiver.GET_SYSTEM_HEALTH_MESSAGE, startTime.toString())
                    .getString("result"));
        }
        mLauncher.enableDebugTracing();
        // Avoid double-reporting of Launcher crashes.
        mLauncher.setOnLauncherCrashed(() -> mLauncherPid = 0);
    }

    @Rule
    public ShellCommandRule mDisableHeadsUpNotification =
            ShellCommandRule.disableHeadsUpNotification();

    @Rule
    public ScreenRecordRule mScreenRecordRule = new ScreenRecordRule();

    @Rule
    public SetFlagsRule mSetFlagsRule = new SetFlagsRule(DEVICE_DEFAULT);

    @Rule
    public ExtendedLongPressTimeoutRule mLongPressTimeoutRule = new ExtendedLongPressTimeoutRule();

    public static void initialize(AbstractLauncherUiTest test) throws Exception {
        test.reinitializeLauncherData();
        test.mDevice.pressHome();
        test.waitForLauncherCondition("Launcher didn't start", Objects::nonNull);
        test.waitForState("Launcher internal state didn't switch to Home",
                () -> LauncherState.NORMAL);
        test.waitForResumed("Launcher internal state is still Background");
        // Check that we switched to home.
        test.mLauncher.getWorkspace();
        AbstractLauncherUiTest.checkDetectedLeaks(test.mLauncher, true);
    }

    protected void clearPackageData(String pkg) throws IOException, InterruptedException {
        final CountDownLatch count = new CountDownLatch(2);
        final SimpleBroadcastReceiver broadcastReceiver =
                new SimpleBroadcastReceiver(i -> count.countDown());
        broadcastReceiver.registerPkgActions(mTargetContext, pkg,
                Intent.ACTION_PACKAGE_RESTARTED, Intent.ACTION_PACKAGE_DATA_CLEARED);

        mDevice.executeShellCommand("pm clear " + pkg);
        assertTrue(pkg + " didn't restart", count.await(10, TimeUnit.SECONDS));
        mTargetContext.unregisterReceiver(broadcastReceiver);
    }

    protected TestRule getRulesInsideActivityMonitor() {
        final ViewCaptureRule viewCaptureRule = new ViewCaptureRule(
                Launcher.ACTIVITY_TRACKER::getCreatedActivity);
        final RuleChain inner = RuleChain
                .outerRule(new PortraitLandscapeRunner<LAUNCHER_TYPE>(this))
                .around(new FailureWatcher(mLauncher, viewCaptureRule::getViewCaptureData))
                // .around(viewCaptureRule) // b/315482167
                .around(new TestIsolationRule(mLauncher, true));

        return TestHelpers.isInLauncherProcess()
                ? RuleChain.outerRule(ShellCommandRule.setDefaultLauncher()).around(inner)
                : inner;
    }

    @Rule
    public TestRule mOrderSensitiveRules = RuleChain
            .outerRule(new SamplerRule())
            .around(new TestStabilityRule())
            .around(getRulesInsideActivityMonitor());

    public UiDevice getDevice() {
        return mDevice;
    }

    @Before
    public void setUp() throws Exception {
        mLauncher.onTestStart();

        final String launcherPackageName = mDevice.getLauncherPackageName();
        try {
            final Context context = InstrumentationRegistry.getContext();
            final PackageManager pm = context.getPackageManager();
            final PackageInfo launcherPackage = pm.getPackageInfo(launcherPackageName, 0);

            if (!launcherPackage.versionName.equals("BuildFromAndroidStudio")) {
                Assert.assertEquals("Launcher version doesn't match tests version",
                        pm.getPackageInfo(context.getPackageName(), 0).getLongVersionCode(),
                        launcherPackage.getLongVersionCode());
            }
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }

        mLauncherPid = 0;

        mTargetContext = InstrumentationRegistry.getTargetContext();
        mTargetPackage = mTargetContext.getPackageName();
        mLauncherPid = mLauncher.getPid();

        UserManager userManager = mTargetContext.getSystemService(UserManager.class);
        if (userManager != null) {
            for (UserHandle userHandle : userManager.getUserProfiles()) {
                if (!userHandle.isSystem()) {
                    mDevice.executeShellCommand(
                            "pm remove-user --wait " + userHandle.getIdentifier());
                }
            }
        }

        onTestStart();

        initialize(this);
    }

    /** Method that should be called when a test starts. */
    public static void onTestStart() {
        waitForSetupWizardDismissal();

        if (TestStabilityRule.isPresubmit()) {
            aggressivelyUnlockSysUi();
        } else {
            verifyKeyguardInvisible();
        }
    }

    private static boolean hasSystemUiObject(String resId) {
        return getUiDevice().hasObject(
                By.res(SYSTEMUI_PACKAGE, resId));
    }

    @NonNull
    private static UiDevice getUiDevice() {
        return UiDevice.getInstance(getInstrumentation());
    }

    private static void aggressivelyUnlockSysUi() {
        final UiDevice device = getUiDevice();
        for (int i = 0; i < 10 && hasSystemUiObject("keyguard_status_view"); ++i) {
            Log.d(TAG, "Before attempting to unlock the phone");
            try {
                device.executeShellCommand("input keyevent 82");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            device.waitForIdle();
        }
        Assert.assertTrue("Keyguard still visible",
                TestHelpers.wait(
                        Until.gone(By.res(SYSTEMUI_PACKAGE, "keyguard_status_view")), 60000));
        Log.d(TAG, "Keyguard is not visible");
    }

    /** Waits for setup wizard to go away. */
    private static void waitForSetupWizardDismissal() {
        if (!TestStabilityRule.isPresubmit()) return;

        if (sFirstTimeWaitingForWizard) {
            try {
                getUiDevice().executeShellCommand(
                        "am force-stop com.google.android.setupwizard");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        final boolean wizardDismissed = TestHelpers.wait(
                Until.gone(By.pkg("com.google.android.setupwizard").depth(0)),
                sFirstTimeWaitingForWizard ? 120000 : 0);
        sFirstTimeWaitingForWizard = false;
        Assert.assertTrue("Setup wizard is still visible", wizardDismissed);
    }

    /** Asserts that keyguard is not visible */
    public static void verifyKeyguardInvisible() {
        final boolean keyguardAlreadyVisible = sSeenKeyguard;

        sSeenKeyguard = sSeenKeyguard
                || !TestHelpers.wait(
                Until.gone(By.res(SYSTEMUI_PACKAGE, "keyguard_status_view")), 60000);

        Assert.assertFalse(
                "Keyguard is visible, which is likely caused by a crash in SysUI, seeing keyguard"
                        + " for the first time = "
                        + !keyguardAlreadyVisible,
                sSeenKeyguard);
    }

    @After
    public void verifyLauncherState() {
        try {
            // Limits UI tests affecting tests running after them.
            mLauncher.waitForLauncherInitialized();
            if (mLauncherPid != 0) {
                assertEquals("Launcher crashed, pid mismatch:",
                        mLauncherPid, mLauncher.getPid().intValue());
            }
        } finally {
            mLauncher.onTestFinish();
        }
    }

    protected void reinitializeLauncherData() {
        reinitializeLauncherData(false);
    }

    protected void reinitializeLauncherData(boolean clearWorkspace) {
        if (clearWorkspace) {
            mLauncher.clearLauncherData();
        } else {
            mLauncher.reinitializeLauncherData();
        }
        mLauncher.waitForLauncherInitialized();
    }

    /**
     * Runs the callback on the UI thread and returns the result.
     */
    protected <T> T getOnUiThread(final Callable<T> callback) {
        try {
            return mMainThreadExecutor.submit(callback).get(DEFAULT_UI_TIMEOUT,
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
        return getOnUiThread(() -> f.apply(Launcher.ACTIVITY_TRACKER.getCreatedActivity()));
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
        waitForLauncherCondition(message, condition, DEFAULT_ACTIVITY_TIMEOUT);
    }

    // Cannot be used in TaplTests after injecting any gesture using Tapl because this can hide
    // flakiness.
    protected <O> O getOnceNotNull(String message, Function<LAUNCHER_TYPE, O> f) {
        return getOnceNotNull(message, f, DEFAULT_ACTIVITY_TIMEOUT);
    }

    // Cannot be used in TaplTests after injecting any gesture using Tapl because this can hide
    // flakiness.
    protected void waitForLauncherCondition(
            String message, Function<LAUNCHER_TYPE, Boolean> condition, long timeout) {
        verifyKeyguardInvisible();
        if (!TestHelpers.isInLauncherProcess()) return;
        Wait.atMost(message, () -> getFromLauncher(condition), timeout, mLauncher);
    }

    // Cannot be used in TaplTests after injecting any gesture using Tapl because this can hide
    // flakiness.
    protected <T> T getOnceNotNull(String message, Function<LAUNCHER_TYPE, T> f, long timeout) {
        if (!TestHelpers.isInLauncherProcess()) return null;

        final Object[] output = new Object[1];
        Wait.atMost(message, () -> {
            final Object fromLauncher = getFromLauncher(f);
            output[0] = fromLauncher;
            return fromLauncher != null;
        }, timeout, mLauncher);
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
        }, timeout, mLauncher);
    }

    protected LauncherActivityInfo getSettingsApp() {
        return mTargetContext.getSystemService(LauncherApps.class)
                .getActivityList("com.android.settings", Process.myUserHandle()).get(0);
    }

    /**
     * Broadcast receiver which blocks until the result is received.
     */
    public class BlockingBroadcastReceiver extends BroadcastReceiver {

        private final CountDownLatch latch = new CountDownLatch(1);
        private Intent mIntent;

        public BlockingBroadcastReceiver(String action) {
            mTargetContext.registerReceiver(this, new IntentFilter(action),
                    Context.RECEIVER_EXPORTED/*UNAUDITED*/);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(WIDGET_CONFIG_NULL_EXTRA_INTENT, intent == null
                    ? "AbstractLauncherUiTest.onReceive(): inputted intent NULL"
                    : "AbstractLauncherUiTest.onReceive(): inputted intent NOT NULL");
            mIntent = intent;
            latch.countDown();
            Log.d(WIDGET_CONFIG_NULL_EXTRA_INTENT,
                    "AbstractLauncherUiTest.onReceive() Countdown Latch started");
        }

        public Intent blockingGetIntent() throws InterruptedException {
            Log.d(WIDGET_CONFIG_NULL_EXTRA_INTENT,
                    "AbstractLauncherUiTest.blockingGetIntent()");
            assertTrue("Timed Out", latch.await(DEFAULT_BROADCAST_TIMEOUT_SECS, TimeUnit.SECONDS));
            mTargetContext.unregisterReceiver(this);
            Log.d(WIDGET_CONFIG_NULL_EXTRA_INTENT, mIntent == null
                    ? "AbstractLauncherUiTest.onReceive(): mIntent NULL"
                    : "AbstractLauncherUiTest.onReceive(): mIntent NOT NULL");
            return mIntent;
        }

        public Intent blockingGetExtraIntent() throws InterruptedException {
            Intent intent = blockingGetIntent();
            return intent == null ? null : (Intent) intent.getParcelableExtra(
                    Intent.EXTRA_INTENT);
        }
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
                TestHelpers.wait(Until.hasObject(selector), DEFAULT_UI_TIMEOUT));

        // Wait for the Launcher to stop.
        final LauncherInstrumentation launcherInstrumentation = new LauncherInstrumentation();
        Wait.atMost("Launcher activity didn't stop",
                () -> !launcherInstrumentation.isLauncherActivityStarted(),
                DEFAULT_ACTIVITY_TIMEOUT, launcherInstrumentation);
    }

    public static ActivityInfo resolveSystemAppInfo(String category) {
        return getInstrumentation().getContext().getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(category),
                PackageManager.MATCH_SYSTEM_ONLY).
                activityInfo;
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
        waitForLauncherCondition(
                "Launcher still active", launcher -> launcher == null, DEFAULT_UI_TIMEOUT);
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

    protected HomeAppIcon createShortcutInCenterIfNotExist(String name) {
        Point dimension = mLauncher.getWorkspace().getIconGridDimensions();
        return createShortcutIfNotExist(name, dimension.x / 2, dimension.y / 2);
    }

    protected HomeAppIcon createShortcutIfNotExist(String name, Point cellPosition) {
        return createShortcutIfNotExist(name, cellPosition.x, cellPosition.y);
    }

    protected HomeAppIcon createShortcutIfNotExist(String name, int cellX, int cellY) {
        HomeAppIcon homeAppIcon = mLauncher.getWorkspace().tryGetWorkspaceAppIcon(name);
        Log.d(ICON_MISSING, "homeAppIcon: " + homeAppIcon + " name: " + name +
                " cell: " + cellX + ", " + cellY);
        if (homeAppIcon == null) {
            HomeAllApps allApps = mLauncher.getWorkspace().switchToAllApps();
            allApps.freeze();
            try {
                allApps.getAppIcon(name).dragToWorkspace(cellX, cellY);
            } finally {
                allApps.unfreeze();
            }
            homeAppIcon = mLauncher.getWorkspace().getWorkspaceAppIcon(name);
        }
        return homeAppIcon;
    }

    protected void commitTransactionAndLoadHome(FavoriteItemsTransaction transaction) {
        transaction.commit();

        // Launch the home activity
        UiDevice.getInstance(getInstrumentation()).pressHome();
        mLauncher.waitForLauncherInitialized();
    }

    /** Clears all recent tasks */
    protected void clearAllRecentTasks() {
        if (!mLauncher.getRecentTasks().isEmpty()) {
            mLauncher.goHome().switchToOverview().dismissAllTasks();
        }
    }
}
