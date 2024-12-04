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

import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.systemui.shared.recents.model.Task;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * A class to cache task id and its corresponding object (e.g. thumbnail)
 *
 * <p>Maximum size of the cache should be provided when creating this class. When the number of
 * entries is larger than its max size, it would remove the entry with the smallest last active time
 * @param <V> Type of object stored in the cache
 */
public class TaskKeyByLastActiveTimeCache<V> implements TaskKeyCache<V> {
    private static final String TAG = "TaskKeyByLastActiveTimeCache";
    private final AtomicInteger mMaxSize;
    private final Map<Integer, Entry<V>> mMap;
    // To sort task id by last active time
    private final PriorityQueue<Task.TaskKey> mQueue;

    public TaskKeyByLastActiveTimeCache(int maxSize) {
        mMap = new HashMap(0);
        mQueue = new PriorityQueue<>(Comparator.comparingLong(t -> t.lastActiveTime));
        mMaxSize = new AtomicInteger(maxSize);
    }

    /**
     * Removes all entries from the cache
     */
    @Override
    public synchronized void evictAll() {
        mMap.clear();
        mQueue.clear();
    }


    /**
     * Removes a particular entry from the cache
     */
    @Override
    public synchronized void remove(Task.TaskKey key) {
        if (key == null) {
            return;
        }

        Entry<V> entry = mMap.remove(key.id);
        if (entry != null) {
            // Use real key in map entry to handle use case of using stub key for removal
            mQueue.remove(entry.mKey);
        }
    }

    /**
     * Removes all entries matching keyCheck
     */
    @Override
    public synchronized void removeAll(Predicate<Task.TaskKey> keyCheck) {
        Iterator<Task.TaskKey> iterator = mQueue.iterator();
        while (iterator.hasNext()) {
            Task.TaskKey key = iterator.next();
            if (keyCheck.test(key)) {
                mMap.remove(key.id);
                iterator.remove();
            }
        }
    }

    /**
     * Gets the entry if it is still valid
     */
    @Override
    public synchronized V getAndInvalidateIfModified(Task.TaskKey key) {
        Entry<V> entry = mMap.get(key.id);
        if (entry != null && entry.mKey.windowingMode == key.windowingMode
                && entry.mKey.lastActiveTime == key.lastActiveTime) {
            return entry.mValue;
        } else {
            remove(key);
            return null;
        }
    }

    /**
     * Adds an entry to the cache, optionally evicting the last accessed entry excluding the newly
     * added entry
     */
    @Override
    public final synchronized void put(Task.TaskKey key, V value) {
        if (key != null && value != null) {
            Entry<V> entry = mMap.get(key.id);
            // If the same key already exist, remove item for existing key
            if (entry != null) {
                mQueue.remove(entry.mKey);
            }

            removeExcessIfNeeded(mMaxSize.get() - 1);
            mMap.put(key.id, new Entry<>(key, value));
            mQueue.add(key);
        } else {
            Log.e(TAG, "Unexpected null key or value: " + key + ", " + value);
        }
    }

    /**
     * Updates the cache entry if it is already present in the cache
     */
    @Override
    public synchronized void updateIfAlreadyInCache(int taskId, V data) {
        Entry<V> entry = mMap.get(taskId);
        if (entry != null) {
            entry.mValue = data;
        }
    }

    /**
     * Updates cache size and remove excess if the number of existing entries is larger than new
     * cache size
     */
    @Override
    public synchronized void updateCacheSizeAndRemoveExcess(int cacheSize) {
        mMaxSize.compareAndSet(mMaxSize.get(), cacheSize);
        removeExcessIfNeeded(mMaxSize.get());
    }

    private synchronized void removeExcessIfNeeded(int maxSize) {
        while (mQueue.size() > maxSize && !mQueue.isEmpty()) {
            Task.TaskKey key = mQueue.poll();
            mMap.remove(key.id);
        }
    }

    /**
     * Get maximum size of the cache
     */
    @Override
    public int getMaxSize() {
        return mMaxSize.get();
    }

    /**
     * Get current size of the cache
     */
    @Override
    public int getSize() {
        return mMap.size();
    }

    @VisibleForTesting
    PriorityQueue<Task.TaskKey> getQueue() {
        return mQueue;
    }
}
