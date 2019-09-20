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

package ch.deletescape.lawnchair.wallpaper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max

class WallpaperBackgroundImageView(context: Context, attrs: AttributeSet?) :
        AppCompatImageView(context, attrs) {

    private val wallpaper = WallpaperPreviewProvider.getInstance(context).wallpaper
    private val wallpaperMatrix = Matrix()

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.setMatrix(wallpaperMatrix)
        wallpaper.setBounds(0, 0, wallpaper.intrinsicWidth, wallpaper.intrinsicHeight)
        wallpaper.draw(canvas)
        canvas.restore()

        super.draw(canvas)
    }

    private fun resetMatrix() {
        wallpaperMatrix.reset()

        val width = wallpaper.intrinsicWidth
        val height = wallpaper.intrinsicHeight
        if (width > 0 && height > 0) {
            val scaleX = measuredWidth.toFloat() / width
            val scaleY = measuredHeight.toFloat() / height
            val scale = max(scaleX, scaleY)
            wallpaperMatrix.setScale(scale, scale)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val dm = resources.displayMetrics
        val screenAspectRatio = dm.heightPixels.toFloat() / dm.widthPixels
        setMeasuredDimension((measuredHeight / screenAspectRatio).toInt(), measuredHeight)
        resetMatrix()
    }
}
