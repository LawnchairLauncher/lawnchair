/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.common.buffer

import kotlin.math.max

/**
 * A simple ring buffer of recycled items
 *
 * Use [advance] to add items to the buffer.
 *
 * As the buffer is used, it will grow, allocating new instances of T using [factory] until it
 * reaches [maxSize]. After this point, no new instances will be created. Instead, calls to
 * [advance] will recycle the "oldest" instance from the start of the buffer, placing it at the end.
 *
 * The items in the buffer are "recycled" in that they are reused, but it is up to the caller of
 * [advance] to properly reset any data that was previously stored on those items.
 *
 * @param maxSize The maximum size the buffer can grow to before it begins functioning as a ring.
 * @param factory A function that creates a fresh instance of T. Used by the buffer while it's
 *   growing to [maxSize].
 */
class RingBuffer<T>(private val maxSize: Int, private val factory: () -> T) : Iterable<T> {

    private val buffer = MutableList<T?>(maxSize) { null }

    /**
     * An abstract representation that points to the "end" of the buffer, i.e. one beyond the
     * location of the last item. Increments every time [advance] is called and is never wrapped.
     *
     * Use [indexOf] to calculate the associated index into the backing array. Before the buffer has
     * been completely filled, this will point to the next empty slot to fill; afterwards it will
     * point to the next item that should be recycled (which, because the buffer is a ring, is the
     * "start" of the buffer).
     *
     * This value is unlikely to overflow. Assuming [advance] is called at rate of 100 calls/ms,
     * omega will overflow after a little under three million years of continuous operation.
     */
    private var omega: Long = 0

    /**
     * The number of items currently stored in the buffer. Calls to [advance] will cause this value
     * to increase by one until it reaches [maxSize].
     */
    val size: Int
        get() = if (omega < maxSize) omega.toInt() else maxSize

    /**
     * Adds an item to the end of the buffer. The caller should reset the returned item's contents
     * and then fill it with appropriate data.
     *
     * If the buffer is not yet full, uses [factory] to create a new item. Otherwise, it recycles
     * the oldest item from the front of the buffer and moves it to the end.
     *
     * Importantly, recycled items are returned as-is, without being reset. They will retain any
     * data that was previously stored on them. Callers must make sure to clear any historical data,
     * if necessary.
     */
    fun advance(): T {
        val index = indexOf(omega)
        omega += 1
        val entry = buffer[index] ?: factory().also { buffer[index] = it }
        return entry
    }

    /**
     * Returns the value stored at [index], which can range from 0 (the "start", or oldest element
     * of the buffer) to [size] - 1 (the "end", or newest element of the buffer).
     */
    operator fun get(index: Int): T {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("Index $index is out of bounds")
        }

        // If omega is larger than the maxSize, then the buffer is full, and omega is equivalent
        // to the "start" of the buffer. If omega is smaller than the maxSize, then the buffer is
        // not yet full and our start should be 0. However, in modspace, maxSize and 0 are
        // equivalent, so we can get away with using it as the start value instead.
        val start = max(omega, maxSize.toLong())

        return buffer[indexOf(start + index)]!!
    }

    override fun iterator(): Iterator<T> {
        return object : Iterator<T> {
            private var position: Int = 0

            override fun next(): T {
                if (position >= size) {
                    throw NoSuchElementException()
                }
                return get(position).also { position += 1 }
            }

            override fun hasNext(): Boolean {
                return position < size
            }
        }
    }

    private fun indexOf(position: Long): Int {
        return (position % maxSize).toInt()
    }
}
