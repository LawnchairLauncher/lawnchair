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

package com.android.wm.shell.common

import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.launch

/**
 * Serializes the execution of suspendable work, ensuring that tasks are processed
 * sequentially in a First-In, First-Out (FIFO) order.
 *
 * This class is useful for managing operations that need to be executed one after another,
 * preventing race conditions and ensuring a predictable order of execution. It uses a
 * [Channel] to queue incoming work and a single worker coroutine to process the queue.
 *
 * @param scope The [CoroutineScope] the serializer will use to launch its worker coroutine.
 *              Cancelling this scope will terminate the queue and stop all processing.
 * @param capacity The number of elements that can be buffered in the channel.
 *                 See [Channel] for options like [Channel.UNLIMITED], [Channel.BUFFERED], etc.
 *                 Defaults to [Channel.UNLIMITED].
 * @param overflowStrategy The action to take when the buffer is full. See [BufferOverflow].
 *                         Defaults to [BufferOverflow.SUSPEND].
 */
class WorkSerializer(
    scope: CoroutineScope,
    capacity: Int = Channel.Factory.UNLIMITED,
    overflowStrategy: BufferOverflow = BufferOverflow.SUSPEND,
) {
    private val channel =
        Channel<suspend () -> Unit>(
            capacity = capacity,
            onBufferOverflow = overflowStrategy,
            onUndeliveredElement = { onUndeliveredElement() })

    init {
        // The single worker coroutine that processes the queue.
        scope.launch {
            for (work in channel) {
                try {
                    work()
                } catch (e: CancellationException) {
                    ProtoLog.w(
                        WM_SHELL, "CoroutineQueue got cancelled %s",
                        e.printStackTrace()
                    )
                } catch (e: Throwable) {
                    ProtoLog.e(
                        WM_SHELL, "Error in CoroutineQueue %s",
                        e.printStackTrace()
                    )
                }
            }
        }
    }

    /**
     * Adds a new coroutine block to the queue to be executed.
     *
     * @param work The suspendable lambda to execute.
     */
    fun post(work: suspend () -> Unit): ChannelResult<Unit> {
        val result = channel.trySend(work)
        if (result.isFailure) {
            ProtoLog.w(
                WM_SHELL,
                "Failed to post work to WorkSerializer %s",
                result.exceptionOrNull()?.stackTraceToString()
            )
        }
        return result
    }

    /**
     * Closes the queue to new work. Pending work will be completed.
     */
    fun close() = channel.close()

    fun onUndeliveredElement() {
        ProtoLog.w(WM_SHELL, "An element in WorkSerializer was undelivered")
    }
}