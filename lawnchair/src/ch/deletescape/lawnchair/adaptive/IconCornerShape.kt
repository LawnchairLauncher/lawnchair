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
import com.android.launcher3.Utilities

sealed class IconCornerShape {

    abstract fun addCorner(path: Path, position: Position, size: Float, progress: Float, offsetX: Float, offsetY: Float)

    object Cut : IconCornerShape() {

        override fun addCorner(path: Path, position: Position, size: Float, progress: Float,
                               offsetX: Float, offsetY: Float) {
            path.lineTo(
                    position.endX * size + offsetX,
                    position.endY * size + offsetY)
        }

        override fun toString(): String {
            return "cut"
        }
    }

    object Cubic : IconCornerShape() {

        private const val normalControlDistance = .2f
        private const val morphedControlDistance = .447771526f

        private fun getControlDistance(progress: Float): Float {
            return Utilities.mapRange(progress, normalControlDistance, morphedControlDistance)
        }

        private fun getControl1X(position: Position, progress: Float): Float {
            return Utilities.mapRange(getControlDistance(progress), position.controlX, position.startX)
        }

        private fun getControl1Y(position: Position, progress: Float): Float {
            return Utilities.mapRange(getControlDistance(progress), position.controlY, position.startY)
        }

        private fun getControl2X(position: Position, progress: Float): Float {
            return Utilities.mapRange(getControlDistance(progress), position.controlX, position.endX)
        }

        private fun getControl2Y(position: Position, progress: Float): Float {
            return Utilities.mapRange(getControlDistance(progress), position.controlY, position.endY)
        }

        override fun addCorner(path: Path, position: Position, size: Float, progress: Float,
                               offsetX: Float, offsetY: Float) {
            path.cubicTo(
                    getControl1X(position, progress) * size + offsetX,
                    getControl1Y(position, progress) * size + offsetY,
                    getControl2X(position, progress) * size + offsetX,
                    getControl2Y(position, progress) * size + offsetY,
                    position.endX * size + offsetX,
                    position.endY * size + offsetY)
        }

        override fun toString(): String {
            return "cubic"
        }
    }

    object Arc : IconCornerShape() {

        override fun addCorner(path: Path, position: Position, size: Float, progress: Float,
                               offsetX: Float, offsetY: Float) {
            val centerX = (1f - position.controlX) * size
            val centerY = (1f - position.controlY) * size
            path.arcTo(centerX - size + offsetX, centerY - size + offsetY,
                       centerX + size + offsetX, centerY + size + offsetY,
                       position.angle, 90f, false)
        }

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

        abstract val angle: Float

        object TopLeft : Position() {

            override val startX = 0f
            override val startY = 1f
            override val controlX = 0f
            override val controlY = 0f
            override val endX = 1f
            override val endY = 0f
            override val angle = 180f
        }

        object TopRight : Position() {

            override val startX = 0f
            override val startY = 0f
            override val controlX = 1f
            override val controlY = 0f
            override val endX = 1f
            override val endY = 1f
            override val angle = 270f
        }

        object BottomRight : Position() {

            override val startX = 1f
            override val startY = 0f
            override val controlX = 1f
            override val controlY = 1f
            override val endX = 0f
            override val endY = 1f
            override val angle = 0f
        }

        object BottomLeft : Position() {

            override val startX = 1f
            override val startY = 1f
            override val controlX = 0f
            override val controlY = 1f
            override val endX = 0f
            override val endY = 0f
            override val angle = 90f
        }
    }

    companion object {

        fun fromString(value: String): IconCornerShape {
            return when (value) {
                "cut" -> Cut
                "cubic" -> Cubic
                "arc" -> Arc
                else -> error("invalid corner shape $value")
            }
        }
    }
}
