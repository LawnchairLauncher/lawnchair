/*
 * Copyright (C) 2023 The Android Open Source Project
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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.rule.TestStabilityRule
import java.util.concurrent.ExecutorService
import java.util.concurrent.locks.ReentrantLock
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Unit test for [CancellableTask] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class CancellableTaskTest {

    private lateinit var underTest: CancellableTask<Int>

    private val lock = ReentrantLock()
    private var result: Int = -1
    private var isTaskExecuted = false
    private var isCallbackExecuted = false

    @get:Rule(order = 0) val testStabilityRule = TestStabilityRule()

    @Before
    fun setup() {
        reset()
        submitJob()
    }

    private fun submitJob() {
        underTest =
            CancellableTask(
                {
                    isTaskExecuted = true
                    1
                },
                Executors.VIEW_PREINFLATION_EXECUTOR,
                {
                    isCallbackExecuted = true
                    result = it + 1
                }
            )
        Executors.UI_HELPER_EXECUTOR.execute(underTest)
    }

    @Test
    fun run_and_complete() {
        awaitAllExecutorCompleted()

        assertTrue("task should be executed", isTaskExecuted)
        assertTrue("callback should be executed", isCallbackExecuted)
        assertEquals(2, result)
    }

    @Test
    fun run_and_cancel_cancelTaskAndCallback() {
        awaitAllExecutorCompleted()
        reset()
        lock.lock()
        Executors.UI_HELPER_EXECUTOR.submit { lock.lock() }
        submitJob()

        underTest.cancel()

        lock.unlock() // unblock task on UI_HELPER_EXECUTOR
        awaitAllExecutorCompleted()
        assertFalse("task should not be executed.", isTaskExecuted)
        assertFalse("callback should not be executed.", isCallbackExecuted)
        assertEquals(0, result)
    }

    @Test
    fun run_and_cancel_cancelCallback() {
        awaitAllExecutorCompleted()
        reset()
        lock.lock()
        Executors.VIEW_PREINFLATION_EXECUTOR.submit { lock.lock() }
        submitJob()
        awaitExecutorCompleted(Executors.UI_HELPER_EXECUTOR)
        assertTrue("task should be executed.", isTaskExecuted)

        underTest.cancel()

        lock.unlock() // unblock callback on VIEW_PREINFLATION_EXECUTOR
        awaitExecutorCompleted(Executors.VIEW_PREINFLATION_EXECUTOR)
        assertFalse("callback should not be executed.", isCallbackExecuted)
        assertEquals(0, result)
    }

    @Test
    fun run_and_cancelAfterCompletion_executeAll() {
        awaitAllExecutorCompleted()

        underTest.cancel()

        assertTrue("task should be executed", isTaskExecuted)
        assertTrue("callback should be executed", isCallbackExecuted)
        assertEquals(2, result)
    }

    private fun awaitExecutorCompleted(executor: ExecutorService) {
        executor.submit<Any> { null }.get()
    }

    private fun awaitAllExecutorCompleted() {
        awaitExecutorCompleted(Executors.UI_HELPER_EXECUTOR)
        awaitExecutorCompleted(Executors.VIEW_PREINFLATION_EXECUTOR)
    }

    private fun reset() {
        result = 0
        isTaskExecuted = false
        isCallbackExecuted = false
    }
}
