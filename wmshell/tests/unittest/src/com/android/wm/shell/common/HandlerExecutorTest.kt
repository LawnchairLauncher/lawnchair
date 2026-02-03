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

package com.android.wm.shell.common

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.wm.shell.ShellTestCase
import com.google.common.truth.Truth.assertThat
import java.util.function.BiConsumer
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.MockitoSession
import org.mockito.kotlin.whenever

/**
 * Tests for HandlerExecutor.
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:HandlerExecutorTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class HandlerExecutorTest : ShellTestCase() {

    class TestSetThreadPriorityFn : BiConsumer<Int, Int> {
        var lastSetPriority = UNSET_THREAD_PRIORITY
            private set
        var callCount = 0
            private set

        override fun accept(tid: Int, priority: Int) {
            lastSetPriority = priority
            callCount++
        }

        fun reset() {
            lastSetPriority = UNSET_THREAD_PRIORITY
            callCount = 0
        }
    }

    val testSetPriorityFn = TestSetThreadPriorityFn()

    @Test
    fun defaultExecutorDisallowBoost() {
        val executor = createTestHandlerExecutor()

        executor.setBoost()

        assertThat(executor.isBoosted()).isFalse()
    }

    @Test
    fun boostExecutor_resetWhenNotSet_expectNoOp() {
        val executor = createTestHandlerExecutor(DEFAULT_THREAD_PRIORITY, BOOSTED_THREAD_PRIORITY)
        val mockSession: MockitoSession = ExtendedMockito.mockitoSession()
            .mockStatic(android.os.Process::class.java)
            .startMocking()

        try {
            // Try to reset and ensure we never try to set the thread priority
            executor.resetBoost()

            assertThat(testSetPriorityFn.callCount).isEqualTo(0)
            assertThat(executor.isBoosted()).isFalse()
        } finally {
            mockSession.finishMocking()
        }
    }

    @Test
    fun boostExecutor_setResetBoost_expectThreadPriorityUpdated() {
        val executor = createTestHandlerExecutor(DEFAULT_THREAD_PRIORITY, BOOSTED_THREAD_PRIORITY)
        val mockSession: MockitoSession = ExtendedMockito.mockitoSession()
            .mockStatic(android.os.Process::class.java)
            .startMocking()

        try {
            // Boost and ensure the boosted thread priority is requested
            executor.setBoost()

            assertThat(testSetPriorityFn.lastSetPriority).isEqualTo(BOOSTED_THREAD_PRIORITY)
            assertThat(testSetPriorityFn.callCount).isEqualTo(1)
            assertThat(executor.isBoosted()).isTrue()

            // Reset and ensure the default thread priority is requested
            executor.resetBoost()

            assertThat(testSetPriorityFn.lastSetPriority).isEqualTo(DEFAULT_THREAD_PRIORITY)
            assertThat(testSetPriorityFn.callCount).isEqualTo(2)
            assertThat(executor.isBoosted()).isFalse()
        } finally {
            mockSession.finishMocking()
        }
    }

    @Test
    fun boostExecutor_overlappingBoost_expectResetOnlyWhenNotOverlapping() {
        val executor = createTestHandlerExecutor(DEFAULT_THREAD_PRIORITY, BOOSTED_THREAD_PRIORITY)
        val mockSession: MockitoSession = ExtendedMockito.mockitoSession()
            .mockStatic(android.os.Process::class.java)
            .startMocking()

        try {
            // Set and ensure we only update the thread priority once
            executor.setBoost()
            executor.setBoost()

            assertThat(testSetPriorityFn.lastSetPriority).isEqualTo(BOOSTED_THREAD_PRIORITY)
            assertThat(testSetPriorityFn.callCount).isEqualTo(1)
            assertThat(executor.isBoosted()).isTrue()

            // Reset and ensure we are still boosted and the thread priority doesn't change
            executor.resetBoost()

            assertThat(testSetPriorityFn.lastSetPriority).isEqualTo(BOOSTED_THREAD_PRIORITY)
            assertThat(testSetPriorityFn.callCount).isEqualTo(1)
            assertThat(executor.isBoosted()).isTrue()

            // Reset again and ensure we update the thread priority accordingly
            executor.resetBoost()

            assertThat(testSetPriorityFn.lastSetPriority).isEqualTo(DEFAULT_THREAD_PRIORITY)
            assertThat(testSetPriorityFn.callCount).isEqualTo(2)
            assertThat(executor.isBoosted()).isFalse()
        } finally {
            mockSession.finishMocking()
        }
    }

    /**
     * Creates a test handler executor backed by a mocked handler thread.
     */
    private fun createTestHandlerExecutor(
        defaultThreadPriority: Int = DEFAULT_THREAD_PRIORITY,
        boostedThreadPriority: Int = DEFAULT_THREAD_PRIORITY
    ) : HandlerExecutor {
        val handler = mock(Handler::class.java)
        val looper = mock(Looper::class.java)
        val thread = mock(HandlerThread::class.java)
        whenever(handler.looper).thenReturn(looper)
        whenever(looper.thread).thenReturn(thread)
        whenever(thread.threadId).thenReturn(1234)
        val executor = HandlerExecutor(handler, defaultThreadPriority, boostedThreadPriority)
        executor.replaceSetThreadPriorityFn(testSetPriorityFn)
        return executor
    }

    companion object {
        private const val UNSET_THREAD_PRIORITY = 0
        private const val DEFAULT_THREAD_PRIORITY = 1
        private const val BOOSTED_THREAD_PRIORITY = 1000
    }
}