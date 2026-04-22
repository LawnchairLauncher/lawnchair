/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.util.ui;

import static android.os.Process.myUserHandle;
import static android.platform.test.flag.junit.SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Debug;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.flag.junit.SetFlagsRule;
import android.platform.test.rule.ExtendedLongPressTimeoutRule;
import android.platform.test.rule.LimitDevicesRule;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.launcher3.tapl.HomeAllApps;
import com.android.launcher3.tapl.HomeAppIcon;
import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.tapl.TestHelpers;
import com.android.launcher3.util.TestUtil;
import com.android.launcher3.util.Wait;
import com.android.launcher3.util.rule.FailureWatcher;
import com.android.launcher3.util.rule.SamplerRule;
import com.android.launcher3.util.rule.ScreenRecordRule;
import com.android.launcher3.util.rule.ShellCommandRule;
import com.android.launcher3.util.rule.SkipAfterTimeOutRule;
import com.android.launcher3.util.rule.TestIsolationRule;
import com.android.launcher3.util.rule.TestStabilityRule;
import com.android.launcher3.util.workspace.FavoriteItemsTransaction;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Base class for all TAPL tests in Launcher providing various utility methods.
 */
public abstract class BaseLauncherTaplTest {

    public static final long DEFAULT_ACTIVITY_TIMEOUT = TimeUnit.SECONDS.toMillis(10);
    public static final long DEFAULT_BROADCAST_TIMEOUT_SECS = 10;

    public static final long DEFAULT_UI_TIMEOUT = TestUtil.DEFAULT_UI_TIMEOUT;
    private static final String TAG = "BaseLauncherTaplTest";

    private static final long BYTES_PER_MEGABYTE = 1 << 20;

    private static boolean sDumpWasGenerated = false;
    private static boolean sActivityLeakReported = false;
    private static boolean sSeenKeyguard = false;
    private static boolean sFirstTimeWaitingForWizard = true;

    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";

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

    private final ActivityManager.MemoryInfo mMemoryInfo = new ActivityManager.MemoryInfo();
    private final ActivityManager mActivityManager;
    private long mMemoryBefore;

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
                }, launcher, DEFAULT_UI_TIMEOUT);
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

    protected BaseLauncherTaplTest() {
        mActivityManager = InstrumentationRegistry.getContext()
                .getSystemService(ActivityManager.class);
        mLauncher.enableCheckEventsForSuccessfulGestures();
        mLauncher.setAnomalyChecker(BaseLauncherTaplTest::verifyKeyguardInvisible);
        try {
            mDevice.setOrientationNatural();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
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

    @Rule
    public LimitDevicesRule mlimitDevicesRule = new LimitDevicesRule();

    @Rule(order = -1000) // This should be the outermost rule
    public SkipAfterTimeOutRule mSkipAfterTimeOutRule = new SkipAfterTimeOutRule();

    protected void performInitialization() {
        reinitializeLauncherData();
        mDevice.pressHome();
        // Check that we switched to home.
        mLauncher.getWorkspace();
        checkDetectedLeaks(mLauncher, true);
    }

    protected void clearPackageData(String pkg) throws IOException, InterruptedException {
        assertTrue("pm clear command failed",
                mDevice.executeShellCommand(
                        String.format("pm clear --user %d %s", myUserHandle().getIdentifier(), pkg))
                        .contains("Success"));
        assertTrue("pm wait-for-handler command failed",
                mDevice.executeShellCommand("pm wait-for-handler")
                        .contains("Success"));
    }

    protected TestRule getRulesInsideActivityMonitor() {
        final RuleChain inner = RuleChain
                .outerRule(new FailureWatcher(mLauncher))
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
        performInitialization();
    }

    private long getAvailableMemory() {
        mActivityManager.getMemoryInfo(mMemoryInfo);

        return Math.divideExact(mMemoryInfo.availMem,  BYTES_PER_MEGABYTE);
    }

    @Before
    public void saveMemoryBefore() {
        mMemoryBefore = getAvailableMemory();
    }

    @After
    public void logMemoryAfter() {
        long memoryAfter = getAvailableMemory();

        Log.d(TAG, "Available memory: before=" + mMemoryBefore
                + "MB, after=" + memoryAfter
                + "MB, delta=" + (memoryAfter - mMemoryBefore) + "MB");
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
    public void resetFreezeRecentTaskList() {
        try {
            mDevice.executeShellCommand("wm reset-freeze-recent-tasks");
        } catch (IOException e) {
            Log.e(TAG, "Failed to reset fozen recent tasks list", e);
        }
    }

    @After
    public void verifyLauncherState() {
        try {
            // Limits UI tests affecting tests running after them.
            mDevice.pressHome();
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

    public static void startAppFast(String packageName) {
        startIntent(
                getInstrumentation().getContext().getPackageManager().getLaunchIntentForPackage(
                        packageName),
                By.pkg(packageName).depth(0),
                true /* newTask */);
    }

    public static void startTestActivity(String activityName, String activityLabel) {
        final String packageName = getAppPackageName();
        final Intent intent = getInstrumentation().getContext().getPackageManager()
                        .getLaunchIntentForPackage(packageName);
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
        final Intent intent = getInstrumentation().getContext().getPackageManager()
                        .getLaunchIntentForPackage(packageName);
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
                launcherInstrumentation, DEFAULT_ACTIVITY_TIMEOUT);
    }

    public static ActivityInfo resolveSystemAppInfo(String category) {
        return getInstrumentation().getContext().getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(category),
                PackageManager.MATCH_SYSTEM_ONLY)
                .activityInfo;
    }


    public static String resolveSystemApp(String category) {
        return resolveSystemAppInfo(category).packageName;
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
