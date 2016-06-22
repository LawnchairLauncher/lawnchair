/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.shortcuts;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherApps.ShortcutQuery;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserHandleCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Performs operations related to deep shortcuts, such as querying for them, pinning them, etc.
 */
@TargetApi(Build.VERSION_CODES.N)
public class DeepShortcutManager {
    private static final int FLAG_GET_ALL = ShortcutQuery.FLAG_GET_DYNAMIC
            | ShortcutQuery.FLAG_GET_PINNED | ShortcutQuery.FLAG_GET_MANIFEST;

    private final LauncherAppsCompat mLauncherApps;

    public DeepShortcutManager(Context context, ShortcutCache shortcutCache) {
        mLauncherApps = LauncherAppsCompat.getInstance(context);
    }

    public void onShortcutsChanged(List<ShortcutInfoCompat> shortcuts) {
        // mShortcutCache.removeShortcuts(shortcuts);
    }

    /**
     * Queries for the shortcuts with the package name and provided ids.
     *
     * This method is intended to get the full details for shortcuts when they are added or updated,
     * because we only get "key" fields in onShortcutsChanged().
     */
    public List<ShortcutInfoCompat> queryForFullDetails(String packageName,
            List<String> shortcutIds, UserHandleCompat user) {
        return query(FLAG_GET_ALL, packageName, null, shortcutIds, user);
    }

    /**
     * Gets all the shortcuts associated with the given package and user.
     */
    public List<ShortcutInfoCompat> queryForAllAppShortcuts(ComponentName activity,
            List<String> ids, UserHandleCompat user) {
        return query(FLAG_GET_ALL, activity.getPackageName(), activity, ids, user);
    }

    /**
     * Removes the given shortcut from the current list of pinned shortcuts.
     * (Runs on background thread)
     */
    public void unpinShortcut(final ShortcutKey key) {
        String packageName = key.componentName.getPackageName();
        String id = key.id;
        UserHandleCompat user = key.user;
        List<String> pinnedIds = extractIds(queryForPinnedShortcuts(packageName, user));
        pinnedIds.remove(id);
        mLauncherApps.pinShortcuts(packageName, pinnedIds, user);
    }

    /**
     * Adds the given shortcut to the current list of pinned shortcuts.
     * (Runs on background thread)
     */
    public void pinShortcut(final ShortcutKey key) {
        String packageName = key.componentName.getPackageName();
        String id = key.id;
        UserHandleCompat user = key.user;
        List<String> pinnedIds = extractIds(queryForPinnedShortcuts(packageName, user));
        pinnedIds.add(id);
        mLauncherApps.pinShortcuts(packageName, pinnedIds, user);
    }

    /**
     * Returns the id's of pinned shortcuts associated with the given package and user.
     *
     * If packageName is null, returns all pinned shortcuts regardless of package.
     */
    public List<ShortcutInfoCompat> queryForPinnedShortcuts(String packageName,
            UserHandleCompat user) {
        return query(ShortcutQuery.FLAG_GET_PINNED, packageName, null, null, user);
    }

    public List<ShortcutInfoCompat> queryForAllShortcuts(UserHandleCompat user) {
        return query(FLAG_GET_ALL, null, null, null, user);
    }

    private List<String> extractIds(List<ShortcutInfoCompat> shortcuts) {
        List<String> shortcutIds = new ArrayList<>(shortcuts.size());
        for (ShortcutInfoCompat shortcut : shortcuts) {
            shortcutIds.add(shortcut.getId());
        }
        return shortcutIds;
    }

    /**
     * Query the system server for all the shortcuts matching the given parameters.
     * If packageName == null, we query for all shortcuts with the passed flags, regardless of app.
     *
     * TODO: Use the cache to optimize this so we don't make an RPC every time.
     */
    private List<ShortcutInfoCompat> query(int flags, String packageName,
            ComponentName activity, List<String> shortcutIds, UserHandleCompat user) {
        ShortcutQuery q = new ShortcutQuery();
        q.setQueryFlags(flags);
        if (packageName != null) {
            q.setPackage(packageName);
            q.setActivity(activity);
            q.setShortcutIds(shortcutIds);
        }
        return mLauncherApps.getShortcuts(q, user);
    }
}
