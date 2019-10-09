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
import android.net.Uri
import android.view.View
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.override.CustomInfoProvider
import ch.deletescape.lawnchair.sesame.Sesame
import ch.deletescape.lawnchair.util.LawnchairSingletonHolder
import ch.deletescape.lawnchair.util.hasFlag
import com.android.launcher3.*
import com.android.launcher3.ItemInfoWithIcon.FLAG_SYSTEM_YES
import com.android.launcher3.LauncherSettings.BaseLauncherColumns.ITEM_TYPE_APPLICATION
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.popup.SystemShortcut
import com.google.android.apps.nexuslauncher.CustomBottomSheet
import ninja.sesame.lib.bridge.v1.SesameFrontend
import java.net.URISyntaxException

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

    class Uninstall : SystemShortcut<Launcher>(R.drawable.ic_uninstall_no_shadow, R.string.uninstall_drop_target_label) {

        override fun getOnClickListener(launcher: Launcher, itemInfo: ItemInfo): View.OnClickListener? {
            if (itemInfo is ItemInfoWithIcon) {
                if (itemInfo.runtimeStatusFlags.hasFlag(FLAG_SYSTEM_YES)) {
                    return null
                }
            }

            return getUninstallTarget(launcher, itemInfo)?.let { cn -> View.OnClickListener {
                AbstractFloatingView.closeAllOpenViews(launcher)
                try {
                    val i = Intent.parseUri(launcher.getString(R.string.delete_package_intent), 0)
                            .setData(Uri.fromParts("package", cn.packageName, cn.className))
                            .putExtra(Intent.EXTRA_USER, itemInfo.user)
                    launcher.startActivity(i)
                } catch (e: URISyntaxException) {
                }
            } }
        }

        private fun getUninstallTarget(launcher: Launcher, item: ItemInfo): ComponentName? {
            if (item.itemType == ITEM_TYPE_APPLICATION && item.id == ItemInfo.NO_ID.toLong()) {
                val intent = item.intent
                val user = item.user
                if (intent != null) {
                    val info = LauncherAppsCompat.getInstance(launcher).resolveActivity(intent, user)
                    if (info != null && !info.applicationInfo.flags.hasFlag(ApplicationInfo.FLAG_SYSTEM)) {
                        return info.componentName
                    }
                }
            }
            return null
        }
    }

    class Remove : SystemShortcut<Launcher>(R.drawable.ic_remove_no_shadow, R.string.remove_drop_target_label) {

        override fun getOnClickListener(launcher: Launcher, itemInfo: ItemInfo): View.OnClickListener? {
            if (itemInfo.id == ItemInfo.NO_ID.toLong()) return null
            return if (itemInfo is ShortcutInfo || itemInfo is LauncherAppWidgetInfo || itemInfo is FolderInfo) {
                View.OnClickListener {
                    AbstractFloatingView.closeAllOpenViews(launcher)

                    launcher.removeItem(null, itemInfo, true /* deleteFromDb */)
                    launcher.model.forceReload()
                    launcher.workspace.stripEmptyScreens()
                }
            } else null
        }
    }

    class Edit : SystemShortcut<Launcher>(R.drawable.ic_edit_no_shadow, R.string.action_preferences) {

        override fun getOnClickListener(launcher: Launcher, itemInfo: ItemInfo): View.OnClickListener? {
            if (launcher.lawnchairPrefs.lockDesktop) return null
            if (!CustomInfoProvider.isEditable(itemInfo)) return null
            return View.OnClickListener {
                AbstractFloatingView.closeAllOpenViews(launcher)
                CustomBottomSheet.show(launcher, itemInfo)
            }
        }
    }

    class SesameSettings : SystemShortcut<Launcher>(R.drawable.ic_sesame, R.string.shortcut_sesame) {

        override fun getOnClickListener(launcher: Launcher, itemInfo: ItemInfo): View.OnClickListener? {
            if (itemInfo.itemType != ITEM_TYPE_APPLICATION) return null
            val packageName = itemInfo.targetComponent?.packageName ?: itemInfo.intent.`package`
            ?: itemInfo.intent.component?.packageName ?: return null
            if (!Sesame.isAvailable(launcher)) return null
            val intent = SesameFrontend.createAppConfigIntent(packageName) ?: return null

            return View.OnClickListener {
                launcher.startActivity(intent)
            }
        }
    }

    companion object : LawnchairSingletonHolder<LawnchairShortcut>(::LawnchairShortcut)

}
