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

package com.android.wm.shell.bubbles

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.InstanceId
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.internal.logging.testing.UiEventLoggerFake.FakeUiEvent
import com.android.wm.shell.bubbles.UiEventSubject.Companion.assertThat
import com.google.common.truth.ExpectFailure.assertThat
import com.google.common.truth.ExpectFailure.expectFailure
import com.google.common.truth.Subject
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Test for [UiEventSubject] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class UiEventSubjectTest {

    private val uiEventSubjectFactory =
        Subject.Factory<UiEventSubject, FakeUiEvent> { metadata, actual ->
            UiEventSubject(metadata, actual)
        }

    private lateinit var uiEventLoggerFake: UiEventLoggerFake

    @Before
    fun setUp() {
        uiEventLoggerFake = UiEventLoggerFake()
    }

    @Test
    fun test_bubbleLogEvent_hasBubbleInfo() {
        val bubble =
            createBubble(
                appUid = 1,
                packageName = "test",
                instanceId = InstanceId.fakeInstanceId(2),
            )
        BubbleLogger(uiEventLoggerFake).log(bubble, BubbleLogger.Event.BUBBLE_BAR_BUBBLE_POSTED)
        val uiEvent = uiEventLoggerFake.logs.first()

        // Check that fields match the expected values
        assertThat(uiEvent.uid).isEqualTo(1)
        assertThat(uiEvent.packageName).isEqualTo("test")
        assertThat(uiEvent.instanceId.id).isEqualTo(2)

        // Check that hasBubbleInfo condition passes
        assertThat(uiEvent).hasBubbleInfo(bubble)
    }

    @Test
    fun test_bubbleLogEvent_uidMismatch() {
        val bubble =
            createBubble(
                appUid = 1,
                packageName = "test",
                instanceId = InstanceId.fakeInstanceId(2),
            )
        BubbleLogger(uiEventLoggerFake).log(bubble, BubbleLogger.Event.BUBBLE_BAR_BUBBLE_POSTED)
        val uiEvent = uiEventLoggerFake.logs.first()

        // Change uid to have a mismatch
        val otherBubble = bubble.copy(appUid = 99)

        val failure = expectFailure { test ->
            test.about(uiEventSubjectFactory).that(uiEvent).hasBubbleInfo(otherBubble)
        }
        assertThat(failure).factValue("value of").isEqualTo("uiEvent.uid")
    }

    @Test
    fun test_bubbleLogEvent_packageNameMismatch() {
        val bubble =
            createBubble(
                appUid = 1,
                packageName = "test",
                instanceId = InstanceId.fakeInstanceId(2),
            )
        BubbleLogger(uiEventLoggerFake).log(bubble, BubbleLogger.Event.BUBBLE_BAR_BUBBLE_POSTED)
        val uiEvent = uiEventLoggerFake.logs.first()

        // Change package name to have a mismatch
        val otherBubble = bubble.copy(packageName = "somethingelse")

        val failure = expectFailure { test ->
            test.about(uiEventSubjectFactory).that(uiEvent).hasBubbleInfo(otherBubble)
        }
        assertThat(failure).factValue("value of").isEqualTo("uiEvent.packageName")
    }

    @Test
    fun test_bubbleLogEvent_instanceIdMismatch() {
        val bubble =
            createBubble(
                appUid = 1,
                packageName = "test",
                instanceId = InstanceId.fakeInstanceId(2),
            )
        BubbleLogger(uiEventLoggerFake).log(bubble, BubbleLogger.Event.BUBBLE_BAR_BUBBLE_POSTED)
        val uiEvent = uiEventLoggerFake.logs.first()

        // Change instance id to have a mismatch
        val otherBubble = bubble.copy(instanceId = InstanceId.fakeInstanceId(99))

        val failure = expectFailure { test ->
            test.about(uiEventSubjectFactory).that(uiEvent).hasBubbleInfo(otherBubble)
        }
        assertThat(failure).factValue("value of").isEqualTo("uiEvent.instanceId")
    }

    private fun createBubble(appUid: Int, packageName: String, instanceId: InstanceId): Bubble {
        return mock<Bubble>() {
            on { getAppUid() } doReturn appUid
            on { getPackageName() } doReturn packageName
            on { getInstanceId() } doReturn instanceId
        }
    }

    private fun Bubble.copy(
        appUid: Int = this.appUid,
        packageName: String = this.packageName,
        instanceId: InstanceId = this.instanceId,
    ): Bubble {
        return createBubble(appUid, packageName, instanceId)
    }
}
