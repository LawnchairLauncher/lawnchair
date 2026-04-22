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

package com.android.launcher3.integration.events

import com.android.launcher3.debug.TestEventEmitter.TestEvent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

enum class EventStatus() {
    SUCCESS,
    FAILURE,
    TIMEOUT,
}

class EventWaiter(val eventToWait: TestEvent) {
    private val deferrable = CompletableDeferred<EventStatus>()

    companion object {
        private const val TAG = "EventWaiter"
        private val SIGNAL_TIMEOUT = TimeUnit.SECONDS.toMillis(5)
    }

    fun waitForSignal(timeout: Long = SIGNAL_TIMEOUT) = runBlocking {
        var status = withTimeoutOrNull(timeout) { deferrable.await() }
        if (status == null) {
            status = EventStatus.TIMEOUT
        }
        if (status != EventStatus.SUCCESS) {
            throw Exception("Failure waiting for event $eventToWait, failure = $status")
        }
    }

    fun terminate() {
        deferrable.complete(EventStatus.SUCCESS)
    }
}
