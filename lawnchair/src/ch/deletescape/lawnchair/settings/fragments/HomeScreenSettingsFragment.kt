package ch.deletescape.lawnchair.settings.fragments

import com.android.launcher3.R

class HomeScreenSettingsFragment : BasePreferenceFragment(R.xml.home_screen_settings, false, true) {
    override val title: String
        get() = "Home Screen"
}