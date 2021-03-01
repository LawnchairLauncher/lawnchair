package ch.deletescape.lawnchair.settings.fragments

import com.android.launcher3.R

// TODO: Migrate missing code from ch.deletescape.settings.ui.SettingsActivity.

class SettingsFragment : BasePreferenceFragment(R.xml.settings, true, false) {
    override val title: String
        get() = "Settings"
}