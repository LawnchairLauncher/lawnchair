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

package app.lawnchair.icons.shape

import android.content.Context
import android.graphics.Path
import android.graphics.Region
import android.graphics.drawable.AdaptiveIconDrawable
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.Utilities
import com.android.launcher3.icons.GraphicsUtils
import com.android.launcher3.icons.IconProvider
import com.android.launcher3.util.MainThreadInitializedObject

class IconShapeManager(private val context: Context) {

    private val systemIconShape = getSystemShape()

    private fun getSystemShape(): IconShape {
        if (!Utilities.ATLEAST_O) throw RuntimeException("not supported on < oreo")

        val iconMask = AdaptiveIconDrawable(null, null).iconMask
        val systemShape = findNearestShape(iconMask)
        return object : IconShape(systemShape) {

            override fun getMaskPath(): Path {
                return Path(iconMask)
            }

            override fun toString() = ""

            override fun getHashString(): String {
                val resId = IconProvider.CONFIG_ICON_MASK_RES_ID
                if (resId == 0) {
                    return "system-path"
                }
                return context.getString(resId)
            }
        }
    }

    private fun findNearestShape(comparePath: Path): IconShape {
        val size = 200
        val clip = Region(0, 0, size, size)
        val iconR = Region().apply {
            setPath(comparePath, clip)
        }
        val shapePath = Path()
        val shapeR = Region()
        return listOf(
            IconShape.Circle,
            IconShape.Square,
            IconShape.RoundedSquare,
            IconShape.Squircle,
            IconShape.Sammy,
            IconShape.Teardrop,
            IconShape.Cylinder
        )
            .minByOrNull {
                shapePath.reset()
                it.addShape(shapePath, 0f, 0f, size / 2f)
                shapeR.setPath(shapePath, clip)
                shapeR.op(iconR, Region.Op.XOR)

                GraphicsUtils.getArea(shapeR)
            }!!
    }

    companion object {
        @JvmField
        val INSTANCE = MainThreadInitializedObject(::IconShapeManager)

        fun getSystemIconShape(context: Context) = INSTANCE.get(context).systemIconShape

        @JvmStatic
        fun getWindowTransitionRadius(context: Context): Float {
            return PreferenceManager.getInstance(context).iconShape.get().windowTransitionRadius
        }
    }
}
