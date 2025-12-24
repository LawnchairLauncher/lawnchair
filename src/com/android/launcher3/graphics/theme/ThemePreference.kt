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

import androidx.annotation.VisibleForTesting
import com.android.launcher3.ConstantItem
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.backedUpItem
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.theme.MonoIconThemeFactory.MONO_FACTORY_ID
import com.android.launcher3.graphics.theme.MonoIconThemeFactory.MONO_THEME_CONTROLLER
import com.android.launcher3.graphics.theme.ThemePreference.ThemeValue
import com.android.launcher3.util.ListenableRef
import com.android.launcher3.util.MutableListenableRef
import javax.inject.Inject
import javax.inject.Named

/**
 * A wrapper class over LauncherPreference specific to theme preferences
 *
 * Originally Launcher had multiple shared prefs (boolean for monochrome, string for extensions) and
 * the priority order was spread across the code. This unifies those and exposes a single string.
 */
@LauncherAppSingleton
class ThemePreference
private constructor(
    private val prefs: LauncherPrefs,
    legacyThemeKeys: Map<String, ConstantItem<String>>,
    private val themePref: MutableListenableRef<ThemeValue?>,
) : ListenableRef<ThemeValue?> by themePref {

    @Inject
    constructor(
        prefs: LauncherPrefs,
        @Named(THEME_OVERRIDES_DAGGER_KEY) legacyThemeKeys: Map<String, ConstantItem<String>>,
    ) : this(prefs, legacyThemeKeys, MutableListenableRef(null))

    init {
        // Migrate old preferences
        val oldValue: ThemeValue? = run {
            legacyThemeKeys.forEach {
                val value = prefs.get(it.value)
                if (value.isNotEmpty()) {
                    return@run ThemeValue(it.key, value)
                }
            }
            // Check the monochrome setting
            if (prefs.get(LEGACY_MONO_THEME_ICON)) MONO_THEME_VALUE else null
        }

        var currentValue = parsePrefValue(prefs.get(THEME_ID))
        if (currentValue == null && oldValue != null) {
            prefs.put(THEME_ID, oldValue.toString())
            currentValue = oldValue
        }
        // Delete old keys from preference, after the one-time migration is complete
        if (oldValue != null)
            prefs.remove(*(legacyThemeKeys.values + LEGACY_MONO_THEME_ICON).toTypedArray())
        themePref.dispatchValue(currentValue)
    }

    /**
     * Updates the launcher theme id atomically, if the [predicate] returns true for the current
     * value
     */
    @JvmOverloads
    fun setValue(value: ThemeValue?, predicate: (ThemeValue?) -> Boolean = { it != value }) {
        synchronized(themePref) {
            if (predicate.invoke(themePref.value)) {
                prefs.put(THEME_ID, value?.toString() ?: "")
                themePref.dispatchValue(value)
            }
        }
    }

    data class ThemeValue(val factoryId: String, val themeId: String) {

        override fun toString() = "$factoryId:$themeId"
    }

    companion object {

        const val THEME_OVERRIDES_DAGGER_KEY = "THEME_OVERRIDES"

        @JvmField val MONO_THEME_VALUE = ThemeValue(MONO_FACTORY_ID, MONO_THEME_CONTROLLER.themeID)

        private const val KEY_ICON_THEME = "icon_theme_id"
        private val THEME_ID = backedUpItem(KEY_ICON_THEME, "")

        @VisibleForTesting val LEGACY_MONO_THEME_ICON = backedUpItem("themed_icons", false)

        @VisibleForTesting
        fun parsePrefValue(value: String): ThemeValue? {
            val colonIndex = value.indexOf(':')
            if (colonIndex == -1) return null
            return ThemeValue(value.substring(0, colonIndex), value.substring(colonIndex + 1))
        }
    }
}
