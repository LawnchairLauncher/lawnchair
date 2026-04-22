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
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.google.common.truth.Truth.assertThat
import java.util.function.Consumer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.same
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class SimpleBroadcastReceiverTest {

    private lateinit var underTest: SimpleBroadcastReceiver

    @Mock private lateinit var intentConsumer: Consumer<Intent>
    @Mock private lateinit var context: Context
    @Mock private lateinit var completionRunnable: Runnable
    @Captor private lateinit var intentFilterCaptor: ArgumentCaptor<IntentFilter>

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        underTest = SimpleBroadcastReceiver(context, UI_HELPER_EXECUTOR, intentConsumer)
        if (Looper.getMainLooper() == null) {
            Looper.prepareMainLooper()
        }
    }

    @Test
    fun async_register() {
        underTest.register("test_action_1", "test_action_2")
        awaitTasksCompleted()

        verify(context).registerReceiver(same(underTest), intentFilterCaptor.capture())
        val intentFilter = intentFilterCaptor.value
        assertThat(intentFilter.countActions()).isEqualTo(2)
        assertThat(intentFilter.getAction(0)).isEqualTo("test_action_1")
        assertThat(intentFilter.getAction(1)).isEqualTo("test_action_2")
    }

    @Test
    fun async_register_withCompletionRunnable() {
        underTest.register(completionRunnable, "test_action_1", "test_action_2")
        awaitTasksCompleted()

        verify(context).registerReceiver(same(underTest), intentFilterCaptor.capture())
        verify(completionRunnable).run()
        val intentFilter = intentFilterCaptor.value
        assertThat(intentFilter.countActions()).isEqualTo(2)
        assertThat(intentFilter.getAction(0)).isEqualTo("test_action_1")
        assertThat(intentFilter.getAction(1)).isEqualTo("test_action_2")
    }

    @Test
    fun async_register_withCompletionRunnable_and_flag() {
        underTest.register(completionRunnable, 1, "test_action_1", "test_action_2")
        awaitTasksCompleted()

        verify(context).registerReceiver(same(underTest), intentFilterCaptor.capture(), eq(1))
        verify(completionRunnable).run()
        val intentFilter = intentFilterCaptor.value
        assertThat(intentFilter.countActions()).isEqualTo(2)
        assertThat(intentFilter.getAction(0)).isEqualTo("test_action_1")
        assertThat(intentFilter.getAction(1)).isEqualTo("test_action_2")
    }

    @Test
    fun async_register_with_package() {
        underTest.registerPkgActions("pkg", "test_action_1", "test_action_2")

        awaitTasksCompleted()
        verify(context).registerReceiver(same(underTest), intentFilterCaptor.capture())
        val intentFilter = intentFilterCaptor.value
        assertThat(intentFilter.getDataScheme(0)).isEqualTo("package")
        assertThat(intentFilter.getDataSchemeSpecificPart(0).path).isEqualTo("pkg")
        assertThat(intentFilter.countActions()).isEqualTo(2)
        assertThat(intentFilter.getAction(0)).isEqualTo("test_action_1")
        assertThat(intentFilter.getAction(1)).isEqualTo("test_action_2")
    }

    @Test
    fun sync_register_withCompletionRunnable_and_flag() {
        underTest =
            SimpleBroadcastReceiver(context, Handler(Looper.getMainLooper()), intentConsumer)

        underTest.register(completionRunnable, 1, "test_action_1", "test_action_2")
        getInstrumentation().waitForIdleSync()

        verify(context).registerReceiver(same(underTest), intentFilterCaptor.capture(), eq(1))
        verify(completionRunnable).run()
        val intentFilter = intentFilterCaptor.value
        assertThat(intentFilter.countActions()).isEqualTo(2)
        assertThat(intentFilter.getAction(0)).isEqualTo("test_action_1")
        assertThat(intentFilter.getAction(1)).isEqualTo("test_action_2")
    }

    @Test
    fun sync_register_withCompletionRunnable_and_permission_and_flag() {
        underTest =
            SimpleBroadcastReceiver(context, Handler(Looper.getMainLooper()), intentConsumer)

        underTest.register(completionRunnable, "permission", 1, "test_action_1", "test_action_2")
        getInstrumentation().waitForIdleSync()

        verify(context)
            .registerReceiver(
                same(underTest),
                intentFilterCaptor.capture(),
                eq("permission"),
                eq(null),
                eq(1),
            )
        verify(completionRunnable).run()
        val intentFilter = intentFilterCaptor.value
        assertThat(intentFilter.countActions()).isEqualTo(2)
        assertThat(intentFilter.getAction(0)).isEqualTo("test_action_1")
        assertThat(intentFilter.getAction(1)).isEqualTo("test_action_2")
    }

    @Test
    fun async_unregister() {
        underTest.unregisterReceiverSafely()

        awaitTasksCompleted()
        verify(context).unregisterReceiver(same(underTest))
    }

    @Test
    fun sync_unregister() {
        underTest =
            SimpleBroadcastReceiver(context, Handler(Looper.getMainLooper()), intentConsumer)

        underTest.unregisterReceiverSafely()
        getInstrumentation().waitForIdleSync()

        verify(context).unregisterReceiver(same(underTest))
    }

    @Test
    fun getPackageFilter() {
        val intentFilter =
            SimpleBroadcastReceiver.getPackageFilter("pkg", "test_action_1", "test_action_2")

        assertThat(intentFilter.getDataScheme(0)).isEqualTo("package")
        assertThat(intentFilter.getDataSchemeSpecificPart(0).path).isEqualTo("pkg")
        assertThat(intentFilter.countActions()).isEqualTo(2)
        assertThat(intentFilter.getAction(0)).isEqualTo("test_action_1")
        assertThat(intentFilter.getAction(1)).isEqualTo("test_action_2")
    }

    private fun awaitTasksCompleted() {
        UI_HELPER_EXECUTOR.submit<Any?> { null }.get()
    }
}
