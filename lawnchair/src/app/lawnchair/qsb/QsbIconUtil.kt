package app.lawnchair.qsb

import android.graphics.drawable.LayerDrawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import app.lawnchair.theme.color.tokens.ColorTokens
import com.android.launcher3.R

fun ImageView.setThemedIconResource(
    @DrawableRes resId: Int,
    themed: Boolean,
    method: ThemingMethod = ThemingMethod.THEME_BY_LAYER_ID,
) {
    if (themed && method == ThemingMethod.THEME_BY_LAYER_ID) {
        val drawable = ResourcesCompat.getDrawable(resources, resId, null)!!
        if (drawable is LayerDrawable) {
            drawable.mutate()
            val primary = ColorTokens.QsbIconTintPrimary.resolveColor(context)
            val secondary = ColorTokens.QsbIconTintSecondary.resolveColor(context)
            val tertiary = ColorTokens.QsbIconTintTertiary.resolveColor(context)
            val quaternary = ColorTokens.QsbIconTintQuaternary.resolveColor(context)

            for (i in (0 until drawable.numberOfLayers)) {
                val color = when (drawable.getId(i)) {
                    R.id.qsbIconTintPrimary -> primary
                    R.id.qsbIconTintSecondary -> secondary
                    R.id.qsbIconTintTertiary -> tertiary
                    R.id.qsbIconTintQuaternary -> quaternary
                    else -> 0
                }
                if (color == 0) continue

                val layer = drawable.getDrawable(i)
                layer.setTint(color)
            }
        }
        setImageDrawable(drawable)
    } else {
        setImageResource(resId)
        if (themed) setColorFilter(ColorTokens.ColorAccent.resolveColor(context))
    }
}

enum class ThemingMethod {
    TINT,
    THEME_BY_LAYER_ID,
}
