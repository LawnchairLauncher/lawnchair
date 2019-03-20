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

import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.WindowManager
import android.widget.*
import ch.deletescape.lawnchair.applyColor
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.colors.preferences.TabbedPickerView
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.settings.DrawerTabs
import ch.deletescape.lawnchair.settings.ui.SettingsBottomSheet
import ch.deletescape.lawnchair.tintDrawable
import ch.deletescape.lawnchair.views.BaseBottomSheet
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey
import me.priyesh.chroma.ColorMode
import me.priyesh.chroma.orientation
import me.priyesh.chroma.percentOf
import me.priyesh.chroma.screenDimensions

class DrawerTabEditBottomSheet(context: Context, private var config: TabConfig,
                               private val callback: (Boolean) -> Unit) : FrameLayout(context), View.OnClickListener {

    private val tabName by lazy { findViewById<TextView>(R.id.name) }
    private val appsCount by lazy { findViewById<TextView>(R.id.apps_count) }
    private val hideSwitch by lazy { findViewById<Switch>(R.id.hide_switch) }
    private val tabColor by lazy { findViewById<ImageView>(R.id.color_ring_icon) }

    init {
        View.inflate(context, R.layout.drawer_tab_edit_bottom_sheet, this)
        tabName.text = config.title
        hideSwitch.isChecked = config.hideFromMain

        val accent = ColorEngine.getInstance(context).accent

        findViewById<TextView>(R.id.name_label).setTextColor(accent)

        findViewById<View>(R.id.hide_toggle).setOnClickListener(this)
        findViewById<View>(R.id.tab_color).setOnClickListener(this)
        findViewById<View>(R.id.manage_apps).setOnClickListener(this)
        findViewById<Button>(R.id.save).apply {
            applyColor(accent)
            setTextColor(accent)
            setOnClickListener(this@DrawerTabEditBottomSheet)
        }
        findViewById<TextView>(R.id.cancel).apply {
            setOnClickListener(this@DrawerTabEditBottomSheet)
        }

        findViewById<ImageView>(R.id.hide_icon).tintDrawable(accent)
        findViewById<ImageView>(R.id.manage_apps_icon).tintDrawable(accent)

        hideSwitch.applyColor(accent)

        updateSummary()
    }

    private fun updateSummary() {
        val count = config.contents.size
        appsCount.text = resources.getQuantityString(R.plurals.tab_apps_count, count, count)
        tabColor.tintDrawable(config.colorResolver.resolveColor())
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.hide_toggle -> toggleHideFromMain()
            R.id.tab_color -> openColorDialog()
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
        SelectableAppsActivity.start(context, config.contents) { newSelections ->
            if (newSelections != null) {
                config.contents.clear()
                config.contents.addAll(newSelections)
                updateSummary()
            }
            try {
                val launcher = Launcher.getLauncher(context)
                val tab = config.drawerTab as? DrawerTabs.CustomTab ?: return@start
                AbstractFloatingView.closeAllOpenViews(launcher, false)
                edit(launcher, TabConfig(tab), config, tab, false)
            } catch (ignored: ClassCastException) {

            }
        }
    }

    private fun openColorDialog() {
        val dialog = AlertDialog.Builder(context).create()
        val current = config.colorResolver
        val resolvers = resources.getStringArray(R.array.resolver_tabs)
        with(dialog) {
            val tabbedPickerView = TabbedPickerView(context, "tabs", current.resolveColor(),
                    ColorMode.RGB, resolvers, current.isCustom, {
                config.colorResolver = it
                updateSummary()
            }, dialog::dismiss)
            setView(tabbedPickerView)
            setOnShowListener {
                val width: Int; val height: Int
                if (orientation(context) == Configuration.ORIENTATION_LANDSCAPE) {
                    height = WindowManager.LayoutParams.WRAP_CONTENT
                    width = 80 percentOf screenDimensions(context).widthPixels
                } else {
                    height = WindowManager.LayoutParams.WRAP_CONTENT
                    width = resources.getDimensionPixelSize(R.dimen.chroma_dialog_width)
                }
                window.setLayout(width, height)

                // for some reason it won't respect the windowBackground attribute in the theme
                window.setBackgroundDrawable(context.getDrawable(R.drawable.dialog_material_background))
            }
        }
        dialog.show()
    }

    data class TabConfig(
            var title: String = "",
            var hideFromMain: Boolean = true,
            val contents: MutableSet<ComponentKey> = mutableSetOf(),
            var colorResolver: ColorEngine.ColorResolver,
            val drawerTab: DrawerTabs.Tab? = null) {
        constructor(tab: DrawerTabs.CustomTab) :
                this(tab.title, tab.hideFromAllApps, HashSet(tab.contents), tab.colorResolver, tab)
        constructor(config: TabConfig) :
                this(config.title, config.hideFromMain, HashSet(config.contents), config.colorResolver, config.drawerTab)
    }

    companion object {

        fun show(context: Context, config: TabConfig, callback: () -> Unit) {
            val sheet = SettingsBottomSheet.inflate(context)
            sheet.show(DrawerTabEditBottomSheet(context, config) {
                if (it) {
                    callback()
                }
                sheet.close(true)
            }, true)
        }

        fun show(launcher: Launcher, config: TabConfig, callback: () -> Unit, animate: Boolean = true) {
            val sheet = BaseBottomSheet.inflate(launcher)
            sheet.show(DrawerTabEditBottomSheet(launcher, config) {
                if (it) {
                    callback()
                }
                sheet.close(true)
            }, animate)
        }

        fun newTab(context: Context, callback: (TabConfig) -> Unit) {
            val config = TabConfig(context.getString(R.string.default_tab_name), true, mutableSetOf(),
                    context.lawnchairPrefs.drawerTabs.defaultColorResolver)
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
                    tab.colorResolver = config.colorResolver
                    tab.hideFromAllApps = config.hideFromMain
                    tab.contents.clear()
                    tab.contents.addAll(config.contents)
                    context.lawnchairPrefs.drawerTabs.saveToJson()
                }
            }
        }

        fun edit(launcher: Launcher, tab: DrawerTabs.CustomTab) {
            val oldConfig = TabConfig(tab)
            val config = TabConfig(oldConfig)
            edit(launcher, oldConfig, config, tab)
        }

        fun edit(launcher: Launcher, oldConfig: TabConfig, config: TabConfig,
                 tab: DrawerTabs.CustomTab, animate: Boolean = true) {
            show(launcher, config, {
                if (oldConfig != config) {
                    tab.title = config.title
                    tab.colorResolver = config.colorResolver
                    tab.hideFromAllApps = config.hideFromMain
                    tab.contents.clear()
                    tab.contents.addAll(config.contents)
                    launcher.lawnchairPrefs.drawerTabs.saveToJson()
                }
            }, animate)
        }
    }
}
