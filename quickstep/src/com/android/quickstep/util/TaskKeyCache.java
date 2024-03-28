/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.quickstep.util;

import com.android.systemui.shared.recents.model.Task;

import java.util.function.Predicate;

/**
 * An interface for caching task id and its corresponding object (e.g. thumbnail, task icon)
 *
 * @param <V> Type of object stored in the cache
 */
public interface TaskKeyCache<V> {

    /**
     * Removes all entries from the cache.
     */
    void evictAll();

    /**
     * Removes a particular entry from the cache.
     */
    void remove(Task.TaskKey key);

    /**
     * Removes all entries matching keyCheck.
     */
    void removeAll(Predicate<Task.TaskKey> keyCheck);

    /**
     * Gets the entry if it is still valid.
     */
    V getAndInvalidateIfModified(Task.TaskKey key);

    /**
     * Adds an entry to the cache, optionally evicting the last accessed entry.
     */
    void put(Task.TaskKey key, V value);

    /**
     * Updates the cache entry if it is already present in the cache.
     */
    void updateIfAlreadyInCache(int taskId, V data);

    /**
     * Updates cache size and remove excess if the number of existing entries is larger than new
     * cache size.
     */
    default void updateCacheSizeAndRemoveExcess(int cacheSize) { }

    /**
     * Gets maximum size of the cache.
     */
    int getMaxSize();

    /**
     * Gets current size of the cache.
     */
    int getSize();

    class Entry<V> {

        final Task.TaskKey mKey;
        V mValue;

        Entry(Task.TaskKey key, V value) {
            mKey = key;
            mValue = value;
        }

        @Override
        public int hashCode() {
            return mKey.id;
        }
    }
}
