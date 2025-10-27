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

package com.android.launcher3

import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations

private val TEST_CONSTANT_ITEM = LauncherPrefs.nonRestorableItem("TEST_BOOLEAN_ITEM", false)

private val TEST_CONTEXTUAL_ITEM =
    ContextualItem(
        "TEST_CONTEXTUAL_ITEM",
        true,
        { false },
        EncryptionType.ENCRYPTED,
        Boolean::class.java,
    )

@RunWith(LauncherMultivalentJUnit::class)
class FakeLauncherPrefsTest {

    @Mock lateinit var lifeCycle: DaggerSingletonTracker
    private lateinit var launcherPrefs: FakeLauncherPrefs

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        launcherPrefs = FakeLauncherPrefs(getApplicationContext(), lifeCycle)
    }

    @Test
    fun testGet_constantItemNotInPrefs_returnsDefaultValue() {
        assertThat(launcherPrefs.get(TEST_CONSTANT_ITEM)).isFalse()
    }

    @Test
    fun testGet_constantItemInPrefs_returnsStoredValue() {
        launcherPrefs.put(TEST_CONSTANT_ITEM, true)
        assertThat(launcherPrefs.get(TEST_CONSTANT_ITEM)).isTrue()
    }

    @Test
    fun testGet_contextualItemNotInPrefs_returnsDefaultValue() {
        assertThat(launcherPrefs.get(TEST_CONTEXTUAL_ITEM)).isFalse()
    }

    @Test
    fun testGet_contextualItemInPrefs_returnsStoredValue() {
        launcherPrefs.put(TEST_CONTEXTUAL_ITEM, true)
        assertThat(launcherPrefs.get(TEST_CONTEXTUAL_ITEM)).isTrue()
    }

    @Test
    fun testPut_multipleItems_storesAll() {
        launcherPrefs.put(TEST_CONSTANT_ITEM to true, TEST_CONTEXTUAL_ITEM to true)
        assertThat(launcherPrefs.get(TEST_CONSTANT_ITEM)).isTrue()
        assertThat(launcherPrefs.get(TEST_CONTEXTUAL_ITEM)).isTrue()
    }

    @Test
    fun testHas_itemNotInPrefs_returnsFalse() {
        assertThat(launcherPrefs.has(TEST_CONSTANT_ITEM)).isFalse()
    }

    @Test
    fun testHas_itemInPrefs_returnsTrue() {
        launcherPrefs.put(TEST_CONSTANT_ITEM, true)
        assertThat(launcherPrefs.has(TEST_CONSTANT_ITEM)).isTrue()
    }

    @Test
    fun testHas_twoItemsWithOneInPrefs_returnsFalse() {
        launcherPrefs.put(TEST_CONSTANT_ITEM, true)
        assertThat(launcherPrefs.has(TEST_CONSTANT_ITEM, TEST_CONTEXTUAL_ITEM)).isFalse()
    }

    @Test
    fun testHas_twoItemsInPrefs_returnsTrue() {
        launcherPrefs.put(TEST_CONSTANT_ITEM to true, TEST_CONTEXTUAL_ITEM to true)
        assertThat(launcherPrefs.has(TEST_CONSTANT_ITEM, TEST_CONTEXTUAL_ITEM)).isTrue()
    }

    @Test
    fun testRemove_itemInPrefs_removesItem() {
        launcherPrefs.put(TEST_CONSTANT_ITEM, true)
        launcherPrefs.remove(TEST_CONSTANT_ITEM)
        assertThat(launcherPrefs.has(TEST_CONSTANT_ITEM)).isFalse()
    }

    @Test
    fun testRemove_itemsInPrefs_removesItems() {
        launcherPrefs.put(TEST_CONSTANT_ITEM to true, TEST_CONTEXTUAL_ITEM to true)
        launcherPrefs.remove(TEST_CONSTANT_ITEM, TEST_CONTEXTUAL_ITEM)
        assertThat(launcherPrefs.has(TEST_CONSTANT_ITEM, TEST_CONTEXTUAL_ITEM)).isFalse()
    }

    @Test
    fun testAddListener_changeItemInPrefs_callsListener() {
        var changedKey: String? = null
        launcherPrefs.addListener({ changedKey = it }, TEST_CONSTANT_ITEM)
        getInstrumentation().runOnMainSync { launcherPrefs.put(TEST_CONSTANT_ITEM, true) }
        assertThat(changedKey).isEqualTo(TEST_CONSTANT_ITEM.sharedPrefKey)
    }

    @Test
    fun testAddListener_removeItemFromPrefs_callsListener() {
        var changedKey: String? = null
        launcherPrefs.put(TEST_CONSTANT_ITEM, true)
        launcherPrefs.addListener({ changedKey = it }, TEST_CONSTANT_ITEM)

        getInstrumentation().runOnMainSync { launcherPrefs.remove(TEST_CONSTANT_ITEM) }
        assertThat(changedKey).isEqualTo(TEST_CONSTANT_ITEM.sharedPrefKey)
    }

    @Test
    fun testRemoveListener_changeItemInPrefs_doesNotCallListener() {
        var changedKey: String? = null
        val listener = LauncherPrefChangeListener { changedKey = it }
        launcherPrefs.addListener(listener, TEST_CONSTANT_ITEM)

        launcherPrefs.removeListener(listener, TEST_CONSTANT_ITEM)
        getInstrumentation().runOnMainSync { launcherPrefs.put(TEST_CONSTANT_ITEM, true) }
        assertThat(changedKey).isNull()
    }
}
