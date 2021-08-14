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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.ComponentWithLabel.ComponentCachingLogic;
import com.android.launcher3.icons.cache.BaseIconCache;
import com.android.launcher3.icons.cache.CachingLogic;
import com.android.launcher3.icons.cache.HandlerRunnable;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.InstantAppResolver;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Preconditions;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Cache of application icons.  Icons can be made from any thread.
 */
public class IconCache extends BaseIconCache {

    private static final String TAG = "Launcher.IconCache";

    private final Predicate<ItemInfoWithIcon> mIsUsingFallbackOrNonDefaultIconCheck = w ->
            w.bitmap != null && (w.bitmap.isNullOrLowRes() || !isDefaultIcon(w.bitmap, w.user));

    private final CachingLogic<ComponentWithLabel> mComponentWithLabelCachingLogic;
    private final CachingLogic<LauncherActivityInfo> mLauncherActivityInfoCachingLogic;
    private final CachingLogic<ShortcutInfo> mShortcutCachingLogic;

    private final LauncherApps mLauncherApps;
    private final UserCache mUserManager;
    private final InstantAppResolver mInstantAppResolver;
    private final IconProvider mIconProvider;

    private int mPendingIconRequestCount = 0;

    public IconCache(Context context, InvariantDeviceProfile idp) {
        this(context, idp, LauncherFiles.APP_ICONS_DB, new IconProvider(context));
    }

    public IconCache(Context context, InvariantDeviceProfile idp, String dbFileName,
            IconProvider iconProvider) {
        super(context, dbFileName, MODEL_EXECUTOR.getLooper(),
                idp.fillResIconDpi, idp.iconBitmapSize, true /* inMemoryCache */);
        mComponentWithLabelCachingLogic = new ComponentCachingLogic(context, false);
        mLauncherActivityInfoCachingLogic = LauncherActivityCachingLogic.newInstance(context);
        mShortcutCachingLogic = new ShortcutCachingLogic();
        mLauncherApps = mContext.getSystemService(LauncherApps.class);
        mUserManager = UserCache.INSTANCE.get(mContext);
        mInstantAppResolver = InstantAppResolver.newInstance(mContext);
        mIconProvider = iconProvider;
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
    public BaseIconFactory getIconFactory() {
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
     * Closes the cache DB. This will clear any in-memory cache.
     */
    public void close() {
        mIconDb.close();
    }

    /**
     * Fetches high-res icon for the provided ItemInfo and updates the caller when done.
     *
     * @return a request ID that can be used to cancel the request.
     */
    public HandlerRunnable updateIconInBackground(final ItemInfoUpdateReceiver caller,
            final ItemInfoWithIcon info) {
        Preconditions.assertUIThread();
        if (mPendingIconRequestCount <= 0) {
            MODEL_EXECUTOR.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
        }
        mPendingIconRequestCount++;

        HandlerRunnable<ItemInfoWithIcon> request = new HandlerRunnable<>(mWorkerHandler,
                () -> {
                    if (info instanceof AppInfo || info instanceof WorkspaceItemInfo) {
                        getTitleAndIcon(info, false);
                    } else if (info instanceof PackageItemInfo) {
                        getTitleAndIconForApp((PackageItemInfo) info, false);
                    }
                    return info;
                },
                MAIN_EXECUTOR,
                caller::reapplyItemInfo,
                this::onIconRequestEnd);
        Utilities.postAsyncCallback(mWorkerHandler, request);
        return request;
    }

    private void onIconRequestEnd() {
        mPendingIconRequestCount--;
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
        if (entry.bitmap != null && !isDefaultIcon(entry.bitmap, application.user)) {
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
     * Fill in {@param info} with the icon for {@param si}
     */
    public void getShortcutIcon(ItemInfoWithIcon info, ShortcutInfo si) {
        getShortcutIcon(info, si, true, mIsUsingFallbackOrNonDefaultIconCheck);
    }

    /**
     * Fill in {@param info} with an unbadged icon for {@param si}
     */
    public void getUnbadgedShortcutIcon(ItemInfoWithIcon info, ShortcutInfo si) {
        getShortcutIcon(info, si, false, mIsUsingFallbackOrNonDefaultIconCheck);
    }

    /**
     * Fill in {@param info} with the icon and label for {@param si}. If the icon is not
     * available, and fallback check returns true, it keeps the old icon.
     */
    public <T extends ItemInfoWithIcon> void getShortcutIcon(T info, ShortcutInfo si,
            @NonNull Predicate<T> fallbackIconCheck) {
        getShortcutIcon(info, si, true /* use badged */, fallbackIconCheck);
    }

    private synchronized <T extends ItemInfoWithIcon> void getShortcutIcon(T info, ShortcutInfo si,
            boolean useBadged, @NonNull Predicate<T> fallbackIconCheck) {
        BitmapInfo bitmapInfo;
        if (FeatureFlags.ENABLE_DEEP_SHORTCUT_ICON_CACHE.get()) {
            bitmapInfo = cacheLocked(ShortcutKey.fromInfo(si).componentName, si.getUserHandle(),
                    () -> si, mShortcutCachingLogic, false, false).bitmap;
        } else {
            // If caching is disabled, load the full icon
            bitmapInfo = mShortcutCachingLogic.loadIcon(mContext, si);
        }
        if (bitmapInfo.isNullOrLowRes()) {
            bitmapInfo = getDefaultIcon(si.getUserHandle());
        }

        if (isDefaultIcon(bitmapInfo, si.getUserHandle()) && fallbackIconCheck.test(info)) {
            return;
        }
        info.bitmap = bitmapInfo;
        if (useBadged) {
            BitmapInfo badgeInfo = getShortcutInfoBadge(si);
            try (LauncherIcons li = LauncherIcons.obtain(mContext)) {
                info.bitmap = li.badgeBitmap(info.bitmap.icon, badgeInfo);
            }
        }
    }

    /**
     * Returns the badging info for the shortcut
     */
    public BitmapInfo getShortcutInfoBadge(ShortcutInfo shortcutInfo) {
        ComponentName cn = shortcutInfo.getActivity();
        if (cn != null) {
            // Get the app info for the source activity.
            AppInfo appInfo = new AppInfo();
            appInfo.user = shortcutInfo.getUserHandle();
            appInfo.componentName = cn;
            appInfo.intent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(cn);
            getTitleAndIcon(appInfo, false);
            return appInfo.bitmap;
        } else {
            PackageItemInfo pkgInfo = new PackageItemInfo(shortcutInfo.getPackage());
            getTitleAndIconForApp(pkgInfo, false);
            return pkgInfo.bitmap;
        }
    }

    /**
     * Fill in {@param info} with the icon and label. If the
     * corresponding activity is not found, it reverts to the package icon.
     */
    public synchronized void getTitleAndIcon(ItemInfoWithIcon info, boolean useLowResIcon) {
        // null info means not installed, but if we have a component from the intent then
        // we should still look in the cache for restored app icons.
        if (info.getTargetComponent() == null) {
            info.bitmap = getDefaultIcon(info.user);
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
    public synchronized void getTitleAndIcon(
            @NonNull ItemInfoWithIcon infoInOut,
            @NonNull Supplier<LauncherActivityInfo> activityInfoProvider,
            boolean usePkgIcon, boolean useLowResIcon) {
        CacheEntry entry = cacheLocked(infoInOut.getTargetComponent(), infoInOut.user,
                activityInfoProvider, mLauncherActivityInfoCachingLogic, usePkgIcon,
                useLowResIcon);
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
        if (infoInOut.category == PackageItemInfo.CONVERSATIONS) {
            infoInOut.title = mContext.getString(R.string.widget_category_conversations);
            infoInOut.contentDescription = mPackageManager.getUserBadgedLabel(
                    infoInOut.title, infoInOut.user);
        }
    }

    protected void applyCacheEntry(CacheEntry entry, ItemInfoWithIcon info) {
        info.title = Utilities.trim(entry.title);
        info.contentDescription = entry.contentDescription;
        info.bitmap = (entry.bitmap == null) ? getDefaultIcon(info.user) : entry.bitmap;
    }

    public Drawable getFullResIcon(LauncherActivityInfo info) {
        return mIconProvider.getIcon(info, mIconDpi);
    }

    public void updateSessionCache(PackageUserKey key, PackageInstaller.SessionInfo info) {
        cachePackageInstallInfo(key.mPackageName, key.mUser, info.getAppIcon(),
                info.getAppLabel());
    }

    @Override
    protected String getIconSystemState(String packageName) {
        return mIconProvider.getSystemStateForPackage(mSystemState, packageName);
    }

    /**
     * Interface for receiving itemInfo with high-res icon.
     */
    public interface ItemInfoUpdateReceiver {

        void reapplyItemInfo(ItemInfoWithIcon info);
    }
}
