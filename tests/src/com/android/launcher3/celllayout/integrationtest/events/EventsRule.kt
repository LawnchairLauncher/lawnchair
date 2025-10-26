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

package com.android.launcher3.celllayout.integrationtest.events

import android.content.Context
import android.util.Log
import com.android.dx.mockito.inline.extended.ExtendedMockito.*
import com.android.dx.mockito.inline.extended.StaticMockitoSession
import com.android.launcher3.debug.TestEventEmitter
import com.android.launcher3.debug.TestEventEmitter.TestEvent
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.mockito.quality.Strictness

/**
 * Rule to create EventWaiters to wait for events that happens on the Launcher. For reference look
 * at [TestEvent] for existing events.
 *
 * Waiting for event should be used to prevent race conditions, it provides a more precise way of
 * waiting for events compared to [AbstractLauncherUiTest#waitForLauncherCondition].
 *
 * This class mocks the static method [TestEventEmitter.sendEvent]
 */
class EventsRule(val context: Context) : TestRule {

    private val expectedEvents: ArrayDeque<EventWaiter> = ArrayDeque()

    private lateinit var mockitoSession: StaticMockitoSession

    override fun apply(base: Statement, description: Description?): Statement {
        return object : Statement() {
            override fun evaluate() {
                try {
                    beforeTest()
                    base.evaluate()
                } finally {
                    afterTest()
                }
            }
        }
    }

    fun createEventWaiter(expectedEvent: TestEvent): EventWaiter {
        val eventWaiter = EventWaiter(expectedEvent)
        expectedEvents.add(eventWaiter)
        return eventWaiter
    }

    private fun beforeTest() {
        mockitoSession =
            mockitoSession()
                .strictness(Strictness.LENIENT)
                .spyStatic(TestEventEmitter::class.java)
                .startMocking()

        doAnswer { invocation ->
                val event = (invocation.arguments[0] as TestEvent)
                Log.d(TAG, "Signal received $event")
                Log.d(TAG, "Total expected events ${expectedEvents.size}")
                if (!expectedEvents.isEmpty()) {
                    val eventWaiter = expectedEvents.last()
                    if (eventWaiter.eventToWait == event) {
                        Log.d(TAG, "Removing $event")
                        expectedEvents.removeLast()
                        eventWaiter.terminate()
                    } else {
                        Log.d(TAG, "Not matching $event")
                    }
                }
                null
            }
            .`when` { TestEventEmitter.sendEvent(any()) }
    }

    private fun afterTest() {
        mockitoSession.finishMocking()
        expectedEvents.clear()
    }

    companion object {
        private const val TAG = "TestEvents"
    }
}
