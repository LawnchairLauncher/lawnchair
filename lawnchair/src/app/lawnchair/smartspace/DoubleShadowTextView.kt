package app.lawnchair.smartspace

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.core.graphics.ColorUtils
import app.lawnchair.views.CustomTextView
import com.android.launcher3.R
import com.android.launcher3.util.Themes

open class DoubleShadowTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : CustomTextView(context, attrs) {

    private var drawShadow = true
    private val keyShadowBlur = resources.getDimensionPixelSize(R.dimen.key_text_shadow_radius).toFloat()
    private val keyShadowOffsetX = resources.getDimensionPixelSize(R.dimen.key_text_shadow_dx).toFloat()
    private val keyShadowOffsetY = resources.getDimensionPixelSize(R.dimen.key_text_shadow_dy).toFloat()
    private val keyShadowColor = Themes.getAttrColor(context, R.attr.keyShadowColor)
    private val ambientShadowBlur = resources.getDimensionPixelSize(R.dimen.ambient_text_shadow_radius).toFloat()
    private val ambientShadowColor = Themes.getAttrColor(context, R.attr.ambientShadowColor)

    init {
        updateDrawShadow(currentTextColor)
    }

    override fun setTextColor(color: Int) {
        super.setTextColor(color)
        updateDrawShadow(color)
    }

    private fun updateDrawShadow(var1: Int) {
        drawShadow = ColorUtils.calculateLuminance(var1) > 0.5
    }

    override fun onDraw(canvas: Canvas) {
        if (!drawShadow) {
            paint.clearShadowLayer()
            super.onDraw(canvas)
        } else {
            paint.setShadowLayer(ambientShadowBlur, 0.0f, 0.0f, ambientShadowColor)
            super.onDraw(canvas)
            canvas.save()
            canvas.clipRect(
                scrollX, scrollY + extendedPaddingTop,
                scrollX + width, scrollY + height
            )
            paint.setShadowLayer(
                keyShadowBlur, keyShadowOffsetX, keyShadowOffsetY,
                keyShadowColor
            )
            super.onDraw(canvas)
            canvas.restore()
        }
    }
}
