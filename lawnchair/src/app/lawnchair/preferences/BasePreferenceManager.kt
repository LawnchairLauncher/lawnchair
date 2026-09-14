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
import androidx.core.content.edit
import app.lawnchair.font.FontCache
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherPrefs
import java.util.concurrent.CopyOnWriteArraySet
import org.json.JSONObject

sealed class BasePreferenceManager(val context: Context) : SharedPreferences.OnSharedPreferenceChangeListener {
    val sp: SharedPreferences = LauncherPrefs.getPrefs(context)
    val prefsMap = mutableMapOf<String, BasePref<*>>()

    private var changedPrefs: MutableSet<BasePref<*>>? = null
    private var editor: SharedPreferences.Editor? = null
    private var inBatchMode = false
        set(value) {
            if (field != value) {
                if (field) {
                    editor!!.apply()
                    editor = null
                    val tmp = changedPrefs?.let { HashSet(it) }
                    changedPrefs = null
                    if (tmp != null) {
                        tmp.forEach { it.invalidate() }
                        tmp.forEach { it.onSharedPreferenceChange() }
                    }
                }
                field = value
                if (field) {
                    editor = sp.edit()
                    changedPrefs = mutableSetOf()
                }
            }
        }
    private var activeBatchCount = 0
        set(value) {
            field = value
            inBatchMode = value > 0
        }

    fun migratePrefs(currentVersion: Int, block: (oldVersion: Int) -> Unit) {
        val oldVersion = sp.getInt("version", 9999)
        block(oldVersion)
        sp.edit {
            putInt("version", currentVersion)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val pref = prefsMap[key] ?: return
        val changedSet = changedPrefs
        if (changedSet != null) {
            changedSet.add(pref)
        } else {
            pref.invalidate()
            pref.onSharedPreferenceChange()
        }
    }

    fun batchEdit(block: () -> Unit) {
        try {
            activeBatchCount++
            block()
        } finally {
            activeBatchCount--
        }
    }

    fun editSp(block: SharedPreferences.Editor.() -> Unit) {
        if (inBatchMode) {
            block(editor!!)
        } else {
            sp.edit { block(this) }
        }
    }

    fun StringPref(key: String, defaultValue: String, primaryListener: ChangeListener? = null) =
        StringPref(this, key, defaultValue, primaryListener)

    fun BoolPref(key: String, defaultValue: Boolean, primaryListener: ChangeListener? = null) =
        BoolPref(this, key, defaultValue, primaryListener)

    fun IntPref(key: String, defaultValue: Int, primaryListener: ChangeListener? = null) =
        IntPref(this, key, defaultValue, primaryListener)

    fun FloatPref(key: String, defaultValue: Float, primaryListener: ChangeListener? = null) =
        FloatPref(this, key, defaultValue, primaryListener)

    fun StringSetPref(key: String, defaultValue: Set<String>, primaryListener: ChangeListener? = null) =
        StringSetPref(this, key, defaultValue, primaryListener)

    fun FontPref(key: String, defaultValue: FontCache.Font, primaryListener: ChangeListener? = null) =
        FontPref(this, key, defaultValue, primaryListener)

    fun <T> ObjectPref(
        key: String,
        defaultValue: T,
        parseFunc: (stringValue: String) -> T,
        stringifyFunc: (value: T) -> String,
        primaryListener: ChangeListener? = null,
    ) = ObjectPref(this, key, defaultValue, parseFunc, stringifyFunc, primaryListener)

    fun IdpIntPref(
        key: String,
        selectDefaultValue: InvariantDeviceProfile.GridOption.() -> Int,
        primaryListener: ChangeListener? = null,
    ) = IdpIntPref(this, key, selectDefaultValue, primaryListener)

    abstract class BasePref<T>(
        val manager: BasePreferenceManager,
        override val key: String,
        private val primaryListener: ChangeListener?,
    ) : PrefEntry<T> {
        protected var loaded = false
        private val listeners = CopyOnWriteArraySet<PreferenceChangeListener>()

        fun invalidate() {
            loaded = false
        }

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

    abstract class StringBasedPref<T>(
        manager: BasePreferenceManager,
        key: String,
        override val defaultValue: T,
        primaryListener: ChangeListener? = null,
    ) : BasePref<T>(manager, key, primaryListener) {
        private var currentValue: T? = null

        init {
            @Suppress("LeakingThis")
            manager.prefsMap[key] = this
        }

        @Suppress("UNCHECKED_CAST")
        override fun get(): T {
            if (!loaded) {
                currentValue = if (manager.sp.contains(key)) {
                    parse(manager.sp.getString(key, null)!!)
                } else {
                    defaultValue
                }
                loaded = true
            }
            return currentValue as T
        }

        override fun set(newValue: T) {
            currentValue = newValue
            manager.editSp { putString(key, stringify(newValue)) }
        }

        protected abstract fun parse(stringValue: String): T
        protected abstract fun stringify(value: T): String
    }

    open class StringPref(
        manager: BasePreferenceManager,
        key: String,
        defaultValue: String,
        primaryListener: ChangeListener? = null,
    ) : StringBasedPref<String>(manager, key, defaultValue, primaryListener) {
        override fun parse(stringValue: String) = stringValue
        override fun stringify(value: String) = value
    }

    open class BoolPref(
        manager: BasePreferenceManager,
        key: String,
        override val defaultValue: Boolean,
        primaryListener: ChangeListener? = null,
    ) : BasePref<Boolean>(manager, key, primaryListener) {
        private var currentValue = false

        init {
            manager.prefsMap[key] = this
        }

        override fun get(): Boolean {
            if (!loaded) {
                currentValue = manager.sp.getBoolean(key, defaultValue)
                loaded = true
            }
            return currentValue
        }

        override fun set(newValue: Boolean) {
            currentValue = newValue
            manager.editSp { putBoolean(key, newValue) }
        }
    }

    open class IntPref(
        manager: BasePreferenceManager,
        key: String,
        private val defaultValueInternal: Int,
        primaryListener: ChangeListener? = null,
    ) : BasePref<Int>(manager, key, primaryListener) {
        override val defaultValue = defaultValueInternal
        private var currentValue = 0

        init {
            @Suppress("LeakingThis")
            manager.prefsMap[key] = this
        }

        override fun get(): Int {
            if (!loaded) {
                currentValue = try {
                    manager.sp.getInt(key, defaultValueInternal)
                } catch (_: ClassCastException) {
                    manager.sp.getFloat(key, defaultValueInternal.toFloat()).toInt()
                }
                loaded = true
            }
            return currentValue
        }

        override fun set(newValue: Int) {
            currentValue = newValue
            manager.editSp { putInt(key, newValue) }
        }
    }

    open class IdpIntPref(
        manager: BasePreferenceManager,
        key: String,
        private val selectDefaultValue: InvariantDeviceProfile.GridOption.() -> Int,
        primaryListener: ChangeListener? = null,
    ) : IntPref(manager, key, -1, primaryListener) {
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

    open class FloatPref(
        manager: BasePreferenceManager,
        key: String,
        override val defaultValue: Float,
        primaryListener: ChangeListener? = null,
    ) : BasePref<Float>(manager, key, primaryListener) {
        private var currentValue = 0f

        init {
            manager.prefsMap[key] = this
        }

        override fun get(): Float {
            if (!loaded) {
                currentValue = manager.sp.getFloat(key, defaultValue)
                loaded = true
            }
            return currentValue
        }

        override fun set(newValue: Float) {
            currentValue = newValue
            manager.editSp { putFloat(key, newValue) }
        }
    }

    open class StringSetPref(
        manager: BasePreferenceManager,
        key: String,
        override val defaultValue: Set<String>,
        primaryListener: ChangeListener? = null,
    ) : BasePref<Set<String>>(manager, key, primaryListener) {
        private var currentValue = setOf<String>()

        init {
            manager.prefsMap[key] = this
        }

        override fun get(): Set<String> {
            if (!loaded) {
                currentValue = manager.sp.getStringSet(key, defaultValue)!!
                loaded = true
            }
            return currentValue
        }

        override fun set(newValue: Set<String>) {
            currentValue = newValue
            manager.editSp { putStringSet(key, newValue) }
        }
    }

    open class FontPref(
        manager: BasePreferenceManager,
        key: String,
        defaultValue: FontCache.Font,
        primaryListener: ChangeListener? = null,
    ) : StringBasedPref<FontCache.Font>(manager, key, defaultValue, primaryListener) {

        override fun parse(stringValue: String): FontCache.Font = runCatching {
            FontCache.Font.fromJsonString(manager.context, stringValue)
        }.getOrDefault(defaultValue)

        override fun stringify(value: FontCache.Font) = value.toJsonString()
    }

    open class ObjectPref<T>(
        manager: BasePreferenceManager,
        key: String,
        defaultValue: T,
        private val parseFunc: (stringValue: String) -> T,
        private val stringifyFunc: (value: T) -> String,
        primaryListener: ChangeListener? = null,
    ) : StringBasedPref<T>(manager, key, defaultValue, primaryListener) {

        override fun parse(stringValue: String) = parseFunc(stringValue)

        override fun stringify(value: T) = stringifyFunc(value)
    }

    abstract class MutableMapPref<K, V>(
        manager: BasePreferenceManager,
        key: String,
        primaryListener: ChangeListener? = null,
    ) : BasePref<Map<K, V>>(manager, key, primaryListener) {

        override val defaultValue = mapOf<K, V>()
        private val valueMap = mutableMapOf<K, V>()

        init {
            val obj = JSONObject(manager.sp.getString(key, "{}")!!)
            obj.keys().forEach {
                valueMap[unflattenKey(it)] = unflattenValue(obj.getString(it))
            }
            @Suppress("LeakingThis")
            manager.prefsMap[key] = this
        }

        override fun get() = HashMap(valueMap)

        override fun set(newValue: Map<K, V>) {
            throw NotImplementedError()
        }

        open fun flattenKey(key: K) = key.toString()
        abstract fun unflattenKey(key: String): K

        open fun flattenValue(value: V) = value.toString()
        abstract fun unflattenValue(value: String): V

        operator fun set(key: K, value: V?) {
            if (value != null) {
                valueMap[key] = value
            } else {
                valueMap.remove(key)
            }
            saveChanges()
        }

        private fun saveChanges() {
            val obj = JSONObject()
            valueMap.entries.forEach { obj.put(flattenKey(it.key), flattenValue(it.value)) }
            manager.editSp { putString(key, obj.toString()) }
        }

        operator fun get(key: K): V? {
            return valueMap[key]
        }

        fun clear() {
            valueMap.clear()
            saveChanges()
        }
    }
}
