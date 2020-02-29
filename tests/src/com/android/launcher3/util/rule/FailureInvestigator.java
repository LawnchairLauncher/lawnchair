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

    static class LogcatMatch {
        String logcatPattern;
        int bug;

        LogcatMatch(String logcatPattern, int bug) {
            this.logcatPattern = logcatPattern;
            this.bug = bug;
        }
    }

    static class ExceptionMatch {
        String exceptionPattern;
        LogcatMatch[] logcatMatches;

        ExceptionMatch(String exceptionPattern, LogcatMatch[] logcatMatches) {
            this.exceptionPattern = exceptionPattern;
            this.logcatMatches = logcatMatches;
        }
    }

    private static final ExceptionMatch[] EXCEPTION_MATCHES = {
            new ExceptionMatch(
                    "java.lang.AssertionError: http://go/tapl : Tests are broken by a "
                            + "non-Launcher system error: (Phone is locked|Screen is empty)",
                    new LogcatMatch[]{
                            new LogcatMatch(
                                    "BroadcastQueue: Can't deliver broadcast to com.android"
                                            + ".systemui.*Crashing it",
                                    147845913),
                            new LogcatMatch(
                                    "Attempt to invoke virtual method 'boolean android\\"
                                            + ".graphics\\.Bitmap\\.isRecycled\\(\\)' on a null "
                                            + "object reference",
                                    148424291),
                            new LogcatMatch(
                                    "java\\.lang\\.IllegalArgumentException\\: Ranking map "
                                            + "doesn't contain key",
                                    148570537),
                    }),
            new ExceptionMatch("Launcher didn't initialize",
                    new LogcatMatch[]{
                            new LogcatMatch(
                                    "ActivityManager: Reason: executing service com.google"
                                            + ".android.apps.nexuslauncher/com.android.launcher3"
                                            + ".notification.NotificationListener",
                                    148238677),
                    }),
    };

    static int getBugForFailure(CharSequence exception) {
        if ("com.google.android.setupwizard".equals(
                UiDevice.getInstance(getInstrumentation()).getLauncherPackageName())) {
            return 145935261;
        }

        if (matches("java\\.lang\\.AssertionError\\: http\\:\\/\\/go\\/tapl \\: want to get "
                + "workspace object; Presence of recents button doesn't match the interaction "
                + "mode, mode\\=ZERO_BUTTON, hasRecents\\=true", exception)) {
            return 148422894;
        }

        final String logSinceBoot;
        try {
            final String systemBootTime =
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(
                            new Date(System.currentTimeMillis() - SystemClock.elapsedRealtime()));

            logSinceBoot =
                    UiDevice.getInstance(getInstrumentation())
                            .executeShellCommand("logcat -d -t " + systemBootTime.replace(" ", ""));
        } catch (IOException | OutOfMemoryError e) {
            return 0;
        }

        if (matches("android\\:\\:uirenderer\\:\\:renderthread\\:\\:EglManager\\:\\:swapBuffers",
                logSinceBoot)) {
            return 148529608;
        }

        for (ExceptionMatch exceptionMatch : EXCEPTION_MATCHES) {
            if (matches(exceptionMatch.exceptionPattern, exception)) {
                for (LogcatMatch logcatMatch : exceptionMatch.logcatMatches) {
                    if (matches(logcatMatch.logcatPattern, logSinceBoot)) {
                        return logcatMatch.bug;
                    }
                }
                break;
            }
        }

        return 0;
    }
}
