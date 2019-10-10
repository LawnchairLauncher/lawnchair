/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.util.LruCache;

import com.android.systemui.shared.recents.model.Task.TaskKey;

import java.io.PrintWriter;

/**
 * A mapping of {@link TaskKey} to value, with additional LRU functionality where the least
 * recently referenced key/values will be evicted as more values than the given cache size are
 * inserted.
 *
 * In addition, this also allows the caller to invalidate cached values for keys that have since
 * changed.
 */
public class TaskKeyLruCache<V> extends TaskKeyCache<V> {

    public interface EvictionCallback {
        void onEntryEvicted(TaskKey key);
    }

    private final LruCache<Integer, V> mCache;
    private final EvictionCallback mEvictionCallback;

    public TaskKeyLruCache(int cacheSize) {
        this(cacheSize, null);
    }

    public TaskKeyLruCache(int cacheSize, EvictionCallback evictionCallback) {
        mEvictionCallback = evictionCallback;
        mCache = new LruCache<Integer, V>(cacheSize) {

            @Override
            protected void entryRemoved(boolean evicted, Integer taskId, V oldV, V newV) {
                if (mEvictionCallback != null) {
                    mEvictionCallback.onEntryEvicted(mKeys.get(taskId));
                }
                mKeys.remove(taskId);
            }
        };
    }

    /** Trims the cache to a specific size */
    public final void trimToSize(int cacheSize) {
        mCache.trimToSize(cacheSize);
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
        mCache.evictAll();
    }
}
