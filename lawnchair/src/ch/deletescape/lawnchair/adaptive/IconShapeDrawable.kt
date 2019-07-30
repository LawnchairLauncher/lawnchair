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

package ch.deletescape.lawnchair.adaptive

import android.graphics.*
import android.graphics.drawable.Drawable

class IconShapeDrawable(private val size: Int, iconShape: IconShape) : Drawable() {

    constructor(iconShape: IconShape) : this(-1, iconShape)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val path = Path()

    var iconShape = iconShape
        set(value) {
            field = value
            rebuildPath()
        }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        rebuildPath()
    }

    override fun getIntrinsicWidth(): Int {
        return size
    }

    override fun getIntrinsicHeight(): Int {
        return size
    }

    private fun rebuildPath() {
        path.reset()
        iconShape.addShape(path, 0f, 0f,
                           Math.min(bounds.width(), bounds.height()) / 2f)
        invalidateSelf()
    }
}
