package ch.deletescape.lawnchair.theme

import android.content.Context
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.ensureOnMainThread
import ch.deletescape.lawnchair.runOnUiWorkerThread
import ch.deletescape.lawnchair.useApplicationContext
import ch.deletescape.lawnchair.util.SingletonHolder
import com.android.launcher3.Utilities
import java.util.HashSet

class ColorEngine private constructor(context: Context) : LawnchairPreferences.OnPreferenceChangeListener {
    private val KEY_ACCENT = "pref_accentColor"
    private val prefs by lazy { Utilities.getLawnchairPrefs(context) }
    private val accentListeners = HashSet<OnAccentChangeListener>()

    private var accent: Int = prefs.accentColor
    fun getAccent() = accent

    init {
        prefs.addOnPreferenceChangeListener(this, KEY_ACCENT)
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        when(key) {
            KEY_ACCENT -> {
                accent = prefs.accentColor
                runOnUiWorkerThread { accentListeners.forEach { it.onAccentChange(accent) } }
            }
        }
    }

    fun addAccentChangeListener(listener: OnAccentChangeListener) {
        accentListeners.add(listener)
        listener.onAccentChange(accent)
    }

    fun removeAccentChangeListener(listener: OnAccentChangeListener) = accentListeners.remove(listener)

    interface OnAccentChangeListener {
        fun onAccentChange (color: Int)
    }

    companion object : SingletonHolder<ColorEngine, Context>(ensureOnMainThread(useApplicationContext(::ColorEngine)))
}
