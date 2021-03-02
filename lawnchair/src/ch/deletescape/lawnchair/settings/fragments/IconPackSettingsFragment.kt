package ch.deletescape.lawnchair.settings.fragments

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ch.deletescape.lawnchair.settings.adapters.IconPackAdapter
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView: RecyclerView = requireView().findViewById(R.id.icon_packs)
        recyclerView.layoutManager = LinearLayoutManager(context)

        // TODO: Add list entry for each installed icon pack. Also see `IconPackAdapter`.
        val dataSet: ArrayList<IconPackAdapter.IconPackInfo> = ArrayList()
        dataSet.add(IconPackAdapter.IconPackInfo("System Icons", "",
            context?.let { ContextCompat.getDrawable(it, R.drawable.ic_launcher_home) }))
        dataSet.add(IconPackAdapter.IconPackInfo("System Icons 2", "a",
            context?.let { ContextCompat.getDrawable(it, R.drawable.ic_launcher_home) }))

        recyclerView.adapter = IconPackAdapter(dataSet)
    }

}