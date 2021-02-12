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

package ch.deletescape.lawnchair.popup

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.net.Uri
import android.view.View
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.override.CustomInfoProvider
import ch.deletescape.lawnchair.sesame.Sesame
import ch.deletescape.lawnchair.util.LawnchairSingletonHolder
import ch.deletescape.lawnchair.util.hasFlag
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.R
import com.android.launcher3.model.data.*
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_SYSTEM_YES
import com.android.launcher3.popup.SystemShortcut
import com.google.android.apps.nexuslauncher.CustomBottomSheet
import ninja.sesame.lib.bridge.v1.SesameFrontend

class LawnchairShortcut(private val context: Context) {

    private val shortcuts = listOf(
            ShortcutEntry("edit", Edit(), true),
            ShortcutEntry("sesame", SesameSettings(), true),
            ShortcutEntry("info", SystemShortcut.AppInfo(), true),
            ShortcutEntry("widgets", SystemShortcut.Widgets(), true),
            ShortcutEntry("install", SystemShortcut.Install(), true),
            ShortcutEntry("remove", Remove(), false),
            ShortcutEntry("uninstall", Uninstall(), false)
    )

    inner class ShortcutEntry(key: String, val shortcut: SystemShortcut<*>, enabled: Boolean) {
        val enabled by context.lawnchairPrefs.BooleanPref("pref_iconPopup_$key", enabled)
    }

    val enabledShortcuts get() = shortcuts.filter { it.enabled }.map { it.shortcut }

    class Uninstall(private val target: Launcher, private val itemInfo: ItemInfo) : SystemShortcut<Launcher>(R.drawable.ic_uninstall_no_shadow, R.string.uninstall_drop_target_label, target, itemInfo) {
        private fun getUninstallTarget(launcher: Launcher, item: ItemInfo): ComponentName? {
            if (item.itemType == ITEM_TYPE_APPLICATION && item.id == ItemInfo.NO_ID) {
                val intent = item.intent
                val user = item.user
                if (intent != null) {
                    val info = launcher.getSystemService(LauncherApps::class.java).resolveActivity(intent, user)
                    if (info != null && !info.applicationInfo.flags.hasFlag(ApplicationInfo.FLAG_SYSTEM)) {
                        return info.componentName
                    }
                }
            }
            return null
        }

        override fun onClick(v: View?) {
            if (itemInfo !is ItemInfoWithIcon || !itemInfo.runtimeStatusFlags.hasFlag(FLAG_SYSTEM_YES)) {
                getUninstallTarget(target, itemInfo)?.let { cn -> View.OnClickListener {
                    AbstractFloatingView.closeAllOpenViews(target)
                    val i = Intent.parseUri(target.getString(R.string.delete_package_intent), 0).setData(Uri.fromParts("package", cn.packageName, cn.className)).putExtra(Intent.EXTRA_USER, itemInfo.user)
                    target.startActivity(i)
                }}
            }
        }
    }

    class Remove(private val target: Launcher, private val itemInfo: ItemInfo) : SystemShortcut<Launcher>(R.drawable.ic_remove_no_shadow, R.string.remove_drop_target_label, target, itemInfo) {
        override fun onClick(v: View?) {
            if (itemInfo is WorkspaceItemInfo || itemInfo is LauncherAppWidgetInfo || itemInfo is FolderInfo) {
                AbstractFloatingView.closeAllOpenViews(target)
                target.removeItem(null, itemInfo, true)
                target.model.forceReload()
                target.workspace.stripEmptyScreens()
            }
        }
    }

    class Edit(private val target: Launcher, private val itemInfo: ItemInfo) : SystemShortcut<Launcher>(R.drawable.ic_edit_no_shadow, R.string.action_preferences, target, itemInfo) {
        override fun onClick(v: View?) {
            if (!target.lawnchairPrefs.lockDesktop && CustomInfoProvider.isEditable(itemInfo)) {
                AbstractFloatingView.closeAllOpenViews(target)
                CustomBottomSheet.show(target, itemInfo)
            }
        }
    }

    class SesameSettings(private val target: Launcher, private val itemInfo: ItemInfo) : SystemShortcut<Launcher>(R.drawable.ic_sesame, R.string.shortcut_sesame, target, itemInfo) {
        override fun onClick(v: View?) {
            val packageName = itemInfo.targetComponent?.packageName ?: itemInfo.intent.`package` ?: itemInfo.intent.component?.packageName
            val intent = packageName?.let { SesameFrontend.createAppConfigIntent(it) }

            if (itemInfo.itemType == ITEM_TYPE_APPLICATION && packageName != null && Sesame.isAvailable(target)) {
                target.startActivity(intent)
            }
        }
    }

    companion object : LawnchairSingletonHolder<LawnchairShortcut>(::LawnchairShortcut)

}
