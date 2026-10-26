package app.lawnchair.curated

import android.app.AlertDialog
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.widget.Toast
import com.android.launcher3.CellLayout
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Lists the curated folders and drops the chosen one on the home screen or a dock slot. */
object CuratedFolderPicker {

    /** Where the folder goes: the first free cell of the current home page, or a dock slot */
    sealed interface Target {
        data object CurrentHomePage : Target
        data class Dock(val page: Int, val cellX: Int, val cellY: Int) : Target
    }

    /**
     * Lists the folders with how many of their apps are installed; picking one places it at
     * [target].
     */
    fun show(launcher: Launcher, target: Target) {
        val catalog = try {
            CuratedCatalog.load(launcher)
        } catch (e: Exception) {
            Toast.makeText(launcher, launcher.getString(R.string.curated_failed, e.message.orEmpty()), Toast.LENGTH_LONG).show()
            return
        }
        val launcherApps = launcher.getSystemService(LauncherApps::class.java)
        val labels = catalog.folders.map { folder ->
            val listed = folder.apps.filter { !it.ifInstalled || it.installedPackage(launcherApps) != null }
            val installed = listed.count { it.installedPackage(launcherApps) != null }
            launcher.getString(R.string.curated_folder_entry, folder.title, installed, listed.size)
        }
        val dark = (launcher.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val theme = if (dark) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }
        AlertDialog.Builder(launcher, theme)
            .setTitle(R.string.curated_folders)
            .setItems(labels.toTypedArray()) { _, which -> place(launcher, catalog.folders[which], target) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Places [folder] at [target] if that cell is free, and reports how many apps are installed. */
    fun place(launcher: Launcher, folder: CuratedFolder, target: Target) {
        val (container, screen, cellX, cellY) = when (target) {
            is Target.Dock -> {
                val layout = launcher.hotseat.getPageAt(target.page) ?: return
                if (layout.isCellOccupied(target.cellX, target.cellY)) return
                val pos = launcher.cellPosMapper.mapPresenterToModel(
                    target.cellX, target.cellY, target.page, Favorites.CONTAINER_HOTSEAT,
                )
                listOf(Favorites.CONTAINER_HOTSEAT, pos.screenId, pos.cellX, pos.cellY)
            }
            Target.CurrentHomePage -> {
                val workspace = launcher.workspace
                val page = workspace.nextPage
                val layout = workspace.getChildAt(page) as? CellLayout
                val cell = IntArray(2)
                if (layout == null || !layout.findCellForSpan(cell, 1, 1)) {
                    Toast.makeText(launcher, R.string.curated_no_space, Toast.LENGTH_SHORT).show()
                    return
                }
                val pos = launcher.cellPosMapper.mapPresenterToModel(
                    cell[0], cell[1], workspace.getScreenIdForPageIndex(page), Favorites.CONTAINER_DESKTOP,
                )
                listOf(Favorites.CONTAINER_DESKTOP, pos.screenId, pos.cellX, pos.cellY)
            }
        }
        val scope = MainScope()
        scope.launch {
            try {
                val result = CuratedFolderInstaller(launcher.applicationContext).add(folder, container, screen, cellX, cellY)
                Toast.makeText(
                    launcher,
                    launcher.getString(R.string.curated_added, folder.title, result.installed, result.notInstalled),
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (e: Exception) {
                Toast.makeText(launcher, launcher.getString(R.string.curated_failed, e.message.orEmpty()), Toast.LENGTH_LONG).show()
            } finally {
                scope.cancel()
            }
        }
    }
}
