/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.util.LongSparseArray;

import java.util.Iterator;

/**
 * Extension of {@link LongSparseArray} with some utility methods.
 */
public class LongArrayMap<E> extends LongSparseArray<E> implements Iterable<E> {

    public boolean containsKey(long key) {
        return indexOfKey(key) >= 0;
    }

    public boolean isEmpty() {
        return size() <= 0;
    }

    @Override
    public LongArrayMap<E> clone() {
        return (LongArrayMap<E>) super.clone();
    }

    @Override
    public Iterator<E> iterator() {
        return new ValueIterator();
    }

    @Thunk class ValueIterator implements Iterator<E> {

        private int mNextIndex = 0;

        @Override
        public boolean hasNext() {
            return mNextIndex < size();
        }

        @Override
        public E next() {
            return valueAt(mNextIndex ++);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
