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
import android.graphics.PointF
import com.android.launcher3.Utilities

abstract class IconCornerShape {

    abstract fun addCorner(path: Path, position: Position, size: PointF, progress: Float, offsetX: Float, offsetY: Float)

    abstract class BaseBezierPath : IconCornerShape() {

        protected abstract val controlDistance: Float
        protected val roundControlDistance = .447771526f

        private fun getControl1X(position: Position, controlDistance: Float): Float {
            return Utilities.mapRange(controlDistance, position.controlX, position.startX)
        }

        private fun getControl1Y(position: Position, controlDistance: Float): Float {
            return Utilities.mapRange(controlDistance, position.controlY, position.startY)
        }

        private fun getControl2X(position: Position, controlDistance: Float): Float {
            return Utilities.mapRange(controlDistance, position.controlX, position.endX)
        }

        private fun getControl2Y(position: Position, controlDistance: Float): Float {
            return Utilities.mapRange(controlDistance, position.controlY, position.endY)
        }

        override fun addCorner(path: Path, position: Position, size: PointF, progress: Float,
                               offsetX: Float, offsetY: Float) {
            val controlDistance = Utilities.mapRange(progress, controlDistance, roundControlDistance)
            path.cubicTo(
                    getControl1X(position, controlDistance) * size.x + offsetX,
                    getControl1Y(position, controlDistance) * size.y + offsetY,
                    getControl2X(position, controlDistance) * size.x + offsetX,
                    getControl2Y(position, controlDistance) * size.y + offsetY,
                    position.endX * size.x + offsetX,
                    position.endY * size.y + offsetY)
        }
    }

    class Cut : BaseBezierPath() {

        override val controlDistance = 1f

        override fun addCorner(path: Path, position: Position, size: PointF, progress: Float,
                               offsetX: Float, offsetY: Float) {
            if (progress == 0f) {
                path.lineTo(
                        position.endX * size.x + offsetX,
                        position.endY * size.y + offsetY)
            } else {
                super.addCorner(path, position, size, progress, offsetX, offsetY)
            }
        }

        override fun toString(): String {
            return "cut"
        }
    }

    class Squircle : BaseBezierPath() {

        override val controlDistance = .2f

        override fun toString(): String {
            return "squircle"
        }
    }

    class Arc : BaseBezierPath() {

        override val controlDistance = roundControlDistance

        override fun toString(): String {
            return "arc"
        }
    }

    sealed class Position {

        abstract val startX: Float
        abstract val startY: Float

        abstract val controlX: Float
        abstract val controlY: Float

        abstract val endX: Float
        abstract val endY: Float

        object TopLeft : Position() {

            override val startX = 0f
            override val startY = 1f
            override val controlX = 0f
            override val controlY = 0f
            override val endX = 1f
            override val endY = 0f
        }

        object TopRight : Position() {

            override val startX = 0f
            override val startY = 0f
            override val controlX = 1f
            override val controlY = 0f
            override val endX = 1f
            override val endY = 1f
        }

        object BottomRight : Position() {

            override val startX = 1f
            override val startY = 0f
            override val controlX = 1f
            override val controlY = 1f
            override val endX = 0f
            override val endY = 1f
        }

        object BottomLeft : Position() {

            override val startX = 1f
            override val startY = 1f
            override val controlX = 0f
            override val controlY = 1f
            override val endX = 0f
            override val endY = 0f
        }
    }

    companion object {

        val cut = Cut()
        val squircle = Squircle()
        val arc = Arc()

        fun fromString(value: String): IconCornerShape {
            return when (value) {
                "cut" -> cut
                "cubic", "squircle" -> squircle
                "arc" -> arc
                else -> error("invalid corner shape $value")
            }
        }
    }
}
