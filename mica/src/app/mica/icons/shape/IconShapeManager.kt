/*
 *     Copyright (C) 2019 paphonb@xda
 *
 *     This file is part of Mica Launcher.
 *
 *     Mica Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Mica Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Mica Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.mica.icons.shape

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import app.mica.preferences2.PreferenceManager2
import app.mica.preferences2.firstCached
import com.android.launcher3.Utilities
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable
import javax.inject.Inject

@LauncherAppSingleton
class IconShapeManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    private val systemIconShape = getSystemShape()

    private fun getSystemShape(): IconShape.SystemBased {
        if (!Utilities.ATLEAST_O) throw RuntimeException("not supported on < oreo")

        val iconMask = AdaptiveIconDrawable(null, null).iconMask
        return IconShape.SystemBased(iconMask, context)
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getIconShapeManager)

        fun getSystemIconShape(context: Context) = INSTANCE.get(context).systemIconShape

        @JvmStatic
        fun getWindowTransitionRadius(context: Context): Float {
            val prefs = PreferenceManager2.getInstance(context)
            return prefs.iconShape.firstCached().windowTransitionRadius
        }
    }
}
