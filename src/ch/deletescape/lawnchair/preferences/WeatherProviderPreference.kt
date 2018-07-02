package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.support.v7.preference.ListPreference
import android.util.AttributeSet
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.smartspace.BlankDataProvider
import ch.deletescape.lawnchair.smartspace.SmartspaceDataWidget
import com.android.launcher3.R
import com.android.launcher3.Utilities

class WeatherProviderPreference(context: Context, attrs: AttributeSet?)
    : ListPreference(context, attrs), LawnchairPreferences.OnPreferenceChangeListener {

    private val prefs = Utilities.getLawnchairPrefs(context)
    private val prefEntry = prefs::weatherProvider

    init {
        entryValues = listOf(
                BlankDataProvider::class.java.name,
                SmartspaceDataWidget::class.java.name).toTypedArray()
        entries = entryValues.map { context.getString(displayNames[it]!!) }.toTypedArray()
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        if (value != prefEntry.get()) {
            value = prefEntry.get()
        }
    }

    override fun onAttached() {
        super.onAttached()

        prefs.addOnPreferenceChangeListener("pref_smartspace_widget_provider", this)
    }

    override fun onDetached() {
        super.onDetached()

        prefs.removeOnPreferenceChangeListener("pref_smartspace_widget_provider", this)
    }

    override fun getPersistedString(defaultReturnValue: String?): String {
        return prefEntry.get()
    }

    override fun persistString(value: String?): Boolean {
        prefEntry.set(value ?: BlankDataProvider::class.java.name)
        return true
    }

    companion object {

        val displayNames = mapOf(
                Pair(BlankDataProvider::class.java.name, R.string.weather_provider_disabled),
                Pair(SmartspaceDataWidget::class.java.name, R.string.weather_provider_widget))
    }
}