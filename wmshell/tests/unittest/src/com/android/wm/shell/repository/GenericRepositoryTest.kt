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

package com.android.wm.shell.repository

import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [GenericRepository].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:GenericRepositoryTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class GenericRepositoryTest : ShellTestCase() {

    @Test
    fun `callback not invoked when item is not found and default is true`() {
        val repository = MemoryRepositoryImpl<FakeKey, FakeItem>()
        var returnedItem: FakeItem? = null
        val result =
            repository.executeOn(
                FakeKey(id1 = 0, id2 = ""),
                defaultResult = true
            ) { item ->
                returnedItem = item
                true
            }
        assertNull(returnedItem)
        assertTrue(result)
    }

    @Test
    fun `callback not invoked when item is not found and default is false`() {
        val repository = MemoryRepositoryImpl<FakeKey, FakeItem>()
        var returnedItem: FakeItem? = null
        val result =
            repository.executeOn(
                FakeKey(id1 = 0, id2 = "")
            ) { item ->
                returnedItem = item
                true
            }
        assertNull(returnedItem)
        assertFalse(result)
    }

    @Test
    fun `callback invoked when item is found and returns true`() {
        val repository = MemoryRepositoryImpl<FakeKey, FakeItem>().apply {
            insert(
                key = FakeKey(id1 = 1, id2 = "test"),
                item = FakeItem(value1 = 1, value2 = "item")
            )
        }
        var returnedItem: FakeItem? = null
        val result =
            repository.executeOn(
                FakeKey(id1 = 1, id2 = "test")
            ) { item ->
                returnedItem = item
                true
            }
        assertEquals(FakeItem(value1 = 1, value2 = "item"), returnedItem)
        assertTrue(result)
    }

    @Test
    fun `callback invoked when item is found and returns false`() {
        val repository = MemoryRepositoryImpl<FakeKey, FakeItem>().apply {
            insert(
                key = FakeKey(id1 = 1, id2 = "test"),
                item = FakeItem(value1 = 1, value2 = "item")
            )
        }
        var returnedItem: FakeItem? = null
        val result =
            repository.executeOn(
                FakeKey(id1 = 1, id2 = "test")
            ) { item ->
                returnedItem = item
                false
            }
        assertEquals(FakeItem(value1 = 1, value2 = "item"), returnedItem!!)
        assertFalse(result)
    }
}
