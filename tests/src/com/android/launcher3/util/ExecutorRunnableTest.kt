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

import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.launcher3.util.rule.TestStabilityRule
import java.util.concurrent.ExecutorService
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Unit test for [ExecutorRunnable] */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class ExecutorRunnableTest {

    private lateinit var underTest: ExecutorRunnable<Int>

    private var result: Int = -1
    private var isTaskExecuted = false
    private var isCallbackExecuted = false

    @get:Rule(order = 0) val testStabilityRule = TestStabilityRule()

    @Before
    fun setup() {
        reset()
        underTest =
            ExecutorRunnable.createAndExecute(
                Executors.UI_HELPER_EXECUTOR,
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
    }

    @Test
    fun run_and_complete() {
        awaitAllExecutorCompleted()

        assertTrue(isTaskExecuted)
        assertTrue(isCallbackExecuted)
        assertEquals(2, result)
    }

    @Test
    @TestStabilityRule.Stability(
        flavors = TestStabilityRule.LOCAL or TestStabilityRule.PLATFORM_POSTSUBMIT
    ) // b/316588649
    fun run_and_cancel_cancelCallback() {
        underTest.cancel(false)
        awaitAllExecutorCompleted()

        assertFalse(isCallbackExecuted)
        assertEquals(0, result)
    }

    @Test
    fun run_and_cancelAfterCompletion_executeAll() {
        awaitAllExecutorCompleted()

        underTest.cancel(false)

        assertTrue(isTaskExecuted)
        assertTrue(isCallbackExecuted)
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
