/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import app.lawnchair.font.FontCache
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Utilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet

abstract class BasePreferenceManager(private val context: Context) : SharedPreferences.OnSharedPreferenceChangeListener {
    val sp: SharedPreferences = Utilities.getPrefs(context)
    val prefsMap = mutableMapOf<String, BasePref<*>>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String) {
        prefsMap[key]?.onSharedPreferenceChange()
    }

    private inline fun editSp(crossinline block: SharedPreferences.Editor.() -> Unit) {
        coroutineScope.launch {
            with(sp.edit()) {
                block(this)
                apply()
            }
        }
    }

    abstract inner class BasePref<T>(val key: String, private val primaryListener: ChangeListener?) : PrefEntry<T> {
        protected var loaded = false
        private val listeners = CopyOnWriteArraySet<PreferenceChangeListener>()

        fun onSharedPreferenceChange() {
            loaded = false
            primaryListener?.invoke()
            listeners.forEach { listener ->
                listener.onPreferenceChange()
            }
        }

        override fun addListener(listener: PreferenceChangeListener) {
            listeners.add(listener)
        }

        override fun removeListener(listener: PreferenceChangeListener) {
            listeners.remove(listener)
        }
    }

    abstract inner class StringBasedPref<T>(
        key: String,
        override val defaultValue: T,
        primaryListener: ChangeListener? = null
    ) : BasePref<T>(key, primaryListener) {
        private var currentValue: T? = null

        init {
            prefsMap[key] = this
        }

        @Suppress("UNCHECKED_CAST")
        override fun get(): T {
            if (!loaded) {
                currentValue = if (sp.contains(key)) {
                    parse(sp.getString(key, null)!!)
                } else {
                    defaultValue
                }
                loaded = true
            }
            return currentValue as T
        }

        override fun set(newValue: T) {
            currentValue = newValue
            editSp { putString(key, stringify(newValue)) }
        }

        protected abstract fun parse(stringValue: String): T
        protected abstract fun stringify(value: T): String
    }

    inner class StringPref(
        key: String,
        defaultValue: String,
        primaryListener: ChangeListener? = null
    ) : StringBasedPref<String>(key, defaultValue, primaryListener) {
        override fun parse(stringValue: String) = stringValue
        override fun stringify(value: String) = value
    }

    inner class BoolPref(
        key: String,
        override val defaultValue: Boolean,
        primaryListener: ChangeListener? = null
    ) : BasePref<Boolean>(key, primaryListener) {
        private var currentValue = false

        init {
            prefsMap[key] = this
        }

        override fun get(): Boolean {
            if (!loaded) {
                currentValue = sp.getBoolean(key, defaultValue)
                loaded = true
            }
            return currentValue
        }

        override fun set(newValue: Boolean) {
            currentValue = newValue
            editSp { putBoolean(key, newValue) }
        }
    }

    open inner class IntPref(
        key: String,
        private val defaultValueInternal: Int,
        primaryListener: ChangeListener? = null
    ) : BasePref<Int>(key, primaryListener) {
        override val defaultValue = defaultValueInternal
        private var currentValue = 0

        init {
            prefsMap[key] = this
        }

        override fun get(): Int {
            if (!loaded) {
                currentValue = try {
                    sp.getInt(key, defaultValueInternal)
                } catch (e: ClassCastException) {
                    sp.getFloat(key, defaultValueInternal.toFloat()).toInt()
                }
                loaded = true
            }
            return currentValue
        }

        override fun set(newValue: Int) {
            currentValue = newValue
            editSp { putInt(key, newValue) }
        }
    }

    inner class IdpIntPref(
        key: String,
        private val selectDefaultValue: InvariantDeviceProfile.GridOption.() -> Int,
        primaryListener: ChangeListener? = null
    ) : IntPref(key, -1, primaryListener) {
        override val defaultValue: Int
            get() = error("unsupported")

        override fun get(): Int {
            error("unsupported")
        }

        override fun set(newValue: Int) {
            error("unsupported")
        }

        fun defaultValue(defaultGrid: InvariantDeviceProfile.GridOption): Int {
            return selectDefaultValue(defaultGrid)
        }

        fun get(defaultGrid: InvariantDeviceProfile.GridOption): Int {
            val value = super.get()
            return if (value == -1) {
                selectDefaultValue(defaultGrid)
            } else {
                value
            }
        }

        fun set(newValue: Int, defaultGrid: InvariantDeviceProfile.GridOption) {
            if (newValue == selectDefaultValue(defaultGrid)) {
                super.set(-1)
            } else {
                super.set(newValue)
            }
        }
    }

    inner class FloatPref(
        key: String,
        override val defaultValue: Float,
        primaryListener: ChangeListener? = null
    ) : BasePref<Float>(key, primaryListener) {
        private var currentValue = 0f

        init {
            prefsMap[key] = this
        }

        override fun get(): Float {
            if (!loaded) {
                currentValue = sp.getFloat(key, defaultValue)
                loaded = true
            }
            return currentValue
        }

        override fun set(newValue: Float) {
            currentValue = newValue
            editSp { putFloat(key, newValue) }
        }
    }

    inner class StringSetPref(
        key: String,
        override val defaultValue: Set<String>,
        primaryListener: ChangeListener? = null
    ) : BasePref<Set<String>>(key, primaryListener) {
        private var currentValue = setOf<String>()

        init {
            prefsMap[key] = this
        }

        override fun get(): Set<String> {
            if (!loaded) {
                currentValue = sp.getStringSet(key, defaultValue)!!
                loaded = true
            }
            return currentValue
        }

        override fun set(newValue: Set<String>) {
            currentValue = newValue
            editSp { putStringSet(key, newValue) }
        }
    }

    inner class FontPref(
        key: String,
        defaultValue: FontCache.Font,
        primaryListener: ChangeListener? = null
    ) : StringBasedPref<FontCache.Font>(key, defaultValue, primaryListener) {

        override fun parse(stringValue: String): FontCache.Font {
            try {
                return FontCache.Font.fromJsonString(context, stringValue)
            } catch (e: Exception) {
                Log.e("FontPref", "Failed to load font $stringValue", e)
            }
            return defaultValue
        }

        override fun stringify(value: FontCache.Font) = value.toJsonString()
    }
}
