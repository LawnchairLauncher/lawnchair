/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.os.Build;
import android.util.Log;

import androidx.test.uiautomator.UiDevice;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestStabilityRule implements TestRule {
    private static final String TAG = "TestStabilityRule";
    private static final Pattern LAUNCHER_BUILD =
            Pattern.compile("^("
                    + "(?<local>(BuildFromAndroidStudio|"
                    + "([0-9]+|[A-Z])-eng\\.[a-z]+\\.[0-9]+\\.[0-9]+))|"
                    + "(?<presubmit>([0-9]+|[A-Z])-P[0-9]+)|"
                    + "(?<postsubmit>([0-9]+|[A-Z])-[0-9]+)|"
                    + "(?<platform>[0-9]+|[A-Z])"
                    + ")$");
    private static final Pattern PLATFORM_BUILD =
            Pattern.compile("^("
                    + "(?<commandLine>eng\\.[a-z]+\\.[0-9]+\\.[0-9]+)|"
                    + "(?<presubmit>P[0-9]+)|"
                    + "(?<postsubmit>[0-9]+)"
                    + ")$");

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Stability {
    }

    @Override
    public Statement apply(Statement base, Description description) {
        if (description.getAnnotation(Stability.class) != null) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    getRunFlavor();

                    base.evaluate();
                }
            };
        } else {
            return base;
        }
    }

    private static void getRunFlavor() throws Exception {
        final String launcherVersion = getInstrumentation().
                getContext().
                getPackageManager().
                getPackageInfo(
                        UiDevice.getInstance(getInstrumentation()).
                                getLauncherPackageName(),
                        0).
                versionName;

        final Matcher launcherBuildMatcher = LAUNCHER_BUILD.matcher(launcherVersion);

        if (!launcherBuildMatcher.find()) {
            Log.e(TAG, "Match not found");
        }

        final String platformVersion = Build.VERSION.INCREMENTAL;
        final Matcher platformBuildMatcher = PLATFORM_BUILD.matcher(platformVersion);

        if (!platformBuildMatcher.find()) {
            Log.e(TAG, "Match not found");
        }

        Log.d(TAG, "Launcher: " + launcherVersion + ", platform: " + platformVersion);

        if (launcherBuildMatcher.group("local") != null && (
                platformBuildMatcher.group("commandLine") != null ||
                        platformBuildMatcher.group("postsubmit") != null)) {
            Log.d(TAG, "LOCAL RUN");
        } else if (launcherBuildMatcher.group("presubmit") != null
                && platformBuildMatcher.group("postsubmit") != null) {
            Log.d(TAG, "UNBUNDLED PRESUBMIT");
        } else if (launcherBuildMatcher.group("postsubmit") != null
                && platformBuildMatcher.group("postsubmit") != null) {
            Log.d(TAG, "UNBUNDLED POSTSUBMIT");
        } else if (launcherBuildMatcher.group("platform") != null
                && platformBuildMatcher.group("presubmit") != null) {
            Log.d(TAG, "PLATFORM PRESUBMIT");
        } else if (launcherBuildMatcher.group("platform") != null
                && platformBuildMatcher.group("postsubmit") != null) {
            Log.d(TAG, "PLATFORM POSTSUBMIT");
        } else {
            Log.e(TAG, "ERROR3");
        }
    }
}
