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

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import ch.deletescape.lawnchair.applyColor
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.settings.DrawerTabs
import ch.deletescape.lawnchair.settings.ui.SettingsBottomSheetDialog
import ch.deletescape.lawnchair.tintDrawable
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey

class DrawerTabEditBottomSheet(context: Context, private var config: TabConfig,
                               private val callback: (Boolean) -> Unit) : FrameLayout(context), View.OnClickListener {

    private val tabName by lazy { findViewById<TextView>(R.id.name) }
    private val appsCount by lazy { findViewById<TextView>(R.id.apps_count) }
    private val hideSwitch by lazy { findViewById<Switch>(R.id.hide_switch) }

    init {
        View.inflate(context, R.layout.drawer_tab_edit_bottom_sheet, this)
        tabName.text = config.title
        hideSwitch.isChecked = config.hideFromMain

        val accent = ColorEngine.getInstance(context).accent

        findViewById<TextView>(R.id.name_label).setTextColor(accent)

        findViewById<View>(R.id.hide_toggle).setOnClickListener(this)
        findViewById<View>(R.id.manage_apps).setOnClickListener(this)
        findViewById<TextView>(R.id.save).apply {
            setOnClickListener(this@DrawerTabEditBottomSheet)
            setTextColor(accent)
        }
        findViewById<View>(R.id.cancel).setOnClickListener(this)

        findViewById<ImageView>(R.id.hide_icon).tintDrawable(accent)
        findViewById<ImageView>(R.id.manage_apps_icon).tintDrawable(accent)

        hideSwitch.applyColor(accent)

        updateSummary()
    }

    private fun updateSummary() {
        val count = config.contents.size
        appsCount.text = resources.getQuantityString(R.plurals.tab_apps_count, count, count)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.hide_toggle -> toggleHideFromMain()
            R.id.manage_apps -> openAppsSelector()
            R.id.save -> {
                config.title = tabName.text.toString()
                config.hideFromMain = hideSwitch.isChecked
                callback(true)
            }
            R.id.cancel -> callback(false)
        }
    }

    private fun toggleHideFromMain() {
        hideSwitch.isChecked = !hideSwitch.isChecked
    }

    private fun openAppsSelector() {
        SelectableAppsActivity.start(context, config.contents) {
            val newSelections = it ?: return@start
            config.contents.clear()
            config.contents.addAll(newSelections)
            updateSummary()
        }
    }

    data class TabConfig(
            var title: String = "",
            var hideFromMain: Boolean = true,
            val contents: MutableSet<ComponentKey> = mutableSetOf()) {
        constructor(tab: DrawerTabs.CustomTab) : this(tab.title, tab.hideFromAllApps, HashSet(tab.contents))
        constructor(config: TabConfig) : this(config.title, config.hideFromMain, HashSet(config.contents))
    }

    companion object {

        fun show(context: Context, config: TabConfig, callback: () -> Unit) {
            SettingsBottomSheetDialog(context).apply {
                setContentView(DrawerTabEditBottomSheet(context, config) {
                    if (it) {
                        callback()
                    }
                    dismiss()
                })
                show()
            }
        }

        fun newTab(context: Context, callback: (TabConfig) -> Unit) {
            val config = TabConfig(context.getString(R.string.default_tab_name), true, mutableSetOf())
            show(context, config) {
                callback(config)
            }
        }

        fun edit(context: Context, tab: DrawerTabs.CustomTab) {
            val oldConfig = TabConfig(tab)
            val config = TabConfig(oldConfig)
            show(context, config) {
                if (oldConfig != config) {
                    tab.title = config.title
                    tab.hideFromAllApps = config.hideFromMain
                    tab.contents.clear()
                    tab.contents.addAll(config.contents)
                    context.lawnchairPrefs.drawerTabs.saveToJson()
                }
            }
        }
    }
}
