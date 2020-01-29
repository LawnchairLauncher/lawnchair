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

import android.os.SystemClock;

import androidx.test.uiautomator.UiDevice;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

class FailureInvestigator {
    private static boolean matches(String regex, CharSequence string) {
        return Pattern.compile(regex).matcher(string).find();
    }

    static int getBugForFailure(CharSequence exception) {
        if ("com.google.android.setupwizard".equals(
                UiDevice.getInstance(getInstrumentation()).getLauncherPackageName())) {
            return 145935261;
        }

        final String logSinceBoot;
        try {
            final String systemBootTime =
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(
                            new Date(System.currentTimeMillis() - SystemClock.elapsedRealtime()));

            logSinceBoot =
                    UiDevice.getInstance(getInstrumentation())
                            .executeShellCommand("logcat -d -t " + systemBootTime.replace(" ", ""));
        } catch (IOException e) {
            return 0;
        }

        if (matches(
                "java.lang.AssertionError: http://go/tapl : Tests are broken by a non-Launcher "
                        + "system error: Phone is locked",
                exception)) {
            if (matches(
                    "BroadcastQueue: Can't deliver broadcast to com.android.systemui.*Crashing it",
                    logSinceBoot)) {
                return 147845913;
            }
            if (matches(
                    "Attempt to invoke virtual method 'boolean android\\.graphics\\.Bitmap\\"
                            + ".isRecycled\\(\\)' on a null object reference",
                    logSinceBoot)) {
                return 148424291;
            }
            if (matches(
                    "java\\.lang\\.IllegalArgumentException\\: Ranking map doesn't contain key",
                    logSinceBoot)) {
                return 148570537;
            }
        } else if (matches("java.lang.AssertionError: Launcher build match not found", exception)) {
            if (matches(
                    "TestStabilityRule: Launcher package: com.google.android.setupwizard",
                    logSinceBoot)) {
                return 145935261;
            }
        } else if (matches("Launcher didn't initialize", exception)) {
            if (matches(
                    "ActivityManager: Reason: executing service com.google.android.apps"
                            + ".nexuslauncher/com.android.launcher3.notification"
                            + ".NotificationListener",
                    logSinceBoot)) {
                return 148238677;
            }
        }

        return 0;
    }
}
