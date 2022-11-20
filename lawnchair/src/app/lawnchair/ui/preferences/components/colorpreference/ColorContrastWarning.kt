package app.lawnchair.ui.preferences.components.colorpreference

import androidx.annotation.ColorInt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.components.WarningPreference

private const val CONTRAST_THRESHOLD = 1.5

/**
 * Displays [WarningPreference] when [foregroundColor] & [backgroundColor] have less than optimal contrast with each other.
 *
 * @see CONTRAST_THRESHOLD
 */
@Composable
fun ColorContrastWarning(
    modifier: Modifier = Modifier,
    foregroundColor: ColorOption,
    backgroundColor: ColorOption,
    text: String,
) {
    val context = LocalContext.current
    val foregroundColorInt = foregroundColor.colorPreferenceEntry.lightColor(context)
    val backgroundColorInt = backgroundColor.colorPreferenceEntry.lightColor(context)
    ColorContrastWarning(
        modifier = modifier,
        foregroundColor = foregroundColorInt,
        backgroundColor = backgroundColorInt,
        text = text,
    )
}

/**
 * Displays [WarningPreference] when [foregroundColor] & [backgroundColor] have less than optimal contrast with each other.
 *
 * @see CONTRAST_THRESHOLD
 */
@Composable
fun ColorContrastWarning(
    modifier: Modifier = Modifier,
    @ColorInt foregroundColor: Int,
    @ColorInt backgroundColor: Int,
    text: String,
) {

    val enoughContrast = if (foregroundColor != 0 && backgroundColor != 0) {
        ColorUtils.calculateContrast(
            foregroundColor,
            backgroundColor,
        ) >= CONTRAST_THRESHOLD
    } else true

    if (!enoughContrast) {
        WarningPreference(
            modifier = modifier,
            text = text,
        )
    }

}
