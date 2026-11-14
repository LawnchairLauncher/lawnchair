package app.lawnchair.icons

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached

class LocalAdaptiveIconDrawable private constructor(
    private val context: Context,
    val bitmap: Bitmap,
    private val foregroundLayer: LocalForegroundDrawable,
) : AdaptiveIconDrawable(
    ColorDrawable(Color.TRANSPARENT),
    foregroundLayer,
) {
    constructor(context: Context, bitmap: Bitmap) : this(
        context,
        bitmap,
        LocalForegroundDrawable(context.resources, bitmap),
    )

    init {
        foregroundLayer.owner = this
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val maskMatrix = Matrix()
    private val transformedMask = Path()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        foregroundLayer.bounds = bounds
        super.getForeground()?.bounds = bounds
        super.getBackground()?.bounds = bounds
    }

    override fun getForeground(): Drawable {
        val fg = super.getForeground()
        if (fg != null && !bounds.isEmpty && fg.bounds != bounds) {
            fg.bounds = bounds
        }
        return fg ?: foregroundLayer
    }

    override fun getBackground(): Drawable? {
        val bg = super.getBackground()
        if (bg != null && !bounds.isEmpty && bg.bounds != bounds) {
            bg.bounds = bounds
        }
        return bg
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return

        val iconShape: IconShape = try {
            val prefs = PreferenceManager2.getInstance(context)
            prefs.iconShape.firstCached(prefs)
        } catch (_: Throwable) {
            IconShape.Circle
        }

        val basePath = iconShape.getMaskPath()
        maskMatrix.reset()
        maskMatrix.setScale(b.width() / 100f, b.height() / 100f)
        maskMatrix.postTranslate(b.left.toFloat(), b.top.toFloat())
        transformedMask.reset()
        basePath.transform(maskMatrix, transformedMask)

        val saveCount = canvas.save()
        canvas.clipPath(transformedMask)
        canvas.drawBitmap(bitmap, null, b, paint)
        canvas.restoreToCount(saveCount)
    }

    override fun getIconMask(): Path {
        val b = bounds
        val iconShape: IconShape = try {
            val prefs = PreferenceManager2.getInstance(context)
            prefs.iconShape.firstCached(prefs)
        } catch (_: Throwable) {
            IconShape.Circle
        }
        val basePath = iconShape.getMaskPath()
        val matrix = Matrix()
        val w = if (b.width() > 0) b.width() else 100
        val h = if (b.height() > 0) b.height() else 100
        matrix.setScale(w / 100f, h / 100f)
        matrix.postTranslate(b.left.toFloat(), b.top.toFloat())
        val path = Path()
        basePath.transform(matrix, path)
        return path
    }

    class LocalForegroundDrawable(
        res: Resources,
        private val fgBitmap: Bitmap,
    ) : BitmapDrawable(res, fgBitmap) {
        var owner: Drawable? = null

        init {
            paint.isAntiAlias = true
            paint.isFilterBitmap = true
            paint.isDither = true
        }

        override fun draw(canvas: Canvas) {
            val b = if (!bounds.isEmpty) bounds else owner?.bounds
            if (b != null && !b.isEmpty) {
                canvas.drawBitmap(fgBitmap, null, b, paint)
            } else {
                super.draw(canvas)
            }
        }
    }
}
