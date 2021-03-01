package ch.deletescape.lawnchair.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.android.launcher3.R
import com.google.android.material.transition.MaterialSharedAxis

// TODO: Migrate missing code from ch.deletescape.settings.ui.SettingsActivity.

class GeneralSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.general_settings, rootKey)

        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
    }
}