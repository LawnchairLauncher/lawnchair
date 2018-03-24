package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.os.Bundle
import android.os.Process
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.*
import ch.deletescape.lawnchair.HiddenAppsAdapter
import com.android.launcher3.R
import com.android.launcher3.compat.LauncherAppsCompat
import com.google.android.apps.nexuslauncher.CustomAppFilter

class HiddenAppsFragment : Fragment(), HiddenAppsAdapter.Callback {

    private lateinit var installedApps: List<LauncherActivityInfo>
    private lateinit var adapter: HiddenAppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return RecyclerView(container!!.context)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = view.context
        val recyclerView = view as RecyclerView
        installedApps = getAppsList(context).apply { sortBy { it.label.toString().toLowerCase() } }
        adapter = HiddenAppsAdapter(view.context, installedApps, this)
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        recyclerView.adapter = adapter
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        val hiddenApps = CustomAppFilter.getHiddenApps(context)
        if (!hiddenApps.isEmpty()) {
            hiddenApps.forEach { Log.d("HiddenAppsFragment", it) }
            activity!!.title = hiddenApps.size.toString() + getString(R.string.hide_app_selected)
        } else {
            activity!!.title = getString(R.string.hidden_app)
        }
    }

    override fun setTitle(newTitle: String) {
        activity!!.title = newTitle
    }

    private fun getAppsList(context: Context?) =
            LauncherAppsCompat.getInstance(context).getActivityList(null, Process.myUserHandle())

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        return inflater.inflate(R.menu.menu_hide_apps, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reset -> {
                activity!!.title = adapter.clearSelection()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}