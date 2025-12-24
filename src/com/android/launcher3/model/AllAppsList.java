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
 *
 * Modifications copyright 2021, Lawnchair
 */

package com.android.launcher3.model;

import static com.android.launcher3.icons.cache.CacheLookupFlag.DEFAULT_LOOKUP_FLAG;
import static com.android.launcher3.model.data.AppInfo.COMPONENT_KEY_COMPARATOR;
import static com.android.launcher3.model.data.AppInfo.EMPTY_ARRAY;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.LocaleList;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.launcher3.AppFilter;
import com.android.launcher3.Flags;
import com.android.launcher3.compat.AlphabeticIndexCompat;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.AppsListData;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.repository.AppsListRepository;
import com.android.launcher3.pm.PackageInstallInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.FlagOp;
import com.android.launcher3.util.PackageManagerHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Provider;


/**
 * Stores the list of all applications for the all apps view.
 */
@LauncherAppSingleton
public class AllAppsList {

    private static final String TAG = "AllAppsList";
    private static final boolean DEBUG = true;

    public static final int DEFAULT_APPLICATIONS_NUMBER = 42;

    /** The list off all apps. */
    public final ArrayList<AppInfo> data = new ArrayList<>(DEFAULT_APPLICATIONS_NUMBER);

    @NonNull
    private final IconCache mIconCache;

    @NonNull
    private final AppFilter mAppFilter;

    @NonNull private final Provider<AppsListRepository> mRepo;

    private boolean mDataChanged = false;

    private AlphabeticIndexCompat mIndex;

    /**
     * @see AppsListData#FLAG_HAS_SHORTCUT_PERMISSION
     * @see AppsListData#FLAG_QUIET_MODE_ENABLED
     * @see AppsListData#FLAG_QUIET_MODE_CHANGE_PERMISSION
     * @see AppsListData#FLAG_WORK_PROFILE_QUIET_MODE_ENABLED
     * @see AppsListData#FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED
     */
    private int mFlags;

    /**
     * Boring constructor.
     */
    @Inject
    public AllAppsList(@NonNull IconCache iconCache,
            @NonNull AppFilter appFilter,
            @NonNull Provider<AppsListRepository> repositoryProvider) {
        mIconCache = iconCache;
        mAppFilter = appFilter;
        mRepo = repositoryProvider;
        mIndex = new AlphabeticIndexCompat(LocaleList.getDefault());
    }

    /**
     * Returns true if there have been any changes since last call.
     */
    public boolean getAndResetChangeFlag() {
        boolean result = mDataChanged;
        mDataChanged = false;

        if (Flags.modelRepository() && result) {
            mRepo.get().dispatchChange(getImmutableData());
        }
        return result;
    }

    /**
     * Helper to checking {@link AppsListData#FLAG_HAS_SHORTCUT_PERMISSION}
     */
    public boolean hasShortcutHostPermission() {
        return (mFlags & AppsListData.FLAG_HAS_SHORTCUT_PERMISSION) != 0;
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

    /** Returns an immutable representation of the current data */
    public AppsListData getImmutableData() {
        return new AppsListData(copyData(), mFlags);
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
        if (data.stream().anyMatch(it ->
                it.getTargetComponent().equals(info.componentName) && it.user.equals(info.user))) {
            return;
        }
        if (loadIcon) {
            mIconCache.getTitleAndIcon(info, activityInfo, DEFAULT_LOOKUP_FLAG);
            info.sectionName = mIndex.computeSectionName(info.title);
        } else {
            info.title = "";
        }

        data.add(info);
        mDataChanged = true;
    }

    public void updateSectionName(AppInfo appInfo) {
        appInfo.sectionName = mIndex.computeSectionName(appInfo.title);
    }

    /** Updates the given PackageInstallInfo's associated AppInfo's installation info. */
    public List<AppInfo> updatePromiseInstallInfo(PackageInstallInfo installInfo,
            FlagOp runtimeFlagUpdate) {
        List<AppInfo> updatedAppInfos = new ArrayList<>();
        UserHandle user = installInfo.user;
        for (int i = data.size() - 1; i >= 0; i--) {
            final AppInfo appInfo = data.get(i);
            if (installInfo.packageName.equals(appInfo.getTargetPackage())
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
                    appInfo.runtimeStatusFlags =
                            runtimeFlagUpdate.apply(appInfo.runtimeStatusFlags);
                    if (Flags.modelRepository()) {
                        mRepo.get().dispatchIncrementationUpdate(appInfo);
                    }
                    updatedAppInfos.add(appInfo);
                }
            }
        }
        return updatedAppInfos;
    }

    public void clear() {
        data.clear();
        mDataChanged = false;
        // Reset the index as locales might have changed
        mIndex = new AlphabeticIndexCompat(LocaleList.getDefault());
    }

    /**
     * Remove the apps for the given apk identified by packageName.
     */
    public void removePackage(String packageName, UserHandle user) {
        boolean removed = data.removeIf(
                info -> info.user.equals(user) && packageName.equals(info.getTargetPackage()));
        mDataChanged |= removed;
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
     * @param outRemovedComponents any component removed as a result of this update will
     *                            be added to this set
     */
    public List<LauncherActivityInfo> updatePackage(
            Context context, String packageName, UserHandle user,
            Set<ComponentName> outRemovedComponents) {
        final ApiWrapper apiWrapper = ApiWrapper.INSTANCE.get(context);
        final UserCache userCache = UserCache.getInstance(context);
        final PackageManagerHelper pmHelper = PackageManagerHelper.INSTANCE.get(context);
        final List<LauncherActivityInfo> matches = context.getSystemService(LauncherApps.class)
                .getActivityList(packageName, user);

        Map<ComponentName, LauncherActivityInfo> activityMap = matches.stream().collect(
                Collectors.toMap(LauncherActivityInfo::getComponentName, lai -> lai));

        Iterator<AppInfo> iterator = data.iterator();
        while (iterator.hasNext()) {
            AppInfo appInfo = iterator.next();
            if (user.equals(appInfo.user) && packageName.equals(appInfo.getTargetPackage())) {
                ComponentName cn = appInfo.getTargetComponent();
                // Keep removing entries from the map, so that we are only left with missing entries
                LauncherActivityInfo lai = activityMap.remove(cn);
                if (lai == null) {
                    // Remove any component which is no longer in the list
                    mIconCache.remove(cn, user);
                    outRemovedComponents.add(cn);
                    iterator.remove();
                    if (DEBUG) {
                        Log.w(TAG, "updatePackage: removing unavailable component, cn=" + cn
                                + ", user=" + user);
                    }
                } else {
                    appInfo.intent = AppInfo.makeLaunchIntent(lai);
                    mIconCache.getTitleAndIcon(appInfo, lai, DEFAULT_LOOKUP_FLAG);
                    appInfo.sectionName = mIndex.computeSectionName(appInfo.title);
                    AppInfo.updateRuntimeFlagsForActivityTarget(appInfo, lai,
                            userCache.getUserInfo(user), apiWrapper, pmHelper);
                }
                mDataChanged = true;
            }
        }

        // Add any new activities to the list
        activityMap.values().forEach(lai -> add(new AppInfo(context, lai, user), lai));
        return matches;
    }

    public AppInfo[] copyData() {
        AppInfo[] result = data.toArray(EMPTY_ARRAY);
        Arrays.sort(result, COMPONENT_KEY_COMPARATOR);
        return result;
    }
}
