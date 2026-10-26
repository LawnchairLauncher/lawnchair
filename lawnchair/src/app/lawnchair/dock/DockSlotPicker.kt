package app.lawnchair.dock

import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import app.lawnchair.curated.CuratedCatalog
import app.lawnchair.curated.CuratedFolder
import app.lawnchair.curated.CuratedFolderPicker
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.WorkspaceItemInfo

/** Picks what goes into an empty dock slot: any app, or the app drawer button. */
object DockSlotPicker {

    private sealed interface Entry {
        val label: String

        data class App(val app: AppInfo, override val label: String) : Entry
        data class Drawer(override val label: String) : Entry
        data class Folder(val folder: CuratedFolder, override val label: String) : Entry
    }

    fun show(launcher: Launcher, page: Int, cellX: Int, cellY: Int) {
        val folders = try {
            CuratedCatalog.load(launcher).folders.map {
                Entry.Folder(it, launcher.getString(R.string.curated_folder_label, it.title))
            }
        } catch (e: Exception) {
            emptyList()
        }
        val all: List<Entry> = listOf<Entry>(Entry.Drawer(launcher.getString(R.string.dock_drawer_button))) + folders +
            launcher.appsView.appsStore.apps
                .map { Entry.App(it, it.title?.toString().orEmpty()) }
                .sortedBy { it.label.lowercase() }

        val dark = (launcher.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val theme = if (dark) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }
        val dp = launcher.resources.displayMetrics.density
        val iconPx = (40 * dp).toInt()
        val pad = (16 * dp).toInt()
        val listHeight = (launcher.resources.displayMetrics.heightPixels * 0.6f).toInt()

        val adapter = object : BaseAdapter() {
            var shown = all

            override fun getCount() = shown.size
            override fun getItem(position: Int) = shown[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView as? TextView ?: TextView(parent.context).apply {
                    setPadding(pad, pad / 2, pad, pad / 2)
                    gravity = Gravity.CENTER_VERTICAL
                    compoundDrawablePadding = pad
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                }
                val entry = shown[position]
                row.text = entry.label
                val icon: Drawable = when (entry) {
                    is Entry.App -> entry.app.bitmap.newIcon(launcher)
                    is Entry.Drawer -> DrawerButton.icon(launcher).newIcon(launcher)
                    is Entry.Folder -> launcher.getDrawable(R.drawable.ic_folder)!!.mutate()
                        .apply { setTint(if (dark) Color.WHITE else Color.BLACK) }
                }
                icon.setBounds(0, 0, iconPx, iconPx)
                row.setCompoundDrawablesRelative(icon, null, null, null)
                return row
            }

            fun filter(query: String) {
                shown = if (query.isBlank()) all else all.filter { it.label.contains(query.trim(), ignoreCase = true) }
                notifyDataSetChanged()
            }
        }

        val search = EditText(launcher).apply {
            hint = launcher.getString(R.string.dock_search_apps)
            isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = adapter.filter(s?.toString().orEmpty())
            })
        }
        val list = ListView(launcher).apply { this.adapter = adapter }
        val content = LinearLayout(launcher).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, listHeight))
        }

        val dialog = AlertDialog.Builder(launcher, theme)
            .setTitle(R.string.dock_pick_app)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        list.setOnItemClickListener { _, _, position, _ ->
            place(launcher, adapter.getItem(position), page, cellX, cellY)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun place(launcher: Launcher, entry: Entry, page: Int, cellX: Int, cellY: Int) {
        val layout = launcher.hotseat.getPageAt(page) ?: return
        if (layout.isCellOccupied(cellX, cellY)) return
        val info: WorkspaceItemInfo = when (entry) {
            is Entry.App -> entry.app.makeWorkspaceItem(launcher)
            is Entry.Drawer -> DrawerButton.makeItem(launcher)
            is Entry.Folder -> {
                CuratedFolderPicker.place(launcher, entry.folder, CuratedFolderPicker.Target.Dock(page, cellX, cellY))
                return
            }
        }
        val pos = launcher.cellPosMapper.mapPresenterToModel(cellX, cellY, page, Favorites.CONTAINER_HOTSEAT)
        launcher.modelWriter.addItemToDatabase(info, Favorites.CONTAINER_HOTSEAT, pos.screenId, pos.cellX, pos.cellY)
        val view = launcher.itemInflater.inflateItem(info, launcher.hotseat) ?: return
        launcher.workspace.addInScreenFromBind(view, info)
    }
}
