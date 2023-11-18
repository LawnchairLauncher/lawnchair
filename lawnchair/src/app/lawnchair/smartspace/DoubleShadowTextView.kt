package app.lawnchair.smartspace

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import app.lawnchair.views.CustomTextView
import com.android.launcher3.views.DoubleShadowBubbleTextView.ShadowInfo

open class DoubleShadowTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : CustomTextView(context, attrs) {

    private val shadowInfo = ShadowInfo(context, attrs, 0)

    init {
        setShadowLayer(shadowInfo.ambientShadowBlur, 0f, 0f, shadowInfo.ambientShadowColor)
    }

    override fun onDraw(canvas: Canvas) {
        // If text is transparent or shadow alpha is 0, don't draw any shadow
        if (shadowInfo.skipDoubleShadow(this)) {
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
