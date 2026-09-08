package app.lawnchair.icons.customization

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF

data class IconCustomizationConfig(
    val iconSizeMultiplier: Float = 1.0f,
    val iconCornerRadiusRatio: Float = 0.35f, // Squircle / rounded ratio
    val darkThemeBgColor: Int = 0xFF1B2E21.toInt(),
    val lightThemeGlyphColor: Int = 0xFF86EFAC.toInt(),
    val enableHighContrastDualTone: Boolean = true
)

class ExpressiveIconCustomizer(private val context: Context) {

    fun applyDualToneStyle(originalBitmap: Bitmap, config: IconCustomizationConfig): Bitmap {
        val width = originalBitmap.width
        val height = originalBitmap.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = config.darkThemeBgColor
        }

        val cornerRadius = width * config.iconCornerRadiusRatio
        val rectF = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, bgPaint)

        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (config.enableHighContrastDualTone) {
                colorFilter = PorterDuffColorFilter(config.lightThemeGlyphColor, PorterDuff.Mode.SRC_IN)
            }
        }

        val padding = (width * 0.2f * (1f / config.iconSizeMultiplier)).toInt()
        val destRect = RectF(padding.toFloat(), padding.toFloat(), (width - padding).toFloat(), (height - padding).toFloat())
        canvas.drawBitmap(originalBitmap, null, destRect, iconPaint)

        return output
    }
}
