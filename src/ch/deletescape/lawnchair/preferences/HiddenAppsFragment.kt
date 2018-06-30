package ch.deletescape.lawnchair.preferences

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import com.android.launcher3.R
import com.android.launcher3.Utilities

class HiddenAppsFragment : Fragment(), SelectableAppsAdapter.Callback {

    private lateinit var adapter: SelectableAppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return LayoutInflater.from(container!!.context).inflate(R.layout.preference_spring_recyclerview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = view.context
        val recyclerView = view.findViewById<RecyclerView>(R.id.list)
        adapter = SelectableAppsAdapter.ofProperty(context, Utilities.getLawnchairPrefs(context)::hiddenAppSet, this)
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        recyclerView.adapter = adapter
    }

    override fun onSelectionsChanged(newSize: Int) {
        if (newSize > 0) {
            activity!!.title = "$newSize${getString(R.string.hide_app_selected)}"
        } else {
            activity!!.title = getString(R.string.hidden_app)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        return inflater.inflate(R.menu.menu_hide_apps, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reset -> {
                adapter.clearSelection()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
