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

package com.android.launcher2;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.util.Log;
import android.os.Process;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Cache of application icons.  Icons can be made from any thread.
 */
public class AppInfoCache {
    private static final String TAG = "Launcher.AppInfoCache";

    private static final int INITIAL_ICON_CACHE_CAPACITY = 50;

    private static final HashMap<ComponentName, ApplicationInfo> sCache =
            new HashMap<ComponentName, ApplicationInfo>(INITIAL_ICON_CACHE_CAPACITY);

    /**
     * no public constructor.
     */
    private AppInfoCache() {
    }

    /**
     * For the given ResolveInfo, return an ApplicationInfo and cache the result for later.
     */
    public static ApplicationInfo cache(ResolveInfo info, Context context,
            Utilities.BubbleText bubble) {
        synchronized (sCache) {
            ComponentName componentName = new ComponentName(
                    info.activityInfo.applicationInfo.packageName,
                    info.activityInfo.name);
            ApplicationInfo application = sCache.get(componentName);

            if (application == null) {
                application = new ApplicationInfo();
                application.container = ItemInfo.NO_ID;

                updateTitleAndIcon(info, application, context, bubble);

                application.setActivity(componentName,
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

                sCache.put(componentName, application);
            }

            return application;
        }
    }

    /**
     * Update the entry in the in the cache with its new metadata.
     */
    public static void update(ResolveInfo info, ApplicationInfo applicationInfo, Context context,
            Utilities.BubbleText bubble) {
        synchronized (sCache) {
            updateTitleAndIcon(info, applicationInfo, context, bubble);

            ComponentName componentName = new ComponentName(
                    info.activityInfo.applicationInfo.packageName, info.activityInfo.name);
            sCache.put(componentName, applicationInfo);
        }
    }

    /**
     * Remove any records for the supplied ComponentName.
     */
    public static void remove(ComponentName componentName) {
        synchronized (sCache) {
            sCache.remove(componentName);
        }
    }

    /**
     * Empty out the cache.
     */
    public static void flush() {
        synchronized (sCache) {
            sCache.clear();
        }
    }

    /**
     * Get the icon for the supplied ApplicationInfo.  If that activity already
     * exists in the cache, use that.
     */
    public static Drawable getIconDrawable(PackageManager packageManager, ApplicationInfo info) {
        final ResolveInfo resolveInfo = packageManager.resolveActivity(info.intent, 0);
        if (resolveInfo == null) {
            return null;
        }

        ComponentName componentName = new ComponentName(
                resolveInfo.activityInfo.applicationInfo.packageName,
                resolveInfo.activityInfo.name);
        ApplicationInfo cached;
        synchronized (sCache) {
            cached = sCache.get(componentName);
            if (cached != null) {
                if (cached.icon == null) {
                    cached.icon = resolveInfo.activityInfo.loadIcon(packageManager);
                }
                return cached.icon;
            } else {
                return resolveInfo.activityInfo.loadIcon(packageManager);
            }
        }
    }

    /**
     * Go through the cache and disconnect any of the callbacks in the drawables or we
     * leak the previous Home screen on orientation change.
     */
    public static void unbindDrawables() {
        synchronized (sCache) {
            for (ApplicationInfo appInfo: sCache.values()) {
                if (appInfo.icon != null) {
                    appInfo.icon.setCallback(null);
                }
            }
        }
    }

    /**
     * Update the title and icon.  Don't keep a reference to the context!
     */
    private static void updateTitleAndIcon(ResolveInfo info, ApplicationInfo application,
            Context context, Utilities.BubbleText bubble) {
        final PackageManager packageManager = context.getPackageManager();

        application.title = info.loadLabel(packageManager);
        if (application.title == null) {
            application.title = info.activityInfo.name;
        }

        // TODO: turn this on in froyo, not enough time for testing in mr3
        //if (application.iconBitmap != null) {
        //    application.iconBitmap.recycle();
        //}
        application.iconBitmap = Utilities.createAllAppsBitmap(
                info.activityInfo.loadIcon(packageManager),
                application.title.toString(), bubble, context);
    }
}

