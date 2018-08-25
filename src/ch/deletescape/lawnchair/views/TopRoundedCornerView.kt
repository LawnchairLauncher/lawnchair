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

package ch.deletescape.lawnchair.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.android.launcher3.R

class TopRoundedCornerView(context: Context, attrs: AttributeSet) : SpringFrameLayout(context, attrs) {

    private val rect = RectF()
    private val clipPath = Path()

    private val radius by lazy { resources.getDimensionPixelSize(R.dimen.bg_round_rect_radius).toFloat() }
    private val radii by lazy { floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f) }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        canvas.save()
        canvas.clipPath(clipPath)
        val result = super.drawChild(canvas, child, drawingTime)
        canvas.restore()
        return result
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        rect.set(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat())
        clipPath.reset()
        clipPath.addRoundRect(rect, radii, Path.Direction.CW)
    }
}