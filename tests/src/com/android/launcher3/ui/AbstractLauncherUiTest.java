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

import static com.android.launcher3.ui.TaplTestsLauncher3.getAppPackageName;
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
import android.os.Debug;
import android.os.Process;
import android.os.RemoteException;
import android.os.StrictMode;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.common.WidgetUtils;
import com.android.launcher3.model.AppLaunchTracker;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.tapl.LauncherInstrumentation.ContainerType;
import com.android.launcher3.tapl.TestHelpers;
import com.android.launcher3.testcomponent.TestCommandReceiver;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.Wait;
import com.android.launcher3.util.rule.FailureRewriterRule;
import com.android.launcher3.util.rule.FailureWatcher;
import com.android.launcher3.util.rule.LauncherActivityRule;
import com.android.launcher3.util.rule.ShellCommandRule;
import com.android.launcher3.util.rule.TestStabilityRule;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Base class for all instrumentation tests providing various utility methods.
 */
public abstract class AbstractLauncherUiTest {

    public static final long DEFAULT_ACTIVITY_TIMEOUT = TimeUnit.SECONDS.toMillis(10);
    public static final long DEFAULT_BROADCAST_TIMEOUT_SECS = 5;

    public static final long DEFAULT_UI_TIMEOUT = 10000;
    private static final String TAG = "AbstractLauncherUiTest";

    private static String sStrictmodeDetectedActivityLeak;
    private static boolean sActivityLeakReported;
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    protected static final ActivityLeakTracker ACTIVITY_LEAK_TRACKER = new ActivityLeakTracker();

    protected LooperExecutor mMainThreadExecutor = MAIN_EXECUTOR;
    protected final UiDevice mDevice = UiDevice.getInstance(getInstrumentation());
    protected final LauncherInstrumentation mLauncher = new LauncherInstrumentation();
    protected Context mTargetContext;
    protected String mTargetPackage;
    private int mLauncherPid;

    static {
        if (TestHelpers.isInLauncherProcess()) {
            StrictMode.VmPolicy.Builder builder =
                    new StrictMode.VmPolicy.Builder()
// b/154772063
//                            .detectActivityLeaks()
                            .penaltyLog()
                            .penaltyListener(Runnable::run, violation -> {
                                if (sStrictmodeDetectedActivityLeak == null) {
                                    sStrictmodeDetectedActivityLeak = violation.toString() + ", "
                                            + dumpHprofData() + ".";
                                }
                            });
            StrictMode.setVmPolicy(builder.build());
        }
    }

    public static void checkDetectedLeaks(LauncherInstrumentation launcher) {
        if (sActivityLeakReported) return;

        if (sStrictmodeDetectedActivityLeak != null) {
            // Report from the test thread strictmode violations detected in the main thread.
            sActivityLeakReported = true;
            Assert.fail(sStrictmodeDetectedActivityLeak);
        }

        // Check whether activity leak detector has found leaked activities.
        Wait.atMost(AbstractLauncherUiTest::getActivityLeakErrorMessage,
                () -> {
                    launcher.getTotalPssKb();  // Triggers GC
                    return MAIN_EXECUTOR.submit(
                            () -> ACTIVITY_LEAK_TRACKER.noLeakedActivities()).get();
                }, DEFAULT_UI_TIMEOUT, launcher);
    }

    private static String getActivityLeakErrorMessage() {
        sActivityLeakReported = true;
        return "Activity leak detector has found leaked activities, " + dumpHprofData() + ".";
    }

    private static String dumpHprofData() {
        try {
            final String fileName = getInstrumentation().getTargetContext().getFilesDir().getPath()
                    + "/ActivityLeakHeapDump.hprof";
            Debug.dumpHprofData(fileName);
            return "memory dump filename: " + fileName;
        } catch (Throwable e) {
            Log.e(TAG, "dumpHprofData failed", e);
            return "failed to save memory dump";
        }
    }

    protected AbstractLauncherUiTest() {
        mLauncher.enableCheckEventsForSuccessfulGestures();
        try {
            mDevice.setOrientationNatural();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        if (TestHelpers.isInLauncherProcess()) {
            Utilities.enableRunningInTestHarnessForTests();
            mLauncher.setSystemHealthSupplier(startTime -> TestCommandReceiver.callCommand(
                    TestCommandReceiver.GET_SYSTEM_HEALTH_MESSAGE, startTime.toString()).
                    getString("result"));
            mLauncher.setOnSettledStateAction(
                    containerType -> executeOnLauncher(
                            launcher ->
                                    checkLauncherIntegrity(launcher, containerType)));
        }
        mLauncher.enableDebugTracing();
        // Avoid double-reporting of Launcher crashes.
        mLauncher.setOnLauncherCrashed(() -> mLauncherPid = 0);
    }

    protected final LauncherActivityRule mActivityMonitor = new LauncherActivityRule();

    @Rule
    public ShellCommandRule mDisableHeadsUpNotification =
            ShellCommandRule.disableHeadsUpNotification();

    protected void clearPackageData(String pkg) throws IOException, InterruptedException {
        final CountDownLatch count = new CountDownLatch(2);
        final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                count.countDown();
            }
        };
        mTargetContext.registerReceiver(broadcastReceiver,
                PackageManagerHelper.getPackageFilter(pkg,
                        Intent.ACTION_PACKAGE_RESTARTED, Intent.ACTION_PACKAGE_DATA_CLEARED));

        mDevice.executeShellCommand("pm clear " + pkg);
        assertTrue(pkg + " didn't restart", count.await(10, TimeUnit.SECONDS));
        mTargetContext.unregisterReceiver(broadcastReceiver);
    }

    // Annotation for tests that need to be run in portrait and landscape modes.
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    protected @interface PortraitLandscape {
    }

    protected TestRule getRulesInsideActivityMonitor() {
        final RuleChain inner = RuleChain.outerRule(new PortraitLandscapeRunner(this))
                .around(new FailureWatcher(mDevice));

        return TestHelpers.isInLauncherProcess()
                ? RuleChain.outerRule(ShellCommandRule.setDefaultLauncher())
                .around(inner) :
                inner;
    }

    @Rule
    public TestRule mOrderSensitiveRules = RuleChain.
            outerRule(new FailureRewriterRule())
            .around(new TestStabilityRule())
            .around(mActivityMonitor)
            .around(getRulesInsideActivityMonitor());

    public UiDevice getDevice() {
        return mDevice;
    }

    private boolean hasSystemUiObject(String resId) {
        return mDevice.hasObject(By.res(SYSTEMUI_PACKAGE, resId));
    }

    @Before
    public void setUp() throws Exception {
        Log.d(TAG, "Before disabling battery defender");
        mDevice.executeShellCommand("setprop vendor.battery.defender.disable 1");
        Log.d(TAG, "Before enabling stay awake");
        mDevice.executeShellCommand("settings put global stay_on_while_plugged_in 3");
        for (int i = 0; i < 10 && hasSystemUiObject("keyguard_status_view"); ++i) {
            Log.d(TAG, "Before unlocking the phone");
            mDevice.executeShellCommand("input keyevent 82");
            mDevice.waitForIdle();
        }
        Assert.assertTrue("Keyguard still visible",
                mDevice.wait(
                        Until.gone(By.res(SYSTEMUI_PACKAGE, "keyguard_status_view")), 60000));
        Log.d(TAG, "Keyguard is not visible");

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
        // Disable app tracker
        AppLaunchTracker.INSTANCE.initializeForTesting(new AppLaunchTracker());

        mTargetContext = InstrumentationRegistry.getTargetContext();
        mTargetPackage = mTargetContext.getPackageName();
        mLauncherPid = mLauncher.getPid();

        UserManager userManager = mTargetContext.getSystemService(UserManager.class);
        if (userManager != null) {
            for (UserHandle userHandle : userManager.getUserProfiles()) {
                if (!userHandle.isSystem()) {
                    mDevice.executeShellCommand("pm remove-user " + userHandle.getIdentifier());
                }
            }
        }
    }

    @After
    public void verifyLauncherState() {
        // Limits UI tests affecting tests running after them.
        mLauncher.waitForLauncherInitialized();
        if (mLauncherPid != 0) {
            assertEquals("Launcher crashed, pid mismatch:",
                    mLauncherPid, mLauncher.getPid().intValue());
        }
        checkDetectedLeaks(mLauncher);
    }

    protected void clearLauncherData() {
        mLauncher.clearLauncherData();
        mLauncher.waitForLauncherInitialized();
    }

    /**
     * Removes all icons from homescreen and hotseat.
     */
    public void clearHomescreen() throws Throwable {
        LauncherSettings.Settings.call(mTargetContext.getContentResolver(),
                LauncherSettings.Settings.METHOD_CREATE_EMPTY_DB);
        LauncherSettings.Settings.call(mTargetContext.getContentResolver(),
                LauncherSettings.Settings.METHOD_CLEAR_EMPTY_DB_FLAG);
        resetLoaderState();
    }

    protected void resetLoaderState() {
        try {
            mMainThreadExecutor.execute(
                    () -> LauncherAppState.getInstance(
                            mTargetContext).getModel().forceReload());
        } catch (Throwable t) {
            throw new IllegalArgumentException(t);
        }
        mLauncher.waitForLauncherInitialized();
    }

    /**
     * Adds {@param item} on the homescreen on the 0th screen
     */
    protected void addItemToScreen(ItemInfo item) {
        WidgetUtils.addItemToScreen(item, mTargetContext);
        resetLoaderState();

        // Launch the home activity
        mDevice.pressHome();
        mLauncher.waitForLauncherInitialized();
    }

    /**
     * Runs the callback on the UI thread and returns the result.
     */
    protected <T> T getOnUiThread(final Callable<T> callback) {
        try {
            return mMainThreadExecutor.submit(callback).get();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    protected <T> T getFromLauncher(Function<Launcher, T> f) {
        if (!TestHelpers.isInLauncherProcess()) return null;
        return getOnUiThread(() -> f.apply(mActivityMonitor.getActivity()));
    }

    protected void executeOnLauncher(Consumer<Launcher> f) {
        getFromLauncher(launcher -> {
            f.accept(launcher);
            return null;
        });
    }

    // Cannot be used in TaplTests between a Tapl call injecting a gesture and a tapl call
    // expecting
    // the results of that gesture because the wait can hide flakeness.
    protected void waitForState(String message, Supplier<LauncherState> state) {
        waitForLauncherCondition(message,
                launcher -> launcher.getStateManager().getCurrentStableState() == state.get());
    }

    protected void waitForResumed(String message) {
        waitForLauncherCondition(message, launcher -> launcher.hasBeenResumed());
    }

    // Cannot be used in TaplTests after injecting any gesture using Tapl because this can hide
    // flakiness.
    protected void waitForLauncherCondition(String
            message, Function<Launcher, Boolean> condition) {
        waitForLauncherCondition(message, condition, DEFAULT_ACTIVITY_TIMEOUT);
    }

    // Cannot be used in TaplTests after injecting any gesture using Tapl because this can hide
    // flakiness.
    protected <T> T getOnceNotNull(String message, Function<Launcher, T> f) {
        return getOnceNotNull(message, f, DEFAULT_ACTIVITY_TIMEOUT);
    }

    // Cannot be used in TaplTests after injecting any gesture using Tapl because this can hide
    // flakiness.
    protected void waitForLauncherCondition(
            String message, Function<Launcher, Boolean> condition, long timeout) {
        if (!TestHelpers.isInLauncherProcess()) return;
        Wait.atMost(message, () -> getFromLauncher(condition), timeout, mLauncher);
    }

    // Cannot be used in TaplTests after injecting any gesture using Tapl because this can hide
    // flakiness.
    protected <T> T getOnceNotNull(String message, Function<Launcher, T> f, long timeout) {
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
            Runnable testThreadAction, Function<Launcher, Boolean> condition,
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
            mTargetContext.registerReceiver(this, new IntentFilter(action));
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            mIntent = intent;
            latch.countDown();
        }

        public Intent blockingGetIntent() throws InterruptedException {
            latch.await(DEFAULT_BROADCAST_TIMEOUT_SECS, TimeUnit.SECONDS);
            mTargetContext.unregisterReceiver(this);
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

    public static void startTestActivity(int activityNumber) {
        final String packageName = getAppPackageName();
        final Intent intent = getInstrumentation().getContext().getPackageManager().
                getLaunchIntentForPackage(packageName);
        intent.setComponent(new ComponentName(packageName,
                "com.android.launcher3.tests.Activity" + activityNumber));
        startIntent(intent, By.pkg(packageName).text("TestActivity" + activityNumber),
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
                UiDevice.getInstance(getInstrumentation())
                        .wait(Until.hasObject(selector), DEFAULT_UI_TIMEOUT));
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
                launcher.finish();
            }
        });
        waitForLauncherCondition(
                "Launcher still active", launcher -> launcher == null, DEFAULT_UI_TIMEOUT);
    }

    protected boolean isInBackground(Launcher launcher) {
        return !launcher.hasBeenResumed();
    }

    protected boolean isInState(Supplier<LauncherState> state) {
        if (!TestHelpers.isInLauncherProcess()) return true;
        return getFromLauncher(
                launcher -> launcher.getStateManager().getState() == state.get());
    }

    protected int getAllAppsScroll(Launcher launcher) {
        return launcher.getAppsView().getActiveRecyclerView().getCurrentScrollY();
    }

    private static void checkLauncherIntegrity(
            Launcher launcher, ContainerType expectedContainerType) {
        if (launcher != null) {
            final StateManager<LauncherState> stateManager = launcher.getStateManager();
            final LauncherState stableState = stateManager.getCurrentStableState();

            assertTrue("Stable state != state: " + stableState.getClass().getSimpleName() + ", "
                            + stateManager.getState().getClass().getSimpleName(),
                    stableState == stateManager.getState());

            final boolean isResumed = launcher.hasBeenResumed();
            assertTrue("hasBeenResumed() != isStarted(), hasBeenResumed(): " + isResumed,
                    isResumed == launcher.isStarted());
            assertTrue("hasBeenResumed() != isUserActive(), hasBeenResumed(): " + isResumed,
                    isResumed == launcher.isUserActive());

            final int ordinal = stableState.ordinal;

            switch (expectedContainerType) {
                case WORKSPACE:
                case WIDGETS: {
                    assertTrue(
                            "Launcher is not resumed in state: " + expectedContainerType,
                            isResumed);
                    assertTrue(TestProtocol.stateOrdinalToString(ordinal),
                            ordinal == TestProtocol.NORMAL_STATE_ORDINAL);
                    break;
                }
                case ALL_APPS: {
                    assertTrue(
                            "Launcher is not resumed in state: " + expectedContainerType,
                            isResumed);
                    assertTrue(TestProtocol.stateOrdinalToString(ordinal),
                            ordinal == TestProtocol.ALL_APPS_STATE_ORDINAL);
                    break;
                }
                case OVERVIEW: {
                    assertTrue(
                            "Launcher is not resumed in state: " + expectedContainerType,
                            isResumed);
                    assertTrue(TestProtocol.stateOrdinalToString(ordinal),
                            ordinal == TestProtocol.OVERVIEW_STATE_ORDINAL);
                    break;
                }
                case BACKGROUND: {
                    assertTrue("Launcher is resumed in state: " + expectedContainerType,
                            !isResumed);
                    assertTrue(TestProtocol.stateOrdinalToString(ordinal),
                            ordinal == TestProtocol.NORMAL_STATE_ORDINAL);
                    break;
                }
                default:
                    throw new IllegalArgumentException(
                            "Illegal container: " + expectedContainerType);
            }
        } else {
            assertTrue(
                    "Container type is not BACKGROUND or FALLBACK_OVERVIEW: "
                            + expectedContainerType,
                    expectedContainerType == ContainerType.BACKGROUND ||
                            expectedContainerType == ContainerType.FALLBACK_OVERVIEW);
        }
    }
}
