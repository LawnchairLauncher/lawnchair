package app.lawnchair.util.preferences

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class StringPreferenceDelegate(private val key: String, private val defValue: String) : ReadWriteProperty<PreferenceManager, String> {
    override fun setValue(thisRef: PreferenceManager, property: KProperty<*>, value: String) {
        with(thisRef.sp.edit()) {
            putString(key, value)
            apply()
        }
    }

    override fun getValue(thisRef: PreferenceManager, property: KProperty<*>): String {
        return thisRef.sp.getString(key, defValue)!!
    }
}

class BooleanPreferenceDelegate(private val key: String, private val defValue: Boolean) : ReadWriteProperty<PreferenceManager, Boolean> {
    override fun setValue(thisRef: PreferenceManager, property: KProperty<*>, value: Boolean) {
        with(thisRef.sp.edit()) {
            putBoolean(key, value)
            apply()
        }
    }

    override fun getValue(thisRef: PreferenceManager, property: KProperty<*>): Boolean {
        return thisRef.sp.getBoolean(key, defValue)
    }
}

class FloatPreferenceDelegate(private val key: String, private val defValue: Float) : ReadWriteProperty<PreferenceManager, Float> {
    override fun setValue(thisRef: PreferenceManager, property: KProperty<*>, value: Float) {
        with(thisRef.sp.edit()) {
            putFloat(key, value)
            apply()
        }
    }

    override fun getValue(thisRef: PreferenceManager, property: KProperty<*>): Float {
        return thisRef.sp.getFloat(key, defValue)
    }
}