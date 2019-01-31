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
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.support.annotation.Keep
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.getColorAccent
import ch.deletescape.lawnchair.getColorAttr

open class SpringRecyclerView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private val springManager = SpringEdgeEffect.Manager(this)
    private val scrollBarColor by lazy {
        val colorControlNormal = context.getColorAttr(android.R.attr.colorControlNormal)
        val useAccentColor = colorControlNormal == context.getColorAccent()
        if (useAccentColor) ColorEngine.getInstance(context).accent else colorControlNormal
    }

    open var shouldTranslateSelf = true

    var isTopFadingEdgeEnabled = true

    init {
        edgeEffectFactory = springManager.createFactory()
    }

    override fun draw(canvas: Canvas) {
        springManager.withSpring(canvas, shouldTranslateSelf) {
            super.draw(canvas)
            false
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        springManager.withSpring(canvas, !shouldTranslateSelf) {
            super.dispatchDraw(canvas)
            false
        }
    }

    override fun getTopFadingEdgeStrength(): Float {
        return if (isTopFadingEdgeEnabled) super.getTopFadingEdgeStrength() else 0f
    }

    /**
     * Called by Android [android.view.View.onDrawScrollBars]
     */
    @Keep
    protected fun onDrawHorizontalScrollBar(canvas: Canvas, scrollBar: Drawable, l: Int, t: Int, r: Int, b: Int) {
        springManager.withSpringNegative(canvas, shouldTranslateSelf) {
            scrollBar.setColorFilter(scrollBarColor, PorterDuff.Mode.SRC_ATOP)
            scrollBar.setBounds(l, t, r, b)
            scrollBar.draw(canvas)
            false
        }
    }

    /**
     * Called by Android [android.view.View.onDrawScrollBars]
     */
    @Keep
    protected fun onDrawVerticalScrollBar(canvas: Canvas, scrollBar: Drawable, l: Int, t: Int, r: Int, b: Int) {
        springManager.withSpringNegative(canvas, shouldTranslateSelf) {
            scrollBar.setColorFilter(scrollBarColor, PorterDuff.Mode.SRC_ATOP)
            scrollBar.setBounds(l, t, r, b)
            scrollBar.draw(canvas)
            false
        }
    }
}
