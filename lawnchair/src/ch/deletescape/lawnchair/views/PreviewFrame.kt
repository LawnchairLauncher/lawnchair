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

import android.app.ActivityOptions
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Point
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import ch.deletescape.lawnchair.childs
import ch.deletescape.lawnchair.dragndrop.CustomWidgetDragListener
import ch.deletescape.lawnchair.wallpaper.WallpaperPreviewProvider
import com.android.launcher3.LauncherAppWidgetProviderInfo
import kotlin.math.max

class PreviewFrame(context: Context, attrs: AttributeSet?) :
        FrameLayout(context, attrs), View.OnLongClickListener, ViewTreeObserver.OnScrollChangedListener {

    private val viewLocation = IntArray(2)
    private val wallpaper = WallpaperPreviewProvider.getInstance(context).wallpaper

    init {
        setOnLongClickListener(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnScrollChangedListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnScrollChangedListener(this)
    }

    override fun onScrollChanged() {
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        val width = wallpaper.intrinsicWidth
        val height = wallpaper.intrinsicHeight
        if (width == 0 || height == 0) {
            super.dispatchDraw(canvas)
            return
        }

        getLocationInWindow(viewLocation)
        val dm = resources.displayMetrics
        val scaleX = dm.widthPixels.toFloat() / width
        val scaleY = dm.heightPixels.toFloat() / height
        val scale = max(scaleX, scaleY)

        canvas.save()
        canvas.translate(0f, -viewLocation[1].toFloat())
        canvas.scale(scale, scale)
        wallpaper.setBounds(0, 0, width, height)
        wallpaper.draw(canvas)
        canvas.restore()

        super.dispatchDraw(canvas)
    }

    override fun onLongClick(v: View): Boolean {
        childs.filterIsInstance<CustomWidgetPreview>().firstOrNull()?.also {
            val provider = it.provider
            val bounds = clipBounds
            val listener = CustomWidgetDragListener(provider, bounds,
                    width, width)

            val homeIntent = listener.addToIntent(
                    Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .setPackage(context.packageName)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

            listener.initWhenReady()
            context.startActivity(
                    homeIntent,
                    ActivityOptions.makeCustomAnimation(
                            context, 0, android.R.anim.fade_out).toBundle())

            // Start a system drag and drop. We use a transparent bitmap as preview for system drag
            // as the preview is handled internally by launcher.
            val description = ClipDescription("", arrayOf(listener.mimeType))
            val data = ClipData(description, ClipData.Item(""))
            startDragAndDrop(data, object : DragShadowBuilder(this) {

                override fun onDrawShadow(canvas: Canvas) {}

                override fun onProvideShadowMetrics(
                        outShadowSize: Point, outShadowTouchPoint: Point
                                                   ) {
                    outShadowSize.set(10, 10)
                    outShadowTouchPoint.set(5, 5)
                }
            }, null, View.DRAG_FLAG_GLOBAL)
        }
        return false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return true
    }

    interface CustomWidgetPreview {

        val provider: LauncherAppWidgetProviderInfo
    }
}
