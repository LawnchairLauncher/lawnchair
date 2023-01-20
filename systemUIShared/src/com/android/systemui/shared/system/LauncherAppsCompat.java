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
 * limitations under the License
 */

package com.android.systemui.shared.system;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.pm.LauncherApps;
import android.os.Bundle;
import android.os.UserHandle;

/**
 * Wrapper around LauncherApps.
 */
public abstract class LauncherAppsCompat {

    public static PendingIntent getMainActivityLaunchIntent(LauncherApps launcherApps,
            ComponentName component, Bundle startActivityOptions, UserHandle user) {
        return launcherApps.getMainActivityLaunchIntent(component, startActivityOptions, user);
    }
}
