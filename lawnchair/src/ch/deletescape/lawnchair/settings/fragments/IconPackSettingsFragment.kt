package ch.deletescape.lawnchair.settings.fragments

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ch.deletescape.lawnchair.settings.adapters.IconPackAdapter
import ch.deletescape.lawnchair.settings.adapters.IconPackAdapter.IconPackInfo
import ch.deletescape.lawnchair.settings.interfaces.TitledFragment
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.google.android.material.transition.MaterialSharedAxis
import kotlin.collections.set


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
        val dataSet: ArrayList<IconPackInfo> = ArrayList()
        dataSet.add(IconPackAdapter.IconPackInfo("System Icons", "",
            context?.let { ContextCompat.getDrawable(it, R.drawable.ic_launcher_home) }))
        dataSet.addAll(loadAvailableIconPacks().values)
        val prefs = Utilities.getPrefs(context)
        recyclerView.adapter = IconPackAdapter(dataSet, prefs)
    }

    private fun loadAvailableIconPacks(): MutableMap<String,IconPackInfo> {
        val iconPacks: MutableMap<String, IconPackInfo> = HashMap()
        val list: MutableList<ResolveInfo>
        val pm = requireContext().packageManager
        list = pm.queryIntentActivities(Intent("com.novalauncher.THEME"), 0)
        list.addAll(pm.queryIntentActivities(Intent("org.adw.launcher.icons.ACTION_PICK_ICON"), 0))
        list.addAll(pm.queryIntentActivities(Intent("com.dlto.atom.launcher.THEME"), 0))
        list.addAll(pm.queryIntentActivities(Intent("android.intent.action.MAIN").addCategory("com.anddoes.launcher.THEME"), 0))
        for (info in list) {
            iconPacks[info.activityInfo.packageName] = IconPackInfo(info.loadLabel(pm).toString(), info.activityInfo.packageName, info.loadIcon(pm))
        }
        return iconPacks
    }
}