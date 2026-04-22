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

import com.android.launcher3.Flags.enableCoroutineThreadingImprovements
import com.android.launcher3.util.coroutines.CoroutinesHelper.bgDispatcher
import kotlin.math.max
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newFixedThreadPoolContext

interface DispatcherProvider {
    /**
     * The default CoroutineDispatcher that is used by all standard builders like launch, async,
     * etc. if neither a dispatcher nor any other ContinuationInterceptor is specified in their
     * context.
     *
     * See Kotlin documentation for [Dispatchers.Default] for more detailed documentation.
     */
    val default: CoroutineDispatcher

    /**
     * Background thread pool for longer running work e.g. accessing storage, making network
     * requests, running AI tasks etc.
     */
    val ioBackground: CoroutineDispatcher

    /**
     * Background thread pool for UI related work e.g. manipulating in-memory objects for
     * presentation on the UI.
     */
    val lightweightBackground: CoroutineDispatcher

    /**
     * A coroutine dispatcher that is confined to the Main thread operating with UI objects. It
     * executes coroutines immediately when it is already in the right context without an additional
     * re-dispatch.
     *
     * See Kotlin documentation for [Dispatchers.Main.immediate] for more detailed documentation.
     */
    val main: CoroutineDispatcher

    /**
     * A coroutine dispatcher that is not confined to any specific thread.
     *
     * See Kotlin documentation for [Dispatchers.Unconfined] for more detailed documentation.
     */
    val unconfined: CoroutineDispatcher
}

object ProductionDispatchers : DispatcherProvider {
    private val availableProcessors = Runtime.getRuntime().availableProcessors()
    private val singleBgThreadPool by lazy { bgDispatcher(availableProcessors, "LauncherBg") }

    override val default = Dispatchers.Default
    override val main = Dispatchers.Main.immediate

    override val ioBackground =
        if (enableCoroutineThreadingImprovements())
            bgDispatcher(nThreads = 1, threadName = "LauncherBgIO")
        else singleBgThreadPool

    override val lightweightBackground =
        if (enableCoroutineThreadingImprovements())
            bgDispatcher(nThreads = 1, threadName = "LauncherBgLight")
        else singleBgThreadPool

    override val unconfined = Dispatchers.Unconfined
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
    fun bgDispatcher(nThreads: Int, threadName: String): CoroutineDispatcher {
        // Why a new ThreadPool instead of just using Dispatchers.IO with
        // CoroutineDispatcher.limitedParallelism? Because, if we were to use Dispatchers.IO, we
        // would share those threads with other dependencies using Dispatchers.IO.
        // Using a dedicated thread pool we have guarantees only Launcher is able to schedule
        // code on those.
        return newFixedThreadPoolContext(nThreads = nThreads, name = threadName)
    }
}
