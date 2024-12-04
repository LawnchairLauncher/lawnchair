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
package com.android.launcher3

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.LauncherPrefs.Companion.BOOT_AWARE_PREFS_KEY
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith

private val TEST_BOOLEAN_ITEM = LauncherPrefs.nonRestorableItem("1", false)
private val TEST_STRING_ITEM = LauncherPrefs.nonRestorableItem("2", "( ͡❛ ͜ʖ ͡❛)")
private val TEST_INT_ITEM = LauncherPrefs.nonRestorableItem("3", -1)
private val TEST_CONTEXTUAL_ITEM =
    ContextualItem("4", true, { true }, EncryptionType.ENCRYPTED, Boolean::class.java)

private const val TEST_DEFAULT_VALUE = "default"
private const val TEST_PREF_KEY = "test_pref_key"

private const val WAIT_TIME_IN_SECONDS = 3L

@SmallTest
@RunWith(AndroidJUnit4::class)
class LauncherPrefsTest {

    private val context by lazy { InstrumentationRegistry.getInstrumentation().targetContext }
    private val launcherPrefs by lazy { LauncherPrefs.get(context) }

    @Test
    fun has_keyMissingFromLauncherPrefs_returnsFalse() {
        assertThat(launcherPrefs.has(TEST_BOOLEAN_ITEM)).isFalse()
    }

    @Test
    fun has_keyPresentInLauncherPrefs_returnsTrue() {
        with(launcherPrefs) {
            putSync(TEST_BOOLEAN_ITEM.to(TEST_BOOLEAN_ITEM.defaultValue))
            assertThat(has(TEST_BOOLEAN_ITEM)).isTrue()
            remove(TEST_BOOLEAN_ITEM)
        }
    }

    @Test
    fun addListener_listeningForStringItemUpdates_isCorrectlyNotifiedOfUpdates() {
        val latch = CountDownLatch(1)
        val listener = OnSharedPreferenceChangeListener { _, _ -> latch.countDown() }

        with(launcherPrefs) {
            putSync(TEST_STRING_ITEM.to(TEST_STRING_ITEM.defaultValue))
            addListener(listener, TEST_STRING_ITEM)
            putSync(TEST_STRING_ITEM.to(TEST_STRING_ITEM.defaultValue + "abc"))

            assertThat(latch.await(WAIT_TIME_IN_SECONDS, TimeUnit.SECONDS)).isTrue()
            remove(TEST_STRING_ITEM)
        }
    }

    @Test
    fun removeListener_previouslyListeningForStringItemUpdates_isNoLongerNotifiedOfUpdates() {
        val latch = CountDownLatch(1)
        val listener = OnSharedPreferenceChangeListener { _, _ -> latch.countDown() }

        with(launcherPrefs) {
            addListener(listener, TEST_STRING_ITEM)
            removeListener(listener, TEST_STRING_ITEM)
            putSync(TEST_STRING_ITEM.to(TEST_STRING_ITEM.defaultValue + "hello."))

            // latch will be still be 1 (and await will return false) if the listener was not called
            assertThat(latch.await(WAIT_TIME_IN_SECONDS, TimeUnit.SECONDS)).isFalse()
            remove(TEST_STRING_ITEM)
        }
    }

    @Test
    fun addListenerAndRemoveListener_forMultipleItems_bothWorkProperly() {
        var latch = CountDownLatch(3)
        val listener = OnSharedPreferenceChangeListener { _, _ -> latch.countDown() }

        with(launcherPrefs) {
            addListener(listener, TEST_INT_ITEM, TEST_STRING_ITEM, TEST_BOOLEAN_ITEM)
            putSync(
                TEST_INT_ITEM.to(TEST_INT_ITEM.defaultValue + 123),
                TEST_STRING_ITEM.to(TEST_STRING_ITEM.defaultValue + "abc"),
                TEST_BOOLEAN_ITEM.to(!TEST_BOOLEAN_ITEM.defaultValue)
            )
            assertThat(latch.await(WAIT_TIME_IN_SECONDS, TimeUnit.SECONDS)).isTrue()

            removeListener(listener, TEST_INT_ITEM, TEST_STRING_ITEM, TEST_BOOLEAN_ITEM)
            latch = CountDownLatch(1)
            putSync(
                TEST_INT_ITEM.to(TEST_INT_ITEM.defaultValue),
                TEST_STRING_ITEM.to(TEST_STRING_ITEM.defaultValue),
                TEST_BOOLEAN_ITEM.to(TEST_BOOLEAN_ITEM.defaultValue)
            )
            remove(TEST_INT_ITEM, TEST_STRING_ITEM, TEST_BOOLEAN_ITEM)

            assertThat(latch.await(WAIT_TIME_IN_SECONDS, TimeUnit.SECONDS)).isFalse()
        }
    }

    @Test
    fun get_booleanItemNotInLauncherprefs_returnsDefaultValue() {
        assertThat(launcherPrefs.get(TEST_BOOLEAN_ITEM)).isEqualTo(TEST_BOOLEAN_ITEM.defaultValue)
    }

    @Test
    fun get_stringItemNotInLauncherPrefs_returnsDefaultValue() {
        assertThat(launcherPrefs.get(TEST_STRING_ITEM)).isEqualTo(TEST_STRING_ITEM.defaultValue)
    }

    @Test
    fun get_intItemNotInLauncherprefs_returnsDefaultValue() {
        assertThat(launcherPrefs.get(TEST_INT_ITEM)).isEqualTo(TEST_INT_ITEM.defaultValue)
    }

    @Test
    fun put_storesItemInLauncherPrefs_successfully() {
        val notDefaultValue = !TEST_BOOLEAN_ITEM.defaultValue

        with(launcherPrefs) {
            putSync(TEST_BOOLEAN_ITEM.to(notDefaultValue))
            assertThat(get(TEST_BOOLEAN_ITEM)).isEqualTo(notDefaultValue)
            remove(TEST_BOOLEAN_ITEM)
        }
    }

    @Test
    fun put_storesListOfItemsInLauncherPrefs_successfully() {
        with(launcherPrefs) {
            putSync(
                TEST_STRING_ITEM.to(TEST_STRING_ITEM.defaultValue),
                TEST_INT_ITEM.to(TEST_INT_ITEM.defaultValue),
                TEST_BOOLEAN_ITEM.to(TEST_BOOLEAN_ITEM.defaultValue)
            )
            assertThat(has(TEST_BOOLEAN_ITEM, TEST_INT_ITEM, TEST_STRING_ITEM)).isTrue()
            remove(TEST_STRING_ITEM, TEST_INT_ITEM, TEST_BOOLEAN_ITEM)
        }
    }

    @Test
    fun remove_deletesItemFromLauncherPrefs_successfully() {
        val notDefaultValue = !TEST_BOOLEAN_ITEM.defaultValue

        with(launcherPrefs) {
            putSync(TEST_BOOLEAN_ITEM.to(notDefaultValue))
            remove(TEST_BOOLEAN_ITEM)
            assertThat(get(TEST_BOOLEAN_ITEM)).isEqualTo(TEST_BOOLEAN_ITEM.defaultValue)
        }
    }

    @Test
    fun get_contextualItem_returnsCorrectDefault() {
        assertThat(launcherPrefs.get(TEST_CONTEXTUAL_ITEM)).isTrue()
    }

    @Test
    fun getItemSharedPrefFile_forNonRestorableItem_isCorrect() {
        val nonRestorableItem = LauncherPrefs.nonRestorableItem(TEST_PREF_KEY, TEST_DEFAULT_VALUE)
        assertThat(nonRestorableItem.sharedPrefFile).isEqualTo(LauncherFiles.DEVICE_PREFERENCES_KEY)
    }

    @Test
    fun getItemSharedPrefFile_forBackedUpItem_isCorrect() {
        val backedUpItem = LauncherPrefs.backedUpItem(TEST_PREF_KEY, TEST_DEFAULT_VALUE)
        assertThat(backedUpItem.sharedPrefFile).isEqualTo(LauncherFiles.SHARED_PREFERENCES_KEY)
    }

    @Test
    fun put_bootAwareItem_updatesDeviceProtectedStorage() {
        val bootAwareItem =
            LauncherPrefs.backedUpItem(
                TEST_PREF_KEY,
                TEST_DEFAULT_VALUE,
                EncryptionType.DEVICE_PROTECTED
            )

        val bootAwarePrefs: SharedPreferences =
            context
                .createDeviceProtectedStorageContext()
                .getSharedPreferences(BOOT_AWARE_PREFS_KEY, Context.MODE_PRIVATE)
        bootAwarePrefs.edit().remove(bootAwareItem.sharedPrefKey).commit()

        launcherPrefs.putSync(bootAwareItem.to(bootAwareItem.defaultValue))
        assertThat(bootAwarePrefs.contains(bootAwareItem.sharedPrefKey)).isTrue()

        launcherPrefs.removeSync(bootAwareItem)
    }

    @Test
    fun remove_bootAwareItem_removesFromDeviceProtectedStorage() {
        val bootAwareItem =
            LauncherPrefs.backedUpItem(
                TEST_PREF_KEY,
                TEST_DEFAULT_VALUE,
                EncryptionType.DEVICE_PROTECTED
            )

        val bootAwarePrefs: SharedPreferences =
            context
                .createDeviceProtectedStorageContext()
                .getSharedPreferences(BOOT_AWARE_PREFS_KEY, Context.MODE_PRIVATE)

        bootAwarePrefs
            .edit()
            .putString(bootAwareItem.sharedPrefKey, bootAwareItem.defaultValue)
            .commit()

        launcherPrefs.removeSync(bootAwareItem)
        assertThat(bootAwarePrefs.contains(bootAwareItem.sharedPrefKey)).isFalse()
    }
}
