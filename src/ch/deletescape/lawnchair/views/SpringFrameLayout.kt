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
import android.util.AttributeSet
import android.util.SparseBooleanArray
import android.view.View
import android.widget.FrameLayout

open class SpringFrameLayout(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {

    private val springManager = SpringEdgeEffect.Manager(this)

    private val springViews = SparseBooleanArray()

    fun addSpringView(view: View) {
        springViews.put(view.id, true)
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        return if (!springViews.get(child.id)) {
            super.drawChild(canvas, child, drawingTime)
        } else {
            springManager.withSpring(canvas) { super.drawChild(canvas, child, drawingTime) }
        }
    }

    fun createEdgeEffectFactory() = springManager.createFactory()
}
