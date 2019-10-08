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
}
