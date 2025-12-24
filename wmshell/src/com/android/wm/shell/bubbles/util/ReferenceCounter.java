/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.bubbles.util;

import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.Map;
import java.util.function.Consumer;

/**
 * A simple utility class to track reference count on items. Primarily used to track bubbles used
 * in animations.
 *
 * @param <T> the type of tracked items.
 */
public class ReferenceCounter<T> {
    @VisibleForTesting
    final Map<T, Integer> mReferences = new ArrayMap<>();

    /** Increments reference count of the {@code items}. */
    @SafeVarargs
    public final void increment(T... items) {
        for (T item : items) {
            if (item == null) {
                continue;
            }
            if (mReferences.containsKey(item)) {
                mReferences.put(item, mReferences.get(item) + 1);
            } else {
                mReferences.put(item, 1);
            }
        }
    }

    /**
     * Decrements reference count of the {@code items}. Note that this does NOT remove any items
     * even if they have reached zero reference.
     *
     * @throws IllegalArgumentException if the item is not tracked or already has zero reference
     *                                  count.
     */
    @SafeVarargs
    public final void decrement(T... items) {
        for (T item : items) {
            if (item == null) {
                continue;
            }
            if (!mReferences.containsKey(item)) {
                throw new IllegalArgumentException("Decrement non-existing item=" + item);
            } else if (mReferences.get(item) == 0) {
                throw new IllegalArgumentException("Decrement zero reference item=" + item);
            } else {
                mReferences.put(item, mReferences.get(item) - 1);
            }
        }
    }

    /** Returns whether any tracked item has a non-zero reference count. */
    public boolean hasReferences() {
        return mReferences.values().stream().anyMatch(count -> count != 0);
    }

    /** Executes the {@code callback} on each item, including items with zero reference count. */
    public void forEach(@NonNull Consumer<T> callback) {
        for (T item : mReferences.keySet()) {
            callback.accept(item);
        }
    }

    /** Clears all the reference counts and removes tracked items. */
    public void clear() {
        mReferences.clear();
    }

    /**
     * Returns whether the item is tracked. An item is tracked even if its reference count is
     * zero.
     */
    @VisibleForTesting
    public boolean isTracked(@NonNull T item) {
        return mReferences.containsKey(item);
    }
}
