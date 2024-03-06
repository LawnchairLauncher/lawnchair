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
package com.android.launcher3.util.rule;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.android.launcher3.tapl.TestHelpers.getLauncherInMyProcess;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.systemui.shared.system.PackageManagerWrapper;

import org.junit.Assert;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;

/**
 * Test rule which executes a shell command at the start of the test.
 */
public class ShellCommandRule implements TestRule {

    private static final String SETPROP_PREFIX = "setprop";
    private static final String GETPROP_PREFIX = "getprop";
    private static final String UNKNOWN = "UNKNOWN";
    private final String mCmd;
    private final String mRevertCommand;
    private final boolean mCheckSuccess;
    private final Runnable mAdditionalChecks;

    public ShellCommandRule(String cmd, @Nullable String revertCommand, boolean checkSuccess,
            Runnable additionalChecks) {
        mCmd = cmd;
        mRevertCommand = revertCommand;
        mCheckSuccess = checkSuccess;
        mAdditionalChecks = additionalChecks;
    }

    public ShellCommandRule(String cmd, @Nullable String revertCommand) {
        this(cmd, revertCommand, false, null);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                String revertSetPropCmd = null;
                if (mCmd.startsWith(SETPROP_PREFIX) && mRevertCommand == null) {
                    // setprop command always follows format : setprop <TAG> <value>
                    // We are stripping out only the TAG here
                    String tag = mCmd.split("\\s")[1];
                    String getpropCmd = GETPROP_PREFIX + " " + tag;
                    String initialValue = UiDevice.getInstance(
                            getInstrumentation()).executeShellCommand(getpropCmd);
                    if (TextUtils.isEmpty(initialValue.trim())) {
                        initialValue = UNKNOWN;
                    }
                    revertSetPropCmd = SETPROP_PREFIX + " " + tag + " " + initialValue;
                }
                final String result =
                        UiDevice.getInstance(getInstrumentation()).executeShellCommand(mCmd);
                if (mCheckSuccess) {
                    Assert.assertTrue(
                            "Failed command: " + mCmd + ", result: " + result,
                            "Success".equals(result.replaceAll("\\s", "")));
                }
                if (mAdditionalChecks != null) mAdditionalChecks.run();
                try {
                    base.evaluate();
                } finally {
                    if (mRevertCommand != null || revertSetPropCmd != null) {
                        String revertCmd =
                                mRevertCommand != null ? mRevertCommand : revertSetPropCmd;
                        final String revertResult = UiDevice.getInstance(
                                getInstrumentation()).executeShellCommand(
                                revertCmd);
                        if (mCheckSuccess) {
                            Assert.assertTrue(
                                    "Failed command: " + revertCmd
                                            + ", result: " + revertResult,
                                    "Success".equals(result.replaceAll("\\s", "")));
                        }
                    }
                }
            }
        };
    }

    /**
     * Grants the launcher permission to bind widgets.
     */
    public static ShellCommandRule grantWidgetBind() {
        return new ShellCommandRule("appwidget grantbind --package "
                + InstrumentationRegistry.getTargetContext().getPackageName(), null);
    }

    /**
     * Sets the target launcher as default launcher.
     */
    public static ShellCommandRule setDefaultLauncher() {
        final ActivityInfo launcher = getLauncherInMyProcess();
        return new ShellCommandRule(getLauncherCommand(launcher), null, true, () ->
                Assert.assertEquals("Setting default launcher failed",
                        new ComponentName(launcher.packageName, launcher.name)
                                .flattenToString(),
                        PackageManagerWrapper.getInstance().getHomeActivities(new ArrayList<>())
                                .flattenToString()));
    }

    public static String getLauncherCommand(ActivityInfo launcher) {
        return "cmd package set-home-activity " +
                new ComponentName(launcher.packageName, launcher.name).flattenToString();
    }

    /**
     * Disables heads up notification for the duration of the test
     */
    public static ShellCommandRule disableHeadsUpNotification() {
        return new ShellCommandRule("settings put global heads_up_notifications_enabled 0",
                "settings put global heads_up_notifications_enabled 1");
    }

    /**
     * Enables "InputTransportPublisher" debug flag. This prints the key input events dispatched by
     * the system server.
     * adb shell setprop log.tag.InputTransportPublisher DEBUG
     * See {@link com.android.cts.input.DebugInputRule} for more details.
     */
    public static ShellCommandRule createEnableInputTransportPublisherRule() {
        return new ShellCommandRule("setprop log.tag.InputTransportPublisher DEBUG", null);
    }
}
