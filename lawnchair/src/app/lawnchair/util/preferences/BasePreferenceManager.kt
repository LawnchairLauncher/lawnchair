package app.lawnchair.util.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.currentComposer
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Utilities
import java.lang.ClassCastException
import java.util.*
import kotlin.collections.HashMap

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

    interface PreferenceChangeListener {
        fun onPreferenceChange(pref: BasePref<*>)
    }

    abstract inner class BasePref<T>(val key: String, private val primaryListener: ChangeListener?) {
        protected var loaded = false
        private val listeners = WeakHashMap<PreferenceChangeListener, Boolean>()

        abstract fun get(): T
        abstract fun set(newValue: T)

        fun onSharedPreferenceChange() {
            loaded = false
            primaryListener?.invoke()
            HashMap(listeners).forEach { (listener, _) ->
                listener.onPreferenceChange(this)
            }
        }

        fun addListener(listener: PreferenceChangeListener) {
            listeners[listener] = true
        }

        fun removeListener(listener: PreferenceChangeListener) {
            listeners.remove(listener)
        }
    }

    inner class StringPref(
        key: String,
        private val defaultValue: String,
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
        private val defaultValue: Boolean,
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
        private val defaultValue: Int,
        primaryListener: ChangeListener? = null
    ) : BasePref<Int>(key, primaryListener) {
        private var currentValue = 0

        init {
            prefsMap[key] = this
        }

        override fun get(): Int {
            if (!loaded) {
                currentValue = try {
                    sp.getInt(key, defaultValue)
                } catch (e: ClassCastException) {
                    sp.getFloat(key, defaultValue.toFloat()).toInt()
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
        private val defaultValue: InvariantDeviceProfile.() -> Int,
        primaryListener: ChangeListener? = null
    ) : IntPref(key, -1, primaryListener) {
        fun get(idp: InvariantDeviceProfile): Int {
            val value = get()
            return if (value == -1) {
                defaultValue(idp)
            } else {
                value
            }
        }

        fun set(newValue: Int, idp: InvariantDeviceProfile) {
            if (newValue == defaultValue(idp)) {
                set(-1)
            } else {
                set(newValue)
            }
        }
    }

    inner class FloatPref(
        key: String,
        private val defaultValue: Float,
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
}

typealias ChangeListener = () -> Unit
