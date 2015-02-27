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

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;

import com.android.launcher3.compat.LauncherActivityInfoCompat;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.compat.UserManagerCompat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

/**
 * Cache of application icons.  Icons can be made from any thread.
 */
public class IconCache {

    private static final String TAG = "Launcher.IconCache";

    private static final int INITIAL_ICON_CACHE_CAPACITY = 50;

    // Empty class name is used for storing package default entry.
    private static final String EMPTY_CLASS_NAME = ".";

    private static final boolean DEBUG = false;

    private static class CacheEntry {
        public Bitmap icon;
        public CharSequence title;
        public CharSequence contentDescription;
    }

    private static class CacheKey {
        public ComponentName componentName;
        public UserHandleCompat user;

        CacheKey(ComponentName componentName, UserHandleCompat user) {
            this.componentName = componentName;
            this.user = user;
        }

        @Override
        public int hashCode() {
            return componentName.hashCode() + user.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            CacheKey other = (CacheKey) o;
            return other.componentName.equals(componentName) && other.user.equals(user);
        }
    }

    private final HashMap<UserHandleCompat, Bitmap> mDefaultIcons =
            new HashMap<UserHandleCompat, Bitmap>();
    private final Context mContext;
    private final PackageManager mPackageManager;
    private final UserManagerCompat mUserManager;
    private final LauncherAppsCompat mLauncherApps;
    private final HashMap<CacheKey, CacheEntry> mCache =
            new HashMap<CacheKey, CacheEntry>(INITIAL_ICON_CACHE_CAPACITY);
    private final int mIconDpi;
    private final IconDB mIconDb;

    public IconCache(Context context) {
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        mContext = context;
        mPackageManager = context.getPackageManager();
        mUserManager = UserManagerCompat.getInstance(mContext);
        mLauncherApps = LauncherAppsCompat.getInstance(mContext);
        mIconDpi = activityManager.getLauncherLargeIconDensity();
        mIconDb = new IconDB(context);
    }

    private Drawable getFullResDefaultActivityIcon() {
        return getFullResIcon(Resources.getSystem(), android.R.mipmap.sym_def_app_icon);
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

    public int getFullResIconDpi() {
        return mIconDpi;
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

    private Bitmap makeDefaultIcon(UserHandleCompat user) {
        Drawable unbadged = getFullResDefaultActivityIcon();
        Drawable d = mUserManager.getBadgedDrawableForUser(unbadged, user);
        Bitmap b = Bitmap.createBitmap(Math.max(d.getIntrinsicWidth(), 1),
                Math.max(d.getIntrinsicHeight(), 1),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, b.getWidth(), b.getHeight());
        d.draw(c);
        c.setBitmap(null);
        return b;
    }

    /**
     * Remove any records for the supplied ComponentName.
     */
    public synchronized void remove(ComponentName componentName, UserHandleCompat user) {
        mCache.remove(new CacheKey(componentName, user));
    }

    /**
     * Remove any records for the supplied package name from memory.
     */
    private void removeFromMemCacheLocked(String packageName, UserHandleCompat user) {
        HashSet<CacheKey> forDeletion = new HashSet<CacheKey>();
        for (CacheKey key: mCache.keySet()) {
            if (key.componentName.getPackageName().equals(packageName)
                    && key.user.equals(user)) {
                forDeletion.add(key);
            }
        }
        for (CacheKey condemned: forDeletion) {
            mCache.remove(condemned);
        }
    }

    /**
     * Updates the entries related to the given package in memory and persistent DB.
     */
    public synchronized void updateIconsForPkg(String packageName, UserHandleCompat user) {
        removeIconsForPkg(packageName, user);
        try {
            PackageInfo info = mPackageManager.getPackageInfo(packageName,
                    PackageManager.GET_UNINSTALLED_PACKAGES);
            long userSerial = mUserManager.getSerialNumberForUser(user);
            for (LauncherActivityInfoCompat app : mLauncherApps.getActivityList(packageName, user)) {
                addIconToDB(app, info, userSerial);
            }
        } catch (NameNotFoundException e) {
            Log.d(TAG, "Package not found", e);
            return;
        }
    }

    /**
     * Removes the entries related to the given package in memory and persistent DB.
     */
    public synchronized void removeIconsForPkg(String packageName, UserHandleCompat user) {
        removeFromMemCacheLocked(packageName, user);
        long userSerial = mUserManager.getSerialNumberForUser(user);
        mIconDb.getWritableDatabase().delete(IconDB.TABLE_NAME,
                IconDB.COLUMN_COMPONENT + " LIKE ? AND " + IconDB.COLUMN_USER + " = ?",
                new String[] {packageName + "/%", Long.toString(userSerial)});
    }

    /**
     * Updates the persistent DB, such that only entries corresponding to {@param apps} remain in
     * the DB and are updated.
     * @return The set of packages for which icons have updated.
     */
    public HashSet<String> updateDBIcons(UserHandleCompat user, List<LauncherActivityInfoCompat> apps) {
        long userSerial = mUserManager.getSerialNumberForUser(user);
        PackageManager pm = mContext.getPackageManager();
        HashMap<String, PackageInfo> pkgInfoMap = new HashMap<String, PackageInfo>();
        for (PackageInfo info : pm.getInstalledPackages(PackageManager.GET_UNINSTALLED_PACKAGES)) {
            pkgInfoMap.put(info.packageName, info);
        }

        HashMap<ComponentName, LauncherActivityInfoCompat> componentMap = new HashMap<>();
        for (LauncherActivityInfoCompat app : apps) {
            componentMap.put(app.getComponentName(), app);
        }

        Cursor c = mIconDb.getReadableDatabase().query(IconDB.TABLE_NAME,
                new String[] {IconDB.COLUMN_ROWID, IconDB.COLUMN_COMPONENT,
                    IconDB.COLUMN_LAST_UPDATED, IconDB.COLUMN_VERSION},
                IconDB.COLUMN_USER + " = ? ",
                new String[] {Long.toString(userSerial)},
                null, null, null);

        final int indexComponent = c.getColumnIndex(IconDB.COLUMN_COMPONENT);
        final int indexLastUpdate = c.getColumnIndex(IconDB.COLUMN_LAST_UPDATED);
        final int indexVersion = c.getColumnIndex(IconDB.COLUMN_VERSION);
        final int rowIndex = c.getColumnIndex(IconDB.COLUMN_ROWID);

        HashSet<Integer> itemsToRemove = new HashSet<Integer>();
        HashSet<String> updatedPackages = new HashSet<String>();

        while (c.moveToNext()) {
            String cn = c.getString(indexComponent);
            ComponentName component = ComponentName.unflattenFromString(cn);
            PackageInfo info = pkgInfoMap.get(component.getPackageName());
            if (info == null) {
                itemsToRemove.add(c.getInt(rowIndex));
                continue;
            }
            if ((info.applicationInfo.flags & ApplicationInfo.FLAG_IS_DATA_ONLY) != 0) {
                // Application is not present
                continue;
            }

            long updateTime = c.getLong(indexLastUpdate);
            int version = c.getInt(indexVersion);
            LauncherActivityInfoCompat app = componentMap.remove(component);
            if (version == info.versionCode && updateTime == info.lastUpdateTime) {
                continue;
            }
            if (app == null) {
                itemsToRemove.add(c.getInt(rowIndex));
                continue;
            }
            ContentValues values = updateCacheAndGetContentValues(app);
            mIconDb.getWritableDatabase().update(IconDB.TABLE_NAME, values,
                    IconDB.COLUMN_COMPONENT + " = ?",
                    new String[] { cn });

            updatedPackages.add(component.getPackageName());
        }
        c.close();
        if (!itemsToRemove.isEmpty()) {
            mIconDb.getWritableDatabase().delete(IconDB.TABLE_NAME,
                    IconDB.COLUMN_ROWID + " IN ( " + TextUtils.join(", ", itemsToRemove) +" )",
                    null);
        }

        // Insert remaining apps.
        for (LauncherActivityInfoCompat app : componentMap.values()) {
            PackageInfo info = pkgInfoMap.get(app.getComponentName().getPackageName());
            if (info == null) {
                continue;
            }
            addIconToDB(app, info, userSerial);
        }
        return updatedPackages;
    }

    private void addIconToDB(LauncherActivityInfoCompat app, PackageInfo info, long userSerial) {
        ContentValues values = updateCacheAndGetContentValues(app);
        values.put(IconDB.COLUMN_COMPONENT, app.getComponentName().flattenToString());
        values.put(IconDB.COLUMN_USER, userSerial);
        values.put(IconDB.COLUMN_LAST_UPDATED, info.lastUpdateTime);
        values.put(IconDB.COLUMN_VERSION, info.versionCode);
        mIconDb.getWritableDatabase().insertWithOnConflict(IconDB.TABLE_NAME, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    private ContentValues updateCacheAndGetContentValues(LauncherActivityInfoCompat app) {
        CacheEntry entry = new CacheEntry();
        entry.icon = Utilities.createIconBitmap(app.getBadgedIcon(mIconDpi), mContext);
        entry.title = app.getLabel();
        entry.contentDescription = mUserManager.getBadgedLabelForUser(entry.title, app.getUser());
        mCache.put(new CacheKey(app.getComponentName(), app.getUser()), entry);

        ContentValues values = new ContentValues();
        values.put(IconDB.COLUMN_ICON, ItemInfo.flattenBitmap(entry.icon));
        values.put(IconDB.COLUMN_LABEL, entry.title.toString());
        return values;
    }


    /**
     * Empty out the cache.
     */
    public synchronized void flush() {
        mCache.clear();
    }

    /**
     * Empty out the cache that aren't of the correct grid size
     */
    public synchronized void flushInvalidIcons(DeviceProfile grid) {
        Iterator<Entry<CacheKey, CacheEntry>> it = mCache.entrySet().iterator();
        while (it.hasNext()) {
            final CacheEntry e = it.next().getValue();
            if ((e.icon != null) && (e.icon.getWidth() < grid.iconSizePx
                    || e.icon.getHeight() < grid.iconSizePx)) {
                it.remove();
            }
        }
    }

    /**
     * Fill in "application" with the icon and label for "info."
     */
    public synchronized void getTitleAndIcon(AppInfo application, LauncherActivityInfoCompat info) {
        CacheEntry entry = cacheLocked(application.componentName, info, info.getUser(), false);

        application.title = entry.title;
        application.iconBitmap = entry.icon;
        application.contentDescription = entry.contentDescription;
    }

    public synchronized Bitmap getIcon(Intent intent, UserHandleCompat user) {
        ComponentName component = intent.getComponent();
        // null info means not installed, but if we have a component from the intent then
        // we should still look in the cache for restored app icons.
        if (component == null) {
            return getDefaultIcon(user);
        }

        LauncherActivityInfoCompat launcherActInfo = mLauncherApps.resolveActivity(intent, user);
        CacheEntry entry = cacheLocked(component, launcherActInfo, user, true);
        return entry.icon;
    }

    /**
     * Fill in {@param shortcutInfo} with the icon and label for {@param intent}. If the
     * corresponding activity is not found, it reverts to the package icon.
     */
    public synchronized void getTitleAndIcon(ShortcutInfo shortcutInfo, Intent intent,
            UserHandleCompat user) {
        ComponentName component = intent.getComponent();
        // null info means not installed, but if we have a component from the intent then
        // we should still look in the cache for restored app icons.
        if (component == null) {
            shortcutInfo.setIcon(getDefaultIcon(user));
            shortcutInfo.title = "";
            shortcutInfo.usingFallbackIcon = true;
        } else {
            LauncherActivityInfoCompat info = mLauncherApps.resolveActivity(intent, user);
            getTitleAndIcon(shortcutInfo, component, info, user, true);
        }
    }

    /**
     * Fill in {@param shortcutInfo} with the icon and label for {@param info}
     */
    public synchronized void getTitleAndIcon(
            ShortcutInfo shortcutInfo, ComponentName component, LauncherActivityInfoCompat info,
            UserHandleCompat user, boolean usePkgIcon) {
        CacheEntry entry = cacheLocked(component, info, user, usePkgIcon);
        shortcutInfo.setIcon(entry.icon);
        shortcutInfo.title = entry.title;
        shortcutInfo.usingFallbackIcon = isDefaultIcon(entry.icon, user);
    }

    public synchronized Bitmap getDefaultIcon(UserHandleCompat user) {
        if (!mDefaultIcons.containsKey(user)) {
            mDefaultIcons.put(user, makeDefaultIcon(user));
        }
        return mDefaultIcons.get(user);
    }

    public boolean isDefaultIcon(Bitmap icon, UserHandleCompat user) {
        return mDefaultIcons.get(user) == icon;
    }

    /**
     * Retrieves the entry from the cache. If the entry is not present, it creates a new entry.
     * This method is not thread safe, it must be called from a synchronized method.
     */
    private CacheEntry cacheLocked(ComponentName componentName, LauncherActivityInfoCompat info,
            UserHandleCompat user, boolean usePackageIcon) {
        CacheKey cacheKey = new CacheKey(componentName, user);
        CacheEntry entry = mCache.get(cacheKey);
        if (entry == null) {
            entry = new CacheEntry();
            mCache.put(cacheKey, entry);

            // Check the DB first.
            if (!getEntryFromDB(componentName, user, entry)) {
                if (info != null) {
                    entry.icon = Utilities.createIconBitmap(info.getBadgedIcon(mIconDpi), mContext);
                } else {
                    if (usePackageIcon) {
                        CacheEntry packageEntry = getEntryForPackage(
                                componentName.getPackageName(), user);
                        if (packageEntry != null) {
                            if (DEBUG) Log.d(TAG, "using package default icon for " +
                                    componentName.toShortString());
                            entry.icon = packageEntry.icon;
                            entry.title = packageEntry.title;
                            entry.contentDescription = packageEntry.contentDescription;
                        }
                    }
                    if (entry.icon == null) {
                        if (DEBUG) Log.d(TAG, "using default icon for " +
                                componentName.toShortString());
                        entry.icon = getDefaultIcon(user);
                    }
                }
            }

            if (TextUtils.isEmpty(entry.title) && info != null) {
                entry.title = info.getLabel().toString();
                entry.contentDescription = mUserManager.getBadgedLabelForUser(entry.title, user);
            }
        }
        return entry;
    }

    /**
     * Adds a default package entry in the cache. This entry is not persisted and will be removed
     * when the cache is flushed.
     */
    public synchronized void cachePackageInstallInfo(String packageName, UserHandleCompat user,
            Bitmap icon, CharSequence title) {
        removeFromMemCacheLocked(packageName, user);

        CacheEntry entry = getEntryForPackage(packageName, user);
        if (!TextUtils.isEmpty(title)) {
            entry.title = title;
        }
        if (icon != null) {
            entry.icon = Utilities.createIconBitmap(icon, mContext);
        }
    }

    /**
     * Gets an entry for the package, which can be used as a fallback entry for various components.
     * This method is not thread safe, it must be called from a synchronized method.
     */
    private CacheEntry getEntryForPackage(String packageName, UserHandleCompat user) {
        ComponentName cn = new ComponentName(packageName, EMPTY_CLASS_NAME);;
        CacheKey cacheKey = new CacheKey(cn, user);
        CacheEntry entry = mCache.get(cacheKey);
        if (entry == null) {
            entry = new CacheEntry();
            entry.title = "";
            entry.contentDescription = "";
            mCache.put(cacheKey, entry);

            try {
                ApplicationInfo info = mPackageManager.getApplicationInfo(packageName, 0);
                entry.icon = Utilities.createIconBitmap(info.loadIcon(mPackageManager), mContext);
                entry.title = info.loadLabel(mPackageManager);
                entry.contentDescription = mUserManager.getBadgedLabelForUser(entry.title, user);
            } catch (NameNotFoundException e) {
                if (DEBUG) Log.d(TAG, "Application not installed " + packageName);
            }
        }
        return entry;
    }

    /**
     * Pre-load an icon into the persistent cache.
     *
     * <P>Queries for a component that does not exist in the package manager
     * will be answered by the persistent cache.
     *
     * @param componentName the icon should be returned for this component
     * @param icon the icon to be persisted
     * @param dpi the native density of the icon
     */
    public void preloadIcon(ComponentName componentName, Bitmap icon, int dpi, String label,
            long userSerial) {
        // TODO rescale to the correct native DPI
        try {
            PackageManager packageManager = mContext.getPackageManager();
            packageManager.getActivityIcon(componentName);
            // component is present on the system already, do nothing
            return;
        } catch (PackageManager.NameNotFoundException e) {
            // pass
        }

        ContentValues values = new ContentValues();
        values.put(IconDB.COLUMN_COMPONENT, componentName.flattenToString());
        values.put(IconDB.COLUMN_USER, userSerial);
        values.put(IconDB.COLUMN_ICON, ItemInfo.flattenBitmap(icon));
        values.put(IconDB.COLUMN_LABEL, label);
        mIconDb.getWritableDatabase().insertWithOnConflict(IconDB.TABLE_NAME, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    private boolean getEntryFromDB(ComponentName component, UserHandleCompat user, CacheEntry entry) {
        Cursor c = mIconDb.getReadableDatabase().query(IconDB.TABLE_NAME,
                new String[] {IconDB.COLUMN_ICON, IconDB.COLUMN_LABEL},
                IconDB.COLUMN_COMPONENT + " = ? AND " + IconDB.COLUMN_USER + " = ?",
                new String[] {component.flattenToString(),
                    Long.toString(mUserManager.getSerialNumberForUser(user))},
                null, null, null);
        try {
            if (c.moveToNext()) {
                entry.icon = Utilities.createIconBitmap(c, 0, mContext);
                entry.title = c.getString(1);
                if (entry.title == null) {
                    entry.title = "";
                    entry.contentDescription = "";
                } else {
                    entry.contentDescription = mUserManager.getBadgedLabelForUser(entry.title, user);
                }
                return true;
            }
        } finally {
            c.close();
        }
        return false;
    }

    private static final class IconDB extends SQLiteOpenHelper {
        private final static int DB_VERSION = 1;

        private final static String TABLE_NAME = "icons";
        private final static String COLUMN_ROWID = "rowid";
        private final static String COLUMN_COMPONENT = "componentName";
        private final static String COLUMN_USER = "profileId";
        private final static String COLUMN_LAST_UPDATED = "lastUpdated";
        private final static String COLUMN_VERSION = "version";
        private final static String COLUMN_ICON = "icon";
        private final static String COLUMN_LABEL = "label";

        public IconDB(Context context) {
            super(context, LauncherFiles.APP_ICONS_DB, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    COLUMN_COMPONENT + " TEXT NOT NULL, " +
                    COLUMN_USER + " INTEGER NOT NULL, " +
                    COLUMN_LAST_UPDATED + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_VERSION + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_ICON + " BLOB, " +
                    COLUMN_LABEL + " TEXT, " +
                    "PRIMARY KEY (" + COLUMN_COMPONENT + ", " + COLUMN_USER + ") " +
                    ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion != newVersion) {
                clearDB(db);
            }
        }

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion != newVersion) {
                clearDB(db);
            }
        }

        private void clearDB(SQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
            onCreate(db);
        }
    }
}
