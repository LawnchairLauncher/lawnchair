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

import android.view.View
import ch.deletescape.lawnchair.sesame.Sesame
import com.android.launcher3.ItemInfo
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.BaseLauncherColumns.ITEM_TYPE_APPLICATION
import com.android.launcher3.R
import com.android.launcher3.popup.SystemShortcut
import ninja.sesame.lib.bridge.v1.SesameFrontend


class SesameSettings : SystemShortcut<Launcher>(R.drawable.ic_sesame, R.string.shortcut_sesame) {

    override fun getOnClickListener(launcher: Launcher, itemInfo: ItemInfo): View.OnClickListener? {
        if (itemInfo.itemType != ITEM_TYPE_APPLICATION) return null
        val packageName = itemInfo.targetComponent?.packageName ?: itemInfo.intent.`package` ?: itemInfo.intent.component?.packageName ?: return null
        if (!Sesame.isAvailable(launcher)) return null
        val intent = SesameFrontend.createAppConfigIntent(packageName) ?: return null

        return View.OnClickListener {
            launcher.startActivity(intent)
        }
    }
}