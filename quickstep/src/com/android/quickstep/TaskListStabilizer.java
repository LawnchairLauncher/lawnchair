/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep;

import static com.android.launcher3.config.FeatureFlags.ENABLE_TASK_STABILIZER;

import android.os.SystemClock;
import android.util.SparseArray;

import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.systemui.shared.recents.model.Task;

import java.util.ArrayList;

public class TaskListStabilizer {

    private static final long TASK_CACHE_TIMEOUT_MS = 5000;

    private final SparseArray<Task> mTempMap = new SparseArray<>();
    private final IntArray mTempArray = new IntArray();
    private final IntSet mTempSet = new IntSet();

    private final IntArray mLastStableOrder = new IntArray();
    private final IntSet mLastSet = new IntSet();
    private final IntArray mLastUnstableOrder = new IntArray();

    private long mLastReorderTime;

    public ArrayList<Task> reorder(ArrayList<Task> tasks) {
        if (!ENABLE_TASK_STABILIZER.get()) {
            return tasks;
        }

        // Create task id array
        int count = tasks.size();
        mTempArray.clear();
        mTempSet.clear();
        mTempMap.clear();

        for (int i = 0; i < count; i++) {
            Task t = tasks.get(i);
            mTempMap.put(t.key.id, t);

            mTempSet.add(t.key.id);
            mTempArray.add(t.key.id);
        }

        if (mLastSet.equals(mTempSet) && isStabilizationQuickEnough()) {
            if (mLastStableOrder.equals(mTempArray)) {
                // Everything is same
                return tasks;
            }

            if (!mLastUnstableOrder.equals(mTempArray)) {
                // Fast reordering, record the current time.
                mLastUnstableOrder.copyFrom(mTempArray);
                mLastReorderTime = SystemClock.uptimeMillis();
            }

            // Reorder the tasks based on the last stable order.
            ArrayList<Task> sorted = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                sorted.add(mTempMap.get(mLastStableOrder.get(i)));
            }
            return sorted;
        }

        // Cache the data
        mLastStableOrder.copyFrom(mTempArray);
        mLastUnstableOrder.copyFrom(mTempArray);
        mLastSet.copyFrom(mTempSet);

        mLastReorderTime = SystemClock.uptimeMillis();

        return tasks;
    }

    private boolean isStabilizationQuickEnough() {
        return (SystemClock.uptimeMillis() - mLastReorderTime) < TASK_CACHE_TIMEOUT_MS;
    }
}
