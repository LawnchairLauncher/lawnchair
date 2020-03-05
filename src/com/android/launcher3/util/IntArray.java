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
import java.util.StringTokenizer;

/**
 * Copy of the platform hidden implementation of android.util.IntArray.
 * Implements a growing array of int primitives.
 */
public class IntArray implements Cloneable {
    private static final int MIN_CAPACITY_INCREMENT = 12;

    private static final int[] EMPTY_INT = new int[0];

    /* package private */ int[] mValues;
    /* package private */ int mSize;

    private  IntArray(int[] array, int size) {
        mValues = array;
        mSize = size;
    }

    /**
     * Creates an empty IntArray with the default initial capacity.
     */
    public IntArray() {
        this(10);
    }

    /**
     * Creates an empty IntArray with the specified initial capacity.
     */
    public IntArray(int initialCapacity) {
        if (initialCapacity == 0) {
            mValues = EMPTY_INT;
        } else {
            mValues = new int[initialCapacity];
        }
        mSize = 0;
    }

    /**
     * Creates an IntArray wrapping the given primitive int array.
     */
    public static IntArray wrap(int... array) {
        return new IntArray(array, array.length);
    }

    /**
     * Appends the specified value to the end of this array.
     */
    public void add(int value) {
        add(mSize, value);
    }

    /**
     * Inserts a value at the specified position in this array. If the specified index is equal to
     * the length of the array, the value is added at the end.
     *
     * @throws IndexOutOfBoundsException when index &lt; 0 || index &gt; size()
     */
    public void add(int index, int value) {
        ensureCapacity(1);
        int rightSegment = mSize - index;
        mSize++;
        checkBounds(mSize, index);

        if (rightSegment != 0) {
            // Move by 1 all values from the right of 'index'
            System.arraycopy(mValues, index, mValues, index + 1, rightSegment);
        }

        mValues[index] = value;
    }

    /**
     * Adds the values in the specified array to this array.
     */
    public void addAll(IntArray values) {
        final int count = values.mSize;
        ensureCapacity(count);

        System.arraycopy(values.mValues, 0, mValues, mSize, count);
        mSize += count;
    }

    /**
     * Sets the array to be same as {@param other}
     */
    public void copyFrom(IntArray other) {
        clear();
        addAll(other);
    }

    /**
     * Ensures capacity to append at least <code>count</code> values.
     */
    private void ensureCapacity(int count) {
        final int currentSize = mSize;
        final int minCapacity = currentSize + count;
        if (minCapacity >= mValues.length) {
            final int targetCap = currentSize + (currentSize < (MIN_CAPACITY_INCREMENT / 2) ?
                    MIN_CAPACITY_INCREMENT : currentSize >> 1);
            final int newCapacity = targetCap > minCapacity ? targetCap : minCapacity;
            final int[] newValues = new int[newCapacity];
            System.arraycopy(mValues, 0, newValues, 0, currentSize);
            mValues = newValues;
        }
    }

    /**
     * Removes all values from this array.
     */
    public void clear() {
        mSize = 0;
    }

    @Override
    public IntArray clone() {
        return wrap(toArray());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof IntArray) {
            IntArray arr = (IntArray) obj;
            if (mSize == arr.mSize) {
                for (int i = 0; i < mSize; i++) {
                    if (arr.mValues[i] != mValues[i]) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the value at the specified position in this array.
     */
    public int get(int index) {
        checkBounds(mSize, index);
        return mValues[index];
    }

    /**
     * Sets the value at the specified position in this array.
     */
    public void set(int index, int value) {
        checkBounds(mSize, index);
        mValues[index] = value;
    }

    /**
     * Returns the index of the first occurrence of the specified value in this
     * array, or -1 if this array does not contain the value.
     */
    public int indexOf(int value) {
        final int n = mSize;
        for (int i = 0; i < n; i++) {
            if (mValues[i] == value) {
                return i;
            }
        }
        return -1;
    }

    public boolean contains(int value) {
        return indexOf(value) >= 0;
    }

    public boolean isEmpty() {
        return mSize == 0;
    }

    /**
     * Removes the value at the specified index from this array.
     */
    public void removeIndex(int index) {
        checkBounds(mSize, index);
        System.arraycopy(mValues, index + 1, mValues, index, mSize - index - 1);
        mSize--;
    }

    /**
     * Removes the values if it exists
     */
    public void removeValue(int value) {
        int index = indexOf(value);
        if (index >= 0) {
            removeIndex(index);
        }
    }

    /**
     * Removes the values if it exists
     */
    public void removeAllValues(IntArray values) {
        for (int i = 0; i < values.mSize; i++) {
            removeValue(values.mValues[i]);
        }
    }

    /**
     * Returns the number of values in this array.
     */
    public int size() {
        return mSize;
    }

    /**
     * Returns a new array with the contents of this IntArray.
     */
    public int[] toArray() {
        return mSize == 0 ? EMPTY_INT : Arrays.copyOf(mValues, mSize);
    }

    /**
     * Returns a comma separate list of all values.
     */
    public String toConcatString() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < mSize ; i++) {
            if (i > 0) {
                b.append(", ");
            }
            b.append(mValues[i]);
        }
        return b.toString();
    }

    public static IntArray fromConcatString(String concatString) {
        StringTokenizer tokenizer = new StringTokenizer(concatString, ",");
        int[] array = new int[tokenizer.countTokens()];
        int count = 0;
        while (tokenizer.hasMoreTokens()) {
            array[count] = Integer.parseInt(tokenizer.nextToken().trim());
            count++;
        }
        return new IntArray(array, array.length);
    }

    /**
     * Throws {@link ArrayIndexOutOfBoundsException} if the index is out of bounds.
     *
     * @param len length of the array. Must be non-negative
     * @param index the index to check
     * @throws ArrayIndexOutOfBoundsException if the {@code index} is out of bounds of the array
     */
    private static void checkBounds(int len, int index) {
        if (index < 0 || len <= index) {
            throw new ArrayIndexOutOfBoundsException("length=" + len + "; index=" + index);
        }
    }
}