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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Handler
import android.support.v4.graphics.PathParser
import android.text.TextUtils
import com.android.launcher3.graphics.IconShape as L3IconShape
import ch.deletescape.lawnchair.iconpack.AdaptiveIconCompat
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.runOnMainThread
import ch.deletescape.lawnchair.util.LawnchairSingletonHolder
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherModel
import com.android.launcher3.Utilities
import com.android.launcher3.graphics.IconShapeOverride
import java.lang.RuntimeException

class IconShapeManager(private val context: Context) {

    private val systemIconShape = getSystemShape()
    var iconShape by context.lawnchairPrefs.StringBasedPref(
            "pref_iconShape", systemIconShape, ::onShapeChanged,
            {
                IconShape.fromString(it) ?: systemIconShape
            }, IconShape::toString) { /* no dispose */ }

    init {
        migratePref()
    }

    @SuppressLint("RestrictedApi")
    private fun migratePref() {
        // Migrate from old path-based override
        val override = IconShapeOverride.getAppliedValue(context)
        if (!TextUtils.isEmpty(override)) {
            try {
                iconShape = findNearestShape(PathParser.createPathFromPathData(override))
                Utilities.getPrefs(context).edit().remove(IconShapeOverride.KEY_PREFERENCE).apply()
            } catch (e: RuntimeException) {
                // Just ignore the error
            }
        }
    }

    private fun getSystemShape(): IconShape {
        if (!Utilities.ATLEAST_OREO) return IconShape.Circle

        val iconMask = AdaptiveIconDrawable(null, null).iconMask
        val systemShape = findNearestShape(iconMask)
        return object : IconShape(systemShape) {

            override fun getMaskPath(): Path {
                return Path(iconMask)
            }

            override fun toString() = ""
        }
    }

    private fun findNearestShape(comparePath: Path): IconShape {
        val clip = Region(0, 0, 100, 100)
        val systemRegion = Region().apply {
            setPath(comparePath, clip)
        }
        val pathRegion = Region()
        val path = Path()
        val rect = Rect()
        return listOf(
                IconShape.Circle,
                IconShape.Square,
                IconShape.RoundedSquare,
                IconShape.Squircle,
                IconShape.Teardrop,
                IconShape.Cylinder).minBy {
            path.reset()
            it.addShape(path, 0f, 0f, 50f)
            pathRegion.setPath(path, clip)
            pathRegion.op(systemRegion, Region.Op.XOR)

            var difference = 0
            val iter = RegionIterator(pathRegion)
            while (iter.next(rect)) {
                difference += rect.width() * rect.height()
            }

            difference
        }!!
    }

    private fun onShapeChanged() {
        Handler(LauncherModel.getWorkerLooper()).post {
            LauncherAppState.getInstance(context).reloadIconCache()

            runOnMainThread {
                AdaptiveIconCompat.resetMask()
                L3IconShape.init(context)
                context.lawnchairPrefs.recreate()
            }
        }
    }

    companion object : LawnchairSingletonHolder<IconShapeManager>(::IconShapeManager) {

        @JvmStatic
        fun getInstanceNoCreate() = dangerousGetInstance()
    }
}
