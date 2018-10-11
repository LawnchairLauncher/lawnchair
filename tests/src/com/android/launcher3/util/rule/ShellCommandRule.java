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

import static com.android.launcher3.tapl.TestHelpers.getLauncherInMyProcess;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

/**
 * Test rule which executes a shell command at the start of the test.
 */
public class ShellCommandRule implements TestRule {

    private final String mCmd;
    private final String mRevertCommand;

    public ShellCommandRule(String cmd, @Nullable String revertCommand) {
        mCmd = cmd;
        mRevertCommand = revertCommand;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                UiDevice.getInstance(getInstrumentation()).executeShellCommand(mCmd);
                try {
                    base.evaluate();
                } finally {
                    if (mRevertCommand != null) {
                        UiDevice.getInstance(getInstrumentation()).executeShellCommand(mRevertCommand);
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
        return new ShellCommandRule(getLauncherCommand(getLauncherInMyProcess()), null);
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
}
