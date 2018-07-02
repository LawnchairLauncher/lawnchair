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
        entryValues = getProviders().toTypedArray()
        entries = entryValues.map { context.getString(displayNames[it]!!) }.toTypedArray()
    }

    fun getProviders(): List<String> {
        val list = ArrayList<String>()
        list.add(BlankDataProvider::class.java.name)
        if (Utilities.ATLEAST_NOUGAT)
            list.add(SmartspaceDataWidget::class.java.name)
        return list
    }

    override fun shouldDisableDependents(): Boolean {
        return super.shouldDisableDependents() && value != BlankDataProvider::class.java.name
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