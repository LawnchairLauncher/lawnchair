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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/** Represents a stream of data which supports listening for updates */
interface ListenableStream<T : Any> {

    /**
     * Registers a listener for getting all new events from now on. Multiple callbacks on the same
     * [executor] are grouped together when dispatching. This allows atomic update across different
     * listeners.
     */
    fun forEach(executor: Executor, callback: (T) -> Unit): SafeCloseable
}

/** [ListenableStream] which allows dispatching custom value */
open class MutableListenableStream<T : Any> : ListenableStream<T> {

    internal val listeners = ConcurrentHashMap<Executor, CopyOnWriteArrayList<(T) -> Unit>>()

    override fun forEach(executor: Executor, callback: (T) -> Unit): SafeCloseable {
        val list = listeners.getOrPut(executor) { CopyOnWriteArrayList() }
        list.add(callback)
        return SafeCloseable { list.remove(callback) }
    }

    /** Dispatches a [newValue] to the stream */
    open fun dispatchValue(newValue: T) {
        listeners.forEach { (executor, callbacks) ->
            if (callbacks.isNotEmpty()) {
                executor.execute { callbacks.forEach { it.invoke(newValue) } }
            }
        }
    }

    open fun asListenable(): ListenableStream<T> = this
}

/** A [ListenableStream] which also holds a reference to the last value */
interface ListenableRef<T : Any> : ListenableStream<T> {
    /* The current value */
    val value: T
}

/** [ListenableRef] which allows updating its value */
class MutableListenableRef<T : Any>(initValue: T) : MutableListenableStream<T>(), ListenableRef<T> {

    override var value: T = initValue
        private set

    /** Updates the reference [value] and also dispatches it to the stream */
    override fun dispatchValue(newValue: T) {
        value = newValue
        super.dispatchValue(newValue)
    }

    override fun asListenable(): ListenableRef<T> = this
}
