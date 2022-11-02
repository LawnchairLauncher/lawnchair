package app.lawnchair.theme.drawable

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import app.lawnchair.theme.ResourceToken
import app.lawnchair.theme.UiColorMode
import app.lawnchair.theme.color.ColorToken
import com.android.launcher3.util.Themes
import dev.kdrag0n.monet.theme.ColorScheme

interface DrawableToken<T : Drawable> : ResourceToken<T>

data class ResourceDrawableToken<T : Drawable>(@DrawableRes private val resId: Int) : DrawableToken<T> {

    @Suppress("UNCHECKED_CAST")
    override fun resolve(context: Context, scheme: ColorScheme, uiColorMode: UiColorMode): T {
        return context.getDrawable(resId) as T
    }
}

data class AttributeDrawableToken<T : Drawable>(@AttrRes private val attr: Int) : DrawableToken<T> {

    override fun resolve(context: Context, scheme: ColorScheme, uiColorMode: UiColorMode): T {
        @Suppress("UNCHECKED_CAST")
        return Themes.getAttrDrawable(context, attr) as T
    }
}

data class MutatedDrawableToken<T : Drawable>(
    private val token: DrawableToken<T>,
    private val mutateBlock: T.(context: Context, scheme: ColorScheme, uiColorMode: UiColorMode) -> Unit
) : DrawableToken<T> {

    override fun resolve(context: Context, scheme: ColorScheme, uiColorMode: UiColorMode): T {
        val drawable = token.resolve(context, scheme, uiColorMode)
        mutateBlock(drawable, context, scheme, uiColorMode)
        return drawable
    }
}

data class NewDrawable<T : Drawable>(
    private val factory: (context: Context, scheme: ColorScheme, uiColorMode: UiColorMode) -> T
) : DrawableToken<T> {

    override fun resolve(context: Context, scheme: ColorScheme, uiColorMode: UiColorMode): T {
        return factory(context, scheme, uiColorMode)
    }
}

fun <T : Drawable> DrawableToken<T>.mutate(
    mutateBlock: T.(context: Context, scheme: ColorScheme, uiColorMode: UiColorMode) -> Unit
) = MutatedDrawableToken(this, mutateBlock)

fun <T : GradientDrawable> DrawableToken<T>.setColor(color: ColorToken) = mutate { context, scheme, uiColorMode ->
    setColor(color.resolveColor(context, scheme, uiColorMode))
}

fun <T : Drawable> DrawableToken<T>.setTint(color: ColorToken) = mutate { context, scheme, uiColorMode ->
    setTint(color.resolveColor(context, scheme, uiColorMode))
}

fun <T : GradientDrawable> DrawableToken<T>.setStroke(widthDp: Float, color: ColorToken) = mutate { context, scheme, uiColorMode ->
    val widthPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        widthDp,
        Resources.getSystem().displayMetrics
    )

    setStroke(widthPx.toInt(), color.resolveColor(context, scheme, uiColorMode))
}
