package app.lawnchair.util.preferences

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class StringPrefDelegate(private val key: String, private val defValue: String) : ReadWriteProperty<PrefManager, String> {
    override fun setValue(thisRef: PrefManager, property: KProperty<*>, value: String) {
        with(thisRef.sp.edit()) {
            putString(key, value)
            apply()
        }
    }

    override fun getValue(thisRef: PrefManager, property: KProperty<*>): String {
        return thisRef.sp.getString(key, defValue)!!
    }
}

class BoolPrefDelegate(private val key: String, private val defValue: Boolean) : ReadWriteProperty<PrefManager, Boolean> {
    override fun setValue(thisRef: PrefManager, property: KProperty<*>, value: Boolean) {
        with(thisRef.sp.edit()) {
            putBoolean(key, value)
            apply()
        }
    }

    override fun getValue(thisRef: PrefManager, property: KProperty<*>): Boolean {
        return thisRef.sp.getBoolean(key, defValue)
    }
}

class IntPrefDelegate(private val key: String, private val defValue: Int) : ReadWriteProperty<PrefManager, Int> {
    override fun setValue(thisRef: PrefManager, property: KProperty<*>, value: Int) {
        with(thisRef.sp.edit()) {
            putInt(key, value)
            apply()
        }
    }

    override fun getValue(thisRef: PrefManager, property: KProperty<*>): Int {
        return thisRef.sp.getInt(key, defValue)
    }
}

class FloatPrefDelegate(private val key: String, private val defValue: Float) : ReadWriteProperty<PrefManager, Float> {
    override fun setValue(thisRef: PrefManager, property: KProperty<*>, value: Float) {
        with(thisRef.sp.edit()) {
            putFloat(key, value)
            apply()
        }
    }

    override fun getValue(thisRef: PrefManager, property: KProperty<*>): Float {
        return thisRef.sp.getFloat(key, defValue)
    }
}