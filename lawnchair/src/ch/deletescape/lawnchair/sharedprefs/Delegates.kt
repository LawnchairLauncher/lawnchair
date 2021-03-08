package ch.deletescape.lawnchair.sharedprefs

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