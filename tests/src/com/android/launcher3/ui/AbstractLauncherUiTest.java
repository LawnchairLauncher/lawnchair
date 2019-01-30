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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.RemoteException;
import android.view.Surface;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherState;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.tapl.TestHelpers;
import com.android.launcher3.util.Wait;
import com.android.launcher3.util.rule.LauncherActivityRule;
import com.android.launcher3.util.rule.ShellCommandRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runners.model.Statement;

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

/**
 * Base class for all instrumentation tests providing various utility methods.
 */
public abstract class AbstractLauncherUiTest {

    public static final long DEFAULT_ACTIVITY_TIMEOUT = TimeUnit.SECONDS.toMillis(10);
    public static final long DEFAULT_BROADCAST_TIMEOUT_SECS = 5;

    public static final long SHORT_UI_TIMEOUT= 300;
    public static final long DEFAULT_UI_TIMEOUT = 10000;
    protected static final int LONG_WAIT_TIME_MS = 60000;

    protected MainThreadExecutor mMainThreadExecutor = new MainThreadExecutor();
    protected final UiDevice mDevice;
    protected final LauncherInstrumentation mLauncher;
    protected Context mTargetContext;
    protected String mTargetPackage;

    protected AbstractLauncherUiTest() {
        final Instrumentation instrumentation = getInstrumentation();
        mDevice = UiDevice.getInstance(instrumentation);
        try {
            mDevice.setOrientationNatural();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        if (TestHelpers.isInLauncherProcess()) Utilities.enableRunningInTestHarnessForTests();
        mLauncher = new LauncherInstrumentation(instrumentation);
    }

    @Rule
    public LauncherActivityRule mActivityMonitor = new LauncherActivityRule();

    @Rule public ShellCommandRule mDefaultLauncherRule =
            TestHelpers.isInLauncherProcess() ? ShellCommandRule.setDefaultLauncher() : null;

    @Rule public ShellCommandRule mDisableHeadsUpNotification =
            ShellCommandRule.disableHeadsUpNotification();

    // Annotation for tests that need to be run in portrait and landscape modes.
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    protected @interface PortraitLandscape {
    }

    @Rule
    public TestRule mPortraitLandscapeExecutor =
            (base, description) -> false && description.getAnnotation(PortraitLandscape.class)
                    != null ? new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    try {
                        // Create launcher activity if necessary and bring it to the front.
                        mLauncher.pressHome();
                        waitForLauncherCondition("Launcher activity wasn't created",
                                launcher -> launcher != null);

                        executeOnLauncher(launcher ->
                                launcher.getRotationHelper().forceAllowRotationForTesting(true));

                        evaluateInPortrait();
                        evaluateInLandscape();
                    } finally {
                        mDevice.setOrientationNatural();
                        executeOnLauncher(launcher ->
                                launcher.getRotationHelper().forceAllowRotationForTesting(false));
                        mLauncher.setExpectedRotation(Surface.ROTATION_0);
                    }
                }

                private void evaluateInPortrait() throws Throwable {
                    mDevice.setOrientationNatural();
                    mLauncher.setExpectedRotation(Surface.ROTATION_0);
                    base.evaluate();
                }

                private void evaluateInLandscape() throws Throwable {
                    mDevice.setOrientationLeft();
                    mLauncher.setExpectedRotation(Surface.ROTATION_90);
                    base.evaluate();
                }
            } : base;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        mTargetPackage = mTargetContext.getPackageName();
    }

    @After
    public void tearDown() throws Exception {
        // Limits UI tests affecting tests running after them.
        waitForModelLoaded();
    }

    protected void lockRotation(boolean naturalOrientation) throws RemoteException {
        if (naturalOrientation) {
            mDevice.setOrientationNatural();
        } else {
            mDevice.setOrientationRight();
        }
    }

    protected void clearLauncherData() throws IOException {
        if (TestHelpers.isInLauncherProcess()) {
            LauncherSettings.Settings.call(mTargetContext.getContentResolver(),
                    LauncherSettings.Settings.METHOD_CREATE_EMPTY_DB);
            resetLoaderState();
        } else {
            mDevice.executeShellCommand("pm clear " + mDevice.getLauncherPackageName());
        }
    }

    /**
     * Scrolls the {@param container} until it finds an object matching {@param condition}.
     * @return the matching object.
     */
    protected UiObject2 scrollAndFind(UiObject2 container, BySelector condition) {
        container.setGestureMargins(0, 0, 0, 200);

        int i = 0;
        for (; ; ) {
            // findObject can only execute after spring settles.
            mDevice.wait(Until.findObject(condition), SHORT_UI_TIMEOUT);
            UiObject2 widget = container.findObject(condition);
            if (widget != null && widget.getVisibleBounds().intersects(
                    0, 0, mDevice.getDisplayWidth(), mDevice.getDisplayHeight() - 200)) {
                return widget;
            }
            if (++i > 40) fail("Too many attempts");
            container.scroll(Direction.DOWN, 1f);
        }
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
            mMainThreadExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    LauncherAppState.getInstance(mTargetContext).getModel().forceReload();
                }
            });
        } catch (Throwable t) {
            throw new IllegalArgumentException(t);
        }
        waitForModelLoaded();
    }

    protected void waitForModelLoaded() {
        waitForLauncherCondition("Launcher model didn't load", launcher -> {
            final LauncherModel model = LauncherAppState.getInstance(mTargetContext).getModel();
            return model.getCallback() == null || model.isModelLoaded();
        });
    }

    /**
     * Runs the callback on the UI thread and returns the result.
     */
    protected <T> T getOnUiThread(final Callable<T> callback) {
        try {
            return mMainThreadExecutor.submit(callback).get();
        } catch (Exception e) {
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

    // Cannot be used in TaplTests between a Tapl call injecting a gesture and a tapl call expecting
    // the results of that gesture because the wait can hide flakeness.
    protected void waitForState(String message, LauncherState state) {
        waitForLauncherCondition(message,
                launcher -> launcher.getStateManager().getState() == state);
    }

    protected void waitForResumed(String message) {
        waitForLauncherCondition(message, launcher -> launcher.hasBeenResumed());
    }

    // Cannot be used in TaplTests after injecting any gesture using Tapl because this can hide
    // flakiness.
    protected void waitForLauncherCondition(String message, Function<Launcher, Boolean> condition) {
        waitForLauncherCondition(message, condition, DEFAULT_ACTIVITY_TIMEOUT);
    }

    // Cannot be used in TaplTests after injecting any gesture using Tapl because this can hide
    // flakiness.
    protected void waitForLauncherCondition(
            String message, Function<Launcher, Boolean> condition, long timeout) {
        if (!TestHelpers.isInLauncherProcess()) return;
        Wait.atMost(message, () -> getFromLauncher(condition), timeout);
    }

    protected LauncherActivityInfo getSettingsApp() {
        return LauncherAppsCompat.getInstance(mTargetContext)
                .getActivityList("com.android.settings",
                        Process.myUserHandle()).get(0);
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
            return intent == null ? null : (Intent) intent.getParcelableExtra(Intent.EXTRA_INTENT);
        }
    }

    protected void startAppFast(String packageName) {
        final Instrumentation instrumentation = getInstrumentation();
        final Intent intent = instrumentation.getContext().getPackageManager().
                getLaunchIntentForPackage(packageName);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        instrumentation.getTargetContext().startActivity(intent);
        assertTrue(packageName + " didn't start",
                mDevice.wait(Until.hasObject(By.pkg(packageName).depth(0)), LONG_WAIT_TIME_MS));
    }

    protected String resolveSystemApp(String category) {
        return getInstrumentation().getContext().getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(category),
                PackageManager.MATCH_SYSTEM_ONLY).
                activityInfo.packageName;
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

    protected boolean isInState(LauncherState state) {
        if (!TestHelpers.isInLauncherProcess()) return true;
        return getFromLauncher(launcher -> launcher.getStateManager().getState() == state);
    }

    protected int getAllAppsScroll(Launcher launcher) {
        return launcher.getAppsView().getActiveRecyclerView().getCurrentScrollY();
    }
}
