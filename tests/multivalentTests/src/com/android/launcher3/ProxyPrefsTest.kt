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

import android.content.Context.MODE_PRIVATE
import android.platform.uiautomatorhelpers.DeviceHelpers.context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.LauncherPrefs.Companion.backedUpItem
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ProxyPrefsTest {

    private val prefName = "pref-test-" + UUID.randomUUID().toString()

    private val proxyPrefs by lazy {
        ProxyPrefs(
            context,
            context.getSharedPreferences(prefName, MODE_PRIVATE).apply { edit().clear().commit() },
        )
    }
    private val launcherPrefs by lazy { LauncherPrefs(context) }

    @After
    fun tearDown() {
        context.deleteSharedPreferences(prefName)
    }

    @Test
    fun `returns fallback value if present`() {
        launcherPrefs.putSync(TEST_ENTRY.to("new_value"))
        assertThat(proxyPrefs.get(TEST_ENTRY)).isEqualTo("new_value")
    }

    @Test
    fun `returns default value if not present`() {
        launcherPrefs.removeSync(TEST_ENTRY)
        assertThat(proxyPrefs.get(TEST_ENTRY)).isEqualTo("default_value")
    }

    @Test
    fun `returns overridden value if present`() {
        launcherPrefs.putSync(TEST_ENTRY.to("new_value"))
        proxyPrefs.putSync(TEST_ENTRY.to("overridden_value"))
        assertThat(proxyPrefs.get(TEST_ENTRY)).isEqualTo("overridden_value")
    }

    @Test
    fun `value not present when removed`() {
        launcherPrefs.putSync(TEST_ENTRY.to("new_value"))
        proxyPrefs.removeSync(TEST_ENTRY)
        assertThat(proxyPrefs.has(TEST_ENTRY)).isFalse()
    }

    @Test
    fun `returns default if removed`() {
        launcherPrefs.putSync(TEST_ENTRY.to("new_value"))
        proxyPrefs.removeSync(TEST_ENTRY)
        assertThat(proxyPrefs.get(TEST_ENTRY)).isEqualTo("default_value")
    }

    @Test
    fun `value present on init`() {
        launcherPrefs.putSync(TEST_ENTRY.to("new_value"))
        assertThat(proxyPrefs.has(TEST_ENTRY)).isTrue()
    }

    @Test
    fun `value absent on init`() {
        launcherPrefs.removeSync(TEST_ENTRY)
        assertThat(proxyPrefs.has(TEST_ENTRY)).isFalse()
    }

    companion object {

        val TEST_ENTRY =
            backedUpItem("test_prefs", "default_value", EncryptionType.DEVICE_PROTECTED)
    }
}
