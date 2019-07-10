/*
 *     Copyright (C) 2019 paphonb@xda
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

package ch.deletescape.lawnchair.adaptive

import android.content.Context
import android.os.Handler
import ch.deletescape.lawnchair.folder.FolderShape
import ch.deletescape.lawnchair.iconpack.AdaptiveIconCompat
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.runOnMainThread
import ch.deletescape.lawnchair.util.LawnchairSingletonHolder
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherModel

class IconShapeManager(private val context: Context) {

    val iconShape by context.lawnchairPrefs.StringBasedPref(
            "pref_iconShape", IconShape.Circle, ::onShapeChanged,
            IconShape.Companion::fromString, IconShape::toString) { /* no dispose */ }

    private fun onShapeChanged() {
        Handler(LauncherModel.getWorkerLooper()).post {
            LauncherAppState.getInstance(context).reloadIconCache()

            runOnMainThread {
                AdaptiveIconCompat.resetMask()
                FolderShape.init(context)
                context.lawnchairPrefs.recreate()
            }
        }
    }

    companion object : LawnchairSingletonHolder<IconShapeManager>(::IconShapeManager) {

        @JvmStatic
        fun getInstanceNoCreate() = dangerousGetInstance()
    }
}
