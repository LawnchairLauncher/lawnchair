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

package com.android.wm.shell.bubbles.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit tests for [ReferenceCounter].
 *
 * Build/Install/Run:
 *  atest WMShellRobolectricTests:ReferenceCounterTest (on host)
 *  atest WMShellMultivalentTestsOnDevice:ReferenceCounterTest (on device)
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class ReferenceCounterTest {
    @Test
    fun testIncrementDecrement() {
        val referenceCounter = ReferenceCounter<Object>()
        val a = Object()
        val b = Object()

        Truth.assertThat(referenceCounter.hasReferences()).isFalse()

        referenceCounter.increment(a)
        Truth.assertThat(referenceCounter.mReferences.get(a)).isEqualTo(1)
        Truth.assertThat(referenceCounter.hasReferences()).isTrue()

        referenceCounter.increment(a, b)
        Truth.assertThat(referenceCounter.mReferences.get(a)).isEqualTo(2)
        Truth.assertThat(referenceCounter.mReferences.get(b)).isEqualTo(1)
        Truth.assertThat(referenceCounter.hasReferences()).isTrue()

        referenceCounter.decrement(a, b)
        Truth.assertThat(referenceCounter.mReferences.get(a)).isEqualTo(1)
        Truth.assertThat(referenceCounter.mReferences.get(b)).isEqualTo(0)
        Truth.assertThat(referenceCounter.hasReferences()).isTrue()

        referenceCounter.decrement(a)
        Truth.assertThat(referenceCounter.mReferences.get(a)).isEqualTo(0)
        Truth.assertThat(referenceCounter.hasReferences()).isFalse()
    }

    @Test
    fun testDecrementNonExisting() {
        val referenceCounter = ReferenceCounter<Object>()
        val a = Object()
        val b = Object()

        referenceCounter.increment(a)
        Assert.assertThrows(IllegalArgumentException::class.java) {
            referenceCounter.decrement(b)
        }
    }

    @Test
    fun testDecrementZeroCount() {
        val referenceCounter = ReferenceCounter<Object>()
        val a = Object()

        referenceCounter.increment(a)
        referenceCounter.decrement(a)
        Assert.assertThrows(IllegalArgumentException::class.java) {
            referenceCounter.decrement(a)
        }
    }

    @Test
    fun testForEach() {
        val referenceCounter = ReferenceCounter<Object>()
        val a = Object()
        val b = Object()
        val set = HashSet<Object>()

        referenceCounter.forEach { set.add(it) }
        Truth.assertThat(set).isEmpty()

        referenceCounter.increment(a)
        referenceCounter.forEach { set.add(it) }
        Truth.assertThat(set).hasSize(1)
        Truth.assertThat(set).contains(a)

        set.clear()
        referenceCounter.increment(a, b)
        referenceCounter.forEach { set.add(it) }
        Truth.assertThat(set).hasSize(2)
        Truth.assertThat(set).contains(a)
        Truth.assertThat(set).contains(b)

        set.clear()
        referenceCounter.decrement(a, b)
        referenceCounter.forEach { set.add(it) }
        Truth.assertThat(set).hasSize(2)
        Truth.assertThat(set).contains(a)
        Truth.assertThat(set).contains(b) // zero count items are retained.

        set.clear()
        referenceCounter.clear()
        referenceCounter.forEach { set.add(it) }
        Truth.assertThat(set).isEmpty()
    }
}
