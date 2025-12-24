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

package com.android.wm.shell.bubbles.logging

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.InstanceId
import com.android.internal.logging.InstanceIdSequence
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.bubbles.BubbleLogger
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_BAR_SESSION_ENDED
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_BAR_SESSION_STARTED
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_BAR_SESSION_SWITCHED_FROM
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_BAR_SESSION_SWITCHED_TO
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_SESSION_ENDED
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_SESSION_STARTED
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_SESSION_SWITCHED_FROM
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_SESSION_SWITCHED_TO
import com.android.wm.shell.bubbles.logging.BubbleSessionTracker.SessionEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [BubbleSessionTrackerImpl]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleSessionTrackerImplTest {

    private val instanceIdSequence = FakeInstanceIdSequence()
    private val uiEventLoggerFake = UiEventLoggerFake()
    private val bubbleLogger = BubbleLogger(uiEventLoggerFake)
    private val bubbleSessionTracker = BubbleSessionTrackerImpl(instanceIdSequence, bubbleLogger)

    @Before
    fun setUp() {
        ProtoLog.REQUIRE_PROTOLOGTOOL = false
        ProtoLog.init()
    }

    @Test
    fun startSession_logsNewSessionId() {
        bubbleSessionTracker.log(
            SessionEvent.Started(forBubbleBar = true, selectedBubblePackage = "app.package")
        )
        bubbleSessionTracker.log(SessionEvent.Ended(forBubbleBar = true))
        bubbleSessionTracker.log(
            SessionEvent.Started(forBubbleBar = true, selectedBubblePackage = "app.package")
        )

        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(3)
        val firstSessionStart = uiEventLoggerFake.logs.first()
        val secondSessionStart = uiEventLoggerFake.logs.last()
        assertThat(firstSessionStart.eventId).isEqualTo(BUBBLE_BAR_SESSION_STARTED.id)
        assertThat(secondSessionStart.eventId).isEqualTo(BUBBLE_BAR_SESSION_STARTED.id)
        assertThat(firstSessionStart.instanceId).isNotEqualTo(secondSessionStart.instanceId)
    }

    @Test
    fun endSession_logsSameSessionId() {
        bubbleSessionTracker.log(
            SessionEvent.Started(forBubbleBar = true, selectedBubblePackage = "app.package")
        )
        bubbleSessionTracker.log(SessionEvent.Ended(forBubbleBar = true))

        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(2)
        val sessionStart = uiEventLoggerFake.logs.first()
        val sessionEnd = uiEventLoggerFake.logs.last()
        assertThat(sessionStart.eventId).isEqualTo(BUBBLE_BAR_SESSION_STARTED.id)
        assertThat(sessionEnd.eventId).isEqualTo(BUBBLE_BAR_SESSION_ENDED.id)
        assertThat(sessionStart.instanceId).isEqualTo(sessionEnd.instanceId)
    }

    @Test
    fun switchedBubble() {
        bubbleSessionTracker.log(
            SessionEvent.Started(forBubbleBar = true, selectedBubblePackage = "initial.package")
        )
        bubbleSessionTracker.log(
            SessionEvent.SwitchedBubble(forBubbleBar = true, toBubblePackage = "new.package")
        )
        bubbleSessionTracker.log(SessionEvent.Ended(forBubbleBar = true))

        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(4)

        with (uiEventLoggerFake.logs[0]) {
            assertThat(eventId).isEqualTo(BUBBLE_BAR_SESSION_STARTED.id)
            assertThat(packageName).isEqualTo("initial.package")
        }

        with (uiEventLoggerFake.logs[1]) {
            assertThat(eventId).isEqualTo(BUBBLE_BAR_SESSION_SWITCHED_FROM.id)
            assertThat(packageName).isEqualTo("initial.package")
        }

        with (uiEventLoggerFake.logs[2]) {
            assertThat(eventId).isEqualTo(BUBBLE_BAR_SESSION_SWITCHED_TO.id)
            assertThat(packageName).isEqualTo("new.package")
        }

        with (uiEventLoggerFake.logs[3]) {
            assertThat(eventId).isEqualTo(BUBBLE_BAR_SESSION_ENDED.id)
            assertThat(packageName).isEqualTo("new.package")
        }

        val loggedInstanceIds = uiEventLoggerFake.logs.map { it.instanceId }.toSet()
        assertThat(loggedInstanceIds).hasSize(1)
    }

    @Test
    fun logCorrectEventId() {
        bubbleSessionTracker.log(
            SessionEvent.Started(forBubbleBar = true, selectedBubblePackage = "app.package")
        )
        bubbleSessionTracker.log(
            SessionEvent.SwitchedBubble(forBubbleBar = true, toBubblePackage = "other.app.package")
        )
        bubbleSessionTracker.log(SessionEvent.Ended(forBubbleBar = true))
        bubbleSessionTracker.log(
            SessionEvent.Started(forBubbleBar = false, selectedBubblePackage = "app.package")
        )
        bubbleSessionTracker.log(
            SessionEvent.SwitchedBubble(forBubbleBar = false, toBubblePackage = "other.app.package")
        )
        bubbleSessionTracker.log(SessionEvent.Ended(forBubbleBar = false))

        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(8)
        assertThat(uiEventLoggerFake.logs.map { it.eventId })
            .containsExactly(
                BUBBLE_BAR_SESSION_STARTED.id,
                BUBBLE_BAR_SESSION_SWITCHED_FROM.id,
                BUBBLE_BAR_SESSION_SWITCHED_TO.id,
                BUBBLE_BAR_SESSION_ENDED.id,
                BUBBLE_SESSION_STARTED.id,
                BUBBLE_SESSION_SWITCHED_FROM.id,
                BUBBLE_SESSION_SWITCHED_TO.id,
                BUBBLE_SESSION_ENDED.id
            )
            .inOrder()
    }

    @Test
    fun sessionEnded_noActiveSession_shouldNotLog() {
        bubbleSessionTracker.log(SessionEvent.Ended(forBubbleBar = true))

        assertThat(uiEventLoggerFake.logs).isEmpty()
    }

    @Test
    fun switchedBubble_noActiveSession_shouldNotLog() {
        bubbleSessionTracker.log(
            SessionEvent.SwitchedBubble(forBubbleBar = true, toBubblePackage = "app.package")
        )

        assertThat(uiEventLoggerFake.logs).isEmpty()
    }

    @Test
    fun startSession_whenSessionActive_startsNewSession() {
        // Start a session.
        bubbleSessionTracker.log(
            SessionEvent.Started(forBubbleBar = true, selectedBubblePackage = "app.package1")
        )

        // Without ending the first session, start another one.
        // This should log an error but still proceed to start a new session, overwriting the old one.
        bubbleSessionTracker.log(
            SessionEvent.Started(forBubbleBar = true, selectedBubblePackage = "app.package2")
        )

        // Now, end the session.
        bubbleSessionTracker.log(SessionEvent.Ended(forBubbleBar = true))

        // Verify the logs.
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(3)

        // The first log should be the start of the first session.
        val firstSessionStart = uiEventLoggerFake.logs[0]
        assertThat(firstSessionStart.eventId).isEqualTo(BUBBLE_BAR_SESSION_STARTED.id)
        assertThat(firstSessionStart.packageName).isEqualTo("app.package1")

        // The second log should be the start of the second session.
        val secondSessionStart = uiEventLoggerFake.logs[1]
        assertThat(secondSessionStart.eventId).isEqualTo(BUBBLE_BAR_SESSION_STARTED.id)
        assertThat(secondSessionStart.packageName).isEqualTo("app.package2")
        assertThat(firstSessionStart.instanceId).isNotEqualTo(secondSessionStart.instanceId)

        // The third log should be the end of the *second* session.
        val sessionEnd = uiEventLoggerFake.logs[2]
        assertThat(sessionEnd.eventId).isEqualTo(BUBBLE_BAR_SESSION_ENDED.id)
        assertThat(sessionEnd.packageName).isEqualTo("app.package2")
        assertThat(sessionEnd.instanceId).isEqualTo(secondSessionStart.instanceId)
    }

    class FakeInstanceIdSequence : InstanceIdSequence(/* instanceIdMax= */ 10) {

        var id = -1
            private set

        override fun newInstanceId(): InstanceId {
            id = if (id == -1 || id == 10) 1 else id + 1
            return InstanceId.fakeInstanceId(id)
        }
    }
}
