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

import static com.android.launcher3.model.data.AppInfo.COMPONENT_KEY_COMPARATOR;
import static com.android.launcher3.model.data.AppInfo.EMPTY_ARRAY;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.LocaleList;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.AppFilter;
import com.android.launcher3.compat.AlphabeticIndexCompat;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.pm.PackageInstallInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.FlagOp;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.SafeCloseable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;


/**
 * Stores the list of all applications for the all apps view.
 */
@SuppressWarnings("NewApi")
public class AllAppsList {

    private static final String TAG = "AllAppsList";
    private static final Consumer<AppInfo> NO_OP_CONSUMER = a -> { };
    private static final boolean DEBUG = true;

    public static final int DEFAULT_APPLICATIONS_NUMBER = 42;

    /** The list off all apps. */
    public final ArrayList<AppInfo> data = new ArrayList<>(DEFAULT_APPLICATIONS_NUMBER);

    @NonNull
    private IconCache mIconCache;

    @NonNull
    private AppFilter mAppFilter;

    private boolean mDataChanged = false;
    private Consumer<AppInfo> mRemoveListener = NO_OP_CONSUMER;

    private AlphabeticIndexCompat mIndex;

    /**
     * @see Callbacks#FLAG_HAS_SHORTCUT_PERMISSION
     * @see Callbacks#FLAG_QUIET_MODE_ENABLED
     * @see Callbacks#FLAG_QUIET_MODE_CHANGE_PERMISSION
     * @see Callbacks#FLAG_WORK_PROFILE_QUIET_MODE_ENABLED
     * @see Callbacks#FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED
     */
    private int mFlags;

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
     * Helper to checking {@link Callbacks#FLAG_HAS_SHORTCUT_PERMISSION}
     */
    public boolean hasShortcutHostPermission() {
        return (mFlags & Callbacks.FLAG_HAS_SHORTCUT_PERMISSION) != 0;
    }

    /**
     * Sets or clears the provided flag
     */
    public void setFlags(int flagMask, boolean enabled) {
        if (enabled) {
            mFlags |= flagMask;
        } else {
            mFlags &= ~flagMask;
        }
        mDataChanged = true;
    }

    /**
     * Returns the model flags
     */
    public int getFlags() {
        return mFlags;
    }


    /**
     * Add the supplied ApplicationInfo objects to the list, and enqueue it into the
     * list to broadcast when notify() is called.
     *
     * If the app is already in the list, doesn't add it.
     */
    public void add(AppInfo info, LauncherActivityInfo activityInfo) {
        add(info, activityInfo, true);
    }

    public void add(AppInfo info, LauncherActivityInfo activityInfo, boolean loadIcon) {
        if (!mAppFilter.shouldShowApp(info.componentName)) {
            return;
        }
        if (findAppInfo(info.componentName, info.user) != null) {
            return;
        }
        if (loadIcon) {
            mIconCache.getTitleAndIcon(info, activityInfo, false /* useLowResIcon */);
            info.sectionName = mIndex.computeSectionName(info.title);
        } else {
            info.title = "";
        }

        data.add(info);
        mDataChanged = true;
    }

    @Nullable
    public AppInfo addPromiseApp(Context context, PackageInstallInfo installInfo) {
        return addPromiseApp(context, installInfo, true);
    }

    @Nullable
    public AppInfo addPromiseApp(
            Context context, PackageInstallInfo installInfo, boolean loadIcon) {
        // only if not yet installed
        if (PackageManagerHelper.INSTANCE.get(context)
                .isAppInstalled(installInfo.packageName, installInfo.user)) {
            return null;
        }
        AppInfo promiseAppInfo = new AppInfo(installInfo);

        if (loadIcon) {
            mIconCache.getTitleAndIcon(promiseAppInfo, promiseAppInfo.usingLowResIcon());
            promiseAppInfo.sectionName = mIndex.computeSectionName(promiseAppInfo.title);
        } else {
            promiseAppInfo.title = "";
        }

        data.add(promiseAppInfo);
        mDataChanged = true;

        return promiseAppInfo;
    }

    public void updateSectionName(AppInfo appInfo) {
        appInfo.sectionName = mIndex.computeSectionName(appInfo.title);

    }

    /** Updates the given PackageInstallInfo's associated AppInfo's installation info. */
    public List<AppInfo> updatePromiseInstallInfo(PackageInstallInfo installInfo) {
        List<AppInfo> updatedAppInfos = new ArrayList<>();
        UserHandle user = installInfo.user;
        for (int i = data.size() - 1; i >= 0; i--) {
            final AppInfo appInfo = data.get(i);
            final ComponentName tgtComp = appInfo.getTargetComponent();
            if (tgtComp != null && tgtComp.getPackageName().equals(installInfo.packageName)
                    && appInfo.user.equals(user)) {
                if (installInfo.state == PackageInstallInfo.STATUS_INSTALLED_DOWNLOADING
                        || installInfo.state == PackageInstallInfo.STATUS_INSTALLING
                        // In case unarchival fails, we would want to keep the icon and update
                        // back the progress to 0 for the all apps view without removing the
                        // icon, which is contrary to what happens during normal app installation
                        // flow.
                        || (installInfo.state == PackageInstallInfo.STATUS_FAILED
                                && appInfo.isArchived())) {
                    if (appInfo.isAppStartable()
                            && installInfo.state == PackageInstallInfo.STATUS_INSTALLING
                            && !appInfo.isArchived()) {
                        continue;
                    }
                    appInfo.setProgressLevel(installInfo);

                    updatedAppInfos.add(appInfo);
                } else if (installInfo.state == PackageInstallInfo.STATUS_FAILED
                        && !appInfo.isAppStartable()) {
                    if (DEBUG) {
                        Log.w(TAG, "updatePromiseInstallInfo: removing app due to install"
                                + " failure and appInfo not startable."
                                + " package=" + appInfo.getTargetPackage());
                    }
                    removeApp(i);
                }
            }
        }
        return updatedAppInfos;
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
    public List<LauncherActivityInfo> addPackage(
            Context context, String packageName, UserHandle user) {
        List<LauncherActivityInfo> activities = context.getSystemService(LauncherApps.class)
                .getActivityList(packageName, user);

        for (LauncherActivityInfo info : activities) {
            add(new AppInfo(context, info, user), info);
        }

        return activities;
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
    public void updateDisabledFlags(Predicate<ItemInfo> matcher, FlagOp op) {
        final List<AppInfo> data = this.data;
        for (int i = data.size() - 1; i >= 0; i--) {
            AppInfo info = data.get(i);
            if (matcher.test(info)) {
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
    public List<LauncherActivityInfo> updatePackage(
            Context context, String packageName, UserHandle user) {
        final ApiWrapper apiWrapper = ApiWrapper.INSTANCE.get(context);
        final UserCache userCache = UserCache.getInstance(context);
        final PackageManagerHelper pmHelper = PackageManagerHelper.INSTANCE.get(context);
        final List<LauncherActivityInfo> matches = context.getSystemService(LauncherApps.class)
                .getActivityList(packageName, user);
        if (matches.size() > 0) {
            // Find disabled/removed activities and remove them from data and add them
            // to the removed list.
            for (int i = data.size() - 1; i >= 0; i--) {
                final AppInfo applicationInfo = data.get(i);
                if (user.equals(applicationInfo.user)
                        && packageName.equals(applicationInfo.componentName.getPackageName())) {
                    if (!findActivity(matches, applicationInfo.componentName)) {
                        if (DEBUG) {
                            Log.w(TAG, "Changing shortcut target due to app component name change."
                                    + " package=" + packageName);
                        }
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
                    Intent launchIntent = AppInfo.makeLaunchIntent(info);

                    mIconCache.getTitleAndIcon(applicationInfo, info, false /* useLowResIcon */);
                    applicationInfo.sectionName = mIndex.computeSectionName(applicationInfo.title);
                    applicationInfo.intent = launchIntent;
                    AppInfo.updateRuntimeFlagsForActivityTarget(applicationInfo, info,
                            userCache.getUserInfo(user), apiWrapper, pmHelper);
                    mDataChanged = true;
                }
            }
        } else {
            // Remove all data for this package.
            if (DEBUG) {
                Log.w(TAG, "updatePromiseInstallInfo: no Activities matched updated package,"
                        + " removing all apps from package=" + packageName);
            }
            for (int i = data.size() - 1; i >= 0; i--) {
                final AppInfo applicationInfo = data.get(i);
                if (user.equals(applicationInfo.user)
                        && packageName.equals(applicationInfo.componentName.getPackageName())) {
                    mIconCache.remove(applicationInfo.componentName, user);
                    removeApp(i);
                }
            }
        }

        return matches;
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
    public @Nullable AppInfo findAppInfo(@NonNull ComponentName componentName,
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
