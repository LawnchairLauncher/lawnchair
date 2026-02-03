package app.lawnchair.smartspace

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import app.lawnchair.views.CustomTextView
import com.android.launcher3.views.ShadowInfo.Companion.fromContext

open class DoubleShadowTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : CustomTextView(context, attrs) {

    private val shadowInfo = fromContext(context, attrs, 0)

    init {
        setShadowLayer(shadowInfo.ambientShadowBlur, 0f, 0f, shadowInfo.ambientShadowColor)
    }

    /**
     * This is KT equivalent of [com.android.launcher3.views.DoubleShadowBubbleTextView.skipDoubleShadow],
     *
     * Usage of this function should be discouraged unless you have to.
     * @see com.android.launcher3.views.DoubleShadowBubbleTextView.skipDoubleShadow
     **/
    private fun skipDoubleShadow(): Boolean {
        val textAlpha = Color.alpha(currentTextColor)
        val keyShadowAlpha = Color.alpha(shadowInfo.keyShadowColor)
        val ambientShadowAlpha = Color.alpha(shadowInfo.ambientShadowColor)

        when {
            textAlpha == 0 || (keyShadowAlpha == 0 && ambientShadowAlpha == 0) -> {
                paint.clearShadowLayer()
                return true
            }

            ambientShadowAlpha > 0 && keyShadowAlpha == 0 -> {
                paint.setShadowLayer(shadowInfo.ambientShadowBlur, 0f, 0f, shadowInfo.ambientShadowColor)
                return true
            }

            keyShadowAlpha > 0 && ambientShadowAlpha == 0 -> {
                paint.setShadowLayer(
                    shadowInfo.keyShadowBlur,
                    shadowInfo.keyShadowOffsetX,
                    shadowInfo.keyShadowOffsetY,
                    shadowInfo.keyShadowColor,
                )
                return true
            }

            else -> {
                return false
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        // If text is transparent or shadow alpha is 0, don't draw any shadow
        if (skipDoubleShadow()) {
            super.onDraw(canvas)
            return
        }

        // We enhance the shadow by drawing the shadow twice
        paint.setShadowLayer(shadowInfo.ambientShadowBlur, 0f, 0f, shadowInfo.ambientShadowColor)

        super.onDraw(canvas)
        canvas.save()
        canvas.clipRect(
            scrollX,
            scrollY + extendedPaddingTop,
            scrollX + width,
            scrollY + height,
        )

        paint.setShadowLayer(
            shadowInfo.keyShadowBlur,
            shadowInfo.keyShadowOffsetX,
            shadowInfo.keyShadowOffsetY,
            shadowInfo.keyShadowColor,
        )
        super.onDraw(canvas)
        canvas.restore()
    }
}
