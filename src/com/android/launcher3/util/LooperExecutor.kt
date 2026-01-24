/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.launcher3.util

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.Process.THREAD_PRIORITY_FOREGROUND
import androidx.annotation.IntDef

import com.google.common.util.concurrent.AbstractListeningExecutorService

import java.util.concurrent.TimeUnit
import kotlin.annotation.AnnotationRetention.SOURCE

/** Extension of [AbstractListeningExecutorService] which executed on a provided looper. */
class LooperExecutor(looper: Looper, private val defaultPriority: Int) : AbstractListeningExecutorService() {
    val handler: Handler = Handler(looper)

    @JvmOverloads
    constructor(
        name: String,
        defaultPriority: Int = Process.THREAD_PRIORITY_DEFAULT,
    ) : this(createAndStartNewLooper(name, defaultPriority), defaultPriority)

    /** Returns the thread for this executor */
    val thread: Thread
        get() = handler.looper.thread

    /** Returns the looper for this executor */
    val looper: Looper
        get() = handler.looper

    @ElevationCaller private var elevationFlags: Int = 0

    override fun execute(runnable: Runnable) {
        if (handler.looper == Looper.myLooper()) {
            runnable.run()
        } else {
            handler.post(runnable)
        }
    }

    /** Same as execute, but never runs the action inline. */
    fun post(runnable: Runnable) {
        handler.post(runnable)
    }

    @Deprecated("Not supported and throws an exception when used")
    override fun shutdown() {
        throw UnsupportedOperationException()
    }

    @Deprecated("Not supported and throws an exception when used.")
    override fun shutdownNow(): List<Runnable> {
        throw UnsupportedOperationException()
    }

    override fun isShutdown() = false

    override fun isTerminated() = false

    @Deprecated("Not supported and throws an exception when used.")
    override fun awaitTermination(l: Long, timeUnit: TimeUnit): Boolean {
        throw UnsupportedOperationException()
    }

    /**
     * Increases the priority of the thread for the [caller]. Multiple calls with same caller are
     * ignored. The priority is reset once wall callers have restored priority
     */
    fun elevatePriority(@ElevationCaller caller: Int) {
        val wasElevated = elevationFlags != 0
        elevationFlags = elevationFlags.or(caller)
        if (elevationFlags != 0 && !wasElevated)
            Process.setThreadPriority(
                (thread as HandlerThread).threadId,
                THREAD_PRIORITY_FOREGROUND,
            )
    }

    /** Restores to default priority if it was previously elevated */
    fun restorePriority(@ElevationCaller caller: Int) {
        val wasElevated = elevationFlags != 0
        elevationFlags = elevationFlags.and(caller.inv())
        if (elevationFlags == 0 && wasElevated)
            Process.setThreadPriority((thread as HandlerThread).threadId, defaultPriority)
    }

    @Retention(SOURCE)
    @IntDef(value = [CALLER_LOADER_TASK, CALLER_ICON_CACHE], flag = true)
    annotation class ElevationCaller

    companion object {
        /** Utility method to get a started handler thread statically with the provided priority */
        @JvmOverloads
        @JvmStatic
        fun createAndStartNewLooper(
            name: String,
            priority: Int = Process.THREAD_PRIORITY_DEFAULT,
        ): Looper = HandlerThread(name, priority).apply { start() }.looper

        const val CALLER_LOADER_TASK = 1 shl 0
        const val CALLER_ICON_CACHE = 1 shl 1
    }
}
