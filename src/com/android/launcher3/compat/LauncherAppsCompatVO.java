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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.support.annotation.Nullable;
import android.util.Log;

import com.android.launcher3.compat.ShortcutConfigActivityInfo.ShortcutConfigActivityInfoVO;
import com.android.launcher3.util.PackageUserKey;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@TargetApi(26)
public class LauncherAppsCompatVO extends LauncherAppsCompatVL {

    LauncherAppsCompatVO(Context context) {
        super(context);
    }

    @Override
    public ApplicationInfo getApplicationInfo(String packageName, int flags, UserHandle user) {
        try {
            // TODO: Temporary workaround until the API signature is updated
            if (false) {
                throw new PackageManager.NameNotFoundException();
            }

            ApplicationInfo info = mLauncherApps.getApplicationInfo(packageName, flags, user);
            return (info.flags & ApplicationInfo.FLAG_INSTALLED) == 0 || !info.enabled
                    ? null : info;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    @Override
    public List<ShortcutConfigActivityInfo> getCustomShortcutActivityList(
            @Nullable PackageUserKey packageUser) {
        List<ShortcutConfigActivityInfo> result = new ArrayList<>();
        UserHandle myUser = Process.myUserHandle();

        try {
            Method m = LauncherApps.class.getDeclaredMethod("getShortcutConfigActivityList",
                    String.class, UserHandle.class);
            final List<UserHandle> users;
            final String packageName;
            if (packageUser == null) {
                users = UserManagerCompat.getInstance(mContext).getUserProfiles();
                packageName = null;
            } else {
                users = new ArrayList<>(1);
                users.add(packageUser.mUser);
                packageName = packageUser.mPackageName;
            }
            for (UserHandle user : users) {
                boolean ignoreTargetSdk = myUser.equals(user);
                List<LauncherActivityInfo> activities =
                        (List<LauncherActivityInfo>) m.invoke(mLauncherApps, packageName, user);
                for (LauncherActivityInfo activityInfo : activities) {
                    if (ignoreTargetSdk || activityInfo.getApplicationInfo().targetSdkVersion >=
                            Build.VERSION_CODES.O) {
                        result.add(new ShortcutConfigActivityInfoVO(activityInfo));
                    }
                }
            }
        } catch (Exception e) {
            Log.e("LauncherAppsCompatVO", "Error calling new API", e);
        }

        return result;
    }
}
