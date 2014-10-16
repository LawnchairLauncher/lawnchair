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
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;

import com.android.launcher3.compat.LauncherActivityInfoCompat;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.compat.UserManagerCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * Cache of application icons.  Icons can be made from any thread.
 */
public class IconCache {

    private static final String TAG = "Launcher.IconCache";

    private static final int INITIAL_ICON_CACHE_CAPACITY = 50;
    private static final String RESOURCE_FILE_PREFIX = "icon_";

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
    private int mIconDpi;

    public IconCache(Context context) {
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        mContext = context;
        mPackageManager = context.getPackageManager();
        mUserManager = UserManagerCompat.getInstance(mContext);
        mLauncherApps = LauncherAppsCompat.getInstance(mContext);
        mIconDpi = activityManager.getLauncherLargeIconDensity();

        // need to set mIconDpi before getting default icon
        UserHandleCompat myUser = UserHandleCompat.myUserHandle();
        mDefaultIcons.put(myUser, makeDefaultIcon(myUser));
    }

    public Drawable getFullResDefaultActivityIcon() {
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
     * Remove any records for the supplied package name.
     */
    public synchronized void remove(String packageName, UserHandleCompat user) {
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
    public synchronized void getTitleAndIcon(AppInfo application, LauncherActivityInfoCompat info,
            HashMap<Object, CharSequence> labelCache) {
        CacheEntry entry = cacheLocked(application.componentName, info, labelCache,
                info.getUser(), false);

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
        CacheEntry entry = cacheLocked(component, launcherActInfo, null, user, true);
        return entry.icon;
    }

    /**
     * Fill in "shortcutInfo" with the icon and label for "info."
     */
    public synchronized void getTitleAndIcon(ShortcutInfo shortcutInfo, Intent intent,
            UserHandleCompat user, boolean usePkgIcon) {
        ComponentName component = intent.getComponent();
        // null info means not installed, but if we have a component from the intent then
        // we should still look in the cache for restored app icons.
        if (component == null) {
            shortcutInfo.setIcon(getDefaultIcon(user));
            shortcutInfo.title = "";
            shortcutInfo.usingFallbackIcon = true;
        } else {
            LauncherActivityInfoCompat launcherActInfo =
                    mLauncherApps.resolveActivity(intent, user);
            CacheEntry entry = cacheLocked(component, launcherActInfo, null, user, usePkgIcon);

            shortcutInfo.setIcon(entry.icon);
            shortcutInfo.title = entry.title;
            shortcutInfo.usingFallbackIcon = isDefaultIcon(entry.icon, user);
        }
    }


    public synchronized Bitmap getDefaultIcon(UserHandleCompat user) {
        if (!mDefaultIcons.containsKey(user)) {
            mDefaultIcons.put(user, makeDefaultIcon(user));
        }
        return mDefaultIcons.get(user);
    }

    public synchronized Bitmap getIcon(ComponentName component, LauncherActivityInfoCompat info,
            HashMap<Object, CharSequence> labelCache) {
        if (info == null || component == null) {
            return null;
        }

        CacheEntry entry = cacheLocked(component, info, labelCache, info.getUser(), false);
        return entry.icon;
    }

    public boolean isDefaultIcon(Bitmap icon, UserHandleCompat user) {
        return mDefaultIcons.get(user) == icon;
    }

    /**
     * Retrieves the entry from the cache. If the entry is not present, it creates a new entry.
     * This method is not thread safe, it must be called from a synchronized method.
     */
    private CacheEntry cacheLocked(ComponentName componentName, LauncherActivityInfoCompat info,
            HashMap<Object, CharSequence> labelCache, UserHandleCompat user, boolean usePackageIcon) {
        CacheKey cacheKey = new CacheKey(componentName, user);
        CacheEntry entry = mCache.get(cacheKey);
        if (entry == null) {
            entry = new CacheEntry();

            mCache.put(cacheKey, entry);

            if (info != null) {
                ComponentName labelKey = info.getComponentName();
                if (labelCache != null && labelCache.containsKey(labelKey)) {
                    entry.title = labelCache.get(labelKey).toString();
                } else {
                    entry.title = info.getLabel().toString();
                    if (labelCache != null) {
                        labelCache.put(labelKey, entry.title);
                    }
                }

                entry.contentDescription = mUserManager.getBadgedLabelForUser(entry.title, user);
                entry.icon = Utilities.createIconBitmap(
                        info.getBadgedIcon(mIconDpi), mContext);
            } else {
                entry.title = "";
                Bitmap preloaded = getPreloadedIcon(componentName, user);
                if (preloaded != null) {
                    if (DEBUG) Log.d(TAG, "using preloaded icon for " +
                            componentName.toShortString());
                    entry.icon = preloaded;
                } else {
                    if (usePackageIcon) {
                        CacheEntry packageEntry = getEntryForPackage(
                                componentName.getPackageName(), user);
                        if (packageEntry != null) {
                            if (DEBUG) Log.d(TAG, "using package default icon for " +
                                    componentName.toShortString());
                            entry.icon = packageEntry.icon;
                            entry.title = packageEntry.title;
                        }
                    }
                    if (entry.icon == null) {
                        if (DEBUG) Log.d(TAG, "using default icon for " +
                                componentName.toShortString());
                        entry.icon = getDefaultIcon(user);
                    }
                }
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
        remove(packageName, user);

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
            mCache.put(cacheKey, entry);

            try {
                ApplicationInfo info = mPackageManager.getApplicationInfo(packageName, 0);
                entry.title = info.loadLabel(mPackageManager);
                entry.icon = Utilities.createIconBitmap(info.loadIcon(mPackageManager), mContext);
            } catch (NameNotFoundException e) {
                if (DEBUG) Log.d(TAG, "Application not installed " + packageName);
            }

            if (entry.icon == null) {
                entry.icon = getPreloadedIcon(cn, user);
            }
        }
        return entry;
    }

    public synchronized HashMap<ComponentName,Bitmap> getAllIcons() {
        HashMap<ComponentName,Bitmap> set = new HashMap<ComponentName,Bitmap>();
        for (CacheKey ck : mCache.keySet()) {
            final CacheEntry e = mCache.get(ck);
            set.put(ck.componentName, e.icon);
        }
        return set;
    }

    /**
     * Pre-load an icon into the persistent cache.
     *
     * <P>Queries for a component that does not exist in the package manager
     * will be answered by the persistent cache.
     *
     * @param context application context
     * @param componentName the icon should be returned for this component
     * @param icon the icon to be persisted
     * @param dpi the native density of the icon
     */
    public static void preloadIcon(Context context, ComponentName componentName, Bitmap icon,
            int dpi) {
        // TODO rescale to the correct native DPI
        try {
            PackageManager packageManager = context.getPackageManager();
            packageManager.getActivityIcon(componentName);
            // component is present on the system already, do nothing
            return;
        } catch (PackageManager.NameNotFoundException e) {
            // pass
        }

        final String key = componentName.flattenToString();
        FileOutputStream resourceFile = null;
        try {
            resourceFile = context.openFileOutput(getResourceFilename(componentName),
                    Context.MODE_PRIVATE);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            if (icon.compress(android.graphics.Bitmap.CompressFormat.PNG, 75, os)) {
                byte[] buffer = os.toByteArray();
                resourceFile.write(buffer, 0, buffer.length);
            } else {
                Log.w(TAG, "failed to encode cache for " + key);
                return;
            }
        } catch (FileNotFoundException e) {
            Log.w(TAG, "failed to pre-load cache for " + key, e);
        } catch (IOException e) {
            Log.w(TAG, "failed to pre-load cache for " + key, e);
        } finally {
            if (resourceFile != null) {
                try {
                    resourceFile.close();
                } catch (IOException e) {
                    Log.d(TAG, "failed to save restored icon for: " + key, e);
                }
            }
        }
    }

    /**
     * Read a pre-loaded icon from the persistent icon cache.
     *
     * @param componentName the component that should own the icon
     * @returns a bitmap if one is cached, or null.
     */
    private Bitmap getPreloadedIcon(ComponentName componentName, UserHandleCompat user) {
        final String key = componentName.flattenToShortString();

        // We don't keep icons for other profiles in persistent cache.
        if (!user.equals(UserHandleCompat.myUserHandle())) {
            return null;
        }

        if (DEBUG) Log.v(TAG, "looking for pre-load icon for " + key);
        Bitmap icon = null;
        FileInputStream resourceFile = null;
        try {
            resourceFile = mContext.openFileInput(getResourceFilename(componentName));
            byte[] buffer = new byte[1024];
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int bytesRead = 0;
            while(bytesRead >= 0) {
                bytes.write(buffer, 0, bytesRead);
                bytesRead = resourceFile.read(buffer, 0, buffer.length);
            }
            if (DEBUG) Log.d(TAG, "read " + bytes.size());
            icon = BitmapFactory.decodeByteArray(bytes.toByteArray(), 0, bytes.size());
            if (icon == null) {
                Log.w(TAG, "failed to decode pre-load icon for " + key);
            }
        } catch (FileNotFoundException e) {
            if (DEBUG) Log.d(TAG, "there is no restored icon for: " + key);
        } catch (IOException e) {
            Log.w(TAG, "failed to read pre-load icon for: " + key, e);
        } finally {
            if(resourceFile != null) {
                try {
                    resourceFile.close();
                } catch (IOException e) {
                    Log.d(TAG, "failed to manage pre-load icon file: " + key, e);
                }
            }
        }

        return icon;
    }

    /**
     * Remove a pre-loaded icon from the persistent icon cache.
     *
     * @param componentName the component that should own the icon
     */
    public void deletePreloadedIcon(ComponentName componentName, UserHandleCompat user) {
        // We don't keep icons for other profiles in persistent cache.
        if (!user.equals(UserHandleCompat.myUserHandle()) || componentName == null) {
            return;
        }
        remove(componentName, user);
        boolean success = mContext.deleteFile(getResourceFilename(componentName));
        if (DEBUG && success) Log.d(TAG, "removed pre-loaded icon from persistent cache");
    }

    private static String getResourceFilename(ComponentName component) {
        String resourceName = component.flattenToShortString();
        String filename = resourceName.replace(File.separatorChar, '_');
        return RESOURCE_FILE_PREFIX + filename;
    }
}
