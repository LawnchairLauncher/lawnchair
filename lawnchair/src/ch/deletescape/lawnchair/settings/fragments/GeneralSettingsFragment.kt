package ch.deletescape.lawnchair.settings.fragments

import com.android.launcher3.R

class GeneralSettingsFragment : BasePreferenceFragment(R.xml.general_settings, true, true) {
    override val title: String
        get() = "General"
}