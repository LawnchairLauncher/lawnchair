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

import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.os.ParcelFileDescriptor;
import androidx.test.InstrumentationRegistry;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Test rule which executes a shell command at the start of the test.
 */
public class ShellCommandRule implements TestRule {

    private final String mCmd;

    public ShellCommandRule(String cmd) {
        mCmd = cmd;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new MyStatement(base, mCmd);
    }

    public static void runShellCommand(String command) throws IOException {
        ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand(command);

        // Read the input stream fully.
        FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
        while (fis.read() != -1);
        fis.close();
    }

    private static class MyStatement extends Statement {
        private final Statement mBase;
        private final String mCmd;

        public MyStatement(Statement base, String cmd) {
            mBase = base;
            mCmd = cmd;
        }

        @Override
        public void evaluate() throws Throwable {
            runShellCommand(mCmd);
            mBase.evaluate();
        }
    }

    /**
     * Grants the launcher permission to bind widgets.
     */
    public static ShellCommandRule grandWidgetBind() {
        return new ShellCommandRule("appwidget grantbind --package "
                + InstrumentationRegistry.getTargetContext().getPackageName());
    }

    /**
     * Sets the target launcher as default launcher.
     */
    public static ShellCommandRule setDefaultLauncher() {
        ActivityInfo launcher = InstrumentationRegistry.getTargetContext().getPackageManager()
                .queryIntentActivities(LauncherActivityRule.getHomeIntent(), 0).get(0)
                .activityInfo;
        return new ShellCommandRule("cmd package set-home-activity " +
                new ComponentName(launcher.packageName, launcher.name).flattenToString());
    }
}
