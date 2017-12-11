package ch.deletescape.lawnchair.pixelify

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.*
import android.os.SystemClock
import ch.deletescape.lawnchair.FastBitmapDrawable
import ch.deletescape.lawnchair.R
import ch.deletescape.lawnchair.Utilities
import ch.deletescape.lawnchair.util.IconNormalizer
import java.util.*

class ClockIconDrawable(val context: Context, val adaptive: Boolean) : Drawable() {
    val backgroundShape = context.getDrawable(R.drawable.launcher_clock_background) as Drawable
    val originalIcon = context.getDrawable(R.drawable.launcher_clock) as LayerDrawable
    val hourLayer = originalIcon.getDrawable(1) as RotateDrawable
    val minuteLayer = originalIcon.getDrawable(2) as RotateDrawable
    val secondLayer = originalIcon.getDrawable(3) as RotateDrawable
    val calendar = Calendar.getInstance() as Calendar
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var background: Bitmap? = null
    var scale = 1f

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
        if (!adaptive)
            canvas.drawBitmap(background, 0f, 0f, paint)
        originalIcon.draw(canvas)
        nextFrame()
    }

    override fun onBoundsChange(bounds: Rect?) {
        updateBounds()
    }

    private fun updateBounds() {
        if (!adaptive) {
            val width = bounds.right - bounds.left
            val height = bounds.bottom - bounds.top

            val inset = (-0.2f * width).toInt()

            background = getBackground(width, height)
            originalIcon.setBounds(inset, inset, width - inset, height - inset)
        } else {
            originalIcon.bounds = bounds
        }
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

    companion object {
        fun createWrapped(context: Context): Wrapper {
            return if (Utilities.ATLEAST_NOUGAT) {
                Wrapper(create(context), true)
            } else {
                Wrapper(create(context), false)
            }
        }

        fun create(context: Context): Drawable {
            return if (Utilities.ATLEAST_OREO) {
                AdaptiveIconDrawable(
                        ColorDrawable(context.resources.getColor(R.color.blue_grey_100)),
                        ClockIconDrawable(context, true))
            } else if (Utilities.ATLEAST_NOUGAT) {
                AdaptiveIconDrawableCompat(
                        ColorDrawable(context.resources.getColor(R.color.blue_grey_100)),
                        ClockIconDrawable(context, true), false)
            } else {
                ClockIconDrawable(context, false)
            }
        }

        fun convertDpToPixel(dp: Float): Int {
            val metrics = Resources.getSystem().displayMetrics
            val px = dp * (metrics.densityDpi / 160f)
            return Math.round(px)
        }
    }

    class Wrapper(val drawable: Drawable, val adaptive: Boolean) : FastBitmapDrawable(), Callback {
        val canvas = Canvas()
        val clearPaint = Paint()
        var shadow: Drawable? = null

        init {
            drawable.callback = this
            clearPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        override fun draw(canvas: Canvas) {
            if (bitmap == null) return
            this.canvas.drawRect(bounds, clearPaint)
            shadow?.draw(this.canvas)
            drawable.draw(this.canvas)
            super.draw(canvas)
        }

        @SuppressLint("NewApi")
        override fun onBoundsChange(bounds: Rect) {
            val width = bounds.right - bounds.left
            val height = bounds.bottom - bounds.top
            bitmap = Bitmap.createBitmap(width, height,
                    Bitmap.Config.ARGB_8888)
            canvas.setBitmap(bitmap)
            if (adaptive) {
                drawable.setBounds(0, 0, 1, 1)
                val scale = IconNormalizer.getInstance().getScale(drawable, null)
                val inset = ((width - (width * scale)) / 2).toInt()
                drawable.setBounds(inset, inset, width - inset, height - inset)
                if (Utilities.ATLEAST_OREO) {
                    AdaptiveIconDrawable(ColorDrawable(Color.WHITE), ColorDrawable(Color.WHITE)).apply {
                        setBounds(inset, inset, width - inset, height - inset)
                    }.draw(canvas)
                } else {
                    AdaptiveIconDrawableCompat(ColorDrawable(Color.WHITE), ColorDrawable(Color.WHITE), false).apply {
                        setBounds(inset, inset, width - inset, height - inset)
                    }.draw(canvas)
                }
                shadow = BitmapDrawable(Utilities.getShadowForIcon(bitmap, width))
                shadow?.setBounds(0, 0, width, height)
            } else {
                drawable.setBounds(0, 0, width, height)
            }
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
