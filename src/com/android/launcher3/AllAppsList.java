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

import com.android.launcher3.compat.LauncherActivityInfoCompat;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserHandleCompat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


/**
 * Stores the list of all applications for the all apps view.
 */
class AllAppsList {
    public static final int DEFAULT_APPLICATIONS_NUMBER = 42;

    /** The list off all apps. */
    public ArrayList<AppInfo> data =
            new ArrayList<AppInfo>(DEFAULT_APPLICATIONS_NUMBER);
    /** The list of apps that have been added since the last notify() call. */
    public ArrayList<AppInfo> added =
            new ArrayList<AppInfo>(DEFAULT_APPLICATIONS_NUMBER);
    /** The list of apps that have been removed since the last notify() call. */
    public ArrayList<AppInfo> removed = new ArrayList<AppInfo>();
    /** The list of apps that have been modified since the last notify() call. */
    public ArrayList<AppInfo> modified = new ArrayList<AppInfo>();

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
    public void add(AppInfo info) {
        if (mAppFilter != null && !mAppFilter.shouldShowApp(info.componentName)) {
            return;
        }
        if (findActivity(data, info.componentName, info.user)) {
            return;
        }
        data.add(info);
        added.add(info);
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
    public void addPackage(Context context, String packageName, UserHandleCompat user) {
        final LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(context);
        final List<LauncherActivityInfoCompat> matches = launcherApps.getActivityList(packageName,
                user);

        for (LauncherActivityInfoCompat info : matches) {
            add(new AppInfo(context, info, user, mIconCache));
        }
    }

    /**
     * Remove the apps for the given apk identified by packageName.
     */
    public void removePackage(String packageName, UserHandleCompat user) {
        final List<AppInfo> data = this.data;
        for (int i = data.size() - 1; i >= 0; i--) {
            AppInfo info = data.get(i);
            final ComponentName component = info.intent.getComponent();
            if (info.user.equals(user) && packageName.equals(component.getPackageName())) {
                removed.add(info);
                data.remove(i);
            }
        }
    }

    public void updateIconsAndLabels(HashSet<String> packages, UserHandleCompat user,
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
    public void updatePackage(Context context, String packageName, UserHandleCompat user) {
        final LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(context);
        final List<LauncherActivityInfoCompat> matches = launcherApps.getActivityList(packageName,
                user);
        if (matches.size() > 0) {
            // Find disabled/removed activities and remove them from data and add them
            // to the removed list.
            for (int i = data.size() - 1; i >= 0; i--) {
                final AppInfo applicationInfo = data.get(i);
                final ComponentName component = applicationInfo.intent.getComponent();
                if (user.equals(applicationInfo.user)
                        && packageName.equals(component.getPackageName())) {
                    if (!findActivity(matches, component)) {
                        removed.add(applicationInfo);
                        data.remove(i);
                    }
                }
            }

            // Find enabled activities and add them to the adapter
            // Also updates existing activities with new labels/icons
            for (final LauncherActivityInfoCompat info : matches) {
                AppInfo applicationInfo = findApplicationInfoLocked(
                        info.getComponentName().getPackageName(), user,
                        info.getComponentName().getClassName());
                if (applicationInfo == null) {
                    add(new AppInfo(context, info, user, mIconCache));
                } else {
                    mIconCache.getTitleAndIcon(applicationInfo, info, true /* useLowResIcon */);
                    modified.add(applicationInfo);
                }
            }
        } else {
            // Remove all data for this package.
            for (int i = data.size() - 1; i >= 0; i--) {
                final AppInfo applicationInfo = data.get(i);
                final ComponentName component = applicationInfo.intent.getComponent();
                if (user.equals(applicationInfo.user)
                        && packageName.equals(component.getPackageName())) {
                    removed.add(applicationInfo);
                    mIconCache.remove(component, user);
                    data.remove(i);
                }
            }
        }
    }


    /**
     * Returns whether <em>apps</em> contains <em>component</em>.
     */
    private static boolean findActivity(List<LauncherActivityInfoCompat> apps,
            ComponentName component) {
        for (LauncherActivityInfoCompat info : apps) {
            if (info.getComponentName().equals(component)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Query the launcher apps service for whether the supplied package has
     * MAIN/LAUNCHER activities in the supplied package.
     */
    static boolean packageHasActivities(Context context, String packageName,
            UserHandleCompat user) {
        final LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(context);
        return launcherApps.getActivityList(packageName, user).size() > 0;
    }

    /**
     * Returns whether <em>apps</em> contains <em>component</em>.
     */
    private static boolean findActivity(ArrayList<AppInfo> apps, ComponentName component,
            UserHandleCompat user) {
        final int N = apps.size();
        for (int i = 0; i < N; i++) {
            final AppInfo info = apps.get(i);
            if (info.user.equals(user) && info.componentName.equals(component)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find an ApplicationInfo object for the given packageName and className.
     */
    private AppInfo findApplicationInfoLocked(String packageName, UserHandleCompat user,
            String className) {
        for (AppInfo info: data) {
            final ComponentName component = info.intent.getComponent();
            if (user.equals(info.user) && packageName.equals(component.getPackageName())
                    && className.equals(component.getClassName())) {
                return info;
            }
        }
        return null;
    }
}
