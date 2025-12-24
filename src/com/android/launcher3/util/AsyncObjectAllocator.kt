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

import android.os.Process
import androidx.annotation.AnyThread
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.android.launcher3.util.Executors.SimpleThreadFactory
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Utility class which allocates items on a background non-looper thread.
 *
 * This is primarily intended for view inflation, but can also be used for other objects. It uses a
 * single thread for all requests. Multiple requests are staggered so that a single large request
 * doesn't start other requests.
 */
object AsyncObjectAllocator {

    /** A background executor to allocating objects. */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val allocationExecutor: ExecutorService =
        Executors.newSingleThreadExecutor(
            SimpleThreadFactory("async-allocation", Process.THREAD_PRIORITY_BACKGROUND)
        )

    private val activeJobs = mutableListOf<JobDescription<*>>()

    /**
     * Allocates [count] instances [T] on the background executor, using the provided [factory].
     * Anytime the factory returns null, the allocation is aborted. The provided [callback] is
     * called for every allocation on [callbackExecutor]. The job can also be aborted by calling
     * [SafeCloseable.close].
     */
    @AnyThread
    @JvmStatic
    fun <T> allocate(
        count: Int,
        factory: () -> T?,
        callbackExecutor: Executor,
        callback: (T) -> Unit,
    ): SafeCloseable {
        if (count <= 0) return SafeCloseable {}
        val job = JobDescription(count, factory, callbackExecutor, callback)

        synchronized(activeJobs) { activeJobs.add(job) }
        allocationExecutor.execute { jobLoop() }
        return job
    }

    @WorkerThread
    private fun jobLoop() {
        var nextJobIndex = 0

        while (true) {
            val currentJob =
                synchronized(activeJobs) {
                    if (activeJobs.isEmpty()) return
                    if (nextJobIndex >= activeJobs.size) nextJobIndex = 0
                    activeJobs[nextJobIndex]
                }

            if (currentJob.cancelled || currentJob.remaining <= 0) {
                synchronized(activeJobs) { activeJobs.remove(currentJob) }
                continue
            }

            nextJobIndex++
            allocateAndPost(currentJob)
        }
    }

    @WorkerThread
    private fun <T> allocateAndPost(currentJob: JobDescription<T>) {
        val obj = kotlin.runCatching { currentJob.factory.invoke() }.getOrNull()
        if (obj == null) {
            currentJob.cancelled = true
        } else {
            currentJob.remaining--
            currentJob.callbackExecutor.execute {
                if (!currentJob.cancelled) currentJob.callback.invoke(obj)
            }
        }
    }

    class JobDescription<T>(
        count: Int,
        val factory: () -> T?,
        val callbackExecutor: Executor,
        val callback: (T) -> Unit,
    ) : SafeCloseable {
        @set:AnyThread var cancelled: Boolean = false

        @set:WorkerThread var remaining: Int = count

        override fun close() {
            cancelled = true
        }
    }
}
