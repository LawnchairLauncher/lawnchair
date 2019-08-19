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

package com.android.launcher3.model;

import static com.android.launcher3.AppInfo.COMPONENT_KEY_COMPARATOR;
import static com.android.launcher3.AppInfo.EMPTY_ARRAY;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.os.LocaleList;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

import com.android.launcher3.AppFilter;
import com.android.launcher3.AppInfo;
import com.android.launcher3.PromiseAppInfo;
import com.android.launcher3.compat.AlphabeticIndexCompat;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.PackageInstallerCompat;
import com.android.launcher3.compat.PackageInstallerCompat.PackageInstallInfo;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.util.FlagOp;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.SafeCloseable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


/**
 * Stores the list of all applications for the all apps view.
 */
public class AllAppsList {

    private static final String TAG = "AllAppsList";
    private static final Consumer<AppInfo> NO_OP_CONSUMER = a -> { };


    public static final int DEFAULT_APPLICATIONS_NUMBER = 42;

    /** The list off all apps. */
    public final ArrayList<AppInfo> data = new ArrayList<>(DEFAULT_APPLICATIONS_NUMBER);

    private IconCache mIconCache;
    private AppFilter mAppFilter;

    private boolean mDataChanged = false;
    private Consumer<AppInfo> mRemoveListener = NO_OP_CONSUMER;

    private AlphabeticIndexCompat mIndex;

    /**
     * Boring constructor.
     */
    public AllAppsList(IconCache iconCache, AppFilter appFilter) {
        mIconCache = iconCache;
        mAppFilter = appFilter;
        mIndex = new AlphabeticIndexCompat(LocaleList.getDefault());
    }

    /**
     * Returns true if there have been any changes since last call.
     */
    public boolean getAndResetChangeFlag() {
        boolean result = mDataChanged;
        mDataChanged = false;
        return result;
    }

    /**
     * Add the supplied ApplicationInfo objects to the list, and enqueue it into the
     * list to broadcast when notify() is called.
     *
     * If the app is already in the list, doesn't add it.
     */
    public void add(AppInfo info, LauncherActivityInfo activityInfo) {
        if (!mAppFilter.shouldShowApp(info.componentName)) {
            return;
        }
        if (findAppInfo(info.componentName, info.user) != null) {
            return;
        }
        mIconCache.getTitleAndIcon(info, activityInfo, true /* useLowResIcon */);
        info.sectionName = mIndex.computeSectionName(info.title);

        data.add(info);
        mDataChanged = true;
    }

    public void addPromiseApp(Context context,
                              PackageInstallerCompat.PackageInstallInfo installInfo) {
        ApplicationInfo applicationInfo = LauncherAppsCompat.getInstance(context)
                .getApplicationInfo(installInfo.packageName, 0, installInfo.user);
        // only if not yet installed
        if (applicationInfo == null) {
            PromiseAppInfo info = new PromiseAppInfo(installInfo);
            mIconCache.getTitleAndIcon(info, info.usingLowResIcon());
            info.sectionName = mIndex.computeSectionName(info.title);

            data.add(info);
            mDataChanged = true;
        }
    }

    public PromiseAppInfo updatePromiseInstallInfo(PackageInstallInfo installInfo) {
        UserHandle user = Process.myUserHandle();
        for (int i=0; i < data.size(); i++) {
            final AppInfo appInfo = data.get(i);
            final ComponentName tgtComp = appInfo.getTargetComponent();
            if (tgtComp != null && tgtComp.getPackageName().equals(installInfo.packageName)
                    && appInfo.user.equals(user)
                    && appInfo instanceof PromiseAppInfo) {
                final PromiseAppInfo promiseAppInfo = (PromiseAppInfo) appInfo;
                if (installInfo.state == PackageInstallerCompat.STATUS_INSTALLING) {
                    promiseAppInfo.level = installInfo.progress;
                    return promiseAppInfo;
                } else if (installInfo.state == PackageInstallerCompat.STATUS_FAILED) {
                    removeApp(i);
                }
            }
        }
        return null;
    }

    private void removeApp(int index) {
        AppInfo removed = data.remove(index);
        if (removed != null) {
            mDataChanged = true;
            mRemoveListener.accept(removed);
        }
    }

    public void clear() {
        data.clear();
        mDataChanged = false;
        // Reset the index as locales might have changed
        mIndex = new AlphabeticIndexCompat(LocaleList.getDefault());
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
                removeApp(i);
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
                info.runtimeStatusFlags = op.apply(info.runtimeStatusFlags);
                mDataChanged = true;
            }
        }
    }

    public void updateIconsAndLabels(HashSet<String> packages, UserHandle user) {
        for (AppInfo info : data) {
            if (info.user.equals(user) && packages.contains(info.componentName.getPackageName())) {
                mIconCache.updateTitleAndIcon(info);
                info.sectionName = mIndex.computeSectionName(info.title);
                mDataChanged = true;
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
                        Log.w(TAG, "Changing shortcut target due to app component name change.");
                        removeApp(i);
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
                    applicationInfo.sectionName = mIndex.computeSectionName(applicationInfo.title);

                    mDataChanged = true;
                }
            }
        } else {
            // Remove all data for this package.
            for (int i = data.size() - 1; i >= 0; i--) {
                final AppInfo applicationInfo = data.get(i);
                if (user.equals(applicationInfo.user)
                        && packageName.equals(applicationInfo.componentName.getPackageName())) {
                    mIconCache.remove(applicationInfo.componentName, user);
                    removeApp(i);
                }
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

    public AppInfo[] copyData() {
        AppInfo[] result = data.toArray(EMPTY_ARRAY);
        Arrays.sort(result, COMPONENT_KEY_COMPARATOR);
        return result;
    }

    public SafeCloseable trackRemoves(Consumer<AppInfo> removeListener) {
        mRemoveListener = removeListener;

        return () -> mRemoveListener = NO_OP_CONSUMER;
    }
}
