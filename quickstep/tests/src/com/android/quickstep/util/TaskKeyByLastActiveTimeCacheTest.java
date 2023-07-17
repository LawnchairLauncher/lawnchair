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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

import android.content.ComponentName;
import android.content.Intent;

import androidx.test.filters.SmallTest;

import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;

import org.junit.Test;

@SmallTest
public class TaskKeyByLastActiveTimeCacheTest {
    @Test
    public void add() {
        TaskKeyByLastActiveTimeCache<ThumbnailData> cache = new TaskKeyByLastActiveTimeCache<>(3);
        Task.TaskKey key1 = new Task.TaskKey(1, 0, new Intent(),
                new ComponentName("", ""), 0, 1);
        ThumbnailData data1 = new ThumbnailData();
        cache.put(key1, data1);

        Task.TaskKey key2 = new Task.TaskKey(2, 0, new Intent(),
                new ComponentName("", ""), 0, 2);
        ThumbnailData data2 = new ThumbnailData();
        cache.put(key2, data2);

        assertEquals(2, cache.getSize());
        assertEquals(data1, cache.getAndInvalidateIfModified(key1));
        assertEquals(data2, cache.getAndInvalidateIfModified(key2));

        assertEquals(2, cache.getQueue().size());
        assertEquals(key1, cache.getQueue().poll());
        assertEquals(key2, cache.getQueue().poll());
    }

    @Test
    public void addSameTasksWithSameLastActiveTimeTwice() {
        // Add 2 tasks with same id and last active time, it should only have 1 entry in cache
        TaskKeyByLastActiveTimeCache<ThumbnailData> cache = new TaskKeyByLastActiveTimeCache<>(3);
        Task.TaskKey key1 = new Task.TaskKey(1, 0, new Intent(),
                new ComponentName("", ""), 0, 1000);
        ThumbnailData data1 = new ThumbnailData();
        cache.put(key1, data1);

        Task.TaskKey key2 = new Task.TaskKey(1, 0, new Intent(),
                new ComponentName("", ""), 0, 1000);
        ThumbnailData data2 = new ThumbnailData();
        cache.put(key2, data2);

        assertEquals(1, cache.getSize());
        assertEquals(data2, cache.getAndInvalidateIfModified(key2));

        assertEquals(1, cache.getQueue().size());
        assertEquals(key2, cache.getQueue().poll());
    }

    @Test
    public void addSameTasksWithDifferentLastActiveTime() {
        // Add 2 tasks with same id and different last active time, it should only have the
        // higher last active time entry
        TaskKeyByLastActiveTimeCache<ThumbnailData> cache = new TaskKeyByLastActiveTimeCache<>(3);
        Task.TaskKey key1 = new Task.TaskKey(1, 0, new Intent(),
                new ComponentName("", ""), 0, 1000);
        ThumbnailData data1 = new ThumbnailData();
        cache.put(key1, data1);

        Task.TaskKey key2 = new Task.TaskKey(1, 0, new Intent(),
                new ComponentName("", ""), 0, 2000);
        ThumbnailData data2 = new ThumbnailData();
        cache.put(key2, data2);

        assertEquals(1, cache.getSize());
        assertEquals(data2, cache.getAndInvalidateIfModified(key2));

        assertEquals(1, cache.getQueue().size());
        Task.TaskKey queueKey = cache.getQueue().poll();
        assertEquals(key2, queueKey);
        // TaskKey's equal method does not check last active time, so we check here
        assertEquals(2000, queueKey.lastActiveTime);
    }

    @Test
    public void remove() {
        TaskKeyByLastActiveTimeCache<ThumbnailData> cache = new TaskKeyByLastActiveTimeCache<>(3);
        Task.TaskKey key1 = new Task.TaskKey(1, 0, new Intent(),
                new ComponentName("", ""), 0, 0);
        cache.put(key1, new ThumbnailData());

        cache.remove(key1);

        assertEquals(0, cache.getSize());
        assertEquals(0, cache.getQueue().size());
    }

    @Test
    public void removeByStubKey() {
        TaskKeyByLastActiveTimeCache<ThumbnailData> cache = new TaskKeyByLastActiveTimeCache<>(3);
        Task.TaskKey key1 = new Task.TaskKey(1, 1, new Intent(),
                new ComponentName("", ""), 1, 100);
        cache.put(key1, new ThumbnailData());

        Task.TaskKey stubKey = new Task.TaskKey(1, 0, new Intent(),
                new ComponentName("", ""), 0, 0);
        cache.remove(stubKey);

        assertEquals(0, cache.getSize());
        assertEquals(0, cache.getQueue().size());
    }

    @Test
    public void evictAll() {
        TaskKeyByLastActiveTimeCache<ThumbnailData> cache = new TaskKeyByLastActiveTimeCache<>(3);
        Task.TaskKey key1 = new Task.TaskKey(1, 0, new Intent(),
                new ComponentName("", ""), 0, 0);
        cache.put(key1, new ThumbnailData());
        Task.TaskKey key2 = new Task.TaskKey(2, 0, new Intent(),
                new ComponentName("", ""), 0, 0);
        cache.put(key2, new ThumbnailData());

        cache.evictAll();

        assertEquals(0, cache.getSize());
        assertEquals(0, cache.getQueue().size());
    }

    @Test
    public void removeAllByPredicate() {
        TaskKeyByLastActiveTimeCache<ThumbnailData> cache = new TaskKeyByLastActiveTimeCache<>(3);
        // Add user 1's tasks
        Task.TaskKey user1Key1 = new Task.TaskKey(1, 0, new Intent(),
                new ComponentName("", ""), 1, 0);
        cache.put(user1Key1, new ThumbnailData());
        Task.TaskKey user1Key2 = new Task.TaskKey(2, 0, new Intent(),
                new ComponentName("", ""), 1, 0);
        cache.put(user1Key2, new ThumbnailData());
        // Add user 2's task
        Task.TaskKey user2Key = new Task.TaskKey(3, 0, new Intent(),
                new ComponentName("", ""), 2, 0);
        ThumbnailData user2Data = new ThumbnailData();
        cache.put(user2Key, user2Data);

        cache.removeAll(key -> key.userId == 1);

        // Only user 2's task remains
        assertEquals(1, cache.getSize());
        assertEquals(user2Data, cache.getAndInvalidateIfModified(user2Key));

        assertEquals(1, cache.getQueue().size());
        assertEquals(user2Key, cache.getQueue().poll());
    }

    @Test
    public void getAndInvalidateIfModified() {
        TaskKeyByLastActiveTimeCache<ThumbnailData> cache = new TaskKeyByLastActiveTimeCache<>(3);
        // Add user 1's tasks
        Task.TaskKey key1 = new Task.TaskKey(1, 0, new Intent(),
                new ComponentName("", ""), 1, 0);
        ThumbnailData data1 = new ThumbnailData();
        cache.put(key1, data1);

        // Get result with task key of same last active time
        Task.TaskKey keyWithSameActiveTime = new Task.TaskKey(1, 0, new Intent(),
                new ComponentName("", ""), 1, 0);
        ThumbnailData result1 = cache.getAndInvalidateIfModified(keyWithSameActiveTime);
        assertEquals(data1, result1);
        assertEquals(1, cache.getQueue().size());

        // Invalidate result with task key of new last active time
        Task.TaskKey keyWithNewActiveTime = new Task.TaskKey(1, 0, new Intent(),
                new ComponentName("", ""), 1, 1);
        ThumbnailData result2 = cache.getAndInvalidateIfModified(keyWithNewActiveTime);
        // No entry is retrieved because the key has higher last active time
        assertNull(result2);
        assertEquals(0, cache.getSize());
        assertEquals(0, cache.getQueue().size());
    }

    @Test
    public void removeByLastActiveTimeWhenOverMaxSize() {
        TaskKeyByLastActiveTimeCache<ThumbnailData> cache = new TaskKeyByLastActiveTimeCache<>(2);
        Task.TaskKey key1 = new Task.TaskKey(1, 0, new Intent(),
                new ComponentName("", ""), 0, 200);
        ThumbnailData task1 = new ThumbnailData();
        cache.put(key1, task1);
        Task.TaskKey key2 = new Task.TaskKey(2, 0, new Intent(),
                new ComponentName("", ""), 0, 100);
        ThumbnailData task2 = new ThumbnailData();
        cache.put(key2, task2);

        // Add the 3rd entry which will exceed the max cache size
        Task.TaskKey key3 = new Task.TaskKey(3, 0, new Intent(),
                new ComponentName("", ""), 0, 300);
        ThumbnailData task3 = new ThumbnailData();
        cache.put(key3, task3);

        // Assert map size and check the remaining entries have higher active time
        assertEquals(2, cache.getSize());
        assertEquals(task1, cache.getAndInvalidateIfModified(key1));
        assertEquals(task3, cache.getAndInvalidateIfModified(key3));
        assertNull(cache.getAndInvalidateIfModified(key2));

        // Assert queue size and check the remaining entries have higher active time
        assertEquals(2, cache.getQueue().size());
        Task.TaskKey queueKey1 = cache.getQueue().poll();
        assertEquals(key1, queueKey1);
        assertEquals(200, queueKey1.lastActiveTime);
        Task.TaskKey queueKey2 = cache.getQueue().poll();
        assertEquals(key3, queueKey2);
        assertEquals(300, queueKey2.lastActiveTime);
    }

    @Test
    public void updateIfAlreadyInCache() {
        TaskKeyByLastActiveTimeCache<ThumbnailData> cache = new TaskKeyByLastActiveTimeCache<>(2);
        Task.TaskKey key1 = new Task.TaskKey(1, 0, new Intent(),
                new ComponentName("", ""), 0, 200);
        cache.put(key1, new ThumbnailData());

        // Update original data to new data
        ThumbnailData newData = new ThumbnailData();
        cache.updateIfAlreadyInCache(key1.id, newData);

        // Data is updated to newData successfully
        ThumbnailData result = cache.getAndInvalidateIfModified(key1);
        assertEquals(newData, result);
    }

    @Test
    public void updateCacheSizeAndInvalidateExcess() {
        // Last active time are not in-sync with insertion order to simulate the real async case
        TaskKeyByLastActiveTimeCache<ThumbnailData> cache = new TaskKeyByLastActiveTimeCache<>(4);
        Task.TaskKey key1 = new Task.TaskKey(1, 0, new Intent(),
                new ComponentName("", ""), 0, 200);
        cache.put(key1, new ThumbnailData());

        Task.TaskKey key2 = new Task.TaskKey(2, 0, new Intent(),
                new ComponentName("", ""), 0, 100);
        cache.put(key2, new ThumbnailData());

        Task.TaskKey key3 = new Task.TaskKey(3, 0, new Intent(),
                new ComponentName("", ""), 0, 400);
        cache.put(key3, new ThumbnailData());

        Task.TaskKey key4 = new Task.TaskKey(4, 0, new Intent(),
                new ComponentName("", ""), 0, 300);
        cache.put(key4, new ThumbnailData());

        // Check that it has 4 entries before cache size changes
        assertEquals(4, cache.getSize());
        assertEquals(4, cache.getQueue().size());

        // Update size to 2
        cache.updateCacheSizeAndRemoveExcess(2);

        // Number of entries becomes 2, only key3 and key4 remain
        assertEquals(2, cache.getSize());
        assertEquals(2, cache.getQueue().size());
        assertNotNull(cache.getAndInvalidateIfModified(key3));
        assertNotNull(cache.getAndInvalidateIfModified(key4));
    }
}
