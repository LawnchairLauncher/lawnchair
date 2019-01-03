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
package com.android.launcher3.util;

import java.util.Arrays;

/**
 * A wrapper over IntArray implementing a growing set of int primitives.
 */
public class IntSet {

    final IntArray mArray = new IntArray();

    /**
     * Appends the specified value to the set if it does not exist.
     */
    public void add(int value) {
        int index = Arrays.binarySearch(mArray.mValues, 0, mArray.mSize, value);
        if (index < 0) {
            mArray.add(-index - 1, value);
        }
    }

    public boolean contains(int value) {
        return Arrays.binarySearch(mArray.mValues, 0, mArray.mSize, value) >= 0;
    }

    public boolean isEmpty() {
        return mArray.isEmpty();
    }

    /**
     * Returns the number of values in this set.
     */
    public int size() {
        return mArray.size();
    }

    public void clear() {
        mArray.clear();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        return (obj instanceof IntSet) && ((IntSet) obj).mArray.equals(mArray);
    }

    public IntArray getArray() {
        return mArray;
    }

    /**
     * Sets this set to be same as {@param other}
     */
    public void copyFrom(IntSet other) {
        mArray.copyFrom(other.mArray);
    }

    public static IntSet wrap(IntArray array) {
        IntSet set = new IntSet();
        set.mArray.addAll(array);
        Arrays.sort(set.mArray.mValues, 0, set.mArray.mSize);
        return set;
    }
}
