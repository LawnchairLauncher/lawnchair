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

package ch.deletescape.lawnchair.groups.ui

import android.content.Context
import android.os.Bundle
import android.support.annotation.Keep
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import ch.deletescape.lawnchair.*
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.groups.AppGroupsManager
import ch.deletescape.lawnchair.groups.DrawerFoldersAdapter
import ch.deletescape.lawnchair.groups.DrawerTabsAdapter
import ch.deletescape.lawnchair.groups.FlowerpotTabsAdapter
import com.android.launcher3.R
import kotlinx.android.synthetic.lawnchair.fragment_app_categorization.*

@Keep
class AppCategorizationFragment : Fragment(), LawnchairPreferences.OnPreferenceChangeListener {

    private val ourContext by lazy { activity as Context }
    private val prefs by lazy { ourContext.lawnchairPrefs }
    private val manager by lazy { prefs.appGroupsManager }

    private val accent by lazy { ColorEngine.getInstance(ourContext).accent }
    private lateinit var recyclerView: RecyclerView
    private var groupAdapter: AppGroupsAdapter<*, *>? = null
        set(value) {
            if (field != value) {
                field?.itemTouchHelper?.attachToRecyclerView(null)
                field?.saveChanges()
                field = value

                recyclerView.adapter = value?.also {
                    it.loadAppGroups()
                    it.itemTouchHelper.attachToRecyclerView(recyclerView)
                }
            }
        }
    private val drawerTabsAdapter by lazy { DrawerTabsAdapter(ourContext) }
    private val flowerpotTabsAdapter by lazy { FlowerpotTabsAdapter(ourContext) }
    private val drawerFoldersAdapter by lazy { DrawerFoldersAdapter(ourContext) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_app_categorization, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(ourContext)
        setupEnableToggle(enableToggle)
        setupStyleSection()
    }

    override fun onResume() {
        super.onResume()

        groupAdapter?.loadAppGroups()
        prefs.addOnPreferenceChangeListener("pref_appsCategorizationType", this)
    }

    override fun onPause() {
        super.onPause()

        groupAdapter?.saveChanges()
        prefs.removeOnPreferenceChangeListener("pref_appsCategorizationType", this)
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        updateGroupAdapter()
    }

    private fun updateGroupAdapter() {
        groupAdapter = when (manager.getEnabledType()) {
            AppGroupsManager.CategorizationType.Flowerpot -> flowerpotTabsAdapter
            AppGroupsManager.CategorizationType.Tabs -> drawerTabsAdapter
            AppGroupsManager.CategorizationType.Folders -> drawerFoldersAdapter
            else -> null
        }
    }

    private fun setupEnableToggle(enableToggle: View) {
        enableToggle.findViewById<ImageView>(android.R.id.icon).tintDrawable(accent)

        val switch = enableToggle.findViewById<Switch>(R.id.switchWidget)
        switch.applyColor(accent)
        val syncSwitch = {
            switch.isChecked = manager.categorizationEnabled
            updateGroupAdapter()
        }

        enableToggle.setOnClickListener {
            manager.categorizationEnabled = !manager.categorizationEnabled
            syncSwitch()
        }

        syncSwitch()
    }

    private fun setupStyleSection() {
        val title = styleHeader.findViewById<TextView>(android.R.id.title)
        title.setText(R.string.pref_appcategorization_style_text)
        title.setTextColor(ourContext.createDisabledColor(accent))

        (folderTypeItem as AppCategorizationTypeItem)
                .setup(AppGroupsManager.CategorizationType.Folders,
                        R.string.pref_appcategorization_folders_title,
                        R.string.pref_appcategorization_folders_summary)

        (tabTypeItem as AppCategorizationTypeItem)
                .setup(AppGroupsManager.CategorizationType.Tabs,
                        R.string.pref_appcategorization_tabs_title,
                        R.string.pref_appcategorization_tabs_summary)
    }
}
