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

package com.android.launcher3.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ListenableRefTest {

    @Test
    fun update_listener_add_and_removed() {
        val executor = mutableListOf<Runnable>()
        val callback = mutableListOf<Int>()

        val listenableStream = MutableListenableStream<Int>()
        listenableStream
            .forEach(executor::add) { callback.add(it) }
            .use {
                listenableStream.dispatchValue(3)
                assertThat(executor).hasSize(1)
                assertThat(callback).isEmpty()
                executor[0].run()
                assertThat(callback).hasSize(1)
                assertThat(callback[0]).isEqualTo(3)

                listenableStream.dispatchValue(5)
                assertThat(executor).hasSize(2)
                executor[1].run()
                assertThat(callback).hasSize(2)
                assertThat(callback[1]).isEqualTo(5)
            }

        listenableStream.dispatchValue(8)

        // No new value was added
        assertThat(executor).hasSize(2)
    }

    @Test
    fun update_groups_multiple_executors() {
        val listenableStream = MutableListenableStream<Int>()

        val executor1 = mutableListOf<Runnable>()
        val callbacks1 = Array(5) { mutableListOf<Int>() }
        val executor1Actual = Executor { executor1.add(it) }
        val cleanups1 =
            callbacks1.map { callback ->
                listenableStream.forEach(executor1Actual) { callback.add(it) }
            }

        val executor2 = mutableListOf<Runnable>()
        val callbacks2 = Array(3) { mutableListOf<Int>() }
        val executor2Actual = Executor { executor2.add(it) }
        val cleanups2 =
            callbacks2.map { callback ->
                listenableStream.forEach(executor2Actual) { callback.add(it) }
            }

        listenableStream.dispatchValue(5)

        // Only on runnable was posted on each executor
        assertThat(executor1).hasSize(1)
        assertThat(executor2).hasSize(1)

        executor1[0].run()
        callbacks1.forEach {
            assertThat(it).hasSize(1)
            assertThat(it[0]).isEqualTo(5)
        }

        executor2[0].run()
        callbacks2.forEach {
            assertThat(it).hasSize(1)
            assertThat(it[0]).isEqualTo(5)
        }

        // Remove two listeners
        cleanups2[1].close()
        cleanups2[2].close()

        listenableStream.dispatchValue(5)
        assertThat(executor1).hasSize(2)
        assertThat(executor2).hasSize(2)

        executor2[1].run()
        assertThat(callbacks2[0]).hasSize(2)
        assertThat(callbacks2[1]).hasSize(1)
        assertThat(callbacks2[2]).hasSize(1)

        // Remove the last callback for executor2
        cleanups2[0].close()

        // No new callback pasted on executor2
        listenableStream.dispatchValue(7)
        assertThat(executor1).hasSize(3)
        assertThat(executor2).hasSize(2)
    }

    @Test
    fun dispatching_updates_value_reference() {
        val listenableRef = MutableListenableRef(3)
        assertThat(listenableRef.value).isEqualTo(3)

        listenableRef.dispatchValue(5)
        assertThat(listenableRef.value).isEqualTo(5)
    }
}
