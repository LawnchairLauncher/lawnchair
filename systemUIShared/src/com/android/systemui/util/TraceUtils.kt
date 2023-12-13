/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.util

import android.os.Trace
import android.os.TraceNameSupplier
import java.util.concurrent.atomic.AtomicInteger

/**
 * Run a block within a [Trace] section. Calls [Trace.beginSection] before and [Trace.endSection]
 * after the passed block.
 */
inline fun <T> traceSection(tag: String, block: () -> T): T =
    if (Trace.isTagEnabled(Trace.TRACE_TAG_APP)) {
        Trace.traceBegin(Trace.TRACE_TAG_APP, tag)
        try {
            block()
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_APP)
        }
    } else {
        block()
    }

class TraceUtils {
    companion object {
        inline fun traceRunnable(tag: String, crossinline block: () -> Unit): Runnable {
            return Runnable { traceSection(tag) { block() } }
        }

        /**
         * Helper function for creating a Runnable object that implements TraceNameSupplier.
         *
         * This is useful for posting Runnables to Handlers with meaningful names.
         */
        inline fun namedRunnable(tag: String, crossinline block: () -> Unit): Runnable {
            return object : Runnable, TraceNameSupplier {
                override fun getTraceName(): String = tag
                override fun run() = block()
            }
        }

        /**
         * Cookie used for async traces. Shouldn't be public, but to use it inside inline methods
         * there is no other way around.
         */
        val lastCookie = AtomicInteger(0)

        /**
         * Creates an async slice in a track called "AsyncTraces".
         *
         * This can be used to trace coroutine code. Note that all usages of this method will appear
         * under a single track.
         */
        inline fun <T> traceAsync(method: String, block: () -> T): T =
            traceAsync("AsyncTraces", method, block)

        /**
         * Creates an async slice in a track with [trackName] while [block] runs.
         *
         * This can be used to trace coroutine code. [method] will be the name of the slice,
         * [trackName] of the track. The track is one of the rows visible in a perfetto trace inside
         * SystemUI process.
         */
        inline fun <T> traceAsync(trackName: String, method: String, block: () -> T): T {
            val cookie = lastCookie.incrementAndGet()
            Trace.asyncTraceForTrackBegin(Trace.TRACE_TAG_APP, trackName, method, cookie)
            try {
                return block()
            } finally {
                Trace.asyncTraceForTrackEnd(Trace.TRACE_TAG_APP, trackName, cookie)
            }
        }
    }
}
