/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import androidx.test.uiautomator.UiDevice;

import java.io.IOException;
import java.util.regex.Pattern;

class FailureInvestigator {
    private static boolean matches(String regex, CharSequence string) {
        return Pattern.compile(regex).matcher(string).find();
    }

    static int getBugForFailure(CharSequence exception, String testsStartTime) {
        final String logSinceTestsStart;
        try {
            logSinceTestsStart =
                    UiDevice.getInstance(getInstrumentation())
                            .executeShellCommand("logcat -d -t " + testsStartTime.replace(" ", ""));
        } catch (IOException e) {
            return 0;
        }

        if (matches(
                "java.lang.AssertionError: http://go/tapl : Tests are broken by a non-Launcher "
                        + "system error: Phone is locked",
                exception)) {
            if (matches(
                    "BroadcastQueue: Can't deliver broadcast to com.android.systemui.*Crashing it",
                    logSinceTestsStart)) {
                return 147845913;
            }
        } else if (matches("java.lang.AssertionError: Launcher build match not found", exception)) {
            if (matches(
                    "TestStabilityRule: Launcher package: com.google.android.setupwizard",
                    logSinceTestsStart)) {
                return 145935261;
            }
        }

        return 0;
    }
}
