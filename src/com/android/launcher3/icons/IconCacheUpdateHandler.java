/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.Utilities;
import com.android.launcher3.icons.BaseIconCache.IconDB;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * Utility class to handle updating the Icon cache
 */
public class IconCacheUpdateHandler {

    private static final String TAG = "IconCacheUpdateHandler";

    private static final Object ICON_UPDATE_TOKEN = new Object();

    private final HashMap<String, PackageInfo> mPkgInfoMap;
    private final BaseIconCache mIconCache;
    private final HashMap<UserHandle, Set<String>> mPackagesToIgnore = new HashMap<>();

    IconCacheUpdateHandler(BaseIconCache cache) {
        mIconCache = cache;

        mPkgInfoMap = new HashMap<>();

        // Remove all active icon update tasks.
        mIconCache.mWorkerHandler.removeCallbacksAndMessages(ICON_UPDATE_TOKEN);

        createPackageInfoMap();
    }

    public void setPackagesToIgnore(UserHandle userHandle, Set<String> packages) {
        mPackagesToIgnore.put(userHandle, packages);
    }

    private void createPackageInfoMap() {
        PackageManager pm = mIconCache.mPackageManager;
        for (PackageInfo info : pm.getInstalledPackages(PackageManager.GET_UNINSTALLED_PACKAGES)) {
            mPkgInfoMap.put(info.packageName, info);
        }
    }

    /**
     * Updates the persistent DB, such that only entries corresponding to {@param apps} remain in
     * the DB and are updated.
     * @return The set of packages for which icons have updated.
     */
    public <T> void updateIcons(List<T> apps, CachingLogic<T> cachingLogic) {
        if (apps.isEmpty()) {
            return;
        }
        UserHandle user = cachingLogic.getUser(apps.get(0));

        Set<String> ignorePackages = mPackagesToIgnore.get(user);
        if (ignorePackages == null) {
            ignorePackages = Collections.emptySet();
        }

        long userSerial = mIconCache.mUserManager.getSerialNumberForUser(user);
        HashMap<ComponentName, T> componentMap = new HashMap<>();
        for (T app : apps) {
            componentMap.put(cachingLogic.getComponent(app), app);
        }

        HashSet<Integer> itemsToRemove = new HashSet<>();
        Stack<T> appsToUpdate = new Stack<>();

        try (Cursor c = mIconCache.mIconDb.query(
                new String[]{IconDB.COLUMN_ROWID, IconDB.COLUMN_COMPONENT,
                        IconDB.COLUMN_LAST_UPDATED, IconDB.COLUMN_VERSION,
                        IconDB.COLUMN_SYSTEM_STATE},
                IconDB.COLUMN_USER + " = ? ",
                new String[]{Long.toString(userSerial)})) {

            final int indexComponent = c.getColumnIndex(IconDB.COLUMN_COMPONENT);
            final int indexLastUpdate = c.getColumnIndex(IconDB.COLUMN_LAST_UPDATED);
            final int indexVersion = c.getColumnIndex(IconDB.COLUMN_VERSION);
            final int rowIndex = c.getColumnIndex(IconDB.COLUMN_ROWID);
            final int systemStateIndex = c.getColumnIndex(IconDB.COLUMN_SYSTEM_STATE);

            while (c.moveToNext()) {
                String cn = c.getString(indexComponent);
                ComponentName component = ComponentName.unflattenFromString(cn);
                PackageInfo info = mPkgInfoMap.get(component.getPackageName());
                if (info == null) {
                    if (!ignorePackages.contains(component.getPackageName())) {
                        mIconCache.remove(component, user);
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
                T app = componentMap.remove(component);
                if (version == info.versionCode && updateTime == info.lastUpdateTime &&
                        TextUtils.equals(c.getString(systemStateIndex),
                                mIconCache.mIconProvider.getIconSystemState(info.packageName))) {
                    continue;
                }
                if (app == null) {
                    mIconCache.remove(component, user);
                    itemsToRemove.add(c.getInt(rowIndex));
                } else {
                    appsToUpdate.add(app);
                }
            }
        } catch (SQLiteException e) {
            Log.d(TAG, "Error reading icon cache", e);
            // Continue updating whatever we have read so far
        }
        if (!itemsToRemove.isEmpty()) {
            mIconCache.mIconDb.delete(
                    Utilities.createDbSelectionQuery(IconDB.COLUMN_ROWID, itemsToRemove), null);
        }

        // Insert remaining apps.
        if (!componentMap.isEmpty() || !appsToUpdate.isEmpty()) {
            Stack<T> appsToAdd = new Stack<>();
            appsToAdd.addAll(componentMap.values());
            new SerializedIconUpdateTask(userSerial, user, appsToAdd, appsToUpdate, cachingLogic)
                    .scheduleNext();
        }
    }


    /**
     * A runnable that updates invalid icons and adds missing icons in the DB for the provided
     * LauncherActivityInfo list. Items are updated/added one at a time, so that the
     * worker thread doesn't get blocked.
     */
    private class SerializedIconUpdateTask<T> implements Runnable {
        private final long mUserSerial;
        private final UserHandle mUserHandle;
        private final Stack<T> mAppsToAdd;
        private final Stack<T> mAppsToUpdate;
        private final CachingLogic<T> mCachingLogic;
        private final HashSet<String> mUpdatedPackages = new HashSet<>();

        SerializedIconUpdateTask(long userSerial, UserHandle userHandle,
                Stack<T> appsToAdd, Stack<T> appsToUpdate, CachingLogic<T> cachingLogic) {
            mUserHandle = userHandle;
            mUserSerial = userSerial;
            mAppsToAdd = appsToAdd;
            mAppsToUpdate = appsToUpdate;
            mCachingLogic = cachingLogic;
        }

        @Override
        public void run() {
            if (!mAppsToUpdate.isEmpty()) {
                T app = mAppsToUpdate.pop();
                String pkg = mCachingLogic.getComponent(app).getPackageName();
                PackageInfo info = mPkgInfoMap.get(pkg);
                mIconCache.addIconToDBAndMemCache(
                        app, mCachingLogic, info, mUserSerial, true /*replace existing*/);
                mUpdatedPackages.add(pkg);

                if (mAppsToUpdate.isEmpty() && !mUpdatedPackages.isEmpty()) {
                    // No more app to update. Notify model.
                    LauncherAppState.getInstance(mIconCache.mContext).getModel()
                            .onPackageIconsUpdated(mUpdatedPackages, mUserHandle);
                }

                // Let it run one more time.
                scheduleNext();
            } else if (!mAppsToAdd.isEmpty()) {
                T app = mAppsToAdd.pop();
                PackageInfo info = mPkgInfoMap.get(mCachingLogic.getComponent(app).getPackageName());
                // We do not check the mPkgInfoMap when generating the mAppsToAdd. Although every
                // app should have package info, this is not guaranteed by the api
                if (info != null) {
                    mIconCache.addIconToDBAndMemCache(app, mCachingLogic, info,
                            mUserSerial, false /*replace existing*/);
                }

                if (!mAppsToAdd.isEmpty()) {
                    scheduleNext();
                }
            }
        }

        public void scheduleNext() {
            mIconCache.mWorkerHandler.postAtTime(this, ICON_UPDATE_TOKEN,
                    SystemClock.uptimeMillis() + 1);
        }
    }
}
