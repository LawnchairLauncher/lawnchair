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
 * limitations under the License
 */

package com.android.systemui.shared.recents.model;

import android.util.ArrayMap;

import com.android.systemui.shared.recents.model.Task.TaskKey;

import java.io.PrintWriter;

/**
 * Like {@link TaskKeyLruCache}, but without LRU functionality.
 */
public class TaskKeyStrongCache<V> extends TaskKeyCache<V> {

    private static final String TAG = "TaskKeyCache";

    private final ArrayMap<Integer, V> mCache = new ArrayMap<>();

    final void copyEntries(TaskKeyStrongCache<V> other) {
        for (int i = other.mKeys.size() - 1; i >= 0; i--) {
            TaskKey key = other.mKeys.valueAt(i);
            put(key, other.mCache.get(key.id));
        }
    }

    public void dump(String prefix, PrintWriter writer) {
        String innerPrefix = prefix + "  ";
        writer.print(prefix); writer.print(TAG);
        writer.print(" numEntries="); writer.print(mKeys.size());
        writer.println();
        int keyCount = mKeys.size();
        for (int i = 0; i < keyCount; i++) {
            writer.print(innerPrefix); writer.println(mKeys.get(mKeys.keyAt(i)));
        }
    }

    @Override
    protected V getCacheEntry(int id) {
        return mCache.get(id);
    }

    @Override
    protected void putCacheEntry(int id, V value) {
        mCache.put(id, value);
    }

    @Override
    protected void removeCacheEntry(int id) {
        mCache.remove(id);
    }

    @Override
    protected void evictAllCache() {
        mCache.clear();
    }
}
