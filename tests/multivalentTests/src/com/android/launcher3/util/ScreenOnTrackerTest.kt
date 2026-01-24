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

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SCREEN_OFF
import android.content.Intent.ACTION_SCREEN_ON
import android.content.Intent.ACTION_USER_PRESENT
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verifyNoMoreInteractions

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenOnTrackerTest {

    @Mock private lateinit var receiver: SimpleBroadcastReceiver
    @Mock private lateinit var context: Context
    @Mock private lateinit var listener: ScreenOnTracker.ScreenOnListener
    @Mock private lateinit var tracker: DaggerSingletonTracker

    private lateinit var underTest: ScreenOnTracker

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        underTest = ScreenOnTracker(context, receiver, tracker)
    }

    @Test
    fun test_default_state() {
        verify(receiver).register(ACTION_SCREEN_ON, ACTION_SCREEN_OFF, ACTION_USER_PRESENT)
        assertThat(underTest.isScreenOn).isTrue()
    }

    @Test
    fun close_unregister_receiver() {
        underTest.close()

        verify(receiver).unregisterReceiverSafely()
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
}
