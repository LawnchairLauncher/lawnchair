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

import android.graphics.Path
import ch.deletescape.lawnchair.util.extensions.e
import com.android.launcher3.Utilities
import java.lang.Exception
import kotlin.math.min

open class IconShape(private val topLeft: Corner,
                     private val topRight: Corner,
                     private val bottomLeft: Corner,
                     private val bottomRight: Corner) {

    constructor(topLeftShape: IconCornerShape,
                topRightShape: IconCornerShape,
                bottomLeftShape: IconCornerShape,
                bottomRightShape: IconCornerShape,
                topLeftScale: Float,
                topRightScale: Float,
                bottomLeftScale: Float,
                bottomRightScale: Float) : this(Corner(topLeftShape, topLeftScale),
                                                Corner(topRightShape, topRightScale),
                                                Corner(bottomLeftShape, bottomLeftScale),
                                                Corner(bottomRightShape, bottomRightScale))

    fun getMaskPath(): Path {
        return Path().also { addToPath(it, 0f, 0f, 100f, 100f, 50f) }
    }

    @JvmOverloads
    fun addToPath(path: Path, left: Float, top: Float, right: Float, bottom: Float,
                  size : Float = 50f, endSize: Float = size, progress: Float = 0f) {
        val topLeftSize = Utilities.mapRange(progress, topLeft.scale * size, endSize)
        val topRightSize = Utilities.mapRange(progress, topRight.scale * size, endSize)
        val bottomLeftSize = Utilities.mapRange(progress, bottomLeft.scale * size, endSize)
        val bottomRightSize = Utilities.mapRange(progress, bottomRight.scale * size, endSize)

        // Start from the bottom right corner
        path.moveTo(right, bottom - bottomRightSize)
        bottomRight.shape.addCorner(path, IconCornerShape.Position.BottomRight,
                                    bottomRightSize,
                                    progress,
                                    right - bottomRightSize,
                                    bottom - bottomRightSize)

        // Move to bottom left
        addLine(path,
                right - bottomRightSize, bottom,
                left + bottomLeftSize, bottom)
        bottomLeft.shape.addCorner(path, IconCornerShape.Position.BottomLeft,
                                   bottomLeftSize,
                                   progress,
                                   left,
                                   bottom - bottomLeftSize)

        // Move to top left
        addLine(path,
                left, bottom - bottomLeftSize,
                left, top + topLeftSize)
        topLeft.shape.addCorner(path, IconCornerShape.Position.TopLeft,
                                topLeftSize,
                                progress,
                                left,
                                top)

        // And then finally top right
        addLine(path,
                left + topLeftSize, top,
                right - topRightSize, top)
        topRight.shape.addCorner(path, IconCornerShape.Position.TopRight,
                                 topRightSize,
                                 progress,
                                 right - topRightSize,
                                 top)

        path.close()
    }

    private fun addLine(path: Path, x1: Float, y1: Float, x2: Float, y2: Float) {
        if (x1 == x2 && y1 == y2) return
        path.lineTo(x2, y2)
    }

    override fun toString(): String {
        return "v1|$topLeft|$topRight|$bottomLeft|$bottomRight"
    }

    data class Corner(val shape: IconCornerShape, val scale: Float) {

        override fun toString(): String {
            return "$shape$scale"
        }

        companion object {

            fun fromString(value: String): Corner {
                val parts = value.split(",")
                val scale = parts[1].toFloat()
                if (scale !in 0f..1f) error("scale must be in [0, 1]")
                return Corner(IconCornerShape.fromString(parts[0]), scale)
            }
        }
    }

    object Circle : IconShape(IconCornerShape.arc,
                              IconCornerShape.arc,
                              IconCornerShape.arc,
                              IconCornerShape.arc,
                              1f, 1f, 1f, 1f) {

        override fun toString(): String {
            return "circle"
        }
    }

    object Square : IconShape(IconCornerShape.arc,
                              IconCornerShape.arc,
                              IconCornerShape.arc,
                              IconCornerShape.arc,
                              .16f, .16f, .16f, .16f) {

        override fun toString(): String {
            return "square"
        }
    }

    object RoundedSquare : IconShape(IconCornerShape.arc,
                                     IconCornerShape.arc,
                                     IconCornerShape.arc,
                                     IconCornerShape.arc,
                                     .6f, .6f, .6f, .6f) {

        override fun toString(): String {
            return "roundedSquare"
        }
    }

    object Squircle : IconShape(IconCornerShape.squircle,
                                IconCornerShape.squircle,
                                IconCornerShape.squircle,
                                IconCornerShape.squircle,
                                1f, 1f, 1f, 1f){

        override fun toString(): String {
            return "squircle"
        }
    }

    object Teardrop : IconShape(IconCornerShape.arc,
                                IconCornerShape.arc,
                                IconCornerShape.arc,
                                IconCornerShape.arc,
                                1f, 1f, 1f, .3f){

        override fun toString(): String {
            return "teardrop"
        }
    }

    companion object {

        fun fromString(value: String): IconShape {
            return when (value) {
                "circle" -> Circle
                "square" -> Square
                "roundedSquare" -> RoundedSquare
                "squircle" -> Squircle
                "teardrop" -> Teardrop
                else -> try {
                    parseCustomShape(value)
                } catch (ex: Exception) {
                    e("Error creating shape $value", ex)
                    Circle
                }
            }
        }

        private fun parseCustomShape(value: String): IconShape {
            val parts = value.split("|")
            if (parts[0] != "v1") error("unknown config format")
            if (parts.size != 5) error("invalid arguments size")
            return IconShape(Corner.fromString(parts[1]),
                             Corner.fromString(parts[2]),
                             Corner.fromString(parts[3]),
                             Corner.fromString(parts[4]))
        }
    }
}
