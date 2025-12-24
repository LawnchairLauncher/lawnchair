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

package com.android.launcher3.util

import android.content.Intent
import android.content.Intent.ACTION_SCREEN_OFF
import android.content.Intent.ACTION_SCREEN_ON
import android.content.Intent.ACTION_USER_PRESENT
import android.content.IntentFilter
import android.util.StringBuilderPrinter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.SimpleBroadcastReceiver.Companion.actionsFilter
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenOnTrackerTest {

    @get:Rule val mockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var receiver: SimpleBroadcastReceiver
    @Mock private lateinit var listener: ScreenOnTracker.ScreenOnListener
    @Mock private lateinit var tracker: DaggerSingletonTracker

    private lateinit var underTest: ScreenOnTracker

    @Before
    fun setup() {
        underTest = ScreenOnTracker(receiver, tracker)
    }

    @Test
    fun test_default_state() {
        val expectedFilter = actionsFilter(ACTION_SCREEN_ON, ACTION_SCREEN_OFF, ACTION_USER_PRESENT)
        verify(receiver)
            .register(
                argThat { toFullString() == expectedFilter.toFullString() },
                any(),
                isNull(),
                isNull(),
            )
        assertThat(underTest.isScreenOn).isTrue()
    }

    @Test
    fun close_unregister_receiver() {
        verify(tracker).addCloseable(receiver)
    }

    @Test
    fun add_listener_then_receive_screen_on_intent_notify_listener() {
        underTest.addListener(listener)

        underTest.onReceive(Intent(ACTION_SCREEN_ON))

        verify(listener).onScreenOnChanged(true)
        assertThat(underTest.isScreenOn).isTrue()
    }

    @Test
    fun add_listener_then_receive_screen_off_intent_notify_listener() {
        underTest.addListener(listener)

        underTest.onReceive(Intent(ACTION_SCREEN_OFF))

        verify(listener).onScreenOnChanged(false)
        assertThat(underTest.isScreenOn).isFalse()
    }

    @Test
    fun add_listener_then_receive_user_present_intent_notify_listener() {
        underTest.addListener(listener)

        underTest.onReceive(Intent(ACTION_USER_PRESENT))

        verify(listener).onUserPresent()
        assertThat(underTest.isScreenOn).isTrue()
    }

    @Test
    fun remove_listener_then_receive_intent_noOp() {
        underTest.addListener(listener)

        underTest.removeListener(listener)

        underTest.onReceive(Intent(ACTION_USER_PRESENT))
        verifyNoMoreInteractions(listener)
    }

    private fun IntentFilter.toFullString(): String =
        StringBuilder().also { dump(StringBuilderPrinter(it), "") }.toString()
}
