package ch.deletescape.lawnchair.settings.fragments

import com.android.launcher3.R

class DockSettingsFragment : BasePreferenceFragment(R.xml.dock_settings, false, true) {
    override val title: String
        get() = "Dock"
}