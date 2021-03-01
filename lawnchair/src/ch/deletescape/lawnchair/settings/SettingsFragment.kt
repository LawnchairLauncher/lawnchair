package ch.deletescape.lawnchair.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.android.launcher3.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.lawnchair_preferences, rootKey)
    }

    // TODO: Migrate missing code from ch.deletescape.settings.ui.SettingsActivity.
}