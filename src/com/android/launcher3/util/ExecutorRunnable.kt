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

package com.android.launcher3.util

import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.function.Consumer
import java.util.function.Supplier

/** A [Runnable] that can be posted to a [Executor] that can be cancelled. */
class ExecutorRunnable<T>
private constructor(
    private val task: Supplier<T>,
    // Executor where consumer needs to be executed on. Typically UI executor.
    private val callbackExecutor: Executor,
    // Consumer that needs to be accepted upon completion of the task. Typically work that needs to
    // be done in UI thread after task completes.
    private val callback: Consumer<T>
) : Runnable {

    // future of this runnable that will used for cancellation.
    lateinit var future: Future<*>

    // flag to cancel the callback
    var canceled = false

    override fun run() {
        val value: T = task.get()
        callbackExecutor.execute {
            if (!canceled) {
                callback.accept(value)
            }
        }
    }

    /**
     * Cancel the [ExecutorRunnable] if not scheduled. If [ExecutorRunnable] has started execution
     * at this time, we will try to cancel the callback if not executed yet.
     */
    fun cancel(interrupt: Boolean) {
        future.cancel(interrupt)
        canceled = true
    }

    companion object {
        /**
         * Create [ExecutorRunnable] and execute it on task [Executor]. It will also save the
         * [Future] into this [ExecutorRunnable] to be used for cancellation.
         */
        fun <T> createAndExecute(
            // Executor where task will be executed, typically an Executor running on background
            // thread.
            taskExecutor: ExecutorService,
            task: Supplier<T>,
            callbackExecutor: Executor,
            callback: Consumer<T>
        ): ExecutorRunnable<T> {
            val executorRunnable = ExecutorRunnable(task, callbackExecutor, callback)
            executorRunnable.future = taskExecutor.submit(executorRunnable)
            return executorRunnable
        }
    }
}
