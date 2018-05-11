package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.support.v14.preference.MultiSelectListPreference
import android.text.TextUtils
import android.util.AttributeSet
import ch.deletescape.lawnchair.R
import ch.deletescape.lawnchair.Utilities

class BitSetPreference(context: Context?, attrs: AttributeSet?) :
        MultiSelectListPreference(context, attrs) {

    val prefs = Utilities.getPrefs(context)
    val defaultValue = (1 shl 30) - 1
    var persistedInt: Int
        get() = prefs.getIntPref(key, defaultValue)
        set(value) {
            prefs.setIntPref(key, value)
        }

    override fun getPersistedStringSet(defaultReturnValue: MutableSet<String>?): MutableSet<String> {
        val bits = persistedInt
        return entryValues.fold(HashSet<String>(), {acc, i -> acc.apply {
            i as String
            if (bits and Integer.parseInt(i) != 0)
                acc += i
        }})
    }

    fun updateSummary() {
        val bits = persistedInt
        val newEntries = entryValues.foldIndexed(HashSet<String>(), {index, acc, i -> acc.apply {
            if (bits and Integer.parseInt(i as String) != 0)
                acc += entries[index] as String
        }})
        if (newEntries.size > 0) {
            summary = TextUtils.join(", ", newEntries)
        } else {
            setSummary(R.string.none)
        }
    }

    override fun persistStringSet(values: MutableSet<String>?): Boolean {
        if (values == null) return false
        persistedInt = values.fold(0, {acc, i -> acc or Integer.parseInt(i)})
        updateSummary()
        return true
    }
}