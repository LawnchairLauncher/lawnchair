package app.lawnchair.preferences

sealed interface PreferenceChangeListener {
    fun onPreferenceChange()
}
