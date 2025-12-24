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

package com.android.wm.shell.common

import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidTestingRunner::class)
@ExperimentalCoroutinesApi
class WorkSerializerTest : ShellTestCase() {

    /**
     * Tests that jobs are executed sequentially in the order they are posted (FIFO),
     * regardless of their internal delays.
     */
    @Test
    fun queueMultipleJobs_executesSequentially() = runTest {
        val testScope = TestScope(testScheduler)
        val queue = WorkSerializer(testScope)
        val executionOrder = mutableListOf<Int>()

        // Post three jobs with out-of-order delays
        queue.post {
            delay(300)
            executionOrder.add(1)
        }
        queue.post {
            delay(100)
            executionOrder.add(2)
        }
        queue.post {
            delay(50)
            executionOrder.add(3)
        }

        // Advance the virtual clock until all coroutines are idle
        testScheduler.advanceUntilIdle() // Let all jobs complete

        // Verify the execution order is 1, 2, 3 as they were posted
        assertThat(executionOrder).containsExactly(1, 2, 3).inOrder()

        testScope.cancel()
    }

    /**
     * Tests that if a job throws an exception, the queue does not stop and
     * continues to process subsequent jobs.
     */
    @Test
    fun queueWithException_doesNotStopProcessing() = runTest {
        val testScope = TestScope(testScheduler)
        val queue = WorkSerializer(testScope)
        val executionOrder = mutableListOf<Int>()

        queue.post { executionOrder.add(1) }
        queue.post { throw IllegalStateException("Job failed!") }
        queue.post { executionOrder.add(3) }

        testScheduler.advanceUntilIdle()

        // Verify that the job after the failing one was still executed
        assertThat(executionOrder).containsExactly(1, 3).inOrder()

        testScope.cancel()
    }

    /**
     * Tests that after calling close(), the queue finishes its current work
     * but does not accept new submissions.
     */
    @Test
    fun queueAfterClose_doesNotAcceptNewJobs() = runTest {
        val testScope = TestScope(testScheduler)
        val queue = WorkSerializer(testScope)
        val executionOrder = mutableListOf<Int>()

        // Post initial jobs
        queue.post {
            delay(100)
            executionOrder.add(1)
        }
        queue.post {
            delay(100)
            executionOrder.add(2)
        }

        // Close the queue. No new jobs should be accepted after this.
        queue.close()

        // This job should be ignored because the channel is closed
        queue.post {
            executionOrder.add(3)
        }

        testScheduler.advanceUntilIdle()

        // Verify that only the jobs posted before close() were executed
        assertThat(executionOrder).containsExactly(1, 2).inOrder()

        testScope.cancel()
    }
}