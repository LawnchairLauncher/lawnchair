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
import static com.android.launcher3.ui.AbstractLauncherUiTest.resolveSystemApp;
import static com.android.launcher3.util.rule.ShellCommandRule.disableHeadsUpNotification;
import static com.android.launcher3.util.rule.ShellCommandRule.getLauncherCommand;
import static com.android.quickstep.NavigationModeSwitchRule.Mode.THREE_BUTTON;

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

import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.tapl.TestHelpers;
import com.android.launcher3.testcomponent.TestCommandReceiver;
import com.android.launcher3.util.rule.FailureWatcher;
import com.android.quickstep.NavigationModeSwitchRule.NavigationModeSwitch;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

@LargeTest
@RunWith(AndroidJUnit4.class)
/**
 * TODO: Fix fallback when quickstep is enabled
 */
public class FallbackRecentsTest {

    private final UiDevice mDevice;
    private final LauncherInstrumentation mLauncher;
    private final ActivityInfo mOtherLauncherActivity;

    @Rule
    public final TestRule mDisableHeadsUpNotification = disableHeadsUpNotification();

    @Rule
    public final TestRule mSetLauncherCommand;

    @Rule
    public final TestRule mOrderSensitiveRules;

    public FallbackRecentsTest() throws RemoteException {
        Instrumentation instrumentation = getInstrumentation();
        Context context = instrumentation.getContext();
        mDevice = UiDevice.getInstance(instrumentation);
        mDevice.setOrientationNatural();
        mLauncher = new LauncherInstrumentation();

        mOrderSensitiveRules = RuleChain.
                outerRule(new NavigationModeSwitchRule(mLauncher)).
                around(new FailureWatcher(mDevice));

        mOtherLauncherActivity = context.getPackageManager().queryIntentActivities(
                getHomeIntentInPackage(context),
                MATCH_DISABLED_COMPONENTS).get(0).activityInfo;

        mSetLauncherCommand = (base, desc) -> new Statement() {
            @Override
            public void evaluate() throws Throwable {
                TestCommandReceiver.callCommand(TestCommandReceiver.ENABLE_TEST_LAUNCHER);
                UiDevice.getInstance(getInstrumentation()).executeShellCommand(
                        getLauncherCommand(mOtherLauncherActivity));
                try {
                    base.evaluate();
                } finally {
                    TestCommandReceiver.callCommand(TestCommandReceiver.DISABLE_TEST_LAUNCHER);
                    UiDevice.getInstance(getInstrumentation()).executeShellCommand(
                            getLauncherCommand(getLauncherInMyProcess()));
                }
            }
        };
        if (TestHelpers.isInLauncherProcess()) {
            mLauncher.setSystemHealthSupplier(startTime -> TestCommandReceiver.callCommand(
                    TestCommandReceiver.GET_SYSTEM_HEALTH_MESSAGE, startTime.toString()).
                    getString("result"));
        }
    }

    @NavigationModeSwitch(mode = THREE_BUTTON)
    @Test
    public void goToOverviewFromHome() {
        mDevice.pressHome();
        assertTrue("Fallback Launcher not visible", mDevice.wait(Until.hasObject(By.pkg(
                mOtherLauncherActivity.packageName)), WAIT_TIME_MS));

        mLauncher.getBackground().switchToOverview();
    }

    @NavigationModeSwitch(mode = THREE_BUTTON)
    @Test
    public void goToOverviewFromApp() {
        startAppFast(resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR));

        mLauncher.getBackground().switchToOverview();
    }

    private void startAppFast(String packageName) {
        final Instrumentation instrumentation = getInstrumentation();
        final Intent intent = instrumentation.getContext().getPackageManager().
                getLaunchIntentForPackage(packageName);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        instrumentation.getTargetContext().startActivity(intent);
        assertTrue(packageName + " didn't start",
                mDevice.wait(Until.hasObject(By.pkg(packageName).depth(0)), WAIT_TIME_MS));
    }

}
