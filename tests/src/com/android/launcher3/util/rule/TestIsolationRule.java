/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.util.rule;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.ui.AbstractLauncherUiTest;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Isolates tests from some of the state created by the previous test.
 */
public class TestIsolationRule implements TestRule {
    private final LauncherInstrumentation mLauncher;
    private final boolean mRequireOneActiveActivity;

    public TestIsolationRule(LauncherInstrumentation launcher, boolean requireOneActiveActivity) {
        mLauncher = launcher;
        mRequireOneActiveActivity = requireOneActiveActivity;
    }

    @NonNull
    @Override
    public Statement apply(@NonNull Statement base, @NonNull Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                base.evaluate();
                // Make sure that Launcher workspace looks correct.

                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressHome();
                AbstractLauncherUiTest.checkDetectedLeaks(mLauncher, mRequireOneActiveActivity);
            }
        };
    }
}
