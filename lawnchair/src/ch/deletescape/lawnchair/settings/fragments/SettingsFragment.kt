package ch.deletescape.lawnchair.settings.fragments

import com.android.launcher3.R

class SettingsFragment : BasePreferenceFragment(R.xml.settings, true, false) {
    override val title: String
        get() = "Settings"
}