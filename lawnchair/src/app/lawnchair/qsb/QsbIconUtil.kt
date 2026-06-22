package app.lawnchair.qsb

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import app.lawnchair.theme.color.tokens.ColorTokens
import com.android.launcher3.R
import com.google.accompanist.drawablepainter.rememberDrawablePainter

/**
 * Loads a drawable resource and applies theming based on the specified [ThemingMethod].
 *
 * If [themed] is true, the function will apply colors based on the chosen method:
 * - [ThemingMethod.THEME_BY_LAYER_ID]: If the drawable is a [LayerDrawable], it searches for specific
 *   layer IDs (e.g., `R.id.qsbIconTintPrimary`) and applies corresponding QSB color tokens.
 * - [ThemingMethod.TINT]: Applies accent color tinting to the entire drawable.
 */
fun setThemedIconResource(
    context: Context,
    @DrawableRes resId: Int,
    themed: Boolean,
    method: ThemingMethod = ThemingMethod.THEME_BY_LAYER_ID,
): Drawable {
    val drawable = requireNotNull(ResourcesCompat.getDrawable(context.resources, resId, context.theme)) {
        "Unable to resolve icon drawable for resId=$resId"
    }.mutate()

    if (!themed) {
        return drawable
    }

    if (method == ThemingMethod.THEME_BY_LAYER_ID && drawable is LayerDrawable) {
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

            drawable.getDrawable(i).setTint(color)
        }
    } else {
        drawable.setTint(ColorTokens.ColorAccent.resolveColor(context))
    }

    return drawable
}

/**
 * Remembers a [Painter] for a themed icon resource.
 *
 * @param resId The drawable resource ID to load.
 * @param themed Whether the icon should have theming applied.
 * @param method The strategy to use for applying theme colors.
 * @return A [Painter] that draws the (potentially themed) drawable resource.
 */
@Composable
fun rememberThemedIconPainter(
    @DrawableRes resId: Int,
    themed: Boolean,
    method: ThemingMethod = ThemingMethod.THEME_BY_LAYER_ID,
): Painter {
    val context = LocalContext.current
    val drawable = remember(context, resId, themed, method) {
        setThemedIconResource(context, resId, themed, method)
    }
    return rememberDrawablePainter(drawable)
}

enum class ThemingMethod {
    TINT,
    THEME_BY_LAYER_ID,
}
