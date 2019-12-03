/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
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

package ch.deletescape.lawnchair.shortcuts

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.UserHandle
import ch.deletescape.lawnchair.sesame.Sesame
import ch.deletescape.lawnchair.sesame.getActivity
import ch.deletescape.lawnchair.sesame.getIcon
import ch.deletescape.lawnchair.sesame.getId
import com.android.launcher3.popup.PopupPopulator
import com.android.launcher3.shortcuts.DeepShortcutManager
import com.android.launcher3.shortcuts.ShortcutKey
import ninja.sesame.lib.bridge.v1.SesameFrontend
import ninja.sesame.lib.bridge.v1.SesameShortcut

class LawnchairShortcutManager(private val context: Context) : DeepShortcutManager(context) {
    private var lastCallExternal = false

    override fun unpinShortcut(key: ShortcutKey) {
        lastCallExternal = isExternal(key.id)
        if (!lastCallExternal) {
            super.unpinShortcut(key)
        }
    }

    override fun pinShortcut(key: ShortcutKey) {
        lastCallExternal = isExternal(key.id)
        if (!lastCallExternal) {
            super.pinShortcut(key)
        }
    }

    override fun startShortcut(packageName: String, id: String, sourceBounds: Rect?,
                               startActivityOptions: Bundle?, user: UserHandle) {
        lastCallExternal = isExternal(id)
        if (lastCallExternal) {
            if (isQuinoa(id)) {
                val shortcuts = queryForFullDetails(packageName, listOf(id), user)
                val shortcut = shortcuts.getOrNull(0)?.sesameShortcut
                if (shortcut != null) {
                    SesameFrontend.runAction(context, shortcut.actions[0])
                }
            }
        } else {
            super.startShortcut(packageName, id, sourceBounds, startActivityOptions, user)
        }
    }

    override fun getShortcutIconDrawable(shortcutInfo: ShortcutInfo, density: Int): Drawable {
        lastCallExternal = isExternal(shortcutInfo.id)
        if (lastCallExternal) {
            if (isQuinoa(shortcutInfo.id)) {
                return shortcutInfo.sesameShortcut!!.getIcon(context, density)
            }
        }
        return super.getShortcutIconDrawable(shortcutInfo, density)
    }

    override fun query(flags: Int, packageName: String?, activity: ComponentName?,
                       shortcutIds: MutableList<String>?,
                       user: UserHandle?): MutableList<ShortcutInfo> {
        if (Sesame.isAvailable(context) && Sesame.showShortcuts) {
            lastCallExternal = true
            return SesameFrontend
                    .getRecentAppShortcuts(packageName, false, PopupPopulator.MAX_SHORTCUTS)
                    .filter {
                        shortcutIds == null || shortcutIds.contains(it.getId())
                    }.map {
                            SesameFrontend.getShortcutInfo(context, it) ?: ShortcutInfo.Builder(context, it.getId())
                                    .setIntent(Intent("dummy").apply {
                                        putExtra(EXTRA_EXTERNAL_ID, it.id)
                                        putExtra(EXTRA_EXTERNAL_PACKAGE, it.packageName)
                                    })
                                    .setShortLabel(it.plainLabel)
                                    .setLongLabel(it.plainLabel)
                                    .setActivity(activity ?: it.getActivity())
                                    .setRank(0)
                                    .build().apply {
                                        val clearFlags = this::class.java
                                                .getDeclaredMethod("clearFlags", Int::class.java)
                                                .apply {
                                                    isAccessible = true
                                                }
                                        clearFlags.invoke(this, (1 shl 0)) // FLAG_DYNAMIC

                                    }
                    }.toMutableList()
        }
        return super.query(flags, packageName, activity, shortcutIds, user)
    }

    override fun wasLastCallSuccess(): Boolean {
        return lastCallExternal || super.wasLastCallSuccess()
    }

    private val ShortcutInfo.sesameShortcut: SesameShortcut?
        get() {
            val sesameId = intent?.extras?.getString(EXTRA_EXTERNAL_ID) ?: return null
            val packageName = intent?.extras?.getString(EXTRA_EXTERNAL_PACKAGE)
            return SesameFrontend.getRecentAppShortcuts(packageName, false, 50).firstOrNull { it.id == sesameId }
        }

    companion object {
        private const val EXTRA_EXTERNAL_ID = "external_id"
        private const val EXTRA_EXTERNAL_PACKAGE = "external_package"

        const val QUINOA_PREFIX = "sesame_"
        const val EXTERNAL_PREFIX = "external_"

        fun isQuinoa(id: String) = id.startsWith(QUINOA_PREFIX)
        fun isExternal(id: String) = isQuinoa(id) || id.startsWith(EXTERNAL_PREFIX)
    }
}