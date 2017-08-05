package ch.deletescape.lawnchair.pixelify

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RotateDrawable
import ch.deletescape.lawnchair.R
import java.util.*
import android.os.SystemClock
import ch.deletescape.lawnchair.FastBitmapDrawable
import ch.deletescape.lawnchair.Utilities


class ClockIconDrawable(val context: Context) : Drawable() {
    val backgroundShape = context.getDrawable(R.drawable.launcher_clock_background) as Drawable
    val originalIcon = context.getDrawable(R.drawable.launcher_clock) as LayerDrawable
    val hourLayer = originalIcon.getDrawable(1) as RotateDrawable
    val minuteLayer = originalIcon.getDrawable(2) as RotateDrawable
    val secondLayer = originalIcon.getDrawable(3) as RotateDrawable
    val calendar = Calendar.getInstance() as Calendar
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var background: Bitmap? = null
    var scale = 1f

    private val TAG = "ClockIconDrawable"

    private fun updateLayers() {
        calendar.timeInMillis = System.currentTimeMillis()
        val second = calendar[Calendar.SECOND]
        val minute = calendar[Calendar.MINUTE]
        val hour = calendar[Calendar.HOUR_OF_DAY] % 12

        val secondLevel = second * 10000 / 60
        val minuteLevel = minute * 10000 / 60 + (secondLevel / 60)
        val hourLevel = hour * 10000 / 12 + (minuteLevel / 12)
        hourLayer.level = hourLevel
        minuteLayer.level = minuteLevel
        secondLayer.level = secondLevel
    }

    override fun draw(canvas: Canvas) {
        updateLayers()
        canvas.drawBitmap(background, 0f, 0f, paint)
        originalIcon.draw(canvas)
        nextFrame()
    }

    override fun onBoundsChange(bounds: Rect?) {
        updateBounds()
    }

    private fun updateBounds() {
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top

        val inset = convertDpToPixel(-11f)

        background = getBackground(width, height)
        originalIcon.setBounds(inset, inset, width - inset, height - inset)
    }

    fun convertDpToPixel(dp: Float): Int {
        val metrics = Resources.getSystem().displayMetrics
        val px = dp * (metrics.densityDpi / 160f)
        return Math.round(px)
    }

    private fun getBackground(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height,
                Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val inset = convertDpToPixel(2f)

        backgroundShape.setBounds(inset, inset, width - inset, height - inset)
        backgroundShape.draw(canvas)

        return Utilities.addShadowToIcon(bitmap, width)
    }

    override fun setAlpha(i: Int) {

    }

    override fun setColorFilter(colorFilter: ColorFilter?) {

    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (visible) {
            nextFrame()
        } else {
            unscheduleSelf(mNextFrame)
        }
        return changed
    }

    private fun nextFrame() {
        unscheduleSelf(mNextFrame)
        scheduleSelf(mNextFrame, SystemClock.uptimeMillis() + 1000)
    }

    private val mNextFrame = Runnable {
        invalidateSelf()
    }

    class Wrapper(context: Context) : FastBitmapDrawable(), Callback {
        val canvas = Canvas()
        val drawable = ClockIconDrawable(context)
        val clearPaint = Paint()

        init {
            drawable.callback = this
            clearPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        override fun draw(canvas: Canvas) {
            if (bitmap == null) return
            this.canvas.drawRect(bounds, clearPaint)
            drawable.draw(this.canvas)
            super.draw(canvas)
        }

        override fun onBoundsChange(bounds: Rect) {
            val width = bounds.right - bounds.left
            val height = bounds.bottom - bounds.top
            bitmap = Bitmap.createBitmap(width, height,
                    Bitmap.Config.ARGB_8888)
            canvas.setBitmap(bitmap)
            drawable.setBounds(0, 0, width, height)
        }

        override fun getIntrinsicWidth(): Int {
            return bitmap?.width ?: 0
        }

        override fun getIntrinsicHeight(): Int {
            return bitmap?.height ?: 0
        }

        override fun unscheduleDrawable(p0: Drawable?, p1: Runnable?) {
            unscheduleSelf(p1)
        }

        override fun invalidateDrawable(p0: Drawable?) {
            invalidateSelf()
        }

        override fun scheduleDrawable(p0: Drawable?, p1: Runnable?, p2: Long) {
            scheduleSelf(p1, p2)
        }
    }
}
