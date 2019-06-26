/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.preferences

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Process
import android.os.ResultReceiver
import android.os.UserHandle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import ch.deletescape.lawnchair.LawnchairAppFilter
import ch.deletescape.lawnchair.groups.DrawerTabs
import ch.deletescape.lawnchair.settings.ui.SettingsActivity
import com.android.launcher3.AppFilter
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey

class SelectableAppsActivity : SettingsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun createLaunchFragment(intent: Intent): Fragment {
        return Fragment.instantiate(this, SelectionFragment::class.java.name, intent.extras)
    }

    override fun shouldUseLargeTitle(): Boolean {
        return false
    }

    override fun shouldShowSearch(): Boolean {
        return false
    }
    
    class SelectionFragment : RecyclerViewFragment(), SelectableAppsAdapter.Callback {
        
        private var selection: Set<String> = emptySet()
        private var changed = false

        override fun onRecyclerViewCreated(recyclerView: RecyclerView) {
            val arguments = arguments!!
            val isWork = if (arguments.containsKey(KEY_FILTER_IS_WORK))
                arguments.getBoolean(KEY_FILTER_IS_WORK) else null
            selection = HashSet(arguments.getStringArrayList(KEY_SELECTION))

            val context = recyclerView.context
            recyclerView.setHasFixedSize(true)
            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.adapter = SelectableAppsAdapter.ofProperty(activity!!,
                    ::selection, this, createAppFilter(context, DrawerTabs.getWorkFilter(isWork)))
        }

        override fun onDestroy() {
            super.onDestroy()
            
            val receiver = arguments!!.getParcelable(KEY_CALLBACK) as ResultReceiver
            if (changed) {
                receiver.send(Activity.RESULT_OK, Bundle(1).apply {
                    putStringArrayList(KEY_SELECTION, ArrayList(selection))
                })
            } else {
                receiver.send(Activity.RESULT_CANCELED, null)
            }
        }

        override fun onResume() {
            super.onResume()

            updateTitle(selection.size)
        }

        override fun onSelectionsChanged(newSize: Int) {
            changed = true
            updateTitle(newSize)
        }

        private fun updateTitle(size: Int) {
            activity?.title = getString(R.string.selected_count, size)
        }
    }

    companion object {
        
        private const val KEY_SELECTION = "selection"
        private const val KEY_CALLBACK = "callback"
        private const val KEY_FILTER_IS_WORK = "filterIsWork"

        fun start(context: Context, selection: Collection<ComponentKey>,
                  callback: (Collection<ComponentKey>?) -> Unit, filterIsWork: Boolean? = null) {
            val intent = Intent(context, SelectableAppsActivity::class.java).apply {
                putStringArrayListExtra(KEY_SELECTION, ArrayList(selection.map { it.toString() }))
                putExtra(KEY_CALLBACK, object : ResultReceiver(Handler()) {

                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        if (resultCode == Activity.RESULT_OK) {
                            callback(resultData!!.getStringArrayList(KEY_SELECTION)!!.map {
                                ComponentKey(context, it)
                            })
                        } else {
                            callback(null)
                        }
                    }
                })
                filterIsWork?.let { putExtra(KEY_FILTER_IS_WORK, it) }
            }
            context.startActivity(intent)
        }

        private fun createAppFilter(context: Context, predicate: (ComponentKey) -> Boolean): AppFilter {
            return object : AppFilter() {

                val base = LawnchairAppFilter(context)

                override fun shouldShowApp(app: ComponentName, user: UserHandle?): Boolean {
                    if (!base.shouldShowApp(app, user)) {
                        return false
                    }
                    return predicate(ComponentKey(app, user ?: Process.myUserHandle()))
                }
            }
        }
    }
}
