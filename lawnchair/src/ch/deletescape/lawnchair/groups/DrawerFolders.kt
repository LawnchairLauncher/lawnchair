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

package ch.deletescape.lawnchair.groups

import android.content.Context
import ch.deletescape.lawnchair.*
import ch.deletescape.lawnchair.util.extensions.d
import com.android.launcher3.AppInfo
import com.android.launcher3.FolderInfo
import com.android.launcher3.R
import com.android.launcher3.ShortcutInfo
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.util.ComponentKey

class DrawerFolders(private val manager: AppGroupsManager) : AppGroups<DrawerFolders.Folder>(manager, AppGroupsManager.CategorizationType.Folders) {

    override fun getDefaultCreators(): List<GroupCreator<Folder>> {
        return emptyList()
    }

    override fun getGroupCreator(type: Int): GroupCreator<Folder> {
        return when (type) {
            TYPE_CUSTOM -> ::createCustomFolder
            else -> ::createNull
        }
    }

    private fun createCustomFolder(context: Context) = CustomFolder(context)

    override fun onGroupsChanged(changeCallback: LawnchairPreferencesChangeCallback) {
        // TODO: reload after icon cache is ready to ensure high res folder previews
        changeCallback.reloadDrawer()
    }

    fun getFolderInfos(apps: AlphabeticalAppsList) = getFolderInfos(buildAppsMap(apps)::get)

    private fun buildAppsMap(apps: AlphabeticalAppsList): Map<ComponentKey, AppInfo> {
        return apps.apps.associateBy { it.toComponentKey() }
    }

    private fun getFolderInfos(getAppInfo: (ComponentKey) -> AppInfo?): List<DrawerFolderInfo> = getGroups()
            .asSequence()
            .filter { !it.isEmpty }
            .map { it.toFolderInfo(getAppInfo) }
            .toList()

    fun getHiddenComponents() = getGroups()
            .asSequence()
            .filterIsInstance<CustomFolder>()
            .filter { it.hideFromAllApps.value() }
            .mapNotNull { it.contents.value }
            .flatMapTo(mutableSetOf()) { it.asSequence() }

    abstract class Folder(val context: Context, type: Int, titleRes: Int) : Group(type, context, titleRes) {
        // Ensure icon customization sticks across group changes
        val id = LongCustomization(KEY_ID, Long.random + 9999L)
        open val isEmpty = true

        init {
            // DO NOT actually change this ever
            addCustomization(id)
        }

        open fun toFolderInfo(getAppInfo: (ComponentKey) -> AppInfo?) = DrawerFolderInfo(this).apply {
            setTitle(this@Folder.getTitle())
            id = this@Folder.id.value()
            contents = ArrayList()
        }
    }

    class CustomFolder(context: Context) : Folder(context, TYPE_CUSTOM, R.string.default_folder_name) {

        val hideFromAllApps = SwitchRow(R.drawable.tab_hide_from_main, R.string.tab_hide_from_main,
                KEY_HIDE_FROM_ALL_APPS, true)
        val contents = AppsRow(KEY_ITEMS, mutableSetOf())
        override val isEmpty get() = contents.value.isNullOrEmpty()

        val comparator = ShortcutInfoComparator(context)

        init {
            addCustomization(hideFromAllApps)
            addCustomization(contents)

            customizations.setOrder(KEY_TITLE, KEY_HIDE_FROM_ALL_APPS, KEY_ITEMS)
        }

        override fun getSummary(context: Context): String? {
            val size = getFilter(context).size
            return context.resources.getQuantityString(R.plurals.tab_apps_count, size, size)
        }

        fun getFilter(context: Context): Filter<*> = CustomFilter(context, contents.value())

        override fun toFolderInfo(getAppInfo: (ComponentKey) -> AppInfo?) = super.toFolderInfo(getAppInfo).apply {
            // ✨
            this@CustomFolder.contents.value?.mapNotNullTo(contents) { key ->
                getAppInfo(key)?.makeShortcut()
            }?.sortWith(comparator)
        }
    }

    companion object {

        const val TYPE_CUSTOM = 0

        const val KEY_ITEMS = "items"
    }
}
