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
import com.android.wm.shell.util.FakeLogger
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests for [MemoryRepositoryImpl].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:MemoryRepositoryImplTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class MemoryRepositoryImplTest : ShellTestCase() {

    private lateinit var repository: MemoryRepositoryImpl<FakeKey, FakeItem>
    private lateinit var fakeLogger: FakeLogger

    @Before
    fun setUp() {
        fakeLogger = FakeLogger()
        repository = MemoryRepositoryImpl(fakeLogger.logger)
    }

    @Test
    fun `itemFactory invoked when item not present`() {
        val itemFactory = FakeItemFactory(0, "test")

        // The item is initially not present.
        assertTrue(repository.insert(FakeKey(1, "test1"), itemFactory))
        assertTrue(itemFactory.fakeFactoryInvokedTimes == 1)

        // The item is then present.
        assertNotNull(repository.find(FakeKey(1, "test1")))

        // Insert again with overrideIfPresent == true so the factory is invoked again.
        assertTrue(repository.insert(FakeKey(1, "test1"), itemFactory))
        assertTrue(itemFactory.fakeFactoryInvokedTimes == 2)

        // Insert again with overrideIfPresent == false so the factory is not invoked again and
        // the item not inserted.
        assertFalse(repository.insert(FakeKey(1, "test1"), itemFactory, overrideIfPresent = false))
        assertTrue(itemFactory.fakeFactoryInvokedTimes == 2)
    }

    @Test
    fun `find returns the item when present`() {
        val item = FakeItem(0, "test")

        // The item is initially not present.
        assertNull(repository.find(FakeKey(1, "test1")))
        assertTrue(repository.insert(FakeKey(1, "test1"), item))

        // The item is then present.
        assertNotNull(repository.find(FakeKey(1, "test1")))
        assertEquals(item, repository.find(FakeKey(1, "test1")))
    }

    @Test
    fun `delete removes the item and returns if the item was present`() {
        val item = FakeItem(0, "test")

        // The item cannot be removed for a not existing key.
        assertFalse(repository.delete(FakeKey(1, "test1")))

        // We insert the item.
        assertTrue(repository.insert(FakeKey(1, "test1"), item))
        assertNotNull(repository.find(FakeKey(1, "test1")))

        // We try to remove a different key and the item is still present.
        assertFalse(repository.delete(FakeKey(0, "test1")))
        assertNotNull(repository.find(FakeKey(1, "test1")))

        // We remove the existing key and the item is not present.
        assertTrue(repository.delete(FakeKey(1, "test1")))
        assertNull(repository.find(FakeKey(1, "test1")))
    }

    @Test
    fun `insert adds the items and return the right value`() {
        val item1 = FakeItem(1, "test1")
        val item2 = FakeItem(2, "test2")

        // Item is not present
        assertNull(repository.find(FakeKey(1, "test1")))

        // Insert when the item is not present. Then it's added.
        assertTrue(repository.insert(FakeKey(1, "test1"), item1))
        assertNotNull(repository.find(FakeKey(1, "test1")))
        assertEquals(item1, repository.find(FakeKey(1, "test1")))

        // Insert when the item is already present and overrideIfPresent is true by default.
        assertTrue(repository.insert(FakeKey(1, "test1"), item2))
        assertNotNull(repository.find(FakeKey(1, "test1")))
        assertEquals(item2, repository.find(FakeKey(1, "test1")))

        // Insert when the item is already present but overrideIfPresent is false.
        assertFalse(
            repository.insert(FakeKey(1, "test1"), item1, overrideIfPresent = false)
        )
        assertNotNull(repository.find(FakeKey(1, "test1")))
        assertEquals(item2, repository.find(FakeKey(1, "test1")))
    }

    @Test
    fun `Predicate is invoked on items`() {
        val item1 = FakeItem(1, "test1")
        val item2 = FakeItem(2, "test2")

        repository.insert(FakeKey(1, "test1"), item1)
        repository.insert(FakeKey(1, "test2"), item2)

        assertTrue(repository.find { key, _ -> key.id1 == 1 }.size == 2)
        assertTrue(repository.find { key, _ -> key.id2 == "test2" }.size == 1)
        assertTrue(repository.find { key, _ -> key.id1 == 2 && key.id2 == "test" }.isEmpty())
    }

    @Test
    fun `Item is not updated if not present`() {
        val result = repository.update(FakeKey(id1 = 1, id2 = "test")) { item ->
            fail("This should not be invoked")
        }
        assertFalse(result)
    }

    @Test
    fun `Item is updated if present`() {
        val item1 = FakeItem(1, "test1")
        val item2 = FakeItem(2, "test2")
        repository.insert(FakeKey(id1 = 1, id2 = "test"), item1)

        val result = repository.update(FakeKey(id1 = 1, id2 = "test")) { item ->
            assert(item == item1)
            item2
        }
        assertTrue(result)
        assertTrue(repository.find { key, _ -> key.id1 == 1 }.size == 1)
        assertTrue(repository.find { key, _ -> key.id1 == 1 }[0] == item2)
    }

    @Test
    fun `Logger is used on dump`() {
        fakeLogger.assertLoggerInvocation(0)
        repository.dump()
        fakeLogger.assertLoggerInvocation(1)
    }
}
