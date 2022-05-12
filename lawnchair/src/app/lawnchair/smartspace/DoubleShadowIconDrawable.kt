package app.lawnchair.smartspace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import androidx.core.graphics.ColorUtils
import com.android.launcher3.R

class DoubleShadowIconDrawable(icon: Drawable, context: Context) : LayerDrawable(emptyArray()) {
    private val iconDrawable: Drawable
    private val shadowDrawable: Drawable

    init {
        val iconSize = context.resources.getDimensionPixelSize(R.dimen.enhanced_smartspace_icon_size)
        val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        icon.setBounds(0, 0, iconSize, iconSize)
        icon.draw(canvas)
        iconDrawable = BitmapDrawable(context.resources, bitmap)
        shadowDrawable = generateShadowDrawable(bitmap, context)
        addLayer(shadowDrawable)
        addLayer(iconDrawable)
        setBounds(0, 0, iconSize, iconSize)
    }

    private fun generateShadowDrawable(iconBitmap: Bitmap, context: Context): Drawable {
        val shadowBitmap = Bitmap.createBitmap(iconBitmap.width, iconBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(shadowBitmap)
        val res = context.resources
        val ambientShadowRadius = res.getDimensionPixelSize(R.dimen.ambient_text_shadow_radius).toFloat()
        val keyShadowRadius = res.getDimensionPixelSize(R.dimen.key_text_shadow_radius).toFloat()
        val keyShadowDx = res.getDimensionPixelSize(R.dimen.key_text_shadow_dx).toFloat()
        val keyShadowDy = res.getDimensionPixelSize(R.dimen.key_text_shadow_dy).toFloat()
        val alphaOffset = IntArray(2)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val alphaPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (ambientShadowRadius != 0f) {
            shadowPaint.maskFilter = BlurMaskFilter(ambientShadowRadius, BlurMaskFilter.Blur.NORMAL)
            val alphaBitmap = iconBitmap.extractAlpha(shadowPaint, alphaOffset)
            alphaPaint.alpha = 64
            canvas.drawBitmap(alphaBitmap, alphaOffset[0].toFloat(), alphaOffset[1].toFloat(), alphaPaint)
        }
        if (keyShadowRadius != 0f) {
            shadowPaint.maskFilter = BlurMaskFilter(keyShadowRadius, BlurMaskFilter.Blur.NORMAL)
            val alphaBitmap = iconBitmap.extractAlpha(shadowPaint, alphaOffset)
            alphaPaint.alpha = 72
            canvas.drawBitmap(alphaBitmap, alphaOffset[0].toFloat() + keyShadowDx, alphaOffset[1].toFloat() + keyShadowDy, alphaPaint)
        }
        return BitmapDrawable(context.resources, shadowBitmap)
    }

    override fun setTint(tintColor: Int) {
        iconDrawable.setTint(tintColor)
        val tintLuminance = ColorUtils.calculateLuminance(tintColor)
        shadowDrawable.alpha = if (tintLuminance > 0.5) 255 else 0
    }
}
