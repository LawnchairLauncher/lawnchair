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

package com.android.launcher3.util.rule;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.UiDevice;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Test rule which executes a set prop command at the start of the test.
 * This rule needs the property tag and property value so that value can be set to a tag.
 */
public class SetPropRule implements TestRule {
    private static final String SETPROP_PREFIX = "setprop";
    private static final String GETPROP_PREFIX = "getprop";
    private static final String UNKNOWN = "UNKNOWN";
    @NonNull private final String mPropTag;
    @NonNull private final String mPropValue;

    public SetPropRule(@NonNull String propTag, @NonNull String propValue) {
        mPropTag = propTag.trim();
        mPropValue = propValue.trim();
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                String getpropCmd = GETPROP_PREFIX + " " + mPropTag;
                String initialValue = UiDevice.getInstance(getInstrumentation())
                        .executeShellCommand(getpropCmd);
                if (TextUtils.isEmpty(initialValue.trim())) {
                    initialValue = UNKNOWN;
                }
                // setprop command always follows format : setprop <TAG> <value>
                String revertSetPropCmd = SETPROP_PREFIX + " " + mPropTag + " " + initialValue;
                String setPropCmd = SETPROP_PREFIX + " " + mPropTag + " " + mPropValue;
                new ShellCommandRule(setPropCmd, revertSetPropCmd)
                        .apply(base, description).evaluate();
            }
        };
    }

    /**
     * Enables "InputTransportPublisher" debug flag. This prints the key input events dispatched by
     * the system server.
     * adb shell setprop log.tag.InputTransportPublisher DEBUG
     * See {@link com.android.cts.input.DebugInputRule} for more details.
     */
    public static SetPropRule createEnableInputTransportPublisherRule() {
        return new SetPropRule("log.tag.InputTransportPublisher", "DEBUG");
    }
}
