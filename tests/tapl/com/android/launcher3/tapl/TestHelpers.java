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

package com.android.launcher3.tapl;

import static androidx.test.InstrumentationRegistry.getInstrumentation;
import static androidx.test.InstrumentationRegistry.getTargetContext;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.DropBoxManager;

import org.junit.Assert;

import java.util.Date;
import java.util.List;

public class TestHelpers {

    private static Boolean sIsInLauncherProcess;

    public static boolean isInLauncherProcess() {
        if (sIsInLauncherProcess == null) {
            sIsInLauncherProcess = initIsInLauncherProcess();
        }
        return sIsInLauncherProcess;
    }

    private static boolean initIsInLauncherProcess() {
        ActivityInfo info = getLauncherInMyProcess();

        // If we are in the same process, we can instantiate the class name.
        try {
            Class launcherClazz = Class.forName("com.android.launcher3.Launcher");
            return launcherClazz.isAssignableFrom(Class.forName(info.name));
        } catch (Exception e) {
            return false;
        }
    }

    public static Intent getHomeIntentInPackage(Context context) {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(context.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    public static ActivityInfo getLauncherInMyProcess() {
        Instrumentation instrumentation = getInstrumentation();
        if (instrumentation.getTargetContext() == null) {
            return null;
        }

        List<ResolveInfo> launchers = getTargetContext().getPackageManager()
                .queryIntentActivities(getHomeIntentInPackage(getTargetContext()), 0);
        if (launchers.size() != 1) {
            return null;
        }
        return launchers.get(0).activityInfo;
    }

    public static String getOverviewPackageName() {
        Resources res = Resources.getSystem();
        int id = res.getIdentifier("config_recentsComponentName", "string", "android");
        if (id != 0) {
            return ComponentName.unflattenFromString(res.getString(id)).getPackageName();
        }
        return "com.android.systemui";
    }

    private static String truncateCrash(String text, int maxLines) {
        String[] lines = text.split("\\r?\\n");
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < maxLines && i < lines.length; i++) {
            ret.append(lines[i]);
            ret.append('\n');
        }
        if (lines.length > maxLines) {
            ret.append("... ");
            ret.append(lines.length - maxLines);
            ret.append(" more lines truncated ...\n");
        }
        return ret.toString();
    }

    private static String checkCrash(Context context, String label, long startTime) {
        DropBoxManager dropbox = (DropBoxManager) context.getSystemService(Context.DROPBOX_SERVICE);
        Assert.assertNotNull("Unable access the DropBoxManager service", dropbox);

        long timestamp = startTime;
        DropBoxManager.Entry entry;
        StringBuilder errorDetails = new StringBuilder();
        while (null != (entry = dropbox.getNextEntry(label, timestamp))) {
            errorDetails.append("------------------------------\n");
            timestamp = entry.getTimeMillis();
            errorDetails.append(new Date(timestamp));
            errorDetails.append(": ");
            errorDetails.append(entry.getTag());
            errorDetails.append(": ");
            final String dropboxSnippet = entry.getText(4096);
            if (dropboxSnippet != null) errorDetails.append(truncateCrash(dropboxSnippet, 40));
            errorDetails.append("    ...\n");
            entry.close();
        }
        return errorDetails.length() != 0 ? errorDetails.toString() : null;
    }

    public static String getSystemHealthMessage(Context context, long startTime) {
        try {
            StringBuilder errors = new StringBuilder();

            final String[] labels = {
                    "system_app_anr",
                    "system_app_crash",
                    "system_app_native_crash",
                    "system_server_anr",
                    "system_server_crash",
                    "system_server_native_crash",
                    "system_server_watchdog",
            };

            for (String label : labels) {
                final String crash = checkCrash(context, label, startTime);
                if (crash != null) errors.append(crash);
            }

            return errors.length() != 0
                    ? "Current time: " + new Date(System.currentTimeMillis()) + "\n" + errors
                    : null;
        } catch (Exception e) {
            return "Failed to get system health diags, maybe build your test via .bp instead of "
                    + ".mk? " + android.util.Log.getStackTraceString(e);
        }
    }
}
