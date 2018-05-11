package ch.deletescape.lawnchair.util

import android.content.SharedPreferences
import android.support.v7.preference.PreferenceDataStore

class SharedPreferenceDataStore(private val prefs: SharedPreferences) : PreferenceDataStore() {

    override fun putString(key: String, value: String?) {
        edit { putString(key, value) }
    }

    override fun putStringSet(key: String, values: MutableSet<String>?) {
        edit { putStringSet(key, values) }
    }

    override fun putInt(key: String, value: Int) {
        edit { putInt(key, value) }
    }

    override fun putLong(key: String, value: Long) {
        edit { putLong(key, value) }
    }

    override fun putFloat(key: String, value: Float) {
        edit { putFloat(key, value) }
    }

    override fun putBoolean(key: String, value: Boolean) {
        edit { putBoolean(key, value) }
    }

    override fun getString(key: String, defValue: String?)
        = prefs.getString(key, defValue)

    override fun getStringSet(key: String, defValues: MutableSet<String>?)
        = prefs.getStringSet(key, defValues)

    override fun getInt(key: String, defValue: Int)
        = prefs.getInt(key, defValue)

    override fun getLong(key: String, defValue: Long)
        = prefs.getLong(key, defValue)

    override fun getFloat(key: String, defValue: Float)
        = prefs.getFloat(key, defValue)

    override fun getBoolean(key: String, defValue: Boolean)
        = prefs.getBoolean(key, defValue)

    private inline fun edit(body: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(body).apply()
    }
}
