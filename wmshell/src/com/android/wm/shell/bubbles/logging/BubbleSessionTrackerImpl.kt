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

import com.android.internal.logging.InstanceId
import com.android.internal.logging.InstanceIdSequence
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
import com.android.wm.shell.dagger.Bubbles
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.protolog.ShellProtoLogGroup
import javax.inject.Inject

/**
 * Keeps track of the current bubble session and logs when sessions start and end.
 *
 * Sessions are represented using [Session].
 */
@WMSingleton
class BubbleSessionTrackerImpl
@Inject
constructor(
    @param:Bubbles private val instanceIdSequence: InstanceIdSequence,
    private val logger: BubbleLogger
) : BubbleSessionTracker {

    private var currentSession: Session? = null

    /** Represents a bubble session. */
    private data class Session(
        /** Session identifier. The identifier remains the same until a new session is started. */
        val id: InstanceId,
        /** The package for the currently selected bubble in this session. */
        val appPackage: String,
    )

    override fun log(event: SessionEvent) {
        when (event) {
            is SessionEvent.Started -> sessionStarted(event)
            is SessionEvent.Ended -> sessionEnded(event)
            is SessionEvent.SwitchedBubble -> switchedBubble(event)
        }
    }

    private fun sessionStarted(event: SessionEvent.Started) {
        if (currentSession != null) {
            ProtoLog.e(
                ShellProtoLogGroup.WM_SHELL_BUBBLES_NOISY,
                "BubbleSessionTracker: starting to track a new session. " +
                    "previous session still active"
            )
        }

        val uiEvent =
            if (event.forBubbleBar) BUBBLE_BAR_SESSION_STARTED else BUBBLE_SESSION_STARTED
        val session =
            Session(
                id = instanceIdSequence.newInstanceId(),
                appPackage = event.selectedBubblePackage
            )
        logger.logWithSessionId(uiEvent, session.appPackage, session.id)
        currentSession = session
    }

    private fun sessionEnded(event: SessionEvent.Ended) {
        val session = currentSession
        if (session == null) {
            ProtoLog.e(
                ShellProtoLogGroup.WM_SHELL_BUBBLES_NOISY,
                "BubbleSessionTracker: session tracking stopped but current session is null"
            )
            return
        }

        val uiEvent =
            if (event.forBubbleBar) BUBBLE_BAR_SESSION_ENDED else BUBBLE_SESSION_ENDED
        logger.logWithSessionId(uiEvent, session.appPackage, session.id)
        currentSession = null
    }

    private fun switchedBubble(event: SessionEvent.SwitchedBubble) {
        val session = currentSession
        if (session == null) {
            ProtoLog.e(
                ShellProtoLogGroup.WM_SHELL_BUBBLES_NOISY,
                "BubbleSessionTracker: tracking bubble switch but current session is null"
            )
            return
        }

        val uiEventSwitchedFrom =
            if (event.forBubbleBar) {
                BUBBLE_BAR_SESSION_SWITCHED_FROM
            } else {
                BUBBLE_SESSION_SWITCHED_FROM
            }
        logger.logWithSessionId(uiEventSwitchedFrom, session.appPackage, session.id)

        val uiEventSwitchedTo =
            if (event.forBubbleBar) BUBBLE_BAR_SESSION_SWITCHED_TO else BUBBLE_SESSION_SWITCHED_TO
        logger.logWithSessionId(uiEventSwitchedTo, event.toBubblePackage, session.id)
        currentSession = session.copy(appPackage = event.toBubblePackage)
    }
}
