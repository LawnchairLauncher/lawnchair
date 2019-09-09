/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.systemui.shared.recents.model;

import static android.content.pm.PackageManager.MATCH_ANY_USER;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.util.IconDrawableFactory;
import android.util.Log;
import android.util.LruCache;

import com.android.systemui.shared.system.PackageManagerWrapper;

public abstract class IconLoader {

    private static final String TAG = "IconLoader";

    protected final Context mContext;
    protected final TaskKeyLruCache<Drawable> mIconCache;
    protected final LruCache<ComponentName, ActivityInfo> mActivityInfoCache;

    public IconLoader(Context context, TaskKeyLruCache<Drawable> iconCache, LruCache<ComponentName,
            ActivityInfo> activityInfoCache) {
        mContext = context;
        mIconCache = iconCache;
        mActivityInfoCache = activityInfoCache;
    }

    /**
     * Returns the activity info for the given task key, retrieving one from the system if the
     * task key is expired.
     *
     * TODO: Move this to an ActivityInfoCache class
     */
    public ActivityInfo getAndUpdateActivityInfo(Task.TaskKey taskKey) {
        ComponentName cn = taskKey.getComponent();
        ActivityInfo activityInfo = mActivityInfoCache.get(cn);
        if (activityInfo == null) {
            activityInfo = PackageManagerWrapper.getInstance().getActivityInfo(cn, taskKey.userId);
            if (cn == null || activityInfo == null) {
                Log.e(TAG, "Unexpected null component name or activity info: " + cn + ", " +
                        activityInfo);
                return null;
            }
            mActivityInfoCache.put(cn, activityInfo);
        }
        return activityInfo;
    }

    public Drawable getIcon(Task t) {
        Drawable cachedIcon = mIconCache.get(t.key);
        if (cachedIcon == null) {
            cachedIcon = createNewIconForTask(t.key, t.taskDescription, true /* returnDefault */);
            mIconCache.put(t.key, cachedIcon);
        }
        return cachedIcon;
    }

    /**
     * Returns the cached task icon if the task key is not expired, updating the cache if it is.
     */
    public Drawable getAndInvalidateIfModified(Task.TaskKey taskKey,
            ActivityManager.TaskDescription td, boolean loadIfNotCached) {
        // Return the cached activity icon if it exists
        Drawable icon = mIconCache.getAndInvalidateIfModified(taskKey);
        if (icon != null) {
            return icon;
        }

        if (loadIfNotCached) {
            icon = createNewIconForTask(taskKey, td, false /* returnDefault */);
            if (icon != null) {
                mIconCache.put(taskKey, icon);
                return icon;
            }
        }

        // We couldn't load any icon
        return null;
    }

    private Drawable createNewIconForTask(Task.TaskKey taskKey,
            ActivityManager.TaskDescription desc, boolean returnDefault) {
        int userId = taskKey.userId;
        Bitmap tdIcon = desc.getInMemoryIcon();
        if (tdIcon != null) {
            return createDrawableFromBitmap(tdIcon, userId, desc);
        }
        if (desc.getIconResource() != 0) {
            try {
                PackageManager pm = mContext.getPackageManager();
                ApplicationInfo appInfo = pm.getApplicationInfo(taskKey.getPackageName(),
                        MATCH_ANY_USER);
                Resources res = pm.getResourcesForApplication(appInfo);
                return createBadgedDrawable(res.getDrawable(desc.getIconResource(), null), userId,
                        desc);
            } catch (Resources.NotFoundException|PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Could not find icon drawable from resource", e);
            }
        }

        tdIcon = ActivityManager.TaskDescription.loadTaskDescriptionIcon(
                desc.getIconFilename(), userId);
        if (tdIcon != null) {
            return createDrawableFromBitmap(tdIcon, userId, desc);
        }

        // Load the icon from the activity info and cache it
        ActivityInfo activityInfo = getAndUpdateActivityInfo(taskKey);
        if (activityInfo != null) {
            Drawable icon = getBadgedActivityIcon(activityInfo, userId, desc);
            if (icon != null) {
                return icon;
            }
        }

        // At this point, even if we can't load the icon, we will set the default icon.
        return returnDefault ? getDefaultIcon(userId) : null;
    }

    public abstract Drawable getDefaultIcon(int userId);

    protected Drawable createDrawableFromBitmap(Bitmap icon, int userId,
            ActivityManager.TaskDescription desc) {
        return createBadgedDrawable(
                new BitmapDrawable(mContext.getResources(), icon), userId, desc);
    }

    protected abstract Drawable createBadgedDrawable(Drawable icon, int userId,
            ActivityManager.TaskDescription desc);

    /**
     * @return the activity icon for the ActivityInfo for a user, badging if necessary.
     */
    protected abstract Drawable getBadgedActivityIcon(ActivityInfo info, int userId,
            ActivityManager.TaskDescription desc);

    public static class DefaultIconLoader extends IconLoader {

        private final BitmapDrawable mDefaultIcon;
        private final IconDrawableFactory mDrawableFactory;

        public DefaultIconLoader(Context context, TaskKeyLruCache<Drawable> iconCache,
                LruCache<ComponentName, ActivityInfo> activityInfoCache) {
            super(context, iconCache, activityInfoCache);

            // Create the default assets
            Bitmap icon = Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8);
            icon.eraseColor(0);
            mDefaultIcon = new BitmapDrawable(context.getResources(), icon);
            mDrawableFactory = IconDrawableFactory.newInstance(context);
        }

        @Override
        public Drawable getDefaultIcon(int userId) {
            return mDefaultIcon;
        }

        @Override
        protected Drawable createBadgedDrawable(Drawable icon, int userId,
                ActivityManager.TaskDescription desc) {
            if (userId != UserHandle.myUserId()) {
                icon = mContext.getPackageManager().getUserBadgedIcon(icon, new UserHandle(userId));
            }
            return icon;
        }

        @Override
        protected Drawable getBadgedActivityIcon(ActivityInfo info, int userId,
                ActivityManager.TaskDescription desc) {
            return mDrawableFactory.getBadgedIcon(info, info.applicationInfo, userId);
        }
    }
}
