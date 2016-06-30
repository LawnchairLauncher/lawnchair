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
import android.os.Build;
import android.os.UserHandle;
import android.util.LruCache;

import java.util.HashMap;
import java.util.List;

/**
 * Loads {@link ShortcutInfoCompat}s on demand (e.g. when launcher
 * loads for pinned shortcuts and on long-press for dynamic shortcuts), and caches them
 * for handful of apps in an LruCache while launcher lives.
 */
@TargetApi(Build.VERSION_CODES.N)
public class ShortcutCache {
    private static final String TAG = "ShortcutCache";
    private static final boolean LOGD = false;

    private static final int CACHE_SIZE = 30; // Max number shortcuts we cache.

    private LruCache<ShortcutKey, ShortcutInfoCompat> mCachedShortcuts;
    // We always keep pinned shortcuts in the cache.
    private HashMap<ShortcutKey, ShortcutInfoCompat> mPinnedShortcuts;

    public ShortcutCache() {
        mCachedShortcuts = new LruCache<>(CACHE_SIZE);
        mPinnedShortcuts = new HashMap<>();
    }

    /**
     * Removes shortcuts from the cache when shortcuts change for a given package.
     *
     * Returns a map of ids to their evicted shortcuts.
     *
     * @see android.content.pm.LauncherApps.Callback#onShortcutsChanged(String, List, UserHandle).
     */
    public void removeShortcuts(List<ShortcutInfoCompat> shortcuts) {
        for (ShortcutInfoCompat shortcut : shortcuts) {
            ShortcutKey key = ShortcutKey.fromInfo(shortcut);
            mCachedShortcuts.remove(key);
            mPinnedShortcuts.remove(key);
        }
    }

    public ShortcutInfoCompat get(ShortcutKey key) {
        if (mPinnedShortcuts.containsKey(key)) {
            return mPinnedShortcuts.get(key);
        }
        return mCachedShortcuts.get(key);
    }

    public void put(ShortcutKey key, ShortcutInfoCompat shortcut) {
        if (shortcut.isPinned()) {
            mPinnedShortcuts.put(key, shortcut);
        } else {
            mCachedShortcuts.put(key, shortcut);
        }
    }
}
