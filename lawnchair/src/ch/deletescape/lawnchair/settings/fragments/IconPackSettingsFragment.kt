package ch.deletescape.lawnchair.settings.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import ch.deletescape.lawnchair.settings.interfaces.TitledFragment
import com.android.launcher3.R
import com.google.android.material.transition.MaterialSharedAxis

class IconPackSettingsFragment : Fragment(R.layout.icon_pack_settings_fragment), TitledFragment {
    override val title: String
        get() = "Icon Pack"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
    }
}