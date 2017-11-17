package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.os.Bundle
import android.os.Process
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import ch.deletescape.lawnchair.LauncherAppState
import ch.deletescape.lawnchair.MultiSelectRecyclerViewAdapter
import ch.deletescape.lawnchair.R
import ch.deletescape.lawnchair.compat.LauncherActivityInfoCompat
import ch.deletescape.lawnchair.compat.LauncherAppsCompat

class HiddenAppsFragment : Fragment(), MultiSelectRecyclerViewAdapter.ItemClickListener {

    private lateinit var installedApps: List<LauncherActivityInfoCompat>
    private lateinit var adapter: MultiSelectRecyclerViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_hidden_apps, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = view.context
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
        installedApps = getAppsList(context).apply { sortBy { it.label.toString() } }
        adapter = MultiSelectRecyclerViewAdapter(installedApps, this)
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        recyclerView.adapter = adapter
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        val hiddenApps = PreferenceProvider.getPreferences(context).hiddenAppsSet
        if (!hiddenApps.isEmpty()) {
            activity!!.title = hiddenApps.size.toString() + getString(R.string.hide_app_selected)
        } else {
            activity!!.title = getString(R.string.hidden_app)
        }
    }

    override fun onItemClicked(position: Int) {
        val title = adapter.toggleSelection(position, installedApps[position].componentName.flattenToString())
        activity!!.title = title
    }

    private fun getAppsList(context: Context?) =
            LauncherAppsCompat.getInstance(context).getActivityList(null, Process.myUserHandle())

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        return inflater.inflate(R.menu.menu_hide_apps, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_apply -> {
                adapter.addSelectionsToHideList(activity)
                LauncherAppState.getInstanceNoCreate().reloadAllApps()
                activity!!.onBackPressed()
                true
            }
            R.id.action_reset -> {
                activity!!.title = adapter.clearSelection()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}