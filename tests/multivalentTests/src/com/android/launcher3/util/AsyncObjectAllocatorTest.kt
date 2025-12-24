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

package com.android.launcher3.util

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.AsyncObjectAllocator.JobDescription
import com.android.launcher3.util.AsyncObjectAllocator.allocationExecutor
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AsyncObjectAllocatorTest {

    @Test
    fun test_items_allocated_on_non_looper_thread() {

        var allocationCount = 0

        UI_HELPER_EXECUTOR.executeSync {
            AsyncObjectAllocator.allocate(
                count = 5,
                factory = {
                    assertNull(Looper.myLooper())
                    Object()
                },
                callbackExecutor = UI_HELPER_EXECUTOR,
                callback = {
                    assertEquals(UI_HELPER_EXECUTOR.looper, Looper.myLooper())
                    allocationCount++
                },
            )
        }

        // Wait for job completion
        allocationExecutor.executeSync {}
        UI_HELPER_EXECUTOR.executeSync {}
        assertEquals(5, allocationCount)
    }

    @Test
    fun test_allocation_ends_on_null() {
        val allocator = Allocator(2, 2, null, 2, 2, 2)
        val result = mutableListOf<Int>()

        val job = allocator.scheduleAllocation(5, result)
        allocationExecutor.executeSync {}
        assertEquals(2, result.size)
        assertTrue((job as JobDescription<*>).cancelled)
    }

    @Test
    fun test_two_allocators_staggered() {
        val result = mutableListOf<Int>()
        val allocator =
            Allocator(CountDownLatch(1), 1, 2, 3, 4, 5).apply { scheduleAllocation(5, result) }
        Allocator(11, 12, 13, 14, 15, 16).scheduleAllocation(5, result)

        allocator.unblockNext()
        allocationExecutor.executeSync {}
        assertThat(result).containsExactly(1, 11, 2, 12, 3, 13, 4, 14, 5, 15)
    }

    @Test
    fun test_allocator_resumes_after_failure() {
        val result = mutableListOf<Int>()
        val allocator =
            Allocator(CountDownLatch(1), 1, 2, null, 3, 4, 5).apply {
                scheduleAllocation(5, result)
            }
        Allocator(11, 12, 13, 14, 15, 16).scheduleAllocation(5, result)

        allocator.unblockNext()
        allocationExecutor.executeSync {}
        assertThat(result).containsExactly(1, 11, 2, 12, 13, 14, 15)
    }

    @Test
    fun test_three_allocators_staggered() {
        val result = mutableListOf<Int>()
        val allocator =
            Allocator(CountDownLatch(1), 1, 2, 3, 4, 5).apply { scheduleAllocation(5, result) }
        Allocator(11, 12, 13, 14, 15, 16).scheduleAllocation(5, result)
        Allocator(21, 22, 23, 24, 25, 26).scheduleAllocation(3, result)

        allocator.unblockNext()
        allocationExecutor.executeSync {}
        assertThat(result).containsExactly(1, 11, 21, 2, 12, 22, 3, 13, 23, 4, 14, 5, 15)
    }

    private fun ExecutorService.executeSync(task: Runnable) = submit(task).get()

    class Allocator(vararg values: Any?) {

        val orderedValues = values.toList()
        var currentIndex = 0

        fun allocate(): Any? {
            val index = currentIndex.coerceAtMost(orderedValues.size - 1)
            val result = orderedValues[index]

            if (result is CountDownLatch) {
                result.await()
                currentIndex++
                // Return the next allocation
                return allocate()
            } else {
                currentIndex++
                return result
            }
        }

        fun unblockNext() =
            orderedValues
                .filterIsInstance<CountDownLatch>()
                .firstOrNull { it.count > 0 }
                ?.countDown()

        inline fun <reified T> scheduleAllocation(count: Int, output: MutableList<T>) =
            AsyncObjectAllocator.allocate(
                count = count,
                factory = this::allocate,
                callbackExecutor = Runnable::run,
                callback = { output.add(it as T) },
            )
    }
}
