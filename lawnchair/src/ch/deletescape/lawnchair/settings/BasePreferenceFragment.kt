package ch.deletescape.lawnchair.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.android.launcher3.LauncherFiles
import com.google.android.material.transition.MaterialSharedAxis

interface TitledFragment {
    val title: String
}

abstract class BasePreferenceFragment(var preferenceResource: Int, var isTopLevelFragment: Boolean = false) : PreferenceFragmentCompat(), TitledFragment {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(preferenceResource, rootKey)
        preferenceManager.sharedPreferencesName = LauncherFiles.SHARED_PREFERENCES_KEY

        if (isTopLevelFragment) {
            exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
            reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
        } else {
            enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
            returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
        }
    }

}