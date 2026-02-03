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

package com.android.launcher3

import android.content.Context
import android.content.Context.MODE_PRIVATE
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.EncryptionType.DEVICE_PROTECTED
import com.android.launcher3.LauncherPrefs.Companion.BOOT_AWARE_PREFS_KEY
import com.android.launcher3.LauncherPrefs.Companion.nonRestorableItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_KEY_1 = "test_key_1"
private const val TEST_KEY_2 = "test_key_2"

private const val TEST_INT_1 = 1337
private const val TEST_INT_2 = 42
private const val TEST_FLOAT_1 = 9000.1f
private const val TEST_FLOAT_2 = 3.14f
private const val TEST_LONG_1 = 1L
private const val TEST_LONG_2 = 10L
private const val TEST_STRING_1 = "potato"
private const val TEST_STRING_2 = "toast"

private val TEST_BOOLEAN_ITEM = nonRestorableItem(TEST_KEY_1, false)
private val TEST_STRING_ITEM = nonRestorableItem(TEST_KEY_2, TEST_STRING_1, DEVICE_PROTECTED)
private val TEST_INT_ITEM = nonRestorableItem("1", TEST_INT_1)
private val TEST_FLOAT_ITEM = nonRestorableItem("2", TEST_FLOAT_1)
private val TEST_LONG_ITEM = nonRestorableItem("3", TEST_LONG_1)
private val TEST_SET_ITEM = nonRestorableItem("4", emptySet<String>())

@RunWith(AndroidJUnit4::class)
class InMemoryLauncherPrefsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = InMemoryLauncherPrefs(context)

    private var lastChangedKey: String? = null
    private val listener = LauncherPrefChangeListener { k -> lastChangedKey = k }

    @Test
    fun get_booleanItemNotInPrefs_defaultValue() {
        assertThat(prefs.get(TEST_BOOLEAN_ITEM)).isFalse()
    }

    @Test
    fun get_booleanItemInPrefs_prefValue() {
        prefs.put(TEST_BOOLEAN_ITEM, true)
        assertThat(prefs.get(TEST_BOOLEAN_ITEM)).isTrue()
    }

    @Test
    fun get_stringItemNotInPrefs_defaultValue() {
        assertThat(prefs.get(TEST_STRING_ITEM)).isEqualTo(TEST_STRING_1)
    }

    @Test
    fun get_stringItemInPrefs_prefValue() {
        prefs.put(TEST_STRING_ITEM, TEST_STRING_2)
        assertThat(prefs.get(TEST_STRING_ITEM)).isEqualTo(TEST_STRING_2)
    }

    @Test
    fun get_intItemNotInPrefs_defaultValue() {
        assertThat(prefs.get(TEST_INT_ITEM)).isEqualTo(TEST_INT_1)
    }

    @Test
    fun get_intItemInPrefs_prefValue() {
        prefs.put(TEST_INT_ITEM, TEST_INT_2)
        assertThat(prefs.get(TEST_INT_ITEM)).isEqualTo(TEST_INT_2)
    }

    @Test
    fun get_longItemNotInPrefs_defaultValue() {
        assertThat(prefs.get(TEST_LONG_ITEM)).isEqualTo(TEST_LONG_1)
    }

    @Test
    fun get_longItemInPrefs_prefValue() {
        prefs.put(TEST_LONG_ITEM, TEST_LONG_2)
        assertThat(prefs.get(TEST_LONG_ITEM)).isEqualTo(TEST_LONG_2)
    }

    @Test
    fun get_floatItemNotInPrefs_defaultValue() {
        assertThat(prefs.get(TEST_FLOAT_ITEM)).isEqualTo(TEST_FLOAT_1)
    }

    @Test
    fun get_floatItemInPrefs_prefValue() {
        prefs.put(TEST_FLOAT_ITEM, TEST_FLOAT_2)
        assertThat(prefs.get(TEST_FLOAT_ITEM)).isEqualTo(TEST_FLOAT_2)
    }

    @Test
    fun get_setItemNotInPrefs_defaultValue() {
        assertThat(prefs.get(TEST_SET_ITEM)).isEmpty()
    }

    @Test
    fun get_setItemInPrefs_prefValue() {
        prefs.put(TEST_SET_ITEM, setOf(TEST_STRING_1, TEST_STRING_2))
        assertThat(prefs.get(TEST_SET_ITEM)).containsExactly(TEST_STRING_1, TEST_STRING_2)
    }

    @Test
    fun has_itemNotInPrefs_false() {
        assertThat(prefs.has(TEST_BOOLEAN_ITEM)).isFalse()
    }

    @Test
    fun has_itemInPrefs_true() {
        prefs.put(TEST_BOOLEAN_ITEM, true)
        assertThat(prefs.has(TEST_BOOLEAN_ITEM)).isTrue()
    }

    @Test
    fun remove_removesPref() {
        prefs.put(TEST_STRING_ITEM, TEST_STRING_2)
        assertThat(prefs.get(TEST_STRING_ITEM)).isEqualTo(TEST_STRING_2)
        prefs.remove(TEST_STRING_ITEM)
        assertThat(prefs.get(TEST_STRING_ITEM)).isEqualTo(TEST_STRING_1)
    }

    @Test
    fun addListener_putItem_invokesListener() {
        prefs.addListener(listener, TEST_BOOLEAN_ITEM)
        getInstrumentation().runOnMainSync { prefs.put(TEST_BOOLEAN_ITEM, true) }
        assertThat(lastChangedKey).isEqualTo(TEST_KEY_1)
    }

    @Test
    fun removeListener_updateItem_listenerNotInvoked() {
        prefs.addListener(listener, TEST_BOOLEAN_ITEM)
        prefs.put(TEST_BOOLEAN_ITEM, true)

        lastChangedKey = null
        prefs.removeListener(listener, TEST_BOOLEAN_ITEM)
        getInstrumentation().runOnMainSync { prefs.put(TEST_BOOLEAN_ITEM, false) }
        assertThat(lastChangedKey).isNull()
    }

    @Test
    fun doNotCopyRealPrefs_valueCopied() {
        val prefs = InMemoryLauncherPrefs(context, copyRealPrefs = false)
        val realSharedPrefs =
            context
                .createDeviceProtectedStorageContext()
                .getSharedPreferences(BOOT_AWARE_PREFS_KEY, MODE_PRIVATE)

        realSharedPrefs.edit().putString(TEST_STRING_ITEM.sharedPrefKey, TEST_STRING_2).commit()
        assertThat(prefs.get(TEST_STRING_ITEM)).isEqualTo(TEST_STRING_1)
        realSharedPrefs.edit().remove(TEST_STRING_ITEM.sharedPrefKey).commit()
    }

    @Test
    fun copyRealPrefs_valueCopied() {
        val prefs = InMemoryLauncherPrefs(context, copyRealPrefs = true)
        val realSharedPrefs =
            context
                .createDeviceProtectedStorageContext()
                .getSharedPreferences(BOOT_AWARE_PREFS_KEY, MODE_PRIVATE)

        realSharedPrefs.edit().putString(TEST_STRING_ITEM.sharedPrefKey, TEST_STRING_2).commit()
        assertThat(prefs.get(TEST_STRING_ITEM)).isEqualTo(TEST_STRING_2)
        realSharedPrefs.edit().remove(TEST_STRING_ITEM.sharedPrefKey).commit()
    }
}
