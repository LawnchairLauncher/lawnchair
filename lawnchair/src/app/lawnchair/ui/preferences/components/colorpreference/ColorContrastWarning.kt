package app.lawnchair.ui.preferences.components.colorpreference

import androidx.annotation.ColorInt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.components.controls.WarningPreference

private const val CONTRAST_THRESHOLD = 1.5

/**
 * Displays [WarningPreference] when [foregroundColor] & [backgroundColor] have less than optimal contrast with each other.
 *
 * @see CONTRAST_THRESHOLD
 */
@Composable
fun ColorContrastWarning(
    foregroundColor: ColorOption,
    backgroundColor: ColorOption,
    text: String,
    modifier: Modifier = Modifier,
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
    @ColorInt foregroundColor: Int,
    @ColorInt backgroundColor: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    val enoughContrast = if (foregroundColor != 0 && backgroundColor != 0) {
        ColorUtils.calculateContrast(
            foregroundColor,
            backgroundColor,
        ) >= CONTRAST_THRESHOLD
    } else {
        true
    }

    if (!enoughContrast) {
        WarningPreference(
            modifier = modifier,
            text = text,
        )
    }
}
