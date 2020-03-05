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

package com.android.launcher3.icons;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.launcher3.AppInfo;
import com.android.launcher3.IconProvider;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.ItemInfoWithIcon;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.Utilities;
import com.android.launcher3.WorkspaceItemInfo;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.ComponentWithLabel.ComponentCachingLogic;
import com.android.launcher3.icons.cache.BaseIconCache;
import com.android.launcher3.icons.cache.CachingLogic;
import com.android.launcher3.icons.cache.HandlerRunnable;
import com.android.launcher3.model.PackageItemInfo;
import com.android.launcher3.util.InstantAppResolver;
import com.android.launcher3.util.Preconditions;

import java.util.function.Supplier;

/**
 * Cache of application icons.  Icons can be made from any thread.
 */
public class IconCache extends BaseIconCache {

    private static final String TAG = "Launcher.IconCache";

    private final CachingLogic<ComponentWithLabel> mComponentWithLabelCachingLogic;
    private final CachingLogic<LauncherActivityInfo> mLauncherActivityInfoCachingLogic;

    private final LauncherAppsCompat mLauncherApps;
    private final UserManagerCompat mUserManager;
    private final InstantAppResolver mInstantAppResolver;
    private final IconProvider mIconProvider;

    private int mPendingIconRequestCount = 0;

    public IconCache(Context context, InvariantDeviceProfile inv) {
        super(context, LauncherFiles.APP_ICONS_DB, MODEL_EXECUTOR.getLooper(),
                inv.fillResIconDpi, inv.iconBitmapSize, true /* inMemoryCache */);
        mComponentWithLabelCachingLogic = new ComponentCachingLogic(context, false);
        mLauncherActivityInfoCachingLogic = LauncherActivityCachingLogic.newInstance(context);
        mLauncherApps = LauncherAppsCompat.getInstance(mContext);
        mUserManager = UserManagerCompat.getInstance(mContext);
        mInstantAppResolver = InstantAppResolver.newInstance(mContext);
        mIconProvider = IconProvider.INSTANCE.get(context);
    }

    @Override
    protected long getSerialNumberForUser(UserHandle user) {
        return mUserManager.getSerialNumberForUser(user);
    }

    @Override
    protected boolean isInstantApp(ApplicationInfo info) {
        return mInstantAppResolver.isInstantApp(info);
    }

    @Override
    protected BaseIconFactory getIconFactory() {
        return LauncherIcons.obtain(mContext);
    }

    /**
     * Updates the entries related to the given package in memory and persistent DB.
     */
    public synchronized void updateIconsForPkg(String packageName, UserHandle user) {
        removeIconsForPkg(packageName, user);
        try {
            PackageInfo info = mPackageManager.getPackageInfo(packageName,
                    PackageManager.GET_UNINSTALLED_PACKAGES);
            long userSerial = mUserManager.getSerialNumberForUser(user);
            for (LauncherActivityInfo app : mLauncherApps.getActivityList(packageName, user)) {
                addIconToDBAndMemCache(app, mLauncherActivityInfoCachingLogic, info, userSerial,
                        false /*replace existing*/);
            }
        } catch (NameNotFoundException e) {
            Log.d(TAG, "Package not found", e);
        }
    }

    /**
     * Fetches high-res icon for the provided ItemInfo and updates the caller when done.
     * @return a request ID that can be used to cancel the request.
     */
    public IconLoadRequest updateIconInBackground(final ItemInfoUpdateReceiver caller,
            final ItemInfoWithIcon info) {
        Preconditions.assertUIThread();
        if (mPendingIconRequestCount <= 0) {
            MODEL_EXECUTOR.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
        }
        mPendingIconRequestCount ++;

        IconLoadRequest request = new IconLoadRequest(mWorkerHandler, this::onIconRequestEnd) {
            @Override
            public void run() {
                if (info instanceof AppInfo || info instanceof WorkspaceItemInfo) {
                    getTitleAndIcon(info, false);
                } else if (info instanceof PackageItemInfo) {
                    getTitleAndIconForApp((PackageItemInfo) info, false);
                }
                MAIN_EXECUTOR.execute(() -> {
                    caller.reapplyItemInfo(info);
                    onEnd();
                });
            }
        };
        Utilities.postAsyncCallback(mWorkerHandler, request);
        return request;
    }

    private void onIconRequestEnd() {
        mPendingIconRequestCount --;
        if (mPendingIconRequestCount <= 0) {
            MODEL_EXECUTOR.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        }
    }

    /**
     * Updates {@param application} only if a valid entry is found.
     */
    public synchronized void updateTitleAndIcon(AppInfo application) {
        CacheEntry entry = cacheLocked(application.componentName,
                application.user, () -> null, mLauncherActivityInfoCachingLogic,
                false, application.usingLowResIcon());
        if (entry.icon != null && !isDefaultIcon(entry.icon, application.user)) {
            applyCacheEntry(entry, application);
        }
    }

    /**
     * Fill in {@param info} with the icon and label for {@param activityInfo}
     */
    public synchronized void getTitleAndIcon(ItemInfoWithIcon info,
            LauncherActivityInfo activityInfo, boolean useLowResIcon) {
        // If we already have activity info, no need to use package icon
        getTitleAndIcon(info, () -> activityInfo, false, useLowResIcon);
    }

    /**
     * Fill in {@param info} with the icon and label. If the
     * corresponding activity is not found, it reverts to the package icon.
     */
    public synchronized void getTitleAndIcon(ItemInfoWithIcon info, boolean useLowResIcon) {
        // null info means not installed, but if we have a component from the intent then
        // we should still look in the cache for restored app icons.
        if (info.getTargetComponent() == null) {
            info.applyFrom(getDefaultIcon(info.user));
            info.title = "";
            info.contentDescription = "";
        } else {
            Intent intent = info.getIntent();
            getTitleAndIcon(info, () -> mLauncherApps.resolveActivity(intent, info.user),
                    true, useLowResIcon);
        }
    }

    public synchronized String getTitleNoCache(ComponentWithLabel info) {
        CacheEntry entry = cacheLocked(info.getComponent(), info.getUser(), () -> info,
                mComponentWithLabelCachingLogic, false /* usePackageIcon */,
                true /* useLowResIcon */);
        return Utilities.trim(entry.title);
    }

    /**
     * Fill in {@param mWorkspaceItemInfo} with the icon and label for {@param info}
     */
    private synchronized void getTitleAndIcon(
            @NonNull ItemInfoWithIcon infoInOut,
            @NonNull Supplier<LauncherActivityInfo> activityInfoProvider,
            boolean usePkgIcon, boolean useLowResIcon) {
        CacheEntry entry = cacheLocked(infoInOut.getTargetComponent(), infoInOut.user,
                activityInfoProvider, mLauncherActivityInfoCachingLogic, usePkgIcon, useLowResIcon);
        applyCacheEntry(entry, infoInOut);
    }


    /**
     * Fill in {@param infoInOut} with the corresponding icon and label.
     */
    public synchronized void getTitleAndIconForApp(
            PackageItemInfo infoInOut, boolean useLowResIcon) {
        CacheEntry entry = getEntryForPackageLocked(
                infoInOut.packageName, infoInOut.user, useLowResIcon);
        applyCacheEntry(entry, infoInOut);
    }

    protected void applyCacheEntry(CacheEntry entry, ItemInfoWithIcon info) {
        info.title = Utilities.trim(entry.title);
        info.contentDescription = entry.contentDescription;
        info.applyFrom((entry.icon == null) ? getDefaultIcon(info.user) : entry);
    }

    public Drawable getFullResIcon(LauncherActivityInfo info) {
        return getFullResIcon(info, true);
    }

    public Drawable getFullResIcon(LauncherActivityInfo info, boolean flattenDrawable) {
        return mIconProvider.getIcon(info, mIconDpi, flattenDrawable);
    }

    @Override
    protected String getIconSystemState(String packageName) {
        return mIconProvider.getSystemStateForPackage(mSystemState, packageName)
                + ",flags_asi:" + FeatureFlags.APP_SEARCH_IMPROVEMENTS.get();
    }

    public static abstract class IconLoadRequest extends HandlerRunnable {
        IconLoadRequest(Handler handler, Runnable endRunnable) {
            super(handler, endRunnable);
        }
    }

    /**
     * Interface for receiving itemInfo with high-res icon.
     */
    public interface ItemInfoUpdateReceiver {

        void reapplyItemInfo(ItemInfoWithIcon info);
    }
}
