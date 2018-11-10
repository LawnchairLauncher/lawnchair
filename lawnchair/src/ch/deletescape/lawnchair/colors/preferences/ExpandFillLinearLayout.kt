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

package ch.deletescape.lawnchair.colors.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import ch.deletescape.lawnchair.forEachChildIndexed

class ExpandFillLinearLayout(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    var childWidth = 0
    var childHeight = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fillLayout = if (orientation == HORIZONTAL) {
            val exactHeight = MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
            performMeasure(widthMeasureSpec, childWidth) { view, spec ->
                measureChild(view, spec, exactHeight)
            }
        } else {
            val exactWidth = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY)
            performMeasure(heightMeasureSpec, childHeight) { view, spec ->
                measureChild(view, exactWidth, spec)
            }
        }
        if (fillLayout) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    private inline fun performMeasure(spec: Int, childSize: Int, crossinline measureChild: (View, Int) -> Unit): Boolean {
        val available = MeasureSpec.getSize(spec)
        if (childSize * childCount >= available) return false
        val width = available / childCount
        val used = width * childCount
        val remaining = available - used
        forEachChildIndexed { view, i ->
            if (i < remaining) measureChild(view, MeasureSpec.makeMeasureSpec(width + 1, MeasureSpec.EXACTLY))
            else measureChild(view, MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY))
        }
        return true
    }
}
