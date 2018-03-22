/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.os.Process;
import android.os.UserHandle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.PackageInstallerCompat;
import com.android.launcher3.util.FlagOp;
import com.android.launcher3.util.ItemInfoMatcher;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


/**
 * Stores the list of all applications for the all apps view.
 */
public class AllAppsList {
    private static final String TAG = "AllAppsList";

    public static final int DEFAULT_APPLICATIONS_NUMBER = 42;

    /** The list off all apps. */
    public final ArrayList<AppInfo> data = new ArrayList<>(DEFAULT_APPLICATIONS_NUMBER);
    /** The list of apps that have been added since the last notify() call. */
    public ArrayList<AppInfo> added = new ArrayList<>(DEFAULT_APPLICATIONS_NUMBER);
    /** The list of apps that have been removed since the last notify() call. */
    public ArrayList<AppInfo> removed = new ArrayList<>();
    /** The list of apps that have been modified since the last notify() call. */
    public ArrayList<AppInfo> modified = new ArrayList<>();

    private IconCache mIconCache;

    private AppFilter mAppFilter;

    /**
     * Boring constructor.
     */
    public AllAppsList(IconCache iconCache, AppFilter appFilter) {
        mIconCache = iconCache;
        mAppFilter = appFilter;
    }

    /**
     * Add the supplied ApplicationInfo objects to the list, and enqueue it into the
     * list to broadcast when notify() is called.
     *
     * If the app is already in the list, doesn't add it.
     */
    public void add(AppInfo info, LauncherActivityInfo activityInfo) {
        if (!mAppFilter.shouldShowApp(info.componentName, info.user)) {
            return;
        }
        if (findAppInfo(info.componentName, info.user) != null) {
            return;
        }
        mIconCache.getTitleAndIcon(info, activityInfo, true /* useLowResIcon */);

        data.add(info);
        added.add(info);
    }

    public void addPromiseApp(Context context,
                              PackageInstallerCompat.PackageInstallInfo installInfo) {
        ApplicationInfo applicationInfo = LauncherAppsCompat.getInstance(context)
                .getApplicationInfo(installInfo.packageName, 0, Process.myUserHandle());
        // only if not yet installed
        if (applicationInfo == null) {
            PromiseAppInfo info = new PromiseAppInfo(installInfo);
            mIconCache.getTitleAndIcon(info, info.usingLowResIcon);
            data.add(info);
            added.add(info);
        }
    }

    public void removePromiseApp(AppInfo appInfo) {
        // the <em>removed</em> list is handled by the caller
        // so not adding it here
        data.remove(appInfo);
    }

    public void clear() {
        data.clear();
        // TODO: do we clear these too?
        added.clear();
        removed.clear();
        modified.clear();
    }

    public int size() {
        return data.size();
    }

    public AppInfo get(int index) {
        return data.get(index);
    }

    /**
     * Add the icons for the supplied apk called packageName.
     */
    public void addPackage(Context context, String packageName, UserHandle user) {
        final LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(context);
        final List<LauncherActivityInfo> matches = launcherApps.getActivityList(packageName,
                user);

        for (LauncherActivityInfo info : matches) {
            add(new AppInfo(context, info, user), info);
        }
    }

    /**
     * Remove the apps for the given apk identified by packageName.
     */
    public void removePackage(String packageName, UserHandle user) {
        final List<AppInfo> data = this.data;
        for (int i = data.size() - 1; i >= 0; i--) {
            AppInfo info = data.get(i);
            if (info.user.equals(user) && packageName.equals(info.componentName.getPackageName())) {
                removed.add(info);
                data.remove(i);
            }
        }
    }

    /**
     * Updates the disabled flags of apps matching {@param matcher} based on {@param op}.
     */
    public void updateDisabledFlags(ItemInfoMatcher matcher, FlagOp op) {
        final List<AppInfo> data = this.data;
        for (int i = data.size() - 1; i >= 0; i--) {
            AppInfo info = data.get(i);
            if (matcher.matches(info, info.componentName)) {
                info.isDisabled = op.apply(info.isDisabled);
                modified.add(info);
            }
        }
    }

    public void updateIconsAndLabels(HashSet<String> packages, UserHandle user,
            ArrayList<AppInfo> outUpdates) {
        for (AppInfo info : data) {
            if (info.user.equals(user) && packages.contains(info.componentName.getPackageName())) {
                mIconCache.updateTitleAndIcon(info);
                outUpdates.add(info);
            }
        }
    }

    /**
     * Add and remove icons for this package which has been updated.
     */
    public void updatePackage(Context context, String packageName, UserHandle user) {
        final LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(context);
        final List<LauncherActivityInfo> matches = launcherApps.getActivityList(packageName,
                user);
        if (matches.size() > 0) {
            // Find disabled/removed activities and remove them from data and add them
            // to the removed list.
            for (int i = data.size() - 1; i >= 0; i--) {
                final AppInfo applicationInfo = data.get(i);
                if (user.equals(applicationInfo.user)
                        && packageName.equals(applicationInfo.componentName.getPackageName())) {
                    if (!findActivity(matches, applicationInfo.componentName)) {
                        Log.w(TAG, "Shortcut will be removed due to app component name change.");
                        removed.add(applicationInfo);
                        data.remove(i);
                    }
                }
            }

            // Find enabled activities and add them to the adapter
            // Also updates existing activities with new labels/icons
            for (final LauncherActivityInfo info : matches) {
                AppInfo applicationInfo = findAppInfo(info.getComponentName(), user);
                if (applicationInfo == null) {
                    add(new AppInfo(context, info, user), info);
                } else {
                    mIconCache.getTitleAndIcon(applicationInfo, info, true /* useLowResIcon */);
                    modified.add(applicationInfo);
                }
            }
        } else {
            // Remove all data for this package.
            for (int i = data.size() - 1; i >= 0; i--) {
                final AppInfo applicationInfo = data.get(i);
                if (user.equals(applicationInfo.user)
                        && packageName.equals(applicationInfo.componentName.getPackageName())) {
                    removed.add(applicationInfo);
                    mIconCache.remove(applicationInfo.componentName, user);
                    data.remove(i);
                }
            }
        }
    }

    /**
     * Add and remove icons for this package, depending on visibility.
     */
    public void reloadPackages(Context context, UserHandle user) {
        for (final LauncherActivityInfo info : LauncherAppsCompat.getInstance(context).getActivityList(null, user)) {
            AppInfo applicationInfo = findAppInfo(info.getComponentName(), user);
            if (applicationInfo == null) {
                add(new AppInfo(context, info, user), info);
            }
        }

        for (int i = data.size() - 1; i >= 0; i--) {
            final AppInfo applicationInfo = data.get(i);
            if (user.equals(applicationInfo.user) && !mAppFilter.shouldShowApp(applicationInfo.componentName, applicationInfo.user)) {
                removed.add(applicationInfo);
                data.remove(i);
            }
        }
    }

    /**
     * Returns whether <em>apps</em> contains <em>component</em>.
     */
    private static boolean findActivity(List<LauncherActivityInfo> apps,
            ComponentName component) {
        for (LauncherActivityInfo info : apps) {
            if (info.getComponentName().equals(component)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find an AppInfo object for the given componentName
     *
     * @return the corresponding AppInfo or null
     */
    private @Nullable AppInfo findAppInfo(@NonNull ComponentName componentName,
                                          @NonNull UserHandle user) {
        for (AppInfo info: data) {
            if (componentName.equals(info.componentName) && user.equals(info.user)) {
                return info;
            }
        }
        return null;
    }
}
