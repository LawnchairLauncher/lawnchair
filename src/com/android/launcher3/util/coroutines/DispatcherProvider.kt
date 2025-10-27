/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.util.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newFixedThreadPoolContext

interface DispatcherProvider {
    val default: CoroutineDispatcher
    val background: CoroutineDispatcher
    val main: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}

object ProductionDispatchers : DispatcherProvider {
    private val bgDispatcher = CoroutinesHelper.bgDispatcher()

    override val default: CoroutineDispatcher = Dispatchers.Default
    override val background: CoroutineDispatcher = bgDispatcher
    override val main: CoroutineDispatcher = Dispatchers.Main.immediate
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}

private object CoroutinesHelper {
    /**
     * Default Coroutine dispatcher for background operations.
     *
     * Note that this is explicitly limiting the number of threads. In the past, we used
     * [Dispatchers.IO]. This caused >40 threads to be spawned, and a lot of thread list lock
     * contention between then, eventually causing jank.
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun bgDispatcher(): CoroutineDispatcher {
        // Why a new ThreadPool instead of just using Dispatchers.IO with
        // CoroutineDispatcher.limitedParallelism? Because, if we were to use Dispatchers.IO, we
        // would share those threads with other dependencies using Dispatchers.IO.
        // Using a dedicated thread pool we have guarantees only Launcher is able to schedule
        // code on those.
        return newFixedThreadPoolContext(
            nThreads = Runtime.getRuntime().availableProcessors(),
            name = "LauncherBg",
        )
    }
}
