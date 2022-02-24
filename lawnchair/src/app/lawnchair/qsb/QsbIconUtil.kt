package app.lawnchair.qsb

import android.widget.ImageView
import androidx.annotation.DrawableRes
import app.lawnchair.theme.color.ColorTokens
import com.devs.vectorchildfinder.VectorChildFinder

fun ImageView.setThemedIconResource(
    @DrawableRes resId: Int,
    themed: Boolean,
    method: ThemingMethod = ThemingMethod.THEME_BY_NAME
) {
    if (themed && method == ThemingMethod.THEME_BY_NAME) {
        val vector = VectorChildFinder(context, resId, this)
        val primary = vector.findPathByName("qsbIconTintPrimary")
        val primary2 = vector.findPathByName("qsbIconTintPrimary2")
        val secondary = vector.findPathByName("qsbIconTintSecondary")
        val tertiary = vector.findPathByName("qsbIconTintTertiary")
        val quaternary = vector.findPathByName("qsbIconTintQuaternary")

        primary?.fillColor = ColorTokens.QsbIconTintPrimary.resolveColor(context)
        primary2?.fillColor = ColorTokens.QsbIconTintPrimary.resolveColor(context)
        secondary?.fillColor = ColorTokens.QsbIconTintSecondary.resolveColor(context)
        tertiary?.fillColor = ColorTokens.QsbIconTintTertiary.resolveColor(context)
        quaternary?.fillColor = ColorTokens.QsbIconTintQuaternary.resolveColor(context)
        invalidate()
    } else {
        setImageResource(resId)
        if (themed) setColorFilter(ColorTokens.ColorAccent.resolveColor(context))
    }
}


enum class ThemingMethod {
    TINT,
    THEME_BY_NAME
}
