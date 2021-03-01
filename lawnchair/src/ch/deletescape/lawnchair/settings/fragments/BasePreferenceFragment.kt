package ch.deletescape.lawnchair.settings.fragments

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import ch.deletescape.lawnchair.settings.interfaces.TitledFragment
import com.android.launcher3.LauncherFiles
import com.google.android.material.transition.MaterialSharedAxis

abstract class BasePreferenceFragment(
    var preferenceResource: Int,
    var hasChild: Boolean,
    var hasParent: Boolean
) : PreferenceFragmentCompat(),
    TitledFragment {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(preferenceResource, rootKey)
        preferenceManager.sharedPreferencesName = LauncherFiles.SHARED_PREFERENCES_KEY

        if (hasChild) {
            exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
            reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
        }

        if (hasParent) {
            enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
            returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
        }
    }

}