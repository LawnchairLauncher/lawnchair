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

package com.android.launcher3.graphics.theme

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.ConstantItem
import com.android.launcher3.InMemoryLauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.backedUpItem
import com.android.launcher3.graphics.theme.ThemePreference.Companion.LEGACY_MONO_THEME_ICON
import com.android.launcher3.graphics.theme.ThemePreference.Companion.MONO_THEME_VALUE
import com.android.launcher3.graphics.theme.ThemePreference.Companion.parsePrefValue
import com.android.launcher3.graphics.theme.ThemePreference.ThemeValue
import com.android.launcher3.util.SandboxApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ThemePreferenceTest {

    @get:Rule val context = SandboxApplication()

    private val prefs = InMemoryLauncherPrefs(context)

    private fun getThemePref(legacyThemeKeys: Map<String, ConstantItem<String>> = emptyMap()) =
        ThemePreference(prefs, legacyThemeKeys)

    @Test
    fun themeValue_correctly_parsed() {
        val value = ThemeValue("alpha", "beta")
        assertEquals(value, parsePrefValue(value.toString()))
    }

    @Test
    fun theme_pref_persisted() {
        getThemePref().setValue(MONO_THEME_VALUE)
        assertEquals(MONO_THEME_VALUE, getThemePref().value)

        getThemePref().setValue(ThemeValue("alpha", "beta"))
        assertEquals(ThemeValue("alpha", "beta"), getThemePref().value)

        getThemePref().setValue(null)
        assertNull(getThemePref().value)
    }

    @Test
    fun legacy_mono_true_value_properly_migrated() {
        prefs.put(LEGACY_MONO_THEME_ICON.to(true))
        assertEquals(MONO_THEME_VALUE, getThemePref().value)

        // Legacy key removed
        assertFalse(prefs.has(LEGACY_MONO_THEME_ICON))

        // New value is persisted in prefs
        assertEquals(MONO_THEME_VALUE, getThemePref().value)
    }

    @Test
    fun legacy_mono_false_value_properly_migrated() {
        prefs.put(LEGACY_MONO_THEME_ICON.to(false))
        assertNull(getThemePref().value)
    }

    @Test
    fun extra_legacy_keys_migrated() {
        val extraPref = backedUpItem("extra_pref", "")
        prefs.put(extraPref.to("extra_value"))

        val themePrefs = getThemePref(mapOf("my_key" to extraPref))
        assertEquals(ThemeValue("my_key", "extra_value"), themePrefs.value)

        // Extra key removed
        assertFalse(prefs.has(extraPref))

        // Value persisted
        assertEquals(ThemeValue("my_key", "extra_value"), getThemePref().value)
    }

    @Test
    fun extra_legacy_keys_supersedes_mono_migrated() {
        val extraPref = backedUpItem("extra_pref", "")
        prefs.put(extraPref.to("extra_value"), LEGACY_MONO_THEME_ICON.to(true))

        val themePrefs = getThemePref(mapOf("my_key" to extraPref))
        assertEquals(ThemeValue("my_key", "extra_value"), themePrefs.value)

        // Extra key removed
        assertFalse(prefs.has(extraPref))
        assertFalse(prefs.has(LEGACY_MONO_THEME_ICON))

        // Value persisted
        assertEquals(ThemeValue("my_key", "extra_value"), getThemePref().value)
    }
}
