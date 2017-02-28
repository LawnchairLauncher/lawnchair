/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.compat;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.UserHandle;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import com.android.launcher3.compat.ShortcutConfigActivityInfo.*;

public class LauncherAppsCompatVO extends LauncherAppsCompatVL {

    LauncherAppsCompatVO(Context context) {
        super(context);
    }

    @Override
    public ApplicationInfo getApplicationInfo(String packageName, int flags, UserHandle user) {
        ApplicationInfo info = mLauncherApps.getApplicationInfo(packageName, flags, user);
        return info == null || (info.flags & ApplicationInfo.FLAG_INSTALLED) == 0 ? null : info;
    }

    @Override
    public List<ShortcutConfigActivityInfo> getCustomShortcutActivityList() {
        List<ShortcutConfigActivityInfo> result = new ArrayList<>();

        try {
            Method m = LauncherApps.class.getDeclaredMethod("getShortcutConfigActivityList",
                    String.class, UserHandle.class);
            for (UserHandle user : UserManagerCompat.getInstance(mContext).getUserProfiles()) {
                List<LauncherActivityInfo> activities =
                        (List<LauncherActivityInfo>) m.invoke(mLauncherApps, null, user);
                for (LauncherActivityInfo activityInfo : activities) {
                    result.add(new ShortcutConfigActivityInfoVO(activityInfo));
                }
            }
        } catch (Exception e) {
            Log.e("LauncherAppsCompatVO", "Error calling new API", e);
        }

        return result;
    }
}
