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
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages [Item] preferences through [InMemorySharedPreferences].
 *
 * Specify [copyRealPrefs] to have [backingPrefs] based on current preferences in storage.
 */
class InMemoryLauncherPrefs(context: Context, private val copyRealPrefs: Boolean = false) :
    LauncherPrefs(context) {

    private val backingPrefs = InMemorySharedPreferences()
    private val copiedPrefs = ConcurrentHashMap<SharedPreferences, Boolean>()

    override fun getSharedPrefs(item: Item): SharedPreferences {
        if (!copyRealPrefs) return backingPrefs

        val realPrefs = super.getSharedPrefs(item)
        copiedPrefs.computeIfAbsent(realPrefs) { _ ->
            backingPrefs.values +=
                realPrefs.all.filterValues { it != null }.mapValues { (_, v) -> checkNotNull(v) }
            true
        }

        return backingPrefs
    }
}

/** A [SharedPreferences] where the values are stored in memory instead of storage. */
private class InMemorySharedPreferences : SharedPreferences {

    val values = mutableMapOf<String, Any>()
    private val listeners = mutableSetOf<OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = values

    override fun getString(key: String, defValue: String?): String? = get(key, defValue)

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        return get(key, defValues)
    }

    override fun getInt(key: String, defValue: Int): Int = get(key, defValue)

    override fun getLong(key: String, defValue: Long): Long = get(key, defValue)

    override fun getFloat(key: String, defValue: Float): Float = get(key, defValue)

    override fun getBoolean(key: String, defValue: Boolean): Boolean = get(key, defValue)

    @Suppress("UNCHECKED_CAST")
    private fun <T> get(key: String, defValue: T): T = values[key] as? T ?: defValue

    override fun contains(key: String): Boolean = values.contains(key)

    override fun edit(): Editor = InMemorySharedPreferencesEditor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: OnSharedPreferenceChangeListener
    ) {
        listeners += listener
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: OnSharedPreferenceChangeListener
    ) {
        listeners -= listener
    }

    private inner class InMemorySharedPreferencesEditor : Editor {
        private var clear = false
        private val keysToRemove = mutableSetOf<String>()
        private val valuesToAdd = mutableMapOf<String, Any>()

        override fun putString(key: String, value: String?): Editor = put(key, value)

        override fun putStringSet(key: String, values: Set<String>?): Editor = put(key, values)

        override fun putInt(key: String, value: Int): Editor = put(key, value)

        override fun putLong(key: String, value: Long): Editor = put(key, value)

        override fun putFloat(key: String, value: Float): Editor = put(key, value)

        override fun putBoolean(key: String, value: Boolean): Editor = put(key, value)

        private fun put(key: String, value: Any?): Editor {
            if (value != null) valuesToAdd[key] = value else remove(key)
            return this
        }

        override fun remove(key: String): Editor = apply { keysToRemove += key }

        override fun clear(): Editor = apply { clear = true }

        override fun apply() {
            commit()
        }

        override fun commit(): Boolean {
            if (clear) values.clear()
            values -= keysToRemove
            values += valuesToAdd

            MAIN_EXECUTOR.execute {
                for (k in keysToRemove.union(valuesToAdd.keys)) {
                    for (l in listeners) {
                        l.onSharedPreferenceChanged(this@InMemorySharedPreferences, k)
                    }
                }
            }

            return clear || keysToRemove.isNotEmpty() || valuesToAdd.isNotEmpty()
        }
    }
}
