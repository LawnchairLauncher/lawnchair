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
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.SimpleBroadcastReceiver.Companion.actionsFilter
import com.android.launcher3.util.SimpleBroadcastReceiver.Companion.packageFilter
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.same
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class SimpleBroadcastReceiverTest {

    @get:Rule val context = spy(SandboxApplication())

    private lateinit var executorThread: HandlerThread
    private lateinit var executor: LooperExecutor

    private lateinit var callbackExecutorThread: HandlerThread
    private lateinit var callbackExecutor: LooperExecutor

    private var intentConsumer: Consumer<Intent> = Consumer {}

    private val underTest: SimpleBroadcastReceiver by lazy {
        SimpleBroadcastReceiver(
            context = context,
            executor = executor,
            callbackExecutor = callbackExecutor,
            intentConsumer = intentConsumer,
        )
    }

    @Before
    fun setup() {
        executorThread =
            HandlerThread("executor-for-registration", Process.THREAD_PRIORITY_DEFAULT).apply {
                start()
            }
        executor = LooperExecutor(executorThread.looper, Process.THREAD_PRIORITY_DEFAULT)

        callbackExecutorThread =
            HandlerThread("executor-for-callback", Process.THREAD_PRIORITY_DEFAULT).apply {
                start()
            }
        callbackExecutor =
            LooperExecutor(callbackExecutorThread.looper, Process.THREAD_PRIORITY_DEFAULT)
    }

    @After
    fun tearDown() {
        underTest.close()
        TestUtil.runOnExecutorSync(executor) {}
        executorThread.quit()
        executorThread.join()

        TestUtil.runOnExecutorSync(callbackExecutor) {}
        callbackExecutorThread.quit()
        callbackExecutorThread.join()
    }

    @Test
    fun register_executes_on_executor() {
        var registrationLooper: Looper? = null
        doAnswer {
                registrationLooper = Looper.myLooper()
                null
            }
            .whenever(context)
            .registerReceiver(any(), any(), any(), any(), any())

        val filter = actionsFilter("test_action_1", "test_action_2")
        underTest.register(filter = filter, flags = 32, permission = "test-permission")
        awaitTasksCompleted()

        verify(context)
            .registerReceiver(
                same(underTest),
                same(filter),
                eq("test-permission"),
                same(callbackExecutor.handler),
                eq(32),
            )
        assertThat(registrationLooper).isEqualTo(executor.looper)
    }

    @Test
    fun completion_executes_on_callback_executor() {
        val filter = actionsFilter("test_action_2", "test_action_3")
        val onCompleteCallback = CompletableFuture<Looper>()
        doReturn(null).whenever(context).registerReceiver(any(), any(), any(), any(), any())

        underTest.register(
            filter = filter,
            flags = 33,
            permission = "test-permission2",
            completionCallback = { onCompleteCallback.complete(Looper.myLooper()) },
        )
        awaitTasksCompleted()

        verify(context)
            .registerReceiver(
                same(underTest),
                same(filter),
                eq("test-permission2"),
                same(callbackExecutor.handler),
                eq(33),
            )
        // Callback called on completion executor
        assertThat(onCompleteCallback.join()).isEqualTo(callbackExecutor.looper)
    }

    @Test
    fun callback_executes_on_callback_executor() {
        val action = "test_action_${SystemClock.uptimeNanos()}"

        val callbackResult = CompletableFuture<Pair<Looper, Intent>>()
        intentConsumer = Consumer { callbackResult.complete(Pair(Looper.myLooper()!!, it)) }
        underTest.register(filter = IntentFilter(action), flags = Context.RECEIVER_EXPORTED)

        awaitTasksCompleted()
        context.sendBroadcast(Intent(action))

        val result = callbackResult.join()
        assertThat(result.first).isEqualTo(callbackExecutor.looper)
        assertThat(result.second.action).isEqualTo(action)
    }

    @Test
    fun unregister_executes_on_executor() {
        var closeLooper: Looper? = null
        doAnswer {
                closeLooper = Looper.myLooper()
                null
            }
            .whenever(context)
            .unregisterReceiver(any())

        underTest.close()

        awaitTasksCompleted()
        verify(context).unregisterReceiver(same(underTest))
        assertThat(closeLooper).isEqualTo(executor.looper)
    }

    @Test
    fun verifyPackageFilter() {
        val intentFilter = packageFilter("pkg", "test_action_1", "test_action_2")

        assertThat(intentFilter.getDataScheme(0)).isEqualTo("package")
        assertThat(intentFilter.getDataSchemeSpecificPart(0).path).isEqualTo("pkg")
        assertThat(intentFilter.countActions()).isEqualTo(2)
        assertThat(intentFilter.getAction(0)).isEqualTo("test_action_1")
        assertThat(intentFilter.getAction(1)).isEqualTo("test_action_2")
    }

    @Test
    fun verifyActionsFilter() {
        val intentFilter = actionsFilter("test_action_1", "test_action_2", "test_action_3")

        assertThat(intentFilter.countDataSchemes()).isEqualTo(0)
        assertThat(intentFilter.countActions()).isEqualTo(3)
        assertThat(intentFilter.getAction(0)).isEqualTo("test_action_1")
        assertThat(intentFilter.getAction(1)).isEqualTo("test_action_2")
        assertThat(intentFilter.getAction(2)).isEqualTo("test_action_3")
    }

    private fun awaitTasksCompleted() {
        TestUtil.runOnExecutorSync(executor) {}
        TestUtil.runOnExecutorSync(callbackExecutor) {}
    }
}
