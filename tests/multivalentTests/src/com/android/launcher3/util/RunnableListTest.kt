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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

@SmallTest
@RunWith(AndroidJUnit4::class)
class RunnableListTest {

    @Mock private lateinit var runnable1: Runnable
    @Mock private lateinit var runnable2: Runnable

    private val underTest = RunnableList()

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
    }

    @Test
    fun not_destroyedByDefault() {
        assertThat(underTest.isDestroyed).isFalse()
    }

    @Test
    fun add_and_run() {
        underTest.add(runnable1)
        underTest.add(runnable2)

        underTest.executeAllAndDestroy()

        verify(runnable1).run()
        verify(runnable2).run()
        assertThat(underTest.isDestroyed).isTrue()
    }

    @Test
    fun add_to_destroyed_runnableList_run_immediately() {
        underTest.executeAllAndDestroy()

        underTest.add(runnable1)

        verify(runnable1).run()
    }

    @Test
    fun second_executeAllAndDestroy_noOp() {
        underTest.executeAllAndDestroy()
        underTest.add(runnable1)
        reset(runnable1)

        underTest.executeAllAndDestroy()

        verifyNoMoreInteractions(runnable1)
    }

    @Test
    fun executeAllAndClear_run_not_destroy() {
        underTest.add(runnable1)
        underTest.add(runnable2)

        underTest.executeAllAndClear()

        verify(runnable1).run()
        verify(runnable2).run()
        assertThat(underTest.isDestroyed).isFalse()
    }

    @Test
    fun executeAllAndClear_not_destroy() {
        underTest.executeAllAndClear()
        underTest.add(runnable1)
        reset(runnable1)

        underTest.executeAllAndClear()

        verify(runnable1).run()
    }

    @Test
    fun remove_and_run_not_executed() {
        underTest.add(runnable1)
        underTest.add(runnable2)

        underTest.remove(runnable1)
        underTest.executeAllAndClear()

        verifyNoMoreInteractions(runnable1)
        verify(runnable2).run()
    }
}
