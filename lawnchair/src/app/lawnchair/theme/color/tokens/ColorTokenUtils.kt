package app.lawnchair.theme.color.tokens

import android.content.Context
import app.lawnchair.preferences.PreferenceManager

fun ColorToken.setAlpha(alpha: Float) = SetAlphaColorToken(this, alpha)
fun ColorToken.setLStar(lStar: Double) = SetLStarColorToken(this, lStar)
fun ColorToken.withContext(transform: ColorToken.(Context) -> ColorToken) = WithContextColorToken(this, transform)
inline fun ColorToken.withPreferences(
    crossinline transform: ColorToken.(PreferenceManager) -> ColorToken,
) = withContext { context ->
    val prefs = PreferenceManager.getInstance(context)
    transform(this, prefs)
}
