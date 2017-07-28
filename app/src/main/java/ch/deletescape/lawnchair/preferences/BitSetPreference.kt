package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.preference.MultiSelectListPreference
import android.text.TextUtils
import android.util.AttributeSet
import ch.deletescape.lawnchair.R
import ch.deletescape.lawnchair.Utilities

class BitSetPreference(context: Context?, attrs: AttributeSet?) :
        MultiSelectListPreference(context, attrs) {

    val prefs = Utilities.getPrefs(context)
    val defaultValue = (1 shl 30) - 1
    var persistedInt: Int
        get() = prefs.getInt(key, defaultValue)
        set(value) {
            prefs.edit().putInt(key, value).apply()
        }

    override fun getPersistedStringSet(defaultReturnValue: MutableSet<String>?): MutableSet<String> {
        val bits = persistedInt
        val newValues = HashSet<String>()
        entryValues.forEach { it ->
            it as String
            val bit = Integer.parseInt(it)
            if (bits and bit > 0) {
                newValues.add(it)
            }
        }
        return newValues
    }

    fun updateSummary() {
        val bits = persistedInt
        val newEntries = HashSet<String>()
        entryValues.forEachIndexed { index, it ->
            it as String
            val bit = Integer.parseInt(it)
            if (bits and bit > 0) {
                newEntries.add(entries[index] as String)
            }
        }
        if (newEntries.size > 0) {
            summary = TextUtils.join(", ", newEntries)
        } else {
            setSummary(R.string.none)
        }
    }

    override fun persistStringSet(values: MutableSet<String>?): Boolean {
        if (values == null) return false
        var bits = 0
        values.forEach {
            val bit = Integer.parseInt(it)
            bits = bits or bit
        }
        persistedInt = bits
        updateSummary()
        return true
    }
}