package ch.deletescape.lawnchair.settings.fragments

import com.android.launcher3.R

class AppDrawerSettingsFragment : BasePreferenceFragment(R.xml.app_drawer_settings, false, true) {
    override val title: String
        get() = "App Drawer"
}