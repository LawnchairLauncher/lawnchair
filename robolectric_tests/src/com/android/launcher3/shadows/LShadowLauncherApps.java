/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.launcher3.shadows;

import static org.robolectric.util.ReflectionHelpers.ClassParameter;
import static org.robolectric.util.ReflectionHelpers.callConstructor;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;
import android.util.ArraySet;

import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.PackageUserKey;

import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowLauncherApps;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Extension of {@link ShadowLauncherApps} with missing shadow methods
 */
@Implements(value = LauncherApps.class)
public class LShadowLauncherApps extends ShadowLauncherApps {

    public final ArraySet<PackageUserKey> disabledApps = new ArraySet<>();
    public final ArraySet<ComponentKey> disabledActivities = new ArraySet<>();

    @Implementation
    @Override
    protected List<ShortcutInfo> getShortcuts(LauncherApps.ShortcutQuery query, UserHandle user) {
        try {
            return super.getShortcuts(query, user);
        } catch (UnsupportedOperationException e) {
            return Collections.emptyList();
        }
    }

    @Implementation
    protected boolean isPackageEnabled(String packageName, UserHandle user) {
        return !disabledApps.contains(new PackageUserKey(packageName, user));
    }

    @Implementation
    protected boolean isActivityEnabled(ComponentName component, UserHandle user) {
        return !disabledActivities.contains(new ComponentKey(component, user));
    }

    @Implementation
    protected LauncherActivityInfo resolveActivity(Intent intent, UserHandle user) {
        ResolveInfo ri = RuntimeEnvironment.application.getPackageManager()
                .resolveActivity(intent, 0);
        return ri == null ? null : getLauncherActivityInfo(ri.activityInfo, user);
    }

    public LauncherActivityInfo getLauncherActivityInfo(
            ActivityInfo activityInfo, UserHandle user) {
        return callConstructor(LauncherActivityInfo.class,
                ClassParameter.from(Context.class, RuntimeEnvironment.application),
                ClassParameter.from(ActivityInfo.class, activityInfo),
                ClassParameter.from(UserHandle.class, user));
    }

    @Implementation
    public ApplicationInfo getApplicationInfo(String packageName, int flags, UserHandle user)
            throws PackageManager.NameNotFoundException {
        return RuntimeEnvironment.application.getPackageManager()
                .getApplicationInfo(packageName, flags);
    }

    @Implementation
    public List<LauncherActivityInfo> getActivityList(String packageName, UserHandle user) {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(packageName);
        return RuntimeEnvironment.application.getPackageManager().queryIntentActivities(intent, 0)
                .stream()
                .map(ri -> getLauncherActivityInfo(ri.activityInfo, user))
                .collect(Collectors.toList());
    }

    @Implementation
    public boolean hasShortcutHostPermission() {
        return true;
    }

    @Implementation
    public List<PackageInstaller.SessionInfo> getAllPackageInstallerSessions() {
        return RuntimeEnvironment.application.getPackageManager().getPackageInstaller()
                .getAllSessions();
    }

    @Implementation
    public void registerPackageInstallerSessionCallback(
            Executor executor, PackageInstaller.SessionCallback callback) {
    }

    @Override
    protected List<LauncherActivityInfo> getShortcutConfigActivityList(String packageName,
            UserHandle user) {
        Intent intent = new Intent(Intent.ACTION_CREATE_SHORTCUT).setPackage(packageName);
        return RuntimeEnvironment.application.getPackageManager().queryIntentActivities(intent, 0)
                .stream()
                .map(ri -> getLauncherActivityInfo(ri.activityInfo, user))
                .collect(Collectors.toList());
    }
}
