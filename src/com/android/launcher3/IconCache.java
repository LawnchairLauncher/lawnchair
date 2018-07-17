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
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.os.Handler;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.support.annotation.NonNull;
import android.support.v4.graphics.ColorUtils;
import android.text.TextUtils;
import android.util.Log;

import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.graphics.BitmapInfo;
import com.android.launcher3.graphics.BitmapRenderer;
import com.android.launcher3.graphics.LauncherIcons;
import com.android.launcher3.model.PackageItemInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.InstantAppResolver;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.Provider;
import com.android.launcher3.util.SQLiteCacheHelper;
import com.android.launcher3.util.Thunk;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * Cache of application icons.  Icons can be made from any thread.
 */
public class IconCache {

    private static final String TAG = "Launcher.IconCache";

    private static final int INITIAL_ICON_CACHE_CAPACITY = 50;

    // Empty class name is used for storing package default entry.
    public static final String EMPTY_CLASS_NAME = ".";

    private static final boolean DEBUG = false;
    private static final boolean DEBUG_IGNORE_CACHE = false;

    private static final int LOW_RES_SCALE_FACTOR = 5;

    @Thunk static final Object ICON_UPDATE_TOKEN = new Object();

    public static class CacheEntry extends BitmapInfo {
        public CharSequence title = "";
        public CharSequence contentDescription = "";
        public boolean isLowResIcon;
    }

    private final HashMap<UserHandle, BitmapInfo> mDefaultIcons = new HashMap<>();
    @Thunk final MainThreadExecutor mMainThreadExecutor = new MainThreadExecutor();

    private final Context mContext;
    private final PackageManager mPackageManager;
    private final IconProvider mIconProvider;
    @Thunk final UserManagerCompat mUserManager;
    private final LauncherAppsCompat mLauncherApps;
    private final HashMap<ComponentKey, CacheEntry> mCache =
            new HashMap<>(INITIAL_ICON_CACHE_CAPACITY);
    private final InstantAppResolver mInstantAppResolver;
    private final int mIconDpi;
    @Thunk final IconDB mIconDb;

    @Thunk final Handler mWorkerHandler;

    private final BitmapFactory.Options mLowResOptions;
    private final BitmapFactory.Options mHighResOptions;

    private int mPendingIconRequestCount = 0;

    public IconCache(Context context, InvariantDeviceProfile inv) {
        mContext = context;
        mPackageManager = context.getPackageManager();
        mUserManager = UserManagerCompat.getInstance(mContext);
        mLauncherApps = LauncherAppsCompat.getInstance(mContext);
        mInstantAppResolver = InstantAppResolver.newInstance(mContext);
        mIconDpi = inv.fillResIconDpi;
        mIconDb = new IconDB(context, inv.iconBitmapSize);

        mIconProvider = IconProvider.newInstance(context);
        mWorkerHandler = new Handler(LauncherModel.getWorkerLooper());

        mLowResOptions = new BitmapFactory.Options();
        // Always prefer RGB_565 config for low res. If the bitmap has transparency, it will
        // automatically be loaded as ALPHA_8888.
        mLowResOptions.inPreferredConfig = Bitmap.Config.RGB_565;

        if (BitmapRenderer.USE_HARDWARE_BITMAP) {
            mHighResOptions = new BitmapFactory.Options();
            mHighResOptions.inPreferredConfig = Bitmap.Config.HARDWARE;
        } else {
            mHighResOptions = null;
        }
    }

    private Drawable getFullResDefaultActivityIcon() {
        return getFullResIcon(Resources.getSystem(), Utilities.ATLEAST_OREO ?
                android.R.drawable.sym_def_app_icon : android.R.mipmap.sym_def_app_icon);
    }

    private Drawable getFullResIcon(Resources resources, int iconId) {
        Drawable d;
        try {
            d = resources.getDrawableForDensity(iconId, mIconDpi);
        } catch (Resources.NotFoundException e) {
            d = null;
        }

        return (d != null) ? d : getFullResDefaultActivityIcon();
    }

    public Drawable getFullResIcon(String packageName, int iconId) {
        Resources resources;
        try {
            resources = mPackageManager.getResourcesForApplication(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            resources = null;
        }
        if (resources != null) {
            if (iconId != 0) {
                return getFullResIcon(resources, iconId);
            }
        }
        return getFullResDefaultActivityIcon();
    }

    public Drawable getFullResIcon(ActivityInfo info) {
        Resources resources;
        try {
            resources = mPackageManager.getResourcesForApplication(
                    info.applicationInfo);
        } catch (PackageManager.NameNotFoundException e) {
            resources = null;
        }
        if (resources != null) {
            int iconId = info.getIconResource();
            if (iconId != 0) {
                return getFullResIcon(resources, iconId);
            }
        }

        return getFullResDefaultActivityIcon();
    }

    public Drawable getFullResIcon(LauncherActivityInfo info) {
        return getFullResIcon(info, true);
    }

    public Drawable getFullResIcon(LauncherActivityInfo info, boolean flattenDrawable) {
        return mIconProvider.getIcon(info, mIconDpi, flattenDrawable);
    }

    protected BitmapInfo makeDefaultIcon(UserHandle user) {
        try (LauncherIcons li = LauncherIcons.obtain(mContext)) {
            return li.createBadgedIconBitmap(
                    getFullResDefaultActivityIcon(), user, VERSION.SDK_INT);
        }
    }

    /**
     * Remove any records for the supplied ComponentName.
     */
    public synchronized void remove(ComponentName componentName, UserHandle user) {
        mCache.remove(new ComponentKey(componentName, user));
    }

    /**
     * Remove any records for the supplied package name from memory.
     */
    private void removeFromMemCacheLocked(String packageName, UserHandle user) {
        HashSet<ComponentKey> forDeletion = new HashSet<>();
        for (ComponentKey key: mCache.keySet()) {
            if (key.componentName.getPackageName().equals(packageName)
                    && key.user.equals(user)) {
                forDeletion.add(key);
            }
        }
        for (ComponentKey condemned: forDeletion) {
            mCache.remove(condemned);
        }
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
                addIconToDBAndMemCache(app, info, userSerial, false /*replace existing*/);
            }
        } catch (NameNotFoundException e) {
            Log.d(TAG, "Package not found", e);
        }
    }

    /**
     * Removes the entries related to the given package in memory and persistent DB.
     */
    public synchronized void removeIconsForPkg(String packageName, UserHandle user) {
        removeFromMemCacheLocked(packageName, user);
        long userSerial = mUserManager.getSerialNumberForUser(user);
        mIconDb.delete(
                IconDB.COLUMN_COMPONENT + " LIKE ? AND " + IconDB.COLUMN_USER + " = ?",
                new String[]{packageName + "/%", Long.toString(userSerial)});
    }

    public void updateDbIcons(Set<String> ignorePackagesForMainUser) {
        // Remove all active icon update tasks.
        mWorkerHandler.removeCallbacksAndMessages(ICON_UPDATE_TOKEN);

        mIconProvider.updateSystemStateString(mContext);
        for (UserHandle user : mUserManager.getUserProfiles()) {
            // Query for the set of apps
            final List<LauncherActivityInfo> apps = mLauncherApps.getActivityList(null, user);
            // Fail if we don't have any apps
            // TODO: Fix this. Only fail for the current user.
            if (apps == null || apps.isEmpty()) {
                return;
            }

            // Update icon cache. This happens in segments and {@link #onPackageIconsUpdated}
            // is called by the icon cache when the job is complete.
            updateDBIcons(user, apps, Process.myUserHandle().equals(user)
                    ? ignorePackagesForMainUser : Collections.<String>emptySet());
        }
    }

    /**
     * Updates the persistent DB, such that only entries corresponding to {@param apps} remain in
     * the DB and are updated.
     * @return The set of packages for which icons have updated.
     */
    private void updateDBIcons(UserHandle user, List<LauncherActivityInfo> apps,
            Set<String> ignorePackages) {
        long userSerial = mUserManager.getSerialNumberForUser(user);
        PackageManager pm = mContext.getPackageManager();
        HashMap<String, PackageInfo> pkgInfoMap = new HashMap<>();
        for (PackageInfo info : pm.getInstalledPackages(PackageManager.GET_UNINSTALLED_PACKAGES)) {
            pkgInfoMap.put(info.packageName, info);
        }

        HashMap<ComponentName, LauncherActivityInfo> componentMap = new HashMap<>();
        for (LauncherActivityInfo app : apps) {
            componentMap.put(app.getComponentName(), app);
        }

        HashSet<Integer> itemsToRemove = new HashSet<>();
        Stack<LauncherActivityInfo> appsToUpdate = new Stack<>();

        Cursor c = null;
        try {
            c = mIconDb.query(
                    new String[]{IconDB.COLUMN_ROWID, IconDB.COLUMN_COMPONENT,
                            IconDB.COLUMN_LAST_UPDATED, IconDB.COLUMN_VERSION,
                            IconDB.COLUMN_SYSTEM_STATE},
                    IconDB.COLUMN_USER + " = ? ",
                    new String[]{Long.toString(userSerial)});

            final int indexComponent = c.getColumnIndex(IconDB.COLUMN_COMPONENT);
            final int indexLastUpdate = c.getColumnIndex(IconDB.COLUMN_LAST_UPDATED);
            final int indexVersion = c.getColumnIndex(IconDB.COLUMN_VERSION);
            final int rowIndex = c.getColumnIndex(IconDB.COLUMN_ROWID);
            final int systemStateIndex = c.getColumnIndex(IconDB.COLUMN_SYSTEM_STATE);

            while (c.moveToNext()) {
                String cn = c.getString(indexComponent);
                ComponentName component = ComponentName.unflattenFromString(cn);
                PackageInfo info = pkgInfoMap.get(component.getPackageName());
                if (info == null) {
                    if (!ignorePackages.contains(component.getPackageName())) {
                        remove(component, user);
                        itemsToRemove.add(c.getInt(rowIndex));
                    }
                    continue;
                }
                if ((info.applicationInfo.flags & ApplicationInfo.FLAG_IS_DATA_ONLY) != 0) {
                    // Application is not present
                    continue;
                }

                long updateTime = c.getLong(indexLastUpdate);
                int version = c.getInt(indexVersion);
                LauncherActivityInfo app = componentMap.remove(component);
                if (version == info.versionCode && updateTime == info.lastUpdateTime &&
                        TextUtils.equals(c.getString(systemStateIndex),
                                mIconProvider.getIconSystemState(info.packageName))) {
                    continue;
                }
                if (app == null) {
                    remove(component, user);
                    itemsToRemove.add(c.getInt(rowIndex));
                } else {
                    appsToUpdate.add(app);
                }
            }
        } catch (SQLiteException e) {
            Log.d(TAG, "Error reading icon cache", e);
            // Continue updating whatever we have read so far
        } finally {
            if (c != null) {
                c.close();
            }
        }
        if (!itemsToRemove.isEmpty()) {
            mIconDb.delete(
                    Utilities.createDbSelectionQuery(IconDB.COLUMN_ROWID, itemsToRemove), null);
        }

        // Insert remaining apps.
        if (!componentMap.isEmpty() || !appsToUpdate.isEmpty()) {
            Stack<LauncherActivityInfo> appsToAdd = new Stack<>();
            appsToAdd.addAll(componentMap.values());
            new SerializedIconUpdateTask(userSerial, pkgInfoMap,
                    appsToAdd, appsToUpdate).scheduleNext();
        }
    }

    /**
     * Adds an entry into the DB and the in-memory cache.
     * @param replaceExisting if true, it will recreate the bitmap even if it already exists in
     *                        the memory. This is useful then the previous bitmap was created using
     *                        old data.
     */
    @Thunk synchronized void addIconToDBAndMemCache(LauncherActivityInfo app,
            PackageInfo info, long userSerial, boolean replaceExisting) {
        final ComponentKey key = new ComponentKey(app.getComponentName(), app.getUser());
        CacheEntry entry = null;
        if (!replaceExisting) {
            entry = mCache.get(key);
            // We can't reuse the entry if the high-res icon is not present.
            if (entry == null || entry.isLowResIcon || entry.icon == null) {
                entry = null;
            }
        }
        if (entry == null) {
            entry = new CacheEntry();
            LauncherIcons li = LauncherIcons.obtain(mContext);
            li.createBadgedIconBitmap(getFullResIcon(app), app.getUser(),
                    app.getApplicationInfo().targetSdkVersion).applyTo(entry);
            li.recycle();
        }
        entry.title = app.getLabel();
        entry.contentDescription = mUserManager.getBadgedLabelForUser(entry.title, app.getUser());
        mCache.put(key, entry);

        Bitmap lowResIcon = generateLowResIcon(entry.icon);
        ContentValues values = newContentValues(entry.icon, lowResIcon, entry.color,
                entry.title.toString(), app.getApplicationInfo().packageName);
        addIconToDB(values, app.getComponentName(), info, userSerial);
    }

    /**
     * Updates {@param values} to contain versioning information and adds it to the DB.
     * @param values {@link ContentValues} containing icon & title
     */
    private void addIconToDB(ContentValues values, ComponentName key,
            PackageInfo info, long userSerial) {
        values.put(IconDB.COLUMN_COMPONENT, key.flattenToString());
        values.put(IconDB.COLUMN_USER, userSerial);
        values.put(IconDB.COLUMN_LAST_UPDATED, info.lastUpdateTime);
        values.put(IconDB.COLUMN_VERSION, info.versionCode);
        mIconDb.insertOrReplace(values);
    }

    /**
     * Fetches high-res icon for the provided ItemInfo and updates the caller when done.
     * @return a request ID that can be used to cancel the request.
     */
    public IconLoadRequest updateIconInBackground(final ItemInfoUpdateReceiver caller,
            final ItemInfoWithIcon info) {
        Preconditions.assertUIThread();
        if (mPendingIconRequestCount <= 0) {
            LauncherModel.setWorkerPriority(Process.THREAD_PRIORITY_FOREGROUND);
        }
        mPendingIconRequestCount ++;

        IconLoadRequest request = new IconLoadRequest(mWorkerHandler, this::onIconRequestEnd) {
            @Override
            public void run() {
                if (info instanceof AppInfo || info instanceof ShortcutInfo) {
                    getTitleAndIcon(info, false);
                } else if (info instanceof PackageItemInfo) {
                    getTitleAndIconForApp((PackageItemInfo) info, false);
                }
                mMainThreadExecutor.execute(() -> {
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
            LauncherModel.setWorkerPriority(Process.THREAD_PRIORITY_BACKGROUND);
        }
    }

    /**
     * Updates {@param application} only if a valid entry is found.
     */
    public synchronized void updateTitleAndIcon(AppInfo application) {
        CacheEntry entry = cacheLocked(application.componentName,
                Provider.<LauncherActivityInfo>of(null),
                application.user, false, application.usingLowResIcon);
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
        getTitleAndIcon(info, Provider.of(activityInfo), false, useLowResIcon);
    }

    /**
     * Fill in {@param info} with the icon and label. If the
     * corresponding activity is not found, it reverts to the package icon.
     */
    public synchronized void getTitleAndIcon(ItemInfoWithIcon info, boolean useLowResIcon) {
        // null info means not installed, but if we have a component from the intent then
        // we should still look in the cache for restored app icons.
        if (info.getTargetComponent() == null) {
            getDefaultIcon(info.user).applyTo(info);
            info.title = "";
            info.contentDescription = "";
            info.usingLowResIcon = false;
        } else {
            getTitleAndIcon(info, new ActivityInfoProvider(info.getIntent(), info.user),
                    true, useLowResIcon);
        }
    }

    /**
     * Fill in {@param shortcutInfo} with the icon and label for {@param info}
     */
    private synchronized void getTitleAndIcon(
            @NonNull ItemInfoWithIcon infoInOut,
            @NonNull Provider<LauncherActivityInfo> activityInfoProvider,
            boolean usePkgIcon, boolean useLowResIcon) {
        CacheEntry entry = cacheLocked(infoInOut.getTargetComponent(), activityInfoProvider,
                infoInOut.user, usePkgIcon, useLowResIcon);
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

    private void applyCacheEntry(CacheEntry entry, ItemInfoWithIcon info) {
        info.title = Utilities.trim(entry.title);
        info.contentDescription = entry.contentDescription;
        info.usingLowResIcon = entry.isLowResIcon;
        ((entry.icon == null) ? getDefaultIcon(info.user) : entry).applyTo(info);
    }

    public synchronized BitmapInfo getDefaultIcon(UserHandle user) {
        if (!mDefaultIcons.containsKey(user)) {
            mDefaultIcons.put(user, makeDefaultIcon(user));
        }
        return mDefaultIcons.get(user);
    }

    public boolean isDefaultIcon(Bitmap icon, UserHandle user) {
        return getDefaultIcon(user).icon == icon;
    }

    /**
     * Retrieves the entry from the cache. If the entry is not present, it creates a new entry.
     * This method is not thread safe, it must be called from a synchronized method.
     */
    protected CacheEntry cacheLocked(
            @NonNull ComponentName componentName,
            @NonNull Provider<LauncherActivityInfo> infoProvider,
            UserHandle user, boolean usePackageIcon, boolean useLowResIcon) {
        Preconditions.assertWorkerThread();
        ComponentKey cacheKey = new ComponentKey(componentName, user);
        CacheEntry entry = mCache.get(cacheKey);
        if (entry == null || (entry.isLowResIcon && !useLowResIcon)) {
            entry = new CacheEntry();
            mCache.put(cacheKey, entry);

            // Check the DB first.
            LauncherActivityInfo info = null;
            boolean providerFetchedOnce = false;

            if (!getEntryFromDB(cacheKey, entry, useLowResIcon) || DEBUG_IGNORE_CACHE) {
                info = infoProvider.get();
                providerFetchedOnce = true;

                if (info != null) {
                    LauncherIcons li = LauncherIcons.obtain(mContext);
                    li.createBadgedIconBitmap(getFullResIcon(info), info.getUser(),
                            info.getApplicationInfo().targetSdkVersion).applyTo(entry);
                    li.recycle();
                } else {
                    if (usePackageIcon) {
                        CacheEntry packageEntry = getEntryForPackageLocked(
                                componentName.getPackageName(), user, false);
                        if (packageEntry != null) {
                            if (DEBUG) Log.d(TAG, "using package default icon for " +
                                    componentName.toShortString());
                            packageEntry.applyTo(entry);
                            entry.title = packageEntry.title;
                            entry.contentDescription = packageEntry.contentDescription;
                        }
                    }
                    if (entry.icon == null) {
                        if (DEBUG) Log.d(TAG, "using default icon for " +
                                componentName.toShortString());
                        getDefaultIcon(user).applyTo(entry);
                    }
                }
            }

            if (TextUtils.isEmpty(entry.title)) {
                if (info == null && !providerFetchedOnce) {
                    info = infoProvider.get();
                    providerFetchedOnce = true;
                }
                if (info != null) {
                    entry.title = info.getLabel();
                    entry.contentDescription = mUserManager.getBadgedLabelForUser(entry.title, user);
                }
            }
        }
        return entry;
    }

    public synchronized void clear() {
        Preconditions.assertWorkerThread();
        mIconDb.clear();
    }

    /**
     * Adds a default package entry in the cache. This entry is not persisted and will be removed
     * when the cache is flushed.
     */
    public synchronized void cachePackageInstallInfo(String packageName, UserHandle user,
            Bitmap icon, CharSequence title) {
        removeFromMemCacheLocked(packageName, user);

        ComponentKey cacheKey = getPackageKey(packageName, user);
        CacheEntry entry = mCache.get(cacheKey);

        // For icon caching, do not go through DB. Just update the in-memory entry.
        if (entry == null) {
            entry = new CacheEntry();
        }
        if (!TextUtils.isEmpty(title)) {
            entry.title = title;
        }
        if (icon != null) {
            LauncherIcons li = LauncherIcons.obtain(mContext);
            li.createIconBitmap(icon).applyTo(entry);
            li.recycle();
        }
        if (!TextUtils.isEmpty(title) && entry.icon != null) {
            mCache.put(cacheKey, entry);
        }
    }

    private static ComponentKey getPackageKey(String packageName, UserHandle user) {
        ComponentName cn = new ComponentName(packageName, packageName + EMPTY_CLASS_NAME);
        return new ComponentKey(cn, user);
    }

    /**
     * Gets an entry for the package, which can be used as a fallback entry for various components.
     * This method is not thread safe, it must be called from a synchronized method.
     */
    private CacheEntry getEntryForPackageLocked(String packageName, UserHandle user,
            boolean useLowResIcon) {
        Preconditions.assertWorkerThread();
        ComponentKey cacheKey = getPackageKey(packageName, user);
        CacheEntry entry = mCache.get(cacheKey);

        if (entry == null || (entry.isLowResIcon && !useLowResIcon)) {
            entry = new CacheEntry();
            boolean entryUpdated = true;

            // Check the DB first.
            if (!getEntryFromDB(cacheKey, entry, useLowResIcon)) {
                try {
                    int flags = Process.myUserHandle().equals(user) ? 0 :
                        PackageManager.GET_UNINSTALLED_PACKAGES;
                    PackageInfo info = mPackageManager.getPackageInfo(packageName, flags);
                    ApplicationInfo appInfo = info.applicationInfo;
                    if (appInfo == null) {
                        throw new NameNotFoundException("ApplicationInfo is null");
                    }

                    LauncherIcons li = LauncherIcons.obtain(mContext);
                    // Load the full res icon for the application, but if useLowResIcon is set, then
                    // only keep the low resolution icon instead of the larger full-sized icon
                    BitmapInfo iconInfo = li.createBadgedIconBitmap(
                            appInfo.loadIcon(mPackageManager), user, appInfo.targetSdkVersion,
                            mInstantAppResolver.isInstantApp(appInfo));
                    li.recycle();

                    Bitmap lowResIcon =  generateLowResIcon(iconInfo.icon);
                    entry.title = appInfo.loadLabel(mPackageManager);
                    entry.contentDescription = mUserManager.getBadgedLabelForUser(entry.title, user);
                    entry.icon = useLowResIcon ? lowResIcon : iconInfo.icon;
                    entry.color = iconInfo.color;
                    entry.isLowResIcon = useLowResIcon;

                    // Add the icon in the DB here, since these do not get written during
                    // package updates.
                    ContentValues values = newContentValues(iconInfo.icon, lowResIcon, entry.color,
                            entry.title.toString(), packageName);
                    addIconToDB(values, cacheKey.componentName, info,
                            mUserManager.getSerialNumberForUser(user));

                } catch (NameNotFoundException e) {
                    if (DEBUG) Log.d(TAG, "Application not installed " + packageName);
                    entryUpdated = false;
                }
            }

            // Only add a filled-out entry to the cache
            if (entryUpdated) {
                mCache.put(cacheKey, entry);
            }
        }
        return entry;
    }

    private boolean getEntryFromDB(ComponentKey cacheKey, CacheEntry entry, boolean lowRes) {
        Cursor c = null;
        try {
            c = mIconDb.query(
                new String[]{lowRes ? IconDB.COLUMN_ICON_LOW_RES : IconDB.COLUMN_ICON,
                        IconDB.COLUMN_ICON_COLOR, IconDB.COLUMN_LABEL},
                IconDB.COLUMN_COMPONENT + " = ? AND " + IconDB.COLUMN_USER + " = ?",
                new String[]{cacheKey.componentName.flattenToString(),
                        Long.toString(mUserManager.getSerialNumberForUser(cacheKey.user))});
            if (c.moveToNext()) {
                entry.icon = loadIconNoResize(c, 0, lowRes ? mLowResOptions : mHighResOptions);
                // Set the alpha to be 255, so that we never have a wrong color
                entry.color = ColorUtils.setAlphaComponent(c.getInt(1), 255);
                entry.isLowResIcon = lowRes;
                entry.title = c.getString(2);
                if (entry.title == null) {
                    entry.title = "";
                    entry.contentDescription = "";
                } else {
                    entry.contentDescription = mUserManager.getBadgedLabelForUser(
                            entry.title, cacheKey.user);
                }
                return true;
            }
        } catch (SQLiteException e) {
            Log.d(TAG, "Error reading icon cache", e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return false;
    }

    public static abstract class IconLoadRequest implements Runnable {
        private final Handler mHandler;
        private final Runnable mEndRunnable;

        private boolean mEnded = false;

        IconLoadRequest(Handler handler, Runnable endRunnable) {
            mHandler = handler;
            mEndRunnable = endRunnable;
        }

        public void cancel() {
            mHandler.removeCallbacks(this);
            onEnd();
        }

        public void onEnd() {
            if (!mEnded) {
                mEnded = true;
                mEndRunnable.run();
            }
        }
    }

    /**
     * A runnable that updates invalid icons and adds missing icons in the DB for the provided
     * LauncherActivityInfo list. Items are updated/added one at a time, so that the
     * worker thread doesn't get blocked.
     */
    @Thunk class SerializedIconUpdateTask implements Runnable {
        private final long mUserSerial;
        private final HashMap<String, PackageInfo> mPkgInfoMap;
        private final Stack<LauncherActivityInfo> mAppsToAdd;
        private final Stack<LauncherActivityInfo> mAppsToUpdate;
        private final HashSet<String> mUpdatedPackages = new HashSet<>();

        @Thunk SerializedIconUpdateTask(long userSerial, HashMap<String, PackageInfo> pkgInfoMap,
                Stack<LauncherActivityInfo> appsToAdd,
                Stack<LauncherActivityInfo> appsToUpdate) {
            mUserSerial = userSerial;
            mPkgInfoMap = pkgInfoMap;
            mAppsToAdd = appsToAdd;
            mAppsToUpdate = appsToUpdate;
        }

        @Override
        public void run() {
            if (!mAppsToUpdate.isEmpty()) {
                LauncherActivityInfo app = mAppsToUpdate.pop();
                String pkg = app.getComponentName().getPackageName();
                PackageInfo info = mPkgInfoMap.get(pkg);
                addIconToDBAndMemCache(app, info, mUserSerial, true /*replace existing*/);
                mUpdatedPackages.add(pkg);

                if (mAppsToUpdate.isEmpty() && !mUpdatedPackages.isEmpty()) {
                    // No more app to update. Notify model.
                    LauncherAppState.getInstance(mContext).getModel().onPackageIconsUpdated(
                            mUpdatedPackages, mUserManager.getUserForSerialNumber(mUserSerial));
                }

                // Let it run one more time.
                scheduleNext();
            } else if (!mAppsToAdd.isEmpty()) {
                LauncherActivityInfo app = mAppsToAdd.pop();
                PackageInfo info = mPkgInfoMap.get(app.getComponentName().getPackageName());
                // We do not check the mPkgInfoMap when generating the mAppsToAdd. Although every
                // app should have package info, this is not guaranteed by the api
                if (info != null) {
                    addIconToDBAndMemCache(app, info, mUserSerial, false /*replace existing*/);
                }

                if (!mAppsToAdd.isEmpty()) {
                    scheduleNext();
                }
            }
        }

        public void scheduleNext() {
            mWorkerHandler.postAtTime(this, ICON_UPDATE_TOKEN, SystemClock.uptimeMillis() + 1);
        }
    }

    private static final class IconDB extends SQLiteCacheHelper {
        private final static int RELEASE_VERSION = 24;

        private final static String TABLE_NAME = "icons";
        private final static String COLUMN_ROWID = "rowid";
        private final static String COLUMN_COMPONENT = "componentName";
        private final static String COLUMN_USER = "profileId";
        private final static String COLUMN_LAST_UPDATED = "lastUpdated";
        private final static String COLUMN_VERSION = "version";
        private final static String COLUMN_ICON = "icon";
        private final static String COLUMN_ICON_LOW_RES = "icon_low_res";
        private final static String COLUMN_ICON_COLOR = "icon_color";
        private final static String COLUMN_LABEL = "label";
        private final static String COLUMN_SYSTEM_STATE = "system_state";

        public IconDB(Context context, int iconPixelSize) {
            super(context, LauncherFiles.APP_ICONS_DB,
                    (RELEASE_VERSION << 16) + iconPixelSize,
                    TABLE_NAME);
        }

        @Override
        protected void onCreateTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    COLUMN_COMPONENT + " TEXT NOT NULL, " +
                    COLUMN_USER + " INTEGER NOT NULL, " +
                    COLUMN_LAST_UPDATED + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_VERSION + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_ICON + " BLOB, " +
                    COLUMN_ICON_LOW_RES + " BLOB, " +
                    COLUMN_ICON_COLOR + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_LABEL + " TEXT, " +
                    COLUMN_SYSTEM_STATE + " TEXT, " +
                    "PRIMARY KEY (" + COLUMN_COMPONENT + ", " + COLUMN_USER + ") " +
                    ");");
        }
    }

    private ContentValues newContentValues(Bitmap icon, Bitmap lowResIcon, int iconColor,
            String label, String packageName) {
        ContentValues values = new ContentValues();
        values.put(IconDB.COLUMN_ICON, Utilities.flattenBitmap(icon));
        values.put(IconDB.COLUMN_ICON_LOW_RES, Utilities.flattenBitmap(lowResIcon));
        values.put(IconDB.COLUMN_ICON_COLOR, iconColor);

        values.put(IconDB.COLUMN_LABEL, label);
        values.put(IconDB.COLUMN_SYSTEM_STATE, mIconProvider.getIconSystemState(packageName));

        return values;
    }

    /**
     * Generates a new low-res icon given a high-res icon.
     */
    private Bitmap generateLowResIcon(Bitmap icon) {
        return Bitmap.createScaledBitmap(icon,
                icon.getWidth() / LOW_RES_SCALE_FACTOR,
                icon.getHeight() / LOW_RES_SCALE_FACTOR, true);
    }

    private static Bitmap loadIconNoResize(Cursor c, int iconIndex, BitmapFactory.Options options) {
        byte[] data = c.getBlob(iconIndex);
        try {
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (Exception e) {
            return null;
        }
    }

    private class ActivityInfoProvider extends Provider<LauncherActivityInfo> {

        private final Intent mIntent;
        private final UserHandle mUser;

        public ActivityInfoProvider(Intent intent, UserHandle user) {
            mIntent = intent;
            mUser = user;
        }

        @Override
        public LauncherActivityInfo get() {
            return mLauncherApps.resolveActivity(mIntent, mUser);
        }
    }

    /**
     * Interface for receiving itemInfo with high-res icon.
     */
    public interface ItemInfoUpdateReceiver {

        void reapplyItemInfo(ItemInfoWithIcon info);
    }
}
