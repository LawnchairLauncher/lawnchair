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
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Utilities
import java.util.concurrent.CopyOnWriteArraySet

abstract class BasePreferenceManager(context: Context) : SharedPreferences.OnSharedPreferenceChangeListener {
    val sp: SharedPreferences = Utilities.getPrefs(context)
    val prefsMap = mutableMapOf<String, BasePref<*>>()

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String) {
        prefsMap[key]?.onSharedPreferenceChange()
    }

    private inline fun editSp(block: SharedPreferences.Editor.() -> Unit) {
        with(sp.edit()) {
            block(this)
            apply()
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

    inner class StringPref(
        key: String,
        override val defaultValue: String,
        primaryListener: ChangeListener? = null
    ) : BasePref<String>(key, primaryListener) {
        private var currentValue = ""

        init {
            prefsMap[key] = this
        }

        override fun get(): String {
            if (!loaded) {
                currentValue = sp.getString(key, defaultValue)!!
                loaded = true
            }
            return currentValue
        }

        override fun set(newValue: String) {
            currentValue = newValue
            editSp { putString(key, newValue) }
        }
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
}
