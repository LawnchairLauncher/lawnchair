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

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/** Immediately executed a submitted task on the caller thread. */
object ImmediateExecutorService : AbstractExecutorService() {

    override fun execute(command: Runnable) {
        // This is the core logic: run the command on the calling thread.
        command.run()
    }

    override fun isShutdown() = false

    override fun isTerminated() = false

    @Deprecated("Not supported and throws an exception when used. ")
    override fun shutdown() {
        throw UnsupportedOperationException()
    }

    @Deprecated("Not supported and throws an exception when used.")
    override fun shutdownNow(): List<Runnable> {
        throw UnsupportedOperationException()
    }

    @Deprecated("Not supported and throws an exception when used.")
    override fun awaitTermination(l: Long, timeUnit: TimeUnit): Boolean {
        throw UnsupportedOperationException()
    }
}
